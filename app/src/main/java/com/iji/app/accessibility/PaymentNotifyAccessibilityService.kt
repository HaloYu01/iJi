package com.iji.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.iji.app.MainActivity
import com.iji.app.R
import com.iji.app.autorecord.AutoBillEngine
import com.iji.app.autorecord.AutoDiag
import com.iji.app.autorecord.AutoListenPrefs
import com.iji.app.autorecord.AutoNode
import com.iji.app.autorecord.AutoNodeCollector
import com.iji.app.autorecord.AutoPlatform
import com.iji.app.autorecord.AutoRecordStatusService
import com.iji.app.autorecord.AutoPlatforms
import com.iji.app.autorecord.AutoRecordStore
import com.iji.app.autorecord.ShizukuNodeReader
import com.iji.app.budget.NotificationHelper
import com.iji.app.capture.ForegroundAppResolver
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.ui.PendingBillHolder
import com.iji.app.ui.overlay.BillOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * 支付通知自动记账（无障碍服务）。
 *
 * 原理：只**读取**通知栏文本（`TYPE_NOTIFICATION_TEXT_CHANGED`），
 * 命中「含金额 + 含收支语义」时解析出一条待确认账单，并发一条带「记一笔 / 忽略」
 * 动作的通知；用户点「记一笔」才真正打开记账面板预填。
 *
 * 三条自律约束（都是为了不给用户添麻烦 / 不越权）：
 * 1. **只读通知，不模拟点击、不拦截通知**——不做自动扣款式的"替用户操作"
 * 2. **只提示，不直接记账**——金额、分类都可能解析错，必须由用户确认后才落库
 * 3. **只认自己 App 之外的通知**，并按「来源+金额+商家」做 60 秒去重，防重复打扰
 *
 * 隐私：通知文本仅在本机内存中解析，不上传任何服务器。
 */
class PaymentNotifyAccessibilityService : AccessibilityService() {

    /** 去重：key = 来源|金额|商家 → 最近一次时间戳 */
    private val recentHits = LinkedHashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        // 连通/断开都落一行：真机上「开关反复闪断」时，日志里能直接数出重连频率，
        // 也能区分「服务被系统重绑」和「进程被杀后重建」（pid 变化）
        AutoDiag.log(this, "a11y", "connected pid=${android.os.Process.myPid()}")
        // 记录「无障碍真的连上了」：下次发现开关被系统关掉时，页面据此判断
        // 是「被系统清理」而不是「用户从没开过」
        CapturePreferences.markA11yConnected(this)
        // 微信/支付宝的界面节点几乎全部标记为 important=false：
        // 不开 FLAG_INCLUDE_NOT_IMPORTANT_VIEWS，getChild() 直接返回空，
        // 表现为「根节点包名正确但 childCount=0」（系统 dump 工具自带该 flag，
        // 所以它能读到 74 个节点，我们读到 0 个）。
        // 代码里显式设置一次，不依赖 XML 是否被正确解析；四个 flag 都是识别必需的。
        // 只在 XML 缺项时补写。
        // 每次 onServiceConnected 都赋值 `serviceInfo` 会让系统重新应用一遍服务配置，
        // 部分 ROM（HyperOS/MIUI 实测）会因此解绑再绑定一次，表现为开关反复闪断。
        runCatching {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            val requiredFlags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                // WebView 内部结构要靠这个 flag 才会暴露：微信支付账单页是
                // MMWebViewUI + android.webkit.WebView，缺了它整棵子树都读不到
                AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            val requiredEvents = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            val missingFlags = requiredFlags and info.flags.inv()
            val missingEvents = requiredEvents and info.eventTypes.inv()
            if (missingFlags != 0 || missingEvents != 0) {
                info.flags = info.flags or requiredFlags
                info.eventTypes = info.eventTypes or requiredEvents
                serviceInfo = info
                AutoDiag.log(this, "service", "补齐缺失配置 flags=0x${missingFlags.toString(16)} events=0x${missingEvents.toString(16)}")
            }
        }.onFailure { Log.w(TAG, "设置 serviceInfo 失败", it) }
        // 常驻状态服务 + 1px 覆盖层：两者都只为提升存活率与可观测性，
        // 不采集任何数据；总闸关或切到半自动时服务会自动停
        AutoRecordStatusService.startIfEnabled(this)
        addAliveOverlay()
    }

    override fun onDestroy() {
        AutoDiag.log(this, "a11y", "destroy pid=${android.os.Process.myPid()}")
        if (current === this) current = null
        clearParseQueues()
        removeAliveOverlay()
        // 不直接停服务：把通知改成「已关闭，点此恢复」——
        // 用户被系统清理后，这是唯一能告诉他发生了什么的地方
        AutoRecordStatusService.markDisconnected(this)
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AutoDiag.log(this, "a11y", "unbind pid=${android.os.Process.myPid()}")
        if (current === this) current = null
        // 与钱迹一致：解除绑定即清空待处理任务，避免服务重建后跑到旧窗口
        clearParseQueues()
        return super.onUnbind(intent)
    }

    /** 取消所有挂起的解析：延迟任务与合并桶（线程池留到 onDestroy 再关，避免重连后不可用） */
    private fun clearParseQueues() {
        autoCaptureHandler.removeCallbacksAndMessages(null)
        synchronized(parseSlots) { parseSlots.clear() }
        loadingWindowId = null
        BillOverlay.dismissLoading()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // 任何一个页面的解析异常都不能炸掉无障碍服务：服务一崩系统会重连，
        // 用户看到的就是设置里的开关反复「关闭 → 开启 → 关闭 → 开启」。
        // 异常只落诊断日志，服务继续活着；下一轮事件照常处理。
        try {
            dispatchEvent(event)
        } catch (t: Throwable) {
            runCatching {
                AutoDiag.log(this, "crash", "type=${event.eventType} ${t.javaClass.simpleName}: ${t.message}")
            }
            Log.w(TAG, "无障碍事件处理异常（服务保持存活）", t)
        }
    }

    private fun dispatchEvent(event: AccessibilityEvent) {
        // 窗口切换只用来记住「当前前台是哪个 App」，供识屏判断账单来源；
        // 只读事件自带的包名，不遍历控件树、不解析界面文字，系统窗口/桌面/本 App 一律跳过。
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != packageName) {
                val autoPlatform = AutoPlatforms.byPackage(pkg)
                if (autoPlatform != null) {
                    lastForegroundPkg = pkg
                    lastForegroundClass = event.className?.toString()
                    maybeAutoCapture(autoPlatform, event)
                } else if (ForegroundAppResolver.isBillAppCandidate(pkg, packageName)) {
                    lastForegroundPkg = pkg
                    lastForegroundClass = event.className?.toString()
                }
            }
            return
        }
        // 只监听「通知状态变化」：现代 SDK（API 35+）已不再有 TYPE_NOTIFICATION_TEXT_CHANGED，
        // 通知文本统一由该事件携带；getText() 也已上移到父类 AccessibilityRecord（继承调用仍可用）。
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        // 应用内逻辑总闸：关掉后不再发「待记账」提示，但不影响窗口切换记录（识屏来源判断要用）
        if (!AutoBookkeepingPrefs.masterEnabled(this)) return
        val sourcePkg = event.packageName?.toString() ?: return
        // 忽略自己发出的通知，否则会因自己发的「记账提醒」再次触发，形成自激
        if (sourcePkg == packageName) return

        val text = (0 until event.text.count())
            .mapNotNull { event.text.get(it)?.toString() }
            .joinToString(" ")
            .trim()
        if (text.isBlank()) return

        // 关键词闸门：默认白名单挡掉绝大多数群消息 / 营销推送；命中黑名单直接丢弃。
        // 放在解析之前，省掉后面的 26 条正则与去重开销。
        val allowed = PaymentNotificationFilter.allowed(
            text = text,
            whitelist = SettingsRepository.getString(
                this,
                PaymentNotificationFilter.KEY_WHITELIST,
                PaymentNotificationFilter.DEFAULT_WHITELIST,
            ),
            blacklist = SettingsRepository.getString(
                this,
                PaymentNotificationFilter.KEY_BLACKLIST,
                "",
            ),
        )
        if (!allowed) return

        // 传来源包名：App 专属识别规则（如微信支付/云闪付）按包名精确限定
        val parsed = PaymentNotificationParser.parse(sourcePkg, text) ?: return

        val now = System.currentTimeMillis()
        // 窗口可在「记账偏好」里调：默认 60s，太短会重复入账、太长会把两笔真实同额消费合并
        val windowMs = AutoBookkeepingPrefs.dedupWindowMs(AutoBookkeepingPrefs.dedupSeconds(this))
        recentHits.entries.removeAll { now - it.value > windowMs }
        val key = "$sourcePkg|${parsed.amountCents}|${parsed.merchant}"
        if (recentHits.containsKey(key)) return
        recentHits[key] = now

        val sourceLabel = PaymentNotificationParser.supportedSources[sourcePkg] ?: sourcePkg
        runCatching { notifyCaptured(parsed, sourceLabel) }
            .onFailure { Log.w(TAG, "发送待记账通知失败", it) }
    }

    override fun onInterrupt() = Unit

    // ── 全自动记账（无障碍节点识别，不截屏、不 OCR）────────────────────────
    //
    // 与半自动（截屏 + OCR）是**两条完全独立的链路**：这里不截图、不看图、
    // 不需要截屏授权，只用无障碍读到的界面文本。参数对齐钱迹 4.5.5：
    // 微信 150ms、其余 300ms；支付宝 160ms（只认支付成功页）+ 450ms（全量）双抓。

    private val autoCaptureHandler = Handler(Looper.getMainLooper())

    /**
     * 1×1 的无障碍保活窗（GKD 同款做法）。
     *
     * 它不显示任何内容，只让进程持有一个 `TYPE_ACCESSIBILITY_OVERLAY` 窗口：
     * 在系统眼里这个进程「有界面」，被当成可回收后台的概率更低。
     * 属于提高存活率的辅助手段，不保证 100%，所以仍然保留「被关后恢复」的链路。
     */
    private var aliveView: View? = null

    /**
     * 事件合并桶，语义对齐钱迹的 `E()` / `D()`：
     *
     * key = `windowId_pass`。同一个窗口同一个通道的连续事件**不重复排队**，
     * 只更新最新一次事件的根节点并计数；如果桶正在被解析（processing），
     * 新事件只置 `needAgain`，本轮处理完自动再跑一轮。
     *
     * 这一点与我们原来的「防抖后只跑一次」有本质区别：解析期间发生的事件
     * （金额后渲染、标题后出现）不会被丢掉，而是被下一轮接住。
     */
    private class ParseSlot(var root: AccessibilityNodeInfo?) {
        var processing = false
        var needAgain = false
        var merged = 0
    }

    private val parseSlots = HashMap<String, ParseSlot>()
    private var loadingWindowId: Int? = null

    /** dump 限流：基准取上一次 dump 结束时刻，避免 dump 完立刻又 dump。 */
    private var lastDumpKey = ""
    private var lastDumpAt = 0L
    private val DUMP_INTERVAL_MS = 4000L

    /** dump 退避：每次 dump 都伴随无障碍解绑重连，连续空树必须停手，否则越转越慢。 */
    private var dumpBackoffUntil = 0L
    private var dumpFailStreak = 0
    private val DUMP_FAIL_BACKOFF_MS = 30_000L
    private val DUMP_HIT_BACKOFF_MS = 8_000L

    /** 每窗口上次解析结束时刻：WebView 事件风暴下防止「解析→移除桶→立刻再解析」拖死主线程。 */
    private val lastParsedAt = HashMap<String, Long>()


    /** 串行解析线程：节点收集在主线程，纯数据解析放这里，避免阻塞无障碍事件分发 */
    private val parseExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "auto-bill-parse").apply { isDaemon = true }
    }

    /**
     * 全自动记账的触发点：页面事件 → 合并桶 → 延迟 → 抓节点 → 平台规则提取 → 确认。
     *
     * - **事件源为空时回落到 rootInActiveWindow**：系统为了性能常常不给
     *   `TYPE_WINDOW_CONTENT_CHANGED` 填 source，钱迹那种「source 为空就返回」
     *   在我们的触发面上会把绝大多数页面变化直接丢掉；
     * - 根节点优先取同 windowId 的 rootInActiveWindow，否则从事件节点向上找；
     * - 微信 150ms、其余 300ms；支付宝 160ms（只认支付成功页）+ 450ms（全量）；
     * - 同窗口同通道的事件合并，解析中再来则清零后重跑。
     */
    private fun maybeAutoCapture(platform: AutoPlatform, event: AccessibilityEvent) {
        if (!AutoBookkeepingPrefs.masterEnabled(this)) return
        if (CapturePreferences.workflow(this) != CapturePreferences.WORKFLOW_AUTO) return
        if (platform.packageName !in AutoListenPrefs.packages(this)) return
        // 页面判据必须用**窗口类名**：CONTENT_CHANGED 的 className 是子节点类型
        // （TextView/View），拿它做微信钱包页白名单会把账单页自己也挡掉。
        // 窗口类名由最近一次 WINDOW_STATE_CHANGED 记录在 lastForegroundClass。
        val className = if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.className?.toString()
        } else {
            lastForegroundClass
        }
        if (!platform.isCandidatePage(className)) return

        // 该窗口已有排队中的解析：WebView 账单页每秒几十条 CONTENT_CHANGED，
        // 再爬一次 parent / 窗口列表都是跨进程 IPC，纯属浪费；
        // 交给 needAgain 在解析结束后重跑即可（runParse 会重新取 freshRoot）。
        // 少了这个快速路径，事件风暴会把主线程拖到 ANR，系统随即杀掉无障碍服务。
        val pendingWindow = runCatching { event.windowId }.getOrDefault(-1)
        if (pendingWindow != -1) {
            val pending = synchronized(parseSlots) { parseSlots.containsKey("${pendingWindow}_main") }
            if (pending) return
            // 解析刚结束的冷却窗口内不再建桶：WebView 一次渲染会连发几十条事件，
            // 否则会形成「解析→移除桶→下一条事件再解析」的无限循环
            val cooldownKey = "${platform.packageName}_$pendingWindow"
            val last = synchronized(lastParsedAt) { lastParsedAt[cooldownKey] ?: 0L }
            if (android.os.SystemClock.elapsedRealtime() - last < PARSE_COOLDOWN_MS) return
        }

        val root = eventRoot(event) ?: return
        // 支付宝早抓通道：支付成功页先出金额、详情页后渲染，用早/晚两次覆盖两种时序
        platform.fastDelayMillis?.let { fastDelay ->
            scheduleParse(platform, root, event.windowId, fastDelay, "fast", platform.fastPageTypes)
        }
        scheduleParse(platform, root, event.windowId, platform.delayMillis, "main", null)
    }

    /**
     * 取**事件所属窗口**的根节点，对齐钱迹的 `C()`。
     *
     * 这里绝不能退化成 `rootInActiveWindow`：真机上系统 UI（通知栏/状态栏）
     * 经常就是 active 窗口，用它解析只会得到 `com.android.systemui` 的十来个节点，
     * 而微信账单页的 74 个节点在另一个窗口里——表现就是「切了页面也识别不了」。
     *
     * 三步逐级降级：
     * 1. 事件自带节点 → 沿 parent 爬到窗口根（最可靠，不依赖窗口列表，钱迹就是这么做的）；
     * 2. 按 `windowId` 在窗口列表里精确匹配（依赖 `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`）；
     * 3. 最后才看 active，且其 `windowId` 必须与事件一致，否则宁可放弃。
     */
    private fun eventRoot(event: AccessibilityEvent): AccessibilityNodeInfo? {
        val windowId = runCatching { event.windowId }.getOrDefault(-1)
        runCatching { event.source }.getOrNull()?.let { return climbToWindowRoot(it) }
        runCatching {
            windows.firstOrNull { it.id == windowId }?.root
        }.getOrNull()?.let { return it }
        val active = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        if (windowId == -1 || runCatching { active.windowId }.getOrNull() == windowId) return active
        return null
    }

    /** 沿 parent 爬到窗口根；跨窗口就停（等价于钱迹 `C()` 的向上找） */
    private fun climbToWindowRoot(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        val windowId = runCatching { node.windowId }.getOrDefault(-1)
        var current = node
        var guard = 0
        while (guard++ < 64) {
            val parent = runCatching { current.parent }.getOrNull() ?: return current
            if (windowId != -1 && runCatching { parent.windowId }.getOrNull() != windowId) return current
            current = parent
        }
        return current
    }

    /**
     * 延迟结束后重新取当前树。
     *
     * `NodeInfo` 是快照，页面重绘后旧引用会失效。这里同样**按 windowId 精确取窗口**，
     * 拿不到再退回 active（同样要求 windowId 一致）。
     * 绝不按包名模糊匹配窗口——那会把 systemui 的通知栏当成账单页。
     */
    private fun freshRoot(windowId: Int): AccessibilityNodeInfo? {
        runCatching {
            windows.firstOrNull { it.id == windowId }?.root
        }.getOrNull()?.let { return it }
        val active = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        return if (windowId == -1 || runCatching { active.windowId }.getOrNull() == windowId) active else null
    }

    /**
     * 入桶并调度。桶已存在时不重复排队，只更新根节点与计数——
     * 这是钱迹「滚动/连续变化只解析一次」的实现方式。
     */
    private fun scheduleParse(
        platform: AutoPlatform,
        root: AccessibilityNodeInfo,
        windowId: Int,
        delayMillis: Long,
        pass: String,
        allowPageTypes: Set<String>?,
    ) {
        val key = "${windowId}_$pass"
        synchronized(parseSlots) {
            val slot = parseSlots[key]
            if (slot != null) {
                slot.root = root
                slot.merged++
                // 正在解析时又来事件：标记重跑，处理完自动再来一轮（钱迹 needAgain）
                if (slot.processing) slot.needAgain = true
                return
            }
            parseSlots[key] = ParseSlot(root)
        }
        // 只在真正入桶时记一笔：CONTENT_CHANGED 很频繁，每次事件都写会把日志刷满。
        // 带上 className，真机才能看出「哪一页在解析」——微信只排除了首页，其它页都会进来
        AutoDiag.log(
            this,
            "schedule",
            "${platform.name} win=$windowId pass=$pass delay=${delayMillis}ms cls=${lastForegroundClass ?: "?"}",
        )
        autoCaptureHandler.postDelayed(
            { runParse(platform, windowId, pass, allowPageTypes) },
            delayMillis,
        )
    }

    /**
     * 跑一轮解析。节点收集留在主线程（NodeInfo 不适合跨线程），
     * 纯文本解析交给串行线程池；结束后按 needAgain 决定是否立刻重跑。
     */
    private fun runParse(
        platform: AutoPlatform,
        windowId: Int,
        pass: String,
        allowPageTypes: Set<String>?,
    ) {
        val key = "${windowId}_$pass"
        val slot = synchronized(parseSlots) {
            val current = parseSlots[key] ?: return
            current.processing = true
            current.needAgain = false
            current.merged = 0
            current
        }
        // 优先用实时树（页面可能已重绘，旧引用会失效）；拿不到才退回事件时的引用。
        // 两者都锚定**事件窗口**，不再猜 active 窗口。
        if (pass == "main") {
            loadingWindowId = windowId
            BillOverlay.showLoading(this)
        }
        val fresh = freshRoot(windowId)
        val root = fresh ?: slot.root
        if (root == null) {
            AutoDiag.log(this, "no-root", "${platform.name} win=$windowId 拿不到根节点")
            synchronized(parseSlots) { parseSlots.remove(key) }
            finishLoadingIfIdle(windowId)
            return
        }
        // 先 refresh 一次：WebView 子树在部分机型上要刷新才会填充，
        // 否则拿到的是 childCount=0 的旧快照。
        runCatching { root.refresh() }
        val groups = listOf(
            runCatching { AutoNodeCollector.collect(root) }.getOrDefault(emptyList()),
        )
        // 空采集时把「来源、子节点数、包名、flags」记全：
        // 能直接区分「引用过期」「窗口拿错」「系统限制」三种情况
        if (groups.all { it.isEmpty() }) {
            val child = runCatching { root.childCount }.getOrDefault(-1)
            val pkg = runCatching { root.packageName?.toString() }.getOrNull()
            val flags = runCatching { serviceInfo?.flags ?: -1 }.getOrDefault(-1)
            AutoDiag.log(
                this,
                "empty",
                "${platform.name} win=$windowId src=${if (fresh != null) "fresh" else "stale"} " +
                    "flags=0x${flags.toString(16)} fg=$lastForegroundPkg [$pkg/$child]",
            )
        }
        // 线程池已关闭（服务正在销毁）时静默放弃：钱迹同样捕获 RejectedExecutionException
        val submitted = runCatching {
            parseExecutor.execute {
                var recognized = false
                try {
                    recognized = parseNodes(platform, windowId, groups, allowPageTypes)
                } finally {
                    // 记录「这个窗口刚解析过」：main 通道结束后进入冷却。
                    // fast 通道（支付宝 160ms 早抓）不参与，否则会挡住正常的全量解析。
                    if (pass == "main") {
                        synchronized(lastParsedAt) {
                            lastParsedAt["${platform.packageName}_$windowId"] =
                                android.os.SystemClock.elapsedRealtime()
                            if (lastParsedAt.size > 32) {
                                lastParsedAt.remove(lastParsedAt.keys.first())
                            }
                        }
                    }
                    var again = false
                    synchronized(parseSlots) {
                        val current = parseSlots[key]
                        if (current == null || !current.needAgain) {
                            parseSlots.remove(key)
                        } else {
                            current.needAgain = false
                            current.processing = false
                            again = true
                        }
                    }
                    if (again) {
                        // 解析期间页面又变了：稍等一拍再跑，等它稳定
                        autoCaptureHandler.postDelayed(
                            { runParse(platform, windowId, pass, allowPageTypes) },
                            RETRY_DELAY_MS,
                        )
                    } else if (!recognized) {
                        finishLoadingIfIdle(windowId)
                    }
                }
            }
        }.isSuccess
        if (!submitted) {
            // 线程池已关闭时到这里：不写日志就会表现为「schedule 有、其余全无」
            AutoDiag.log(this, "parse", "${platform.name} 线程池拒绝执行")
            synchronized(parseSlots) { parseSlots.remove(key) }
            finishLoadingIfIdle(windowId)
        }
    }

    private fun finishLoadingIfIdle(windowId: Int) {
        autoCaptureHandler.post {
            if (loadingWindowId != windowId) return@post
            val busy = synchronized(parseSlots) {
                parseSlots.keys.any { key -> key.startsWith("${windowId}_") }
            }
            if (!busy) {
                loadingWindowId = null
                BillOverlay.dismissLoading()
            }
        }
    }

    /**
     * 抓一次节点并按平台规则提取。
     *
     * @param allowPageTypes 非 null 时只接受这些页面类型（支付宝早抓通道只认支付成功页，
     *   避免它先把账单详情页解析成半成品）
     */
    /**
     * 纯数据解析（在串行线程池里跑）：不碰 NodeInfo、不做延迟。
     *
     * 调度、合并、重跑已由 [scheduleParse] / [runParse] 负责，
     * 这里只回答「这棵节点树是哪一笔账」，与钱迹的 `A()` 一一对应。
     */
    private fun parseNodes(
        platform: AutoPlatform,
        windowId: Int,
        groups: List<List<AutoNode>>,
        allowPageTypes: Set<String>?,
    ): Boolean {
        if (CapturePreferences.workflow(this) != CapturePreferences.WORKFLOW_AUTO) return false
        if (!AutoBookkeepingPrefs.masterEnabled(this)) return false
        // 用户可能已经切走：前台包名变了就别解析这个窗口。
        // 但 lastForegroundPkg 可能残留旧值（进程刚重启、系统丢了切换事件），
        // 不能让一份过期缓存把正确的解析挡死——只有**明确切到另一个账单应用**才放弃。
        val fg = lastForegroundPkg
        if (fg != null && fg != platform.packageName && AutoPlatforms.byPackage(fg) != null) {
            AutoDiag.log(this, "skip", "${platform.name} 已切到 $fg")
            return false
        }
        var effective = groups
        if (groups.all { it.isEmpty() } && platform.shizukuFallback) {
            // 无障碍读不到：微信账单页对第三方服务返回空树（child=0，flags 已全开）。
            // 用 Shizuku 的 shell 身份跑 uiautomator dump 兜底，规则完全复用。
            // 只对显式声明 shizukuFallback 的平台生效：dump 单次 3–5 秒，
            // 无差别套用会把单线程解析池堵死（真机曾见京东一次 11.3 秒）。
            // 限流基准取「上一次 dump 结束时刻」：read() 单次实测 3–5 秒，
            // 若按开始时刻算 1.5s 窗口，一次 dump 结束时限流早已过期，
            // 紧接着的页面事件会立刻再 dump，形成连续阻塞
            // （真机日志里两次 dump 的间隔正好等于上一次的耗时）。
            val key = "${platform.packageName}_$windowId"
            val started = android.os.SystemClock.elapsedRealtime()
            // 退避中不再 dump：每次 dump 都伴随一次无障碍解绑重连，空转只会更慢
            if (started < dumpBackoffUntil) return false
            if (key == lastDumpKey && started - lastDumpAt < DUMP_INTERVAL_MS) return false
            lastDumpKey = key
            val ctx = applicationContext
            val dump = runCatching {
                kotlinx.coroutines.runBlocking { ShizukuNodeReader.read(ctx, platform.packageName) }
            }.getOrDefault(emptyList())
            // dump 结束后才刷新基准
            lastDumpAt = android.os.SystemClock.elapsedRealtime()
            AutoDiag.log(
                this,
                "shizuku",
                "${platform.name} dump=${dump.size} " +
                    "cost=${android.os.SystemClock.elapsedRealtime() - started}ms",
            )
            if (dump.isEmpty()) {
                dumpFailStreak++
                if (dumpFailStreak >= 2) {
                    dumpBackoffUntil = android.os.SystemClock.elapsedRealtime() + DUMP_FAIL_BACKOFF_MS
                    dumpFailStreak = 0
                    AutoDiag.log(this, "shizuku", "${platform.name} 连续空树，暂停 dump 30s")
                }
                // 具体原因（窗口数 / 各自子节点 / 包名）已由 runParse 记在 empty 行
                Log.d(TAG, "全自动识别跳过：${platform.name} 节点为空")
                return false
            }
            dumpFailStreak = 0
            effective = listOf(dump)
        }
        val sizes = effective.map { it.size }
        // 先每个窗口单独试：同一窗口内「标签下一段」的下标关系才成立
        var hit = effective.firstNotNullOfOrNull { group ->
            if (group.isEmpty()) null else AutoBillEngine.parseWithPage(platform, group)
        }
        // 单窗口都不成立时再合并：有些账单把金额和商户分在两个窗口里
        if (hit == null && effective.size > 1) {
            hit = AutoBillEngine.parseWithPage(platform, effective.flatten())
        }
        if (hit == null) {
            // 真机上查「为什么没识别」就看这一行：平台 + 每个窗口的节点数
            Log.d(TAG, "全自动未命中：${platform.name} win=$windowId sizes=$sizes")
            AutoDiag.log(this, "miss", "${platform.name} win=$windowId sizes=$sizes")
            AutoDiag.dumpPage(this, platform.name, null, effective.maxByOrNull { it.size }.orEmpty())
            // 不再手动补抓：解析期间的新事件已经由 needAgain 接住并重跑
            return false
        }
        if (allowPageTypes != null && hit.second !in allowPageTypes) return false
        val draft = hit.first
        // 命中后暂停 dump 8s：已经识别到了，页面继续渲染只会重复触发 dump + 无障碍重连
        if (platform.shizukuFallback) {
            dumpBackoffUntil = android.os.SystemClock.elapsedRealtime() + DUMP_HIT_BACKOFF_MS
        }
        AutoDiag.log(
            this,
            "hit",
            "${platform.name}/${hit.second} win=$windowId amount=${draft.amountCents} " +
                "out=${draft.outAsset} in=${draft.inAsset} fee=${draft.feeCents}",
        )
        // 投递走 AutoRecordStore 的应用级作用域，不再用服务自己的 scope：
        // 服务被销毁重建时 scope 会被 cancel，旧实例上排着的投递会静默丢失
        // （真机日志 `scopeActive=false`，表现就是「识别到但永不弹窗」）。
        AutoRecordStore.deliverDetected(this, draft)
        return true
    }

    /** 加一个 1×1、不可触摸不可聚焦的无障碍覆盖层；失败不影响主链路，只记一行日志 */
    private fun addAliveOverlay() {
        if (aliveView != null) return
        runCatching {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = View(this)
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.START or Gravity.TOP
                packageName = packageName
            }
            wm.addView(view, params)
            aliveView = view
            Log.d(TAG, "无障碍保活窗已添加")
        }.onFailure {
            aliveView = null
            Log.w(TAG, "无障碍保活窗添加失败（不影响识别）", it)
        }
    }

    private fun removeAliveOverlay() {
        val view = aliveView ?: return
        aliveView = null
        runCatching {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        }
    }

    /**
     * 取一帧当前屏幕（API 30+ 由系统直接提供，**不需要 MediaProjection 的授权弹窗**）。
     *
     * 只在用户主动点「截取屏幕」时调用；系统对截屏频率有节流，刚截过会直接失败，
     * 失败一律返回 null，由调用方如实提示（不假装成功）。
     */
    private suspend fun captureScreenOrNull(): Bitmap? = suspendCancellableCoroutine { cont ->
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val bmp = runCatching {
                            // 硬件缓冲必须拷成软件位图才能交给 ML Kit；拷完立刻释放缓冲
                            Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        }.getOrNull()
                        runCatching { result.hardwareBuffer.close() }
                        if (cont.isActive) cont.resume(bmp)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "无障碍截屏失败：${screenshotErrorText(errorCode)}（$errorCode）")
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }.onFailure {
            Log.w(TAG, "无障碍截屏调用失败", it)
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun screenshotErrorText(errorCode: Int): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "系统内部错误"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "无障碍服务没有截屏权限（需重新开启服务）"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "截屏太频繁，请稍后重试"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "显示设备不可用"
        else -> "未知错误"
    }

    private fun notifyCaptured(parsed: PaymentNotificationParser.Parsed, sourceLabel: String) {
        ensureChannel()

        val money = "¥%,.2f".format(parsed.amountCents / 100.0)
        val verb = if (parsed.type == "income") "收入" else "支出"
        val title = "记一笔 $verb $money"
        val text = parsed.merchant.ifBlank { "来自支付通知，点击确认后记账" }

        val saveIntent = Intent(this, PaymentActionReceiver::class.java).apply {
            action = PaymentActionReceiver.ACTION_SAVE
            putExtra(PaymentActionReceiver.EXTRA_TYPE, parsed.type)
            putExtra(PaymentActionReceiver.EXTRA_AMOUNT, parsed.amountCents)
            putExtra(PaymentActionReceiver.EXTRA_MERCHANT, parsed.merchant)
            putExtra(PaymentActionReceiver.EXTRA_ACCOUNT, parsed.accountHint)
            putExtra(PaymentActionReceiver.EXTRA_SOURCE, sourceLabel)
        }
        val ignoreIntent = Intent(this, PaymentActionReceiver::class.java).apply {
            action = PaymentActionReceiver.ACTION_IGNORE
            putExtra(PaymentActionReceiver.EXTRA_ID, NOTIFICATION_ID)
        }

        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (!NotificationHelper.canPostNotifications(this)) return

        // 动作 PendingIntent 必须**携带解析结果**，否则点「记一笔」拿不到金额。
        // contentIntent 与「记一笔」动作复用同一个 savePI。
        val savePI = PendingIntent.getBroadcast(
            this,
            1,
            saveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val ignorePI = PendingIntent.getBroadcast(
            this,
            2,
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(savePI)
                .addAction(0, "记一笔", savePI)
                .addAction(0, "忽略", ignorePI)
                .build(),
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "识别到支付通知时提示你一键记账" },
        )
    }

    companion object {
        const val TAG = "PaymentNotifyA11y"
        const val CHANNEL_ID = "payment_capture"
        const val CHANNEL_NAME = "自动记账"
        const val NOTIFICATION_ID = 9101

        /** 未命中时的补抓间隔：页面二次渲染（金额晚于标题出现）常见于支付宝/美团 */
        private const val RETRY_DELAY_MS = 400L

        /** 同一窗口解析结束后的冷却：WebView 事件风暴下避免「解析→移除桶→立刻再解析」 */
        private const val PARSE_COOLDOWN_MS = 700L

        /** 当前已连接的服务实例：截屏能力只能由活着的服务提供 */
        @Volatile
        private var current: PaymentNotifyAccessibilityService? = null

        /** 最近一次窗口切换事件里的前台包名（系统窗口 / 桌面 / 本 App 已在写入时过滤） */
        @Volatile
        private var lastForegroundPkg: String? = null

        /** 最近一次窗口切换事件的窗口类名：全自动记账的页面级预过滤用 */
        @Volatile
        private var lastForegroundClass: String? = null

        /** 无障碍服务是否已连接（连接了才具备截屏能力；未开启时不要假装能截屏） */
        fun isConnected(): Boolean = current != null

        /**
         * 已连接的服务实例，供确认悬浮卡使用 `TYPE_ACCESSIBILITY_OVERLAY`。
         *
         * 云闪付等银行类 App 用 `HIDE_OVERLAY_WINDOWS` 隐藏普通应用悬浮窗，
         * 无障碍 overlay 属于系统层窗口，不受这个 flag 影响。
         * 服务未连接时返回 null，调用方退回应用悬浮窗。
         */
        fun overlayHost(): PaymentNotifyAccessibilityService? = if (isConnected()) current else null

        /**
         * 给识屏用的前台包名兜底来源。服务断开后返回 null，
         * 避免拿一份不知道是多久以前的缓存去标注账单来源。
         */
        fun foregroundPackage(): String? = if (isConnected()) lastForegroundPkg else null

        /**
         * 截取当前屏幕。
         *
         * @return 位图；服务未连接、系统拒绝（截屏太频繁 / 无权限）时返回 null
         */
        suspend fun capture(): Bitmap? =
            withTimeoutOrNull(SCREENSHOT_TIMEOUT_MS) { current?.captureScreenOrNull() }

        /** 截屏回调兜底超时：系统不回调时不能让调用方一直挂着 */
        private const val SCREENSHOT_TIMEOUT_MS = 6_000L
    }
}

/**
 * 通知动作接收器：「记一笔」把待确认账单落盘并拉起 App；「忽略」关掉通知。
 *
 * 单独用 BroadcastReceiver 而不是在 Service 里处理，是因为 Service 可能已被系统销毁，
 * 而 Receiver 有独立生命周期。
 */
class PaymentActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SAVE -> {
                val amount = intent.getLongExtra(EXTRA_AMOUNT, 0L)
                if (amount <= 0L) return
                PaymentBillStore.put(
                    context,
                    type = intent.getStringExtra(EXTRA_TYPE) ?: "expense",
                    amountCents = amount,
                    merchant = intent.getStringExtra(EXTRA_MERCHANT).orEmpty(),
                    accountHint = intent.getStringExtra(EXTRA_ACCOUNT).orEmpty(),
                    sourceLabel = intent.getStringExtra(EXTRA_SOURCE).orEmpty(),
                )
                // 落盘后直接拉起并打开记账面板；进程被杀死时消费方从 SharedPreferences 取
                PaymentBillStore.consume(context)?.let { bill ->
                    PendingBillHolder.post(bill)
                }
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(MainActivity.EXTRA_OPEN_ADD_BILL, true)
                    },
                )
            }

            ACTION_IGNORE -> {
                intent.getIntExtra(EXTRA_ID, 0).takeIf { it != 0 }?.let { id ->
                    context.getSystemService(NotificationManager::class.java)?.cancel(id)
                }
            }
        }
    }

    companion object {
        const val ACTION_SAVE = "com.iji.app.action.SAVE_PAYMENT_BILL"
        const val ACTION_IGNORE = "com.iji.app.action.IGNORE_PAYMENT_BILL"
        const val EXTRA_ID = "notification_id"
        const val EXTRA_TYPE = "type"
        const val EXTRA_AMOUNT = "amount_cents"
        const val EXTRA_MERCHANT = "merchant"
        const val EXTRA_ACCOUNT = "account"
        const val EXTRA_SOURCE = "source"
    }
}