package com.iji.app.ui.components.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayListPopup

/**
 * 下拉菜单弹窗圆角 25dp。
 *
 * 官方 `ListPopupContent` 内部硬编码 16dp，此处按「下拉菜单组件提取」README 规格用外层 clip 覆盖为 25dp
 * （圆角实现为 `com.kyant.capsule` 的 G2 连续曲率超椭圆，与弹窗其余部分观感一致）。
 */
val DropdownPopupCornerRadius: Dp = 25.dp

/** 下拉菜单条目圆角 16dp（README 规格：逐条目药丸状圆角）。 */
val DropdownItemCornerRadius: Dp = 16.dp

/**
 * 图标锚定式下拉菜单：把 [trigger]（通常是三点/齿轮等图标按钮）与弹出菜单封在一起。
 *
 * 与同包的 [AnchoredDropdownMenu]（行触发式）共用同一套官方 Miuix 弹窗实现，因此全站下拉菜单
 * 行为完全一致：
 * - 弹出/收起动画、方向裁剪揭示、背景变暗（[PopupPositionProvider] 定位，空间不足自动上翻）；
 * - 点击菜单外部关闭、按系统返回键关闭（预测性返回手势跟随）；
 * - 条目选中 `HapticFeedbackType.Confirm` 震动，选中后自动收起；
 * - 圆角统一为弹窗 [DropdownPopupCornerRadius] / 条目 [DropdownItemCornerRadius]。
 *
 * 锚点为包裹 [trigger] 的 [Box]（官方 `ListPopupLayout` 取所在父节点为锚点），因此
 * [alignment] 决定弹窗与图标的水平对齐方式：图标在右缘用 [PopupPositionProvider.Align.End]，
 * 在左缘用 [PopupPositionProvider.Align.Start]。
 *
 * ⚠️ 调用点必须位于 Miuix `Scaffold` 子树内（弹层宿主由它提供），否则弹窗不渲染。
 *
 * @param expanded 弹窗是否展开。
 * @param onDismissRequest 请求关闭（点外部 / 返回键 / 选中条目都会走这里）。
 * @param items 条目列表；用 [DropdownItem.selected] 标记选中项（显示对勾与主题色）。
 * @param onDismissFinished 收起动画结束后的回调（用于复位触发器按压态）。
 * @param trigger 触发器内容，由本组件包裹以确定锚点与展开手势。
 */
@Composable
fun AnchoredIconDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<DropdownItem>,
    modifier: Modifier = Modifier,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.End,
    verticalMargin: Dp = 4.dp,
    minWidth: Dp = 160.dp,
    maxHeight: Dp? = null,
    onDismissFinished: (() -> Unit)? = null,
    trigger: @Composable () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val positionProvider = remember(verticalMargin) {
        ListPopupDefaults.dropdownPositionProvider(verticalMargin = verticalMargin)
    }

    // 关闭中标记：弹窗收起动画期间组件仍留在组合内（否则会丢失退出动画），
    // 这段时间若不拦截，用户的连续点击会重复触发动作（例如把「删除」点成两次）。
    // 展开时清零，收起动画结束（onDismissFinished）后再清零，保证每次开合都是干净的一轮。
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        closing = !expanded
    }

    Box(modifier = modifier) {
        trigger()
        // 常驻组合（仅条目为空时不渲染），把进出场动画交给官方 ListPopupLayout 驱动，
        // 否则关闭时组件立即离组会丢失退出动画。
        if (items.isNotEmpty()) {
            OverlayListPopup(
                show = expanded,
                // 弹窗 25dp 圆角：官方内部为 16dp，用外层 clip 覆盖为 README 规格
                popupModifier = Modifier.clip(ContinuousRoundedRectangle(DropdownPopupCornerRadius)),
                popupPositionProvider = positionProvider,
                alignment = alignment,
                onDismissRequest = onDismissRequest,
                onDismissFinished = {
                    closing = false
                    onDismissFinished?.invoke()
                },
                maxHeight = maxHeight,
                minWidth = minWidth,
            ) {
                ListPopupColumn {
                    items.forEachIndexed { index, item ->
                        Box(modifier = Modifier.clip(ContinuousRoundedRectangle(DropdownItemCornerRadius))) {
                            DropdownImpl(
                                item = item,
                                optionSize = items.size,
                                isSelected = item.selected,
                                index = index,
                                dropdownColors = dropdownColors,
                                onSelectedIndexChange = select@{
                                    // 关闭动画期间不再响应，避免一次操作被连续点击触发多次
                                    if (closing) return@select
                                    closing = true
                                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onDismissRequest()
                                    item.onClick?.invoke()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
