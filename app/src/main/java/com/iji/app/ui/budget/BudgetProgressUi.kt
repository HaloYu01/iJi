package com.iji.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.budget.BudgetComponents
import com.iji.app.data.model.BudgetAlertLevel
import com.iji.app.data.model.BudgetProgress
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.formatMoney
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「接近超支」的琥珀色。
 *
 * Miuix 稳定版没有 warning/alert 语义色（官方红色状态角色是 error），
 * 因此中间态用应用自有色，不伪造框架 token。
 */
private val NearLimitColor = Color(0xFFFF9F0A)

/** 按当前级别取进度条颜色（MiuixTheme.colorScheme 是 @Composable，故本函数也需 @Composable） */
@Composable
private fun progressColor(progress: BudgetProgress): Color = when (progress.level) {
    BudgetAlertLevel.OVER_LIMIT -> MiuixTheme.colorScheme.error
    BudgetAlertLevel.NEAR_LIMIT -> NearLimitColor
    null -> MiuixTheme.colorScheme.primary
}

/**
 * 预算进度条：已用比例 + 按级别变色（正常=主色 / 接近超支=琥珀 / 已超支=红）。
 *
 * 纯展示组件，无状态、无依赖，可直接放进首页卡片、预算列表行或详情页。
 */
@Composable
fun BudgetProgressBar(
    progress: BudgetProgress,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    // 超支时 percent 可能 >100，进度条最多铺满，颜色已表达超支状态
    val fraction = (progress.percent / 100f).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(MiuixTheme.colorScheme.secondaryContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(shape)
                .background(progressColor(progress)),
        )
    }
}

/**
 * 预算详情页：通知点击的落地页（路由 `budget_detail/{budgetId}`）。
 *
 * 直接用仓库读取，未引入额外 ViewModel（项目无 Hilt，避免为单个页面再接工厂）。
 */
@Composable
fun BudgetDetailScreen(
    budgetId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember { BudgetComponents.repository() }
    var progress by remember(budgetId) { mutableStateOf<BudgetProgress?>(null) }

    LaunchedEffect(budgetId) {
        val budget = runCatching { repository.get(budgetId) }.getOrNull()
        progress = budget?.let { runCatching { repository.progressOf(it) }.getOrNull() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        IjiTextAction(
            text = "返回",
            onClick = onBack,
            compact = true,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        val p = progress
        if (p == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("预算不存在或已删除", color = MiuixTheme.colorScheme.onBackground)
            }
            return@Column
        }

        val scopeName = if (p.budget.categoryId.isEmpty()) "总预算" else CategoryCatalog.of(p.budget.categoryId).name

        Text(scopeName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(
            "周期 ${p.period.start} ~ ${p.period.end}",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(24.dp))

        BudgetProgressBar(progress = p)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("已用 ${formatMoney(p.spentCents)}", fontSize = 14.sp, color = MiuixTheme.colorScheme.onBackground)
            Text("预算 ${formatMoney(p.amountCents)}", fontSize = 14.sp, color = MiuixTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(16.dp))

        val statusText = when (p.level) {
            BudgetAlertLevel.OVER_LIMIT -> "已超支 ${formatMoney(-p.remainingCents)}"
            BudgetAlertLevel.NEAR_LIMIT -> "已用 ${p.percent}%，接近超支（阈值 ${p.budget.alertThresholdPercent}%）"
            null -> "剩余 ${formatMoney(p.remainingCents)}（${p.percent}%）"
        }
        Text(statusText, fontSize = 14.sp, color = progressColor(p))

        if (p.budget.categoryId.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "分类：${CategoryCatalog.of(p.budget.categoryId).name}",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.width(1.dp))
    }
}
