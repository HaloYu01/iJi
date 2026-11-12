// 弹窗动画基建：folme 弹簧。
//
// 1:1 移植自 NexioSchedule miuix/src/commonMain/.../anim/MiuixEasing.kt 的 folmeSpring
// （上游 Miuix 0.9.4 没有它）；DecelerateEasing 直接用 Maven 版 miuix（签名一致）。

package com.iji.app.ui.components.dialog

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI

/**
 * Creates a [SpringSpec] from damping and response parameters.
 *
 * @param damping The damping ratio. 1.0 = critically damped (no overshoot),
 *   < 1.0 = underdamped (oscillates), > 1.0 = overdamped.
 * @param response The response time in seconds. Smaller values = faster animation.
 */
fun <T> folmeSpring(
    damping: Float,
    response: Float,
): SpringSpec<T> {
    val stiffness = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat()
    return spring(dampingRatio = damping, stiffness = stiffness)
}
