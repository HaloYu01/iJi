package com.iji.app.ui.components

import android.content.Context
import android.widget.Toast
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
 * 轻量提示的统一入口（全 App 通用）。
 *
 * **口径：非必要不提示**。纯文本提示只用于失败、校验不通过、权限或前置条件缺失、
 * 操作指引等用户必须知道的场景；保存成功、状态变化、自动完成等有明显界面反馈的结果
 * 不要调用。纯文本走系统 **Toast**（浮层打开时也能看到），`duration` 映射为
 * Toast 时长（Short ≈ 2s，Long ≈ 3.5s）。
 *
 * **带动作按钮的提示**（如「已记一笔 · 撤销」）走 Miuix Snackbar（Host 由 `AppRoot` 渲染），
 * 它是操作入口，不受上述口径限制。
 *
 * **基础用法**
 * ```
 * val snackbar = LocalSnackbarController.current
 * snackbar.show("保存失败，请重试")
 * snackbar.show("已记一笔", "撤销") { undo() }
 * ```
 *
 * **接入方式**：由宿主（当前是 `AppRoot`）创建 `SnackbarHostState`，
 * 通过 [LocalSnackbarController] 提供；Context 由 [rememberSnackbarController] 取自组合。
 */
class SnackbarController internal constructor(
    private val hostState: SnackbarHostState?,
    private val scope: CoroutineScope?,
    private val toastContext: Context?,
) {

    /**
     * 显示一条轻量提示。
     *
     * @param message 提示文案。
     * @param actionLabel 动作按钮文案；为 null 则纯文本走 Toast，非 null 走 Snackbar（可点击动作）。
     * @param onAction 用户点击动作按钮时的回调（仅在 [actionLabel] 非 null 时可能触发）。
     * @param duration 显示时长，默认 [SnackbarDuration.Short]。
     */
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) {
        // 纯文本提示 → Toast：不占布局、浮层之上也可见；时长跟随 duration 口径
        if (actionLabel == null) {
            val context = toastContext ?: return
            Toast.makeText(
                context,
                message,
                if (duration == SnackbarDuration.Long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
            ).show()
            return
        }
        // 带动作按钮 → Snackbar（Toast 放不了按钮；目前仅「撤销」类场景使用）
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
        val NoOp = SnackbarController(null, null, null)
    }
}

/** 当前可用的提示控制器；宿主未接入时为 [SnackbarController.NoOp]（调用即空操作）。 */
val LocalSnackbarController = staticCompositionLocalOf { SnackbarController.NoOp }

/** 在宿主处创建并记住 [SnackbarHostState]。 */
@Composable
fun rememberSnackbarHostState(): SnackbarHostState = remember { SnackbarHostState() }

/** 在宿主处由 [SnackbarHostState] 派生出提示控制器（Toast 用 Context 取自当前组合）。 */
@Composable
fun rememberSnackbarController(hostState: SnackbarHostState): SnackbarController {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(hostState, scope, context) {
        SnackbarController(hostState, scope, context)
    }
}
