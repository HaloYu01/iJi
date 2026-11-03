package com.iji.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iji.app.data.repository.SettingsRepository

/**
 * 全局字体大小。
 *
 * **此前 `font_size` 是个只写不读的键**：字体大小页把值写进 DataStore，但主题、
 * 排版、任何地方都没有消费它——滑到 24sp 保存后界面纹丝不动，是典型的"假开关"。
 *
 * 现在由主题根统一消费：`IjiTheme` 用 `LocalDensity(fontScale = sp / 16)` 提供，
 * 于是全 App 的 sp 文本（含 Miuix 组件内部）一起缩放，效果等同系统「字体大小」设置，
 * 且不必逐个改 `MaterialTheme.typography` 的十几个 slot。
 *
 * 用 Compose State 持有（与 [PerfState] 同一套做法）：读取处直接参与重组，
 * 写入同时落盘。
 */
object FontScaleState {

    const val KEY_FONT_SIZE = "font_size"

    /** 基准字号：**同时也是 fontScale = 1.0 的锚点**，改它等于改变"标准"的定义 */
    const val BASE_SP = 16f
    const val MIN_SP = 12f
    const val MAX_SP = 24f

    var fontSizeSp by mutableStateOf(BASE_SP)
        private set

    /** 相对基准的缩放比，供 `LocalDensity(fontScale = ...)` 使用 */
    val scale: Float get() = fontSizeSp / BASE_SP

    /** 由 IjiApplication.onCreate 调用（预热过缓存，这里不会阻塞主线程） */
    fun init(context: Context) {
        fontSizeSp = SettingsRepository.getFloat(context, KEY_FONT_SIZE, BASE_SP)
            .coerceIn(MIN_SP, MAX_SP)
    }

    fun setFontSize(context: Context, sp: Float) {
        val v = sp.coerceIn(MIN_SP, MAX_SP)
        fontSizeSp = v
        SettingsRepository.setFloat(context, KEY_FONT_SIZE, v)
    }

    /** 档位文案：偏好设置里的摘要显示它，让用户一眼看出当前是哪一档 */
    fun label(sp: Float = fontSizeSp): String = when {
        sp <= 13f -> "小"
        sp <= 17f -> "标准"
        sp <= 20f -> "大"
        else -> "特大"
    }
}
