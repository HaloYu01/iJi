package com.iji.app.ui.mine

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import com.iji.app.ui.components.ijiTopBarContentPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.ReimburseState
import com.iji.app.data.model.Transaction
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.theme.AppleOrange
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.IjiIconSize
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.icon.MiuixIcons
import com.iji.app.ui.util.sharedMainViewModel

/**
 * 应收 / 垫付管理页。
 *
 * `Transaction` 上的 `reimburseState` / `counterparty` 两个字段早已落地，但一直没有界面，
 * 用户无从把「替同事垫的钱」标成已收回 —— 这是最容易丢钱的一段。本页补齐：
 *
 * - 按**对方**归集（counterparty），一眼看出谁还欠着多少
 * - 每笔可就地核销（PENDING → SETTLED）
 * - 顶部汇总待收回 / 已结清
 */
@Composable
fun ReimburseManageScreen(
    onBack: () -> Unit,
    app: MainViewModel = sharedMainViewModel(),
) {
    val snackbar = LocalSnackbarController.current
    val transactions by app.transactions.collectAsStateWithLifecycle()

    val related = transactions.filter { it.reimburseState != ReimburseState.NONE }
    // 待确认（对方说还了 / 我已还待确认）的钱还没真正落袋，和待收回一起算，避免汇总额突然变少
    val pendingCents = related.filter { it.reimburseState.isOpen }
        .sumOf { it.amountCents }
    val settledCents = related.filter { it.reimburseState == ReimburseState.SETTLED }
        .sumOf { it.amountCents }
    // 按对方归集；未填写对方的归到「未注明」一组，避免这类记录被悄悄漏掉
    val groups = related
        .groupBy { it.counterparty.ifBlank { "未注明" } }
        .toList()
        .sortedByDescending { (_, list) -> list.filter { it.reimburseState.isOpen }.sumOf { it.amountCents } }

    MineSubPage(title = "应收 / 垫付", onBack = onBack) {
        if (related.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无垫付 / 借款记录\n记账时在「更多」里选择「待收回」即可加入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            return@MineSubPage
        }

        // 汇总（固定在顶栏下方，不随列表滚动）
        Column(
            modifier = Modifier
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding()),
        ) {
            Row {
                SummaryCell(
                    label = "待收回",
                    cents = pendingCents,
                    color = AppleRed,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                SummaryCell(
                    label = "已结清",
                    cents = settledCents,
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            groups.forEach { (name, list) ->
                val groupPending = list.filter { it.reimburseState.isOpen }
                    .sumOf { it.amountCents }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        // 用 != 0L 而非> 0L：退款冲减后合计可能为负，> 0L 会把「待收回」误显示成「已结清」
                        if (groupPending != 0L) {
                            Text(
                                "待收回 ${formatMoney(groupPending)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppleRed,
                            )
                        } else {
                            Text(
                                "已结清",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
                items(list.size) { index ->
                    ReimburseRow(
                        tx = list[index],
                        onSettle = {
                            app.setReimburseState(list[index], ReimburseState.SETTLED)
                            snackbar.show("已标记结清")
                        },
                        onReopen = {
                            app.setReimburseState(list[index], ReimburseState.PENDING)
                            snackbar.show("已恢复为待收回")
                        },
                    )
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SummaryCell(
    label: String,
    cents: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = modifier,
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(
                formatMoney(cents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

@Composable
private fun ReimburseRow(
    tx: Transaction,
    onSettle: () -> Unit,
    onReopen: () -> Unit,
) {
    val category = CategoryCatalog.of(tx.categoryId)
    val settled = tx.reimburseState == ReimburseState.SETTLED
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconImage(
                name = category.name,
                color = Color(category.color),
                size = IjiIconSize.ListRow,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tx.note.ifBlank { category.name },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    "${tx.date} · ${category.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(tx.amountCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (settled) TextSecondary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        settled -> "已结清 · 撤销"
                        tx.reimburseState == ReimburseState.PENDING_CONFIRM -> "待确认 · 确认"
                        else -> "标记结清"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        settled -> TextSecondary
                        tx.reimburseState == ReimburseState.PENDING_CONFIRM -> AppleOrange
                        else -> MiuixPrimary
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { if (settled) onReopen() else onSettle() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
}
