package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iji.app.MainViewModel
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiSettingValue
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.IjiSettingsRow
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiPrimaryButtonColors
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.util.sharedMainViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import java.time.LocalDate

/**
 * 导入预览页：CSV 文件与截图 OCR 的解析结果在这里汇合，核对分类 / 金额 / 时间 / 备注后才写库。
 *
 * 结果由 [BillImportDraftHolder] 跨页传入（大列表不进路由参数）；导入成功后清掉，
 * 避免用户返回后再进预览页重复导入同一批。
 * 「本次导入」里的目标账本、资产账户可在预览页直接改；账单明细全量展示，行尾可剔除单笔。
 * 命中「同文件同笔数」的历史批次只弹确认，不静默去重：判断是否重复归用户。
 */
@Composable
fun BillImportPreviewScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = sharedMainViewModel(),
) {
    val snackbar = LocalSnackbarController.current
    val draft by BillImportDraftHolder.draft.collectAsStateWithLifecycle()
    val batches by viewModel.importHistory.collectAsStateWithLifecycle()
    val assetAccounts by viewModel.accounts.collectAsStateWithLifecycle()
    var importing by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<PickMode?>(null) }

    // 只列真实存在的资产账户；不混内置默认名，避免选到本机根本没有的账户
    val accountOptions = remember(assetAccounts) {
        listOf(KEEP_FILE_ACCOUNT) + assetAccounts.map { it.name }.distinct()
    }

    fun doImport(d: ImportDraft) {
        importing = true
        val done: (Int, Int) -> Unit = { ok, failed ->
            importing = false
            BillImportDraftHolder.clear()
            if (failed > 0) snackbar.show("导入完成，失败 $failed 笔", duration = SnackbarDuration.Long)
            onBack()
        }
        if (d.isOcr) {
            viewModel.importDrafts(
                drafts = d.ocrDrafts,
                targetLedger = d.ledgerId,
                account = d.account.ifBlank { d.ocrAccount },
                onResult = done,
            )
        } else {
            viewModel.importCsvTransactions(
                transactions = d.transactions,
                source = d.source,
                fileName = d.fileName,
                targetLedger = d.ledgerId,
                account = d.account,
                failedCount = d.failedCount,
                onResult = done,
            )
        }
    }

    MineSubPage(title = "导入预览", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val d = draft
            if (d == null) {
                ImportSection("预览") {
                    IjiSettingsRow("没有待导入的账单", subtitle = "请返回重新选择账单文件或截图")
                }
            } else {
                ImportSection("本次导入") {
                    // 右值一律贴行尾右对齐、不挂箭头图标；可改的两行用主色区分
                    IjiSettingsRow("来源", trailing = { IjiSettingValue(d.source, chevron = false) })
                    IjiSettingsDivider()
                    IjiSettingsRow(
                        "目标账本",
                        onClick = { picking = PickMode.LEDGER },
                        trailing = { ValueText(d.ledgerId, editable = true) },
                    )
                    IjiSettingsDivider()
                    IjiSettingsRow(
                        "资产账户",
                        onClick = { picking = PickMode.ACCOUNT },
                        trailing = { ValueText(d.account.ifBlank { KEEP_FILE_ACCOUNT }, editable = true) },
                    )
                    if (d.failedCount > 0) {
                        IjiSettingsDivider()
                        IjiSettingsRow("未识别", trailing = { IjiSettingValue("${d.failedCount} 行", chevron = false) })
                    }
                }

                val items = d.items()
                val range = if (items.isEmpty()) {
                    "无账单"
                } else {
                    "${items.minOf { it.date }} ~ ${items.maxOf { it.date }}"
                }
                ImportSection("账单明细") {
                    IjiSettingsRow("共 ${items.size} 笔 · $range")
                    items.forEachIndexed { index, item ->
                        IjiSettingsDivider()
                        PreviewBillRow(item) {
                            BillImportDraftHolder.update { cur ->
                                if (cur.isOcr) {
                                    cur.copy(ocrDrafts = cur.ocrDrafts.filterIndexed { i, _ -> i != index })
                                } else {
                                    cur.copy(transactions = cur.transactions.filterIndexed { i, _ -> i != index })
                                }
                            }
                        }
                    }
                    if (items.isEmpty()) {
                        IjiSettingsDivider()
                        IjiSettingsRow("账单已全部移除", subtitle = "返回重新选择文件即可重新导入")
                    }
                }

                Button(
                    onClick = {
                        if (importing) return@Button
                        val duplicate = !d.isOcr && batches.any { batch ->
                            d.fileName.isNotBlank() &&
                                batch.fileName.equals(d.fileName, ignoreCase = true) &&
                                batch.count == d.count &&
                                !batch.undone
                        }
                        if (duplicate) pendingConfirm = true else doImport(d)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !importing && items.isNotEmpty(),
                    colors = ijiPrimaryButtonColors(),
                ) {
                    Text(
                        text = if (importing) "导入中…" else "导入 ${items.size} 笔",
                        color = Color.White,
                    )
                }
            }
        }
    }

    // 账本 / 资产账户选择：都写回同一份 draft，返回本页时不会丢
    draft?.let { d ->
        val options = when (picking) {
            PickMode.LEDGER -> viewModel.ledgers.ifEmpty { listOf(d.ledgerId) }.distinct()
            PickMode.ACCOUNT -> accountOptions
            null -> emptyList()
        }
        PickDialog(
            show = picking != null,
            title = if (picking == PickMode.LEDGER) "目标账本" else "资产账户",
            hint = if (picking == PickMode.ACCOUNT && assetAccounts.isEmpty()) {
                "还没有资产账户，请先到「资产」页添加；当前只能沿用文件自带"
            } else {
                null
            },
            options = options,
            selected = if (picking == PickMode.LEDGER) d.ledgerId else d.account.ifBlank { KEEP_FILE_ACCOUNT },
            onDismiss = { picking = null },
            onPick = { name ->
                val mode = picking
                BillImportDraftHolder.update { cur ->
                    when (mode) {
                        PickMode.LEDGER -> cur.copy(ledgerId = name)
                        PickMode.ACCOUNT -> cur.copy(
                            account = if (name == KEEP_FILE_ACCOUNT) "" else name,
                        )
                        null -> cur
                    }
                }
                picking = null
            },
        )
    }

    IjiDialog(
        show = pendingConfirm,
        onDismissRequest = { pendingConfirm = false },
        title = "这份账单导入过",
        summary = "同文件、同笔数的导入已存在。仍要导入会再写一批，是否重复由你决定。",
        confirmText = "仍要导入",
        confirmColor = AppleRed,
        onConfirm = {
            pendingConfirm = false
            draft?.let { doImport(it) }
        },
    )
}

private const val KEEP_FILE_ACCOUNT = "沿用文件自带"

private enum class PickMode { LEDGER, ACCOUNT }

private data class PreviewBill(
    val date: LocalDate,
    val type: TransactionType,
    val categoryId: String,
    val amountCents: Long,
    val note: String,
    val merchant: String,
)

private fun ImportDraft.items(): List<PreviewBill> =
    if (isOcr) {
        ocrDrafts.map { PreviewBill(it.date, it.type, it.categoryId, it.amountCents, it.note, "") }
    } else {
        transactions.map {
            PreviewBill(it.date, it.type, it.categoryId, it.amountCents, it.note, it.merchant)
        }
    }

/** 卡片右值：贴行尾右对齐、不带箭头；可改的行用主色，可点的线索留在文字颜色上 */
@Composable
private fun ValueText(text: String, editable: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (editable) MiuixPrimary else TextSecondary,
        textAlign = TextAlign.End,
        modifier = Modifier.padding(start = 12.dp),
    )
}

@Composable
private fun PreviewBillRow(item: PreviewBill, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${item.date.monthValue}/${item.date.dayOfMonth}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(40.dp),
        )
        Text(
            runCatching { CategoryCatalog.of(item.categoryId).name }.getOrDefault(item.categoryId),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(58.dp),
        )
        Text(
            item.note.ifBlank { item.merchant.ifBlank { "（无备注）" } },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Text(
            (if (item.type == TransactionType.INCOME) "+" else "") + formatMoney(item.amountCents),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(Modifier.width(16.dp))
        IjiTextAction(
            text = "删除",
            compact = true,
            color = AppleRed,
            onClick = onRemove,
        )
    }
}

/** 账本 / 资产账户的纯列表选择弹窗（不传 onConfirm，点选项即关闭） */
@Composable
private fun PickDialog(
    show: Boolean,
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    hint: String? = null,
) {
    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = title,
        showDismiss = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            options.forEach { name ->
                val active = name == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(if (active) MiuixPrimary.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { onPick(name) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) MiuixPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (active) Text("✓", color = MiuixPrimary)
                }
            }
        }
    }
}
