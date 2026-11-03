package com.iji.app.ui.nav

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry

/**
 * 全站页面转场。
 *
 * 设计目标：横向推进的层级关系，来回方向对称，纯位移、不做淡入淡出，
 * 并且**两页逐帧锁步（lockstep）**——同一次转场里入场页与出场页必须用完全
 * 相同的时长和缓动曲线。两页位移始终同步，就永远严丝合缝地拼在一起，
 * 任意一帧屏幕都被页面内容完整覆盖，不会裂出露出窗口底色的缝。
 *
 * 旧实现入场用减速曲线、出场用加速曲线，转场中段两页位移差可达半屏，
 * 中间露出一条背景色带，两页看起来互相撕开——这正是「切页割裂感」的根源，
 * 已废弃。改用全站唯一的 [Smooth] 曲线（FastOutSlowIn），两条腿共用一个节奏。
 *
 * - 主 tab：250ms 横向平移，方向跟随底栏顺序（左→右从右侧推入，右→左从左侧推入）；
 * - push：新页从右推入、旧页同步向左推走，400ms；
 * - pop：当前页向右推走、上一页从左侧同步回位，250ms；
 * - 低内存设备自动降级：push/pop 缩到 250/200ms，tab 直接瞬时切换；
 * - 系统「移除动画 / 动画缩放 0」由 Compose 的 MotionDurationScale 统一处理。
 *
 * 注意：改任何一条转场都必须同步改配对的那条（enter↔exit、popEnter↔popExit），
 * 两边 spec 不一致就会重新出现裂缝。
 */
class NavMotion(private val tabOrder: List<String>, private val lowEnd: Boolean = false) {

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTab(): Boolean =
        tabOrder.indexOf(initialState.destination.route) >= 0 &&
            tabOrder.indexOf(targetState.destination.route) >= 0

    /** 1 = 新页从右侧来，-1 = 新页从左侧来（仅 tab 之间有意义） */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.direction(): Int =
        if (isTab() && tabOrder.indexOf(targetState.destination.route) < tabOrder.indexOf(initialState.destination.route)) -1 else 1

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = with(scope) {
        when {
            isTab() ->
                if (lowEnd) EnterTransition.None
                else slideInHorizontally(tween(TAB_MS, easing = Smooth)) { it * direction() }
            else -> slideInHorizontally(tween(if (lowEnd) PUSH_MS_LOW else PUSH_MS, easing = Smooth)) { it }
        }
    }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = with(scope) {
        when {
            isTab() ->
                if (lowEnd) ExitTransition.None
                else slideOutHorizontally(tween(TAB_MS, easing = Smooth)) { -it * direction() }
            else -> slideOutHorizontally(tween(if (lowEnd) PUSH_MS_LOW else PUSH_MS, easing = Smooth)) { -it }
        }
    }

    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
        slideInHorizontally(tween(if (lowEnd) POP_MS_LOW else POP_MS, easing = Smooth)) { -it }

    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
        slideOutHorizontally(tween(if (lowEnd) POP_MS_LOW else POP_MS, easing = Smooth)) { it }

    companion object {
        const val PUSH_MS = 400
        const val PUSH_MS_LOW = 250
        const val POP_MS = 250
        const val POP_MS_LOW = 200
        const val TAB_MS = 250

        /** 全站唯一缓动曲线：入场页与出场页共用，两页才能逐帧锁步（见类注释）。 */
        val Smooth = FastOutSlowInEasing

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
