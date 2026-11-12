// 下拉面板 overlay 宿主：把 LiquidGlassDropdownMenu 挂到 Miuix Scaffold 的 popup 层。
//
// ⚠️ 为什么必须走 popup 层而不能放在页面布局里：面板用 drawBackdrop 采样
// LocalAppBackdrop（NavHost 的 layerBackdrop 录制层）。若面板节点本身在录制树内
// （NavHost 子树），录制时会把自己录进采样图 → RenderNode 树自引用，
// prepareTree 阶段无限递归 → RenderThread SIGSEGV（2026-11-07 实测）。
// 上游 NexioSchedule 把控件放在 chrome/overlay 层（录制树外），等价于本文件的 popup 层。
//
// 外点关闭：展开时在面板**下方**垫一层全屏手势层（scrim，无视觉）——点面板=面板手势
// 层消费，点面板外=scrim 消费并回调 onOutsideTap。收起态不挂 scrim，页面触摸完全不受影响。

package com.iji.app.ui.components.menu

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

/**
 * 常驻 popup 层覆盖层：children 直接渲染在 Scaffold popup host（页面内容之上、
 * 弹窗之下）。收起态的玻璃圆钮也常驻在这里（由 [show] 驱动 fraction 显隐）。
 *
 * @param show 控件可见性（收起态玻璃圆钮需要常驻绘制，调用方传 true）
 * @param expanded 面板是否展开；展开时挂全屏外点手势层（scrim），收起态不挂、
 *   页面触摸不受任何影响
 * @param onOutsideTap 点击面板外区域时回调（点外部关闭）
 */
@Composable
fun LiquidGlassDropdownOverlayHost(
    expanded: Boolean,
    onOutsideTap: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // DialogLayout 的 popup 宿主会拦截全屏触摸（实测：常驻时页面点不了任何东西），
    // 所以只在展开期间挂载；收起后保留 900ms 让收起弹簧 + 退出回弹脉冲（settleBounce
    // 已调成 ~400ms 落定）播完再卸载——卸载早于回弹收尾会把还在微动的面板整个抽掉
    //（视觉上「突然消失」，实测）。收起态玻璃圆钮由占位槽承担。
    val visible = remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded) {
        if (expanded) {
            visible.value = true
        } else {
            delay(900)
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
            // 外点 scrim：垫在面板下方（先组合），仅展开时存在。
            // 点面板内：面板手势层消费，scrim 收不到；点面板外：scrim 回调关闭。
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
