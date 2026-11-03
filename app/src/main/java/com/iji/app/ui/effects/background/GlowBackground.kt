package com.iji.app.ui.effects.background

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.iji.app.ui.util.isAppDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds

/**
 * 动态光晕背景：在内容下方铺一层缓慢流动的光效。
 *
 * 用法：包在页面内容外层即可，特效绘制在 [content] 之下，不拦截任何触摸事件。
 *
 * @param dynamicBackground 是否播放动画（false 时颜色停止流动、光晕点静止）
 * @param bgModifier 挂在特效绘制层上的 modifier（如需让它同时作为顶栏模糊的采样源，传 `Modifier.layerBackdrop(...)`）
 * @param isFullSize true → 光晕作用区为容器高度的 80%；false → 50%
 * @param effectBackground 总开关，false 时只画底色
 * @param alpha 特效不透明度（0 时完全不画），通常由页面滚动进度驱动做淡出
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun GlowBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    effectBackground: Boolean = true,
    alpha: () -> Float = { 1f },
    content: @Composable (BoxScope.() -> Unit),
) {
    Box(modifier = modifier) {
        val surface = MaterialTheme.colorScheme.background
        val isDarkTheme = isAppDarkTheme()
        val config = LocalConfiguration.current
        val isTablet = config.screenWidthDp >= 600
        val painter = remember { GlowPainter() }
        val preset = remember(isDarkTheme, isTablet) { GlowConfig.get(isDarkTheme, isTablet) }
        val colorStage = remember { Animatable(0f) }

        // 颜色阶段推进：每 (colorInterpPeriod × 500ms) 前进一阶，由 spring 平滑过渡
        LaunchedEffect(dynamicBackground, preset) {
            if (!dynamicBackground) return@LaunchedEffect
            val animatesColors =
                preset.colors1 !== preset.colors2 || preset.colors2 !== preset.colors3
            if (!animatesColors) return@LaunchedEffect
            var targetStage = floor(colorStage.value) + 1f
            while (isActive) {
                delay((preset.colorInterpPeriod * 500).toLong().milliseconds)
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                targetStage += 1f
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier)
                .glowEffectDraw(
                    painter = painter,
                    preset = preset,
                    isDarkTheme = isDarkTheme,
                    surface = surface,
                    effectBackground = effectBackground,
                    isFullSize = isFullSize,
                    playing = dynamicBackground,
                    colorStage = { colorStage.value },
                    alpha = alpha,
                    isTablet = isTablet,
                ),
        )
        content()
    }
}
