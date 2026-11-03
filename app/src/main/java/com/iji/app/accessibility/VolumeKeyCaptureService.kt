package com.iji.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.iji.app.MainActivity

/**
 * 「音量键快捷键」触发的极简无障碍服务（参考 Hyper-pick-up-code 的做法）。
 *
 * 机制：系统「无障碍 → 音量键快捷键」里选中本服务后，按音量键时系统会**启动**它。
 * 我们在 [onServiceConnected] 里发起一次截屏识别，然后在 finally 里 `disableSelf()` 立刻退出——
 * 不常驻、不监听任何事件、不读取界面内容。
 *
 * 为什么不在已有服务里 `onKeyEvent`：那需要 `canRequestFilterKeyEvents`，
 * 等于让支付通知服务拿到所有按键事件，权限面明显变大；独立服务只在按键那一刻被唤起。
 *
 * 延迟 1.6s：按音量键时系统会先弹音量条，太早截图会把音量面板一起截进去。
 */
class VolumeKeyCaptureService : AccessibilityService() {

    override fun onServiceConnected() {
        try {
            if (!CapturePreferences.volumeKeyEnabled(this)) return
            val mode = CapturePreferences.resolve(this)
            if (mode == null) {
                // 两种授权都没有：打开应用让用户自己授权，不静默失败
                openApp()
                return
            }
            val started = when (mode) {
                CapturePreferences.MODE_SHIZUKU ->
                    ScreenCaptureFlow.startWithShizuku(this, VOLUME_DELAY_MS)
                else -> ScreenCaptureFlow.start(this, VOLUME_DELAY_MS)
            }
            if (!started) openApp()
        } finally {
            // 服务用完即走：下次按键系统会重新启动它
            disableSelf()
        }
    }

    private fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        /** 等音量条淡出，避免截到系统音量面板 */
        const val VOLUME_DELAY_MS = 1_600L
    }
}
