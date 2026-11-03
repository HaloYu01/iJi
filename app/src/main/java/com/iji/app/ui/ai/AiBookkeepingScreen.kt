package com.iji.app.ui.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.iji.app.ai.AiBookkeepingService
import com.iji.app.ai.AiConfig
import com.iji.app.ai.AiPromptKind
import com.iji.app.ai.LocalOcrEngine
import com.iji.app.ai.applyExifRotation
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiFormDivider
import com.iji.app.ui.components.IjiFormSectionTitle
import com.iji.app.ui.components.IjiSwitchRow
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.StateSize
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.mine.MineSubPage
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 记账：**一页装完 AI 的全部配置与使用**（结构对齐 AutoAccounting 的「AI 助理」）。
 *
 * 页面分三段，顺序按使用频率：
 * 1. **AI 状态卡**：总开关 + 当前生效情况；「展开」内嵌的模型服务区块；
 * 2. **记账区**：文本输入 / 拍照 / 相册 / 语音 → 解析 → 结果卡；
 * 3. **AI 功能**：文本用 AI、图片用 AI、AI 识别分类（都是真实生效的开关）。
 *
 * 原来的「AI 记账设置」整页已删除——配置只有 5 个字段，单独一页只会让「改 Key → 回来记账」
 * 多两次跳转。提示词仍是独立页（内容长，且要整页编辑）。
 *
 * 原则不变：未配 Key / 关掉开关时**不发起任何网络请求**，图片不出设备。
 */
@Composable
fun AiBookkeepingScreen(
    onBack: () -> Unit,
    onNavigatePrompts: () -> Unit,
    onConfirm: (AiBookkeepingService.BillResult) -> Unit,
) {
    val ctx = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var isParsing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AiBookkeepingService.BillResult?>(null) }
    var errorMsg by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }

    /** 识别来源：本地引擎 or 云端模型，展示给用户以便判断可信度与排查 */
    var ocrSource by remember { mutableStateOf("") }

    var aiReady by remember { mutableStateOf(AiConfig.aiReady(ctx)) }
    var aiEnabled by remember { mutableStateOf(AiConfig.isEnabled(ctx)) }
    var useAi by remember { mutableStateOf(AiConfig.parseMode(ctx) == AiConfig.MODE_AI) }
    var visionEnabled by remember { mutableStateOf(AiConfig.isVisionEnabled(ctx)) }
    var categoryEnabled by remember { mutableStateOf(AiConfig.isCategoryEnabled(ctx)) }
    var accountEnabled by remember { mutableStateOf(AiConfig.isAccountEnabled(ctx)) }
    var configExpanded by rememberSaveable { mutableStateOf(false) }

    // 从提示词页 / 系统设置回来时刷新：刚配上 Key 就能立刻生效，清空 Key 立刻退回本地
    LifecycleResumeEffect(Unit) {
        aiReady = AiConfig.aiReady(ctx)
        aiEnabled = AiConfig.isEnabled(ctx)
        useAi = AiConfig.parseMode(ctx) == AiConfig.MODE_AI
        visionEnabled = AiConfig.isVisionEnabled(ctx)
        categoryEnabled = AiConfig.isCategoryEnabled(ctx)
        accountEnabled = AiConfig.isAccountEnabled(ctx)
        onPauseOrDispose { }
    }

    // 窄屏（<380dp）四个「图标 + 文字」按钮会把文字挤没，此时只保留文字
    val compactWidth = LocalConfiguration.current.screenWidthDp < 380

    /**
     * 统一 OCR 入口：**本地优先、云端兜底**。
     *
     * 先用 ML Kit 在设备内识别（离线可用、图片不出设备）；解析不出金额且用户开了
     * 「图片识别用 AI」时，才把降采样后的图片送到云端视觉模型。
     */
    fun runOcr(bmp: Bitmap) {
        selectedImage = bmp
        isParsing = true
        errorMsg = ""
        ocrSource = ""
        scope.launch {
            // 降采样 + JPEG + base64 是纯 CPU 活；原图可能 4000px，放主线程会卡一帧
            val base64 = withContext(Dispatchers.Default) { encodeForOcr(bmp) }
            val local = LocalOcrEngine.recognize(bmp)
            if (local != null && local.amountCents > 0L) {
                isParsing = false
                ocrSource = SOURCE_LOCAL
                result = local
                return@launch
            }
            // 没开「图片用 AI」就不把图片发出去：认不出就如实说
            if (!visionEnabled) {
                isParsing = false
                errorMsg = "本机未认出金额；可开启「图片识别用 AI」后重试"
                return@launch
            }
            val parsed = AiBookkeepingService.parseBillFromImage(base64, ctx)
            isParsing = false
            if (parsed != null && parsed.amountCents > 0) {
                ocrSource = SOURCE_CLOUD
                result = parsed
            } else {
                errorMsg = "未从小票认出金额，可手动输入后解析"
            }
        }
    }

    /** 文本解析的唯一入口：输入框按钮与语音回调都走这里 */
    fun parseText(text: String) {
        if (text.isBlank()) return
        isParsing = true
        errorMsg = ""
        ocrSource = ""
        scope.launch {
            val parsed = AiBookkeepingService.parseBill(text, ctx)
            isParsing = false
            if (parsed != null && parsed.amountCents > 0) {
                result = parsed
            } else {
                errorMsg = if (useAi) {
                    "AI 未返回金额，可重试或改用本地规则"
                } else {
                    "未能识别金额，请换种说法试试"
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let {
            val bitmap = ctx.contentResolver.openInputStream(it)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            bitmap?.let(::runOcr)
        }
    }

    // 拍照：临时文件 URI 经 FileProvider 授权给系统相机写入
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraUri ?: return@rememberLauncherForActivityResult
        if (!ok) return@rememberLauncherForActivityResult
        val bitmap = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                // 相机写入的 JPEG 带 EXIF 方向标记，不修正会让竖拍小票横着送进模型
                val raw = BitmapFactory.decodeStream(stream) ?: return@runCatching null
                applyExifRotation(ctx, uri, raw)
            }
        }.getOrNull()
        if (bitmap != null) runOcr(bitmap) else errorMsg = "拍照失败，请重试或改用相册"
    }

    // 语音识别（系统弹窗，无需自带模型）
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
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "说出这笔账，如：今天午饭花了35块")
        }
        if (intent.resolveActivity(ctx.packageManager) == null) {
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

    /** 开关「文本识别用 AI」前置条件：没有可用 Key 时只提示，不写配置 */
    fun toggleUseAi(on: Boolean) {
        if (on && !aiReady) {
            configExpanded = true
            snackbar.show("先在上方填好 API Key 并保存")
            return
        }
        useAi = on
        AiConfig.setParseMode(ctx, if (on) AiConfig.MODE_AI else AiConfig.MODE_LOCAL)
    }

    val statusText = when {
        !aiEnabled -> "已关闭 · 仅本地识别"
        !aiReady -> "未填 Key · 暂走本地"
        useAi -> AiConfig.resolvedModel(ctx)
        else -> "文本用本地规则"
    }

    MineSubPage(title = "AI 记账", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 1. AI 状态：总开关 + 配置展开 ──
            IjiFormCard {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("AI 识别", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(2.dp))
                            Text(statusText, fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = aiEnabled,
                            onCheckedChange = { on ->
                                aiEnabled = on
                                AiConfig.setEnabled(ctx, on)
                                // 关掉总开关后 parseMode 会自动回落本地，界面同步过来
                                useAi = AiConfig.parseMode(ctx) == AiConfig.MODE_AI
                            },
                        )
                    }
                    IjiFormDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("服务配置", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IjiTextAction(
                            text = if (configExpanded) "收起" else "展开",
                            compact = true,
                            onClick = { configExpanded = !configExpanded },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = configExpanded) {
                AiServiceSection(context = ctx)
            }

            // ── 2. 记账区 ──
            if (selectedImage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(Radius.xxl),
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = selectedImage!!.asImageBitmap(),
                        contentDescription = "小票预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            if (isParsing) {
                StateView(state = ContentState.Loading("识别中"), size = StateSize.Inline)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                label = { Text("如：今天午饭花了35块") },
                shape = RoundedCornerShape(Radius.xxl),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("拍照", Icons.Default.PhotoCamera, compactWidth, !isParsing) {
                    val dir = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }
                    val file = java.io.File(dir, "receipt_${System.currentTimeMillis()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        ctx,
                        "${ctx.packageName}.fileprovider",
                        file,
                    )
                    cameraUri = uri
                    takePicture.launch(uri)
                }
                ActionButton("相册", Icons.Default.PhotoLibrary, compactWidth, !isParsing) {
                    imagePicker.launch("image/*")
                }
                ActionButton("语音", Icons.Default.Mic, compactWidth, !isParsing) {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        ctx,
                        android.Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) startVoice() else audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
                ActionButton(
                    text = "解析",
                    icon = null,
                    compact = compactWidth,
                    enabled = !isParsing,
                    primary = true,
                    loading = isParsing,
                ) { parseText(inputText) }
            }

            if (errorMsg.isNotBlank()) {
                Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            result?.let { bill ->
                ParseResultCard(
                    bill = bill,
                    ocrSource = ocrSource,
                    onReset = {
                        result = null
                        inputText = ""
                        selectedImage = null
                        ocrSource = ""
                    },
                    onConfirm = { onConfirm(bill) },
                )
            }

            // ── 3. AI 功能（对应 AutoAccounting「AI 功能」里 iji 真正有的三项） ──
            Column {
                IjiFormSectionTitle("AI 功能")
                IjiFormCard {
                    IjiSwitchRow(
                        title = "文本识别用 AI",
                        desc = "规则未命中时调用模型",
                        checked = useAi,
                        onCheckedChange = { toggleUseAi(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    IjiFormDivider()
                    IjiSwitchRow(
                        title = "图片识别用 AI",
                        desc = "本机认不出时发给视觉模型",
                        checked = visionEnabled,
                        onCheckedChange = { on ->
                            if (on && !aiReady) {
                                configExpanded = true
                                snackbar.show("先在上方填好 API Key 并保存")
                            } else {
                                visionEnabled = on
                                AiConfig.setVisionEnabled(ctx, on)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    IjiFormDivider()
                    IjiSwitchRow(
                        title = "AI 识别分类",
                        desc = "关闭时分类记为「其他」",
                        checked = categoryEnabled,
                        onCheckedChange = { on ->
                            categoryEnabled = on
                            AiConfig.setCategoryEnabled(ctx, on)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    IjiFormDivider()
                    IjiSwitchRow(
                        title = "账户识别用 AI",
                        desc = "从已有账户中匹配付款方式",
                        checked = accountEnabled,
                        onCheckedChange = { on ->
                            accountEnabled = on
                            AiConfig.setAccountEnabled(ctx, on)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    IjiFormDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("提示词与识别规则", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${AiPromptKind.entries.size} 类提示词 + 本地规则",
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }
                        IjiTextAction(text = "编辑", compact = true, onClick = onNavigatePrompts)
                    }
                }
            }
        }
    }
}


