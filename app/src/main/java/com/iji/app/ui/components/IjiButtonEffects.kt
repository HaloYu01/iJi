package com.iji.app.ui.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.yukonga.miuix.kmp.utils.SinkFeedback

/**
 * App「主要按钮」的统一视觉反馈（水波纹 / 按压缩放 / 边缘光晕）的**可调参数**。
 *
 * 生效方式见 [IjiButtonFx.PressIndication] 与 [Modifier.ijiButtonFocusRing]。
 */
object IjiButtonFx {

    // ── ① 按压水波纹（按压遮罩）─────────────────────────────
    /**
     * 水波纹（按压遮罩）颜色。仅取 RGB，按下时按内部固定 alpha 叠加
     * （PRESS 0.10 / HOVER 0.06 / FOCUS 0.08 / HOLD_DOWN 0.10，由 MiuixIndication 决定）。
     * - 改颜色：直接换这里的 Color（浅色底用 Black，深色底可用 White）。
     * - 改浓淡：alpha 不由这里控制，需改 MiuixIndication 内部常量（不推荐动 vendored 源码）。
     */
    val RippleColor: Color = Color.Black

    // ── ② 按压缩放 ────────────────────────────────────────
    /** 按下时缩放到原尺寸的比例（0.94f = 缩到 94%；1f = 不缩放） */
    const val SinkAmount: Float = 0.94f

    /** 缩放弹簧：(damping, stiffness)，damping 越小回弹越明显 */
    val SinkSpec: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 600f)

    // ── ③ 聚焦描边（可访问性）─────────────────────────────
    /**
     * 键盘 / 开关控制（TalkBack）聚焦时描边的宽度。
     * 颜色固定取 MiuixTheme.colorScheme.primary，不在此处配置。
     */
    val FocusRingWidth: Dp = 2.dp

    /**
     * 统一的按压反馈 = 水波纹([MiuixIndication]) + 按压缩放([SinkFeedback])。
     *
     * **为什么需要合并**：Miuix 的 `Button`/`IconButton`/`TextButton` 只有一个 `indication` 槽，
     * 而水波纹与按压缩放都属于 `Indication`，没法各挂一个。两者都是 `IndicationNodeFactory`，
     * 因此这里用 `DelegatingNode` 把它们的节点合并成一个，一个槽同时生效。
     *
     * 由 `IjiTheme` 注入到 `LocalIndication`，所有 Miuix 按钮自动生效。
     */
    val PressIndication: IndicationNodeFactory
        get() = IjiPressIndication(
            color = RippleColor,
            sinkAmount = SinkAmount,
            sinkSpec = SinkSpec,
        )
}

/** 合并水波纹与按压缩放的 Indication（参数见 [IjiButtonFx]） */
@Immutable
class IjiPressIndication(
    private val color: Color = IjiButtonFx.RippleColor,
    private val sinkAmount: Float = IjiButtonFx.SinkAmount,
    private val sinkSpec: SpringSpec<Float> = IjiButtonFx.SinkSpec,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        IjiPressNode(interactionSource, color, sinkAmount, sinkSpec)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is IjiPressIndication &&
                    color == other.color &&
                    sinkAmount == other.sinkAmount
                )

    override fun hashCode(): Int = 31 * color.hashCode() + sinkAmount.hashCode()
}

private class IjiPressNode(
    interactionSource: InteractionSource,
    color: Color,
    sinkAmount: Float,
    sinkSpec: SpringSpec<Float>,
) : DelegatingNode() {
    init {
        // 按压缩放（LayoutModifierNode + placeWithLayer）
        delegate(SinkFeedback(sinkAmount = sinkAmount, animationSpec = sinkSpec).create(interactionSource))
        // 按压水波纹 / 遮罩（DrawModifierNode）
        delegate(MiuixIndication(color = color).create(interactionSource))
    }
}

/**
 * 主要按钮的**聚焦描边**：仅在键盘 / 开关控制（TalkBack）等焦点导航时才绘制一圈主色描边，
 * 用来替代系统默认轮廓、保证可访问性。
 *
 * **常驻状态不绘制任何东西**：此前用的是常驻 edgeLight 白色光晕，在白色系按钮上会显示为
 * 一圈「小白圈」，与按钮底色几乎同色、观感像脏边，因此已移除。
 * 触摸交互的反馈由 [IjiButtonFx.PressIndication]（水波纹 + 按压缩放）提供，不受影响。
 *
 * 用法：`Button(onClick = {...}, modifier = Modifier.ijiButtonFocusRing(RoundedCornerShape(12.dp)))`
 * 注意：shape 必须与按钮实际形状一致，否则描边对不上；按钮背景色不受此影响。
 */
@Composable
fun Modifier.ijiButtonFocusRing(shape: Shape): Modifier {
    val ringColor = MiuixTheme.colorScheme.primary
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (focused) {
                val stroke = Stroke(width = IjiButtonFx.FocusRingWidth.toPx())
                val inset = stroke.width / 2f
                val outline = shape.createOutline(
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    layoutDirection = layoutDirection,
                    density = this,
                )
                translate(inset, inset) {
                    val path = Path().apply {
                        when (outline) {
                            is Outline.Rectangle -> addRect(outline.rect)
                            is Outline.Rounded -> addRoundRect(outline.roundRect)
                            is Outline.Generic -> addPath(outline.path)
                        }
                    }
                    drawPath(path = path, color = ringColor, style = stroke)
                }
            }
        }
}

/**
 * 小图标按钮（IconButton）的**圆形**聚焦描边：等价于 [ijiButtonFocusRing] 传 [CircleShape]。
 * 同样只在聚焦时出现，常驻无描边。
 */
@Composable
fun Modifier.ijiIconFocusRing(): Modifier = this.ijiButtonFocusRing(shape = CircleShape)
