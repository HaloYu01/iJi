// 下拉菜单专用的材质档兼容桩，只服务同包的 LiquidGlassDropdownMenu。
//
// 保持与上游同名同签名的 API 子集，让菜单主体维持 1:1 移植、零改动。档位不读
// ui.util.AppMaterialSettings（本工程另有一套完整三档实现 + 持久化，但尚未与菜单合并），
// 而是映射到「降低动态效果」开关 PerfState.reducedEffects：
// - reducedEffects=false → 通透玻璃档（BEST）：折射开、面板模糊恒定、底色 alpha 降一档
// - reducedEffects=true  → 无材质档（NONE）：模糊/折射/染色透明度全关
// level 因此是只读映射、无持久化；两套材质档目前各自生效，改档位只影响菜单侧。

package com.iji.app.ui.components.menu

import com.iji.app.ui.PerfState

object AppMaterialSettings {

    const val BEST = "best"
    const val BALANCED = "balanced"
    const val NONE = "none"

    /** 当前档位：由「降低动态效果」开关映射；只有 true/false，实际只会得到 BEST 或 NONE */
    val level: String
        get() = if (PerfState.reducedEffects) NONE else BEST

    /** 按钮 / 低栏背景板折射：仅通透玻璃档保留，其余档关闭。 */
    fun chromeLensEnabled(): Boolean = level == BEST

    /** 是否通透玻璃档（折射 + 低模糊那一档）。 */
    fun isBestTier(): Boolean = level == BEST

    /** 是否无材质档：玻璃模糊 / 折射 / vibrancy 全关。 */
    fun isNoneTier(): Boolean = level == NONE

    /** 玻璃材质总开关（无材质档 false）：玻璃件的模糊与折射一并关闭。 */
    fun glassEffectsEnabled(): Boolean = level != NONE

    /** 玻璃件采样模糊半径（dp）：所有档位统一 6（无材质档 0）。 */
    fun glassBlurDp(): Float = if (level == NONE) 0f else 6f

    /**
     * 弹出面板（下拉菜单）采样模糊半径（dp）。
     * 通透玻璃档取恒定值、不随展开渐变（上游 GLASS_PANEL_BLUR_BEST = 4f）；
     * 清透磨砂档 = 6 + 16；无材质档 0。
     * 当前 [level] 只会产出 BEST / NONE，BALANCED 分支按上游结构保留、实际不可达。
     */
    fun glassPanelBlurDp(): Float = when (level) {
        NONE -> 0f
        BEST -> 4f
        else -> glassBlurDp() + 16f
    }

    /**
     * 玻璃底色 alpha：无材质档返回 1f（玻璃面改实色）；通透玻璃档比常态低一档让折射和
     * 背后的内容透出来（浅色降 0.2 / 深色降 0.32，上游同值）；清透磨砂档原值返回。
     *
     * @param milledAlpha 清透磨砂档原本使用的 alpha
     */
    fun glassSurfaceAlpha(milledAlpha: Float, isLight: Boolean): Float =
        if (level == NONE) {
            1f
        } else {
            val drop = when {
                level != BEST -> 0f
                isLight -> 0.2f
                else -> 0.32f
            }
            (milledAlpha - drop).coerceAtLeast(0f)
        }
}
