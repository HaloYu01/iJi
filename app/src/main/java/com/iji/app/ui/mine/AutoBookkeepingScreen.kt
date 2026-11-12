package com.iji.app.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import com.iji.app.R
import com.iji.app.shizuku.ScreenCaptureTileService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iji.app.accessibility.AccessibilityShortcutHelper
import com.iji.app.accessibility.AutoBookkeepingPrefs
import com.iji.app.accessibility.CapturePreferences
import com.iji.app.accessibility.PaymentAccessibilityStatus
import com.iji.app.accessibility.PaymentNotifyAccessibilityService
import com.iji.app.accessibility.ScreenCaptureFlow
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
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.findActivity
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.overlay.BillOverlay
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.util.sharedMainViewModel
import kotlinx.coroutines.launch

/**
 * 自动记账：一页讲清「能不能用、怎么触发、记到哪」。
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
        val manager = context.getSystemService(Context.STATUS_BAR_SERVICE) as StatusBarManager
        manager.requestAddTileService(
            ComponentName(context, ScreenCaptureTileService::class.java),
            "识屏记账",
            Icon.createWithResource(context, R.drawable.ic_tile_scan),
            ContextCompat.getMainExecutor(context),
        ) { result ->
            val added = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            snackbar.show(
                if (added) "磁贴已加入控制中心，下拉即可识屏"
                else "未添加；可下拉控制中心 → 编辑磁贴，手动加入「识屏记账」",
            )
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
    var ledger by remember { mutableStateOf(AutoBookkeepingPrefs.ledger(context)) }
    var dedup by remember { mutableStateOf(AutoBookkeepingPrefs.dedupSeconds(context)) }
    var showCaptureConfirm by remember { mutableStateOf(false) }

    val shizukuReady = remember(refreshKey) {
        ShizukuBridge.state(context) == ShizukuState.READY
    }
    val a11yEnabled = remember(refreshKey) { PaymentAccessibilityStatus.isEnabled(context) }
    val a11yConnected = remember(refreshKey) { PaymentNotifyAccessibilityService.isConnected() }
    val overlayGranted = remember(refreshKey) { BillOverlay.canShow(context) }
    val shortcutBound = remember(refreshKey) {
        PaymentAccessibilityStatus.isVolumeShortcutBound(context)
    }
    val effective = CapturePreferences.resolveMode(mode, shizukuReady, a11yConnected)
    val ledgerOptions = remember(refreshKey) { listOf("") + mainVm.ledgers.toList() }

    val readyText = listOf(
        if (shizukuReady) "Shizuku 就绪" else "Shizuku 未就绪",
        if (a11yEnabled) "无障碍已开启" else "无障碍未开启",
        if (overlayGranted) "悬浮窗已授权" else "悬浮窗未授权",
        if (shortcutBound) "音量键已绑定" else "音量键未绑定",
    ).joinToString(" · ")

    val modeDescription = when (effective) {
        CapturePreferences.MODE_SHIZUKU -> "当前生效 Shizuku：随时可截、无授权弹窗"
        CapturePreferences.MODE_A11Y -> "当前生效无障碍截屏：需系统已开启通知服务"
        else -> "两种方式都不可用，先完成其中一个授权"
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
            // ── 1. 状态与总闸 ──
            IjiSettingsSection("状态与总闸") {
                IjiSettingsRow(
                    title = "自动记账",
                    subtitle = "关闭后支付通知不再提示记账；识屏始终由你主动触发",
                ) {
                    IjiSettingSwitch(masterEnabled) { on ->
                        masterEnabled = on
                        AutoBookkeepingPrefs.setMasterEnabled(context, on)
                    }
                }
                IjiSettingsDivider()
                IjiSettingsRow(
                    title = "权限就绪度",
                    subtitle = readyText,
                    onClick = { onNavigate(Routes.PERMISSION_CHECK) },
                ) { IjiSettingValue("查看") }
            }

            // ── 2. 触发器 ──
            IjiSettingsSection("触发器") {
                IjiSettingsRow(
                    title = "支付通知记账",
                    subtitle = if (a11yEnabled) {
                        "已开启：只读支付通知文字，本机解析后提示你确认"
                    } else {
                        "未开启：需在系统「无障碍」里授权"
                    },
                    onClick = { onNavigate(Routes.NOTIFICATION_RULES) },
                ) { IjiSettingValue(if (a11yEnabled) "已开启" else "去开启") }
                IjiSettingsDivider()
                IjiSettingsDropdownRow(
                    title = "截图授权方式",
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
                IjiSettingsRow("音量键快捷识屏", subtitle = volumeKeyDescription) {
                    IjiSettingSwitch(volumeKey) { on ->
                        volumeKey = on
                        CapturePreferences.setVolumeKeyEnabled(context, on)
                        if (!on) {
                            // 关 = 解绑，避免「应用内关了、系统快捷键还占着」
                            if (shizukuReady && shortcutBound) {
                                scope.launch {
                                    val ok = AccessibilityShortcutHelper.unbindShortcut(context)
                                    refreshKey++
                                    snackbar.show(
                                        if (ok) "已关闭并解除系统快捷键绑定"
                                        else "已关闭；系统快捷键未能自动解除，可在系统「无障碍」里改",
                                    )
                                }
                            }
                            return@IjiSettingSwitch
                        }
                        if (shizukuReady) {
                            scope.launch {
                                val ok = AccessibilityShortcutHelper.bindShortcut(context)
                                refreshKey++
                                snackbar.show(
                                    if (ok) "已开启并绑定音量键快捷键"
                                    else "已开启，但没能写入系统设置，请到系统「无障碍」里手动绑定",
                                )
                                if (!ok) PaymentAccessibilityStatus.openShortcutSettings(context)
                            }
                        } else if (!shortcutBound) {
                            if (!PaymentAccessibilityStatus.openShortcutSettings(context)) {
                                snackbar.show("无法打开系统无障碍设置")
                            } else {
                                snackbar.show("请为「音量键识屏记账」开启快捷方式")
                            }
                        }
                    }
                }
            }

            IjiSettingsSection("一键授权") {
                IjiSettingsRow(
                    title = "添加识屏磁贴",
                    subtitle = "弹系统确认框，把「识屏记账」加进控制中心快捷栏",
                    onClick = ::addTile,
                ) { IjiSettingValue("添加") }
                IjiSettingsDivider()
                IjiSettingsRow(
                    title = if (a11yEnabled) "关闭无障碍服务" else "一键开启无障碍",
                    subtitle = if (shizukuReady) {
                        "只把本应用服务追加到系统已启用列表，不覆盖其它服务；可撤销"
                    } else {
                        "需要 Shizuku 已授权；否则点这里跳系统无障碍设置"
                    },
                    onClick = {
                        if (!shizukuReady) {
                            PaymentAccessibilityStatus.openSettings(context)
                            return@IjiSettingsRow
                        }
                        scope.launch {
                            val ok = if (a11yEnabled) {
                                AccessibilityShortcutHelper.disableAccessibility(context)
                            } else {
                                AccessibilityShortcutHelper.enableAccessibility(context)
                            }
                            refreshKey++
                            snackbar.show(
                                if (ok) {
                                    if (a11yEnabled) "已恢复系统无障碍设置" else "已开启，支付通知可提示记账"
                                } else {
                                    "没能写入系统设置，请到系统「无障碍」里手动开启"
                                },
                            )
                        }
                    },
                ) { IjiSettingValue(if (a11yEnabled) "关闭" else "开启") }
            }

            IjiPrimaryButton(
                text = "立即识屏",
                enabled = effective != null,
                onClick = { showCaptureConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            )
            if (effective == null) {
                Text(
                    "先完成 Shizuku 授权或开启无障碍服务，「立即识屏」才可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // ── 3. 识别与落账 ──
            IjiSettingsSection("识别与落账") {
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
                IjiSettingsDivider()
                IjiSettingsRow(
                    title = "AI 记账",
                    subtitle = "识别方式、API Key 与 AI 功能开关",
                    onClick = { onNavigate(Routes.AI) },
                ) { IjiSettingValue() }
                IjiSettingsDivider()
                IjiSettingsRow(
                    title = "截屏批量导入",
                    subtitle = "账单列表页整屏识别成多笔，强制预览后导入",
                    onClick = { onNavigate(Routes.SCREENSHOT_IMPORT) },
                ) { IjiSettingValue() }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) refreshKey++
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
