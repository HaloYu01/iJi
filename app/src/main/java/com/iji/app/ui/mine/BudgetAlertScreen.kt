package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.budget.BudgetComponents
import com.iji.app.data.model.Budget
import com.iji.app.data.model.BudgetAlertLevel
import com.iji.app.data.model.BudgetAlertRecord
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleOrange
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.iji.app.ui.util.sharedMainViewModel

private val ALERT_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

/**
 * 预算提醒记录页（预算功能 → 提醒记录）。
 *
 * `budget_alerts` 表一直在写（每次真正发提醒前插一条用于去重），
 * 但**没有任何读取方**：用户被通知过几次、哪一期超的支，App 里无处可查。本页补的就是这个出口。
 *
 * 记录语义：同一预算 / 同一周期 / 同一级别靠唯一索引只留一条，
 * 所以列表里的一条 = 一次**真正发出过**的提醒（不是每次越阈值的流水）。
 */
@Composable
fun BudgetAlertScreen(
    onBack: () -> Unit,
    app: MainViewModel = sharedMainViewModel(),
) {
    val snackbar = LocalSnackbarController.current
    val repo = remember { BudgetComponents.repository() }
    val alerts by repo.alerts.collectAsStateWithLifecycle(initialValue = emptyList())
    val budgets by repo.budgets.collectAsStateWithLifecycle(initialValue = emptyList())

    var showClearConfirm by remember { mutableStateOf(false) }

    MineSubPage(title = "提醒记录", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                top = ijiTopBarContentPadding(),
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "共 ${alerts.size} 条",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "同一预算每期每级别只记一次",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    if (alerts.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.xl))
                                .background(MiuixPrimary.copy(alpha = 0.14f))
                                .clickable { showClearConfirm = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "清空",
                                style = MaterialTheme.typography.bodySmall,
                                color = MiuixPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            if (alerts.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "还没有提醒记录\n预算达到阈值时会自动记一条",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }

            items(alerts, key = { it.id }) { record ->
                AlertRow(record = record, budget = budgets.find { it.id == record.budgetId })
            }
        }
    }

    IjiDialog(
        show = showClearConfirm,
        onDismissRequest = { showClearConfirm = false },
        title = "清空提醒记录",
        confirmText = "清空",
        onConfirm = {
            showClearConfirm = false
            app.clearBudgetAlerts { ok ->
                snackbar.show(if (ok) "已清空提醒记录" else "清空失败")
            }
        },
    ) {
        Text(
            "删除全部 ${alerts.size} 条记录？这只影响历史列表，预算与提醒设置不受影响。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun AlertRow(record: BudgetAlertRecord, budget: Budget?) {
    val over = record.level == BudgetAlertLevel.OVER_LIMIT
    val levelColor = if (over) AppleRed else AppleOrange
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = Radius.lg) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(levelColor.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (over) "已超支" else "接近超支",
                            style = MaterialTheme.typography.bodySmall,
                            color = levelColor,
                        )
                    }
                    Spacer(Modifier.height(0.dp))
                    Text(
                        "  ${budgetLabel(budget)} · ${periodLabel(record.periodKey)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "触发时已花 ${formatMoney(record.spentCents)} · 进度 ${record.progressPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Text(
                ALERT_TIME_FORMAT.format(
                    LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(record.triggeredAtMillis),
                        ZoneId.systemDefault(),
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

/** 预算名：全分类总预算叫「全部分类」，分类预算取分类名；预算已删除时兜底 */
private fun budgetLabel(budget: Budget?): String {
    if (budget == null) return "已删除的预算"
    if (budget.categoryId.isEmpty()) return "全部分类"
    return runCatching { CategoryCatalog.of(budget.categoryId).name }
        .getOrDefault(budget.categoryId)
}

/** 周期 key → 人话：2026-10 → 2026年10月；2026-W42 → 2026年第42周；custom:… → 自定义区间 */
private fun periodLabel(key: String): String {
    val month = Regex("^(\\d{4})-(\\d{2})$").find(key)
    if (month != null) return "${month.groupValues[1]}年${month.groupValues[2].toInt()}月"
    val week = Regex("^(\\d{4})-W(\\d{2})$").find(key)
    if (week != null) return "${week.groupValues[1]}年第${week.groupValues[2].toInt()}周"
    val year = Regex("^(\\d{4})$").find(key)
    if (year != null) return "${year.groupValues[1]}年"
    return "自定义区间"
}
