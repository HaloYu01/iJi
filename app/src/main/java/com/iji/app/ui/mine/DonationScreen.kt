package com.iji.app.ui.mine

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iji.app.R
import com.iji.app.ui.components.IjiSettingValue
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.IjiSettingsRow
import com.iji.app.ui.components.IjiSettingsSection
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.ExpenseAmount
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

/**
 * 捐赠支出：赞赏码 + 捐赠名单 + 项目支出公示。
 *
 * 名单与支出是 DonationData.kt 里手工填入的静态数据，不从账单或网络读取，
 * 因此页面只做展示，没有增删改入口。赞赏码裁掉了原图的感谢文案与装饰留白，
 * 只保留二维码本体（无损 WebP），200dp 显示，减少页面占用且保证可扫。
 */
@Composable
fun DonationScreen(onBack: () -> Unit) {
    val totalIn = donationRecords.sumOf { it.amountCents }
    val totalOut = donationExpenses.sumOf { it.amountCents }

    MineSubPage(title = "捐赠支出", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                top = ijiTopBarContentPadding(),
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap),
        ) {
            item { QrCard() }
            item { SummaryCard(totalIn, totalOut) }
            item {
                IjiSettingsSection("捐赠名单（${donationRecords.size}）") {
                    if (donationRecords.isEmpty()) {
                        EmptyHint("名单由维护者手工填入，暂无记录")
                    } else {
                        donationRecords.forEachIndexed { index, record ->
                            IjiSettingsRow(
                                title = record.name,
                                subtitle = listOfNotNull(record.date, record.note)
                                    .joinToString(" · ")
                                    .takeIf { it.isNotBlank() },
                            ) {
                                IjiSettingValue(formatMoney(record.amountCents), chevron = false)
                            }
                            if (index < donationRecords.lastIndex) IjiSettingsDivider()
                        }
                    }
                }
            }
            item {
                IjiSettingsSection("项目支出（${donationExpenses.size}）") {
                    if (donationExpenses.isEmpty()) {
                        EmptyHint("支出由维护者手工填入，暂无记录")
                    } else {
                        donationExpenses.forEachIndexed { index, expense ->
                            IjiSettingsRow(
                                title = expense.label,
                                subtitle = listOfNotNull(expense.date, expense.note)
                                    .joinToString(" · ")
                                    .takeIf { it.isNotBlank() },
                            ) {
                                IjiSettingValue(formatMoney(expense.amountCents), chevron = false)
                            }
                            if (index < donationExpenses.lastIndex) IjiSettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.donate_qr),
                contentDescription = "赞赏码",
                modifier = Modifier.size(200.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "感谢您的支持",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "截图后用微信「扫一扫」识别",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun SummaryCard(totalIn: Long, totalOut: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("累计赞赏", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(
                        formatMoney(totalIn),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IncomeGreen,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("累计支出", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(
                        formatMoney(totalOut),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseAmount,
                    )
                }
            }
            Text(
                "结余 ${formatMoney(totalIn - totalOut)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    )
}
