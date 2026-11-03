package com.iji.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.formatSignedMoney
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.mine.MineSubPage
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import java.time.LocalDate

/**
 * 统计页下钻：某个分类在指定区间内的账单列表。
 *
 * 只做「看」不做「改」：编辑仍回首页/详情，避免在这里再造一套编辑链路。
 * 区间由调用方显式传入（统计页的当期区间），不读全局选中月，
 * 这样从「周 / 年」维度下钻时也能拿到正确范围。
 */
@Composable
fun CategoryBillsScreen(
    categoryId: String,
    start: LocalDate,
    end: LocalDate,
    onBack: () -> Unit,
    app: MainViewModel = viewModel(),
) {
    val allTransactions by app.transactions.collectAsStateWithLifecycle()
    val rows = remember(allTransactions, categoryId, start, end) {
        allTransactions
            .filter {
                it.type == TransactionType.EXPENSE &&
                    it.categoryId == categoryId &&
                    !it.date.isBefore(start) && !it.date.isAfter(end)
            }
            .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
    }
    val netCents = rows.sumOf { it.amountCents }
    val category = CategoryCatalog.of(categoryId)

    MineSubPage(title = category.name, onBack = onBack) {
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
                SummaryCard(
                    categoryId = categoryId,
                    billCount = rows.size,
                    netCents = netCents,
                    rangeText = "${start.monthValue}/${start.dayOfMonth} – " +
                        "${end.monthValue}/${end.dayOfMonth}",
                )
            }
            if (rows.isEmpty()) {
                item { StateView(state = ContentState.Empty("该分类本期暂无账单")) }
            } else {
                items(rows, key = { it.id }) { transaction ->
                    BillRow(transaction, category.name)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    categoryId: String,
    billCount: Int,
    netCents: Long,
    rangeText: String,
) {
    val category = CategoryCatalog.of(categoryId)
    val tint = Color(category.color)
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.sheet,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconImage(
                name = category.name,
                color = tint,
                size = 38.dp,
                categoryId = category.id,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "合计支出",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    formatMoney(netCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$billCount 笔",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    rangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun BillRow(transaction: Transaction, categoryName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.lg,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    transaction.note.ifBlank { categoryName },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${transaction.date.monthValue}月${transaction.date.dayOfMonth}日" +
                        if (transaction.account.isNotBlank()) " · ${transaction.account}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                formatSignedMoney(TransactionType.EXPENSE, transaction.amountCents),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
