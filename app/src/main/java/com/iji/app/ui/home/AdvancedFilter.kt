package com.iji.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iji.app.data.model.Category
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** 搜索范围：只在当月找 / 指定日期区间 / 全部时间（跨月，仍限于当前账本） */
enum class SearchScope { MONTH, RANGE, ALL }

/**
 * 高级筛选条件。
 *
 * 背景：原来首页搜索只在 `monthSummaries`（**当月**）这一层里过滤，
 * 于是"搜去年那笔房租"永远搜不到；也没有金额区间 / 日期区间 / 分类这类组合条件。
 *
 * 金额与日期都保留**输入原文**（[amountMinYuan] / [fromText]），解析失败即视为"不限"——
 * 宁可少筛，也不要因为用户半路上的输入把结果筛成空。
 * [fromDate] / [toDate] 是应用时解析好的结果。
 */
data class SearchFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val scope: SearchScope = SearchScope.MONTH,
    val amountMinYuan: String = "",
    val amountMaxYuan: String = "",
    val fromText: String = "",
    val toText: String = "",
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    /** 空 = 不限分类（分类 id 是 String） */
    val categoryIds: Set<String> = emptySet(),
) {
    val minCents: Long? get() = amountMinYuan.trim().toDoubleOrNull()?.let { (it * 100).toLong() }
    val maxCents: Long? get() = amountMaxYuan.trim().toDoubleOrNull()?.let { (it * 100).toLong() }

    /** 是否有任何条件生效（决定要不要显示"筛选中"提示与清除入口） */
    val isActive: Boolean
        get() = query.isNotBlank() || type != null || scope != SearchScope.MONTH ||
            minCents != null || maxCents != null || fromDate != null || toDate != null ||
            categoryIds.isNotEmpty()

    /** 一句话说清当前筛了什么（常驻在列表上方，免得"列表看着不对却不知道为什么"） */
    fun summary(): String {
        val parts = mutableListOf<String>()
        when (scope) {
            SearchScope.MONTH -> Unit
            SearchScope.ALL -> parts += "全部时间"
            SearchScope.RANGE -> {
                val from = fromDate
                val to = toDate
                when {
                    from != null && to != null -> parts += "$from ~ $to"
                    from != null -> parts += "$from 起"
                    to != null -> parts += "截至 $to"
                    else -> parts += "自定义区间"
                }
            }
        }
        type?.let { parts += if (it == TransactionType.INCOME) "只看收入" else "只看支出" }
        val min = minCents
        val max = maxCents
        if (min != null && max != null && min <= max) parts += "金额 ${min / 100.0} ~ ${max / 100.0} 元"
        else if (min != null) parts += "金额 ≥ ${min / 100.0} 元"
        else if (max != null) parts += "金额 ≤ ${max / 100.0} 元"
        if (categoryIds.isNotEmpty()) parts += "分类 ${categoryIds.size} 项"
        // 关键词不进摘要：它就显示在搜索框里，重复一遍只是噪音
        return parts.joinToString(" · ")
    }
}

/** 单笔账单是否命中筛选条件（不含"报销列 / 未对账"两个额外开关，由调用方叠加） */
internal fun Transaction.matchesFilter(f: SearchFilter): Boolean {
    val q = f.query.trim()
    if (q.isNotBlank() && !matchesKeyword(q)) return false
    if (f.type != null && type != f.type) return false
    val min = f.minCents
    val max = f.maxCents
    // 填反了（最小 > 最大）没有意义，这一项直接不作数
    if (min != null && max != null && min <= max) {
        if (amountCents < min || amountCents > max) return false
    } else {
        if (min != null && amountCents < min) return false
        if (max != null && amountCents > max) return false
    }
    f.fromDate?.let { if (date.isBefore(it)) return false }
    f.toDate?.let { if (date.isAfter(it)) return false }
    if (!matchesCategories(f.categoryIds)) return false
    return true
}

/**
 * 关键词命中：备注 / 账户 / 商家 / 对方 / 标签 / 类型 / 分类（**含父级**）/ 金额。
 *
 * - 分类沿 parentId 上溯：搜「餐饮」也要能搜到「早餐」「外卖」这类二级分类的账，
 *   否则搜索框承诺的「搜分类」对二级分类全部失效；
 * - 金额按「分」比较：`12` 命中 ¥12.00 与 ¥12.50，但不再命中 ¥112.00。
 */
private fun Transaction.matchesKeyword(q: String): Boolean {
    val typeName = when (type) {
        TransactionType.EXPENSE -> "支出"
        TransactionType.INCOME -> "收入"
        TransactionType.TRANSFER -> "转账"
    }
    if (listOf(note, account, merchant, counterparty, counterpartyContact, counterpartyOrg, typeName)
            .any { it.contains(q, ignoreCase = true) }
    ) {
        return true
    }
    if (tags.any { it.contains(q, ignoreCase = true) }) return true
    var cursor: String? = categoryId
    var depth = 0
    while (cursor != null && cursor.isNotEmpty() && depth < 8) {
        val category = runCatching { CategoryCatalog.of(cursor) }.getOrNull() ?: break
        if (category.name.contains(q, ignoreCase = true)) return true
        cursor = category.parentId
        depth++
    }
    val target = q.toBigDecimalOrNull()
        ?.multiply(java.math.BigDecimal(100))
        ?.setScale(0, java.math.RoundingMode.HALF_UP)
        ?.toLong()
    if (target != null && target > 0L) {
        val abs = kotlin.math.abs(amountCents)
        if (abs == target) return true
        // 输入整数元时允许命中同元的小数金额（12 → 12.50）
        if (!q.contains('.') && abs / 100 == target / 100) return true
    }
    return false
}

/**
 * 分类命中判定：选中的是一级分类时，其子分类（餐饮 → 早餐 / 外卖）也算命中。
 * 否则用户在筛选里勾「餐饮」却查不到任何外卖账，会以为筛选坏了。
 */
private fun Transaction.matchesCategories(ids: Set<String>): Boolean {
    if (ids.isEmpty()) return true
    var cursor: String? = categoryId
    var depth = 0
    while (cursor != null && cursor.isNotEmpty() && depth < 8) {
        if (cursor in ids) return true
        cursor = runCatching { CategoryCatalog.of(cursor).parentId }.getOrNull()
        depth++
    }
    return false
}

/**
 * 高级筛选弹窗：范围 + 类型 + 金额区间 + 日期区间 + 分类，可叠加。
 *
 * 日期用文本输入（yyyy-MM-dd）而非日期滚轮：这里只需要区间，且解析失败要**明确提示**
 * 而不是静默变成"不限"。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedFilterDialog(
    show: Boolean,
    filter: SearchFilter,
    onDismiss: () -> Unit,
    onApply: (SearchFilter) -> Unit,
    /** 条件预览：给定条件算一下会剩几笔，让用户点「应用」前心里有数 */
    previewCount: (SearchFilter) -> Int = { 0 },
) {
    var draft by remember(show, filter) { mutableStateOf(filter) }
    var dateError by remember { mutableStateOf("") }
    // 刚点过的一级分类：展开它的子分类，用于「只筛外卖」这类细化
    var refineParentId by remember(show) { mutableStateOf<String?>(null) }

    fun parseDate(text: String): LocalDate? =
        if (text.isBlank()) null else try { LocalDate.parse(text.trim()) } catch (e: DateTimeParseException) { null }

    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = "筛选",
        summary = "范围、金额、日期、分类可以叠加；留空即不限。",
        confirmText = "应用",
        onConfirm = {
            val from = parseDate(draft.fromText)
            val to = parseDate(draft.toText)
            if ((draft.fromText.isNotBlank() && from == null) || (draft.toText.isNotBlank() && to == null)) {
                dateError = "日期格式不对，请按 yyyy-MM-dd 填写（如 2026-10-01）"
                return@IjiDialog
            }
            dateError = ""
            onApply(draft.copy(fromDate = from, toDate = to))
        },
    ) {
        // 内容多（日期 + 金额 + 几十个分类），小屏会顶出屏幕：按屏幕高度限高后可滚动
        val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
        Column(
            modifier = Modifier
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 预览 + 重置放在顶部：条件多时内容要滚动，放底部等于藏起来
            val previewFilter = draft.copy(
                fromDate = parseDate(draft.fromText),
                toDate = parseDate(draft.toText),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "应用后剩 ${previewCount(previewFilter)} 笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                IjiTextAction(
                    text = "重置",
                    onClick = {
                        draft = SearchFilter(scope = draft.scope)
                        dateError = ""
                        refineParentId = null
                    },
                    compact = true,
                )
            }

            SectionTitle("范围")
            ChipRow(
                items = listOf(
                    SearchScope.MONTH to "本月",
                    SearchScope.ALL to "全部时间",
                    SearchScope.RANGE to "自定义区间",
                ),
                isSelected = { it.first == draft.scope },
                label = { it.second },
                onSelect = { draft = draft.copy(scope = it.first) },
            )
            if (draft.scope == SearchScope.RANGE) {
                // 手机上敲 10 个字符选日期太费劲：先给常用区间，再允许手动改
                val today = LocalDate.now()
                val presets = remember(today) {
                    listOf(
                        "近 7 天" to (today.minusDays(6) to today),
                        "本月" to (today.withDayOfMonth(1) to today),
                        "近 3 月" to (today.minusMonths(3) to today),
                        "今年" to (today.withDayOfMonth(1).withMonth(1) to today),
                    )
                }
                ChipRow(
                    items = presets,
                    isSelected = {
                        draft.fromText == it.second.first.toString() && draft.toText == it.second.second.toString()
                    },
                    label = { it.first },
                    onSelect = {
                        draft = draft.copy(
                            fromText = it.second.first.toString(),
                            toText = it.second.second.toString(),
                        )
                    },
                )
                top.yukonga.miuix.kmp.basic.TextField(
                    draft.fromText,
                    { draft = draft.copy(fromText = it) },
                    Modifier.fillMaxWidth(),
                    label = "开始日期 yyyy-MM-dd",
                    useLabelAsPlaceholder = true,
                )
                top.yukonga.miuix.kmp.basic.TextField(
                    draft.toText,
                    { draft = draft.copy(toText = it) },
                    Modifier.fillMaxWidth(),
                    label = "结束日期 yyyy-MM-dd",
                    useLabelAsPlaceholder = true,
                )
                if (dateError.isNotBlank()) {
                    Text(dateError, style = MaterialTheme.typography.bodySmall, color = AppleRed)
                }
            }

            SectionTitle("类型")
            ChipRow(
                items = listOf(
                    null to "全部",
                    TransactionType.EXPENSE to "支出",
                    TransactionType.INCOME to "收入",
                ),
                isSelected = { it.first == draft.type },
                label = { it.second },
                // 换类型时清掉旧分类：勾着收入分类却只看支出，结果必然是空
                onSelect = { draft = draft.copy(type = it.first, categoryIds = emptySet()) },
            )

            SectionTitle("金额区间（元）")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                top.yukonga.miuix.kmp.basic.TextField(
                    draft.amountMinYuan,
                    { draft = draft.copy(amountMinYuan = it.filter { c -> c.isDigit() || c == '.' }) },
                    Modifier.weight(1f),
                    label = "最小",
                    useLabelAsPlaceholder = true,
                )
                top.yukonga.miuix.kmp.basic.TextField(
                    draft.amountMaxYuan,
                    { draft = draft.copy(amountMaxYuan = it.filter { c -> c.isDigit() || c == '.' }) },
                    Modifier.weight(1f),
                    label = "最大",
                    useLabelAsPlaceholder = true,
                )
            }
            val min = draft.minCents
            val max = draft.maxCents
            if (min != null && max != null && min > max) {
                Text(
                    "最小值比最大值还大，这一项会被忽略",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleRed,
                )
            }

            SectionTitle("分类（不选 = 全部，选一级含子分类）")
            // 一级分类平铺；点某个一级分类后展开它的子分类，可只筛「外卖」这种二级分类。
            val expenses = remember { CategoryCatalog.parentsOfType(TransactionType.EXPENSE) }
            val incomes = remember { CategoryCatalog.parentsOfType(TransactionType.INCOME) }
            fun toggleCategory(id: String) {
                draft = draft.copy(
                    categoryIds = if (id in draft.categoryIds) draft.categoryIds - id else draft.categoryIds + id,
                )
                refineParentId = id
            }
            when (draft.type) {
                TransactionType.INCOME -> CategoryChips(incomes, draft.categoryIds, ::toggleCategory)
                TransactionType.EXPENSE -> CategoryChips(expenses, draft.categoryIds, ::toggleCategory)
                else -> {
                    Text("支出", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    CategoryChips(expenses, draft.categoryIds, ::toggleCategory)
                    Text("收入", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    CategoryChips(incomes, draft.categoryIds, ::toggleCategory)
                }
            }
            // 细化：选中二级分类会顶掉它的父分类，否则父分类把子分类的筛选意图盖掉
            val refineParent = refineParentId
            val children = remember(refineParent) { refineParent?.let { CategoryCatalog.childrenOf(it) }.orEmpty() }
            if (refineParent != null && children.isNotEmpty()) {
                Text(
                    "细化「${CategoryCatalog.of(refineParent).name}」",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                CategoryChips(children, draft.categoryIds) { id ->
                    val rest = draft.categoryIds - refineParent
                    draft = draft.copy(categoryIds = if (id in rest) rest - id else rest + id)
                }
            }

        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}

/** 分类胶囊组：点击切换选中，多个分类之间是「或」的关系 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    categories: List<Category>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEach { cat ->
            val selected = cat.id in selectedIds
            // 热区 44dp：文字 12sp 本身只有 ~27dp，手指点不准
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .clickable { onToggle(cat.id) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    cat.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MiuixPrimary else TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun <T> ChipRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val selected = isSelected(item)
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(item),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MiuixPrimary else TextSecondary,
                )
            }
        }
    }
}
