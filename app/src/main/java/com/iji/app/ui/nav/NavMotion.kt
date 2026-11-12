package com.iji.app.ui.nav

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/**
 * 全站页面转场：**纯位移，不做任何透明度混合**。
 *
 * 层级（深度）关系决定方向：
 * - push（enter/exit）：**视差推入**。新页从右整幅滑入，旧页只向左让出 1/4 幅宽；
 *   旧页右缘永远在新页左缘右侧，任何一帧都不会露出底色缝隙。
 * - pop（popEnter/popExit）：与 push 反向。
 * - **同层级（两个主 tab）：整页平推**。两页用同一条位移弹簧**同速、锁步**地横向平移，
 *   方向由底栏顺序（[tabOrder] 索引差）决定：去右侧 tab 时新页从右推入、旧页向左推出；
 *   往左回则相反。像翻页器一样，任何一帧屏幕都被整页内容铺满，不叠影、不露底。
 *
 * 去掉淡入淡出：alpha 混合会让两页在转场中同时可见，既加重每帧绘制，也让平推看起来
 * 像「淡出旧页 + 淡入新页」而不是一次连续位移。位移只改 graphicsLayer，动画帧只写矩阵。
 * 这也让底栏玻璃采样层能逐帧录到「正在移动的内容」，而不是等页面显示完再补渲染一遍。
 *
 * - 低内存设备：所有转场直接无动画；
 * - 系统「移除动画 / 动画缩放 0」由 Compose 的 MotionDurationScale 统一处理。
 */
class NavMotion(private val tabOrder: List<String>, private val lowEnd: Boolean = false) {

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTab(): Boolean =
        tabOrder.indexOf(initialState.destination.route) >= 0 &&
            tabOrder.indexOf(targetState.destination.route) >= 0

    /**
     * tab 平推方向：+1 = 去底栏右侧的 tab（新页从右进），-1 = 往左回（新页从左进）。
     * pop 时 initial/target 互换，算出来的方向自动反过来，无需额外分支。
     */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int =
        if (tabOrder.indexOf(targetState.destination.route) >=
            tabOrder.indexOf(initialState.destination.route)
        ) 1 else -1

    /**
     * 位移弹簧：轻微欠阻尼、无肉眼回弹，落在 HyperOS 转场的 300~400ms 节奏带。
     * 页面平推、push/pop 视差、底栏显隐与底栏滑块飞行共用同一组参数，保证同起同落。
     */
    fun slideSpring() = spring(
        dampingRatio = 0.95f,
        stiffness = 450f,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    /** tab 平推：新页从方向侧整幅推入 */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSlideIn(): EnterTransition {
        if (lowEnd) return EnterTransition.None
        val dir = tabDirection()
        return slideInHorizontally(slideSpring()) { it * dir }
    }

    /** tab 平推：旧页向反方向整幅推出，与新页同速锁步 */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSlideOut(): ExitTransition {
        if (lowEnd) return ExitTransition.None
        val dir = tabDirection()
        return slideOutHorizontally(slideSpring()) { -it * dir }
    }

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = with(scope) {
        when {
            isTab() -> tabSlideIn()
            // push：新页从右整幅滑入
            else ->
                if (lowEnd) EnterTransition.None
                else slideInHorizontally(slideSpring()) { it }
        }
    }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = with(scope) {
        when {
            isTab() -> tabSlideOut()
            // push：旧页只向左让出 1/4（视差）
            else ->
                if (lowEnd) ExitTransition.None
                else slideOutHorizontally(slideSpring()) { -it / 4 }
        }
    }

    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
        if (lowEnd) return EnterTransition.None
        return with(scope) {
            if (isTab()) {
                tabSlideIn()
            } else {
                // pop：旧页从左侧 1/4 处回位
                slideInHorizontally(slideSpring()) { -it / 4 }
            }
        }
    }

    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
        if (lowEnd) return ExitTransition.None
        return with(scope) {
            if (isTab()) {
                tabSlideOut()
            } else {
                // pop：当前页整幅向右滑出
                slideOutHorizontally(slideSpring()) { it }
            }
        }
    }

    companion object {
        @Composable
        fun remember(tabOrder: List<String>): NavMotion {
            val context = LocalContext.current
            val lowEnd = remember(context) {
                (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
            }
            return remember(tabOrder, lowEnd) { NavMotion(tabOrder, lowEnd) }
        }
    }
}
