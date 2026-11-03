package com.iji.app.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.Transaction
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.SubPageTopBar
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.theme.AppleOrange
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.map

/**
 * 借贷记录页：展示某一方向（借入 / 借出）的全部订单。
 *
 * 顶部两支切换只改本地状态，不重建路由——切回来时列表位置与已打开详情的历史都还在。
 */
@Composable
fun BorrowLendScreen(
    initialDirection: String,
    onBack: () -> Unit = {},
    onOpenOrder: (Long) -> Unit = {},
    app: MainViewModel = viewModel(),
) {
    // null = Room 首帧还没到（和「确实没有记录」区分开，只有前者显示加载态）
    val transactionFlow = remember(app) {
        app.transactions.map<List<Transaction>, List<Transaction>?> { it }
    }
    val transactions by transactionFlow.collectAsStateWithLifecycle(initialValue = null)

    var direction by rememberSaveable { mutableStateOf(BorrowDirection.ofName(initialDirection)) }
    val orders = remember(transactions, direction) {
        transactions?.let { borrowLendOrders(it, direction) }.orEmpty()
    }
    // 未结清 = 待归还 / 待收回 + 待确认；累计数在资产页（总借入 / 总借出）已能看到，这里只报「还欠多少」
    val unsettledCents = remember(orders) {
        orders.filter { it.borrowOrderStatus != BorrowOrderStatus.SETTLED }.sumOf { it.amountCents }
    }
    val snackbar = LocalSnackbarController.current

    val listState = rememberLazyListState()
    // 切换方向后回到顶部，否则会停留在上一个列表的滚动位置
    LaunchedEffect(direction) { listState.scrollToItem(0) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubPageTopBar(title = "借贷记录", onBack = onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                top = 4.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "direction-switch") {
                DirectionSwitch(direction = direction, onChange = { direction = it })
            }
            when {
                transactions == null -> item(key = "loading") {
                    StateView(ContentState.Loading("加载中"))
                }

                orders.isEmpty() -> item(key = "empty") {
                    StateView(
                        ContentState.Empty(
                            if (direction == BorrowDirection.BORROW) {
                                "还没有借入记录\n记账时选「借入」分类即可出现在这里"
                            } else {
                                "还没有借出记录\n记账时选「借出」分类即可出现在这里"
                            },
                        ),
                    )
                }

                else -> {
                    item(key = "summary") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "共 ${orders.size} 笔",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.weight(1f))
                            if (unsettledCents > 0L) {
                                Text(
                                    "未结清 ${formatMoney(unsettledCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = AppleRed,
                                )
                            } else {
                                Text(
                                    "已全部结清",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IncomeGreen,
                                )
                            }
                        }
                    }
                    items(orders, key = { it.id }) { tx ->
                        BorrowOrderRow(
                            tx = tx,
                            direction = direction,
                            onClick = { onOpenOrder(tx.id) },
                            onAction = { action ->
                                app.setReimburseState(tx, action.targetState)
                                snackbar.show(action.doneMessage(direction))
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 借入 / 借出切换：与资产页筛选同一套分段控件外观 */
@Composable
private fun DirectionSwitch(
    direction: BorrowDirection,
    onChange: (BorrowDirection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xxl))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BorrowDirection.entries.forEach { option ->
            val selected = option == direction
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(Radius.xxl))
                    .background(if (selected) MiuixPrimary else Color.Transparent)
                    .clickable(enabled = !selected) { onChange(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.White else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 单笔订单卡：上行是「分类图标 + 对方/分类 + 日期 + 金额与状态」，点进详情；
 * 下行是本状态可执行的操作（归还 / 确认 / 撤销），不点进详情也能当场推进流程。
 *
 * 操作放在**独立一行**而不是塞进可点击的上行里：Compose 里嵌套 clickable 虽然能工作，
 * 但点歪一点就会被外层吞掉，用户会以为按钮没反应。
 */
@Composable
private fun BorrowOrderRow(
    tx: Transaction,
    direction: BorrowDirection,
    onClick: () -> Unit,
    onAction: (BorrowOrderAction) -> Unit,
) {
    val category = CategoryCatalog.of(tx.categoryId)
    val status = tx.borrowOrderStatus
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sheet))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconImage(name = category.name, color = Color(category.color), size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tx.counterparty.ifBlank { category.name },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(tx.date)
                        // 单位是识别「哪个张三」的关键信息，跟在日期后面
                        if (tx.counterpartyOrg.isNotBlank()) append(" · ").append(tx.counterpartyOrg)
                        // 对方为空时标题就是分类名，副标题不再重复
                        if (tx.counterparty.isNotBlank()) append(" · ").append(category.name)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(tx.amountCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                BorrowOrderStatusBadge(status = status, direction = direction)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.padding(start = 2.dp).size(16.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tx.borrowOrderActions.forEach { action ->
                IjiTextAction(
                    text = action.label(direction),
                    onClick = { onAction(action) },
                    compact = true,
                    color = when (action) {
                        BorrowOrderAction.REVOKE -> TextSecondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

/** 借贷状态徽标：文案 + 颜色双编码（待办红 / 待确认橙 / 已结清绿），深色模式下靠低透明度底色保持可读 */
@Composable
internal fun BorrowOrderStatusBadge(
    status: BorrowOrderStatus,
    direction: BorrowDirection,
    modifier: Modifier = Modifier,
) {
    val color = status.badgeColor()
    Text(
        text = status.label(direction),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private fun BorrowOrderStatus.badgeColor(): Color = when (this) {
    BorrowOrderStatus.PENDING -> AppleRed
    BorrowOrderStatus.PENDING_CONFIRM -> AppleOrange
    BorrowOrderStatus.SETTLED -> IncomeGreen
}

