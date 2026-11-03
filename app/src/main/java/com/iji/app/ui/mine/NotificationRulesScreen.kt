package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iji.app.accessibility.PaymentAccessibilityStatus
import com.iji.app.accessibility.PaymentNotificationFilter
import com.iji.app.accessibility.PaymentNotificationParser
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiFormDivider
import com.iji.app.ui.components.IjiFormSectionTitle
import com.iji.app.ui.components.IjiPrimaryButton
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 通知识别规则：无障碍开关 + 关键词闸门 + 支持来源。
 *
 * 这三件事原先在自动记账页是「一张状态卡 + 两个弹窗」：30 个词的白名单挤在
 * 单行输入框里，来源清单也塞进弹窗，改一个词要滚半天。改成整页后，
 * 输入框和清单都有足够高度，且页面里没有任何二级弹窗。
 */
@Composable
fun NotificationRulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current

    var a11yEnabled by remember { mutableStateOf(PaymentAccessibilityStatus.isEnabled(context)) }
    var whitelist by remember {
        mutableStateOf(
            SettingsRepository.getString(
                context,
                PaymentNotificationFilter.KEY_WHITELIST,
                PaymentNotificationFilter.DEFAULT_WHITELIST,
            ),
        )
    }
    var blacklist by remember {
        mutableStateOf(SettingsRepository.getString(context, PaymentNotificationFilter.KEY_BLACKLIST, ""))
    }
    val saved = remember { whitelist to blacklist }
    val dirty = (whitelist to blacklist) != saved

    val sources = remember { PaymentNotificationParser.supportedSources.values.toList() }

    MineSubPage(title = "通知识别规则", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IjiFormCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("支付通知自动记账", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (a11yEnabled) {
                                "已开启：只读支付类通知的文字，本机解析后提示你确认。"
                            } else {
                                "未开启：需要在系统「无障碍」里授权，应用才能读到通知文字。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    IjiTextAction(
                        text = if (a11yEnabled) "去设置" else "去开启",
                        compact = true,
                        onClick = {
                            if (!PaymentAccessibilityStatus.openSettings(context)) {
                                snackbar.show("无法打开系统无障碍设置")
                            }
                        },
                    )
                }
            }

            Column {
                IjiFormSectionTitle("过滤关键词")
                IjiFormCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "命中白名单任一词、且不含黑名单词的通知才会去识别；白名单留空 = 不过滤。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("白名单（逗号 / 换行分隔）", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        BoxBackground {
                            TextField(
                                whitelist,
                                { whitelist = it },
                                Modifier.fillMaxWidth().height(120.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("黑名单（命中即忽略，优先级更高）", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        BoxBackground {
                            TextField(
                                blacklist,
                                { blacklist = it },
                                Modifier.fillMaxWidth().height(72.dp),
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IjiPrimaryButton(
                    text = "保存",
                    enabled = dirty,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        SettingsRepository.setString(
                            context,
                            PaymentNotificationFilter.KEY_WHITELIST,
                            whitelist,
                        )
                        SettingsRepository.setString(
                            context,
                            PaymentNotificationFilter.KEY_BLACKLIST,
                            blacklist,
                        )
                        snackbar.show(
                            "已保存：白名单 ${PaymentNotificationFilter.wordCount(whitelist)} 词，" +
                                "黑名单 ${PaymentNotificationFilter.wordCount(blacklist)} 词",
                        )
                    },
                )
                Spacer(Modifier.width(12.dp))
                IjiTextAction(
                    text = "恢复默认",
                    onClick = {
                        whitelist = PaymentNotificationFilter.DEFAULT_WHITELIST
                        blacklist = ""
                        SettingsRepository.setString(
                            context,
                            PaymentNotificationFilter.KEY_WHITELIST,
                            PaymentNotificationFilter.DEFAULT_WHITELIST,
                        )
                        SettingsRepository.setString(context, PaymentNotificationFilter.KEY_BLACKLIST, "")
                        snackbar.show("已恢复默认词表")
                    },
                )
            }

            Column {
                IjiFormSectionTitle("已支持识别的来源（${sources.size}）")
                IjiFormCard {
                    sources.forEachIndexed { index, name ->
                        if (index > 0) IjiFormDivider()
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    IjiFormDivider()
                    Text(
                        "以上为带专属模板的来源；其余通知会走通用兜底（识别金额与收支方向）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    a11yEnabled = PaymentAccessibilityStatus.isEnabled(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
}

@Composable
private fun BoxBackground(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sheet))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    ) { content() }
}
