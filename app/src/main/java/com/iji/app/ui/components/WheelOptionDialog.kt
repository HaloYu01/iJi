package com.iji.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「滚轮选项」弹窗：用 Miuix [NumberPicker] 滚轮选择一组选项。
 *
 * **交互**（与时段选择器一致）：滚到中间的那一项即为**暂定值**（无需手点选项），
 * 底部「取消 / 确定」提交或放弃；每次打开按 [selected] 重置到当前值。
 * 动效：惯性滑动、吸附回弹、居中项放大、两侧淡出衰减。
 *
 * 外壳为 [IjiDialog]（玻璃弹窗 + 统一底部操作行）。
 * 需要滚轮外观更小/更大时调 [itemHeight] / [visibleItemCount]；文字风格用 [textStyle]。
 *
 * ⚠️ 调用点必须在 Miuix `Scaffold` 子树内。
 *
 * @param show 是否显示。
 * @param options 选项列表（非空；为空时不渲染）。
 * @param selected 打开时的当前选项（不在 [options] 中时落到第一项）。
 * @param title 弹窗标题。
 * @param onDismissRequest 关闭/取消回调。
 * @param onConfirm 确定回调，回传滚轮居中那一项。
 * @param label 选项显示文案，默认 `toString()`。
 * @param itemHeight 单个滚轮项高度，默认 60dp（同时段选择器）。
 * @param visibleItemCount 可见项数，奇数且 ≥ 3，默认 3。
 * @param wrapAround 是否无限循环滚动。默认 **false**：滚到第一项 / 最后一项就停住，不再绕回另一端。
 *   日期时间选择器是 [DateTimePickerDialog]，不走本组件，行为不受影响。
 * @param textStyle 滚轮文字样式，默认 `title2`。
 */
@Composable
fun <T> WheelOptionDialog(
    show: Boolean,
    options: List<T>,
    selected: T,
    onDismissRequest: () -> Unit,
    onConfirm: (T) -> Unit,
    title: String? = null,
    label: (T) -> String = { it.toString() },
    itemHeight: Dp = 60.dp,
    visibleItemCount: Int = 3,
    wrapAround: Boolean = false,
    textStyle: TextStyle = MiuixTheme.textStyles.title2,
) {
    if (options.isEmpty()) return

    // 每次打开重置为当前值；滚动过程中只改这里的暂定值，点「确定」才回传
    var index by remember(show) {
        mutableIntStateOf(options.indexOf(selected).coerceAtLeast(0))
    }

    IjiDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        onConfirm = { onConfirm(options[index.coerceIn(options.indices)]) },
    ) {
        NumberPicker(
            value = index,
            onValueChange = { index = it },
            range = options.indices,
            visibleItemCount = visibleItemCount,
            itemHeight = itemHeight,
            label = { label(options[it]) },
            wrapAround = wrapAround,
            textStyle = textStyle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
