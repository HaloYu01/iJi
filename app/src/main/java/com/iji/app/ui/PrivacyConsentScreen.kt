package com.iji.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.ui.components.IjiDialogActions
import com.iji.app.ui.mine.privacyPolicyItems
import com.iji.app.ui.mine.privacyPolicyVersion
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * 首次启动（或隐私政策版本升级后）的**全屏同意页**。
 *
 * 挂在 IjiApp() 最顶层并提前 return：未同意时 NavHost / Scaffold / 底栏都不存在，
 * 返回键在这里被接成「不同意并退出」，任何页面内操作都绕不过这道门禁。
 * 正文与「关于 → 隐私政策」共用 [privacyPolicyItems]，保证同意前读到的就是完整政策。
 *
 * 「同意并继续」会**同步等 DataStore 落盘**再放行：setXxx 系列是内存立即生效 +
 * 异步落盘，用户在写入完成前杀进程会丢标记、下次重复弹窗。
 */
@Composable
fun PrivacyConsentScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAgreeing by remember { mutableStateOf(false) }

    // 返回键等同「不同意并退出」，不能借返回键绕过门禁
    BackHandler(enabled = true) { onDecline() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // MainActivity 开了 edge-to-edge，拦截页不走 NavHost / MineSubPage，
            // 必须自己消化状态栏与导航栏安全区，否则内容会顶进状态栏
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "欢迎使用 i记",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "首次使用前，请阅读并同意下面的《隐私政策》。不同意将无法使用本应用，可选择退出。",
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = TextSecondary,
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            privacyPolicyItems()
        }

        IjiDialogActions(
            onConfirm = {
                if (!isAgreeing) {
                    isAgreeing = true
                    scope.launch {
                        SettingsRepository.setIntAwait(
                            context,
                            SettingsRepository.KEY_PRIVACY_AGREED_VERSION,
                            privacyPolicyVersion,
                        )
                        // 同一次强一致写入里推进引导状态：同意隐私 → 待引导。
                        // 已经是「完成」的老用户（政策版本升级后重新同意）保持原值，不重复弹引导。
                        if (SettingsRepository.getInt(
                                context,
                                SettingsRepository.KEY_ONBOARDING_STATE,
                                SettingsRepository.ONBOARDING_FRESH,
                            ) == SettingsRepository.ONBOARDING_FRESH
                        ) {
                            SettingsRepository.setIntAwait(
                                context,
                                SettingsRepository.KEY_ONBOARDING_STATE,
                                SettingsRepository.ONBOARDING_PENDING,
                            )
                        }
                        onAgree()
                    }
                }
            },
            onDismiss = onDecline,
            dismissText = "不同意并退出",
            confirmText = "同意并继续",
            confirmEnabled = !isAgreeing,
            topPadding = 8,
        )
        Spacer(Modifier.height(12.dp))
    }
}
