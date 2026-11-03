package com.iji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ui.components.dialog.GlassDialog
import com.iji.app.ui.theme.Radius
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全局统一弹窗（应用内所有弹窗都走这里，样式与动效集中维护）。
 *
 * **外壳视觉/动效**：中央弹出、40dp 连续曲率大圆角、手机端上滑入场 / 大屏缩放入场、遮罩渐暗、
 * 预测性返回手势跟随（下拉/缩放回弹）、边缘光；背景为不透明主题底色（不做毛玻璃）。
 *
 * **底部操作区**：不传 [onConfirm] 时只渲染 [content]（如纯列表选择弹窗）；
 * 传了 [onConfirm] 则自动在底部渲染「取消 / 确定」操作行，样式统一见 [IjiDialogActions]。
 * 需要自定义按钮文案/颜色（如「删除」用红色）时，直接在 [content] 里放 [IjiDialogActions]。
 *
 * ⚠️ 调用点必须位于 Miuix `Scaffold` 子树内（弹层宿主由它提供），否则弹窗不渲染。
 *
 * @param show 是否显示。
 * @param onDismissRequest 请求关闭（点遮罩 / 系统返回 / 点取消都会走这里）。
 * @param title 标题。
 * @param summary 摘要文案（标题下方的说明）。
 * @param dismissText 取消按钮文案。
 * @param confirmText 确定按钮文案。
 * @param confirmColor 确定按钮主色（默认主题色，破坏性操作可传红）。
 * @param confirmEnabled 确定按钮是否可点。
 * @param onConfirm 确定回调；为 null 则底部不渲染操作行。
 * @param autoFocusRequester 需要键盘输入的弹窗：传入挂在**首个输入框**上的 [FocusRequester]，
 *   弹窗弹出的同时会自动聚焦并唤起输入法，用户可直接输入、无需再点一下。
 *   若该 requester 尚未挂到任何节点上，会静默跳过（不会崩）。
 * @param showDismiss 是否渲染「取消」按钮。面板式弹窗（内容里已经有主操作，只需要一个关闭出口）
 *   传 `false`，避免出现「取消 / 关闭」两个语义相同的按钮。
 * @param content 弹窗内容（无滚轮类内容时留空即可）。
 */
@Composable
fun IjiDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    summary: String? = null,
    dismissText: String = "取消",
    confirmText: String = "确定",
    confirmColor: Color? = null,
    confirmEnabled: Boolean = true,
    showDismiss: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    autoFocusRequester: FocusRequester? = null,
    content: @Composable () -> Unit = {},
) {
    // 弹出即聚焦 + 唤起输入法（与弹窗弹出同步，省掉"再点一下输入框"）
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(show, autoFocusRequester) {
        val requester = autoFocusRequester
        if (show && requester != null) {
            delay(100) // 等弹层内容挂载完成再请求焦点，否则输入法可能不弹
            runCatching { requester.requestFocus() }
            keyboardController?.show()
        }
    }

    // 登记进全局弹窗返回栈：返回键只关闭「最后打开」的那个弹窗，
    // 弹窗里再套弹窗时按打开顺序逐层退出（详见 OverlayBackStack）。
    RegisterOverlayBackLayer(show = show, onDismiss = onDismissRequest, label = "IjiDialog:$title")

    GlassDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
            if (onConfirm != null) {
                IjiDialogActions(
                    onDismiss = onDismissRequest.takeIf { showDismiss },
                    onConfirm = onConfirm,
                    dismissText = dismissText,
                    confirmText = confirmText,
                    confirmColor = confirmColor,
                    confirmEnabled = confirmEnabled,
                )
            }
        }
    }
}

/**
 * 弹窗底部操作行：左「取消」（次要样式）、右「确定」（主题色高亮胶囊）。
 *
 * @param onDismiss 取消回调；传 null 则只渲染确定按钮（占满整行）。
 * @param onConfirm 确定回调。
 * @param dismissText 取消按钮文案。
 * @param confirmText 确定按钮文案。
 * @param confirmColor 确定按钮主色；默认主题色。
 * @param confirmEnabled 确定按钮是否可点（不可点时降透明度）。
 * @param topPadding 与上方内容的间距。
 */
@Composable
fun IjiDialogActions(
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    dismissText: String = "取消",
    confirmText: String = "确定",
    confirmColor: Color? = null,
    confirmEnabled: Boolean = true,
    topPadding: Int = 16,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onDismiss != null) {
            IjiDialogActionButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
        }
        IjiDialogActionButton(
            text = confirmText,
            onClick = onConfirm,
            prominent = true,
            color = confirmColor,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 弹窗底部按钮：纯 Compose 实现，胶囊连续圆角（[Radius.lg]）。
 * [prominent] 为主题色高亮（半透明主题色底 + 主题色字），否则为透明底 + 次要文字色。
 */
@Composable
fun IjiDialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    color: Color? = null,
    enabled: Boolean = true,
) {
    val accent = color ?: MiuixTheme.colorScheme.primary
    val bgColor = if (prominent) accent.copy(alpha = 0.12f) else Color.Transparent
    val textColor = if (prominent) accent else MiuixTheme.colorScheme.onSurfaceVariantActions
    val shape = ContinuousRoundedRectangle(Radius.lg)
    // 弹窗按钮原本是 clickable(indication = null)，完全没有任何按压反馈，
    // 也不会继承 LocalIndication。这里显式接上统一反馈：水波纹 + 按压缩放 + 边缘光晕。
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(44.dp)
            .ijiButtonFocusRing(shape = shape)
            .background(bgColor, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = IjiButtonFx.PressIndication,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = if (enabled) textColor else textColor.copy(alpha = 0.4f),
        )
    }
}
