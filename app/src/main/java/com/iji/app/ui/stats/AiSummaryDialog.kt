package com.iji.app.ui.stats

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iji.app.ai.AiConfig
import com.iji.app.ai.AiSummaryService
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.StateSize
import com.iji.app.ui.components.StateView
import com.iji.app.ui.theme.TextSecondary
import java.time.temporal.ChronoUnit

/**
 * 统计页「AI 小结」弹窗：按**当期汇总**生成一段点评。
 *
 * 三条边界，与全项目「去桩 / 不偷跑」的口径一致：
 * 1. **不自动联网**：只有用户点了「AI 小结」才发请求；
 * 2. **只发聚合数据**：周期、收支合计、笔数、分类前三 —— 不发单笔流水、不发备注里的商户名；
 * 3. **说清失败原因**：没配 Key 和网络失败给不同文案，不显示空卡片。
 *
 * 结果按「维度 + 周期」缓存在弹窗状态里：关掉再打开同一个月不重复计费，换周期才会重新生成。
 */
@Composable
internal fun AiSummaryDialog(
    show: Boolean,
    dimensionLabel: String,
    periodTitle: String,
    period: PeriodStats,
    /** 预算参照（有效额度 / 已用，分）：仅「月维度 + 浏览当月 + 存在预算」时由调用方给出 */
    budgetCents: Long? = null,
    budgetSpentCents: Long? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val key = "$dimensionLabel|$periodTitle"

    var text by remember(key) { mutableStateOf<String?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    var loading by remember(key) { mutableStateOf(false) }

    val empty = period.billCount == 0

    LaunchedEffect(show, attempt, key) {
        if (!show || text != null || empty) return@LaunchedEffect
        loading = true
        error = null
        val result = AiSummaryService.summarize(
            context,
            AiSummaryService.Input(
                dimension = dimensionLabel,
                period = periodTitle,
                expenseCents = period.expenseCents,
                incomeCents = period.incomeCents,
                billCount = period.billCount,
                // 支出笔数与区间天数：笔均 / 日均的分母，payload 里预计算
                expenseCount = period.expenseCount,
                daysInPeriod = period.range
                    ?.let { ChronoUnit.DAYS.between(it.start, it.end).toInt() + 1 }
                    ?: 0,
                prevExpenseCents = period.prevExpenseCents,
                prevIncomeCents = period.prevIncomeCents,
                budgetCents = budgetCents,
                budgetSpentCents = budgetSpentCents,
                // 一级分类归并后的前三名（二级分类太多，模型看不过来也没必要发那么多）
                topCategories = period.byParentCategory
                    .take(3)
                    .map { AiSummaryService.CategoryLine(CategoryCatalog.of(it.categoryId).name, it.totalCents) },
            ),
        )
        loading = false
        if (result == null) {
            // 三种失败三种话：总开关关了 ≠ 没 Key ≠ 网络失败，文案必须如实区分
            error = when {
                !AiConfig.isEnabled(context) ->
                    "「AI 识别」总开关已关闭，到「AI 记账」页打开后再回来生成。"
                AiConfig.getApiKey(context).isBlank() ->
                    "还没有可用的 API Key。先到「AI 记账 → 配置」填上再回来。"
                else ->
                    "生成失败：检查网络或模型配置后重试。"
            }
        } else {
            text = result
        }
    }

    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = "AI 小结 · $periodTitle",
        summary = "只发送本期汇总与预计算指标，不发送单笔流水。",
        dismissText = "关闭",
        confirmText = if (text == null) "重试" else "重新生成",
        confirmEnabled = !loading && !empty,
        onConfirm = {
            text = null
            error = null
            attempt++
        },
    ) {
        when {
            empty -> Text(
                "本期还没有记账，先记几笔再来看看。",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                StateView(state = ContentState.Loading("正在生成"), size = StateSize.Inline)
                Spacer(Modifier.width(8.dp))
                Text("大概需要几秒", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }

            error != null -> Text(
                error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )

            text != null -> Text(
                text!!,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
