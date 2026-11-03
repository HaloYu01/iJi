package com.iji.app.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iji.app.accessibility.CapturePreferences
import com.iji.app.accessibility.PaymentAccessibilityStatus
import com.iji.app.accessibility.PaymentNotifyAccessibilityService
import com.iji.app.accessibility.ScreenCaptureFlow
import com.iji.app.shizuku.ShizukuBridge
import com.iji.app.shizuku.ShizukuState
import com.iji.app.ui.components.IjiCategoryChips
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiFormDivider
import com.iji.app.ui.components.IjiFormSectionTitle
import com.iji.app.ui.components.IjiPrimaryButton
import com.iji.app.ui.components.IjiSwitchRow
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.findActivity
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.overlay.BillOverlay
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.TextSecondary

/**
 * 识屏与授权：把原来散在自动记账页的三张卡（Shizuku / 悬浮窗 / 截屏）收成一页。
 *
 * 页面只回答两件事：**用什么方式截**（Shizuku / 无障碍）和**截完怎么确认**（悬浮窗 / 通知）。
 * 音量键快捷只是一个开关，绑定动作交给系统设置（无障碍 → 音量键快捷键）。
 */
private val MODE_OPTIONS = listOf("自动", "Shizuku", "无障碍")
private val MODE_VALUES = listOf(
    CapturePreferences.MODE_AUTO,
    CapturePreferences.MODE_SHIZUKU,
    CapturePreferences.MODE_A11Y,
)

/** 识别结果的呈现样式：详细（全可编辑）/ 简洁（只补备注）/ 通知（超级岛提醒） */
private val OVERLAY_STYLE_OPTIONS = listOf("详细", "简洁", "通知")
private val OVERLAY_STYLE_VALUES = listOf(
    CapturePreferences.OVERLAY_DETAILED,
    CapturePreferences.OVERLAY_COMPACT,
    CapturePreferences.OVERLAY_ISLAND,
)

@Composable
fun ScreenCaptureSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current

    var mode by remember { mutableStateOf(CapturePreferences.mode(context)) }
    var volumeKey by remember { mutableStateOf(CapturePreferences.volumeKeyEnabled(context)) }
    var shizukuState by remember { mutableStateOf(ShizukuBridge.state(context)) }
    var a11yConnected by remember { mutableStateOf(PaymentNotifyAccessibilityService.isConnected()) }
    var overlayEnabled by remember { mutableStateOf(BillOverlay.canShow(context)) }
    var overlayStyle by remember { mutableStateOf(CapturePreferences.overlayStyle(context)) }
    var showConfirm by remember { mutableStateOf(false) }

    fun refresh() {
        shizukuState = ShizukuBridge.state(context)
        a11yConnected = PaymentNotifyAccessibilityService.isConnected()
        overlayEnabled = BillOverlay.canShow(context)
    }

    val effective = CapturePreferences.resolveMode(
        mode = mode,
        shizukuReady = shizukuState == ShizukuState.READY,
        a11yConnected = a11yConnected,
    )

    fun shizukuAction() {
        when (shizukuState) {
            ShizukuState.READY -> snackbar.show("Shizuku 已授权，可直接识屏")
            ShizukuState.NO_PERMISSION -> {
                ShizukuBridge.requestPermission()
                snackbar.show("请在 Shizuku 的授权弹窗里点「允许」")
            }
            ShizukuState.NOT_RUNNING -> if (!ShizukuBridge.openManager(context)) {
                snackbar.show("打不开 Shizuku，请在它自己的界面里启动服务")
            }
            ShizukuState.NOT_INSTALLED -> snackbar.show("需要先安装 Shizuku（GitHub: RikkaApps/Shizuku）")
            ShizukuState.UNSUPPORTED -> snackbar.show("Shizuku 版本过低，需要 11 及以上")
        }
    }

    MineSubPage(title = "识屏与授权", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IjiFormCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "识屏 = 截取当前屏幕 → 本机 OCR → 拆成多笔供你核对。图片不出设备，截屏只在你主动触发时发生。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            Column {
                IjiFormSectionTitle("授权方式")
                IjiCategoryChips(
                    items = MODE_OPTIONS,
                    selected = MODE_OPTIONS[MODE_VALUES.indexOf(mode).coerceAtLeast(0)],
                    onSelect = {
                        mode = MODE_VALUES[MODE_OPTIONS.indexOf(it)]
                        CapturePreferences.setMode(context, mode)
                    },
                    labelOf = { it },
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            effective == CapturePreferences.MODE_SHIZUKU -> "当前生效：Shizuku（随时可截）"
                            effective == CapturePreferences.MODE_A11Y -> "当前生效：无障碍截屏"
                            else -> "两种授权都不可用，先完成其中一个"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (effective == null) MaterialTheme.colorScheme.error else TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        shizukuState != ShizukuState.READY -> IjiTextAction(
                            text = "Shizuku 授权",
                            compact = true,
                            onClick = { shizukuAction() },
                        )
                        !a11yConnected -> IjiTextAction(
                            text = "开启无障碍",
                            compact = true,
                            onClick = {
                                if (!PaymentAccessibilityStatus.openSettings(context)) {
                                    snackbar.show("无法打开系统无障碍设置")
                                }
                            },
                        )
                        else -> Unit
                    }
                }
            }

            IjiFormCard {
                IjiSwitchRow(
                    title = "音量键快捷识屏",
                    desc = "开启后到系统「无障碍 → 音量键快捷键」选中「音量键识屏记账」；按下音量键即截屏识别，服务不会常驻。",
                    checked = volumeKey,
                    onCheckedChange = { on ->
                        volumeKey = on
                        CapturePreferences.setVolumeKeyEnabled(context, on)
                        if (on && !PaymentAccessibilityStatus.openSettings(context)) {
                            snackbar.show("已开启；但打不开系统设置，请手动到「无障碍 → 音量键快捷键」绑定")
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                )
                IjiFormDivider()
                IjiSwitchRow(
                    title = "悬浮窗确认",
                    desc = if (overlayEnabled) {
                        "识屏结果浮在当前页面上确认，确认即入账。"
                    } else {
                        "不开也能用：结果以通知提醒。点开关去授予「显示在其他应用上层」。"
                    },
                    checked = overlayEnabled,
                    onCheckedChange = {
                        if (BillOverlay.canShow(context)) {
                            overlayEnabled = true
                            snackbar.show("已开启，识屏后会直接弹悬浮窗确认")
                        } else if (!BillOverlay.openPermissionSettings(context)) {
                            snackbar.show("无法打开悬浮窗权限设置")
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }

            Column {
                IjiFormSectionTitle("识别结果样式")
                IjiFormCard {
                    Column(Modifier.padding(16.dp)) {
                        IjiCategoryChips(
                            items = OVERLAY_STYLE_OPTIONS,
                            selected = OVERLAY_STYLE_OPTIONS[
                                OVERLAY_STYLE_VALUES.indexOf(overlayStyle).coerceAtLeast(0),
                            ],
                            onSelect = {
                                overlayStyle = OVERLAY_STYLE_VALUES[OVERLAY_STYLE_OPTIONS.indexOf(it)]
                                CapturePreferences.setOverlayStyle(context, overlayStyle)
                            },
                            labelOf = { it },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when (overlayStyle) {
                                CapturePreferences.OVERLAY_DETAILED ->
                                    "屏幕顶端的详细卡片：类型、金额、账户、分类、日期、标签、备注都能改，保存后自动进下一笔"
                                CapturePreferences.OVERLAY_COMPACT ->
                                    "悬浮卡片只读预览（含账户与日期），点一行可补改备注"
                                else ->
                                    "不弹卡片，以超级岛焦点通知提醒；点通知回应用在识屏导入页核对导入"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            IjiPrimaryButton(
                text = "立即识屏",
                enabled = effective != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = { showConfirm = true },
            )
            if (effective == null) {
                Text(
                    "先在上方完成 Shizuku 授权或开启无障碍服务，「立即识屏」才可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) refresh()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
        }
    }

    IjiDialog(
        show = showConfirm,
        onDismissRequest = { showConfirm = false },
        title = "截屏识别账单",
        summary = "开始后本应用退到后台，3 秒后截取当前屏幕并在本机识别。",
        confirmText = "开始识别",
        onConfirm = {
            showConfirm = false
            val started = when (effective) {
                CapturePreferences.MODE_SHIZUKU ->
                    ScreenCaptureFlow.startWithShizuku(context, ScreenCaptureFlow.DEFAULT_DELAY_MS)
                CapturePreferences.MODE_A11Y ->
                    ScreenCaptureFlow.start(context, ScreenCaptureFlow.DEFAULT_DELAY_MS)
                else -> false
            }
            if (!started) {
                snackbar.show("没能发起截屏：授权可能已失效")
                return@IjiDialog
            }
            context.findActivity()?.moveTaskToBack(true)
        },
    ) {
        Text(
            "请先切到支付宝 / 微信的账单列表页——截到的是那一刻的屏幕，停在本页只会认出本应用。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}
