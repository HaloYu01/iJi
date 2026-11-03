package com.iji.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.iji.app.data.repository.SettingsRepository

/**
 * 应用触感反馈全局开关（偏好设置中可切换，默认开启）。
 *
 * 用 Compose State 持有，读取处直接参与重组；写入同时落盘（DataStore）。
 */
object HapticsState {

    const val KEY_HAPTICS_ENABLED = "haptics_enabled"

    var enabled by mutableStateOf(true)
        private set

    /** 由 IjiApplication.onCreate 调用（runBlocking 读一次，之后全走内存） */
    fun init(context: Context) {
        enabled = SettingsRepository.getBoolean(context, KEY_HAPTICS_ENABLED, true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        SettingsRepository.setBoolean(context, KEY_HAPTICS_ENABLED, value)
    }
}

/**
 * 把真实触感实现包一层总开关：开启时透传，关闭时静默丢弃。
 * 在 IjiTheme 根部注入 LocalHapticFeedback，应用内全部触感调用点
 * （操作确认、长按、菜单选择等）无需逐个判断即可统一受控。
 */
internal class ToggleableHapticFeedback(
    private val delegate: HapticFeedback,
) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (HapticsState.enabled) {
            delegate.performHapticFeedback(hapticFeedbackType)
        }
    }
}
