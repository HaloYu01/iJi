package com.iji.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.theme.ExpenseAmount
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.TextSecondary

/**
 * 账单只读详情：列表里点一行先看这里，编辑是详情里的显式动作。
 *
 * 之前列表单击直接进编辑面板，手滑就改账（编辑面板保存即落库）；
 * 详情把「看」和「改」拆成两步，成本只有一次点击。
 *
 * [onEdit] 为 null 时是纯只读场景（标签 / 分类下钻），按钮换成「关闭」而不是
 * 留一个点了没反应的「编辑」。
 */
@Composable
fun BillDetailDialog(
    tx: Transaction?,
    onDismiss: () -> Unit,
    onEdit: ((Transaction) -> Unit)? = null,
) {
    val bill = tx
    val editable = onEdit != null
    IjiDialog(
        show = bill != null,
        onDismissRequest = onDismiss,
        title = "账单详情",
        dismissText = "关闭",
        confirmText = if (editable) "编辑" else "关闭",
        showDismiss = editable,
        onConfirm = bill?.let { b ->
            {
                if (editable) onEdit?.invoke(b) else onDismiss()
            }
        },
    ) {
        if (bill != null) BillDetailContent(bill)
    }
}

@Composable
private fun BillDetailContent(tx: Transaction) {
    val typeLabel = when (tx.type) {
        TransactionType.EXPENSE -> "支出"
        TransactionType.INCOME -> "收入"
        TransactionType.TRANSFER -> "转账"
    }
    val signed = when (tx.type) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "-"
        TransactionType.TRANSFER -> ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$signed${formatMoney(tx.amountCents)}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            // 全局约定：消费红 / 收入绿，转账保持中性色
            color = when (tx.type) {
                TransactionType.INCOME -> IncomeGreen
                TransactionType.EXPENSE -> ExpenseAmount
                TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
            },
        )
        DetailLine("分类", "${CategoryCatalog.of(tx.categoryId).name} · $typeLabel")
        DetailLine(
            "账户",
            if (tx.type == TransactionType.TRANSFER) {
                "${tx.account.ifBlank { "未指定" }} → ${tx.transferToAccount.ifBlank { "未指定" }}"
            } else {
                tx.account.ifBlank { "未指定" }
            },
        )
        // 时分是可选的（-1 = 只记到天）：有才拼在日期后面，不要凭空补 00:00
        val clock = if (tx.minuteOfDay >= 0) {
            "%02d:%02d".format(tx.minuteOfDay / 60, tx.minuteOfDay % 60)
        } else {
            null
        }
        DetailLine("日期", listOfNotNull(tx.date.toString(), clock).joinToString(" "))
        if (tx.note.isNotBlank()) DetailLine("备注", tx.note)
        if (tx.tags.isNotEmpty()) DetailLine("标签", tx.tags.joinToString("、"))
        if (tx.merchant.isNotBlank()) DetailLine("商家", tx.merchant)
        if (tx.counterparty.isNotBlank()) DetailLine("对方", tx.counterparty)
        if (tx.discountCents > 0L) DetailLine("优惠", formatMoney(tx.discountCents))
        if (tx.splits.isNotEmpty()) {
            DetailLine("组合付款", tx.splits.joinToString("、") { "${it.account} ${formatMoney(it.amountCents)}" })
        }
        if (tx.excludeFromStats || tx.excludeFromBudget) {
            DetailLine(
                "统计",
                listOfNotNull(
                    "不计入收支".takeIf { tx.excludeFromStats },
                    "不计入预算".takeIf { tx.excludeFromBudget },
                ).joinToString("、"),
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
