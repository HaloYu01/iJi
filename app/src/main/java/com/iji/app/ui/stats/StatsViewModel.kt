package com.iji.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iji.app.CategoryStat
import com.iji.app.MainViewModel
import com.iji.app.MonthStat
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** 统计维度：周 / 月 / 年。切换后「当期」数据（收支概览、分类构成）同步换口径。 */
enum class StatsDimension(val label: String) {
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
}

/** 闭区间日期范围 */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * 「当期」统计口径：收支概览与分类构成共用这一份数据。
 *
 * 为什么要单独算、而不是复用 [MainViewModel.monthUiState]：那个状态只认 `selectedMonth`，
 * 切到「周 / 年」后数字不会变（旧实现的假交互）。这里按维度真正取区间，
 * 保证一屏之内所有卡片说的是同一个时间段。
 */
data class PeriodStats(
    /** 净支出：含退款（负数支出）抵扣，与首页/概览一致 */
    val expenseCents: Long = 0L,
    val incomeCents: Long = 0L,
    /** 上一期支出（周→上周 / 月→上月 / 年→去年），仅用于环比，不做展示口径 */
    val prevExpenseCents: Long = 0L,
    /** 上一期收入（口径同上）：AI 小结算收入环比用 */
    val prevIncomeCents: Long = 0L,
    /** 分类构成：只保留净额 > 0 的分类（净退款分类不是"花在哪"，会让占比失真） */
    val byCategory: List<CategoryStat> = emptyList(),
    /** 同一份支出按**一级分类**归并；账单多用二级分类时，这里才看得出"餐饮一共花了多少" */
    val byParentCategory: List<CategoryStat> = emptyList(),
    /** [byCategory] 的合计，环形图/占比的分母；与 [expenseCents] 的差额即退款抵扣 */
    val categoryExpenseCents: Long = 0L,
    val billCount: Int = 0,
    /** 支出笔数（不含收入/转账）：算笔均用 */
    val expenseCount: Int = 0,
    val range: DateRange? = null,
)

/**
 * 图表页专属 ViewModel：持有本屏局部 UI 状态（维度 / 趋势时间范围），
 * 共享数据（流水 / 趋势 / 日期导航）委托给 App 级 MainViewModel。
 *
 * 所有聚合都在 [Dispatchers.Default] 上做：区间过滤是 O(n)，不要在 UI 线程重扫。
 */
class StatsViewModel(private val app: MainViewModel) : ViewModel() {

    private val _dimension = MutableStateFlow(StatsDimension.MONTH)
    val dimension: StateFlow<StatsDimension> = _dimension
    fun setDimension(dimension: StatsDimension) { _dimension.value = dimension }

    val selectedWeek = app.selectedWeek
    val selectedMonth = app.selectedMonth
    val selectedYear = app.selectedYear

    /** 趋势图的时间范围（月）；与上方「当期」口径相互独立，由用户显式选择 */
    private val _trendRange = MutableStateFlow(TrendRange.LAST6)
    val trendRange: StateFlow<TrendRange> = _trendRange
    fun setTrendRange(range: TrendRange) { _trendRange.value = range }

    /** 当前维度 + 日期导航下的当期统计（收支 / 分类 / 环比基数） */
    val periodStats: StateFlow<PeriodStats> =
        combine(
            app.transactions,
            _dimension,
            app.selectedMonth,
            app.selectedWeek,
            app.selectedYear,
        ) { list, dimension, month, week, year ->
            buildPeriod(list, dimension, month, week, year)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodStats())

    /** 当前时间范围内的月度收支趋势（从旧到新），后台聚合 */
    val trend: StateFlow<List<MonthStat>> = combine(app.transactions, _trendRange) { list, range ->
        buildTrend(list, range)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun prevWeek() = app.prevWeek()
    fun nextWeek() = app.nextWeek()
    fun prevMonth() = app.prevMonth()
    fun nextMonth() = app.nextMonth()
    fun prevYear() = app.prevYear()
    fun nextYear() = app.nextYear()

    /** 把三个日期锚点都拨回「现在」；维度与趋势范围保持不变（它们是用户的显式选择） */
    fun backToCurrentPeriod() {
        app.backToThisWeek()
        app.backToThisMonth()
        app.backToThisYear()
    }
}

/** 当前维度对应的时间区间 */
internal fun periodRange(
    dimension: StatsDimension,
    week: LocalDate,
    month: YearMonth,
    year: Year,
): DateRange = when (dimension) {
    StatsDimension.WEEK -> {
        // 周一为一周起点（与日历页一致）
        val monday = week.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        DateRange(monday, monday.plusDays(6))
    }
    StatsDimension.MONTH -> DateRange(month.atDay(1), month.atEndOfMonth())
    StatsDimension.YEAR -> DateRange(year.atDay(1), year.atMonth(12).atEndOfMonth())
}

/** 上一期的同长度区间（周/月/年各退一格），用于环比 */
internal fun previousRange(range: DateRange, dimension: StatsDimension): DateRange = when (dimension) {
    StatsDimension.WEEK -> DateRange(range.start.minusWeeks(1), range.end.minusWeeks(1))
    StatsDimension.MONTH -> {
        val prev = YearMonth.from(range.start).minusMonths(1)
        DateRange(prev.atDay(1), prev.atEndOfMonth())
    }
    StatsDimension.YEAR -> DateRange(range.start.minusYears(1), range.end.minusYears(1))
}

private fun buildPeriod(
    list: List<Transaction>,
    dimension: StatsDimension,
    month: YearMonth,
    week: LocalDate,
    year: Year,
): PeriodStats {
    val range = periodRange(dimension, week, month, year)
    val previous = previousRange(range, dimension)

    // 单遍扫描：当期与上一期互斥判断，避免对同一份流水反复 filter/sum
    var expenseCents = 0L
    var incomeCents = 0L
    var prevExpenseCents = 0L
    var prevIncomeCents = 0L
    var billCount = 0
    var expenseCount = 0
    val byCategory = HashMap<String, Long>()
    val byParent = HashMap<String, Long>()
    list.forEach { tx ->
        val inCurrent = tx.date in range
        val inPrevious = !inCurrent && tx.date in previous
        if (!inCurrent && !inPrevious) return@forEach
        // 与首页口径一致：「不计入收支」在支出/收入两侧都剔除
        if (tx.excludeFromStats) return@forEach
        when (tx.type) {
            TransactionType.EXPENSE -> if (inCurrent) {
                expenseCents += tx.amountCents
                byCategory[tx.categoryId] = (byCategory[tx.categoryId] ?: 0L) + tx.amountCents
                // 二级分类归并到它的一级分类；找不到父级（自定义/已删除）就按自己算
                val parentId = runCatching { CategoryCatalog.of(tx.categoryId).parentId }
                    .getOrNull() ?: tx.categoryId
                byParent[parentId] = (byParent[parentId] ?: 0L) + tx.amountCents
                billCount++
                expenseCount++
            } else {
                prevExpenseCents += tx.amountCents
            }
            TransactionType.INCOME -> if (inCurrent) {
                incomeCents += tx.amountCents
                billCount++
            } else {
                prevIncomeCents += tx.amountCents
            }
            TransactionType.TRANSFER -> Unit
        }
    }

    // 负净额（退款大于支出）不是"钱花在哪"，会污染环形占比，直接排除
    fun toStats(map: Map<String, Long>) = map
        .map { (id, total) -> CategoryStat(id, total) }
        .filter { it.totalCents > 0L }
        .sortedByDescending { it.totalCents }

    val categories = toStats(byCategory)

    return PeriodStats(
        expenseCents = expenseCents,
        incomeCents = incomeCents,
        prevExpenseCents = prevExpenseCents,
        prevIncomeCents = prevIncomeCents,
        byCategory = categories,
        byParentCategory = toStats(byParent),
        categoryExpenseCents = categories.sumOf { it.totalCents },
        billCount = billCount,
        expenseCount = expenseCount,
        range = range,
    )
}

/** 趋势时间范围选项 */
enum class TrendRange(val label: String) {
    LAST6("近6月"),
    LAST12("近12月"),
    YEAR("今年"),
    ALL("全部"),
}

/** 按范围生成从旧到新的月度趋势（支出 / 收入，单位分） */
private fun buildTrend(list: List<Transaction>, range: TrendRange): List<MonthStat> {
    val now = YearMonth.now()
    val months: List<YearMonth> = when (range) {
        TrendRange.LAST6 -> (5L downTo 0L).map { now.minusMonths(it) }
        TrendRange.LAST12 -> (11L downTo 0L).map { now.minusMonths(it) }
        TrendRange.YEAR -> (now.monthValue - 1 downTo 0).map { now.withMonth(it + 1) }
        TrendRange.ALL -> {
            val min = list.minOfOrNull { YearMonth.from(it.date) } ?: now
            val span = ChronoUnit.MONTHS.between(min, now).coerceAtLeast(0L)
            (0L..span).map { min.plusMonths(it) }
        }
    }
    // 单次分桶：O(N)。旧实现是「每个月都全表扫一遍」= O(月数 × N)，
    // 选「全部」时月数可达几十，数据量一大就会在切换范围时卡顿。
    // 口径与收支概览/分类一致：剔除「不计入收支」，否则同页会出现两个"10 月支出"。
    val expenseByMonth = HashMap<YearMonth, Long>()
    val incomeByMonth = HashMap<YearMonth, Long>()
    list.forEach { tx ->
        if (tx.excludeFromStats) return@forEach
        val month = YearMonth.from(tx.date)
        when (tx.type) {
            TransactionType.EXPENSE ->
                expenseByMonth[month] = (expenseByMonth[month] ?: 0L) + tx.amountCents
            TransactionType.INCOME ->
                incomeByMonth[month] = (incomeByMonth[month] ?: 0L) + tx.amountCents
            TransactionType.TRANSFER -> Unit
        }
    }
    return months.map { month ->
        MonthStat(
            month = month,
            expenseCents = expenseByMonth[month] ?: 0L,
            incomeCents = incomeByMonth[month] ?: 0L,
        )
    }
}
