package com.iji.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.TextSecondary

/**
 * 通用内容状态：加载中 / 失败 / 空数据。
 *
 * 用于把各页面散落的 `isLoading` + `CircularProgressIndicator` + `errorMsg` + "暂无数据"
 * 之类实现统一收敛到一处，保证交互状态与异常提示在不同场景下一致。
 *
 * - [Loading]：加载中（转圈 + 可选文案）
 * - [Error]：加载 / 处理失败（带 message，可选重试）
 * - [Empty]：空数据（带 message）
 */
sealed interface ContentState {

    /** 加载中。`text` 为空则不显示文字（只转圈）。 */
    data class Loading(val text: String? = null) : ContentState

    /** 失败。`message` 为展示给用户的提示。 */
    data class Error(val message: String) : ContentState

    /** 空数据。`message` 默认"暂无数据"。 */
    data class Empty(val message: String = "暂无数据") : ContentState
}

/**
 * 占位形式（尺寸），决定组件如何占据空间。
 */
enum class StateSize {
    /**
     * 紧凑内联：不占区块高度，横向排列（转圈 + 文案）。
     * 适用于**按钮内部**、行内提示（如"解析中…""测试中…"）。
     */
    Inline,

    /**
     * 区块占位：占满宽度并居中，带上下留白。
     * 适用于列表 / 图表 / 卡片区域的空态或失败态。
     */
    Area,
}

/**
 * 统一的状态占位视图。
 *
 * **基础用法**
 * ```
 * // 1）按钮内「加载中」（紧凑）
 * Button(onClick = { ... }) {
 *     if (isParsing) {
 *         StateView(ContentState.Loading("解析中"), size = StateSize.Inline)
 *     } else {
 *         Text("AI 解析")
 *     }
 * }
 *
 * // 2）图表 / 列表空态（区块占位）
 * if (items.isEmpty()) {
 *     StateView(ContentState.Empty("暂无数据"))
 * } else {
 *     Chart(items)
 * }
 *
 * // 3）失败并支持重试
 * StateView(
 *     state = ContentState.Error("加载失败，请检查网络"),
 *     onRetry = { viewModel.reload() },
 * )
 *
 * // 4）用状态变量驱动（正常内容时传 null，组件不渲染任何东西）
 * val state: ContentState? = when {
 *     isLoading -> ContentState.Loading()
 *     error != null -> ContentState.Error(error)
 *     list.isEmpty() -> ContentState.Empty()
 *     else -> null
 * }
 * StateView(state)
 * ```
 *
 * **后续可扩展方向**
 * - **空态插图**：给 [ContentState.Empty] 增加 `icon: ImageVector?` 或 `content: @Composable () -> Unit`，
 *   支持插画型空态（当前仅文字）。
 * - **骨架屏**：新增 `ContentState.Skeleton` / `StateSize.Skeleton`，加载时用骨架占位替代转圈，
 *   避免内容区域尺寸跳动。
 * - **整页错误页**：把 [StateSize] 扩展为 `FullScreen`，配合 `onRetry` 形成整页错误页与下拉重试。
 * - **对接 ViewModel**：定义 `UiState<T>`（`Loading / Success(data) / Error / Empty`）并提供
 *   `UiState<*>.toContentState()`，即可 `StateView(uiState.toContentState())` 一行接入，
 *   进一步消除各页面的 when 模板代码。
 * - **国际化**：把文案默认值改为 `stringResource(...)`，便于多语言；当前默认值写死为中文。
 * - **换用 Miuix 控件**：目前转圈用 Material3 `CircularProgressIndicator`、
 *   重试用 Miuix `TextButton`；待 Miuix 提供 Progress 组件后可整体替换，接口不变。
 *
 * @param state 当前状态；**传 null 表示状态正常**，组件不渲染（便于用状态变量直接驱动）。
 * @param modifier 作用于最外层容器。
 * @param size 占位形式，见 [StateSize]。
 * @param contentColor 文案 / 转圈颜色；默认取 [LocalContentColor]，
 *   因此放进主色按钮内会自动继承按钮的 onPrimary（白色）。
 * @param errorColor 失败文案颜色，默认 [AppleRed]。
 * @param onRetry 非空时展示"重试"入口；[StateSize.Inline] 下只显示文案不显示按钮。
 * @param retryText 重试按钮文案。
 * @param areaVerticalPadding 仅 [StateSize.Area] 生效的上下留白。
 */
@Composable
fun StateView(
    state: ContentState?,
    modifier: Modifier = Modifier,
    size: StateSize = StateSize.Area,
    contentColor: Color = LocalContentColor.current,
    errorColor: Color = AppleRed,
    onRetry: (() -> Unit)? = null,
    retryText: String = "重试",
    areaVerticalPadding: Dp = 32.dp,
) {
    if (state == null) return

    when (size) {
        StateSize.Inline -> InlineState(
            state = state,
            modifier = modifier,
            contentColor = contentColor,
            errorColor = errorColor,
        )

        StateSize.Area -> Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = areaVerticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            AreaState(
                state = state,
                contentColor = contentColor,
                errorColor = errorColor,
                onRetry = onRetry,
                retryText = retryText,
            )
        }
    }
}

/** 紧凑内联态：横向「转圈 + 文案」或单行提示。 */
@Composable
private fun InlineState(
    state: ContentState,
    modifier: Modifier = Modifier,
    contentColor: Color,
    errorColor: Color,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is ContentState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
                if (!state.text.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(state.text, color = contentColor)
                }
            }

            is ContentState.Error -> Text(
                state.message,
                color = errorColor,
                style = MaterialTheme.typography.bodySmall,
            )

            is ContentState.Empty -> Text(
                state.message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 区块占位态：居中展示，失败时可选重试入口。 */
@Composable
private fun AreaState(
    state: ContentState,
    contentColor: Color,
    errorColor: Color,
    onRetry: (() -> Unit)?,
    retryText: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            is ContentState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
                if (!state.text.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.text, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }

            is ContentState.Error -> {
                Text(
                    state.message,
                    color = errorColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (onRetry != null) {
                    Spacer(Modifier.height(8.dp))
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = retryText,
                        onClick = onRetry,
                        modifier = Modifier.ijiButtonFocusRing(
                            shape = com.kyant.capsule.ContinuousRoundedRectangle(
                                top.yukonga.miuix.kmp.basic.ButtonDefaults.CornerRadius
                            )
                        ),
                    )
                }
            }

            is ContentState.Empty -> Text(
                state.message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
