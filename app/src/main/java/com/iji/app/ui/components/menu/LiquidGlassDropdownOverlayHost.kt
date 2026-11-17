// 下拉面板 overlay 宿主：把 LiquidGlassDropdownMenu 挂到 Miuix Scaffold 的 popup 层。
// 面板用 drawBackdrop 采样 LocalAppBackdrop；放进录制树会自引用 → RenderNode 递归 SIGSEGV。
// 展开时在面板下方垫全屏 scrim 处理外点关闭；收起动画播完必须立刻卸载宿主，否则 popup 层吞掉页面点击。

package com.iji.app.ui.components.menu

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

internal const val DropdownExitMs = 340

@Composable
fun LiquidGlassDropdownOverlayHost(
    expanded: Boolean,
    onOutsideTap: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val visible = remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded) {
        if (expanded) {
            visible.value = true
        } else {
            delay(DropdownExitMs + 32L)
            visible.value = false
        }
    }
    if (!visible.value) return
    DialogLayout(
        visible = remember { mutableStateOf(true) },
        enableWindowDim = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        enableAutoLargeScreen = false,
        renderInRootScaffold = true,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (expanded && onOutsideTap != null) {
                val currentOnOutsideTap = rememberUpdatedState(onOutsideTap)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                currentOnOutsideTap.value.invoke()
                            }
                        },
                )
            }
            content()
        }
    }
}
