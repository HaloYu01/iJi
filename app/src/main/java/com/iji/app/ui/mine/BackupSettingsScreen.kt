package com.iji.app.ui.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.backup.BackupManager
import com.iji.app.backup.CloudBackupManager
import com.iji.app.data.repository.ConfigBackupRepository
import com.iji.app.ui.components.ChoiceRow
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.safeDisplayName
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.util.saveToDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据与备份页。
 *
 * 两块分组，与「我的」其他设置页同一套「小标题 + 卡片」：
 * - **导入**：从备份恢复（全量 / 仅配置 / 本机加密备份，选文件后先预览再确认）、
 *   导入账单（CSV / 微信 / 支付宝 / 整屏截图，独立子页 [BillImportScreen]）。
 *   账单导入原先挂在「自动记账」里，但它属于数据导入，和备份恢复同一类动作，
 *   藏进弹窗更不合适——现在是一级入口。
 * - **备份与导出**：导出（全量 / 仅配置 / 账单 CSV × 下载目录 / 自选位置）、
 *   网盘备份（独立子页 [CloudBackupScreen]：配置 / 上传 / 恢复 / 自动备份都在那边）。
 *
 * 刻意没回到主页的：每日自动备份与「立即备份」——它们做的是本机加密备份 + 上传网盘，
 * 没有网盘就没有意义，不该在主页出现第二个说法。
 */
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    /** 导入账单（CSV / 微信 / 支付宝 / 截图）独立子页 */
    onNavigateBillImport: () -> Unit = {},
    /** 网盘备份是独立子页（配置 / 上传 / 恢复 / 自动备份都在那边） */
    onNavigateCloudBackup: () -> Unit = {},
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarController.current

    // ── 页头状态 ──
    var backups by remember { mutableStateOf<List<File>>(emptyList()) }
    var lastBackupAt by remember { mutableStateOf(0L) }
    var totalSize by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf(false) }

    // ── 面板开关 ──
    var showImportPanel by remember { mutableStateOf(false) }
    var showExportPanel by remember { mutableStateOf(false) }

    /** 只用于状态卡与入口副标题的展示；真正的配置在网盘页里改 */
    var cloudConfigured by remember { mutableStateOf(false) }

    // ── 导入：先解析预览，再执行一次确认 ──
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }

    // ── 导出 ──
    var exportContent by remember { mutableStateOf(ExportContent.FULL) }
    var exportToDownloads by remember { mutableStateOf(true) }
    var pendingFileExport by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 危险操作
    var showDeleteBills by remember { mutableStateOf(false) }

    suspend fun refreshCloudConfigured() {
        cloudConfigured = withContext(Dispatchers.IO) { CloudBackupManager.isConfigured(context) }
    }

    suspend fun refresh() {
        backups = BackupManager.listBackups(context)
        lastBackupAt = BackupManager.latestBackupTime(context)
        totalSize = BackupManager.totalSize(context)
    }

    LaunchedEffect(Unit) {
        refresh()
        refreshCloudConfigured()
    }

    // 从网盘页返回时重读一次：那边可能刚配置好、换了目录，或者清空了配置
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    refreshCloudConfigured()
                    refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── 导入：读取文件 → 识别类型 → 预览 ──
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            busy = false
            if (text.isNullOrBlank()) {
                snackbar.show("读取文件失败")
                return@launch
            }
            val preview = runCatching { describeImport(text) }.getOrNull()
            if (preview == null) {
                snackbar.show("无法识别的文件：既不是备份，也不是账单数据")
                return@launch
            }
            // 先收起入口面板再弹确认：两个 Dialog 同时 show 时后一个不会正常渲染
            showImportPanel = false
            // SAF 的 lastPathSegment 是 documentId（如 msf:358），要查 DISPLAY_NAME 才是用户认得的文件名
            pendingImport = preview.copy(source = safeDisplayName(context, uri))
        }
    }

    // ── 导出到「自选位置」 ──
    val fileExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val job = pendingFileExport
        pendingFileExport = null
        if (job == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val (mime, text) = job
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("无法写入文件")
                }.isSuccess
            }
            busy = false
            snackbar.show(if (ok) "已导出（$mime）" else "导出失败：无法写入文件")
        }
    }

    /** 生成导出内容（明文 JSON 或 CSV） */
    suspend fun buildExportText(content: ExportContent): Pair<String, String>? =
        runCatching {
            when (content) {
                ExportContent.FULL -> "application/json" to BackupManager.buildSnapshot(context)
                ExportContent.CONFIG -> "application/json" to ConfigBackupRepository.exportJson(context)
                ExportContent.BILLS -> {
                    val rows = withContext(Dispatchers.IO) { viewModel.transactionsForExport() }
                    val csv = viewModel.backupRepository.toCsv(
                        rows,
                        com.iji.app.data.model.CurrencyDisplay.current.code,
                    )
                    "text/csv" to csv
                }
            }
        }.getOrNull()

    fun runExport() {
        scope.launch {
            busy = true
            val payload = buildExportText(exportContent)
            busy = false
            if (payload == null) {
                snackbar.show("导出失败：生成数据时出错")
                return@launch
            }
            val (mime, text) = payload
            val name = defaultExportName(exportContent)
            if (exportToDownloads) {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { saveToDownloads(context, name, mime, text) }.getOrNull() != null
                }
                snackbar.show(if (ok) "已保存到 下载/$name" else "保存失败：请检查存储空间")
                if (ok) {
                    showExportPanel = false
                    refresh()
                }
            } else {
                pendingFileExport = mime to text
                fileExportLauncher.launch(name)
                showExportPanel = false
            }
        }
    }

    /** 真正执行一次导入：快照走统一恢复，裸配置走配置还原 */
    fun executeImport(item: PendingImport) {
        scope.launch {
            busy = true
            val summary = runCatching {
                if (item.kind == ImportKind.CONFIG) {
                    val r = ConfigBackupRepository.importJson(context, item.snapshot)
                    "配置还原 ${r.total} 项（跳过 ${r.skipped} 项已存在）"
                } else {
                    BackupManager.restoreSnapshot(context, item.snapshot) { arrayText ->
                        var ok = 0
                        var bad = 0
                        viewModel.importBackupJson(arrayText) { o, b ->
                            ok = o
                            bad = b
                        }
                        ok to bad
                    }
                }
            }.getOrElse { "恢复失败：${it.message ?: "文件内容异常"}" }
            busy = false
            snackbar.show(summary)
            refresh()
        }
    }

    MineSubPage(title = "数据与备份", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                top = ijiTopBarContentPadding(),
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // ── 状态：一眼看清"上次备份是什么时候、有没有网盘" ──
            item {
                SurfaceCard {
                    Text(
                        if (lastBackupAt > 0) "上次备份 ${formatTime(lastBackupAt)}"
                        else "还没有备份过",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append("本机 ${backups.size} 份 · ")
                            append("%.1f MB".format(totalSize / 1024f / 1024f))
                            append("　|　网盘")
                            append(if (cloudConfigured) "已配置" else "未配置")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            // ── 导入：备份恢复与账单导入是两件事，各自一个入口 ──
            // 以前「导入账单」藏在「导入数据」弹窗里，用户得先点开面板才发现；
            // 从「自动记账」挪过来之后直接作为一级入口，和备份恢复并列。
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallTitle(text = "导入", insideMargin = PaddingValues(0.dp))
                    EntryCard(
                        title = "从备份恢复",
                        subtitle = "全量备份 / 仅配置 / 本机加密备份（.ijbak）",
                    ) { showImportPanel = true }
                    EntryCard(
                        title = "导入账单",
                        subtitle = "CSV / 微信 / 支付宝账单，或整屏截图批量识别",
                    ) { onNavigateBillImport() }
                }
            }

            // ── 备份与导出 ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallTitle(text = "备份与导出", insideMargin = PaddingValues(0.dp))
                    EntryCard(
                        title = "导出数据",
                        subtitle = "全量备份 / 仅配置 / 账单 CSV，可存到下载目录",
                    ) { showExportPanel = true }
                    EntryCard(
                        title = "网盘备份",
                        subtitle = if (cloudConfigured) {
                            "已配置：上传 / 从网盘恢复 / 每日自动备份"
                        } else {
                            "未配置：填地址和账号后即可上传"
                        },
                    ) { onNavigateCloudBackup() }
                }
            }

            // ── 危险操作（不属于备份，留在页尾，弱化为一行） ──
            item { Spacer(Modifier.height(8.dp)) }
            item {
                SurfaceCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteBills = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("删除账单", style = MaterialTheme.typography.bodyLarge, color = AppleRed)
                            Text(
                                "按单日 / 整月 / 自定义区间删除，删除后余额同步回滚",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Icon(
                            MiuixIcons.ChevronForward,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ══════════════════ 导入面板 ══════════════════
    IjiDialog(
        show = showImportPanel,
        onDismissRequest = { showImportPanel = false },
        title = "从备份恢复",
        summary = "备份文件会自动识别内容，先预览再执行。账单文件请走「导入账单」。",
        confirmText = "完成",
        showDismiss = false,
        onConfirm = { showImportPanel = false },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PanelAction("从文件导入备份…", "支持全量备份、仅配置备份，以及旧版流水 JSON") {
                importPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
            }

            if (backups.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SectionTitle("本机加密备份（只能在本机恢复）")
                backups.forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                formatTime(file.lastModified()),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "%.2f MB".format(file.length() / 1024f / 1024f),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        IjiTextAction(
                            text = "恢复",
                            onClick = {
                                scope.launch {
                                    val snapshot = BackupManager.readSnapshot(context, file)
                                    if (snapshot == null) {
                                        snackbar.show("解密失败：密钥不匹配或文件损坏")
                                    } else {
                                        pendingImport = describeImport(snapshot).copy(
                                            source = "本机备份 ${formatTime(file.lastModified())}",
                                        )
                                        showImportPanel = false
                                    }
                                }
                            },
                            color = MiuixPrimary,
                            compact = true,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(6.dp))
                        IjiTextAction(
                            text = "删除",
                            onClick = {
                                scope.launch {
                                    BackupManager.deleteBackup(context, file)
                                    refresh()
                                }
                            },
                            color = AppleRed,
                            compact = true,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    // ══════════════════ 导出面板 ══════════════════
    IjiDialog(
        show = showExportPanel,
        onDismissRequest = { showExportPanel = false },
        title = "导出数据",
        summary = "选内容与位置，点「导出」直接写文件，不再二次确认。",
        dismissText = "关闭",
        confirmText = "导出",
        confirmEnabled = !busy,
        onConfirm = { if (!busy) runExport() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("导出内容")
            ExportContent.entries.forEach { option ->
                ChoiceRow(
                    selected = exportContent == option,
                    title = option.label,
                    subtitle = option.hint,
                ) { exportContent = option }

            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("保存位置")
            ChoiceRow(
                selected = exportToDownloads,
                title = "下载目录（一键，不用选）",
                subtitle = "保存到系统「下载」文件夹，文件名带日期",
            ) { exportToDownloads = true }
            ChoiceRow(
                selected = !exportToDownloads,
                title = "选择保存位置…",
                subtitle = "弹出系统文件选择器，可存到网盘 App / SD 卡",
            ) { exportToDownloads = false }

            if (exportContent == ExportContent.BILLS) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "只导出当前账本的账单（CSV，14 列含币种），配置不会被导出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }

    // ── 导入确认：唯一一次确认，把"从哪来、包含什么、会怎么合并"讲清楚 ──
    val importItem = pendingImport
    IjiDialog(
        show = importItem != null,
        onDismissRequest = { pendingImport = null },
        title = "确认导入",
        summary = importItem?.detail.orEmpty(),
        confirmText = "导入",
        onConfirm = {
            val item = importItem ?: return@IjiDialog
            pendingImport = null
            executeImport(item)
        },
    ) {
        Text(
            "来源：${importItem?.source.orEmpty()}\n配置按「合并」还原（同名跳过，不覆盖本机数据）；流水一律追加，同一笔可能重复。",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }

    DeleteBillsDialog(
        show = showDeleteBills,
        onDismiss = { showDeleteBills = false },
        viewModel = viewModel,
    )
}

// ══════════════════ 数据模型与工具 ══════════════════

/** 导出内容选项 */
private enum class ExportContent(val label: String, val hint: String) {
    FULL("全量备份（配置 + 全部流水）", "换机、误删都能救，体积最大"),
    CONFIG("仅配置（分类 / 账户 / 预算等）", "不含流水，体积小，适合只搬设置"),
    BILLS("账单 CSV（当前账本）", "Excel 可直接打开，14 列含币种"),
}

/** 导入内容的形态：决定走哪套还原逻辑 */
private enum class ImportKind {
    /** 完整快照（含 config 节点，可能还有 transactions） */
    SNAPSHOT,

    /** 裸配置 JSON（导出的「仅配置」就是这种，字段平铺：categories / accounts / ledgers …） */
    CONFIG,

    /** 旧版纯流水数组 */
    LEGACY_TX,
}

/**
 * 待确认的导入项。
 *
 * [snapshot] 是**已经解析好**的明文 JSON（本机备份已解密），确认后直接交给还原逻辑，
 * 避免"确认完再读一次文件"导致两次结果不一致。
 */
private data class PendingImport(
    val source: String,
    val detail: String,
    val snapshot: String,
    val kind: ImportKind,
)

/**
 * 识别一段文本是哪种备份，并生成人类可读的预览。
 *
 * 四种实际形态（都要能导入，否则用户手里不同来源的备份就会有一半打不开）：
 * 1. 全量快照：有 `transactions` + `config`
 * 2. 带壳的仅配置（网盘上传的那种）：有 `config`、无 `transactions`
 * 3. 裸配置（导出「仅配置」得到的）：字段平铺，有 `categories`/`accounts`/`ledgers`
 * 4. 旧版流水数组：顶层就是 JSONArray
 */
private fun describeImport(text: String): PendingImport {
    val obj = runCatching { JSONObject(text) }.getOrNull()
    if (obj != null) {
        val tx = obj.optJSONArray("transactions")
        val cfg = obj.optJSONObject("config")
        val txCount = tx?.length() ?: 0
        return when {
            tx != null -> PendingImport(
                source = "",
                detail = "全量备份：$txCount 笔流水 + 配置（分类/账户/预算等）",
                snapshot = text,
                kind = ImportKind.SNAPSHOT,
            )

            cfg != null -> PendingImport(
                source = "",
                detail = "仅配置备份：不含流水，只还原分类/账户/预算等设置",
                snapshot = text,
                kind = ImportKind.SNAPSHOT,
            )

            obj.has("categories") || obj.has("accounts") || obj.has("ledgers") || obj.has("tags") ->
                PendingImport(
                    source = "",
                    detail = "仅配置备份：不含流水，只还原分类/账户/预算等设置",
                    snapshot = text,
                    kind = ImportKind.CONFIG,
                )

            // 两者都没有就不是备份：抛出去，让调用方提示"无法识别的文件"
            else -> error("无法识别的备份结构")
        }
    }
    // 旧版：纯流水数组
    val arr = runCatching { JSONArray(text) }.getOrNull()
        ?: error("不是 JSON")
    return PendingImport(
        source = "",
        detail = "旧版备份：$arr.length() 条流水（不含配置）",
        snapshot = JSONObject().apply { put("transactions", arr) }.toString(),
        kind = ImportKind.LEGACY_TX,
    )
}

private fun defaultExportName(content: ExportContent): String {
    // 精确到秒：一分钟内连点两次导出不会撞名（撞名会被系统改写成 "xxx (1).json"，反而难认）
    val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return when (content) {
        ExportContent.FULL -> "iji_backup_$date.json"
        ExportContent.CONFIG -> "iji_config_$date.json"
        ExportContent.BILLS -> "iji_账单_$date.csv"
    }
}

// ══════════════════ 小组件 ══════════════════

@Composable
private fun SurfaceCard(content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) { content() }
    }
}

/** 三入口卡：标题 + 副标题 + 箭头，点整卡进入对应面板 */
@Composable
private fun EntryCard(title: String, subtitle: String, onClick: () -> Unit) {
    SurfaceCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(
                MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        fontWeight = FontWeight.Medium,
    )
}

/** 面板内的动作行（不是单选，点了就走） */
@Composable
private fun PanelAction(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MiuixPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

/** 文件显示名已抽到 components 的 [safeDisplayName]，导入记录与备份导入共用一份实现 */
