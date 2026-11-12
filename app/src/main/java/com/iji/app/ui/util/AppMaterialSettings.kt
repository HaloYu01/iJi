package com.iji.app.ui.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iji.app.ui.effects.edgelight.EdgeLight
import com.iji.app.ui.effects.edgelight.EdgeLightStyle
import com.kyant.backdrop.isRenderEffectSupported

/**
 * 应用材质质量档（通透玻璃 / 清透磨砂 / 无材质），默认清透磨砂。
 *
 * 统一限制：RenderEffect 不可用（Android API < 31）的设备锁死无材质档（见 [normalize]），
 * 模糊相关的按能力降级分支不在这里出现——能力不足时档位本身就是无材质。
 */
object AppMaterialSettings {

    const val KEY_APP_MATERIAL = "app_material"

    const val BEST = "best"
    const val BALANCED = "balanced"
    const val NONE = "none"

    /** 降级描边同色系提亮幅度（向白混合比例） */
    private const val STROKE_LIGHTEN = 0.2f

    /** 玻璃件采样模糊（dp）：全档位统一（无材质档为 0） */
    private const val GLASS_BLUR_DP = 6f

    /** 当前档位；组合期可读，写入走 [apply]。 */
    var level: String by mutableStateOf(BALANCED)
        private set

    /** 按钮 / 底栏背景板折射：仅通透玻璃档保留，其余档关闭。 */
    fun chromeLensEnabled(): Boolean = level == BEST

    /** 是否通透玻璃档（折射 + 低模糊那一档）。 */
    fun isBestTier(): Boolean = level == BEST

    /** 是否无材质档：玻璃模糊 / 折射 / vibrancy 全关。 */
    fun isNoneTier(): Boolean = level == NONE

    /** 玻璃材质总开关（无材质档 false）：玻璃件的 vibrancy / 硬编码模糊与折射一并关闭。 */
    fun glassEffectsEnabled(): Boolean = level != NONE

    /**
     * 玻璃件采样模糊半径（dp）。**所有档位统一 6**：档位差异交给折射与描边，
     * 不再靠模糊量区分（4/8 两档在小控件上几乎看不出差别，反而让参数多一套）。
     */
    fun glassBlurDp(): Float = if (level == NONE) 0f else GLASS_BLUR_DP

    /**
     * 玻璃底色 alpha：无材质档返回 1f（玻璃面改实色），其余档原样返回。
     *
     * @param baseAlpha 其余档原本使用的 alpha
     */
    fun solidSurfaceAlpha(baseAlpha: Float): Float = if (level == NONE) 1f else baseAlpha

    /**
     * 无材质档把高光描边降级为普通纯色描边（无模糊、SrcOver）；[baseColor] 非空时按同色系
     * 提亮一档作描边，否则按主题回退（浅色白 / 深色灰）。通透玻璃 / 清透磨砂保留原高光。
     */
    fun resolveEdgeLight(
        source: EdgeLight,
        isLightTheme: Boolean,
        baseColor: Color? = null,
    ): EdgeLight {
        if (level != NONE) return source
        val stroke = baseColor?.let(::lightenSameHue)
            ?: if (isLightTheme) Color.White else Color(0xFF333333)
        return EdgeLight(
            width = 0.8.dp,
            blurRadius = 0.dp,
            intensity = source.intensity,
            style = EdgeLightStyle.Uniform(
                color = stroke,
                blendMode = BlendMode.SrcOver,
            ),
        )
    }

    /** 同色系提亮一档：丢弃原 alpha 按不透明底色向白混合 [STROKE_LIGHTEN]，白底仍是白 */
    private fun lightenSameHue(color: Color): Color {
        val t = STROKE_LIGHTEN
        return Color(
            red = color.red + (1f - color.red) * t,
            green = color.green + (1f - color.green) * t,
            blue = color.blue + (1f - color.blue) * t,
            alpha = 1f,
        )
    }

    fun apply(level: String) {
        this.level = normalize(level)
    }

    /**
     * 启动时从偏好载入到全局状态。旧值 / 非法值落到清透磨砂档；
     * 模糊能力不可用（Android API < 31）一律锁进无材质档。
     */
    fun load(context: Context) {
        level = normalize(
            com.iji.app.data.repository.SettingsRepository.getString(context, KEY_APP_MATERIAL, BALANCED),
        )
    }

    /** 持久化并立即切换档位。 */
    fun apply(context: Context, level: String) {
        apply(level)
        com.iji.app.data.repository.SettingsRepository.setString(context, KEY_APP_MATERIAL, this.level)
    }

    /** 非法值落到清透磨砂档；模糊能力不可用（Android API < 31）一律锁进无材质档。 */
    private fun normalize(level: String): String {
        val resolved = when (level) {
            BEST, BALANCED, NONE -> level
            else -> BALANCED
        }
        return if (resolved != NONE && !isRenderEffectSupported()) NONE else resolved
    }
}
