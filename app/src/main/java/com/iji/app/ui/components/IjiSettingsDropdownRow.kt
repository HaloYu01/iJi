package com.iji.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.iji.app.ui.components.menu.LiquidGlassDropdownMenu
import com.iji.app.ui.components.menu.LiquidGlassDropdownMenuItem
import com.iji.app.ui.components.menu.LiquidGlassDropdownOverlayHost
import com.iji.app.ui.effects.glass.LocalAppBackdrop

/**
 * 设置页的「少量选项」下拉行：把偏好设置页里那套
 * `Box(锚点) + IjiSettingsRow + IjiSettingDropdownValue + popup 层 LiquidGlass 面板`
 * 收成一行调用，避免每个下拉都抄 20 行窗口坐标换算。
 *
 * 全局选择器口径：
 * - **≤ [maxDropdownItems] 项**：原地展开 LiquidGlass 下拉面板（用户 2026-11-10 定案，
 *   与偏好设置页的「主题颜色 / 周开始日」一致）；
 * - **超过该数量**：自动降级为 [WheelOptionDialog] 滚轮弹窗（`wrapAround=false`，
 *   选项顺序由调用方按创建顺序给出）；
 * - 布尔量用 [IjiSettingSwitch]，日期时间用 `DateTimePickerDialog`，自由文本就地编辑。
 *
 * ⚠️ 下拉面板必须挂在 `LiquidGlassDropdownOverlayHost`（Miuix Scaffold 的 popup 层）。
 * 放进 NavHost 录制树会让 `drawBackdrop` 把自己录进采样图，实测触发 RenderThread SIGSEGV。
 * `LocalAppBackdrop` 为空时（页面不在玻璃采样层内）直接走滚轮，绝不退化成页内 Popup。
 */
@Composable
fun <T> IjiSettingsDropdownRow(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    maxDropdownItems: Int = 5,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var dropdownOpen by remember { mutableStateOf(false) }
    val anchorBounds = remember { mutableStateOf(Rect.Zero) }
    val hostOrigin = remember { mutableStateOf(Offset.Zero) }
    val fraction = remember { Animatable(0f) }
    val backdrop = LocalAppBackdrop.current
    val useDropdown = options.size <= maxDropdownItems && backdrop != null

    Box(
        modifier = modifier
            .onGloballyPositioned { anchorBounds.value = it.boundsInWindow() },
    ) {
        IjiSettingsRow(
            title = title,
            subtitle = subtitle,
            onClick = if (enabled) {
                { if (useDropdown) dropdownOpen = !dropdownOpen else pickerOpen = true }
            } else {
                null
            },
        ) {
            IjiSettingDropdownValue(
                label(selected),
                modifier = Modifier.graphicsLayer {
                    // 一镜到底（与偏好设置页「主题颜色 / 周开始日」逐字同款）：
                    // 行尾取值文字 + 上下双箭头随面板 fraction 向面板内部（左下）漂移淡出，
                    // 面板从行位置长出；收起时 iconK 更小 → 文字更早浮现，与容器回弹衔接。
                    val iconK = if (dropdownOpen) 2.5f else 1.7f
                    val p = (fraction.value * iconK).coerceIn(0f, 1f)
                    alpha = 1f - p
                    translationX = -p * 10.dp.toPx()
                    translationY = p * 10.dp.toPx()
                },
            )
        }
    }

    if (!useDropdown) {
        WheelOptionDialog(
            show = pickerOpen,
            options = options,
            selected = selected,
            onDismissRequest = { pickerOpen = false },
            onConfirm = {
                pickerOpen = false
                onSelect(it)
            },
            title = title,
            label = label,
        )
    } else {
        LiquidGlassDropdownOverlayHost(
            expanded = dropdownOpen,
            onOutsideTap = { dropdownOpen = false },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { hostOrigin.value = it.positionInWindow() },
            ) {
                LiquidGlassDropdownMenu(
                    show = dropdownOpen,
                    backdrop = backdrop!!,
                    fraction = fraction,
                    onDismiss = { dropdownOpen = false },
                    modifier = Modifier.graphicsLayer {
                        val b = anchorBounds.value
                        // 与偏好设置页一致：控制盒右上角恒 = 行右缘 + 26dp；
                        // 面板从行位置长出，收起缩回行位置与行内容衔接
                        alpha = (fraction.value.coerceAtLeast(0f) / 0.3f).coerceIn(0f, 1f)
                        translationX = b.right + 26.dp.toPx() - size.width - hostOrigin.value.x
                        translationY = b.top - 26.dp.toPx() - hostOrigin.value.y
                    },
                ) {
                    options.forEach { value ->
                        LiquidGlassDropdownMenuItem(
                            text = label(value),
                            selected = value == selected,
                            onClick = {
                                dropdownOpen = false
                                onSelect(value)
                            },
                        )
                    }
                }
            }
        }
    }
}
