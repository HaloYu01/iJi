package com.iji.app.ui.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iji.app.data.repository.SettingsRepository

/**
 * 预测性返回动画全局开关（应用偏好设置中可切换，默认开启）。
 *
 * 关闭后：各组件仍拦截返回（关闭行为不变），但手势进度不再驱动动画，
 * 返回时直接关闭、无手势跟随动画。
 *
 * 用 Compose State 持有，读取处直接参与重组；写入同时落盘（DataStore）。
 */
object PredictiveBackSettings {

    const val KEY_PREDICTIVE_BACK_ANIMATION = "predictive_back_animation_enabled"

    var enabled by mutableStateOf(true)
        private set

    /** 由 IjiApplication.onCreate 调用（runBlocking 读一次，之后全走内存） */
    fun init(context: Context) {
        enabled = SettingsRepository.getBoolean(context, KEY_PREDICTIVE_BACK_ANIMATION, true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        SettingsRepository.setBoolean(context, KEY_PREDICTIVE_BACK_ANIMATION, value)
    }
}
