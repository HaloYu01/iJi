package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iji.app.accessibility.AutoBookkeepingPrefs
import com.iji.app.accessibility.CapturePreferences
import com.iji.app.accessibility.PaymentAccessibilityStatus
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/**
 * 自动记账：只放「后台自动 / 系统授权」这一层。
 *
 * 重整合并后的边界很明确：
 * - **识屏记账**（Shizuku / 无障碍 / 音量键 / 悬浮窗）→ [ScreenCaptureSettingsScreen] 一页收口；
 * - **支付通知**（无障碍状态 / 过滤词 / 支持来源）→ [NotificationRulesScreen] 一页收口；
 * - AI 配置、图片识别、提示词都在「AI 记账」那边，不再从这里绕一圈。
 *
 * 原来这里是 8 张并列卡片、两个弹窗：同一件事（识屏）能点出三种入口，
 * 白名单 30 个词挤在单行输入框里。合并后本页只剩两张卡，点进去才是完整设置。
 */
@Composable
fun AutoBookkeepingScreen(
    onBack: () -> Unit,
    onNavigateScreenCapture: () -> Unit,
    onNavigateNotificationRules: () -> Unit,
    onNavigatePrefs: () -> Unit,
) {
    val context = LocalContext.current

    var a11yEnabled by remember { mutableStateOf(PaymentAccessibilityStatus.isEnabled(context)) }
    var captureMode by remember { mutableStateOf(CapturePreferences.resolve(context)) }
    var ledgerPref by remember { mutableStateOf(AutoBookkeepingPrefs.ledger(context)) }

    fun refresh() {
        a11yEnabled = PaymentAccessibilityStatus.isEnabled(context)
        captureMode = CapturePreferences.resolve(context)
        ledgerPref = AutoBookkeepingPrefs.ledger(context)
    }

    MineSubPage(title = "自动记账", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IjiFormCard {
                Text(
                    "这里负责「自动采集」：截屏识屏与支付通知。AI 识别、提示词与模型配置在「AI 记账」页。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            // 识屏：授权方式只在这一个入口里选，页面上不再并排摆 Shizuku / 无障碍 / 截屏导入
            EntryCard(
                title = "识屏记账",
                subtitle = when (captureMode) {
                    CapturePreferences.MODE_SHIZUKU ->
                        "Shizuku 已授权：截当前屏幕 → 本机 OCR → 多笔预览。可开音量键快捷。"
                    CapturePreferences.MODE_A11Y ->
                        "无障碍截屏可用：截当前屏幕 → 本机 OCR → 多笔预览。可开音量键快捷。"
                    else -> "尚未授权：进入后选择 Shizuku 或无障碍截屏，并设置音量键快捷与悬浮窗确认。"
                },
                status = when (captureMode) {
                    CapturePreferences.MODE_SHIZUKU -> "Shizuku"
                    CapturePreferences.MODE_A11Y -> "无障碍"
                    else -> "未授权"
                },
                active = captureMode != null,
                onClick = onNavigateScreenCapture,
            )

            // 支付通知：状态 + 规则（过滤词、支持来源）都在子页里
            EntryCard(
                title = "支付通知记账",
                subtitle = "读取支付通知、在本机解析金额，命中后提示你确认；可设置过滤关键词与查看支持的来源。",
                status = if (a11yEnabled) "已开启" else "未开启",
                active = a11yEnabled,
                onClick = onNavigateNotificationRules,
            )

            // 记账偏好：落账账本 / 备注格式 / 去重窗口（对照 AutoAccounting 记账设置里能落地的那几项）
            EntryCard(
                title = "记账偏好",
                subtitle = "落账账本、备注格式（{商户} / {来源}）、重复通知合并窗口。",
                status = ledgerPref.ifBlank { "跟随当前账本" },
                active = ledgerPref.isNotBlank(),
                onClick = onNavigatePrefs,
            )

            Text(
                "「截屏批量导入」在「数据与备份 → 导入账单」里：它是一次性数据导入，不属于自动采集。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

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
}

/**
 * 页面统一的入口卡：标题 + 副文案 + 右侧状态/箭头。
 *
 * 识屏、通知两张卡形态一致，抽出来免得又各写一遍 padding / 圆角 / 状态色。
 */
@Composable
private fun EntryCard(
    title: String,
    subtitle: String,
    status: String? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xxl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (status != null) {
                    Spacer(Modifier.size(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (active) IncomeGreen else TextSecondary.copy(alpha = 0.5f)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) IncomeGreen else TextSecondary,
                        )
                    }
                }
            }
            ChevronForwardIcon()
        }
    }
}

@Composable
private fun ChevronForwardIcon() {
    androidx.compose.material3.Icon(
        imageVector = MiuixIcons.ChevronForward,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = TextSecondary.copy(alpha = 0.6f),
    )
}


