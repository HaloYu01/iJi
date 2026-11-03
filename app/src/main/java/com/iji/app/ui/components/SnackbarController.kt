package com.iji.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

/**
 * 轻量提示（Snackbar）的统一入口。
 *
 * **背景**：此前各处直接用 `Toast.makeText(...)`，问题是样式不受控、不支持动作按钮、
 * 不跟随主题、且与 Miuix 设计语言不一致。这里基于 Miuix `SnackbarHostState` 封装一层，
 * 让调用方**无需处理协程**（`showSnackbar` 是 suspend 函数）。
 *
 * **基础用法**
 * ```
 * // 1）最简单：纯文本提示
 * val snackbar = LocalSnackbarController.current
 * snackbar.show("已保存")
 *
 * // 2）带动作（如"撤销"）
 * snackbar.show("已删除", actionLabel = "撤销") { undo() }
 *
 * // 3）长时提示
 * snackbar.show("导入完成", duration = SnackbarDuration.Long)
 * ```
 *
 * **接入方式**：由宿主（当前是 `AppRoot`）创建 `SnackbarHostState`，
 * 通过 [LocalSnackbarController] 提供，并在 Scaffold 的 `snackbarHost` 渲染 `SnackbarHost`。
 *
 * **后续可扩展方向**
 * - 语义化封装：`showSuccess/showError/showInfo`，按语义自动匹配图标与配色。
 * - 队列与去重：相同文案短时间内重复触发时合并，避免刷屏。
 * - 与 [ContentState] 联动：失败态自动附带"重试"动作（复用 [ContentState.Error] 的 message）。
 * - 支持自定义 `content`（Miuix `SnackbarHost` 已支持），实现带图标/进度的富提示。
 */
class SnackbarController internal constructor(
    private val hostState: SnackbarHostState?,
    private val scope: CoroutineScope?,
) {

    /**
     * 显示一条轻量提示。
     *
     * @param message 提示文案。
     * @param actionLabel 动作按钮文案；为 null 则不显示动作。
     * @param onAction 用户点击动作按钮时的回调（仅在 [actionLabel] 非 null 时可能触发）。
     * @param duration 显示时长，默认 [SnackbarDuration.Short]。
     */
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) {
        val state = hostState ?: return
        val coroutineScope = scope ?: return
        coroutineScope.launch {
            val result = state.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = duration,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    companion object {
        /** 未接入宿主时的空实现，保证 `LocalSnackbarController.current.show(...)` 永远安全。 */
        val NoOp = SnackbarController(null, null)
    }
}

/** 当前可用的 Snackbar 控制器；宿主未接入时为 [SnackbarController.NoOp]（调用即空操作）。 */
val LocalSnackbarController = staticCompositionLocalOf { SnackbarController.NoOp }

/** 在宿主处创建并记住 [SnackbarHostState]。 */
@Composable
fun rememberSnackbarHostState(): SnackbarHostState = remember { SnackbarHostState() }

/** 在宿主处由 [SnackbarHostState] 派生出 [SnackbarController]。 */
@Composable
fun rememberSnackbarController(hostState: SnackbarHostState): SnackbarController {
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) { SnackbarController(hostState, scope) }
}
