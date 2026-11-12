package com.iji.app.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iji.app.MainViewModel
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.ui.components.BillDetailDialog
import com.iji.app.ui.components.TransactionRow
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.ExpenseAmount
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.util.sharedMainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

/**
 * 标签管理下钻：带某个标签的**全部账单**（跨账本），点一行看只读详情。
 *
 * 标签是 DataStore 里的字符串池、账单的 `tags` 是逗号分隔串，筛选走
 * [MainViewModel.transactionsByTag] 精确匹配。和分类下钻一样只做「看」不做「改」：
 * 编辑仍回首页，避免在这里再造一套编辑链路、也避免下钻页误改账。
 *
 * 改名 / 删除标签不会回写已记账单上的标签字符串，所以旧名的账单仍挂在旧标签下；
 * 空态里把这个口径说清楚，免得用户以为账单丢了。
 */
@Composable
fun TagBillsScreen(
    tag: String,
    onBack: () -> Unit,
    app: MainViewModel = sharedMainViewModel(),
) {
    val rows by remember(tag) { app.transactionsByTag(tag) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var detailTx by remember { mutableStateOf<Transaction?>(null) }

    val expenseCents = rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
    val incomeCents = rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }

    MineSubPage(title = if (tag.isBlank()) "标签账单" else "#$tag", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                top = ijiTopBarContentPadding(),
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                TagSummaryCard(
                    billCount = rows.size,
                    expenseCents = expenseCents,
                    incomeCents = incomeCents,
                )
            }
            if (rows.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = Radius.lg,
                        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("还没有带「#$tag」的账单", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "标签改名不会同步修改已记账单，旧名账单仍挂在旧标签下",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                items(rows, key = { it.id }) { tx ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { detailTx = tx },
                        cornerRadius = Radius.lg,
                        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
                    ) {
                        TransactionRow(tx = tx)
                    }
                }
            }
        }
    }

    // 只读详情：不传 onEdit，弹窗只给「关闭」，编辑回首页/列表进行
    BillDetailDialog(tx = detailTx, onDismiss = { detailTx = null })
}

/** 汇总：笔数 + 支出 / 收入合计；跨账本口径，转账不计入收支合计 */
@Composable
private fun TagSummaryCard(
    billCount: Int,
    expenseCents: Long,
    incomeCents: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.sheet,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "共 $billCount 笔账单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "全部账本",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Row {
                Column(Modifier.weight(1f)) {
                    Text("支出", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(
                        formatMoney(expenseCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseAmount,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("收入", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(
                        formatMoney(incomeCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IncomeGreen,
                    )
                }
            }
        }
    }
}
