package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.Transaction
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.TransactionRow
import com.iji.app.ui.components.WheelOptionDialog
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleTertiaryBackground
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import com.iji.app.ui.util.sharedMainViewModel

/** 账户作用域的「全部」哨兵值 */
const val RECONCILE_ALL_ACCOUNTS = "全部账户"

/**
 * 对账页（账户详情 → 对账）。
 *
 * `transactions.reconciled` 字段、[MainViewModel.setReconciled]、
 * 「记一笔 → 更多 → 已对账」的录入入口此前都已就绪，缺的是**集中核对与批量标记的出口**：
 * 用户只能逐笔进编辑面板才能打勾，实际没人用。本页补的就是这一层。
 *
 * 语义：对账只是「这笔和银行 / 支付账单核对过」的勾，**不影响余额**（钱早就进出过了），
 * 因此走 [MainViewModel.setReconciled] 而不是会回滚余额的 `updateTransaction`。
 *
 * 交互：
 * - 顶部可切换账户作用域（默认「全部账户」，从某个账户进来时预选该账户）；
 *   组合付款按**参与账户**归属，与账户详情页口径一致；
 * - 每笔右侧「对账」按钮逐笔标记；「全部对账」批量标记；
 * - 打开「显示已对账」可看到已打勾的账单并**取消对账**（标记错了要能撤回）。
 */
@Composable
fun ReconcileScreen(
    accountName: String = RECONCILE_ALL_ACCOUNTS,
    onBack: () -> Unit,
    app: MainViewModel = sharedMainViewModel(),
) {
    val snackbar = LocalSnackbarController.current
    val accounts by app.accounts.collectAsStateWithLifecycle()
    val transactions by app.transactions.collectAsStateWithLifecycle()

    var scope by remember { mutableStateOf(accountName) }
    var showScopePicker by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var showConfirmAll by remember { mutableStateOf(false) }

    val scopeOptions = remember(accounts) {
        listOf(RECONCILE_ALL_ACCOUNTS) + accounts.map { it.name }
    }
    /** 该账单是否落在当前作用域：组合付款归入**每个**参与账户 */
    fun inScope(tx: Transaction): Boolean =
        scope == RECONCILE_ALL_ACCOUNTS ||
            tx.account == scope ||
            tx.splits.any { it.account == scope }

    val pending = remember(transactions, scope) {
        transactions.filter { !it.reconciled && inScope(it) }.sortedByDescending { it.id }
    }
    val done = remember(transactions, scope, showDone) {
        if (!showDone) emptyList()
        else transactions.filter { it.reconciled && inScope(it) }.sortedByDescending { it.id }
    }
    val doneTotal = remember(transactions, scope) {
        transactions.count { it.reconciled && inScope(it) }
    }

    MineSubPage(title = "对账", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                top = ijiTopBarContentPadding(),
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 概览 + 作用域 + 批量操作
            item(key = "header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = Radius.xxl,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("待对账", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text(
                                    "${pending.size} 笔",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                "已对账 $doneTotal 笔",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        // 账户作用域
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.lg))
                                .background(AppleTertiaryBackground)
                                .clickable { showScopePicker = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("账户", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text(
                                    scope,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            androidx.compose.material3.Icon(
                                MiuixIcons.ChevronForward,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                label = if (showDone) "隐藏已对账" else "显示已对账",
                                active = showDone,
                                onClick = { showDone = !showDone },
                            )
                            Spacer(Modifier.weight(1f))
                            if (pending.isNotEmpty()) {
                                FilterChip(
                                    label = "全部对账",
                                    active = true,
                                    onClick = { showConfirmAll = true },
                                )
                            }
                        }
                    }
                }
            }

            if (pending.isEmpty() && done.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (showDone) "该范围内没有账单" else "全部账单都已对账",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            items(pending, key = { "p-${it.id}" }) { tx ->
                ReconcileRow(
                    tx = tx,
                    reconciled = false,
                    showAccount = scope == RECONCILE_ALL_ACCOUNTS,
                    onToggle = {
                        app.setReconciled(tx, true)
                        snackbar.show("已标记为已对账")
                    },
                )
            }

            if (done.isNotEmpty()) {
                item(key = "done-title") {
                    Text(
                        "已对账（${done.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(done, key = { "d-${it.id}" }) { tx ->
                    ReconcileRow(
                        tx = tx,
                        reconciled = true,
                        showAccount = scope == RECONCILE_ALL_ACCOUNTS,
                        onToggle = {
                            app.setReconciled(tx, false)
                            snackbar.show("已取消对账标记")
                        },
                    )
                }
            }
        }
    }

    WheelOptionDialog(
        show = showScopePicker,
        options = scopeOptions,
        selected = scope,
        onDismissRequest = { showScopePicker = false },
        // WheelOptionDialog 只回传选中值、不会自己关闭，必须在这里把 show 置回 false
        onConfirm = { scope = it; showScopePicker = false },
        title = "选择账户",
    )

    IjiDialog(
        show = showConfirmAll,
        onDismissRequest = { showConfirmAll = false },
        title = "全部对账",
        confirmText = "标记",
        onConfirm = {
            val list = pending
            app.markAllReconciled(list)
            showConfirmAll = false
            snackbar.show("已把 ${list.size} 笔标记为已对账")
        },
    ) {
        Text(
            "把当前范围内 ${pending.size} 笔账单标记为已对账？\n" +
                "标记后可在「显示已对账」里逐笔取消。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

/** 单笔对账行：左账单、右操作按钮 */
@Composable
private fun ReconcileRow(
    tx: Transaction,
    reconciled: Boolean,
    showAccount: Boolean,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = Radius.lg) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TransactionRow(
                    tx = tx,
                    subtitle = runCatching { CategoryCatalog.of(tx.categoryId).name }
                        .getOrDefault(tx.categoryId),
                    showAccountUnderAmount = showAccount,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilterChip(
                label = if (reconciled) "取消" else "对账",
                active = !reconciled,
                onClick = onToggle,
            )
        }
    }
}

/** 小胶囊按钮：选中态用主色浅底 + 勾号 + 描边，与账本页的筛选胶囊同一套视觉（状态不只靠颜色） */
@Composable
private fun FilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xl)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (active) MiuixPrimary.copy(alpha = 0.14f) else AppleTertiaryBackground)
            .then(if (active) Modifier.border(1.dp, MiuixPrimary.copy(alpha = 0.4f), shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { selected = active }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (active) "✓ $label" else label,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) MiuixPrimary else TextSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
}
