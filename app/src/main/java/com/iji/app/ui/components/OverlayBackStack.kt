package com.iji.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 全 App 弹窗的**返回栈**：弹窗按**打开顺序**登记，返回键只关闭**栈顶**那一个，
 * 从而得到与视觉层叠一致的确定层级关系。
 *
 * 为什么需要显式栈，而不是让各弹窗各自 `BackHandler`、靠注册顺序隐式分层：
 * - 弹层实现不统一：`GlassDialog`（[IjiDialog] 及其派生的选择器）内部已注册
 *   `PredictiveBackHandler`（带预测性返回跟手动画，走 OnBackPressedDispatcher 统一管线，
 *   组合顺序在 [OverlayBackHandler] 之后、优先级更高）；
 *   而「记一笔」用的 Miuix `OverlayBottomSheet` 没有，返回键会直接穿透到页面/退出应用。
 * - **声明顺序 ≠ 打开顺序**：AddTransactionSheet 里几个选择器声明在 `OverlayBottomSheet`
 *   之前，若按「谁先注册谁响应」决定优先级，会得到与视觉层级**相反**的关闭顺序
 *   （先关掉底下的记一笔抽屉，而上面还盖着选择器）。
 *
 * 因此这里以「打开时间」为准：栈顶 = 最后打开 = 视觉最上层，返回时逐层退出。
 */
class OverlayBackStack {
    private val entries = mutableStateListOf<Entry>()

    /** 栈内是否还有打开的弹窗（供根 [OverlayBackHandler] 决定是否拦截返回键） */
    val isEmpty: Boolean get() = entries.isEmpty()

    /** 栈顶（最后打开的）弹窗 */
    fun top(): Entry? = entries.lastOrNull()

    /** 当前栈深度（调试用） */
    val depth: Int get() = entries.size

    /** 自底向上的层级标签（调试用） */
    internal fun labels(): String = entries.joinToString(" > ") { it.label }

    internal fun push(dismiss: () -> Unit, label: String): Entry {
        val entry = Entry(dismiss, label)
        entries.add(entry)
        return entry
    }

    internal fun remove(entry: Entry) {
        entries.remove(entry)
    }

    /** 一个已打开弹窗在栈中的登记项 */
    class Entry internal constructor(
        internal val dismiss: () -> Unit,
        internal val label: String,
    )
}

/** 未提供时的兜底栈（正常由 AppRoot 提供，见其 CompositionLocalProvider） */
private val OverlayBackStackFallback = OverlayBackStack()

val LocalOverlayBackStack = staticCompositionLocalOf { OverlayBackStackFallback }

/**
 * 把一个弹窗登记进返回栈：[show] 为 true 期间占栈，关闭或离开组合时自动出栈。
 *
 * 由弹窗组件内部（[IjiDialog]、记一笔抽屉等）调用，调用点无需关心。
 *
 * @param show 弹窗是否显示。
 * @param onDismiss 关闭该弹窗的动作；只会在它是**栈顶**时被调用。
 */
@Composable
fun RegisterOverlayBackLayer(
    show: Boolean,
    onDismiss: () -> Unit,
    label: String = "overlay",
) {
    val stack = LocalOverlayBackStack.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(stack, show) {
        if (!show) {
            onDispose {}
        } else {
            val entry = stack.push({ currentOnDismiss() }, label)
            onDispose { stack.remove(entry) }
        }
    }
}

/**
 * 全 App 返回键的**总入口**：安装在组合根（AppRoot，NavHost 之外）。
 *
 * - **栈非空**（有弹窗打开）：拦截返回键，只关闭栈顶那一个弹窗，
 *   路由与页面完全不受影响 —— 满足「弹窗显示时按返回仅关闭弹窗」。
 * - **栈空**：enabled=false，返回键交还系统默认行为（NavHost 出栈 / 退出应用），
 *   不会重复消费、也不会遗漏 —— 满足「弹窗关闭后再次按返回执行默认行为」。
 *
 * 注意：若某个弹层自身也注册了返回处理（如 `GlassDialog` 的预测性返回），
 * 那个回调比本入口更晚注册、优先级更高，会先响应并自行关闭（保留跟手动画）；
 * 本入口只在没有弹层自行处理时兜底，且同样只关栈顶，两者不会同时触发。
 */
@Composable
fun OverlayBackHandler() {
    val stack = LocalOverlayBackStack.current
    BackHandler(enabled = !stack.isEmpty) {
        // 只关闭栈顶：嵌套弹窗（抽屉里再开选择器）时按打开顺序逐层退出。
        stack.top()?.dismiss?.invoke()
    }
}
