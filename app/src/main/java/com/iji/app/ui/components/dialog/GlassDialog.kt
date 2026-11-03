// 弹窗外壳：复用官方 Miuix 的 DialogLayout 作为弹层宿主。
// 注意：不要直接引入 Miuix 源码副本，同名同包会与官方库冲突（DuplicateClasses）。

package com.iji.app.ui.components.dialog

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import com.iji.app.ui.components.topbar.dampedSpring
import com.iji.app.ui.util.PredictiveBackSettings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.DecelerateEasing
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

/**
 * 中央弹出弹窗壳（Miuix `OverlayDialog` 的自定义外观版）：
 * 40dp 连续曲率圆角、手机端上滑入场 / 大屏缩放入场、遮罩渐暗、预测性返回跟随、边缘光，
 * 背景为不透明主题底色。卡片布局见 [GlassDialogBody]。
 *
 * 注意：弹层经 Miuix 的 `DialogLayout` 渲染，宿主必须由 **Miuix `Scaffold`** 提供，
 * 即调用点必须位于 Miuix `Scaffold` 子树内，否则弹窗不渲染；同时组合根需提供
 * `LocalNavigationEventDispatcherOwner`（本工程已在 `MainActivity` 根提供）。
 *
 * @param show 是否显示。
 * @param onDismissRequest 请求关闭（点遮罩 / 系统返回）。
 * @param title 标题。
 * @param summary 摘要文案（标题下方的说明）。
 * @param backgroundColor 卡片底色，默认主题背景色。
 * @param content 弹窗内容。
 */
@Composable
fun GlassDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    backgroundColor: Color = GlassDialogDefaults.backgroundColor(),
    content: @Composable () -> Unit,
) {
    val animationProgress = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val dimProgress = remember { Animatable(0f) }
    val visible = remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLargeScreen = GlassDialogDefaults.isLargeScreen()

    LaunchedEffect(show) {
        val largeAtStart = isLargeScreen
        if (show) {
            visible.value = true
            launch { dimProgress.animateTo(1f, tween(340, easing = DecelerateEasing(1.5f))) }
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = if (largeAtStart) {
                    dampedSpring(damping = 0.9f, response = 0.3f)
                } else {
                    spring(dampingRatio = 0.88f, stiffness = 450f, visibilityThreshold = 0.0001f)
                },
            )
        } else {
            if (!visible.value) return@LaunchedEffect
            if (imeInsets.getBottom(density) > 0) keyboardController?.hide()
            launch { dimProgress.animateTo(0f, tween(340, easing = DecelerateEasing(1.5f))) }
            animationProgress.animateTo(0f, tween(durationMillis = 340, easing = DecelerateEasing(1.5f)))
            dimProgress.snapTo(0f)
            visible.value = false
        }
    }

    if (!show && !visible.value) return

    val scope = rememberCoroutineScope()
    val dimAlpha = remember { mutableFloatStateOf(1f) }
    val dialogHeightPx = remember { mutableIntStateOf(0) }
    val backProgress = remember { Animatable(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val windowInfo = LocalWindowInfo.current

    val requestDismiss: () -> Unit = remember { { currentOnDismiss() } }
    val resetGesture: suspend () -> Unit = remember {
        {
            backProgress.animateTo(0f, animationSpec = tween(150))
            animate(dimAlpha.floatValue, 1f, animationSpec = tween(150)) { value, _ ->
                dimAlpha.floatValue = value
            }
        }
    }

    val visibleState = remember { mutableStateOf(false) }
    visibleState.value = visible.value
    DialogLayout(
        visible = visibleState,
        enableWindowDim = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        enableAutoLargeScreen = false,
        renderInRootScaffold = true,
    ) {
        // 预测性返回跟手：拖动时光标随进度位移、遮罩同步变淡；关闭开关时只保留拦截关闭。
        //
        // 走 OnBackPressedDispatcher 统一管线（PredictiveBackHandler），与 OverlayBackHandler
        // 同管线、按组合顺序取得优先级（Miuix Scaffold 先组合 content 再组合 popup host，
        // 弹窗内的 handler 更晚注册、先响应）。不要换回 androidx.navigationevent 的
        // NavigationBackHandler：MainActivity 的根 dispatcher 是孤立创建的、从未挂
        // NavigationEventInput，挂在它上面收不到任何系统手势事件（进度永远不来，
        // 预测性返回动画整体失效——2026-10 实测）。
        PredictiveBackHandler(enabled = show) { progress ->
            try {
                progress.collect { event ->
                    if (PredictiveBackSettings.enabled) {
                        backProgress.snapTo(event.progress)
                        dimAlpha.floatValue = 1f - event.progress
                    }
                }
                // Flow 正常结束 = 手势提交 → 关闭
                requestDismiss()
            } catch (e: CancellationException) {
                // 手势取消 → 回弹复位
                scope.launch { resetGesture() }
            }
        }

        val dimColor = MiuixTheme.colorScheme.windowDimming
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(dimColor.copy(alpha = dimColor.alpha * 0.6f * dimAlpha.floatValue * dimProgress.value))
                },
        )

        GlassDialogBody(
            title = title,
            summary = summary,
            backgroundColor = backgroundColor,
            backProgress = backProgress,
            heightPx = dialogHeightPx,
            onDismissRequest = requestDismiss,
            modifier = modifier.graphicsLayer {
                val progress = animationProgress.value
                if (isLargeScreen) {
                    scaleX = 0.8f + 0.2f * progress
                    scaleY = scaleX
                    alpha = progress
                } else {
                    translationY = (1f - progress) * windowInfo.containerDpSize.height.toPx()
                }
            },
            content = {
                CompositionLocalProvider(LocalDismissState provides requestDismiss) { content() }
            },
        )
    }
}
