package com.iji.app.ui.mine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iji.app.accessibility.AutoBookkeepingPrefs
import com.iji.app.ai.AiBookkeepingService
import com.iji.app.ai.AiConfig
import com.iji.app.ai.AiParseHistoryStore
import com.iji.app.ai.ImageBillRecognizer
import com.iji.app.ai.TempImages
import com.iji.app.ai.applyExifRotation
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.LedgerSelection
import com.iji.app.ui.ai.ActionButton
import com.iji.app.ui.ai.ParseResultCard
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiFormDivider
import com.iji.app.ui.components.IjiFormSectionTitle
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.StateSize
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.ExpenseAmount
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.SharedReceiptHolder
import com.iji.app.ui.util.sharedMainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 智能识别（原「自动记账」页的手动记账区，2026-11-10 迁出）。
 *
 * 这是**手动**识别：一句话 / 拍照 / 相册 / 语音 → 解析 → 可编辑确认卡（快存 / 详细编辑）。
 * 与「自动记账」页分开后，后者只负责后台触发器、授权与落账偏好，不再被输入区推到屏幕外。
 *
 * 快存与识屏/通知链路共用 `AutoBookkeepingPrefs`：落账账本与备注模板在同一处配置、同一处生效。
 */
@Composable
fun SmartCaptureScreen(
    onBack: () -> Unit,
    /** 详细编辑：把结果带回「记一笔」面板预填（不离开本页） */
    onConfirmDetailEdit: (AiBookkeepingService.BillResult) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    val mainVm = sharedMainViewModel()

    var inputText by remember { mutableStateOf("") }
    var isParsing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AiBookkeepingService.BillResult?>(null) }
    var errorMsg by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var showImageDialog by remember { mutableStateOf(false) }

    /** 解析协程句柄：云端请求最长 30+60s（OkHttp 超时），期间可点「停止」取消 */
    var parseJob by remember { mutableStateOf<Job?>(null) }
    var history by remember { mutableStateOf(AiParseHistoryStore.all(context)) }

    val accounts by mainVm.accounts.collectAsStateWithLifecycle()
    val accountNames = remember(accounts) { accounts.map { it.name } }
    val compactWidth = LocalConfiguration.current.screenWidthDp < 380

    /** 清掉本轮解析现场：结果、输入与图片一并归零（结果已进历史，误触可从「最近解析」找回） */
    fun resetAll() {
        result = null
        inputText = ""
        selectedImage = null
        errorMsg = ""
    }

    fun runOcr(bmp: Bitmap) {
        selectedImage = bmp
        parseJob?.cancel()
        isParsing = true
        errorMsg = ""
        scope.launch {
            val outcome = ImageBillRecognizer.recognizeSingle(context, bmp)
            isParsing = false
            val bill = outcome.bill
            if (bill != null) {
                result = bill
                AiParseHistoryStore.record(context, bill)
                history = AiParseHistoryStore.all(context)
            } else {
                errorMsg = outcome.hint ?: "未从小票认出账单，可手动输入后解析"
            }
        }.also { parseJob = it }
    }

    /** 文本解析的唯一入口：输入框按钮与语音回调都走这里 */
    fun parseText(text: String) {
        if (text.isBlank()) return
        parseJob?.cancel()
        isParsing = true
        errorMsg = ""
        scope.launch {
            val parsed = AiBookkeepingService.parseBill(text, context)
            isParsing = false
            if (parsed != null) {
                result = parsed
                AiParseHistoryStore.record(context, parsed)
                history = AiParseHistoryStore.all(context)
            } else {
                val useAi = AiConfig.parseMode(context) == AiConfig.MODE_AI
                errorMsg = if (useAi) {
                    "未能识别，可重试、换种说法，或检查 API Key"
                } else {
                    "未能识别，请换种说法试试，或在「AI 记账」页开启「文本识别用 AI」"
                }
            }
        }.also { parseJob = it }
    }

    /**
     * 快存：结果卡编辑后的结果直接入库（不走记一笔面板）。
     * 落账账本与备注模板跟随「自动记账 → 识别与落账」的配置，避免同页两套口径。
     */
    fun quickSave(bill: AiBookkeepingService.BillResult) {
        if (bill.amountCents <= 0L) {
            snackbar.show("金额要大于 0 才能保存")
            return
        }
        if (bill.type == "transfer") {
            snackbar.show("转账请在「详细编辑」里选择转入账户")
            return
        }
        val categoryId = (CategoryCatalog.expenses + CategoryCatalog.incomes)
            .firstOrNull { it.name == bill.categoryHint.trim() }?.id
        if (categoryId == null) {
            snackbar.show("分类「${bill.categoryHint}」不在分类表里，请点分类行重新选择")
            return
        }
        val ledger = AutoBookkeepingPrefs.effectiveLedger(
            setting = AutoBookkeepingPrefs.ledger(context),
            current = LedgerSelection.current.value,
        )
        mainVm.addTransaction(
            type = if (bill.type == "income") TransactionType.INCOME else TransactionType.EXPENSE,
            categoryId = categoryId,
            amountCents = bill.amountCents,
            note = AutoBookkeepingPrefs.buildNote(
                AutoBookkeepingPrefs.noteTemplate(context),
                bill.note,
                bill.source,
            ),
            account = bill.accountHint,
            tags = bill.tags,
            ledgerId = ledger,
        )
        resetAll()
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
        if (bitmap != null) runOcr(bitmap) else errorMsg = "图片读取失败，请换一张试试"
    }

    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    /** 相机写进 cache 的原图：decode 进内存后立刻删，用户的照片不在缓存里攒着 */
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraUri ?: return@rememberLauncherForActivityResult
        val shot = cameraFile
        if (!ok) {
            // 取消拍照：FileProvider 已经建好空文件，别留垃圾
            TempImages.delete(shot)
            return@rememberLauncherForActivityResult
        }
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = BitmapFactory.decodeStream(stream) ?: return@runCatching null
                applyExifRotation(context, uri, raw)
            }
        }.getOrNull()
        TempImages.delete(shot)
        if (bitmap != null) runOcr(bitmap) else errorMsg = "拍照失败，请重试或改用相册"
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = res.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = spoken
                parseText(spoken)
            }
        }
    }

    fun startVoice() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "说出这笔账，如：今天午饭花了35块")
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            errorMsg = "设备不支持语音识别"
            return
        }
        speechLauncher.launch(intent)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoice() else errorMsg = "需要录音权限"
    }

    // 系统分享进来的小票：自动走 OCR，但结果仍停在确认卡，不会静默入账
    val sharedPath by SharedReceiptHolder.path.collectAsStateWithLifecycle()
    LaunchedEffect(sharedPath) {
        val path = sharedPath ?: return@LaunchedEffect
        SharedReceiptHolder.consume()
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                // 位图已在内存里，cache 里那份复制品用不上了
                .also { TempImages.delete(File(path)) }
        }
        if (bitmap != null) {
            runOcr(bitmap)
        } else {
            snackbar.show("分享的图片读取失败，请用「相册」再选一次")
        }
    }

    // 兜底清理：上次点了拍照又取消、或进程被杀留下的 cache/camera 图片
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            TempImages.sweep(File(context.cacheDir, "camera"))
        }
    }

    MineSubPage(title = "智能识别", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (selectedImage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    Image(
                        bitmap = selectedImage!!.asImageBitmap(),
                        contentDescription = "小票预览，点击查看原图",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(Radius.xxl))
                            .clickable { showImageDialog = true },
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        "移除",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { selectedImage = null }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (isParsing) {
                StateView(state = ContentState.Loading("识别中"), size = StateSize.Inline)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    if (errorMsg.isNotBlank()) errorMsg = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                label = { Text("如：今天午饭花了35块") },
                shape = RoundedCornerShape(Radius.xxl),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("拍照", Icons.Default.PhotoCamera, compactWidth, !isParsing) {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    cameraUri = uri
                    cameraFile = file
                    takePicture.launch(uri)
                }
                ActionButton("相册", Icons.Default.PhotoLibrary, compactWidth, !isParsing) {
                    imagePicker.launch("image/*")
                }
                ActionButton("语音", Icons.Default.Mic, compactWidth, !isParsing) {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) startVoice() else audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
                if (isParsing) {
                    ActionButton("停止", icon = null, compact = compactWidth, enabled = true) {
                        parseJob?.cancel()
                        isParsing = false
                    }
                } else {
                    ActionButton(
                        text = "解析",
                        icon = null,
                        compact = compactWidth,
                        enabled = true,
                        primary = true,
                    ) { parseText(inputText) }
                }
            }

            if (errorMsg.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IjiTextAction(
                        text = "重试",
                        compact = true,
                        onClick = {
                            val image = selectedImage
                            if (image != null) runOcr(image) else parseText(inputText)
                        },
                    )
                }
            }

            result?.let { bill ->
                ParseResultCard(
                    bill = bill,
                    accounts = accountNames,
                    onSave = ::quickSave,
                    onDetailEdit = { parsed ->
                        onConfirmDetailEdit(parsed)
                        resetAll()
                    },
                    onReset = ::resetAll,
                )
            }

            IjiFormSectionTitle("最近解析")
            IjiFormCard {
                if (history.isEmpty()) {
                    Text(
                        "还没有识别记录。上面输入一句话或选一张小票，识别结果会留在这里方便找回。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    history.forEachIndexed { index, record ->
                        if (index > 0) IjiFormDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    result = record.bill
                                    errorMsg = ""
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    record.bill.note.ifBlank { "（无备注）" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                val time = SimpleDateFormat("MM-dd HH:mm", LocalConfiguration.current.locales[0])
                                    .format(Date(record.time))
                                Text(
                                    listOf(time, record.bill.source, record.bill.categoryHint)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                )
                            }
                            Text(
                                formatMoney(record.bill.amountCents),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (record.bill.type == "income") IncomeGreen else ExpenseAmount,
                            )
                        }
                    }
                    IjiFormDivider()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "点击可重新载入，仅保留最近 ${AiParseHistoryStore.MAX} 条",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    IjiTextAction(
                        text = "清空",
                        compact = true,
                        onClick = {
                            AiParseHistoryStore.clear(context)
                            history = emptyList()
                        },
                    )
                }
            }
        }
    }

    // 原图查看：统一走 IjiDialog（不再用平台 Dialog）
    if (showImageDialog && selectedImage != null) {
        IjiDialog(
            show = showImageDialog,
            onDismissRequest = { showImageDialog = false },
            showDismiss = false,
        ) {
            Image(
                bitmap = selectedImage!!.asImageBitmap(),
                contentDescription = "小票原图，点击关闭",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showImageDialog = false },
            )
        }
    }
}
