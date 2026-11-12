package com.iji.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.budget.BudgetCalculator
import com.iji.app.budget.BudgetComponents
import com.iji.app.data.model.Budget
import com.iji.app.data.model.BudgetPeriodType
import com.iji.app.data.model.BudgetProgress
import com.iji.app.data.model.Category
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.IjiIconSize
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import com.iji.app.ui.util.sharedMainViewModel

/**
 * 分类预算管理页（设置 → 记账设置 → 预算功能 → 分类预算）。
 *
 * 之前这里是「暂未开放」的 Toast 占位 —— Repository 的 `save()` 早已支持分类预算，
 * 缺的只是界面与「子分类支出并入父分类」的口径，本页补齐这两点。
 *
 * 口径说明：预算挂在**一级支出分类**上，统计时把其子分类的支出一并计入
 * （用户对「餐饮」设预算，显然包含「餐饮 > 早餐 / 午餐」），与首页总预算的
 * 「EXPENSE 且 excludeFromBudget=0」口径保持一致。
 */
@Composable
fun CategoryBudgetScreen(
    onBack: () -> Unit,
    app: MainViewModel = sharedMainViewModel(),
) {
    val scope = rememberCoroutineScope()
    val repo = remember { BudgetComponents.repository() }
    val budgets by repo.budgets.collectAsStateWithLifecycle(initialValue = emptyList())
    val ledger by app.currentLedger.collectAsStateWithLifecycle()

    // 一级支出分类（含用户自定义）。每次重组重新读取，新增分类后重进本页即可见。
    val categories = CategoryCatalog.expenses.filter { it.parentId == null }

    // 各分类的进度快照：spentCents 要走 DAO，只能在协程里算，结果落到 state 供 UI 读
    var progressMap by remember { mutableStateOf<Map<String, BudgetProgress>>(emptyMap()) }
    LaunchedEffect(budgets, ledger, categories.size) {
        val map = LinkedHashMap<String, BudgetProgress>()
        budgets
            .filter {
                it.categoryId.isNotEmpty() &&
                    it.periodType == BudgetPeriodType.MONTH &&
                    it.enabled
            }
            .forEach { b ->
                val period = BudgetCalculator.periodOf(b)
                val ids = listOf(b.categoryId) +
                    CategoryCatalog.expenses.filter { it.parentId == b.categoryId }.map { it.id }
                val spent = ids.sumOf { id -> repo.spentCents(b.copy(categoryId = id), period) }
                map[b.categoryId] = BudgetProgress(b, period, spent)
            }
        progressMap = map
    }

    var editing by remember { mutableStateOf<Category?>(null) }
    var amountInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶部栏（与预算功能页一致）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MiuixIcons.ChevronBackward,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp),
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "分类预算",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(40.dp))
        }

        Spacer(Modifier.height(16.dp))

        // 合计概览：已设预算的分类额度 / 已用
        val totalBudgetCents = progressMap.values.sumOf { it.amountCents }
        val totalSpentCents = progressMap.values.sumOf { it.spentCents }
        if (progressMap.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding)) {
                Text(
                    "已设 ${progressMap.size} 个分类预算 · 合计 ${formatMoney(totalBudgetCents)}，" +
                        "已用 ${formatMoney(totalSpentCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = LocalDimens.current.screenPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryBudgetRow(
                    category = category,
                    progress = progressMap[category.id],
                    onClick = {
                        amountInput = progressMap[category.id]?.budget?.amountCents
                            ?.let { (it / 100).toString() }
                            .orEmpty()
                        editing = category
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // 设置 / 修改分类预算
    val target = editing
    val existingBudget = target?.let { c ->
        budgets.firstOrNull { it.categoryId == c.id && it.periodType == BudgetPeriodType.MONTH }
    }
    IjiDialog(
        show = target != null,
        onDismissRequest = { editing = null },
        title = target?.let { "设置「${it.name}」预算" },
        summary = "按月统计，含该分类下的子分类支出",
        confirmText = "保存",
        onConfirm = {
            val cat = editing ?: return@IjiDialog
            val cents = (amountInput.toDoubleOrNull() ?: 0.0).let { (it * 100).toLong() }
            if (cents <= 0L) return@IjiDialog
            // 复用同一行：避免反复修改产生重复预算（与总预算 findTotalMonthlyBudget 同理）
            val base = existingBudget ?: Budget(
                categoryId = cat.id,
                periodType = BudgetPeriodType.MONTH,
            )
            val saved = base.copy(
                amountCents = cents,
                ledgerId = ledger,
                enabled = true,
            )
            scope.launch { BudgetComponents.repository().save(saved) }
            editing = null
        },
    ) {
        Column {
            top.yukonga.miuix.kmp.basic.TextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = "预算金额（元）",
                useLabelAsPlaceholder = true,
            )
            if (existingBudget != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "删除该分类预算",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppleRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val id = existingBudget.id
                            scope.launch { BudgetComponents.repository().remove(id) }
                            editing = null
                        }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

/** 单个分类预算行：图标 + 名称 + 进度 + 箭头 */
@Composable
private fun CategoryBudgetRow(
    category: Category,
    progress: BudgetProgress?,
    onClick: () -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconImage(
                    name = category.name,
                    color = Color(category.color),
                    size = IjiIconSize.ListRow,
                    categoryId = category.id,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (progress == null) {
                            "未设置预算"
                        } else {
                            "已用 ${formatMoney(progress.spentCents)} / ${formatMoney(progress.amountCents)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    if (progress == null) "设置" else "${progress.percent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (progress == null) MiuixPrimary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    MiuixIcons.ChevronForward,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                BudgetProgressBar(progress = progress)
            }
        }
    }
}
