package com.iji.app.budget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iji.app.MainActivity
import com.iji.app.R
import com.iji.app.data.model.BudgetAlertLevel
import com.iji.app.data.model.BudgetAlertTrigger
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.reminder.ReminderScheduler
import com.iji.app.reminder.SuperIslandNotifier
import com.iji.app.ui.Routes
import com.iji.app.ui.components.formatMoney

/**
 * 预算提醒通知。
 *
 * - 独立渠道 `budget_alert`（用户可单独开关，不影响其他通知）
 * - 点击打开「我的 → 预算」管理页（原预算详情页已按需求删除）
 * - Android 13+ 需要 POST_NOTIFICATIONS 权限，未授权时静默跳过（不打扰、不崩溃）
 */
object NotificationHelper {

    const val CHANNEL_ID = "budget_alert"
    private const val CHANNEL_NAME = "预算提醒"
    private const val CHANNEL_DESC = "预算接近超支或已超支时发送提醒"

    /** 记账提醒（独立渠道，用户可单独开关，不受预算提醒影响） */
    const val REMINDER_CHANNEL_ID = "record_reminder"
    private const val REMINDER_CHANNEL_NAME = "记账提醒"
    private const val REMINDER_CHANNEL_DESC = "当天还没有记账时提醒你补记"

    /**
     * 记账提醒实况渠道。实况渲染（Android 16 Live Updates / HyperOS 3 超级岛）
     * 对 ongoing 通知建议配高重要性渠道，故与普通提醒分渠道；
     * 渠道重要性创建后不可改，只能新开渠道 id。
     */
    const val REMINDER_LIVE_CHANNEL_ID = "record_reminder_live"
    private const val REMINDER_LIVE_CHANNEL_NAME = "记账提醒（实况）"
    private const val REMINDER_LIVE_CHANNEL_DESC = "以原生实况通知（或小米超级岛）样式显示的记账提醒"

    // ── 超级岛扩展通知（偏好设置 → 超级岛通知，三个独立开关）──
    // 开关打开且本机焦点协议 ≥ 3 时，对应通知走超级岛；否则回落普通通知。
    private const val KEY_ISLAND_BUDGET = "island_budget"
    private const val KEY_ISLAND_SCREENSHOT = "island_screenshot"
    private const val KEY_ISLAND_RECURRING = "island_recurring"

    /**
     * 识屏「过程态」上岛（进行中的「正在识别」），与上面的结果态分开：
     * 过程态是 10 秒级的临时卡片、结束时自己撤下，默认开（打扰成本低，且它是
     * 用户切到别的 App 之后唯一的进度反馈）；结果态是常驻通知，默认关。
     */
    private const val KEY_ISLAND_SCREENSHOT_PROGRESS = "island_screenshot_progress"

    /** 预算提醒超级岛渠道（焦点通知要求高重要性渠道，与普通渠道分开） */
    const val BUDGET_ISLAND_CHANNEL_ID = "budget_alert_island"
    private const val BUDGET_ISLAND_CHANNEL_NAME = "预算提醒（超级岛）"
    private const val BUDGET_ISLAND_CHANNEL_DESC = "预算接近超支或已超支时，以小米超级岛样式显示"
    private const val BUDGET_ISLAND_BUSINESS = "budget_alert"

    /** 截屏记账状态超级岛渠道 */
    const val SCREENSHOT_ISLAND_CHANNEL_ID = "screenshot_import_island"
    private const val SCREENSHOT_ISLAND_CHANNEL_NAME = "截屏记账（超级岛）"
    private const val SCREENSHOT_ISLAND_CHANNEL_DESC = "截屏识别的过程与结果，以小米超级岛样式显示"
    private const val SCREENSHOT_ISLAND_BUSINESS = "screenshot_import"

    /** 周期账单自动记账：普通渠道 + 超级岛渠道 */
    const val RECURRING_CHANNEL_ID = "recurring_auto"
    private const val RECURRING_CHANNEL_NAME = "周期账单自动记账"
    private const val RECURRING_CHANNEL_DESC = "周期账单按规则自动生成账单时通知你"
    const val RECURRING_ISLAND_CHANNEL_ID = "recurring_auto_island"
    private const val RECURRING_ISLAND_CHANNEL_NAME = "周期账单（超级岛）"
    private const val RECURRING_ISLAND_CHANNEL_DESC = "周期账单自动记账结果，以小米超级岛样式显示"
    private const val RECURRING_ISLAND_BUSINESS = "recurring_auto"

    private const val RECURRING_NOTIFICATION_ID = 9103

    fun isBudgetIslandEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_ISLAND_BUDGET, false)

    fun isScreenshotIslandEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_ISLAND_SCREENSHOT, false)

    fun isRecurringIslandEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_ISLAND_RECURRING, false)

    /** 识屏进行中是否上岛（默认开：只活一次识别的时间，结束即撤） */
    fun isScreenshotProgressIslandEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_ISLAND_SCREENSHOT_PROGRESS, true)

    fun setScreenshotProgressIslandEnabled(context: Context, enabled: Boolean) {
        SettingsRepository.setBoolean(context, KEY_ISLAND_SCREENSHOT_PROGRESS, enabled)
    }

    /** 截屏记账（无障碍截屏识别完成后，提示用户回到 App 核对结果） */
    const val SCREENSHOT_CHANNEL_ID = "screenshot_import"
    private const val SCREENSHOT_CHANNEL_NAME = "截屏记账"
    private const val SCREENSHOT_CHANNEL_DESC = "截屏识别完成后提示你回来核对导入"

    /** MainActivity 读取该 extra 后执行导航（见 AppRoot 的 LaunchedEffect） */
    const val EXTRA_NAV_ROUTE = "nav_route"

    /** 创建全部通知渠道（Android 8+；重复调用幂等） */
    fun createChannels(context: Context) {
        createChannel(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(REMINDER_LIVE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(REMINDER_LIVE_CHANNEL_ID, REMINDER_LIVE_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = REMINDER_LIVE_CHANNEL_DESC
                },
            )
        }
        // 实况功能改走 record_reminder_live 渠道；删掉旧渠道，避免系统设置里留下死项
        if (manager.getNotificationChannel(REMINDER_CHANNEL_ID) != null) {
            manager.deleteNotificationChannel(REMINDER_CHANNEL_ID)
        }
        if (manager.getNotificationChannel(SCREENSHOT_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(SCREENSHOT_CHANNEL_ID, SCREENSHOT_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = SCREENSHOT_CHANNEL_DESC
                },
            )
        }
        if (manager.getNotificationChannel(RECURRING_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(RECURRING_CHANNEL_ID, RECURRING_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = RECURRING_CHANNEL_DESC
                },
            )
        }
        // 超级岛渠道（HIGH）由 SuperIslandNotifier.ensureChannel 在发送时按需创建，这里不重复建
    }

    /** 创建通知渠道（Android 8+；重复调用幂等） */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESC
            },
        )
    }

    /** 是否有发通知的权限（Android 13+ 需 POST_NOTIFICATIONS） */
    fun canPostNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /**
     * 发送一条预算告警通知。
     *
     * @return 是否真的发出去了（无权限返回 false）
     */
    fun sendBudgetAlert(context: Context, trigger: BudgetAlertTrigger): Boolean {
        if (!canPostNotifications(context)) return false
        createChannel(context)

        val progress = trigger.progress
        val budget = progress.budget
        val scopeName = if (budget.categoryId.isEmpty()) "总预算" else CategoryCatalog.of(budget.categoryId).name

        val title: String
        val body: String
        when (trigger.level) {
            BudgetAlertLevel.NEAR_LIMIT -> {
                title = "$scopeName 预算即将超支"
                body = "已用 ${progress.percent}%（${formatMoney(progress.spentCents)} / ${formatMoney(progress.amountCents)}），" +
                    "剩余 ${formatMoney(progress.remainingCents)}"
            }
            BudgetAlertLevel.OVER_LIMIT -> {
                title = "$scopeName 预算已超支"
                body = "已用 ${progress.percent}%（${formatMoney(progress.spentCents)} / ${formatMoney(progress.amountCents)}），" +
                    "超出 ${formatMoney(-progress.remainingCents)}"
            }
        }

        val id = notificationId(budget.id, progress.period.key, trigger.level)

        // 超级岛开关打开且本机支持焦点通知时上岛；否则走下面的普通通知
        if (isBudgetIslandEnabled(context) && SuperIslandNotifier.islandLevel(context) >= 3) {
            SuperIslandNotifier.sendIsland(
                context,
                channelId = BUDGET_ISLAND_CHANNEL_ID,
                channelName = BUDGET_ISLAND_CHANNEL_NAME,
                channelDesc = BUDGET_ISLAND_CHANNEL_DESC,
                notificationId = id,
                businessTag = BUDGET_ISLAND_BUSINESS,
                title = title,
                content = body,
                hintTitle = title.take(8),
                clickIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_NAV_ROUTE, Routes.BUDGET)
                },
            )
            return true
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAV_ROUTE, Routes.BUDGET)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true) // 同一 id 重复更新时不再次响铃，避免同一周期反复打扰
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
        return true
    }

    /**
     * 发送「今天还没记账」提醒，样式按 [ReminderScheduler.currentStyle] 分发：
     * - 超级岛（[SuperIslandNotifier]）：焦点协议代数 ≥ 3（MIUI/HyperOS，兜底含岛特性
     *   检测）时走小米焦点通知，不满足时自动回落原生；
     * - 原生（[sendNative]）：Android 15+ 请求实时通知（Live Updates），
     *   不叠加任何 MIUI 焦点参数（两者互斥，详见 [sendNative] 注释）。
     *
     * @param title 主标题；测试发送时传别的文案复用同一条链路
     * @param testMode 测试发送传 true：岛通知用独立 id，避免与真实提醒的岛互相静默替换
     * @return [SuperIslandNotifier.SendResult]：sent = 是否真的发出；detail = 实际走了哪条路
     */
    fun sendRecordReminder(
        context: Context,
        title: String = "今天还没记账",
        content: String = "趁还记得，把今天的收支补上吧",
        testMode: Boolean = false,
    ): SuperIslandNotifier.SendResult {
        if (!canPostNotifications(context)) {
            return SuperIslandNotifier.SendResult(false, "无通知权限，请先在系统设置里允许通知")
        }
        val style = ReminderScheduler.currentStyle(context)
        if (style == ReminderScheduler.STYLE_ISLAND && SuperIslandNotifier.islandLevel(context) >= 3) {
            return SuperIslandNotifier.sendReminder(context, title, content, testMode)
        }
        if (style == ReminderScheduler.STYLE_ISLAND) {
            // 选了超级岛但本机读不到焦点通知能力（非 MIUI/HyperOS）：说清楚为什么回落，
            // 别让用户以为选了没生效
            val fallback = sendNative(context, title, content)
            return SuperIslandNotifier.SendResult(
                fallback.sent,
                "未检测到焦点通知能力（非 MIUI/HyperOS），已回落原生 · ${fallback.detail}",
            )
        }
        return sendNative(context, title, content)
    }

    /**
     * 原生通知路径：只走 Android 官方实时通知（Android 15+ promoted ongoing，
     * Android 16 再加 `setShortCriticalText` 给状态栏芯片）。
     *
     * **刻意不叠加 `miui.focus.param`**：焦点通知与 `PROMOTED_ONGOING` 是两条互斥的
     * 渲染路。HyperOS 3 只要看到焦点参数就按焦点通知接管（真机 dumpsys 显示
     * `focusType=PARAMS`），而原生样式历史遗留的是 protocol 1 模板（HyperOS 3 不认），
     * 结果两头落空：既不上岛，也没有实时通知——这正是「切到原生后实时通知不触发」的原因。
     * 要岛请选「小米超级岛」样式，两条路各管一段，不再互相打架。
     */
    private fun sendNative(context: Context, title: String, content: String): SuperIslandNotifier.SendResult {
        createChannels(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REMINDER_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
        )
        val builder = Notification.Builder(context, REMINDER_LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            // ongoing 与 autoCancel 语义互斥：实况通知由发送方/系统撤下，点掉不该消失
            .setAutoCancel(false)
        val liveRequested = Build.VERSION.SDK_INT >= 35
        if (liveRequested) {
            builder.setOngoing(true)
            builder.addExtras(
                android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) },
            )
            if (Build.VERSION.SDK_INT >= 36) {
                // Android 16 状态栏芯片只认这个专用短文案，不认 contentTitle/contentText
                runCatching { builder.setShortCriticalText("记账提醒") }
            }
        }

        val notification = builder.build()
        val promotedPermGranted = if (Build.VERSION.SDK_INT >= 36) {
            runCatching {
                context.checkSelfPermission("android.permission.POST_PROMOTED_NOTIFICATIONS") ==
                    PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        } else {
            false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return SuperIslandNotifier.SendResult(
                false,
                buildNativeDetail(liveRequested, promotedPermGranted, "无通知服务"),
            )
        return try {
            manager.notify(REMINDER_NOTIFICATION_ID, notification)
            SuperIslandNotifier.SendResult(
                true,
                buildNativeDetail(liveRequested, promotedPermGranted, null),
            )
        } catch (t: Throwable) {
            Log.w("NotificationHelper", "原生实时通知发送失败", t)
            SuperIslandNotifier.SendResult(
                false,
                buildNativeDetail(liveRequested, promotedPermGranted, t.message ?: t.javaClass.simpleName),
            )
        }
    }

    /** sendNative 的诊断串：只报实况请求与 promoted 权限（原生路径不涉及岛参数） */
    private fun buildNativeDetail(
        liveRequested: Boolean,
        promotedPermGranted: Boolean,
        failure: String?,
    ): String = buildString {
        append(if (liveRequested) "实况请求=已附加" else "实况=不支持（Android < 15）")
        if (promotedPermGranted || Build.VERSION.SDK_INT >= 36) {
            append(" · promoted权限=${if (promotedPermGranted) "有" else "无"}")
        }
        failure?.let { append(" · 失败:$it") }
    }

    private const val REMINDER_NOTIFICATION_ID = 9001

    private const val SCREENSHOT_NOTIFICATION_ID = 9102

    /** 识屏进行中的岛：与结果通知分开，收尾时整条撤下不动结果 */
    private const val SCREENSHOT_PROGRESS_ID = 9104

    /** 识别中岛的存活上限（秒）；异常时岛自己过期，不会永久挂在状态栏 */
    private const val SCREENSHOT_PROGRESS_TIMEOUT_SECONDS = 120

    /**
     * 识屏**进行中**状态上岛：把「正在识屏 / 正在识别」发布成一条进行中的超级岛。
     *
     * 为什么必须有它：截屏识别发生在用户已经切到微信/支付宝的时刻，界面上什么都不显示，
     * 从点下到出结果之间是一段没有任何反馈的黑洞（无障碍倒计时 3s + OCR 数秒）。
     * 岛是这段时间里唯一能看见的进度，识别结束由 [clearScreenshotProgress] 撤下。
     *
     * 只有**真的发出去了**才返回 true：调用方据此决定要不要刷文案，
     * 非小米机型 / 关了开关 / 没有通知权限时静默返回 false —— 识屏本身不依赖它。
     *
     * @param title 主标题（岛卡片大字），如「正在识别」
     * @param content 正文（岛卡片小字），说明此刻在做什么
     */
    fun showScreenshotProgress(context: Context, title: String, content: String): Boolean {
        if (!isScreenshotProgressIslandEnabled(context)) return false
        if (!canPostNotifications(context)) return false
        // 非小米 / 读不到焦点能力时不上岛：普通通知在这里只是噪音（结果通知另有一条）
        if (SuperIslandNotifier.islandLevel(context) < 3) return false
        return SuperIslandNotifier.sendIsland(
            context,
            channelId = SCREENSHOT_ISLAND_CHANNEL_ID,
            channelName = SCREENSHOT_ISLAND_CHANNEL_NAME,
            channelDesc = SCREENSHOT_ISLAND_CHANNEL_DESC,
            // 与结果岛（9102）分开：过程态结束要整条撤下，不能把结果一起带走
            notificationId = SCREENSHOT_PROGRESS_ID,
            businessTag = SCREENSHOT_ISLAND_BUSINESS,
            title = title,
            content = content,
            hintTitle = title.take(8),
            clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAV_ROUTE, Routes.SCREENSHOT_IMPORT)
            },
            ongoing = true,
            // 识别一般 10 秒内结束；给到 2 分钟覆盖 OCR/AI 慢的情况，
            // 真出异常（进程被杀）时岛也会自己过期，不会永久占着状态栏
            islandTimeoutSeconds = SCREENSHOT_PROGRESS_TIMEOUT_SECONDS,
        ).sent
    }

    /** 撤下识屏进行中的岛（幂等；无论上岛成功与否都该调用，避免状态残留） */
    fun clearScreenshotProgress(context: Context) {
        SuperIslandNotifier.cancel(context, SCREENSHOT_PROGRESS_ID)
    }

    /**
     * 无障碍截屏结果提醒。
     *
     * 截屏发生在用户已经切到支付 App 的时刻，识别结果没法当场告诉他，
     * 所以用一条通知把结果与「回来核对」的入口一起给出（点击直达 [route] 指定的页面）。
     * 成功与失败都走这一条，避免「点了没反应、也不知道成没成」。
     *
     * @param route 目标路由，需与 AppRoot 注册的 route 一致（截屏导入页）
     * @param forceIsland 用户在识屏设置里明确选了「通知（超级岛）」呈现结果时传 true：
     *   此时不再看「截屏记账状态上岛」开关，否则「选了通知模式却拿到普通通知」会让人莫名其妙
     * @param islandActionTitle 岛/通知上的动作按钮文案（如「确认记账」），
     *   与 [islandAction] 成对提供才生效；超级岛与普通通知回落路径都会带上按钮
     * @param islandAction 动作按钮的 PendingIntent（广播：点按钮直接入账，不回应用）
     * @return 是否真的发出去了（无通知权限返回 false）
     */
    fun sendScreenshotNotice(
        context: Context,
        title: String,
        body: String,
        route: String,
        forceIsland: Boolean = false,
        islandActionTitle: String? = null,
        islandAction: PendingIntent? = null,
        /** 大岛展开卡主体的逐笔明细；null = 用 [body] */
        islandDetail: String? = null,
        /** 大岛头部 / 底栏的笔数摘要；null = 用标题前 8 字 */
        islandSummary: String? = null,
        /** 大岛左侧大图标（当前识别到的类别图标）；null = 应用图标 */
        islandIcon: android.graphics.drawable.Icon? = null,
    ): Boolean {
        if (!canPostNotifications(context)) return false

        // 超级岛开关打开（或显式要求）且本机支持焦点通知时上岛；否则走下面的普通通知
        val islandAllowed = forceIsland || isScreenshotIslandEnabled(context)
        if (islandAllowed && SuperIslandNotifier.islandLevel(context) >= 3) {
            return SuperIslandNotifier.sendIsland(
                context,
                channelId = SCREENSHOT_ISLAND_CHANNEL_ID,
                channelName = SCREENSHOT_ISLAND_CHANNEL_NAME,
                channelDesc = SCREENSHOT_ISLAND_CHANNEL_DESC,
                notificationId = SCREENSHOT_NOTIFICATION_ID,
                businessTag = SCREENSHOT_ISLAND_BUSINESS,
                title = title,
                content = body,
                hintTitle = title.take(8),
                clickIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_NAV_ROUTE, route)
                },
                islandActionTitle = islandActionTitle,
                islandAction = islandAction,
                // 识屏结果用大岛样式（官方模板 16 展开卡）：识别到几笔、状态一眼可读
                largeIsland = true,
                islandDetail = islandDetail,
                islandSummary = islandSummary,
                islandIcon = islandIcon,
                // 岛的存活时长对齐 Maling-Island 的 120s：结果卡不宜挂一小时，
                // 过时只收岛，通知仍在通知栏里（点开进导入页的路不丢）
                islandTimeoutSeconds = 120,
            ).sent
        }

        createChannels(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            SCREENSHOT_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAV_ROUTE, route)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
        )
        val builder = NotificationCompat.Builder(context, SCREENSHOT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        // 普通通知同样带动作按钮：岛不可用时「直接确认」这条路不能断
        if (islandAction != null && islandActionTitle != null) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    androidx.core.graphics.drawable.IconCompat.createWithResource(
                        context,
                        R.drawable.ic_notification,
                    ),
                    islandActionTitle,
                    islandAction,
                ).build(),
            )
        }
        runCatching { NotificationManagerCompat.from(context).notify(SCREENSHOT_NOTIFICATION_ID, builder.build()) }
        return true
    }

    /** 撤下截屏结果通知/岛（岛按钮「确认记账」入账后由接收器调用） */
    fun cancelScreenshotNotice(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(SCREENSHOT_NOTIFICATION_ID) }
    }

    /**
     * 周期账单自动记账结果提醒（此前自动记账完全静默，用户不知道规则执行了没有）。
     * 超级岛开关打开且本机支持焦点通知时上岛，点击进周期账单管理页；否则普通通知。
     *
     * @return 是否真的发出（无通知权限返回 false）
     */
    fun sendRecurringNotice(context: Context, title: String, body: String): Boolean {
        if (!canPostNotifications(context)) return false

        if (isRecurringIslandEnabled(context) && SuperIslandNotifier.islandLevel(context) >= 3) {
            return SuperIslandNotifier.sendIsland(
                context,
                channelId = RECURRING_ISLAND_CHANNEL_ID,
                channelName = RECURRING_ISLAND_CHANNEL_NAME,
                channelDesc = RECURRING_ISLAND_CHANNEL_DESC,
                notificationId = RECURRING_NOTIFICATION_ID,
                businessTag = RECURRING_ISLAND_BUSINESS,
                title = title,
                content = body,
                hintTitle = title.take(8),
                clickIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_NAV_ROUTE, Routes.RECURRING_MANAGE)
                },
            ).sent
        }

        createChannels(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            RECURRING_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAV_ROUTE, Routes.RECURRING_MANAGE)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
        )
        val notification = NotificationCompat.Builder(context, RECURRING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(RECURRING_NOTIFICATION_ID, notification) }
        return true
    }

    /**
     * 通知 id：按「预算 + 周期 + 级别」稳定派生。
     * 同一组合复用同一条通知（更新内容而不堆叠），不同组合互不影响。
     */
    private fun notificationId(
        budgetId: Long,
        periodKey: String,
        level: BudgetAlertLevel,
    ): Int = ("$budgetId|$periodKey|${level.name}").hashCode() and 0x7FFF_FFFF
}
