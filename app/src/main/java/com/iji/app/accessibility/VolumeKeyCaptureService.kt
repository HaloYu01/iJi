package com.iji.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.iji.app.MainActivity

/**
 * 「音量键快捷键」触发的极简无障碍服务。
 *
 * 机制：系统「无障碍 → 音量键快捷键」里选中本服务后，**同时按住音量上 + 下约 3 秒**，
 * 系统会启用本服务，[onServiceConnected] 随即被调用；处理完再 `disableSelf()` 回到
 * 未启用状态，下次按键才会再次启用（音量键快捷键是 toggle 语义，不能去掉自我禁用）。
 *
 * 修「按住音量加减不调用截屏」：旧版在 `finally` 里同步 `disableSelf()`，服务当场解绑，
 * 进程在 1.6s 倒计时里没有任何活动组件，可能被系统回收，进程级协程里的截屏根本没机会
 * 执行。现在只在**真的发起了截屏**时，等 [ScreenCaptureFlow.capturing] 落回 false 才解除
 * 绑定，整个倒计时 + 本地 OCR 期间服务都还在，进程不会被提前回收。
 * 没发起截屏的分支（开关关闭 / 无授权 / 被 `_capturing` 拒绝）不需要保活，立即退出，
 * 并通过 Toast + Log 说明原因，不再「按了完全没反应」。
 *
 * 不监听任何事件、不读界面内容；截屏由 Shizuku 或支付通知服务完成。
 */
class VolumeKeyCaptureService : AccessibilityService() {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "音量键快捷键已唤起服务")

        if (!CapturePreferences.volumeKeyEnabled(this)) {
            Log.d(TAG, "App 内「音量键快捷识屏」未开启，忽略本次触发")
            hint("请先在应用内开启「音量键快捷识屏」")
            disableSelf()
            return
        }

        val mode = CapturePreferences.resolve(this)
        if (mode == null) {
            Log.d(TAG, "Shizuku / 无障碍截屏都不可用")
            hint("请先授予 Shizuku 或开启无障碍截屏服务")
            openApp()
            disableSelf()
            return
        }

        val started = when (mode) {
            CapturePreferences.MODE_SHIZUKU ->
                ScreenCaptureFlow.startWithShizuku(applicationContext, VOLUME_DELAY_MS)
            else -> ScreenCaptureFlow.start(applicationContext, VOLUME_DELAY_MS)
        }
        if (!started) {
            Log.d(TAG, "截屏请求被拒绝（上一次识别未结束或授权已失效）")
            hint("识屏没能发起，请稍后重试或检查授权")
            openApp()
            disableSelf()
            return
        }

        Log.d(TAG, "已发起截屏识别，${VOLUME_DELAY_MS}ms 后取帧")
        // 截屏协程在进程级 ScreenCaptureFlow 里：服务一解绑，进程就只剩空组件，
        // 可能在倒计时 / OCR 结束前被系统回收。跟着 capturing 走，识别完再解除绑定。
        disableSelfWhenCaptureEnds()
    }

    /** 服务里没有 Snackbar/通知上下文，用最轻的 Toast 把「为什么没截屏」说清楚 */
    private fun hint(text: String) {
        runCatching { Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show() }
    }

    private fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * 识别进行中一直保持服务绑定，结束（或超时兜底）才 `disableSelf()`。
     * 轮询而非回调：识别链路在 [ScreenCaptureFlow] 的进程级协程里，不该为了一个
     * toggle 开关往通用识别流程里塞服务专属的回调。
     */
    private fun disableSelfWhenCaptureEnds(elapsedMs: Long = 0L) {
        if (!ScreenCaptureFlow.capturing.value || elapsedMs >= MAX_KEEP_ALIVE_MS) {
            Log.d(TAG, "识屏结束（累计 ${elapsedMs}ms），解除服务绑定")
            runCatching { disableSelf() }
            return
        }
        mainHandler.postDelayed(
            { disableSelfWhenCaptureEnds(elapsedMs + POLL_INTERVAL_MS) },
            POLL_INTERVAL_MS,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "VolumeKeyCapture"
        /** 等音量条淡出，避免截到系统音量面板 */
        const val VOLUME_DELAY_MS = 1_600L
        /** 轮询识别是否结束的间隔 */
        private const val POLL_INTERVAL_MS = 250L
        /** 兜底：识别异常卡住时也不能一直绑着，否则下次按住音量键只会「关掉」它 */
        private const val MAX_KEEP_ALIVE_MS = 30_000L
    }
}
