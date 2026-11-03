package com.iji.app.ui.effects.background

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.cos
import kotlin.math.sin

/**
 * 动态光晕背景的画笔：负责 shader 创建与 uniform 绑定。
 *
 * 适配说明：原实现依赖 `top.yukonga.miuix.kmp.blur.RuntimeShader` / `asBrush()`，
 * 官方 Miuix 0.9.4 **没有**这两个符号；
 * 且本工程 Compose 版本的 `androidx.compose.ui.graphics` 也不含 RuntimeShader。
 * 因此直接使用平台类 [RuntimeShader]（AGSL，Android 13+）配合 [ShaderBrush]，
 * shader 源码与全部 uniform 语义保持原样。
 */
internal class GlowPainter {

    /** AGSL 需要 Android 13+；创建失败时整体降级为纯色背景 */
    val runtimeShader: RuntimeShader? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            null
        } else {
            runCatching { RuntimeShader(GLOW_BACKGROUND_SHADER).also { initStaticUniforms(it) } }.getOrNull()
        }
    }

    /** 光晕画刷；shader 不可用时为 null（调用方跳过特效） */
    val brush: Brush? by lazy { runtimeShader?.let { ShaderBrush(it) } }

    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private val colorsBuffer = FloatArray(16)
    private val pointsAnimBuffer = FloatArray(8)

    private var animTime = Float.NaN
    private var isDarkCached: Boolean? = null
    private var isTabletCached: Boolean? = null
    private var presetApplied = false
    private var cachedLogoHeight = Float.NaN
    private var cachedTotalHeight = Float.NaN
    private var cachedTotalWidth = Float.NaN
    private var cachedColorStage = Float.NaN
    private var cachedColorsPreset: GlowConfig.Config? = null
    private var cachedPointsAnimTime = Float.NaN
    private var cachedPointsAnimPreset: GlowConfig.Config? = null

    private companion object {
        const val U_TRANSLATE_Y = 0f
        const val U_ALPHA_MULTI = 1f
        const val U_NOISE_SCALE = 1.5f
        const val U_POINT_RADIUS_MULTI = 1f
    }

    private fun initStaticUniforms(shader: RuntimeShader) {
        shader.setFloatUniform("uTranslateY", U_TRANSLATE_Y)
        shader.setFloatUniform("uNoiseScale", U_NOISE_SCALE)
        shader.setFloatUniform("uPointRadiusMulti", U_POINT_RADIUS_MULTI)
        shader.setFloatUniform("uAlphaMulti", U_ALPHA_MULTI)
    }

    fun updateResolution(width: Float, height: Float) {
        val shader = runtimeShader ?: return
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        shader.setFloatUniform("uResolution", resolution)
    }

    fun updateAnimTime(time: Float) {
        val shader = runtimeShader ?: return
        if (animTime == time) return
        animTime = time
        shader.setFloatUniform("uAnimTime", animTime)
    }

    /** 4 个光晕点按 sin/cos 缓慢漂移 */
    fun updatePointsAnim(time: Float, preset: GlowConfig.Config) {
        val shader = runtimeShader ?: return
        if (cachedPointsAnimTime == time && cachedPointsAnimPreset === preset) return
        val offset = preset.pointOffset
        var i = 0
        while (i < 4) {
            val srcX = preset.points[i * 3]
            val srcY = preset.points[i * 3 + 1]
            val animX = srcX + sin(time + srcY) * offset
            val animY = srcY + cos(time + animX) * offset
            pointsAnimBuffer[i * 2] = animX
            pointsAnimBuffer[i * 2 + 1] = animY
            i++
        }
        shader.setFloatUniform("uPointsAnim", pointsAnimBuffer)
        cachedPointsAnimTime = time
        cachedPointsAnimPreset = preset
    }

    /** 颜色按 stage 在 colors2 → colors1 → colors2 → colors3 间循环插值 */
    fun updateColors(preset: GlowConfig.Config, stage: Float) {
        val shader = runtimeShader ?: return
        if (cachedColorsPreset === preset && cachedColorStage == stage) return
        val base = stage.toInt()
        val fraction = stage - base
        val start = colorsForCycleIndex(preset, base)
        val end = colorsForCycleIndex(preset, base + 1)
        for (i in 0 until 16) {
            colorsBuffer[i] = start[i] + (end[i] - start[i]) * fraction
        }
        shader.setFloatUniform("uColors", colorsBuffer)
        cachedColorsPreset = preset
        cachedColorStage = stage
    }

    private fun colorsForCycleIndex(preset: GlowConfig.Config, index: Int): FloatArray =
        when (index.mod(4)) {
            1 -> preset.colors1
            3 -> preset.colors3
            else -> preset.colors2
        }

    fun updateBoundIfNeeded(logoHeight: Float, totalHeight: Float, totalWidth: Float) {
        val shader = runtimeShader ?: return
        if (cachedLogoHeight == logoHeight && cachedTotalHeight == totalHeight && cachedTotalWidth == totalWidth) return
        updateBound(logoHeight, totalHeight, totalWidth)
        shader.setFloatUniform("uBound", bound)
        cachedLogoHeight = logoHeight
        cachedTotalHeight = totalHeight
        cachedTotalWidth = totalWidth
    }

    fun updatePresetIfNeeded(isDark: Boolean, isTablet: Boolean) {
        val shader = runtimeShader ?: return
        if (presetApplied && isDarkCached == isDark && isTabletCached == isTablet) return
        applyPreset(isDark, isTablet)
        isDarkCached = isDark
        isTabletCached = isTablet
        presetApplied = true
    }

    private fun applyPreset(isDark: Boolean, isTablet: Boolean) {
        val shader = runtimeShader ?: return
        val preset = GlowConfig.get(isDark, isTablet)
        shader.setFloatUniform("uPoints", preset.points)
        shader.setFloatUniform("uLightOffset", preset.lightOffset)
        shader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
    }

    private fun updateBound(logoHeight: Float, totalHeight: Float, totalWidth: Float) {
        val heightRatio = logoHeight / totalHeight
        if (totalWidth <= totalHeight) {
            bound[0] = 0f
            bound[1] = 1f - heightRatio
            bound[2] = 1f
            bound[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            bound[0] = 0f
            bound[1] = contentCenterY - aspectRatio / 2f
            bound[2] = 1f
            bound[3] = aspectRatio
        }
    }
}
