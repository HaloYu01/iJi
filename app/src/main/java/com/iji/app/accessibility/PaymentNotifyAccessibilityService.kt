package com.iji.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.iji.app.MainActivity
import com.iji.app.R
import com.iji.app.budget.NotificationHelper
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.ui.PendingBillHolder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (current === this) current = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 只监听「通知状态变化」：现代 SDK（API 35+）已不再有 TYPE_NOTIFICATION_TEXT_CHANGED，
        // 通知文本统一由该事件携带；getText() 也已上移到父类 AccessibilityRecord（继承调用仍可用）。
        if (event?.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
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

        /** 当前已连接的服务实例：截屏能力只能由活着的服务提供 */
        @Volatile
        private var current: PaymentNotifyAccessibilityService? = null

        /** 无障碍服务是否已连接（连接了才具备截屏能力；未开启时不要假装能截屏） */
        fun isConnected(): Boolean = current != null

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