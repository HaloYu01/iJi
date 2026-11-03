package com.iji.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iji.app.ui.theme.AppleSecondaryBackground
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.TextSecondary

/**
 * 首次引导 / 新手教程。
 *
 * **为什么现在需要它**：D2 去掉了"空库灌入模拟流水"的假数据，新用户首启看到的就是
 * 一本空账本——不给指引等于让用户对着空界面猜。所以这里补一个真实引导，
 * 每一步都对应**确实存在的功能**（不是"敬请期待"式的占位入口）。
 *
 * 出现时机：
 * - 首次启动（标记落在 DataStore，看过就不再弹）；
 * - 「我的 → 新手教程」手动再看一遍。
 */
@Composable
fun OnboardingDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    /** 记第一笔：直接打开记账面板 */
    onAddFirst: () -> Unit,
    /** 导入已有账单 */
    onImport: () -> Unit,
    /** 设置备份 */
    onBackup: () -> Unit,
    /** 「开始使用」：写入"已看过引导" */
    onFinish: () -> Unit,
) {
    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = "欢迎使用 i记",
        summary = "三步就能用起来。随时可以在「我的 → 新手教程」再看一遍。",
        confirmText = "开始使用",
        onConfirm = onFinish,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GuideStep(
                index = "1",
                title = "记第一笔",
                desc = "点底部「＋」选分类、填金额就行；也能一句话 AI 记账或从截屏批量导入。",
                actionText = "去记一笔",
                onAction = onAddFirst,
            )
            GuideStep(
                index = "2",
                title = "导入已有账单",
                desc = "微信 / 支付宝导出的 CSV，或者账单页截屏，一次能进几十笔。",
                actionText = "去导入",
                onAction = onImport,
            )
            GuideStep(
                index = "3",
                title = "开个备份",
                desc = "账本只存在这台手机上。开了备份，换机时分类、标签、预算才跟着走。",
                actionText = "去设置",
                onAction = onBackup,
            )
        }
    }
}

@Composable
private fun GuideStep(
    index: String,
    title: String,
    desc: String,
    actionText: String,
    onAction: () -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(AppleSecondaryBackground),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 序号：状态不靠颜色传达，直接写数字
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .padding(0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        index,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MiuixPrimary,
                    )
                }
                Text(
                    title,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                desc,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            IjiTextAction(
                text = actionText,
                onClick = onAction,
                compact = true,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
