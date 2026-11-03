package com.iji.app.ui.components.topbar

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI

/**
 * Miuix 的 `dampedSpring` 在本工程所依赖的远程 miuix AAR（0.9.4）中并未导出，
 * 而组件（CollapsibleTopBar / 弹层）需要它做标题栏折叠、阴影、渐变遮罩的弹簧动画。
 * 这里提供等价实现：阻尼比取 [damping]，刚度按响应时间 [response] 反推
 * （stiffness = (2π / response)^2），与系统阻尼弹簧手感一致。
 */
fun <T> dampedSpring(damping: Float, response: Float): AnimationSpec<T> {
    val omega = 2f * PI.toFloat() / response.coerceAtLeast(0.0001f)
    val stiffness = omega * omega
    return spring(dampingRatio = damping, stiffness = stiffness)
}
