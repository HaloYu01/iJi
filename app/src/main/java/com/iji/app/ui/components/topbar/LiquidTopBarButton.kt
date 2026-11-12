package com.iji.app.ui.components.topbar

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.iji.app.ui.PerfState
import com.iji.app.ui.components.ijiIconFocusRing
import com.iji.app.ui.effects.edgelight.edgeLight
import com.iji.app.ui.effects.edgelight.rememberDefaultEdgeLight
import com.iji.app.ui.effects.glass.InteractiveHighlight
import com.iji.app.ui.theme.minTouchTarget
import com.iji.app.ui.util.isAppDarkTheme
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.IconButton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 顶栏玻璃圆钮的直径（[LiquidTopBarButton] 的默认 [LiquidTopBarButton.buttonHeight]）。
 *
 * 所有顶栏按钮**以及包裹它的容器**都必须用它：外层一旦写更小的固定尺寸，Compose 会把
 * 44dp 的圆压到那个上限（首页账本按钮曾套在 `Modifier.size(40.dp)` 的弹层触发器里，
 * 于是左边那颗圆比右侧搜索/我的小一圈，肉眼看「边缘大小不统一」）。
 */
val LiquidTopBarButtonSize = 44.dp

/**
 * 顶栏液态玻璃圆形按钮（参考 NexioSchedule 的 LiquidTopBarButton）。
 *
 * ## 特效何时出现（与顶栏模糊同步）
 * [backdropAlpha] 由 [IjiTopBar] 的 startAction/endAction 槽位传入：内容上滑超过 10dp、
 * 顶栏模糊带出现后弹簧升到 1，滚回顶部降回 0。玻璃层（背景折射 + 染色 + 边缘光）通过
 * `drawBackdrop(layerBlock = { alpha = backdropAlpha })` 整体淡入淡出——静止时按钮就是
 * 一枚普通悬浮图标，上滑顶栏变糊的瞬间才「长出」玻璃材质。环形阴影单独跟 [shadowAlpha]。
 *
 * ## 材质（通透液态玻璃）
 * `vibrancy + blur(8dp) + lens(6dp, 36dp)` 折射 + 低不透明度染色（浅色 0xFFFAFAFA@0.55 /
 * 深色 0xFF242424@0.60，透出折射的同时保住图标对比度）+ 默认边缘光 + 环形模糊阴影。
 * 按压：缩放（4dp 等效）+ 沿拖动方向拉伸 + tanh 跟手位移 + 高光跟随，抬手弹簧回弹；
 * 点击带 Confirm 触感。
 *
 * ## 降级
 * [backdrop] 为 null（设置子页等纯色顶栏）或「降低动态效果」开启时退回普通图标按钮，
 * 与现有 [IconButton] 视觉一致，不采样 backdrop。
 *
 * drawBackdrop 的 element 按引用比较 shape / effects 等lambda：这里全部用 remember 固定，
 * alpha 走 [rememberUpdatedState] 在绘制期读取，避免弹簧动画期间每帧重建 lambda
 * 触发采样层重录。
 */
@Composable
fun LiquidTopBarButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    /** 玻璃层透明度：0 = 纯图标，1 = 完整玻璃。跟随顶栏模糊带的出现进度 */
    backdropAlpha: Float = 0f,
    /** 环形阴影透明度：与 [backdropAlpha] 同源，让阴影与玻璃同步浮现 */
    shadowAlpha: Float = 0f,
    buttonHeight: Dp = LiquidTopBarButtonSize,
    iconSize: Dp = com.iji.app.ui.theme.IjiIconSize.Action,
    iconTint: Color = Color.Unspecified,
    /** 图标层动画挂点：下拉一镜到底时由调用点注入 fraction 驱动的 graphicsLayer */
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * 头像 URI（「我的」入口）：非空时内容层改为**圆形头像**（替代图标）。
     * 尺寸随 [backdropAlpha] 联动：回顶玻璃未显时为小版（≈26dp，比图标稍大），
     * 上滑玻璃显现时逐渐放大至接近充满圆钮（留 3dp 玻璃边），见 [CircularAvatar]；
     * 加载中 / 失败回退到原图标，与未设置头像时一致，保证不破版。
     */
    avatarUri: String? = null,
) {
    // 降级：无采样层（子页）或「降低动态效果」开启 → 普通图标按钮（现状视觉，常显）
    if (backdrop == null || PerfState.reducedEffects) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.minTouchTarget().ijiIconFocusRing(),
        ) {
            if (avatarUri.isNullOrBlank()) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(iconSize).then(iconModifier),
                )
            } else {
                CircularAvatar(
                    uri = avatarUri,
                    size = buttonHeight,
                    // 降级无玻璃：固定静止小尺寸
                    backdropAlpha = 0f,
                    contentDescription = contentDescription,
                    fallback = {
                        Icon(
                            icon,
                            contentDescription = contentDescription,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(iconSize),
                        )
                    },
                )
            }
        }
        return
    }

    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()

    // 材质染色：低不透明度让折射透出来，深浅各一档（与底栏加号圆钮同思路）
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.55f)
    else Color(0xFF242424).copy(alpha = 0.60f)
    val shadowColor = if (isLightTheme) Color(0x12000000) else Color(0x20000000)

    // 环形模糊阴影：BlurMaskFilter 半径随 shadowAlpha 展开，Paint 复用避免每帧分配
    val shadowPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    val interactionSource = remember { MutableInteractionSource() }

    // lambda 全部固定引用；alpha 用 State 在绘制期读，不进组合
    val glassAlpha = rememberUpdatedState(backdropAlpha)
    val shapeBlock: () -> Shape = remember { { CircleShape } }
    val glassEffects: BackdropEffectScope.() -> Unit = remember {
        {
            vibrancy()
            blur(8f.dp.toPx())
            lens(6f.dp.toPx(), 36f.dp.toPx())
        }
    }
    // layerBlock 会作为 Modifier.graphicsLayer 挂在整条链前面：玻璃、染色、边缘光一起淡入淡出
    val layerBlock: GraphicsLayerScope.() -> Unit = remember { { alpha = glassAlpha.value } }
    val onDrawSurface: DrawScope.() -> Unit = remember(containerColor) { { drawRect(containerColor) } }

    Box(
        modifier = modifier
            .minTouchTarget()
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                // 按压视觉由高光层提供，不要默认涟漪
                indication = null,
                role = Role.Button,
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(buttonHeight)
                .graphicsLayer {
                    clip = false
                    val width = size.width
                    val height = size.height
                    val progress = interactiveHighlight.pressProgress.coerceAtLeast(0f)
                    // 按压缩放：等效放大 4dp（同 NexioSchedule，视觉是「鼓起」而非缩小）
                    val scale = 1f + 4f.dp.toPx() / height * progress
                    val offset = interactiveHighlight.offset
                    val maxDragScale = 1f.dp.toPx() / height
                    val offsetAngle = atan2(offset.y, offset.x)
                    // 沿拖动方向拉伸：x/y 按拖动角度的分量各自拉长
                    scaleX = scale +
                        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                        (width / height).coerceAtMost(1f)
                    scaleY = scale +
                        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                        (height / width).coerceAtMost(1f)
                    val contentMin = size.minDimension.coerceAtLeast(1f)
                    // 跟手位移：起始斜率 0.08（位移约为手指的 8%），越拖阻尼越强
                    val initialDerivative = 0.08f
                    translationX = contentMin * tanh(initialDerivative * offset.x / contentMin)
                    translationY = contentMin * tanh(initialDerivative * offset.y / contentMin)
                }
                .drawBehind {
                    // 环形阴影：外圈 CW + 内圈 CCW（winding 差集挖洞），blur 半径与
                    // alpha 都随 shadowAlpha 展开，与玻璃同步浮现
                    val spread = shadowAlpha
                    if (spread > 0.01f) {
                        val outerRadius = size.minDimension / 2f + 2f * density * spread
                        val innerRadius = size.minDimension / 2f
                        val path = Path().apply {
                            addOval(
                                Rect(
                                    center.x - outerRadius, center.y - outerRadius,
                                    center.x + outerRadius, center.y + outerRadius,
                                ),
                                Path.Direction.Clockwise,
                            )
                            addOval(
                                Rect(
                                    center.x - innerRadius, center.y - innerRadius,
                                    center.x + innerRadius, center.y + innerRadius,
                                ),
                                Path.Direction.CounterClockwise,
                            )
                        }
                        shadowPaint.color = android.graphics.Color.argb(
                            ((shadowColor.alpha * 3.2f).coerceAtMost(1f) * spread * 255f).toInt(),
                            0, 0, 0,
                        )
                        shadowPaint.maskFilter = BlurMaskFilter(
                            (10f * density * spread).coerceAtLeast(0.1f),
                            BlurMaskFilter.Blur.NORMAL,
                        )
                        drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), shadowPaint)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 玻璃材质层：backdrop 折射 + 染色 + 边缘光，随 backdropAlpha 整体淡入淡出
            Box(
                modifier = Modifier
                    .size(buttonHeight)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = shapeBlock,
                        effects = glassEffects,
                        highlight = null,
                        shadow = null,
                        layerBlock = layerBlock,
                        onDrawSurface = onDrawSurface,
                    )
                    .edgeLight(shape = CircleShape, edgeLight = rememberDefaultEdgeLight()),
            )
            // 按压高光层：与材质层平级，避免被 layerBlock 的 alpha 一起带走；
            // 必须 clip——高光半径是 minDimension * 1.5，不裁会画成亮方块
            Box(
                modifier = Modifier
                    .size(buttonHeight)
                    .clip(CircleShape)
                    .then(interactiveHighlight.modifier)
                    .then(if (enabled) interactiveHighlight.gestureModifier else Modifier),
            )
            if (avatarUri.isNullOrBlank()) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize).then(iconModifier),
                    tint = if (iconTint != Color.Unspecified) iconTint
                    else if (isLightTheme) Color.Black.copy(alpha = 0.85f)
                    else Color.White.copy(alpha = 0.85f),
                )
            } else {
                CircularAvatar(
                    uri = avatarUri,
                    size = buttonHeight,
                    backdropAlpha = backdropAlpha,
                    contentDescription = contentDescription,
                    fallback = {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDescription,
                            modifier = Modifier.size(iconSize),
                            tint = if (iconTint != Color.Unspecified) iconTint
                            else if (isLightTheme) Color.Black.copy(alpha = 0.85f)
                            else Color.White.copy(alpha = 0.85f),
                        )
                    },
                )
            }
        }
    }
}

/**
 * 头像尺寸随玻璃显现进度联动：
 * - 静止（回顶、玻璃未显现）：缩到 [AvatarRestFraction]，内容槽 38dp × 0.75 ≈ 28dp，
 *   比顶栏图标（22dp）稍大一点，直接贴页面内容（与图标同处境）；
 * - 页面上滑玻璃逐渐显现：头像同进度放大到距圆钮边 [AvatarFullInset]，接近充满玻璃。
 *
 * 布局尺寸固定取满显直径（内容槽），缩放走绘制期 graphicsLayer（读进度 State，不逐帧重组）。
 */
private val AvatarFullInset = 3.dp
private const val AvatarRestFraction = 0.75f

/**
 * 圆形头像内容：Coil 异步加载（与 [com.iji.app.ui.mine.MineScreen] 的头像同一套口径），
 * 加载中 / 失败统一回退到 [fallback]，保证不破版。
 *
 * 尺寸随 [backdropAlpha]（玻璃显现进度）联动：布局固定取满显直径（圆钮 - 2×[AvatarFullInset]），
 * 静止时经 graphicsLayer 缩到 [AvatarRestFraction]、玻璃显现时同进度放大到满显——
 * 缩放在绘制期读进度 State，不逐帧重组。边缘不加描边，玻璃 / 背景就是过渡层。
 */
@Composable
private fun CircularAvatar(
    uri: String,
    size: Dp,
    backdropAlpha: Float,
    contentDescription: String?,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val glassProgress = rememberUpdatedState(backdropAlpha.coerceIn(0f, 1f))
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size - AvatarFullInset * 2),
            loading = { fallback() },
            error = { fallback() },
            // 只有成功加载的图片参与缩放/裁切：加载中 / 失败的兜底图标保持原始图标尺寸，
            // 与未设置头像时的视觉完全一致
            success = {
                SubcomposeAsyncImageContent(
                    modifier = Modifier
                        .graphicsLayer {
                            val scale = lerp(AvatarRestFraction, 1f, glassProgress.value)
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape),
                )
            },
        )
    }
}
