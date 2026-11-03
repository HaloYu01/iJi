package com.iji.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iji.app.data.repository.SettingsRepository

/**
 * 性能开关：**降低动态效果**——关闭液态玻璃的全屏采样与模糊/折射 shader。
 *
 * 为什么是手动开关而不是自动降级：
 * 自动判定（设备等级 / 低电量）很容易误伤——旗舰机也可能因为一次低电量提示
 * 突然"变丑"，用户无法预期。手动开关行为可预测，且用户能自己权衡观感与流畅度。
 *
 * 用 Compose State 持有，读取处直接参与重组；写入同时落盘（DataStore）。
 */
object PerfState {

    private const val KEY_REDUCED_EFFECTS = "perf_reduced_effects"

    var reducedEffects by mutableStateOf(false)
        private set

    /** 由 IjiApplication.onCreate 调用（runBlocking 读一次，之后全走内存） */
    fun init(context: Context) {
        reducedEffects = SettingsRepository.getBoolean(context, KEY_REDUCED_EFFECTS, false)
    }

    fun setReducedEffects(context: Context, value: Boolean) {
        reducedEffects = value
        SettingsRepository.setBoolean(context, KEY_REDUCED_EFFECTS, value)
    }
}