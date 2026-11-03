// 从课程表项目 OverScroll.kt 精简而来：
// 仅保留 CollapsibleTopBar 需要的 OverScrollState + LocalOverScrollState，
// 去掉了依赖 miuix jar（top.yukonga.miuix.kmp.utils.Platform）的 overScroll modifier 扩展，
// 让本包脱离 miuix jar 也能编译。
package com.iji.app.ui.util

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 超出滚动状态，暴露 offset 供顶栏检测「内容已滚动」以显示阴影/渐变遮罩。
 */
@Stable
class OverScrollState {
    var isOverScrollActive by mutableStateOf(false)
        internal set

    /** 超出滚动偏移量：0 = 在顶部，非 0 = 内容已滚动或被回弹 */
    var offset by mutableFloatStateOf(0f)
        internal set
}

val LocalOverScrollState = compositionLocalOf { OverScrollState() }
