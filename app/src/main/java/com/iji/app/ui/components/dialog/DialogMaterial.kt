// 弹窗卡片材质档注入点 + 描边光解析。
//
// 1:1 移植自 NexioSchedule miuix/src/commonMain/.../material/MaterialSettings.kt 中
// 弹窗卡片实际消费的子集（LocalChromeLensEnabled / LocalMaterialNoneEnabled /
// LocalGlassDialogBlurDp / rememberMaterialEdgeLight），未用到的渐进模糊 /
// 面板档 / 文字动效档不再复制。差异（包内注释在案）：
// - EdgeLight 类型用本工程自研的 com.iji.app.ui.effects.edgelight（与参考项目
//   vendored 的 com.kyant.backdrop.edgelight 同构、API 同形）；
// - rememberMaterialEdgeLight 的默认亮暗判定用本工程 isAppDarkTheme()。

package com.iji.app.ui.components.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.iji.app.ui.effects.edgelight.EdgeLight
import com.iji.app.ui.effects.edgelight.rememberDefaultEdgeLight
import com.iji.app.ui.util.isAppDarkTheme

/**
 * 材质档位能力的注入点。
 *
 * 弹窗卡片不能反向依赖具体设置页，所以由 App 在根部 provide、卡片内随处可读。
 * 与 DialogPredictiveBack 的 `LocalPredictiveBackEnabled` 同思路：
 * 「App 的行为偏好」用 CompositionLocal。
 *
 * ⚠ 弹窗 / 对话框渲染在 root host，读不到声明页面的 CompositionLocal，
 * 要注入的值必须提供在 Miuix `Scaffold` **之上**（AppRoot 的根 provider 即在 Scaffold 外）。
 * 目前只注入了 [LocalChromeLensEnabled]（写死 true）与 [LocalMaterialNoneEnabled]
 * （跟随 PerfState.reducedEffects）；[LocalGlassDialogBlurDp]、[LocalEdgeLightResolver]
 * 和 DialogScreenCorner 的 LocalScreenCornerRadius 均未注入，卡片走各自默认值。
 * 各值默认取「无折射 / 不降级」的一方，与不注入时行为一致。
 */

/** chrome 透镜（折射）开关：通透玻璃档开，其余档关。 */
val LocalChromeLensEnabled = compositionLocalOf { false }

/** 无材质档：玻璃材质（模糊 / 折射 / vibrancy）整体关闭。 */
val LocalMaterialNoneEnabled = compositionLocalOf { false }

/** 对话框 / 贴底弹窗的采样模糊半径（dp）。 */
val LocalGlassDialogBlurDp = compositionLocalOf { 24f }

/**
 * 描边光降级解析器：材质档策略在 App 侧，卡片只能注入。
 *
 * 入参是 edgelight 生成的高光描边、当前是否浅色、以及调用点传入的底色（降级时按它提亮）。
 * 默认原样返回，与不注入时行为一致。App 侧必须让实例身份随材质档变化
 * （`remember(level) { ... }`），否则调用点按它做 key 时切档不会重算。
 */
val LocalEdgeLightResolver =
    compositionLocalOf<(EdgeLight, Boolean, Color?) -> EdgeLight> { { base, _, _ -> base } }

/**
 * 模块内统一取描边光。
 *
 * 本工程 `rememberDefaultEdgeLight` 不带底色参数（与参考项目的
 * `com.kyant.backdrop.edgelight.rememberDefaultEdgeLight` 相比少了缓存维度），
 * 这里保持「解析器进 key、切档重算」的结构不变。
 */
@Composable
fun rememberMaterialEdgeLight(
    baseColor: Color? = null,
    isLightTheme: Boolean = !isAppDarkTheme(),
): EdgeLight {
    val resolver = LocalEdgeLightResolver.current
    return key(resolver) {
        @Suppress("UNUSED_EXPRESSION")
        baseColor // 参考实现按底色参与缓存键；本工程 edgelight 不消费它，保留参数位以对齐签名
        resolver(rememberDefaultEdgeLight(), isLightTheme, baseColor)
    }
}
