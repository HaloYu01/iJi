package com.iji.app.ui.mine

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.accessibility.PaymentAccessibilityStatus
import com.iji.app.accessibility.PaymentNotifyAccessibilityService
import com.iji.app.accessibility.ScreenCaptureFlow
import com.iji.app.ai.BillDraft
import com.iji.app.ai.ImageBillRecognizer
import com.iji.app.ai.TempImages
import com.iji.app.ai.applyExifRotation
import com.iji.app.ai.markDuplicates
import com.iji.app.data.model.Accounts
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.IjiPrimaryButtonBlue
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.findActivity
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleOrange
import com.iji.app.ui.theme.DividerGray
import com.iji.app.ui.theme.ExpenseAmount
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.components.formatSignedMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import java.io.File
import com.iji.app.ui.util.sharedMainViewModel

/**
 * 截屏导入：把支付宝 / 微信**账单列表页**的整屏截图识别成多笔账单。
 *
 * 与 CSV 导入的差别（也是这次要补的两件事）：
 * - **识别**：列表页一屏十几行，必须按行切分逐行抽字段（见 [ScreenshotBillParser]）；
 * - **预览 + 去重**：CSV 那套是选完文件就直接写库、无预览、无去重，同一份账单重复导入会翻倍。
 *   这里一律先出预览，疑似重复项默认不勾选。
 *
 * 版式识别不可能 100% 准，所以预览不是可选步骤而是**强制环节**。
 */
@Composable
fun ScreenshotImportScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = sharedMainViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarController.current

    // 判重用的库内账单：当前账本全量。本页**不做账本选择** ——
    // 判重必须和"要写进去的那本账"用同一份数据，否则换个账本就判不准；
    // 需要导到别的账本时，先切账本再进这里（预览页会显示当前账本）。
    val existing by viewModel.transactions.collectAsStateWithLifecycle()
    val ledger by viewModel.currentLedger.collectAsStateWithLifecycle()
    val assetAccounts by viewModel.accounts.collectAsStateWithLifecycle()

    val drafts = remember { mutableStateListOf<BillDraft>() }
    var recognizing by remember { mutableStateOf(false) }
    // 这批账单记到哪个账户（截屏来自哪个 App）；识别后按 App 名自动预选
    var batchAccount by remember { mutableStateOf("") }
    var showAccountPicker by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val accountOptions = remember(assetAccounts) {
        (Accounts.all + assetAccounts.map { it.name }).distinct()
    }

    /** 识别结果 → 预览草稿（判重 + 自动预选账户） */
    fun applyFound(found: List<BillDraft>) {
        if (found.isEmpty()) {
            snackbar.show("没有从这张图里识别出账单，请确认截的是账单列表页")
            return
        }
        drafts.clear()
        drafts.addAll(markDuplicates(found, existing))
        // 自动预选账户：草稿里带的是整屏文字猜出来的 App 名
        batchAccount = found.firstOrNull { it.account.isNotBlank() }?.account ?: ""
    }

    /** 取图 → 识别 → 判重，产出预览草稿 */
    fun recognize(uri: Uri, tempFile: File? = null) {
        scope.launch {
            recognizing = true
            val bmp = withContext(Dispatchers.IO) {
                val decoded = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()?.let { applyExifRotation(context, uri, it) }
                // 位图（含 EXIF 修正）已经在内存里了，拍照产生的临时文件立刻删
                TempImages.delete(tempFile)
                decoded
            }

            if (bmp == null) {
                recognizing = false
                snackbar.show("图片读取失败，请换一张截图")
                return@launch
            }

            // 后台自动识屏 ScreenCaptureFlow 刻意不走它：无感场景不发网、图不出设备。
            val outcome = withContext(Dispatchers.Default) {
                ImageBillRecognizer.recognizeDrafts(context, bmp)
            }
            recognizing = false
            if (outcome.drafts.isEmpty()) {
                snackbar.show(outcome.hint ?: "没有从这张图里识别出账单，请确认截的是账单列表页")
            } else {
                applyFound(outcome.drafts)
            }
        }
    }

    // ── 无障碍截屏：不用自己截图，直接在微信/支付宝账单页取一帧 ──
    // 截的是「当前可见的那一屏」，所以必须先把本 App 退到后台，否则截到的是 iji 自己。
    val lifecycleOwner = LocalLifecycleOwner.current
    var a11yConnected by remember { mutableStateOf(PaymentNotifyAccessibilityService.isConnected()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yConnected = PaymentNotifyAccessibilityService.isConnected()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /** 服务在后台跑完识别后把草稿送回来（结果同时有一条通知，点击可回到本页） */
    val capturedDrafts by ScreenCaptureFlow.drafts.collectAsStateWithLifecycle()
    LaunchedEffect(capturedDrafts) {
        val found = capturedDrafts ?: return@LaunchedEffect
        applyFound(found)
        ScreenCaptureFlow.consume()
    }

    // 截屏失败 / 没识别出账单时也有一条文案：通知没授权时，这里是唯一能看到的反馈
    val captureNotice by ScreenCaptureFlow.notice.collectAsStateWithLifecycle()
    LaunchedEffect(captureNotice) {
        val message = captureNotice ?: return@LaunchedEffect
        snackbar.show(message)
        ScreenCaptureFlow.consumeNotice()
    }

    fun captureCurrentScreen() {
        if (!a11yConnected) {
            snackbar.show("请先开启无障碍服务，才能截取屏幕")
            PaymentAccessibilityStatus.openSettings(context)
            return
        }
        if (!ScreenCaptureFlow.start(context)) {
            snackbar.show("上一条截屏还在识别中，请稍候")
            return
        }
        snackbar.show("3 秒后自动截屏，请立刻切到账单页")
        // 本 App 退到后台，让支付 App 的账单页成为「当前屏幕」
        context.findActivity()?.moveTaskToBack(true)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { recognize(it) } }

    // 拍照用的临时文件走 FileProvider；路径必须是 file_paths.xml 白名单内的 cache/camera
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    /** 相机写进 cache 的原图，识别完立刻删（识别失败也删，留着只会占缓存） */
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraUri
        val shot = cameraFile
        if (!ok || uri == null) {
            TempImages.delete(shot)
            return@rememberLauncherForActivityResult
        }
        recognize(uri, tempFile = shot)
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            TempImages.delete(file)
            snackbar.show("无法创建拍照文件")
            return
        }
        cameraUri = uri
        cameraFile = file
        cameraLauncher.launch(uri)
    }

    // 兜底清理：上次取消拍照 / 进程被杀留下的 cache/camera 图片
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            TempImages.sweep(File(context.cacheDir, "camera"))
        }
    }

    val selectedCount = drafts.count { it.selected }
    val dupCount = drafts.count { it.duplicateOfId != null }

    // 顶栏是浮层，用 Spacer 让出「安全区 + 52dp 折叠态」；三个 pane 自带 fillMaxSize，
    // 会自动占满顶栏下方的剩余高度，无需改动 pane 内部。
    MineSubPage(title = "截屏导入", onBack = onBack) {
        Spacer(Modifier.height(ijiTopBarContentPadding()))

        when {
            recognizing -> RecognizingPane()

            drafts.isEmpty() -> PickPane(
                ledger = ledger,
                account = batchAccount,
                a11yConnected = a11yConnected,
                onPickAccount = { showAccountPicker = true },
                onPickGallery = { galleryLauncher.launch("image/*") },
                onTakePhoto = { launchCamera() },
                onCaptureScreen = { captureCurrentScreen() },
            )

            else -> PreviewPane(
                drafts = drafts,
                selectedCount = selectedCount,
                dupCount = dupCount,
                account = batchAccount,
                ledger = ledger,
                importing = importing,
                onPickAccount = { showAccountPicker = true },
                onToggle = { index -> drafts[index] = drafts[index].copy(selected = !drafts[index].selected) },
                onRemove = { index -> drafts.removeAt(index) },
                onToggleAll = {
                    val target = selectedCount < drafts.size
                    for (i in drafts.indices) drafts[i] = drafts[i].copy(selected = target)
                },
                onReset = { drafts.clear() },
                onImport = {
                    importing = true
                    val payload = drafts.filter { it.selected }
                    viewModel.importDrafts(
                        drafts = payload,
                        targetLedger = null,
                        account = batchAccount,
                    ) { ok, failed ->
                        importing = false
                        drafts.clear()
                        if (failed > 0) snackbar.show("已导入 $ok 笔，失败 $failed 笔")
                    }
                },
            )
        }
    }

    if (showAccountPicker) {
        AccountChoiceDialog(
            accounts = accountOptions,
            selected = batchAccount,
            onDismiss = { showAccountPicker = false },
            onPick = {
                batchAccount = it
                showAccountPicker = false
            },
        )
    }
}

/** 空态：说明怎么用 + 选图入口 */
@Composable
private fun PickPane(
    ledger: String,
    account: String,
    a11yConnected: Boolean,
    onPickAccount: () -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onCaptureScreen: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = Radius.xxl,
            colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("怎么用", fontWeight = FontWeight.SemiBold)
                Text(
                    "1. 打开支付宝 / 微信的「账单」列表页\n" +
                        "2. 截屏（尽量让多笔账单落在一屏里）\n" +
                        "3. 回到这里选择这张截图\n" +
                        "4. 核对识别结果后再导入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "识别结果仅供参考，务必在下一步逐笔核对；与已有账单重复的会自动取消勾选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleOrange,
                )
            }
        }

        SettingsRowLite(
            label = "导入到账本",
            value = ledger,
        )
        SettingsRowLite(
            label = "账单账户",
            value = account.ifBlank { "点击选择" },
            onClick = onPickAccount,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigAction(
                text = "选择截图",
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = onPickGallery,
            )
            BigAction(
                text = "拍照",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = onTakePhoto,
            )
        }

        // 无障碍截屏：不用自己截图，直接在账单页取一帧
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = Radius.xxl,
            colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("不用手动截图", fontWeight = FontWeight.SemiBold)
                Text(
                    if (a11yConnected) {
                        "点「截取屏幕」后本 App 会退到后台，3 秒后自动截取当前画面并识别。" +
                            "请在这 3 秒内切到微信 / 支付宝的账单页。识别完成后会有一条通知带你回来核对。"
                    } else {
                        "开启无障碍服务后，可在账单页直接取一帧识别，免去「截屏 → 相册选图」。" +
                            "点下面的按钮去系统设置开启。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                BigAction(
                    text = if (a11yConnected) "截取屏幕（3 秒后）" else "去开启无障碍服务",
                    primary = a11yConnected,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCaptureScreen,
                )
            }
        }
    }
}

/** 识别中 */
@Composable
private fun RecognizingPane() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MiuixPrimary)
            Spacer(Modifier.height(12.dp))
            Text("正在识别截图…", color = TextSecondary)
        }
    }
}

/** 预览：逐笔可勾选、可删、重复项标注 */
@Composable
private fun PreviewPane(
    drafts: List<BillDraft>,
    selectedCount: Int,
    dupCount: Int,
    account: String,
    ledger: String,
    importing: Boolean,
    onPickAccount: () -> Unit,
    onToggle: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onToggleAll: () -> Unit,
    onReset: () -> Unit,
    onImport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalDimens.current.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "识别到 ${drafts.size} 笔，已选 $selectedCount 笔",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    buildString {
                        append("账户 ${account.ifBlank { "未选" }} · 账本 $ledger")
                        if (dupCount > 0) append(" · 疑似重复 $dupCount 笔")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dupCount > 0) AppleOrange else TextSecondary,
                )
            }
            Text(
                if (selectedCount < drafts.size) "全选" else "全不选",
                style = MaterialTheme.typography.bodyMedium,
                color = MiuixPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onToggleAll() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                "重选",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onReset() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = LocalDimens.current.screenPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(drafts.size) { index ->
                DraftRow(
                    draft = drafts[index],
                    onToggle = { onToggle(index) },
                    onRemove = { onRemove(index) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // 底部操作条
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalDimens.current.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsRowLite(
                label = "账单账户",
                value = account.ifBlank { "点击选择" },
                onClick = onPickAccount,
            )
            BigAction(
                text = if (importing) "正在导入…" else "导入 $selectedCount 笔",
                primary = true,
                enabled = selectedCount > 0 && !importing,
                modifier = Modifier.fillMaxWidth(),
                onClick = onImport,
            )
        }
    }
}

/** 单笔草稿行 */
@Composable
private fun DraftRow(
    draft: BillDraft,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val isDup = draft.duplicateOfId != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 勾选框：沿用首页多选的方框样式，保持观感一致
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(Radius.dot))
                .background(if (draft.selected) MiuixPrimary else DividerGray),
            contentAlignment = Alignment.Center,
        ) {
            if (draft.selected) {
                Text("✓", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    draft.note.ifBlank { "未识别商户" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isDup) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(AppleOrange.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("重复", style = MaterialTheme.typography.bodySmall, color = AppleOrange)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${draft.date.monthValue}月${draft.date.dayOfMonth}日 · " +
                    runCatching { CategoryCatalog.of(draft.categoryId).name }.getOrDefault("其他"),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(8.dp))
        Text(
            formatSignedMoney(draft.type, draft.amountCents),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = when (draft.type) {
                // 全局约定：消费红 / 收入绿
                TransactionType.EXPENSE -> ExpenseAmount
                TransactionType.INCOME -> IncomeGreen
                TransactionType.TRANSFER -> TextSecondary
            },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "×",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onRemove() }
                .padding(6.dp),
        )
    }
}

/** 两列表单行（本页自用，不引设置页的组件以免耦合） */
@Composable
private fun SettingsRowLite(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MiuixPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 主行动按钮 */
@Composable
private fun BigAction(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(
                when {
                    !enabled -> DividerGray
                    primary -> IjiPrimaryButtonBlue
                    else -> DividerGray
                },
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary && enabled) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 账户选择弹窗（本页自用） */
@Composable
private fun AccountChoiceDialog(
    accounts: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    if (accounts.isEmpty()) {
        onDismiss()
        return
    }
    var open by remember { mutableStateOf(true) }
    top.yukonga.miuix.kmp.overlay.OverlayBottomSheet(
        show = open,
        onDismissRequest = {
            open = false
            onDismiss()
        },
        title = "账单账户",
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalDimens.current.screenPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(accounts) { name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(if (name == selected) DividerGray else Color.Transparent)
                        .clickable {
                            open = false
                            onPick(name)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    if (name == selected) {
                        Text("✓", color = MiuixPrimary)
                    }
                }
            }
        }
    }
}
