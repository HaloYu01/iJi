package com.iji.app.ui.mine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.ImportRecord
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.EXCEL_NOT_SUPPORTED_HINT
import com.iji.app.ui.components.IMPORT_MIME_TYPES
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiDialogActions
import com.iji.app.ui.components.ImportFileResult
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.WheelOptionDialog
import com.iji.app.ui.components.ijiButtonFocusRing
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.readImportFile
import com.iji.app.ui.components.safeDisplayName
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 账单导入页：一个表单走完「选文件 → 自动识别来源 → 预览 → 导入到哪本账 → 结果」。
 *
 * - 选完文件立即在 IO 线程解析并**给出预览**（笔数、日期范围、收支合计、前几笔），
 *   确认后才写库，避免选错文件直接落账；
 * - 来源按内容自动识别（交易号 → 支付宝，交易时间 → 微信，否则通用 CSV），
 *   识别错了在「导入来源」里手动指定，改完立刻按新来源重解析；
 * - 成功/失败数来自真实写入结果，而不是解析条数的乐观计数；
 * - 撞上同文件同条数的历史导入只弹确认，不静默去重（同一天两笔一样的消费是真实存在的）。
 */
@Composable
fun BillImportScreen(
    onBack: () -> Unit,
    onNavigateScreenshotImport: () -> Unit = {},
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    val currentLedger by viewModel.currentLedger.collectAsStateWithLifecycle()
    val importHistory by viewModel.importHistory.collectAsStateWithLifecycle()

    var ledgerName by rememberSaveable { mutableStateOf(currentLedger) }
    var fileName by rememberSaveable { mutableStateOf("") }
    // 文件正文只在内存里过一遍，不参与状态保存（可能几百 KB）
    var fileText by remember { mutableStateOf("") }
    // 生效的账单格式；null = 还没选文件（行上显示「自动识别」）
    var source by rememberSaveable { mutableStateOf<CsvSource?>(null) }
    var preview by remember { mutableStateOf<CsvPreview?>(null) }
    var parseError by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ImportOutcome?>(null) }

    var showRecords by remember { mutableStateOf(false) }
    var showClearRecordsConfirm by remember { mutableStateOf(false) }
    // 命中「这份文件导过一遍」时攒在这里，弹确认；确认后才真正写库
    var duplicateHit by remember { mutableStateOf<ImportRecord?>(null) }
    var showLedgerPicker by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }

    /** 换文件 / 读失败时清掉上一次的解析痕迹 */
    fun clearSession() {
        fileName = ""
        fileText = ""
        source = null
        preview = null
        parseError = ""
        outcome = null
        parsing = false
    }

    /** 按指定格式解析文本并生成预览 */
    suspend fun parse(text: String, src: CsvSource) {
        parsing = true
        parseError = ""
        outcome = null
        val parsed = withContext(Dispatchers.IO) {
            when (src) {
                CsvSource.WECHAT -> viewModel.backupRepository.parseWeChatCsv(text)
                CsvSource.ALIPAY -> viewModel.backupRepository.parseAlipayCsv(text)
                CsvSource.COMMON -> viewModel.backupRepository.parseCsv(text)
            }
        }
        parsing = false
        source = src
        if (parsed.transactions.isEmpty()) {
            preview = null
            parseError = if (parsed.failedCount > 0) {
                "没有解析出可导入的账单（${parsed.failedCount} 行无法识别）。换个「导入来源」再试，或确认导出的是 CSV 而不是 Excel。"
            } else {
                "文件里没有账单数据。"
            }
        } else {
            preview = CsvPreview(src, parsed.transactions, parsed.failedCount)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) { safeDisplayName(context, uri) }
            when (val read = withContext(Dispatchers.IO) { readImportFile(context, uri) }) {
                is ImportFileResult.Text -> {
                    fileName = name
                    fileText = read.content
                    // 选完文件先按内容识别来源，识别错了在「导入来源」里手动改
                    parse(read.content, detectCsvSource(read.content))
                }
                ImportFileResult.ExcelBinary -> {
                    clearSession()
                    snackbar.show(EXCEL_NOT_SUPPORTED_HINT, duration = SnackbarDuration.Long)
                }
                is ImportFileResult.Failure -> {
                    clearSession()
                    snackbar.show("读取失败：${read.reason}", duration = SnackbarDuration.Long)
                }
            }
        }
    }

    /** 真正写库：首次导入，或重复确认里选了「仍要导入」之后进来 */
    fun performImport(p: CsvPreview) {
        importing = true
        outcome = null
        duplicateHit = null
        viewModel.importCsvTransactions(
            transactions = p.transactions,
            source = p.source.label,
            fileName = fileName,
            targetLedger = ledgerName,
            failedCount = p.failed,
        ) { ok, failed ->
            importing = false
            outcome = ImportOutcome(ok, failed)
            // 清掉预览：同一次选择不允许再点一次按钮重复导入
            preview = null
            fileText = ""
        }
    }

    /**
     * 点「导入」：先看这批账单是不是已经导过一遍。
     *
     * 命中只弹一次确认，不自动跳过——同一天两笔金额、分类、备注都相同的消费确实存在，
     * 静默去重会把真账单吞掉，判断权交给用户。
     */
    fun startImport() {
        val p = preview ?: return
        val hit = findPreviousImport(importHistory, fileName, ledgerName, p)
        if (hit == null) performImport(p) else duplicateHit = hit
    }

    MineSubPage(title = "导入账单", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 1. 表单：文件 / 来源 / 账本 ──
            // 不叫「导入账单」：与页面标题重复，读屏会连念两遍
            SmallTitle(text = "导入设置", insideMargin = PaddingValues(0.dp))
            SettingsCard {
                Column {
                    SettingRow(
                        title = "账单文件",
                        value = fileName.ifBlank { "选择文件" },
                        subtitle = "CSV / TXT，微信、支付宝导出的都可以",
                        highlightValue = fileName.isNotBlank(),
                        enabled = !importing,
                        onClick = { filePicker.launch(IMPORT_MIME_TYPES) },
                    )
                    RowDivider()
                    SettingRow(
                        title = "导入来源",
                        value = source?.label ?: "自动识别",
                        subtitle = "按文件内容自动识别，识别错了可以手动改",
                        enabled = !importing,
                        onClick = { showSourcePicker = true },
                    )
                    RowDivider()
                    SettingRow(
                        title = "导入到",
                        value = ledgerName,
                        subtitle = "导入的每一笔都会记进这本账",
                        enabled = !importing,
                        onClick = { showLedgerPicker = true },
                    )
                }
            }

            // ── 2. 预览 / 导入结果：同一块区域，解析中 → 预览 → 结果逐段演进 ──
            val pending = preview
            if (parsing || pending != null || parseError.isNotBlank() || outcome != null) {
                Spacer(Modifier.height(12.dp))
                SmallTitle(
                    // 导入完成后预览被清空，这时这一块只剩结果，标题也要跟着换
                    text = if (pending == null && parseError.isBlank() && outcome != null) "导入结果" else "预览",
                    insideMargin = PaddingValues(0.dp),
                )
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        when {
                            parsing -> Text(
                                "正在读取并解析文件…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )

                            parseError.isNotBlank() -> Text(
                                text = parseError,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp,
                                color = AppleRed,
                            )

                            pending != null -> PreviewBlock(pending)
                        }
                        outcome?.let {
                            Text(
                                // 想看是哪一批就去「导入记录」，这里不再挂第二条入口
                                text = "成功 ${it.ok} 笔" + if (it.failed > 0) " · 失败 ${it.failed} 笔" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (it.failed > 0) AppleRed else TextSecondary,
                            )
                        }
                    }
                }

                if (pending != null) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { startImport() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .ijiButtonFocusRing(
                                shape = ContinuousRoundedRectangle(ButtonDefaults.CornerRadius),
                            ),
                        enabled = !importing && !parsing,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(if (importing) "导入中…" else "导入 ${pending.transactions.size} 笔")
                    }
                }
            }

            // ── 3. 其他：另一种导入方式 + 两个工具 ──
            Spacer(Modifier.height(12.dp))
            SmallTitle(text = "其他", insideMargin = PaddingValues(0.dp))
            SettingsCard {
                Column {
                    SettingRow(
                        title = "截屏批量导入",
                        value = "",
                        subtitle = "从相册选整屏账单截图，一屏按行拆成多笔，导入前可逐笔核对",
                        enabled = !importing,
                        onClick = onNavigateScreenshotImport,
                    )
                    RowDivider()
                    SettingRow(
                        title = "下载 CSV 模板",
                        value = "",
                        subtitle = "表头与导出的账单一致，填好直接导入",
                        onClick = {
                            val ok = saveCsvTemplate(context) != null
                            snackbar.show(
                                if (ok) "模板已保存到下载目录：iji_import_template.csv" else "保存失败",
                                duration = SnackbarDuration.Long,
                            )
                        },
                    )
                    RowDivider()
                    SettingRow(
                        title = "导入记录",
                        value = if (importHistory.isEmpty()) "暂无" else "${importHistory.size} 次",
                        subtitle = "",
                        onClick = { showRecords = true },
                    )
                }
            }
        }
    }

    // ── 导入记录（真实数据：CSV / 备份恢复 / 截屏导入都会落一条）──
    IjiDialog(
        show = showRecords,
        onDismissRequest = { showRecords = false },
        title = "导入记录",
        summary = if (importHistory.isEmpty()) null else "共 ${importHistory.size} 次导入 · 最新的在最上面",
    ) {
        if (importHistory.isEmpty()) {
            Text(
                "还没有导入过账单。\n\n用 CSV / 备份恢复 / 截屏批量导入进来的每一批，都会在这里留下一条记录（来源、文件、目标账本、成功与失败条数）。",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                color = TextSecondary,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                importHistory.forEachIndexed { index, record ->
                    ImportRecordRow(record)
                    // 弹窗内没有卡片内边距，分割线不再缩进
                    if (index < importHistory.lastIndex) RowDivider(startPadding = 0.dp)
                }
            }
            Text(
                "清空记录",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showClearRecordsConfirm = true }
                    .padding(vertical = 12.dp),
                color = AppleRed,
                fontWeight = FontWeight.Medium,
            )
        }
        IjiDialogActions(onConfirm = { showRecords = false })
    }

    // ── 重复导入确认：同一文件 + 同样条数 + 同一本账，才提醒一次 ──
    IjiDialog(
        show = duplicateHit != null,
        onDismissRequest = { duplicateHit = null },
        title = "这份账单导入过",
        summary = "再导一次会把同样的账单重新记一遍。",
        confirmText = "仍要导入",
        confirmColor = AppleRed,
        onConfirm = {
            val p = preview
            duplicateHit = null
            if (p != null) performImport(p)
        },
    ) {
        val hit = duplicateHit
        Text(
            text = if (hit == null) "" else {
                "「${hit.fileName}」${hit.importedAtMillis.importTimeText()} 已导入过 ${hit.okCount} 笔" +
                    (if (hit.ledgerId.isNotBlank()) "到「${hit.ledgerId}」" else "") +
                    "，这次还是 ${preview?.transactions?.size ?: 0} 笔。"
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        )
    }

    IjiDialog(
        show = showClearRecordsConfirm,
        onDismissRequest = { showClearRecordsConfirm = false },
        title = "清空导入记录",
        summary = "只删除这些记录，已经导入的账单不受影响。",
        confirmText = "清空",
        confirmColor = AppleRed,
        onConfirm = {
            viewModel.clearImportHistory()
            showClearRecordsConfirm = false
            showRecords = false
        },
    ) {
        Text(
            "确定清空 ${importHistory.size} 条导入记录吗？",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        )
    }

    // ── 账本 / 来源选择（滚轮：居中即暂定，点确定提交）──
    WheelOptionDialog(
        show = showLedgerPicker,
        options = viewModel.ledgers.ifEmpty { listOf(ledgerName) },
        selected = ledgerName,
        title = "导入到",
        onDismissRequest = { showLedgerPicker = false },
        onConfirm = {
            ledgerName = it
            showLedgerPicker = false
        },
    )

    WheelOptionDialog(
        show = showSourcePicker,
        options = CsvSource.labels,
        selected = source?.label ?: "",
        title = "导入来源",
        onDismissRequest = { showSourcePicker = false },
        onConfirm = { picked ->
            showSourcePicker = false
            val next = CsvSource.ofLabel(picked)
            if (next != source) {
                source = next
                // 手上已经有文件时立刻按新来源重解析，预览随来源变
                if (fileText.isNotBlank()) scope.launch { parse(fileText, next) }
            }
        },
    )
}

// ══════════════════ 页面内部组件 ══════════════════

/** 文件里解析出来的可导入结果 */
private data class CsvPreview(
    val source: CsvSource,
    val transactions: List<Transaction>,
    val failed: Int,
)

/** 一次导入的真实结果 */
private data class ImportOutcome(val ok: Int, val failed: Int)

/**
 * 找出「同一份文件、同样条数、同一本账」的上一次导入记录，没有则返回 null。
 *
 * 用导入历史而不是逐笔比对账本：历史里存着文件名和条数，命中成本低，
 * 也不会把「同一天两笔一样的消费」误判成重复；代价是同名不同内容的文件会多问一次，
 * 而多问一次确认的代价远小于静默吞掉真账单。
 */
private fun findPreviousImport(
    history: List<ImportRecord>,
    fileName: String,
    ledgerName: String,
    preview: CsvPreview,
): ImportRecord? {
    val name = fileName.trim()
    if (name.isEmpty()) return null
    val total = preview.transactions.size + preview.failed
    return history.firstOrNull { record ->
        record.fileName.equals(name, ignoreCase = true) &&
            record.okCount + record.failedCount == total &&
            (record.ledgerId.isBlank() || ledgerName.isBlank() || record.ledgerId == ledgerName)
    }
}

/** 账单来源格式；「自动识别」不是一个独立格式，只是还没选定 */
private enum class CsvSource(val label: String) {
    COMMON("通用 CSV"),
    WECHAT("微信账单"),
    ALIPAY("支付宝账单"),
    ;

    companion object {
        val labels = entries.map { it.label }

        fun ofLabel(label: String): CsvSource = entries.firstOrNull { it.label == label } ?: COMMON
    }
}

/**
 * 按内容判断账单来源：支付宝导出带「交易号」列，微信带「交易时间」表头，其余按通用 CSV。
 * 判断错了有退路——在「导入来源」里手动指定后重新解析。
 */
private fun detectCsvSource(text: String): CsvSource {
    val head = text.take(4000)
    return when {
        head.contains("交易号") -> CsvSource.ALIPAY
        head.contains("交易时间") -> CsvSource.WECHAT
        else -> CsvSource.COMMON
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

/** 设置行：标题 + 可选的右侧取值 + 说明；点整行进对应选择器 */
@Composable
private fun SettingRow(
    title: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlightValue: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        if (value.isNotBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlightValue) MiuixPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 180.dp)
                    .padding(start = 8.dp),
            )
        }
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(20.dp),
        )
    }
}

@Composable
private fun RowDivider(startPadding: Dp = 20.dp) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

/** 预览：笔数与日期范围、收支合计、前几笔明细（来源看上面的「导入来源」行） */
@Composable
private fun PreviewBlock(preview: CsvPreview) {
    val txs = preview.transactions
    val expense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
    val income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }

    Text(
        text = buildString {
            append("共 ${txs.size} 笔 · ${txs.minOf { it.date }} ~ ${txs.maxOf { it.date }}")
            if (expense > 0) append(" · 支出 ${money(expense)}")
            if (income > 0) append(" · 收入 ${money(income)}")
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (preview.failed > 0) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${preview.failed} 行无法识别，导入时会跳过",
            style = MaterialTheme.typography.bodySmall,
            color = AppleRed,
        )
    }

    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        txs.take(3).forEach { tx ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${tx.date.monthValue}/${tx.date.dayOfMonth}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    text = when (tx.type) {
                        TransactionType.INCOME -> "收入"
                        TransactionType.EXPENSE -> "支出"
                        TransactionType.TRANSFER -> "转账"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.width(36.dp),
                )
                Text(
                    text = tx.note.ifBlank { "（无备注）" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = money(tx.amountCents),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (txs.size > 3) {
            Text(
                text = "仅显示前 3 笔，共 ${txs.size} 笔",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

private val IMPORT_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

/** 导入时间戳 → 「10月27日 10:09」 */
private fun Long.importTimeText(): String =
    IMPORT_TIME_FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault()))

/** 一条导入记录：来源 + 时间 + 结果条数 + 目标账本 + 文件名 */
@Composable
private fun ImportRecordRow(record: ImportRecord) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                record.source.ifBlank { "导入" },
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
            )
            Text(
                record.importedAtMillis.importTimeText(),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            buildString {
                append("成功 ${record.okCount} 条")
                if (record.failedCount > 0) append(" · 失败 ${record.failedCount} 条")
                if (record.ledgerId.isNotBlank()) append(" · 进入「${record.ledgerId}」")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (record.failedCount > 0) AppleRed else TextSecondary,
        )
        if (record.fileName.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                record.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun money(cents: Long): String = "¥" + String.format(Locale.US, "%,.2f", cents / 100.0)

/**
 * 生成「通用 CSV」导入模板并写入系统下载目录。
 *
 * 表头与 [com.iji.app.data.repository.BackupRepository.toCsv] 的导出格式完全一致
 * （日期,类型,分类,金额,账户,标签,备注），所以**先导出一份自己的账单**就是最好的模板；
 * 这里额外给一份可直接填写的样例，省去首次使用的摸索成本。
 *
 * 带 UTF-8 BOM：Excel / WPS 打开中文不乱码。
 * 分类列写中文名即可（导入时 [com.iji.app.data.repository.BackupRepository] 同时兼容分类 id 与分类名）。
 */
private fun saveCsvTemplate(context: Context): Uri? {
    val expenseName = CategoryCatalog.expenses.firstOrNull()?.name ?: "餐饮"
    val incomeName = CategoryCatalog.incomes.firstOrNull()?.name ?: "工资"
    val csv = "\uFEFF" + "日期,类型,分类,金额,账户,标签,备注\n" +
        "2026-01-01,支出,$expenseName,35.00,微信,,示例行，导入前请删除本行\n" +
        "2026-01-01,收入,$incomeName,10000.00,银行卡,月薪,\n"
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "iji_import_template.csv")
        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    return runCatching {
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: error("无法创建文件")
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(csv.toByteArray(Charsets.UTF_8))
        } ?: error("无法写入文件")
        uri
    }.getOrNull()
}
