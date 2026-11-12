// 屏幕圆角：贴底弹窗的底部圆角要贴合设备屏幕圆角（圆角 = 屏幕圆角 − 弹窗外边距）。
//
// 1:1 移植自 NexioSchedule fork：LocalScreenCornerRadius 定义在它的 MiuixTheme.kt
// （本工程不整体移植 theme 包，落在本文件）；getRoundedCorner() 在参考仓库中来自其
// vendored 库未随源码可见，这里按 Android 平台惯例实现（读系统 rounded_corner_radius
// 资源，失败回退 0 —— 卡片侧再用 coerceAtLeast 兜底）。

package com.iji.app.ui.components.dialog

import android.content.res.Resources
import android.util.TypedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** App 可在根部注入已知的屏幕圆角；未注入（null）时卡片走 [getRoundedCorner] 探测。 */
val LocalScreenCornerRadius = staticCompositionLocalOf<Dp?> { null }

private val roundedCornerId: Int by lazy {
    runCatching { Resources.getSystem().getIdentifier("rounded_corner_radius", "dimen", "android") }
        .getOrDefault(0)
}

/** 读系统屏幕圆角半径；读不到（无该资源 / 异常）回退 0.dp。 */
fun getRoundedCorner(): Dp = runCatching {
    if (roundedCornerId == 0) return@runCatching 0.dp
    val res = Resources.getSystem()
    val tv = TypedValue()
    res.getValue(roundedCornerId, tv, true)
    val px = TypedValue.complexToDimensionPixelSize(tv.data, res.displayMetrics)
    Dp(px / res.displayMetrics.density)
}.getOrDefault(0.dp)
