package com.iji.app.ui.mine
import com.iji.app.ui.components.LocalSnackbarController

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iji.app.lock.AppLockManager
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

@Composable
fun PasswordProtectScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    var enabled by remember { mutableStateOf(AppLockManager.isEnabled(context)) }
    // 首次开启：还没有旧密码可验证，直接设置新 PIN
    var showPinDialog by remember { mutableStateOf(false) }
    // 修改 / 关闭密码前的身份验证（PIN 或指纹），通过后各自进入后续流程
    var showChangeVerify by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var showCloseVerify by remember { mutableStateOf(false) }
    var fingerprintEnabled by remember { mutableStateOf(AppLockManager.isFingerprintEnabled(context)) }
    val fingerprintAvailable = remember { AppLockManager.isFingerprintAvailable(context) }
    // 验证弹窗里的指纹入口：设备支持且用户开了指纹解锁才出现
    val fingerprintUsable = fingerprintAvailable && fingerprintEnabled

    // P0：手搓顶栏 + 不可滚动的 Column → 统一改用 MineSubPage（顶栏一套 + 内容可滚动）
    MineSubPage(title = "密码保护", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "开启密码",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = {
                        if (it) {
                            showPinDialog = true
                        } else {
                            showCloseVerify = true
                        }
                    })
                }
            }

            // 修改PIN密码卡片：先验证身份（密码或指纹），通过后设置新 PIN
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.4f),
                cornerRadius = Radius.xxl,
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled) { showChangeVerify = true }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "修改PIN密码",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        MiuixIcons.ChevronForward,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // 指纹密码卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled && fingerprintAvailable) 1f else 0.4f),
                cornerRadius = Radius.xxl,
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "指纹密码",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (!fingerprintAvailable) {
                        Text("不支持", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(end = 8.dp))
                    }
                    Switch(
                        checked = fingerprintEnabled,
                        onCheckedChange = {
                            if (enabled && fingerprintAvailable) {
                                fingerprintEnabled = it
                                AppLockManager.setFingerprintEnabled(context, it)
                                snackbar.show(if (it) "指纹解锁已开启" else "指纹解锁已关闭")
                            }
                        },
                        enabled = enabled && fingerprintAvailable,
                    )
                }
            }

            // 注意事项（放进滚动列：此前排在滚动列外面，被 fillMaxSize 的滚动列
            // 挤出屏幕，用户永远看不到这些说明）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    "注意事项：",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "1. 修改或关闭密码前需验证身份：当前密码或指纹",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "2. 关闭密码后再次开启需重新设置PIN",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "3. 设置的PIN为4位",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }

    // 首次开启：直接设置新 PIN（无旧密码可验证）
    PinSetupDialog(
        show = showPinDialog,
        fieldLabel = "设置密码（4位数字）",
        onDismiss = { showPinDialog = false },
        onConfirm = { pinCode ->
            // setPin 会校验位数：写成功了才提示，别再出现"其实没设上却说已设置"
            if (AppLockManager.setPin(context, pinCode)) {
                enabled = true
                snackbar.show("PIN已设置")
                showPinDialog = false
            } else {
                snackbar.show("PIN 需为 ${AppLockManager.PIN_LENGTH} 位数字")
            }
        },
    )

    // 修改 PIN：先验证身份（当前密码或指纹）
    PinVerifyDialog(
        show = showChangeVerify,
        title = "修改密码",
        summary = "输入当前密码或使用指纹，验证通过后设置新密码",
        fingerprintUsable = fingerprintUsable,
        onDismiss = { showChangeVerify = false },
        onVerified = {
            showChangeVerify = false
            showChangePin = true
        },
    )

    // 修改流程第二步：设置新 PIN
    PinSetupDialog(
        show = showChangePin,
        title = "设置新密码",
        fieldLabel = "新密码（4位数字）",
        onDismiss = { showChangePin = false },
        onConfirm = { pinCode ->
            if (AppLockManager.setPin(context, pinCode)) {
                enabled = true
                snackbar.show("PIN已修改")
                showChangePin = false
            } else {
                snackbar.show("PIN 需为 ${AppLockManager.PIN_LENGTH} 位数字")
            }
        },
    )

    // 关闭密码：验证身份（当前密码或指纹）后关闭
    PinVerifyDialog(
        show = showCloseVerify,
        title = "关闭密码",
        summary = "输入当前密码或使用指纹，验证通过后关闭密码保护",
        fingerprintUsable = fingerprintUsable,
        onDismiss = { showCloseVerify = false },
        onVerified = {
            AppLockManager.disable(context)
            enabled = false
            snackbar.show("密码保护已关闭")
            showCloseVerify = false
        },
    )
}

@Composable
private fun PinSetupDialog(
    show: Boolean,
    title: String = "密码设置",
    fieldLabel: String = "请输入4位数字密码",
    buttonText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // 每次打开清空输入
    var pin by remember(show) { mutableStateOf("") }

    val pinFocus = remember { FocusRequester() }
    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = title,
        confirmText = buttonText,
        autoFocusRequester = pinFocus,
        onConfirm = { if (pin.length == 4) onConfirm(pin) },
    ) {
        top.yukonga.miuix.kmp.basic.TextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pin = it },
            modifier = Modifier.fillMaxWidth().focusRequester(pinFocus),
            label = fieldLabel,
            useLabelAsPlaceholder = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
            ),
        )
    }
}

/**
 * 身份验证弹窗：输入当前 PIN，或点「使用指纹验证」走系统指纹认证。
 *
 * 修改 / 关闭密码共用。此前验证只支持 PIN，开着指纹解锁的用户
 * 改个密码也得翻出 4 位数字；现在两个动作都多一条指纹出路。
 *
 * 弹窗自己负责触发指纹并**就地显示错误**：snackbar 渲染在 Scaffold 层，
 * 会被上层弹窗盖住（只露出底下一条边），弹窗期间的提示必须画在弹窗内部。
 */
@Composable
private fun PinVerifyDialog(
    show: Boolean,
    title: String,
    summary: String,
    fingerprintUsable: Boolean,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    // 每次打开清空输入与错误
    var pin by remember(show) { mutableStateOf("") }
    var error by remember(show) { mutableStateOf("") }
    val context = LocalContext.current
    val pinFocus = remember { FocusRequester() }

    fun authenticateWithFingerprint() {
        val activity = context as? android.app.Activity
        if (activity == null) {
            error = "指纹验证不可用，请输入当前密码"
            return
        }
        AppLockManager.showFingerprintPrompt(
            activity,
            title = "身份验证",
            subtitle = "验证通过后$title",
            onSuccess = onVerified,
            onError = { msg -> error = msg },
        )
    }

    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = title,
        summary = summary,
        confirmText = "验证",
        confirmEnabled = pin.length == AppLockManager.PIN_LENGTH,
        autoFocusRequester = pinFocus,
        onConfirm = {
            if (pin == AppLockManager.getPin(context)) {
                onVerified()
            } else {
                error = "当前密码不正确，请重试"
            }
        },
    ) {
        top.yukonga.miuix.kmp.basic.TextField(
            value = pin,
            onValueChange = {
                if (it.length <= AppLockManager.PIN_LENGTH && it.all { ch -> ch.isDigit() }) {
                    pin = it
                    if (error.isNotEmpty()) error = ""
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(pinFocus),
            label = "当前密码",
            useLabelAsPlaceholder = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
            ),
        )
        if (error.isNotEmpty()) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = AppleRed,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (fingerprintUsable) {
            Text(
                "使用指纹验证",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MiuixPrimary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clickable { authenticateWithFingerprint() },
            )
        }
    }
}