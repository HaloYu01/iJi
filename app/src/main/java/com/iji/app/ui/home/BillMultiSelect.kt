package com.iji.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.data.model.Category
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalOverlayBackStack
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.DividerGray
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary

/**
 * 多选模式的返回键处理：**返回 = 退出多选**，只清掉选择、留在当前页面。
 *
 * 判定与优先级（由高到低）：
 * 1. 有弹层打开（二次确认 / 批量分类 / 记一笔抽屉等，均登记在 [LocalOverlayBackStack]）
 *    → 本回调 `enabled = false`，返回键只关弹层，多选状态与已选项**原样保留**；
 * 2. 弹层全关且 [active] 为 true → 拦截返回并执行 [onExit]（清空已选、收起操作栏、行高亮消失），
 *    事件被消费，不会继续下传给路由出栈 / 退出应用；
 * 3. [active] 为 false（普通状态）→ 不注册任何回调，返回键行为与改动前完全一致
 *    （NavHost 出栈，栈空则由系统退出应用）。
 *
 * 为什么必须显式判掉「有弹层」：返回键同一时刻只能有一个消费者。若这里不判，
 * 弹窗打开时按返回会先退出多选、弹窗仍留在屏幕上，状态与视觉错位。
 *
 * @param active 是否处于多选状态（普通状态传 false，等于不启用）。
 * @param onExit 退出多选的动作，应与点「完成」完全一致。
 */
@Composable
fun MultiSelectBackHandler(active: Boolean, onExit: () -> Unit) {
    val overlayStack = LocalOverlayBackStack.current
    // entries 是 SnapshotStateList：弹层一开一关都会触发重组，enabled 随之切换
    val overlayOpen = !overlayStack.isEmpty
    val currentOnExit by rememberUpdatedState(onExit)
    BackHandler(enabled = active && !overlayOpen) { currentOnExit() }
}

/**
 * 多选底部操作栏。
 *
 * 关键点：
 * - 由调用方放在**根 Box 内**并 `align(BottomCenter)`，与列表同级 → 不随列表滚动、不参与页面转场；
 * - `bottomPadding` 由调用方按悬浮 tab 栏高度传入，避免两个条叠在一起；
 * - 数量/合计随选中集合实时刷新，0 项时三个动作按钮整体禁用（不隐藏，避免布局跳动）。
 */
@Composable
fun BillMultiSelectBar(
    selectedCount: Int,
    expenseCents: Long,
    incomeCents: Long,
    allSelected: Boolean,
    /** 当前列表有可选项时「全选」才可用 —— 它不能跟着"已选数量"一起禁用（0 选中时正是要全选） */
    canSelectAll: Boolean,
    onToggleSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExit: () -> Unit,
    onDelete: () -> Unit,
    onCategory: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = selectedCount > 0
    // 混选时分开陈述，避免把收入当成支出一并「合计」
    val totalText = when {
        expenseCents > 0 && incomeCents > 0 ->
            "支出 ${formatMoney(expenseCents)} · 收入 ${formatMoney(incomeCents)}"
        incomeCents > 0 -> "合计 ${formatMoney(incomeCents)}"
        else -> "合计 ${formatMoney(expenseCents)}"
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        shape = RoundedCornerShape(Radius.sheet),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, DividerGray.copy(alpha = 0.5f)),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "已选 $selectedCount 项",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        totalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BarTextAction(if (allSelected) "取消全选" else "全选", canSelectAll, onToggleSelectAll)
                    BarTextAction("取消选择", enabled, onClearSelection)
                    BarTextAction("完成", true, onExit, color = MiuixPrimary)
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BarAction("删除", AppleRed, enabled, Modifier.weight(1f), onDelete)
                BarAction("分类", MiuixPrimary, enabled, Modifier.weight(1f), onCategory)
                BarAction("导出", MiuixPrimary, enabled, Modifier.weight(1f), onExport)
            }
        }
    }
}

/** 多选栏里的文字操作：统一走胶囊按钮，不再是一串看不出能点的灰字 */
@Composable
private fun BarTextAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    color: Color = TextSecondary,
) {
    IjiTextAction(
        text = text,
        onClick = onClick,
        enabled = enabled,
        color = color,
        compact = true,
    )
}

@Composable
private fun BarAction(
    text: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(color.copy(alpha = if (enabled) 0.12f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = if (enabled) 1f else 0.35f),
        )
    }
}

/**
 * 批量改分类。
 *
 * 只列出与所选账单**同类型**的分类（支出/收入），避免把支出改成收入分类后金额口径打架；
 * 调用方在混选时不会打开本弹窗。
 */
@Composable
fun BulkCategoryDialog(
    show: Boolean,
    count: Int,
    type: TransactionType,
    currentCategoryIds: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val options = if (type == TransactionType.EXPENSE) CategoryCatalog.expenses else CategoryCatalog.incomes
    var picked by remember(show) { mutableStateOf("") }
    // 全部账单同属一个分类时把它预选中，方便"再点一次确认"
    val preselected = currentCategoryIds.singleOrNull().orEmpty()

    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = "批量分类",
        summary = "将 $count 笔账单归到同一分类",
        dismissText = "取消",
        confirmText = "确定",
        confirmEnabled = picked.isNotEmpty(),
        onConfirm = { onPick(picked) },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(options, key = { it.id }) { category ->
                CategoryCell(
                    category = category,
                    selected = picked == category.id || (picked.isEmpty() && category.id == preselected),
                    onClick = { picked = category.id },
                )
            }
        }
    }
}

@Composable
private fun CategoryCell(category: Category, selected: Boolean, onClick: () -> Unit) {
    val tint = Color(category.color)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(if (selected) tint.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIconImage(
            name = category.name,
            color = tint,
            size = 34.dp,
            categoryId = category.id,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            category.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) MiuixPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
