// 弹窗卡片本体：统一圆角、边缘光、安全区与输入法避让。

package com.iji.app.ui.components.dialog

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.captionBarPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.iji.app.ui.effects.edgelight.edgeLight
import com.iji.app.ui.effects.edgelight.rememberDefaultEdgeLight
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 弹窗卡片本体。
 *
 * 手机端贴底（居中观感不佳）、大屏居中并限宽限高；点遮罩关闭；预测性返回时随
 * [backProgress] 下移/缩小。
 *
 * @param backProgress 预测性返回进度（0 复位、1 完全退出）。
 * @param heightPx 回填卡片实测高度，用于计算手机端退出位移量。
 */
@Suppress("ktlint:compose:modifier-not-used-at-root")
@Composable
internal fun GlassDialogBody(
    title: String?,
    summary: String?,
    backgroundColor: Color,
    backProgress: Animatable<Float, *>,
    heightPx: MutableIntState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val isLargeScreen = GlassDialogDefaults.isLargeScreen()
    val outsideMargin = GlassDialogDefaults.outsideMargin
    val insideMargin = GlassDialogDefaults.insideMargin
    val shape = ContinuousRoundedRectangle(GlassDialogDefaults.cornerRadius)
    val contentAlignment = remember(isLargeScreen) {
        if (isLargeScreen) Alignment.Center else Alignment.BottomCenter
    }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)

    // 弹层宿主在键盘弹出时会把整块弹层上移（容器 top 变负）。记录容器位置做反向补偿，
    // 保证弹窗始终贴在键盘上方；键盘收起时 top 归零，补偿自动失效。
    val boxTopPx = remember { mutableFloatStateOf(0f) }

    val topPadding = maxOf(
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        WindowInsets.captionBar.asPaddingValues().calculateTopPadding(),
        WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
    )
    val bottomPadding = if (isLargeScreen) {
        0.dp
    } else {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
    }
    val extraBottomPaddingPx = remember(density, bottomPadding, outsideMargin, isLargeScreen) {
        if (isLargeScreen) 0f else with(density) { (bottomPadding + outsideMargin.height).toPx() }
    }

    val contentModifier = modifier
        .widthIn(max = GlassDialogDefaults.MaxWidth)
        .heightIn(max = if (isLargeScreen) windowHeight * (2f / 3f) else Dp.Unspecified)
        .onGloballyPositioned { heightPx.intValue = it.size.height }
        .graphicsLayer {
            val hostShift = boxTopPx.floatValue
            if (isLargeScreen) {
                scaleX = 1f - backProgress.value * 0.2f
                scaleY = scaleX
                translationY = -hostShift
            } else {
                val cardHeight = heightPx.intValue.takeIf { it > 0 }?.toFloat() ?: 500f
                translationY = backProgress.value * (cardHeight + extraBottomPaddingPx) - hostShift
            }
        }
        // 消费卡片内的点击，避免穿透到遮罩把弹窗关掉
        .pointerInput(Unit) { detectTapGestures { } }
        .clip(shape)
        .edgeLight(shape = shape, edgeLight = rememberDefaultEdgeLight())
        .background(color = backgroundColor, shape = shape)
        .padding(horizontal = insideMargin.width, vertical = insideMargin.height)

    Box(
        modifier = Modifier
            // imePadding：键盘弹出时弹窗整体位于键盘之上，输入类弹窗不会出现"被顶上去"的跳动
            .imePadding()
            .navigationBarsPadding()
            .captionBarPadding()
            .fillMaxSize()
            .onGloballyPositioned { boxTopPx.floatValue = it.localToWindow(Offset.Zero).y }
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnDismiss() }) }
            .semantics {
                onClick(label = "Dismiss") {
                    currentOnDismiss()
                    true
                }
            }
            .padding(horizontal = outsideMargin.width)
            .padding(top = topPadding, bottom = outsideMargin.height),
    ) {
        Column(modifier = contentModifier.align(contentAlignment)) {
            title?.let {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    text = it,
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            }
            summary?.let {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    text = it,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
            content()
        }
    }
}

object GlassDialogDefaults {
    val MaxWidth = 420.dp
    val cornerRadius = 40.dp
    val outsideMargin = DpSize(12.dp, 12.dp)
    val insideMargin = DpSize(24.dp, 24.dp)

    @Composable
    internal fun isLargeScreen(): Boolean {
        val windowInfo = LocalWindowInfo.current
        val windowWidth = windowInfo.containerDpSize.width
        val windowHeight = windowInfo.containerDpSize.height
        return windowHeight >= 480.dp && windowWidth >= 840.dp
    }

    @Composable
    fun backgroundColor() = MiuixTheme.colorScheme.background
}
