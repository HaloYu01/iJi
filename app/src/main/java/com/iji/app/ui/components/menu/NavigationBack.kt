// 1:1 拷贝自 NexioSchedule miuix/.../utils/NavigationBack.kt（commonMain 类定义原样）。
// expect/actual 的 Android 实现改为本工程管线：上游用 androidx.navigationevent 的
// NavigationBackHandler + rememberNavigationEventState；本工程实测（2026-10，GlassDialog
// 注释在案）MainActivity 根 dispatcher 孤立、从未挂 NavigationEventInput，挂在它上面
// 收不到任何系统手势事件（进度永远不来）。因此这里走 PredictiveBackHandler
// （androidx.activity，GlassDialog / OverlayBackHandler 同管线、实测可用），
// 对外 API 与上游完全一致（isTransitioningBack / backProgress / handler）。
// LGDM 只消费 isTransitioningBack / backProgress / handler；edge 与触摸位移上游
// 用于缩放锚点与全屏跟随，本控件未用到，返回安全默认值。

package com.iji.app.ui.components.menu

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException

/** [NavigationBackState.handler] 的入参。用一个类打包，是为了能以**具名参数**调用。 */
class BackHandlerScope(
    val enabled: Boolean,
    val onBackCompleted: () -> Unit,
    val onBackCancelled: () -> Unit,
)

/**
 * 返回转场状态。
 *
 * ⚠ **[handler] 与本对象里的进度读取必须来自同一次 [rememberNavigationBack]。**
 * 拆成两个独立状态会让 handler 收到的手势进度不出现在另一份 state 上，跟手动画永远不动。
 */
class NavigationBackState(
    val isTransitioningBack: () -> Boolean,
    /** 手势进度 0f→1f；非手势期间为 null。 */
    val backProgress: () -> Float?,
    /** 手势起始边缘：0 = 左边缘，1 = 右边缘，2 = 非边缘滑动（返回键 / 无手势）。 */
    val backSwipeEdge: () -> Int,
    /** 手势开始至今的垂直触摸位移（px）；本实现未跟踪，恒 null。 */
    val backSwipeOffsetY: () -> Float?,
    /** 手势开始至今的水平触摸位移（px）；本实现未跟踪，恒 null。 */
    val backSwipeOffsetX: () -> Float?,
    private val onHandler: @Composable BackHandlerScope.() -> Unit,
) {
    /**
     * 注册系统返回处理器（与本 state 共用同一份底层状态）。
     *
     * @param enabled 是否启用
     * @param onBackCompleted 完整返回（键按下 / 手势完成）
     * @param onBackCancelled 返回手势中途取消
     */
    @Composable
    fun handler(
        enabled: Boolean,
        onBackCompleted: () -> Unit,
        onBackCancelled: () -> Unit,
    ) {
        onHandler(BackHandlerScope(enabled, onBackCompleted, onBackCancelled))
    }
}

/**
 * 取得返回转场状态与返回处理器，**同一份状态**。
 *
 * 用法：
 * ```
 * val back = rememberNavigationBack()
 * back.handler(enabled = show, onBackCompleted = { … }, onBackCancelled = { … })
 * val progress = back.backProgress()
 * ```
 */
@Composable
fun rememberNavigationBack(): NavigationBackState {
    val inProgress = remember { mutableStateOf(false) }
    val progress = remember { mutableFloatStateOf(0f) }
    // 完成回调从 handler 分支就地调用（PredictiveBackHandler 的 flow 正常结束 = 提交）
    var completed: (() -> Unit)? by remember { mutableStateOf(null) }
    var cancelled: (() -> Unit)? by remember { mutableStateOf(null) }

    // handler 的组合体：注册 PredictiveBackHandler 并喂同一份状态
    val onHandler: @Composable BackHandlerScope.() -> Unit = {
        val onCompletedLocal = onBackCompleted
        val onCancelledLocal = onBackCancelled
        completed = onCompletedLocal
        cancelled = onCancelledLocal
        PredictiveBackHandler(enabled = enabled) { backEvent ->
            try {
                backEvent.collect { event ->
                    inProgress.value = true
                    progress.floatValue = event.progress
                }
                // Flow 正常结束 = 手势提交
                inProgress.value = false
                progress.floatValue = 0f
                completed?.invoke()
            } catch (e: CancellationException) {
                // 手势取消 → 回弹复位（调用方在 onBackCancelled 里把 fraction 拉回 1f）
                inProgress.value = false
                progress.floatValue = 0f
                cancelled?.invoke()
            }
        }
    }

    return remember {
        NavigationBackState(
            isTransitioningBack = { inProgress.value },
            backProgress = { if (inProgress.value) progress.floatValue else null },
            backSwipeEdge = { 2 },
            backSwipeOffsetY = { null },
            backSwipeOffsetX = { null },
            onHandler = onHandler,
        )
    }
}
