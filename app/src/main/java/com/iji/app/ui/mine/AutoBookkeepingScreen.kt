package com.iji.app.ui.mine

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.iji.app.R
import com.iji.app.shizuku.ScreenCaptureTileService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iji.app.accessibility.A11ySelfHeal
import com.iji.app.accessibility.AccessibilityShortcutHelper
import com.iji.app.accessibility.AutoBookkeepingPrefs
import com.iji.app.accessibility.CapturePreferences
import com.iji.app.accessibility.PaymentAccessibilityStatus
import com.iji.app.accessibility.PaymentNotifyAccessibilityService
import com.iji.app.accessibility.ScreenCaptureFlow
import com.iji.app.autorecord.AutoListenPrefs
import com.iji.app.autorecord.AutoPlatforms
import com.iji.app.autorecord.AutoRecordStatusService
import com.iji.app.autorecord.AutoRecordStore
import com.iji.app.budget.NotificationHelper
import com.iji.app.shizuku.ShizukuBridge
import com.iji.app.shizuku.ShizukuState
import com.iji.app.ui.Routes
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiPrimaryButton
import com.iji.app.ui.components.IjiSettingSwitch
import com.iji.app.ui.components.IjiSettingValue
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.IjiSettingsDropdownRow
import com.iji.app.ui.components.IjiSettingsRow
import com.iji.app.ui.components.IjiSettingsSection
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.findActivity
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.overlay.BillOverlay
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.util.sharedMainViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

/**
 * 自动记账：**按能力链路分类，不按设置类型分类**。
 *
 * 两条互不依赖的能力：
 * - 支付通知记账（被动）：无障碍读支付通知 → 本机解析 → 用户确认 → 入账；
 * - 截图识屏记账（主动）：**页面最顶部的胶囊分段切换**选「半自动」或「全自动」，
 *   下面以「向上推动」的动画只展开所选模式的卡片。
 *   半自动 = 点按钮 / 磁贴 / 音量键 → 倒计时截屏 → 识别 → 确认 → 入账，截屏来源
 *   Shizuku / 无障碍二选一（自动模式优先 Shizuku、没授权就降级无障碍）；
 *   全自动 = 切到监听清单里的支付 App（微信 / 支付宝 / 云闪付）后延迟自动识屏，
 *   只依赖无障碍的窗口切换事件，Shizuku 帮不上忙，识别不到账单静默跳过
 *   （见 PaymentNotifyAccessibilityService.maybeAutoCapture）。
 *
 * 页面骨架遵循 Nexio 设置页规范：20dp 连续圆角卡片、卡内不画分隔线、组间 12dp、
 * 靠留白与 8dp 状态点分层。结构 = 分段切换（顶部，选中滑块弹性滑动）
 * → 配置状态卡（全页唯一「下一步」；全自动下 Shizuku 行收起）→ 分支内容（AnimatedContent）
 * → 支付通知卡 → 默认收起的高级设置。
 * 子页面（通知规则 / AI / 批量导入 / 权限检验 / 使用说明）按归属挂在卡片底部，一件事只出现一处。
 * 不重建已删的 3 步向导子页，不改任何偏好键。
 *
 * 参考 AutoAccounting 的权限自检卡与 Hyper-pick-up-code 的「设置在首页」组织方式，
 * 只借鉴交互结构，未复制其源码（GPL-3.0 / AGPL-3.0 与本项目闭源授权不兼容）。
 */
private val MODE_VALUES = listOf(
    CapturePreferences.MODE_AUTO,
    CapturePreferences.MODE_SHIZUKU,
    CapturePreferences.MODE_A11Y,
)

private val STYLE_VALUES = listOf(
    CapturePreferences.OVERLAY_DETAILED,
    CapturePreferences.OVERLAY_COMPACT,
    CapturePreferences.OVERLAY_ISLAND,
)

private val DELAY_OPTIONS = listOf(3, 5)

private fun modeLabel(value: String) = when (value) {
    CapturePreferences.MODE_SHIZUKU -> "Shizuku"
    CapturePreferences.MODE_A11Y -> "无障碍"
    else -> "自动"
}

private val WORKFLOW_VALUES = listOf(
    CapturePreferences.WORKFLOW_AUTO,
    CapturePreferences.WORKFLOW_SEMI,
)

private fun workflowLabel(value: String) = when (value) {
    CapturePreferences.WORKFLOW_AUTO -> "全自动"
    else -> "半自动"
}



private fun styleLabel(value: String) = when (value) {
    CapturePreferences.OVERLAY_DETAILED -> "详细"
    CapturePreferences.OVERLAY_COMPACT -> "简洁"
    else -> "通知"
}

private fun styleDescription(value: String) = when (value) {
    CapturePreferences.OVERLAY_DETAILED -> "详细卡片：金额、账户、分类、日期、标签、备注都能改"
    CapturePreferences.OVERLAY_COMPACT -> "简洁卡片：只读预览，点一行补改备注，行尾可剔除误识别"
    else -> "不弹卡片，以超级岛大岛提醒；选它就等价于关掉上方悬浮窗确认"
}

/**
 * 无障碍的三层状态：系统开关、服务连接、真正可用。
 * 混成一个布尔就会写出「开关已开但什么也没发生」这种用户看不懂的提示。
 */
private enum class A11yStage { OFF, NOT_CONNECTED, READY }

/** Shizuku 五态的短提示 + 对应动作；每个动作都必须真能做，不允许点了没反应 */
private data class ShizukuHint(val status: String, val action: String? = null)

private fun shizukuHint(state: ShizukuState): ShizukuHint = when (state) {
    ShizukuState.NOT_INSTALLED -> ShizukuHint("未安装 · 可选")
    ShizukuState.NOT_RUNNING -> ShizukuHint("已安装，但没在运行", "打开 Shizuku")
    ShizukuState.UNSUPPORTED -> ShizukuHint("版本低于 11，暂不能用", "打开 Shizuku")
    ShizukuState.NO_PERMISSION -> ShizukuHint("正在运行，还没授权给本应用", "请求授权")
    ShizukuState.READY -> ShizukuHint("已运行并授权 · 自动模式优先用它")
}

/**
 * 配置状态卡：全页唯一的「下一步」落点。
 *
 * 以前把授权、状态、触发、落账四组平铺，新用户不知道先点哪个；现在把
 * 「无障碍」与「Shizuku」的真实状态并排给出，并算出一个且只一个主按钮。
 * Shizuku 是否运行、是否授权都直接写在状态行里，不再只显示一个 ready 布尔。
 */
@Composable
private fun SetupStatusCard(
    readyCount: Int,
    a11yStage: A11yStage,
    shizuku: ShizukuHint,
    shizukuReady: Boolean,
    summary: String,
    showShizuku: Boolean,
    primary: Pair<String, () -> Unit>?,
    onA11yAction: () -> Unit,
    onShizukuAction: () -> Unit,
    onOpenPermissionCheck: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (readyCount == 2) "已就绪" else "$readyCount / 2 项就绪",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (readyCount == 2) MiuixPrimary else TextSecondary,
                )
            }
            StatusLine(
                ready = a11yStage == A11yStage.READY,
                label = "无障碍服务",
                status = when (a11yStage) {
                    A11yStage.OFF -> "未开启 · 通知记账不可用"
                    A11yStage.NOT_CONNECTED -> "开关已开，服务没连上"
                    A11yStage.READY -> "已连接"
                },
                action = if (a11yStage == A11yStage.READY) null else "去处理",
                onAction = onA11yAction,
            )
            // 全自动不依赖 Shizuku：切到全自动时整行收起——少一行就少一个「要不要装」的疑问
            AnimatedVisibility(
                visible = showShizuku,
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(150)),
            ) {
                StatusLine(
                    ready = shizukuReady,
                    label = "Shizuku",
                    status = shizuku.status,
                    action = shizuku.action,
                    onAction = onShizukuAction,
                )
            }
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                )
            }
            if (primary != null) {
                IjiPrimaryButton(
                    text = primary.first,
                    onClick = primary.second,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // 次要入口走中性色，全页只让「下一步」与主按钮抢视线
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IjiTextAction(
                    text = "逐项检查权限 ›",
                    color = TextSecondary,
                    compact = true,
                    onClick = onOpenPermissionCheck,
                )
                IjiTextAction(
                    text = "使用说明 ›",
                    color = TextSecondary,
                    compact = true,
                    onClick = onOpenGuide,
                )
            }
        }
    }
}

/** 8dp 状态点 + 两行文字 + 可选动作，卡内不画分隔线（靠留白分层） */
@Composable
private fun StatusLine(
    ready: Boolean,
    label: String,
    status: String,
    action: String?,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ready) MiuixPrimary else TextSecondary.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (ready) MiuixPrimary else TextSecondary,
            )
        }
        if (action != null) {
            IjiTextAction(text = action, compact = true, onClick = onAction)
        }
    }
}

/**
 * 一条能力链路的卡片：标题 + 状态 + 一句流程说明 + 属于它的设置行 + 属于它的子页面。
 * 把「这张卡管什么、去哪里继续配」写在同一块里，子页面关系不再散落在折叠区。
 */
@Composable
private fun RouteCard(
    title: String,
    ready: Boolean,
    stateLabel: String,
    summary: String,
    links: List<Pair<String, () -> Unit>>,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (ready) MiuixPrimary else TextSecondary.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) MiuixPrimary else TextSecondary,
                )
            }
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
            // 没有链接就不渲染这一行：否则会留下一个孤零零的「相关设置」标签
            if (links.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("相关设置", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    links.forEach { (label, onClick) ->
                        IjiTextAction(
                            text = "$label ›",
                            color = TextSecondary,
                            compact = true,
                            onClick = onClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AutoBookkeepingScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val mainVm = sharedMainViewModel()
    val scope = rememberCoroutineScope()

    /** 系统「添加到控制中心」对话框：结果码由系统回调，不假装成功 */
    fun addTile() {
        // requestAddTileService 为 13+ API，低版本如实引导手动编辑
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            snackbar.show("系统不支持一键添加（需 Android 13+），请下拉控制中心 → 编辑磁贴手动加入「识屏记账」")
            return
        }
        val manager = context.getSystemService(StatusBarManager::class.java)
        manager.requestAddTileService(
            ComponentName(context, ScreenCaptureTileService::class.java),
            "识屏记账",
            Icon.createWithResource(context, R.drawable.ic_tile_scan),
            ContextCompat.getMainExecutor(context),
        ) { result ->
            val added = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            if (!added) snackbar.show("未添加；可下拉控制中心 → 编辑磁贴，手动加入「识屏记账」")
        }
    }

    /** 系统状态只能在 ON_RESUME 复查（应用内改不了系统开关），推进它即重算全部派生状态 */
    var refreshKey by remember { mutableIntStateOf(0) }

    var masterEnabled by remember { mutableStateOf(AutoBookkeepingPrefs.masterEnabled(context)) }
    var mode by remember { mutableStateOf(CapturePreferences.mode(context)) }
    var volumeKey by remember { mutableStateOf(CapturePreferences.volumeKeyEnabled(context)) }
    var overlayConfirm by remember { mutableStateOf(CapturePreferences.overlayConfirmEnabled(context)) }
    var style by remember { mutableStateOf(CapturePreferences.overlayStyle(context)) }
    var delaySec by remember { mutableStateOf(CapturePreferences.captureDelaySeconds(context)) }
    var workflow by remember { mutableStateOf(CapturePreferences.workflow(context)) }
    // 全自动自己的监听清单，与半自动的截屏偏好完全分开（见 AutoListenPrefs）
    var triggerApps by remember { mutableStateOf(AutoListenPrefs.packages(context)) }
    var ledger by remember { mutableStateOf(AutoBookkeepingPrefs.ledger(context)) }
    var dupPolicy by remember { mutableStateOf(AutoBookkeepingPrefs.dupPolicy(context)) }
    var dedup by remember { mutableStateOf(AutoBookkeepingPrefs.dedupSeconds(context)) }
    var showCaptureConfirm by remember { mutableStateOf(false) }

    val recentEntries = remember(refreshKey) { AutoRecordStore.recent(context) }
    val unmatchedAsset = remember(refreshKey) { AutoRecordStore.lastUnmatched(context) }
    val assetNames = remember(refreshKey) {
        runCatching { mainVm.accounts.value.map { it.name } }.getOrDefault(emptyList())
    }
    val shizukuState = remember(refreshKey) { ShizukuBridge.state(context) }
    val shizukuReady = shizukuState == ShizukuState.READY
    val a11yEnabled = remember(refreshKey) { PaymentAccessibilityStatus.isEnabled(context) }
    // 系统实际绑定状态优先于进程内静态标记：进程刚重建时静态标记还没置位，
    // 但服务可能已经被系统绑上了（钱迹 RUNNING 的判定方式）
    val a11yConnected = remember(refreshKey) {
        PaymentAccessibilityStatus.isRunning(context) ||
            PaymentNotifyAccessibilityService.isConnected()
    }
    val a11yStage = when {
        !a11yEnabled -> A11yStage.OFF
        !a11yConnected -> A11yStage.NOT_CONNECTED
        else -> A11yStage.READY
    }
    val overlayGranted = remember(refreshKey) { BillOverlay.canShow(context) }
    // 后台保护三项：前两项能自动检测，后台锁定只能给操作路径
    val batteryUnrestricted = remember(refreshKey) {
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)
    }
    val notificationsEnabled = remember(refreshKey) { NotificationHelper.canPostNotifications(context) }
    // 从没连上过 ≠ 被清理：这个时间戳让提示能说具体原因
    val wasConnectedBefore = remember(refreshKey) { CapturePreferences.lastA11yConnectedAt(context) > 0 }
    // 是否已被 adb 授权写系统设置：决定「被清理后能不能自己修回来」
    val canSelfHeal = remember(refreshKey) { A11ySelfHeal.canWrite(context) }
    val shortcutBound = remember(refreshKey) {
        PaymentAccessibilityStatus.isVolumeShortcutBound(context)
    }
    val effective = CapturePreferences.resolveMode(mode, shizukuReady, a11yConnected)
    val fullAuto = workflow == CapturePreferences.WORKFLOW_AUTO
    // 半自动的截屏来源：Shizuku / 无障碍二选一。全自动走节点识别，根本不看截屏来源
    val semiSource = when (effective) {
        CapturePreferences.MODE_SHIZUKU -> "Shizuku"
        CapturePreferences.MODE_A11Y -> "无障碍服务"
        else -> null
    }
    val readyCount = (if (a11yStage == A11yStage.READY) 1 else 0) +
        (if (semiSource != null) 1 else 0)

    // 全自动的运行状态：无障碍是唯一前提，与 Shizuku、截屏来源都无关
    val autoRunning = masterEnabled && a11yStage == A11yStage.READY && triggerApps.isNotEmpty()
    // 胶囊只说全自动自己的状态：「总闸已关」直接归因到上面的开关，不叫「已暂停」这种要用户去猜的说法
    val autoStatusLabel = when {
        !masterEnabled -> "总闸已关"
        a11yStage != A11yStage.READY -> "待无障碍"
        triggerApps.isEmpty() -> "未选平台"
        else -> "运行中"
    }
    val autoLastText = recentEntries.firstOrNull()?.let { entry ->
        "最近：${AutoRecordStore.money(entry.amountCents)} · ${entry.platform} · ${entry.status}"
    } ?: "还没有识别记录：切到监听平台的账单页试试，识别到会先弹确认。"
    val ledgerOptions = remember(refreshKey) { listOf("") + mainVm.ledgers.toList() }

    var advancedExpanded by remember { mutableStateOf(false) }

    /** 开 / 关无障碍：Shizuku 就绪时借 shell 一键写入，否则如实跳系统设置 */
    fun toggleA11y() {
        if (!shizukuReady) {
            if (!PaymentAccessibilityStatus.openSettings(context)) {
                snackbar.show("无法打开无障碍设置，请手动前往")
            }
            return
        }
        scope.launch {
            val ok = if (a11yEnabled) {
                AccessibilityShortcutHelper.disableAccessibility(context)
            } else {
                AccessibilityShortcutHelper.enableAccessibility(context)
            }
            refreshKey++
            if (!ok) snackbar.show("没能写入系统设置，请到系统「无障碍」里手动开启")
        }
    }

    /** Shizuku 五态各给一个真能做的动作；未安装也要明确告诉用户「可以不装」 */
    fun shizukuAction() {
        when (shizukuState) {
            ShizukuState.NOT_INSTALLED ->
                snackbar.show("Shizuku 是可选的：不装也能用无障碍截屏")
            ShizukuState.NOT_RUNNING, ShizukuState.UNSUPPORTED ->
                if (!ShizukuBridge.openManager(context)) {
                    snackbar.show("无法打开 Shizuku，请手动启动它")
                }
            ShizukuState.NO_PERMISSION -> ShizukuBridge.requestPermission()
            ShizukuState.READY -> {}
        }
    }

    /** 全页唯一的「下一步」：按真实状态算，就绪后自动消失，不留一个点了没反应的按钮 */
    val primary: Pair<String, () -> Unit>? = when {
        a11yStage == A11yStage.OFF -> "去开启无障碍" to { toggleA11y() }
        a11yStage == A11yStage.NOT_CONNECTED -> "重新打开无障碍设置" to {
            if (!PaymentAccessibilityStatus.openSettings(context)) {
                snackbar.show("无法打开无障碍设置，请手动前往")
            }
        }
        // 「切回自动」只服务半自动：全自动根本不看截屏来源，别给用户无意义的修复动作
        !fullAuto && effective == null -> "切回自动截屏" to {
            mode = CapturePreferences.MODE_AUTO
            CapturePreferences.setMode(context, mode)
        }
        else -> null
    }

    /** 音量键开关 = 绑定 / 解绑系统快捷键，关掉时不留一个占着快捷键的幽灵服务 */
    fun setVolumeKey(on: Boolean) {
        volumeKey = on
        CapturePreferences.setVolumeKeyEnabled(context, on)
        if (!on) {
            if (shizukuReady && shortcutBound) {
                scope.launch {
                    val ok = AccessibilityShortcutHelper.unbindShortcut(context)
                    refreshKey++
                    if (!ok) snackbar.show("已关闭；系统快捷键未能自动解除，可在系统「无障碍」里改")
                }
            }
            return
        }
        if (shizukuReady) {
            scope.launch {
                val ok = AccessibilityShortcutHelper.bindShortcut(context)
                refreshKey++
                if (!ok) {
                    snackbar.show("已开启，但没能写入系统设置，请到系统「无障碍」里手动绑定")
                    PaymentAccessibilityStatus.openShortcutSettings(context)
                }
            }
        } else if (!shortcutBound) {
            if (!PaymentAccessibilityStatus.openShortcutSettings(context)) {
                snackbar.show("无法打开系统无障碍设置")
            } else {
                snackbar.show("请为「音量键识屏记账」开启快捷方式")
            }
        }
    }

    // 「自动」到底在自动什么：优先 Shizuku，没授权就降级无障碍
    val modeDescription = when {
        effective == CapturePreferences.MODE_SHIZUKU -> "当前用 Shizuku 截屏"
        effective == CapturePreferences.MODE_A11Y -> "当前用无障碍截屏，无需 Shizuku"
        mode == CapturePreferences.MODE_SHIZUKU ->
            "指定了 Shizuku，但它还没授权；切回「自动」即可"
        else -> "自动 = 优先 Shizuku，没授权就用无障碍"
    }

    val volumeKeyDescription = when {
        volumeKey && shortcutBound -> "已绑定本应用，按住音量上 + 下约 3 秒触发"
        volumeKey -> "开关已开，但系统音量键快捷键还没指向本应用"
        else -> "关闭；开启后可在系统「无障碍」里绑定快捷键"
    }

    MineSubPage(title = "自动记账", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 一张卡解决两个开关：总闸（管通知与识别）和方式（全自动 / 半自动）。
            // 拆成两张卡只多一次视觉停顿，没有信息增量。
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                IjiSettingsRow(title = "自动记账") {
                    IjiSettingSwitch(masterEnabled) { on ->
                        masterEnabled = on
                        AutoBookkeepingPrefs.setMasterEnabled(context, on)
                        // 总闸也是常驻状态服务的开关：关掉就不该再占一条通知
                        if (on) {
                            AutoRecordStatusService.startIfEnabled(context)
                        } else {
                            AutoRecordStatusService.stop(context)
                        }
                    }
                }
                IjiSettingsDivider()
                IjiSettingsDropdownRow(
                    title = "方式",
                    options = WORKFLOW_VALUES,
                    selected = workflow,
                    onSelect = {
                        workflow = it
                        CapturePreferences.setWorkflow(context, it)
                        // 状态服务只服务全自动：切到半自动立刻停，避免一条无意义的常驻通知
                        if (it == CapturePreferences.WORKFLOW_AUTO) {
                            AutoRecordStatusService.startIfEnabled(context)
                        } else {
                            AutoRecordStatusService.stop(context)
                        }
                    },
                    label = ::workflowLabel,
                )
            }

            // 2. 两套界面互不相关：全自动只讲节点识别与平台；半自动只讲截屏触发。
            //    targetState 判定保证退场内容不被新状态改写
            AnimatedContent(
                targetState = workflow,
                transitionSpec = {
                    (slideInVertically(tween(260)) { it } + fadeIn(tween(200))) togetherWith
                        (slideOutVertically(tween(260)) { -it } + fadeOut(tween(140)))
                },
                label = "workflowBranch",
            ) { target ->
                if (target == CapturePreferences.WORKFLOW_AUTO) {
                    // ── 全自动：全新的一整套组件，不出现截屏来源 / 磁贴 / 音量键 ──
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AutoRecordPanel(
                            running = autoRunning,
                            statusLabel = autoStatusLabel,
                            platformCount = triggerApps.size,
                            selected = triggerApps,
                            onToggle = { pkg ->
                                triggerApps = if (pkg in triggerApps) {
                                    triggerApps - pkg
                                } else {
                                    triggerApps + pkg
                                }
                                AutoListenPrefs.setPackages(context, triggerApps)
                                AutoRecordStatusService.refresh(context)
                            },
                            // 按钮常驻，承担开关语义：运行时是「关闭」，未就绪是「开启 / 恢复」。
                            // 文案本身就是归因：曾经连过又被系统关掉 → 「恢复」，从没开过 → 「开启」。
                            action = when {
                                a11yStage == A11yStage.READY -> "关闭无障碍" to { toggleA11y() }
                                wasConnectedBefore -> "恢复无障碍" to { toggleA11y() }
                                else -> "开启无障碍" to { toggleA11y() }
                            },
                            // 关闭是破坏性动作，用次要样式，避免主色按钮诱使误点
                            actionPrimary = a11yStage != A11yStage.READY,
                        )
                        AutoRecordRecentCard(entries = recentEntries)
                        // 用户可维护的「关键词 → 分类」规则：内置表判错时在这里纠正
                        AutoCategoryRulesCard()
                        // 只在**真的没匹配上**时才出现：平时这张卡只是一段说明，白占一屏
                        if (unmatchedAsset.isNotBlank()) {
                            AutoAssetMappingCard(
                                unmatched = unmatchedAsset,
                                accounts = assetNames,
                                onMap = { asset ->
                                    AutoRecordStore.saveMapping(context, unmatchedAsset, asset, platform = "")
                                    refreshKey++
                                },
                            )
                        }

                    }
                } else {
                    // ── 半自动：权限卡 + 截屏触发卡，删掉与触发无关的入口 ──
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SetupStatusCard(
                            readyCount = readyCount,
                            a11yStage = a11yStage,
                            shizuku = shizukuHint(shizukuState),
                            shizukuReady = shizukuReady,
                            // 原理说明删掉：用户只需要知道自己下一步点哪
                            summary = "",
                            showShizuku = !fullAuto,
                            primary = primary,
                            onA11yAction = { toggleA11y() },
                            onShizukuAction = { shizukuAction() },
                            onOpenPermissionCheck = { onNavigate(Routes.PERMISSION_CHECK) },
                            onOpenGuide = { onNavigate(Routes.GUIDE_TUTORIAL) },
                        )
                        RouteCard(
                            title = "半自动记账",
                            ready = semiSource != null,
                            stateLabel = if (semiSource == null) "未就绪" else semiSource.orEmpty(),
                            summary = "点一下，切到账单页",
                            links = emptyList(),
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                IjiPrimaryButton(
                                    text = "识屏",
                                    enabled = semiSource != null,
                                    onClick = { showCaptureConfirm = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            // 通知记账：一行状态 + 一个入口，不再用整张卡把原理再讲一遍
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                IjiSettingsRow(
                    title = "通知记账",
                    onClick = { onNavigate(Routes.NOTIFICATION_RULES) },
                ) {
                    IjiSettingValue(
                        when {
                            !masterEnabled -> "已暂停"
                            a11yStage == A11yStage.READY -> "运行中"
                            else -> "未开启"
                        },
                    )
                }
            }

            // 4. 与「怎么配」无关的细节：默认收起；撤销无障碍授权也留在这里，不占首屏
            IjiSettingsSection("高级设置") {
                IjiSettingsRow(
                    title = "识别与落账细节",
                    subtitle = "结果样式、账本、去重、倒计时等；默认即可用",
                    onClick = { advancedExpanded = !advancedExpanded },
                ) { IjiSettingValue(if (advancedExpanded) "收起" else "展开") }
                if (!advancedExpanded) return@IjiSettingsSection
                // 后台保护只在展开时出现：它是「想更稳」的加分项，不该占首屏
                Column(Modifier.padding(vertical = 6.dp)) {
                    AutoBackgroundProtectionCard(
                        batteryUnrestricted = batteryUnrestricted,
                        notificationsEnabled = notificationsEnabled,
                        onOpenBattery = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                                )
                            }
                        },
                        onOpenNotifications = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    },
                                )
                            }
                        },
                    )
                }
                // 高级恢复：没有 Shizuku 时，用一条 adb 命令换「被清理后能自己修回」。
                // 这是钱迹的做法：授权 WRITE_SECURE_SETTINGS 后直写系统设置，不依赖 Shizuku。
                IjiSettingsDivider()
                IjiSettingsRow(
                    title = "高级恢复",
                    subtitle = if (canSelfHeal) {
                        "已授权：被系统关闭后可自己修回"
                    } else {
                        "无 Shizuku 时，用电脑执行一次命令"
                    },
                    onClick = {
                        if (!canSelfHeal) {
                            runCatching {
                                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                                manager.setPrimaryClip(
                                    ClipData.newPlainText("adb", A11ySelfHeal.adbCommand(context)),
                                )
                            }
                            snackbar.show("命令已复制，粘贴到电脑终端执行")
                        }
                    },
                ) {
                    IjiSettingValue(if (canSelfHeal) "已开启" else "复制")
                }
                // 半自动专属的触发设置：全部收进这里，首屏只留一个大按钮
                if (!fullAuto) {
                    IjiSettingsDivider()
                    IjiSettingsDropdownRow(
                        title = "截屏来源",
                        options = MODE_VALUES,
                        selected = mode,
                        onSelect = {
                            mode = it
                            CapturePreferences.setMode(context, it)
                        },
                        label = ::modeLabel,
                        subtitle = modeDescription,
                    )
                    IjiSettingsDivider()
                    IjiSettingsRow(
                        title = "控制中心磁贴",
                        subtitle = "下拉控制中心点一下就能开始识别",
                        onClick = ::addTile,
                    ) { IjiSettingValue("添加") }
                    IjiSettingsDivider()
                    IjiSettingsRow("音量键快捷识屏", subtitle = volumeKeyDescription) {
                        IjiSettingSwitch(volumeKey) { on -> setVolumeKey(on) }
                    }
                }
                IjiSettingsDivider()
                if (a11yEnabled) {
                    IjiSettingsRow(
                        title = "关闭无障碍服务",
                        subtitle = if (shizukuReady) {
                            "借 Shizuku 撤销本应用的服务，不影响其它服务"
                        } else {
                            "跳系统「无障碍」里手动关闭"
                        },
                        onClick = { toggleA11y() },
                    ) { IjiSettingValue("关闭") }
                    IjiSettingsDivider()
                }
                // 「弹不弹卡片」的总闸放在样式上方：关掉会把样式自动切到「通知」，
                // 与下面的样式下拉保持单一状态，不再出现两个互相打架的入口。
                IjiSettingsRow(
                    title = "悬浮窗确认",
                    subtitle = when {
                        !overlayGranted -> "未授予「显示在其他应用上层」，开启会跳转系统设置"
                        overlayConfirm -> "识别结果浮在当前页面上确认，确认后才入账"
                        else -> "已关闭：自动切到「通知」样式，以通知 / 超级岛提醒"
                    },
                ) {
                    IjiSettingSwitch(overlayConfirm && overlayGranted) { on ->
                        if (on && !overlayGranted) {
                            if (!BillOverlay.openPermissionSettings(context)) {
                                snackbar.show("无法打开悬浮窗权限设置")
                            }
                            return@IjiSettingSwitch
                        }
                        overlayConfirm = on
                        CapturePreferences.setOverlayConfirmEnabled(context, on)
                        val nextStyle = when {
                            !on -> CapturePreferences.OVERLAY_ISLAND
                            style == CapturePreferences.OVERLAY_ISLAND -> CapturePreferences.OVERLAY_COMPACT
                            else -> style
                        }
                        if (nextStyle != style) {
                            style = nextStyle
                            CapturePreferences.setOverlayStyle(context, nextStyle)
                        }
                    }
                }
                IjiSettingsDivider()
                IjiSettingsDropdownRow(
                    title = "识别结果样式",
                    options = STYLE_VALUES,
                    selected = style,
                    onSelect = {
                        style = it
                        CapturePreferences.setOverlayStyle(context, it)
                        // 反向联动：选「通知」等价于关掉悬浮窗确认，选卡片样式则打开
                        val confirm = it != CapturePreferences.OVERLAY_ISLAND
                        overlayConfirm = confirm
                        CapturePreferences.setOverlayConfirmEnabled(context, confirm)
                    },
                    label = ::styleLabel,
                    subtitle = styleDescription(style),
                )
                IjiSettingsDivider()
                IjiSettingsDropdownRow(
                    title = "落账账本",
                    options = ledgerOptions,
                    selected = ledger,
                    onSelect = {
                        ledger = it
                        AutoBookkeepingPrefs.setLedger(context, it)
                    },
                    label = { if (it.isBlank()) "跟随当前账本" else it },
                    subtitle = "识屏悬浮窗与通知确认的账单记到这里；页内快存同样遵循",
                )
                IjiSettingsDivider()
                IjiSettingsDropdownRow(
                    title = "重复通知合并",
                    options = AutoBookkeepingPrefs.DEDUP_OPTIONS,
                    selected = dedup,
                    onSelect = {
                        dedup = it
                        AutoBookkeepingPrefs.setDedupSeconds(context, it)
                    },
                    label = { "$it 秒" },
                    subtitle = "同来源、同金额的通知在窗口内只提示一次",
                )
                if (fullAuto) {
                    // 重复识别只服务全自动：半自动是用户主动点的，不存在「页面反复触发」
                    IjiSettingsDivider()
                    IjiSettingsDropdownRow(
                        title = "重复识别",
                        options = AutoBookkeepingPrefs.DUP_POLICY_VALUES,
                        selected = dupPolicy,
                        onSelect = {
                            dupPolicy = it
                            AutoBookkeepingPrefs.setDupPolicy(context, it)
                        },
                        label = AutoBookkeepingPrefs::dupPolicyLabel,
                        subtitle = "同一页面反复触发时的处理；同日同额只提示、不拦掉",
                    )
                }
                if (!fullAuto) {
                    // 倒计时只服务半自动；全自动的等待时间按平台固定（微信 150ms / 其余 300ms），
                    // 用户不需要也不应该为它做决定
                    IjiSettingsDivider()
                    IjiSettingsDropdownRow(
                        title = "识屏倒计时",
                        options = DELAY_OPTIONS,
                        selected = delaySec,
                        onSelect = {
                            delaySec = it
                            CapturePreferences.setCaptureDelaySeconds(context, it)
                        },
                        label = { "$it 秒" },
                        subtitle = "点「开始识别」后留给你切到账单页的时间",
                    )
                }
                IjiSettingsDivider()
                IjiSettingsRow(
                    title = "AI 记账",
                    subtitle = "识别方式、API Key 与 AI 功能开关",
                    onClick = { onNavigate(Routes.AI) },
                ) { IjiSettingValue() }
                IjiSettingsDivider()
                IjiSettingsRow(
                    title = "截屏批量导入",
                    subtitle = "账户列表 / 账单列表整屏识别成多笔（属半自动能力）",
                    onClick = { onNavigate(Routes.SCREENSHOT_IMPORT) },
                ) { IjiSettingValue() }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                    refreshKey++
                    // 进程可能刚被系统重建/重开：只要该跑，就把常驻状态服务拉起来
                    AutoRecordStatusService.startIfEnabled(context)
                }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
        }
    }

    IjiDialog(
        show = showCaptureConfirm,
        onDismissRequest = { showCaptureConfirm = false },
        title = "截屏识别账单",
        summary = "开始后本应用退到后台，$delaySec 秒后截取当前屏幕并在本机识别。",
        confirmText = "开始识别",
        onConfirm = {
            showCaptureConfirm = false
            val delayMs = CapturePreferences.captureDelayMs(context)
            val started = when (effective) {
                CapturePreferences.MODE_SHIZUKU ->
                    ScreenCaptureFlow.startWithShizuku(context, delayMs)
                CapturePreferences.MODE_A11Y ->
                    ScreenCaptureFlow.start(context, delayMs)
                else -> false
            }
            if (!started) {
                snackbar.show("没能发起截屏：授权可能已失效")
            } else {
                context.findActivity()?.moveTaskToBack(true)
            }
        },
    ) {
        Text(
            "请先切到支付宝 / 微信的账单列表页——截到的是那一刻的屏幕，停在本页只会认出本应用。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}
