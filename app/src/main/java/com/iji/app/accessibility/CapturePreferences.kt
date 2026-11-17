package com.iji.app.accessibility

import android.content.Context
import com.iji.app.capture.ForegroundAppResolver
import com.iji.app.data.repository.SettingsRepository

/**
 * 识屏截图的授权方式与快捷触发开关。
 *
 * 两种真实能力：
 * - [MODE_SHIZUKU]：借 Shizuku 的 shell 身份 `screencap`，随时可截、无需无障碍；
 * - [MODE_A11Y]：无障碍服务截屏（`PaymentNotifyAccessibilityService.capture()`），
 *   需要该服务已连接（也就是用户开了「支付通知自动记账」那个无障碍服务）。
 *
 * [MODE_AUTO] 不猜：运行时优先 Shizuku，不可用才落到无障碍。两者都不可用时返回 null，
 * 界面据此显示「去授权」，绝不假装能截。
 */
object CapturePreferences {

    const val MODE_AUTO = "auto"
    const val MODE_SHIZUKU = "shizuku"
    const val MODE_A11Y = "a11y"

    const val KEY_MODE = "screen_capture_mode"
    const val KEY_VOLUME_KEY = "volume_key_capture"

    // ── 识别结果的呈现样式 ──
    /** 详细模式：悬浮卡片里类型 / 分类 / 金额 / 账户 / 备注 都能就地改 */
    const val OVERLAY_DETAILED = "overlay_detailed"
    /** 简洁模式：卡片只读，点一行补改备注 */
    const val OVERLAY_COMPACT = "overlay_compact"
    /** 通知模式：不弹卡片，以超级岛焦点通知提醒，点击回应用核对 */
    const val OVERLAY_ISLAND = "overlay_island"

    const val KEY_OVERLAY_STYLE = "screen_capture_overlay_style"

    /** 用户是否要用悬浮窗确认；与系统权限解耦，授予权限后依然可以关掉 */
    const val KEY_OVERLAY_CONFIRM = "screen_capture_overlay_confirm"
    /** 识屏倒计时（秒）：给用户留出切到账单页的时间 */
    const val KEY_CAPTURE_DELAY = "screen_capture_delay_seconds"

    /** 截屏识屏的工作流：半自动（默认，用户主动触发） / 全自动（切到支付 App 自动识别） */
    const val WORKFLOW_SEMI = "semi"
    const val WORKFLOW_AUTO = "auto"
    const val KEY_WORKFLOW = "capture_workflow"
    /**
     * 旧的全自动监听清单键，**只用于一次性迁移**。
     *
     * 新的读写入口是 `AutoListenPrefs`（键 `auto_listen_packages`），放在 `autorecord` 包：
     * 全自动的平台选择、延迟、去重策略都归它自己，半自动改设置不会波及全自动。
     */
    const val KEY_TRIGGER_APPS = "auto_capture_trigger_apps"

    /** 无障碍最近一次真的连上系统的时间（毫秒，0 = 从没连上过） */
    private const val KEY_A11Y_CONNECTED_AT = "a11y_last_connected_at"

    fun workflow(context: Context): String =
        SettingsRepository.getString(context, KEY_WORKFLOW, WORKFLOW_SEMI)

    fun setWorkflow(context: Context, value: String) {
        SettingsRepository.setString(context, KEY_WORKFLOW, value)
    }

    /**
     * 记下「无障碍确实连上过」。
     *
     * 用来区分两种「现在没连上」：从没开过（引导去开）vs 开过但被系统清理
     * （明确告诉用户发生了什么，并给一键恢复）。没有这个时间戳，页面只能猜。
     */
    fun markA11yConnected(context: Context) {
        SettingsRepository.setString(
            context,
            KEY_A11Y_CONNECTED_AT,
            System.currentTimeMillis().toString(),
        )
    }

    fun lastA11yConnectedAt(context: Context): Long =
        SettingsRepository.getString(context, KEY_A11Y_CONNECTED_AT, "0").toLongOrNull() ?: 0L

    // 执行记录已迁到 AutoRecordStore（全自动独立链路，不再经过截屏通道）

    val DELAY_OPTIONS_SECONDS = listOf(3, 5)

    fun mode(context: Context): String =
        SettingsRepository.getString(context, KEY_MODE, MODE_AUTO)

    fun setMode(context: Context, mode: String) {
        SettingsRepository.setString(context, KEY_MODE, mode)
    }

    /** 识别结果呈现样式，默认简洁（与旧版行为最接近） */
    fun overlayStyle(context: Context): String =
        SettingsRepository.getString(context, KEY_OVERLAY_STYLE, OVERLAY_COMPACT)

    fun setOverlayStyle(context: Context, style: String) {
        SettingsRepository.setString(context, KEY_OVERLAY_STYLE, style)
    }

    /** 默认 true：保持旧行为（有系统权限就弹悬浮窗）；用户可在应用内单独关掉 */
    fun overlayConfirmEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_OVERLAY_CONFIRM, true)

    fun setOverlayConfirmEnabled(context: Context, enabled: Boolean) {
        SettingsRepository.setBoolean(context, KEY_OVERLAY_CONFIRM, enabled)
    }

    fun captureDelaySeconds(context: Context): Int =
        SettingsRepository.getInt(context, KEY_CAPTURE_DELAY, 3)

    fun setCaptureDelaySeconds(context: Context, seconds: Int) {
        SettingsRepository.setInt(context, KEY_CAPTURE_DELAY, seconds)
    }

    /** 倒计时毫秒，钳在 1..10 秒防脏数据 */
    fun captureDelayMs(context: Context): Long =
        captureDelaySeconds(context).coerceIn(1, 10) * 1000L

    fun volumeKeyEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_VOLUME_KEY, false)

    fun setVolumeKeyEnabled(context: Context, enabled: Boolean) {
        SettingsRepository.setBoolean(context, KEY_VOLUME_KEY, enabled)
    }

    /**
     * 按偏好 + 当前可用性挑出真正能用的方式。
     *
     * @return [MODE_SHIZUKU] / [MODE_A11Y]；都不可用返回 null
     */
    fun resolveMode(mode: String, shizukuReady: Boolean, a11yConnected: Boolean): String? = when (mode) {
        MODE_SHIZUKU -> if (shizukuReady) MODE_SHIZUKU else null
        MODE_A11Y -> if (a11yConnected) MODE_A11Y else null
        // auto：优先 Shizuku（不受音量面板/授权弹窗影响），其次无障碍
        else -> when {
            shizukuReady -> MODE_SHIZUKU
            a11yConnected -> MODE_A11Y
            else -> null
        }
    }

    /** 当前设备上实际生效的方式；null = 两种授权都不可用 */
    fun resolve(context: Context): String? = resolveMode(
        mode = mode(context),
        shizukuReady = com.iji.app.shizuku.ShizukuBridge.state(context) ==
            com.iji.app.shizuku.ShizukuState.READY,
        a11yConnected = PaymentNotifyAccessibilityService.isConnected(),
    )
}
