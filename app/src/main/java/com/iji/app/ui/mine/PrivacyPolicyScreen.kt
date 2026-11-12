package com.iji.app.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.ui.components.IjiPrimaryButton
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.exitApp
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

/**
 * 隐私政策页：正文与首次启动同意页共用 [privacyPolicyItems]，末尾追加同意状态卡。
 * 撤回 = 清零已同意版本后退出，下次冷启动重新征求同意。
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agreed = SettingsRepository.getInt(
        context, SettingsRepository.KEY_PRIVACY_AGREED_VERSION, 0,
    ) >= privacyPolicyVersion
    var showRevokeConfirm by remember { mutableStateOf(false) }

    MineSubPage(title = "隐私政策", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = ijiTopBarContentPadding(),
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
        ) {
            privacyPolicyItems()
            item(key = "privacy_consent_status") {
                ConsentStatusCard(agreed, onRevoke = { showRevokeConfirm = true })
            }
        }
    }

    IjiDialog(
        show = showRevokeConfirm,
        onDismissRequest = { showRevokeConfirm = false },
        title = "撤回隐私政策同意",
        summary = "撤回后将退出应用，下次打开需重新同意《隐私政策》；本机数据不会删除。",
        confirmText = "撤回并退出",
        confirmColor = MaterialTheme.colorScheme.error,
        onConfirm = {
            showRevokeConfirm = false
            scope.launch {
                // 等 DataStore 落盘再退出，避免杀进程丢标记
                SettingsRepository.setIntAwait(
                    context, SettingsRepository.KEY_PRIVACY_AGREED_VERSION, 0,
                )
                context.exitApp()
            }
        },
    )
}

@Composable
private fun ConsentStatusCard(agreed: Boolean, onRevoke: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        cornerRadius = Radius.xxl,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "同意状态",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (agreed) "已同意" else "未同意",
                    fontSize = 14.sp,
                    color = if (agreed) MaterialTheme.colorScheme.error else TextSecondary,
                )
            }
            if (agreed) {
                Spacer(Modifier.height(12.dp))
                IjiPrimaryButton(
                    text = "撤回同意",
                    onClick = onRevoke,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
