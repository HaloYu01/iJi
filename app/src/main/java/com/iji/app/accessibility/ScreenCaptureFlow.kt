package com.iji.app.accessibility

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import com.iji.app.ai.BillDraft
import com.iji.app.ai.ScreenshotBillParser
import com.iji.app.budget.NotificationHelper
import com.iji.app.capture.ForegroundAppResolver
import com.iji.app.data.db.DbProvider
import com.iji.app.data.repository.LedgerSelection
import com.iji.app.shizuku.ShizukuBridge
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.shizuku.ShizukuState
import com.iji.app.ui.Routes
import com.iji.app.ui.components.categoryNotificationBitmap
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.overlay.BillOverlay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「无障碍截屏记账」的执行链路。
 *
 * 为什么需要它（而不是在界面里直接截屏）：`AccessibilityService.takeScreenshot()` 截的是
 * **当前可见的那一屏**——用户点按钮时屏幕上正是本 App，截下来只会得到 iji 自己的界面。
 * 所以真实流程必须是：用户点按钮 → 本 App 退到后台 → 倒计时（给用户时间切到
 * 微信/支付宝的账单页）→ 服务截一帧 → 本地 OCR → 结果存进内存并**发一条通知**
 * 把用户带回来核对（识别发生在用户已经不在本 App 的时刻，不通知就无从知晓结果）。
 *
 * 线程与生命周期：跑在自己的 [scope] 上而不是界面的 `rememberCoroutineScope()`——
 * 界面退到后台后组合可能被销毁，挂在上面的协程会一并取消，截屏就永远不会发生。
 *
 * 结果只放内存（不落盘）：识别草稿必须由用户核对后才入账，进程被杀就当作没截过，
 * 不做「偷偷攒着等你下次打开」这种让人意外的行为。
 */
object ScreenCaptureFlow {

    /** 默认倒计时：够切到另一个 App 的账单页，又不至于让人干等 */
    const val DEFAULT_DELAY_MS = 3_000L

    /**
     * Shizuku 版：从磁贴/后台触发时人已经在账单页上了。
     *
     * 这个间隔就是「收起下拉栏 → 露出屏幕」的展示窗口：系统收栏动画约 300ms，
     * 留 500ms 总量（收栏 300 + 200ms 渲染余量），比旧版 900ms 少近一半干等——
     * 再短就会把半收起状态截进去，反而要重试。
     */
    const val SHIZUKU_DELAY_MS = 500L

    private const val TAG = "ScreenCaptureFlow"

    /** 岛按钮「确认记账」广播 PendingIntent 的请求码（与结果通知 9102 分开） */
    private const val SCREENSHOT_CONFIRM_REQUEST_CODE = 9103

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _drafts = MutableStateFlow<List<BillDraft>?>(null)

    /** 待核对的识别结果（未做去重；去重需要与账本内既有账单比对，由界面侧完成） */
    val drafts: StateFlow<List<BillDraft>?> = _drafts.asStateFlow()

    private val _capturing = MutableStateFlow(false)

    /** 是否已发出截屏请求（倒计时 + 识别中） */
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /**
     * 最近一次截屏结果的文案（成功 / 失败都写）。
     *
     * 通知是主要出口，但**没有通知权限时通知发不出去**——那时这条就是用户唯一的反馈，
     * 界面回来时用 Snackbar 补上，避免「点了按钮，什么都没发生」。
     */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /**
     * 发起一次截屏识别。
     *
     * 调用方（界面）负责在此之前把本 App 退到后台，否则截到的就是自己。
     * 无障碍服务未连接时直接返回 false，由界面提示「请先开启无障碍」——
     * 绝不在没有能力的情况下假装截了。
     *
     * 这条链路只服务**半自动**（用户主动触发）。全自动走的是
     * `AutoBillEngine` 的节点识别，不截屏、不经过这里。
     *
     * @return 是否成功发起（已有一个请求在跑、或服务未连接时返回 false）
     */
    fun start(context: Context, delayMillis: Long = DEFAULT_DELAY_MS): Boolean {
        if (!PaymentNotifyAccessibilityService.isConnected()) return false
        return launch(context, delayMillis) { PaymentNotifyAccessibilityService.capture() }
    }

    /**
     * Shizuku 版截屏识别：不需要无障碍服务，`screencap` 随时可截，所以倒计时可以短得多。
     *
     * [fromShade] 为 true 表示从通知栏磁贴触发：那一刻屏幕上是下拉面板，
     * 不先收起的话截到的就是通知栏本身，所以先 `cmd statusbar collapse` 再等一小会儿。
     */
    fun startWithShizuku(
        context: Context,
        delayMillis: Long = SHIZUKU_DELAY_MS,
        fromShade: Boolean = false,
    ): Boolean {
        if (ShizukuBridge.state(context) != ShizukuState.READY) return false
        return launch(context, delayMillis, fromShade) { ShizukuBridge.captureScreen(context) }
    }

    private fun launch(
        context: Context,
        delayMillis: Long,
        collapseShade: Boolean = false,
        capture: suspend () -> Bitmap?,
    ): Boolean {
        if (_capturing.value) return false

        val appContext = context.applicationContext
        _capturing.value = true
        scope.launch {
            // 磁贴触发时先收起下拉栏：岛（通知）在下拉栏盖着时看不见，先退出下拉栏
            // 露出屏幕，岛才能浮出来；非磁贴路径（应用内触发）没有栏可收，直接跳过。
            try {
                if (collapseShade) ShizukuBridge.exec(appContext, "cmd statusbar collapse")
            } catch (t: Throwable) {
                Log.w(TAG, "收起下拉栏失败，继续截屏", t)
            }
            // 从这一刻起用户就切到账单页了：界面上看不到任何东西，先上一条进行中的岛。
            // 返回 false 表示这台机器上岛不了（非小米 / 关了开关 / 没通知权限），
            // 后续就不再白刷文案。
            val islandShown = NotificationHelper.showScreenshotProgress(
                appContext,
                "准备识屏",
                "马上自动截屏，请把账单页露出来",
            )
            try {
                // 露出屏幕的展示窗口（收栏动画 + 渲染），到点即截
                delay(delayMillis)
                // 与截图贴在同一时刻取前台包名：倒计时结束后用户已切到账单页，
                // 晚了可能又切走，来源就对不上了。取不到一律降级为 null，识别照常走。
                val foregroundPkg = ForegroundAppResolver.current(appContext)
                val bmp = capture()
                if (bmp == null) {
                    report(appContext, "截屏失败", "没能取到屏幕画面（授权可能已失效，或系统拒绝），可改用相册选图")
                    return@launch
                }
                withContext(Dispatchers.Main) { BillOverlay.showLoading(appContext) }
                // 图拿到了，OCR/解析还要几秒：把岛刷成「正在识别」，别让用户以为卡住了
                if (islandShown) {
                    NotificationHelper.showScreenshotProgress(
                        appContext,
                        "正在识别",
                        "已截屏，正在分析账单",
                    )
                }
                val found = ScreenshotBillParser.recognizeDrafts(bmp, sourcePkg = foregroundPkg)
                // 能确认来源才在结果里说一句，认不出就不提，避免给用户错误暗示
                val sourceHint = ForegroundAppResolver.accountFor(foregroundPkg)
                    ?.let { " · 来自 $it" } ?: ""
                if (found.isEmpty()) {
                    report(appContext, "没识别出账单", "这一屏里没找到账单明细，请确认截的是账单列表页")
                    return@launch
                }
                // 先把数据库摸热：识屏进程常是新起的，首次点「记录」就不再等 Room 初始化
                withContext(Dispatchers.IO) { runCatching { DbProvider.database } }

                when (CapturePreferences.overlayStyle(appContext)) {
                    CapturePreferences.OVERLAY_ISLAND -> {
                        // 通知模式：不弹卡片（不可编辑），以超级岛**大岛**提醒；
                        // 展开卡带「确认记账」按钮，点击直接入账（BillConfirmReceiver），
                        // 点卡片本体仍回应用进导入页逐笔核对。
                        // forceIsland = true：用户明确选了这一种呈现方式，就按它发
                        // （以前这里借的是「记账提醒」的岛 id，会和当天的记账提醒互相顶掉）
                        _drafts.value = found
                        // 大岛布局按信息层级分配（保证可读性）：
                        // - 展开卡主体：逐笔明细，每行「类型 金额 分类 · 备注」；
                        //   备注截 8 字防换行溢出，最多 3 行，多出的折叠为「…共 N 笔」
                        // - 大岛头部 / 底栏：笔数摘要「共 N 笔 · 待确认」——
                        //   不再复读标题（旧版底栏又显示一遍「截屏识别完成」是纯浪费）
                        // 明细回应用后在导入页逐笔可查
                        val billDetail = found.take(3).joinToString("\n") { d ->
                            val cat = runCatching { CategoryCatalog.of(d.categoryId).name }
                                .getOrDefault("未分类")
                            val kind = if (d.type == TransactionType.EXPENSE) "支出" else "收入"
                            val note = d.note.take(8) + if (d.note.length > 8) "…" else ""
                            buildString {
                                append(kind).append(' ').append(formatMoney(d.amountCents))
                                append(' ').append(cat)
                                if (note.isNotBlank()) append(" · ").append(note)
                            }
                        } + if (found.size > 3) "\n…共 ${found.size} 笔" else ""
                        val billSummary = "共 ${found.size} 笔 · 待确认$sourceHint"
                        // 左侧大图标 = 当前识别到的类别（取第一笔的分类合成彩色圆底 + 白线素材）
                        val islandIcon = runCatching {
                            val category = CategoryCatalog.of(found.first().categoryId)
                            android.graphics.drawable.Icon.createWithBitmap(
                                categoryNotificationBitmap(
                                    appContext,
                                    category.name,
                                    category.id,
                                    // Category.color 存的是 Long（0xFF…），转回 ARGB Int
                                    category.color.toInt(),
                                ),
                            )
                        }.getOrNull()
                        val confirmPi = PendingIntent.getBroadcast(
                            appContext,
                            SCREENSHOT_CONFIRM_REQUEST_CODE,
                            Intent(appContext, BillConfirmReceiver::class.java).apply {
                                action = BillConfirmReceiver.ACTION_CONFIRM
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        report(
                            appContext,
                            "截屏识别完成",
                            "识别到 ${found.size} 笔账单，点「确认记账」直接入账",
                            forceIsland = true,
                            islandActionTitle = "确认记账",
                            islandAction = confirmPi,
                            islandDetail = billDetail,
                            islandSummary = billSummary,
                            islandIcon = islandIcon,
                            inAppNotice = false,
                        )
                    }
                    else -> {
                        // 悬浮窗模式：优先在卡片里就地确认（用户此刻就在账单页上）；
                        // 没有悬浮窗权限、或窗口加不上时，退回「草稿 + 通知」，绝不让结果凭空消失
                        // 用户可在应用内关掉悬浮窗确认（即使系统权限仍在）；关掉时回落到草稿 + 通知
                        val shownInOverlay = if (CapturePreferences.overlayConfirmEnabled(appContext)) {
                            withContext(Dispatchers.Main) {
                                // 记账偏好里指定了账本就用它，否则跟随当前账本
                                BillOverlay.show(
                                    appContext,
                                    found,
                                    AutoBookkeepingPrefs.effectiveLedger(
                                        setting = AutoBookkeepingPrefs.ledger(appContext),
                                        current = LedgerSelection.current.value,
                                    ),
                                    detailed = CapturePreferences.overlayStyle(appContext) ==
                                        CapturePreferences.OVERLAY_DETAILED,
                                )
                            }
                        } else {
                            false
                        }
                        if (!shownInOverlay) {
                            _drafts.value = found
                            report(
                                appContext,
                                "截屏识别完成$sourceHint",
                                "识别到 ${found.size} 笔账单，点击核对后再导入",
                                inAppNotice = false,
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "截屏识别失败", t)
                report(appContext, "截屏识别失败", t.message ?: "发生未知错误")
            } finally {
                withContext(Dispatchers.Main) { BillOverlay.dismissLoading() }
                _capturing.value = false
                // 进度岛必须先撤：结果通知（9102）走的是另一条 id，
                // 留着它就会变成「识别已完成」旁边还挂着一个「正在识别」
                NotificationHelper.clearScreenshotProgress(appContext)
            }
        }
        return true
    }

    /** 界面取走结果后清空（避免重组 / 旋转屏幕时重复弹出预览） */
    fun consume() {
        _drafts.value = null
    }

    /** 界面已用 Snackbar 呈现过结果文案 */
    fun consumeNotice() {
        _notice.value = null
    }

    private fun report(
        context: Context,
        title: String,
        body: String,
        forceIsland: Boolean = false,
        islandActionTitle: String? = null,
        islandAction: PendingIntent? = null,
        /** 大岛展开卡主体的逐笔明细（仅通知模式的大岛用；null = 用 [body]） */
        islandDetail: String? = null,
        /** 大岛头部 / 底栏的笔数摘要；null = 用 [hintTitle] 短标题 */
        islandSummary: String? = null,
        /** 大岛左侧大图标（当前识别到的类别图标）；null = 应用图标 */
        islandIcon: android.graphics.drawable.Icon? = null,
        inAppNotice: Boolean = true,
    ) {
        if (inAppNotice) _notice.value = "$title：$body"
        NotificationHelper.sendScreenshotNotice(
            context = context,
            title = title,
            body = body,
            route = Routes.SCREENSHOT_IMPORT,
            forceIsland = forceIsland,
            islandActionTitle = islandActionTitle,
            islandAction = islandAction,
            islandDetail = islandDetail,
            islandSummary = islandSummary,
            islandIcon = islandIcon,
        )
    }
}
