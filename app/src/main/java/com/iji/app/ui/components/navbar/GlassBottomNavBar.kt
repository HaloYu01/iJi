package com.iji.app.ui.components.navbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.iji.app.ui.effects.edgelight.edgeLight
import com.iji.app.ui.effects.edgelight.rememberDefaultEdgeLight
import com.iji.app.ui.effects.glass.DampedDragAnimation
import com.iji.app.ui.effects.glass.InteractiveHighlight
import com.iji.app.ui.util.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/** 底部导航 tab：纯视觉；手势由 [GlassBottomNavBar] 统一层处理 */
@Composable
fun RowScope.GlassBottomNavItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(ContinuousCapsule())
            .semantics { role = Role.Tab }
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * 液态玻璃底部导航。
 *
 * 统一手势层：
 * - 按下 tab：胶囊飞过去并保持按压
 * - 按下胶囊 / 随后拖动：1:1 跟手
 * - 松手：吸附最近 tab，提交选中
 */
@Composable
fun GlassBottomNavBar(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 56.dp,
    highlightHeight: Dp = 48.dp,
    selectorHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isAppDarkTheme()
    val accentColor =
        if (isLightTheme) Color.Black
        else Color.White
    val containerColor =
        if (isLightTheme) Color(0xFFFFFFFF).copy(0.42f)
        else Color(0xFF121212).copy(0.42f)
    val defaultEdgeLight = rememberDefaultEdgeLight()

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val viewConfiguration = LocalViewConfiguration.current
        val padPx = with(density) { 4f.dp.toPx() }
        // 玻璃效果的固定像素值，多处复用（避免重复 toPx）
        val blur8Px = with(density) { 8f.dp.toPx() }
        val lens24Px = with(density) { 24f.dp.toPx() }
        // 可见层玻璃效果是静态的（不随按压/拖动变化）→ remember 固定引用：
        // drawBackdrop 对 effects 按引用比较，否则切 tab / 动画帧都会重建整条模糊 + 折射链
        val staticBackdropEffects: BackdropEffectScope.() -> Unit =
            remember(blur8Px, lens24Px) {
                {
                    vibrancy()
                    blur(blur8Px)
                    lens(lens24Px, lens24Px)
                }
            }
        // 捕获层玻璃静态化（去掉 lens 折射/高光）：让 tabsBackdrop 录制内容不随按压变化，
        // 从而省掉按压/飞行中每帧的全宽重录（tab 内容本体是静态的，选中由胶囊位置体现）。
        val captureBackdropEffects: BackdropEffectScope.() -> Unit =
            remember(blur8Px) {
                {
                    vibrancy()
                    blur(blur8Px)
                }
            }
        val tabWidth = (constraints.maxWidth.toFloat() - padPx * 2f) / tabsCount
        val maxIndex = (tabsCount - 1).toFloat()

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val ltrSign = if (isLtr) 1f else -1f
        val animationScope = rememberCoroutineScope()
        // 不能用 selectedTabIndex lambda 做 remember key：每次重组都是新实例，currentIndex 会被冲掉导致点 tab 无效
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..maxIndex,
                visibilityThreshold = 0.001f,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> }
            )
        }
        val latestSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
        val latestOnTabSelected by rememberUpdatedState(onTabSelected)
        // 通知去重：重复点当前 tab 时 target 与上次相同，不必再 navigate 一次
        //（navigate 会带着 popUpTo(saveState)+restoreState 把目标页拆掉重建，表现为"点 tab 顿一下"）。
        var reportedTabIndex by remember { mutableIntStateOf(Int.MIN_VALUE) }
        val notifyTabSelected: (Int) -> Unit = { index ->
            if (index != reportedTabIndex) {
                reportedTabIndex = index
                latestOnTabSelected(index)
            }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { latestSelectedTabIndex() }
                .collectLatest { index ->
                    if (index != currentIndex) {
                        currentIndex = index
                    }
                }
        }
        // 这里的变化来源是"宿主路由变了"，只同步指示器位置，**绝不再回调 onTabSelected**：
        // 否则离开主 tab 区（如进「我的」）时宿主传进来的 index 变化会被当成一次点击，
        // 反向导航把返回栈弹回首页（曾表现为"点我的跳到账本"）。用户手势的切页通知
        // 只由下方 pointerInput 松手处发出。
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index -> dampedDragAnimation.animateToValue(index.toFloat()) }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // 可见层：不缩放（源库只缩放下面的捕获层，滑块外的放大不可见）
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule() },
                    effects = staticBackdropEffects,
                    highlight = null,
                    onDrawSurface = { drawRect(containerColor) }
                )
                .edgeLight(shape = ContinuousCapsule(), edgeLight = defaultEdgeLight)
                .then(interactiveHighlight.modifier)
                .height(containerHeight)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // tab 内容本体是静态的（图标/文字不随选中变化，选中由胶囊位置体现），捕获层玻璃也已
        // 静态化（无按压折射/高光），所以采样层只在「拖动位移中」+ 松手后那一帧录制。
        // recordKey 涵盖所有能改变录制内容的因素（主题色、密度）。
        val tabsRecordKey = remember(isLightTheme, accentColor, containerColor, density) {
            listOf(isLightTheme, accentColor, containerColor, density)
        }
        val tabsRecordingDirty = remember { booleanArrayOf(true) }
        val mustRecordTabs: () -> Boolean = remember {
            {
                val active = offsetAnimation.value != 0f
                if (active) {
                    tabsRecordingDirty[0] = true
                    true
                } else {
                    // 松手后再录一帧，把采样层从「位移态」换回静止态
                    val wasDirty = tabsRecordingDirty[0]
                    tabsRecordingDirty[0] = false
                    wasDirty
                }
            }
        }

        // 捕获层（透明）：内容不缩放（已删除按压放大动画，优化性能）
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop, recordKey = tabsRecordKey, mustRecord = mustRecordTabs)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule() },
                    effects = captureBackdropEffects,
                    highlight = null,
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(highlightHeight)
                .fillMaxWidth()
                .padding(horizontal = 4f.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // 胶囊（纯视觉）
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { ContinuousCapsule() },
                    downsampleScale = 0.5f,
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        // 静息时半径 0 —— 跳过。飞行中镜头折射是每帧最重的 shader：
                        // 去色散（chromaticAberration 三采样→单采样）并降采样到 0.5，
                        // 消除弹簧速度峰值段（移动中间）的掉帧。
                        if (progress > 0f) {
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress
                            )
                        }
                    },
                    // 高光/投影/内阴影三层随按压变化，飞行中(progress=1)每帧重建，是移动中除折射外的
                    // 主要额外开销；静息态本就为 null，这里一并去掉，让飞行中的胶囊与静息态同构。
                    highlight = null,
                    shadow = null,
                    innerShadow = null,
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.08f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .then(interactiveHighlight.gestureModifier)
                .height(selectorHeight)
                .fillMaxWidth(1f / tabsCount)
        )

        // 统一手势层：按下 tab / 拖胶囊 / 松手选中 都在这里
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(tabsCount, tabWidth, isLtr, padPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x

                        val capsuleCenterX = if (isLtr) {
                            padPx + (dampedDragAnimation.value + 0.5f) * tabWidth
                        } else {
                            size.width - padPx - (dampedDragAnimation.value + 0.5f) * tabWidth
                        }
                        val capsuleHalf = tabWidth * 0.55f
                        var dragging = abs(downX - capsuleCenterX) <= capsuleHalf
                        var pressedTab = -1

                        if (dragging) {
                            dampedDragAnimation.press()
                        } else {
                            pressedTab = if (isLtr) {
                                floor((downX - padPx) / tabWidth).toInt()
                            } else {
                                floor((size.width - padPx - downX) / tabWidth).toInt()
                            }.fastCoerceIn(0, tabsCount - 1)
                            // 按下 tab：胶囊飞过去，保持按压
                            dampedDragAnimation.animateToValueKeepingPress(pressedTab.toFloat())
                        }

                        var lastX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                val target = if (dragging) {
                                    dampedDragAnimation.targetValue
                                        .fastRoundToInt()
                                        .fastCoerceIn(0, tabsCount - 1)
                                } else {
                                    pressedTab.fastCoerceIn(0, tabsCount - 1)
                                }
                                currentIndex = target
                                dampedDragAnimation.animateToValue(target.toFloat())
                                dampedDragAnimation.release()
                                // 立即通知宿主切页，不等 snapshotFlow 下一拍，减少底栏与 pager 错帧
                                notifyTabSelected(target)
                                animationScope.launch {
                                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                                }
                                break
                            }

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - downX

                            // 点 tab 后继续滑 → 接管为拖动
                            if (!dragging && abs(totalDx) > touchSlop) {
                                dragging = true
                                lastX = change.position.x
                            }

                            if (dragging && abs(dx) > 0.01f) {
                                lastX = change.position.x
                                dampedDragAnimation.updateValue(
                                    (dampedDragAnimation.targetValue + dx / tabWidth * ltrSign)
                                        .fastCoerceIn(0f, maxIndex)
                                )
                                animationScope.launch {
                                    offsetAnimation.snapTo(offsetAnimation.value + dx)
                                }
                            } else {
                                lastX = change.position.x
                            }

                            change.consume()
                        }
                    }
                }
        )
    }
}

