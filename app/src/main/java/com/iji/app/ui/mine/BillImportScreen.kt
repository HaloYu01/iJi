package com.iji.app.ui.mine

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iji.app.MainViewModel
import com.iji.app.ai.AiBookkeepingService
import com.iji.app.ai.BillDraft
import com.iji.app.ai.ImageBillRecognizer
import com.iji.app.ai.LocalRuleStore
import com.iji.app.ai.applyExifRotation
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.EXCEL_NOT_SUPPORTED_HINT
import com.iji.app.ui.components.IMPORT_MIME_TYPES
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiSegmentedRow
import com.iji.app.ui.components.IjiSettingValue
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.IjiSettingsRow
import com.iji.app.ui.components.ImportFileResult
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.readImportFile
import com.iji.app.ui.components.safeDisplayName
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.util.saveToDownloads
import com.iji.app.ui.util.sharedMainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 账单导入/导出页：三块分组、五个入口，文案尽量短。
 *
 * 解析结果一律交给 [BillImportPreviewScreen] 核对后才写库：文件走「配置面板 → 解析 → 预览页」，
 * 截图走「相册多选 → OCR → 预览页」，两条链路共用 [BillImportDraftHolder] 传结果。
 * Excel 二进制仍按既有口径提示另存 CSV（[EXCEL_NOT_SUPPORTED_HINT]），不假装支持；
 * 导出的 CSV 带 UTF-8 BOM，Excel 可直接打开。
 */

/** 配置面板里的展开/收起点击不要水波纹：这是行内展开，不是按钮 */
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun BillImportScreen(
    onBack: () -> Unit,
    onNavigatePreview: () -> Unit = {},
    onNavigateHistory: () -> Unit = {},
    viewModel: MainViewModel = sharedMainViewModel(),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    val currentLedger by viewModel.currentLedger.collectAsStateWithLifecycle()
    val batches by viewModel.importHistory.collectAsStateWithLifecycle()

    var fileName by rememberSaveable { mutableStateOf("") }
    // 文件正文可能几百 KB，只在内存里过一遍，不参与状态保存
    var fileText by remember { mutableStateOf("") }
    var showConfig by remember { mutableStateOf(false) }
    var sourceChoice by remember { mutableStateOf(ImportSource.AUTO) }
    var ledgerName by rememberSaveable { mutableStateOf(currentLedger) }
    var ledgerExpanded by remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var showTemplateConfirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val name = withContext(Dispatchers.IO) { safeDisplayName(context, uri) }
            val read = withContext(Dispatchers.IO) { readImportFile(context, uri) }
            busy = false
            when (read) {
                is ImportFileResult.Text -> {
                    fileName = name
                    fileText = read.content
                    sourceChoice = ImportSource.AUTO
                    ledgerName = currentLedger
                    ledgerExpanded = false
                    showConfig = true
                }

                ImportFileResult.ExcelBinary ->
                    snackbar.show(EXCEL_NOT_SUPPORTED_HINT, duration = SnackbarDuration.Long)

                is ImportFileResult.Failure ->
                    snackbar.show("读取失败：${read.reason}", duration = SnackbarDuration.Long)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val (drafts, failureHint) = withContext(Dispatchers.IO) {
                val found = mutableListOf<BillDraft>()
                var hint: String? = null
                uris.forEach { uri ->
                    val bmp = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()?.let { applyExifRotation(context, uri, it) }
                    if (bmp != null) {
                        val outcome = ImageBillRecognizer.recognizeDrafts(context, bmp)
                        found += outcome.drafts
                        if (hint == null) hint = outcome.hint
                    }
                }
                found to hint
            }
            busy = false
            if (drafts.isEmpty()) {
                snackbar.show(failureHint ?: "没有识别到账单，请换一张清晰的截图")
                return@launch
            }
            val account = drafts.firstNotNullOfOrNull { draft ->
                draft.account.takeIf { it.isNotBlank() }
            }.orEmpty()
            BillImportDraftHolder.post(
                ImportDraft(
                    fileName = "截图 ${uris.size} 张",
                    source = "截屏批量导入",
                    ledgerId = currentLedger,
                    ocrDrafts = drafts,
                    ocrAccount = account,
                ),
            )
            onNavigatePreview()
        }
    }

    /** 按面板选定的来源解析并过一遍识别管线，结果交给预览页 */
    fun parseAndPreview() {
        val text = fileText
        if (busy || text.isBlank()) return
        scope.launch {
            busy = true
            val src = when (sourceChoice) {
                ImportSource.AUTO -> detectCsvSource(text)
                ImportSource.WECHAT -> CsvSource.WECHAT
                ImportSource.ALIPAY -> CsvSource.ALIPAY
            }
            val parsed = withContext(Dispatchers.IO) {
                val raw = when (src) {
                    CsvSource.WECHAT -> viewModel.backupRepository.parseWeChatCsv(text)
                    CsvSource.ALIPAY -> viewModel.backupRepository.parseAlipayCsv(text)
                    CsvSource.COMMON -> viewModel.backupRepository.parseCsv(text)
                }
                raw.copy(transactions = recognizeImported(context, raw.transactions))
            }
            busy = false
            if (parsed.transactions.isEmpty()) {
                snackbar.show("没有解析出账单，请检查文件或切换来源", duration = SnackbarDuration.Long)
                return@launch
            }
            BillImportDraftHolder.post(
                ImportDraft(
                    fileName = fileName,
                    source = src.label,
                    ledgerId = ledgerName,
                    transactions = parsed.transactions,
                    failedCount = parsed.failedCount,
                ),
            )
            showConfig = false
            fileText = ""
            onNavigatePreview()
        }
    }

    /** 当前账本全部流水 → CSV，直接落到下载目录 */
    fun exportAll() {
        if (busy) return
        scope.launch {
            busy = true
            val csv = withContext(Dispatchers.IO) {
                val rows = viewModel.transactionsForExport()
                viewModel.backupRepository.toCsv(
                    rows,
                    com.iji.app.data.model.CurrencyDisplay.current.code,
                )
            }
            val name = "iji_账单_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv"
            val ok = withContext(Dispatchers.IO) {
                saveToDownloads(context, name, "text/csv", csv) != null
            }
            busy = false
            if (!ok) snackbar.show("保存失败：请检查存储空间", duration = SnackbarDuration.Long)
        }
    }

    MineSubPage(title = "账单导入/导出", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ImportSection("数据导入") {
                IjiSettingsRow(
                    title = "导入账单文件",
                    subtitle = "支持 Excel / CSV 格式",
                    onClick = { if (!busy) filePicker.launch(IMPORT_MIME_TYPES) },
                    trailing = { IjiSettingValue() },
                )
                IjiSettingsDivider()
                IjiSettingsRow(
                    title = "截图识别导入",
                    subtitle = "从相册选择多条账单截图智能识别",
                    onClick = {
                        if (!busy) {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    },
                    trailing = { IjiSettingValue() },
                )
            }

            ImportSection("数据导出") {
                IjiSettingsRow(
                    title = "导出全部账单",
                    subtitle = "导出为 CSV / Excel 文件",
                    onClick = { if (!busy) showExportConfirm = true },
                    trailing = { IjiSettingValue() },
                )
                IjiSettingsDivider()
                IjiSettingsRow(
                    title = "下载导入模板",
                    subtitle = "获取标准格式模板",
                    onClick = { showTemplateConfirm = true },
                    trailing = { IjiSettingValue() },
                )
            }

            ImportSection("导入记录与恢复") {
                IjiSettingsRow(
                    title = "导入记录",
                    subtitle = null,
                    onClick = onNavigateHistory,
                    trailing = {
                        IjiSettingValue(if (batches.isEmpty()) "暂无" else "${batches.size} 批")
                    },
                )
            }

            if (busy) {
                Text("处理中…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }

    // ── 导入配置面板：来源 + 目标账本，确认后才解析 ──
    IjiDialog(
        show = showConfig,
        onDismissRequest = { showConfig = false },
        title = "导入配置",
        summary = fileName,
        confirmText = "解析",
        confirmEnabled = !busy,
        onConfirm = { parseAndPreview() },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("来源", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            IjiSegmentedRow(
                options = ImportSource.entries,
                selected = sourceChoice,
                onSelect = { sourceChoice = it },
                modifier = Modifier.fillMaxWidth(),
                label = { it.label },
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable { ledgerExpanded = !ledgerExpanded }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("目标账本", style = MaterialTheme.typography.bodyLarge)
                    Text(ledgerName, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                IjiSettingValue(if (ledgerExpanded) "收起" else "选择")
            }
            if (ledgerExpanded) {
                viewModel.ledgers.ifEmpty { listOf(ledgerName) }.distinct().forEach { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .noRippleClickable {
                                ledgerName = name
                                ledgerExpanded = false
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (name == ledgerName) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }

    // ── 导出确认：与模板下载一样，先问一句再写文件 ──
    IjiDialog(
        show = showExportConfirm,
        onDismissRequest = { showExportConfirm = false },
        title = "确认导出账单吗？",
        summary = "当前账本全部账单会导出为 CSV 保存到「下载」目录，Excel 可直接打开。",
        confirmText = "导出",
        onConfirm = {
            showExportConfirm = false
            exportAll()
        },
    )

    // ── 模板下载确认 ──
    IjiDialog(
        show = showTemplateConfirm,
        onDismissRequest = { showTemplateConfirm = false },
        title = "确认下载模板吗？",
        summary = "标准 CSV 模板会保存到「下载」目录，可直接用 Excel 打开填写。",
        confirmText = "下载",
        onConfirm = {
            showTemplateConfirm = false
            val ok = saveCsvTemplate(context) != null
            if (!ok) snackbar.show("模板保存失败", duration = SnackbarDuration.Long)
        },
    )
}

/**
 * 结构化账单再过一遍 AI / 本地规则识别管线。
 *
 * 金额、日期、收支方向一律以文件为准（不让模型从文本里重猜）；只有文件里是
 * 「其他 / 未分类 / 空」的分类才用识别结果补，避免把本来正确的分类洗掉。
 * 本地规则先跑，命中就不为每笔发 AI 请求（批量导入可能上百笔）；本地没把握时才交给
 * [AiBookkeepingService.parseBill]，没配 AI 时它仍回落到同一套本地规则。
 */
private suspend fun recognizeImported(
    context: Context,
    list: List<Transaction>,
): List<Transaction> = withContext(Dispatchers.IO) {
    val rules = LocalRuleStore.rules(context)
    list.map { tx ->
        val originalName = runCatching { CategoryCatalog.of(tx.categoryId).name }.getOrDefault("")
        val needsCategory = originalName.isBlank() ||
            originalName == "未分类" || originalName == "其他"
        val text = listOfNotNull(
            tx.note.takeIf { it.isNotBlank() },
            tx.merchant.takeIf { it.isNotBlank() },
            originalName.takeIf { it.isNotBlank() && !needsCategory },
        ).distinct().joinToString(" ")
        if (text.isBlank()) return@map tx
        val local = AiBookkeepingService.localRuleParse(text, rules)
        val result = if (local.categoryHint.isNotBlank() && local.categoryHint != "其他") {
            local
        } else {
            runCatching { AiBookkeepingService.parseBill(text, context) }.getOrNull() ?: local
        }
        tx.copy(
            categoryId = if (needsCategory) {
                categoryIdByName(result.categoryHint, tx.type)
                    .takeIf { it.isNotBlank() } ?: tx.categoryId
            } else {
                tx.categoryId
            },
            account = if (tx.account.isBlank() && result.accountHint.isNotBlank()) {
                result.accountHint
            } else {
                tx.account
            },
        )
    }
}

/** 识别给的是分类名，落库要 id；「其他」是本地规则没命中的兜底，不覆盖文件原分类 */
private fun categoryIdByName(name: String, type: TransactionType): String {
    if (name.isBlank() || name == "其他") return ""
    val catalog = if (type == TransactionType.INCOME) {
        CategoryCatalog.incomes
    } else {
        CategoryCatalog.expenses
    }
    return catalog.firstOrNull { it.name == name }?.id.orEmpty()
}

/** 配置面板的来源选项；「自动」按表头判断微信 / 支付宝 / 通用 CSV */
private enum class ImportSource(val label: String) {
    AUTO("自动"),
    WECHAT("微信"),
    ALIPAY("支付宝"),
}

private enum class CsvSource(val label: String) {
    COMMON("通用 CSV"),
    WECHAT("微信账单"),
    ALIPAY("支付宝账单"),
}

/** 支付宝导出带「交易号」列，微信带「交易时间」表头，其余按通用 CSV */
private fun detectCsvSource(text: String): CsvSource {
    val head = text.take(4000)
    return when {
        head.contains("交易号") -> CsvSource.ALIPAY
        head.contains("交易时间") -> CsvSource.WECHAT
        else -> CsvSource.COMMON
    }
}

/** 标准模板：表头与 `BackupRepository.parseCsv` 的前 7 列一致，示例行导入前删掉即可 */
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
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建文件")
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(csv.toByteArray(Charsets.UTF_8))
        } ?: error("无法写入文件")
        uri
    }.getOrNull()
}
