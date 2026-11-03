package com.iji.app.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.ReimburseState
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.add.AddTransactionSheet
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.SubPageTopBar
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.map

/**
 * 借贷订单详情：金额与状态置顶，其余字段（时间 / 对方 / 账户 / 分类 / 备注…）按行平铺。
 *
 * 数据直接从已加载的账单流里按 id 取；找不到视为「已删除」，避免停留在空白页。
 */
@Composable
fun BorrowLendDetailScreen(
    txId: Long,
    onBack: () -> Unit = {},
    app: MainViewModel = viewModel(),
) {
    val transactionFlow = remember(app) {
        app.transactions.map<List<Transaction>, List<Transaction>?> { it }
    }
    val transactions by transactionFlow.collectAsStateWithLifecycle(initialValue = null)
    val tx = transactions?.firstOrNull { it.id == txId }
    var editing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubPageTopBar(title = "订单详情", onBack = onBack)
        when {
            transactions == null -> StateView(ContentState.Loading("加载中"))
            tx == null -> StateView(ContentState.Empty("订单不存在或已被删除"))
            else -> OrderDetailContent(
                tx = tx,
                onSetState = { app.setReimburseState(tx, it) },
                onEdit = { editing = true },
            )
        }
    }

    // 对方资料 / 金额 / 日期都在这个面板里改：详情页是借贷的主要入口，
    // 不能让用户先返回首页再找到这一行才能补联系方式。
    AddTransactionSheet(
        show = editing && tx != null,
        existing = tx,
        onDismiss = { editing = false },
        onConfirm = { draft ->
            tx?.let {
                app.updateTransaction(
                    it, draft.type, draft.categoryId, draft.amountCents, draft.note,
                    draft.account, draft.tags, draft.date, draft.excludeFromStats,
                    draft.excludeFromBudget, draft.reimburse, draft.discountCents, draft.attachments,
                    draft.merchant, draft.counterparty, draft.counterpartyContact, draft.counterpartyOrg,
                    draft.reimburseState, draft.reconciled,
                    ledgerId = draft.ledgerId, transferToAccount = draft.transferToAccount,
                    splits = draft.splits, refundOfId = draft.refundOfId,
                    minuteOfDay = draft.minuteOfDay,
                )
            }
            editing = false
        },
    )
}

@Composable
private fun OrderDetailContent(
    tx: Transaction,
    onSetState: (ReimburseState) -> Unit,
    onEdit: () -> Unit,
) {
    // 正常情况下一定能在分类树上命中；类型被改过时按收支方向兜底
    val direction = BorrowDirection.entries.firstOrNull { it.matches(tx) }
        ?: if (tx.type == TransactionType.INCOME) BorrowDirection.BORROW else BorrowDirection.LEND
    val category = CategoryCatalog.of(tx.categoryId)
    val snackbar = LocalSnackbarController.current
    val actions = tx.borrowOrderActions

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = LocalDimens.current.screenPadding,
            end = LocalDimens.current.screenPadding,
            top = 4.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sheet))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        direction.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    BorrowOrderStatusBadge(status = tx.borrowOrderStatus, direction = direction)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    formatMoney(tx.amountCents),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${tx.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        // 操作卡紧跟 hero：打开详情最常做的事就是推进状态，不该埋在长列表底部
        item(key = "actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sheet))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    tx.borrowOrderStatus.label(direction),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    nextStepHint(tx, direction),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { action ->
                        IjiTextAction(
                            text = action.label(direction),
                            onClick = {
                                onSetState(action.targetState)
                                snackbar.show(action.doneMessage(direction))
                            },
                            modifier = Modifier.weight(1f),
                            color = when (action) {
                                BorrowOrderAction.REVOKE -> TextSecondary
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "状态流转只改这笔借贷的标记：钱在记账时已经真实进出过，不会重复生成收支流水。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IjiTextAction(
                        text = "编辑对方资料 / 账单",
                        onClick = onEdit,
                        compact = true,
                        color = TextSecondary,
                    )
                }
            }
        }

        item(key = "info") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sheet))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                DetailRow("类型", direction.label)
                DetailRow("状态", tx.borrowOrderStatus.label(direction))
                DetailRow("时间", "${tx.date}")
                DetailRow("对方", tx.counterparty.ifBlank { "—" })
                if (tx.counterpartyOrg.isNotBlank()) DetailRow("单位", tx.counterpartyOrg)
                if (tx.counterpartyContact.isNotBlank()) {
                    DetailRow("联系方式", tx.counterpartyContact)
                }
                DetailRow("账户", tx.account.ifBlank { "—" })
                DetailRow("分类", category.name)
                DetailRow("账本", tx.ledgerId.ifBlank { "—" })
                if (tx.tags.isNotEmpty()) DetailRow("标签", tx.tags.joinToString("、"))
                if (tx.discountCents > 0L) DetailRow("优惠", formatMoney(tx.discountCents))
                DetailRow("计入统计", if (tx.excludeFromStats) "否" else "是")
            }
        }

        item(key = "note") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sheet))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("备注", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    tx.note.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** 当前状态下「下一步该做什么」的一句话说明，减少用户对按钮含义的猜测 */
private fun nextStepHint(tx: Transaction, direction: BorrowDirection): String {
    val borrow = direction == BorrowDirection.BORROW
    return when (tx.borrowOrderStatus) {
        BorrowOrderStatus.PENDING -> if (borrow) {
            "钱还没还：还了之后点「我已归还」等对方确认；双方当面结清也可以直接确认。"
        } else {
            "钱还没回来：对方还了之后点「对方已还款」；已到账可直接确认收款。"
        }

        BorrowOrderStatus.PENDING_CONFIRM -> if (borrow) {
            "已标记归还，等待对方确认；确认无误后点「确认已还清」，点错了可以撤销。"
        } else {
            "对方已还款，等待你确认到账；确认后点「确认已收款」，点错了可以撤销。"
        }

        BorrowOrderStatus.SETTLED -> if (borrow) {
            "这笔借款已还清；如果点错了，撤销后回到「待归还」。"
        } else {
            "这笔借款已收回；如果点错了，撤销后回到「待收回」。"
        }
    }
}

/** 信息行：标签固定宽度，值右对齐，多行时仍与右侧边距对齐 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(72.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
