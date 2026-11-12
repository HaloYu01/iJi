package com.iji.app.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.CategoryStat
import com.iji.app.MainViewModel
import com.iji.app.MonthStat
import com.iji.app.data.model.CurrencyDisplay
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiSegmentedRow
import com.iji.app.ui.components.IjiTopBar
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiIconFocusRing
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.ijiTopBarRecordHeight
import com.iji.app.ui.components.topbar.rememberTopBarScrollBehavior
import com.iji.app.ui.components.topbar.topBarContentInset
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.ExpenseAmount
import com.iji.app.ui.theme.IjiIconSize
import com.iji.app.ui.theme.IjiTheme
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.theme.minTouchTarget
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import com.iji.app.ui.util.sharedMainViewModel

/** 图例最多展示的分类数，其余合并为「其他 N 类」 */
private const val LEGEND_TOP = 4

/** 对比条两侧的最小可见宽度（比例），避免 0.2% 被画成 0 */
private const val MIN_BAR_FRACTION = 0.03f

/**
 * 统计页：时间控制（维度 + 日期导航）+ 收支概览 + 分类构成 + 收支趋势。
 *
 * 口径约定：
 * - 「当期」= 周 / 月 / 年维度下的同一时间段，收支概览与分类构成共用；
 * - 「趋势」有自己的范围选择（近6月 / 近12月 / 今年 / 全部），卡内显式标注，二者互不影响。
 *
 * 图表类型按数据语义固定：构成用环形、趋势用折线，不提供图表类型切换。
 */
@Composable
fun StatsScreen(
    onNavigateMine: () -> Unit = {},
    /** 下钻：分类 + 当期区间 → 该分类的账单列表 */
    onNavigateCategory: (categoryId: String, start: LocalDate, end: LocalDate) -> Unit = { _, _, _ -> },
    app: MainViewModel = sharedMainViewModel(),
    viewModel: StatsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StatsViewModel(app) as T
        },
    ),
) {
    val period by viewModel.periodStats.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()
    val dimension by viewModel.dimension.collectAsStateWithLifecycle()
    val week by viewModel.selectedWeek.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val year by viewModel.selectedYear.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarController.current
    // 平板 / 横屏（≥600dp）用双列，窄屏单列
    val isWide = LocalConfiguration.current.screenWidthDp >= 600

    // AI 小结：只有用户点了才联网（详见 AiSummaryDialog 的三条边界）
    var showAiSummary by remember { mutableStateOf(false) }

    // ── 固定顶栏（浮层）──
    val listState = rememberLazyListState()
    val topBarSurface = MaterialTheme.colorScheme.background
    val topBarBackdrop = rememberLayerBackdrop { drawRect(topBarSurface); drawContent() }
    // 顶栏采样层节流：滚动中才重录，静止不录、不重跑顶栏模糊 shader
    val topBarRecordGate = remember { com.iji.app.ui.effects.glass.BackdropRecordGate() }
    val topBarMustRecord = remember(listState) { { topBarRecordGate.shouldRecord(listState.isScrollInProgress) } }
    val listScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val scrollBehavior = rememberTopBarScrollBehavior()

    // 当前是否已经停在「本期」：决定「回到本期」是否可点
    val isCurrentPeriod = remember(dimension, week, month, year) {
        when (dimension) {
            StatsDimension.WEEK -> week.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) ==
                LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            StatsDimension.MONTH -> month == YearMonth.now()
            StatsDimension.YEAR -> year == Year.now()
        }
    }
    val periodTitle = when (dimension) {
        // 跨年周用 ISO 周所属年，避免出现「2027年第1周」落在 12 月的错位
        StatsDimension.WEEK -> "${week.get(IsoFields.WEEK_BASED_YEAR)}年第${
            week.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        }周"
        StatsDimension.MONTH -> "${month.year}年${month.monthValue}月"
        StatsDimension.YEAR -> "${year.value}年"
    }
    // 周维度的起止日期信息量最大；月/年标题本身已说明范围，不重复
    val rangeText = period.range
        ?.takeIf { dimension == StatsDimension.WEEK }
        ?.let { "${it.start.monthValue}/${it.start.dayOfMonth} – ${it.end.monthValue}/${it.end.dayOfMonth}" }
        .orEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
                .topBarContentInset(scrollBehavior)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .then(
                    if (listScrolled) {
                        Modifier.layerBackdrop(topBarBackdrop, recordKey = Unit, mustRecord = topBarMustRecord, recordHeight = ijiTopBarRecordHeight())
                    } else {
                        Modifier
                    },
                ),
            // 底部留出悬浮 tab 栏的高度，最后一张卡（趋势图 X 轴标签）不会被压在栏下
            contentPadding = PaddingValues(top = ijiTopBarContentPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // 宽屏封顶并居中，避免分段控件被拉成一整条
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimeControlBar(
                        modifier = Modifier.widthIn(max = if (isWide) 760.dp else 560.dp),
                        compact = isWide,
                        dimension = dimension,
                        periodTitle = periodTitle,
                        rangeText = rangeText,
                        isCurrentPeriod = isCurrentPeriod,
                        onPrev = {
                            when (dimension) {
                                StatsDimension.WEEK -> viewModel.prevWeek()
                                StatsDimension.MONTH -> viewModel.prevMonth()
                                StatsDimension.YEAR -> viewModel.prevYear()
                            }
                        },
                        onNext = {
                            when (dimension) {
                                StatsDimension.WEEK -> viewModel.nextWeek()
                                StatsDimension.MONTH -> viewModel.nextMonth()
                                StatsDimension.YEAR -> viewModel.nextYear()
                            }
                        },
                        onSelectDimension = viewModel::setDimension,
                        onBackToNow = {
                            viewModel.backToCurrentPeriod()
                            snackbar.show("已回到本期")
                        },
                    )
                }
            }
            // ≥600dp（平板 / 横屏）概览与分类并排，趋势通栏；窄屏保持单列
            if (isWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(Modifier.weight(1f)) {
                            OverviewCard(period) { showAiSummary = true }
                        }
                        Box(Modifier.weight(1f)) {
                            CategoryCardBlock(period, onNavigateCategory)
                        }
                    }
                }
            } else {
                item { OverviewCard(period) { showAiSummary = true } }
                item { CategoryCardBlock(period, onNavigateCategory) }
            }
            item { TrendCard(viewModel, trend) }
        }

        IjiTopBar(
            backdrop = topBarBackdrop,
            scrollBehavior = scrollBehavior,
            onMineClick = onNavigateMine,
            title = "统计",
        )
    }

    // AI 小结的预算参照：monthBudgetProgress 永远指向**当前自然月**的总预算，
    // 只有「月维度 + 浏览的就是这个月」时口径才对得上；浏览历史月份或周 / 年维度不带。
    val budgetProgress by app.monthBudgetProgress.collectAsStateWithLifecycle()
    val summaryBudget = if (dimension == StatsDimension.MONTH && month == YearMonth.now()) {
        budgetProgress?.let { it.amountCents to it.spentCents }
    } else {
        null
    }

    AiSummaryDialog(
        show = showAiSummary,
        dimensionLabel = dimension.label,
        periodTitle = periodTitle,
        period = period,
        budgetCents = summaryBudget?.first,
        budgetSpentCents = summaryBudget?.second,
        onDismiss = { showAiSummary = false },
    )
}

/**
 * 时间控制条：日期导航（‹ 2026年10月 ›）+ 维度分段（周 / 月 / 年）+ 回到本期。
 *
 * 维度切换与日期导航放在一起，避免「顶栏改维度、列表里改日期」两处操作互相看不见；
 * 维度切换会真正改变下方账本的口径（见 [StatsViewModel.periodStats]）。
 */
@Composable
private fun TimeControlBar(
    modifier: Modifier = Modifier,
    dimension: StatsDimension,
    periodTitle: String,
    rangeText: String,
    isCurrentPeriod: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectDimension: (StatsDimension) -> Unit,
    onBackToNow: () -> Unit,
    /** 宽屏单行：兼顾横屏（高度只有 360dp）与平板，避免两行控制条吃掉首屏 */
    compact: Boolean = false,
) {
    if (compact) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavArrow(isBack = true, onClick = onPrev)
            Text(periodTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            NavArrow(isBack = false, onClick = onNext)
            if (rangeText.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(rangeText, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(Modifier.weight(1f))
            DimensionSelector(dimension, onSelectDimension, Modifier.width(240.dp))
            Spacer(Modifier.width(8.dp))
            BackToNowText(isCurrentPeriod, onBackToNow)
        }
        return
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NavArrow(isBack = true, onClick = onPrev)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(periodTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (rangeText.isNotEmpty()) {
                    Text(rangeText, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            NavArrow(isBack = false, onClick = onNext)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DimensionSelector(dimension, onSelectDimension, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            BackToNowText(isCurrentPeriod, onBackToNow)
        }
    }
}

@Composable
private fun NavArrow(isBack: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.minTouchTarget().ijiIconFocusRing(),
    ) {
        Icon(
            if (isBack) MiuixIcons.ChevronBackward else MiuixIcons.ChevronForward,
            contentDescription = if (isBack) "上一期" else "下一期",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DimensionSelector(
    dimension: StatsDimension,
    onSelect: (StatsDimension) -> Unit,
    modifier: Modifier = Modifier,
) {
    IjiSegmentedRow(
        options = StatsDimension.entries.toList(),
        selected = dimension,
        onSelect = onSelect,
        modifier = modifier,
        label = { it.label },
    )
}

/** 常驻占位（不可用时置灰），避免出现/消失导致控制条跳动 */
@Composable
private fun BackToNowText(isCurrentPeriod: Boolean, onClick: () -> Unit) {
    IjiTextAction(
        text = "回到本期",
        onClick = onClick,
        enabled = !isCurrentPeriod,
        // 禁用态整块转中性灰：留下淡蓝会看着像"可点但没反应"
        color = if (isCurrentPeriod) TextSecondary else MaterialTheme.colorScheme.primary,
        compact = true,
    )
}

/**
 * 收支概览：支出为主指标（最大字号）+ 环比，收入 / 结余为次级，底部 100% 堆叠对比条。
 *
 * 结余不再靠「绿 / 蓝」切换暗示正负（蓝在本页已代表支出），改用中性色 + 「结余 / 超支」文案。
 */
@Composable
private fun OverviewCard(period: PeriodStats, onAiSummary: () -> Unit) {
    val balance = period.incomeCents - period.expenseCents
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.sheet,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "收支概览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (period.billCount > 0) {
                    // 明确是「收支笔数」：转账不计入，避免与列表总条数对不上
                    Text(
                        "${period.billCount} 笔收支",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                // AI 小结入口：放在概览卡标题行，和「本期」这个概念挨着，不额外占一屏高度
                IjiTextAction(text = "AI 小结", compact = true, onClick = onAiSummary)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("支出", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(2.dp))
                    // 主数字跟随区间做短促的「上滑淡入」，切换周/月/年时不至于硬跳
                    AnimatedContent(
                        targetState = period.expenseCents,
                        transitionSpec = {
                            (slideInVertically { it / 3 } + fadeIn(tween(180))) togetherWith
                                (slideOutVertically { -it / 3 } + fadeOut(tween(120)))
                        },
                        label = "expenseAmount",
                    ) { value ->
                        Text(
                            formatMoney(value),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            // 全局约定：消费红色
                            color = ExpenseAmount,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                MomentumChip(current = period.expenseCents, previous = period.prevExpenseCents)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                SubMetric(
                    label = "收入",
                    value = formatMoney(period.incomeCents),
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f),
                )
                SubMetric(
                    label = if (balance < 0) "超支" else "结余",
                    value = formatMoney(abs(balance)),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            if (period.billCount > 0) {
                Spacer(Modifier.height(16.dp))
                RatioBar(expenseCents = period.expenseCents, incomeCents = period.incomeCents)
            }
            if (period.expenseCount > 0) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                )
                Spacer(Modifier.height(12.dp))
                InsightRow(period)
            }
        }
    }
}

/**
 * 洞察行（不单独开卡，避免首屏多一层卡片、也避免双列布局时高度失衡）。
 *
 * 只放"这段时间的钱怎么花的"：最大分类 / 日均 / 笔均。
 * **刻意不做超支预警**：预算相关提醒已在预算功能里承担，重复展示会让两处口径互相打脸。
 */
@Composable
private fun InsightRow(period: PeriodStats) {
    val days = period.range
        ?.let { ChronoUnit.DAYS.between(it.start, it.end) + 1 }
        ?.coerceAtLeast(1L)
        ?: 1L
    val dailyAverage = period.expenseCents / days
    val perBill = if (period.expenseCount > 0) period.expenseCents / period.expenseCount else 0L
    val top = period.byCategory.firstOrNull()

    Row(Modifier.fillMaxWidth()) {
        InsightItem(
            label = "最大分类",
            value = top?.let { "${CategoryCatalog.of(it.categoryId).name} ${formatMoney(it.totalCents)}" } ?: "—",
            modifier = Modifier.weight(1f),
        )
        InsightDivider()
        InsightItem(
            label = "日均支出",
            value = formatMoney(dailyAverage),
            modifier = Modifier.weight(1f),
        )
        InsightDivider()
        InsightItem(
            label = "笔均支出",
            value = formatMoney(perBill),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InsightItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InsightDivider() {
    Box(
        Modifier
            .padding(horizontal = 8.dp)
            .width(0.5.dp)
            .height(26.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    )
}

/** 环比 chip：支出变多用警示色、变少用收入色；持平用中性色 */
@Composable
private fun MomentumChip(current: Long, previous: Long) {
    if (previous <= 0L) {
        // 上期没有支出：给出中性说明而不是让 chip 凭空消失（否则像是漏算了）
        if (current > 0L) {
            Text(
                "上期无支出",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.xxl))
                    .background(TextSecondary.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        return
    }
    val percent = ((current - previous) * 100.0 / previous).roundToInt()
    // 同时给金额差：只有「↑12%」时，用户还得自己回算多花了多少钱
    val delta = compactMoney(abs(current - previous))
    val (text, color) = when {
        percent > 0 -> "较上期 ↑$percent% · +$delta" to AppleRed
        percent < 0 -> "较上期 ↓${-percent}% · -$delta" to IncomeGreen
        else -> "与上期持平" to TextSecondary
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.xxl))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun SubMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 100% 堆叠对比条：支出 + 收入 = 整条，条下直接给百分比。
 *
 * 旧实现用 `coerceIn(0.01f, 1f)` 给两侧各自兜底，双方都兜底后总权重 > 1，
 * 比例反而失真；这里只对**有值的一侧**兜最小可见宽度，且百分比文字才是准确值。
 */
@Composable
private fun RatioBar(expenseCents: Long, incomeCents: Long) {
    val expenseMagnitude = abs(expenseCents)
    val incomeMagnitude = abs(incomeCents)
    val total = (expenseMagnitude + incomeMagnitude).toDouble()
    val expenseFraction = if (total > 0.0) expenseMagnitude / total else 0.0

    Box(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(Radius.dot))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Row(Modifier.fillMaxSize()) {
            if (expenseMagnitude > 0L) {
                Box(
                    Modifier
                        .weight(expenseFraction.toFloat().coerceIn(MIN_BAR_FRACTION, 1f))
                        .fillMaxHeight()
                        .background(MiuixPrimary),
                )
            }
            if (incomeMagnitude > 0L) {
                Box(
                    Modifier
                        .weight((1f - expenseFraction.toFloat()).coerceIn(MIN_BAR_FRACTION, 1f))
                        .fillMaxHeight()
                        .background(IncomeGreen),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (total > 0.0) "支出 ${(expenseFraction * 100).roundToInt()}%" else "支出 —",
            style = MaterialTheme.typography.bodySmall,
            color = MiuixPrimary,
        )
        Text(
            if (total > 0.0) "收入 ${((1 - expenseFraction) * 100).roundToInt()}%" else "收入 —",
            style = MaterialTheme.typography.bodySmall,
            color = IncomeGreen,
        )
    }
}

/** 分类卡的数据接线（单列 / 双列共用同一份参数，避免两处各写一遍） */
@Composable
private fun CategoryCardBlock(
    period: PeriodStats,
    onNavigateCategory: (String, LocalDate, LocalDate) -> Unit,
) {
    CategoryCard(
        byCategory = period.byCategory,
        byParentCategory = period.byParentCategory,
        categoryTotalCents = period.categoryExpenseCents,
        netExpenseCents = period.expenseCents,
        onSelectCategory = { categoryId ->
            // 区间可能尚未就绪（首帧），此时不响应下钻
            period.range?.let { onNavigateCategory(categoryId, it.start, it.end) }
        },
    )
}

/**
 * 分类构成：环形 + Top 4 图例（其余合并为其他），按需展开完整排行。
 *
 * 「占比」与「排行」原本是两张卡、同一份数据讲两遍；合并后默认只占用一屏内的空间，
 * 需要明细时再展开。
 */
@Composable
private fun CategoryCard(
    byCategory: List<CategoryStat>,
    byParentCategory: List<CategoryStat> = emptyList(),
    categoryTotalCents: Long,
    netExpenseCents: Long,
    onSelectCategory: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // 环形/图例共用的选中态：点扇区或点图例都走这里，两处高亮天然同步
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // 分组口径：账单记的是二级分类（早餐/外卖）时，切换后才是「餐饮一共花了多少」
    var groupByParent by rememberSaveable { mutableStateOf(false) }
    // 两种口径结果一致时（所有账单都记在一级分类）不显示切换，避免多一个无效开关
    val canRegroup = byParentCategory.isNotEmpty() && byParentCategory.size != byCategory.size
    val stats = if (groupByParent && canRegroup) byParentCategory else byCategory
    val collapsible = stats.size > LEGEND_TOP
    // 退款抵扣：分类合计（只含正向支出）会大于净支出，差额需要给用户一个解释
    val refundCents = (categoryTotalCents - netExpenseCents).coerceAtLeast(0L)
    // 分类被区间筛掉后自动失效（不回写状态，切回来仍能保持选中）
    val effectiveSelectedId = selectedId?.takeIf { id -> stats.any { it.categoryId == id } }
    val toggleSelect: (String) -> Unit = { id -> selectedId = if (selectedId == id) null else id }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.sheet,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "分类构成",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (canRegroup) {
                    IjiTextAction(
                        text = if (groupByParent) "看细分" else "看一级",
                        onClick = {
                            groupByParent = !groupByParent
                            // 口径变了，之前的选中 id 可能不在新列表里，直接清掉更直观
                            selectedId = null
                        },
                        compact = true,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (collapsible) {
                    IjiTextAction(
                        text = if (expanded) "收起" else "全部 ${stats.size} 类",
                        onClick = { expanded = !expanded },
                        compact = true,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (stats.isEmpty()) {
                StateView(state = ContentState.Empty("本期暂无支出"))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "分类构成图，共 ${stats.size} 类，" +
                                "总支出 ${formatMoney(categoryTotalCents)}，" +
                                "最大支出为 ${CategoryCatalog.of(stats.first().categoryId).name}；" +
                                "点击扇区或图例可查看单个分类占比"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 用 BoxWithConstraints 取半栏实际宽度再封顶：weight 会给死宽度，
                    // widthIn(max) 直接被忽略，平板/横屏上环会无限放大
                    BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val diameter = minOf(maxWidth, 180.dp)
                        DonutChart(
                            stats = stats,
                            totalExpenseCents = categoryTotalCents,
                            selectedId = effectiveSelectedId,
                            onSelect = toggleSelect,
                            modifier = Modifier.size(diameter),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    CategoryLegend(
                        stats = stats,
                        selectedId = effectiveSelectedId,
                        onToggle = toggleSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (refundCents > 0L) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "退款抵扣 ${formatMoney(refundCents)}（净支出 ${formatMoney(netExpenseCents)}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        )
                        Spacer(Modifier.height(14.dp))
                        CategoryDetailList(stats, onSelectCategory)
                    }
                }
            }
        }
    }
}

/** 分类图例：Top 4 + 「其他 N 类」，可点选（与环形扇区共用选中态） */
@Composable
private fun CategoryLegend(
    stats: List<CategoryStat>,
    selectedId: String?,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = stats.sumOf { it.totalCents }.coerceAtLeast(1L)
    val top = stats.take(LEGEND_TOP)
    val rest = stats.drop(LEGEND_TOP)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        top.forEach { stat ->
            LegendRow(
                name = CategoryCatalog.of(stat.categoryId).name,
                percent = percentOf(stat.totalCents, total),
                color = Color(CategoryCatalog.of(stat.categoryId).color),
                selected = stat.categoryId == selectedId,
                onClick = { onToggle(stat.categoryId) },
            )
        }
        if (rest.isNotEmpty()) {
            LegendRow(
                name = "其他 ${rest.size} 类",
                percent = percentOf(rest.sumOf { it.totalCents }, total),
                color = TextSecondary.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun LegendRow(
    name: String,
    percent: Long,
    color: Color,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xxl))
            .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$percent%",
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) color else TextSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 环形图（构成语义固定用环形）。
 *
 * 弧长按绝对值归一：退款/红字会让某分类合计为负，用带符号的合计当分母会算出负角度；
 * 中心文字仍展示真实合计。
 */
@Composable
private fun DonutChart(
    stats: List<CategoryStat>,
    totalExpenseCents: Long,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 分类已按「净额 > 0」过滤，这里再兜一次 0，避免脏数据画出负角度
    val magnitudes = stats.map { it.totalCents.coerceAtLeast(0L) }
    val total = magnitudes.sum().coerceAtLeast(1L)
    val colors = stats.map { Color(CategoryCatalog.of(it.categoryId).color) }
    val selectedIndex = stats.indexOfFirst { it.categoryId == selectedId }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                // 命中测试：把点击位置换算成"从 12 点顺时针的角度"，再落到对应扇区
                .pointerInput(stats, magnitudes, total, selectedId) {
                    detectTapGestures { offset ->
                        val radius = minOf(size.width, size.height) / 2f
                        val strokePx = 20.dp.toPx()
                        val dx = offset.x - size.width / 2f
                        val dy = offset.y - size.height / 2f
                        val distance = hypot(dx, dy)
                        // 环带之外（中心文字 / 图外空白）视为「取消选中」
                        if (distance < radius - strokePx * 1.8f || distance > radius + 6.dp.toPx()) {
                            selectedId?.let(onSelect)
                            return@detectTapGestures
                        }
                        val degrees = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
                        val fromTop = (degrees - 270f + 360f) % 360f
                        var accumulated = 0f
                        magnitudes.forEachIndexed { index, magnitude ->
                            val sweep = magnitude.toFloat() / total * 360f
                            if (fromTop >= accumulated && fromTop < accumulated + sweep) {
                                onSelect(stats[index].categoryId)
                                return@detectTapGestures
                            }
                            accumulated += sweep
                        }
                    }
                },
        ) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            var start = -90f
            magnitudes.forEachIndexed { index, magnitude ->
                val sweep = magnitude.toFloat() / total * 360f
                val gap = if (sweep > 3f) 2f else 0f
                val isSelected = index == selectedIndex
                drawArc(
                    // 有选中项时其余扇区淡化，靠明度而非只靠"多一圈描边"来区分
                    color = if (selectedIndex >= 0 && !isSelected) {
                        colors[index].copy(alpha = 0.28f)
                    } else {
                        colors[index]
                    },
                    startAngle = start,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.6f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = if (isSelected) stroke + 4.dp.toPx() else stroke,
                        cap = StrokeCap.Butt,
                    ),
                )
                start += sweep
            }
        }
        // 中心文字跟随选中项：从"总支出"切到"某分类金额 + 占比"
        val focused = stats.getOrNull(selectedIndex)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (focused != null) {
                Text(
                    CategoryCatalog.of(focused.categoryId).name,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatMoney(focused.totalCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${percentOf(focused.totalCents, total)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            } else {
                Text("总支出", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(
                    formatMoney(totalExpenseCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    // 全局约定：消费红色
                    color = ExpenseAmount,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 分类明细排行：序号 + 图标 + 占比条 + 金额；整行可点，下钻到该分类账单 */
@Composable
private fun CategoryDetailList(stats: List<CategoryStat>, onSelectCategory: (String) -> Unit) {
    val total = stats.sumOf { it.totalCents }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        stats.forEachIndexed { index, stat ->
            val category = CategoryCatalog.of(stat.categoryId)
            val tint = Color(category.color)
            val fraction = (stat.totalCents.toFloat() / total).coerceIn(0f, 1f)
            Row(
                modifier = Modifier
                    // 整行可点 + 最小触控高度；下钻入口
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .clickable { onSelectCategory(stat.categoryId) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.width(20.dp),
                )
                CategoryIconImage(
                    name = category.name,
                    color = tint,
                    size = IjiIconSize.ChartRow,
                    categoryId = category.id,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${percentOf(stat.totalCents, total)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(Radius.dot))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(tint),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    formatMoney(stat.totalCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * 收支趋势卡：范围选择 + 双折线（Y 轴刻度 + 网格 + 对称面积）。
 *
 * 范围是**独立**于上方当期的显式选择，卡内用「范围」标签标出，避免误以为跟着周/月/年走。
 */
@Composable
private fun TrendCard(viewModel: StatsViewModel, trend: List<MonthStat>) {
    val range by viewModel.trendRange.collectAsStateWithLifecycle()
    // 点选的月份（null = 未选中）；数据换范围后旧下标可能越界，用 getOrNull 兜住
    var selectedIndex by remember(trend) { mutableStateOf<Int?>(null) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    val focused = selectedIndex?.let { trend.getOrNull(it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.sheet,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("收支趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("范围", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.width(8.dp))
                IjiSegmentedRow(
                    options = TrendRange.entries.toList(),
                    selected = range,
                    onSelect = { viewModel.setTrendRange(it) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { it.label },
                )
            }
            Spacer(Modifier.height(12.dp))
            // 实线 / 虚线是颜色之外的第二编码：色盲用户也能区分两条线
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendLine(MiuixPrimary, dashed = false)
                Spacer(Modifier.width(6.dp))
                Text("支出", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.width(14.dp))
                LegendLine(IncomeGreen, dashed = true)
                Spacer(Modifier.width(6.dp))
                Text("收入", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(Modifier.height(10.dp))
            if (focused != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.xxl))
                        .background(MiuixPrimary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "${focused.month.monthValue}月",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "支出 ${formatMoney(focused.expenseCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        // 全局约定：消费红色
                        color = ExpenseAmount,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "收入 ${formatMoney(focused.incomeCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = IncomeGreen,
                    )
                }
            } else {
                Text(
                    "点击折线数据点查看当月数值",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (trend.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StateView(state = ContentState.Empty("该范围暂无数据"))
                    if (range != TrendRange.LAST6) {
                        IjiTextAction(
                            text = "看近 6 月",
                            onClick = { viewModel.setTrendRange(TrendRange.LAST6) },
                        )
                    }
                }
            } else {
                val expenseTotal = trend.sumOf { it.expenseCents }
                val incomeTotal = trend.sumOf { it.incomeCents }
                // 切换范围时交叉淡入（200ms），避免折线「瞬移」；只做淡入淡出，
                // 不加位移，避免与页面滚动方向冲突
                Crossfade(targetState = trend, animationSpec = tween(200), label = "trendChart") { data ->
                    MonthLineChart(
                        stats = data,
                        selectedIndex = selectedIndex?.takeIf { it in data.indices },
                        onSelect = { index -> selectedIndex = if (selectedIndex == index) null else index },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .semantics {
                                contentDescription = "收支趋势折线图，支出合计 ${formatMoney(expenseTotal)}，" +
                                    "收入合计 ${formatMoney(incomeTotal)}；点击数据点可查看单月数值"
                            },
                    )
                }
                Spacer(Modifier.height(8.dp))
                // 图表数据的文本等价物：读屏与"要精确数字"的场景都靠它
                IjiTextAction(
                    text = if (showTable) "收起数据" else "查看数据",
                    onClick = { showTable = !showTable },
                    compact = true,
                )
                AnimatedVisibility(
                    visible = showTable,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    TrendDataTable(trend)
                }
            }
        }
    }
}

/** 图例线段：实线=支出、虚线=收入（颜色之外的第二编码） */
@Composable
private fun LegendLine(color: Color, dashed: Boolean) {
    Canvas(Modifier.size(16.dp, 10.dp)) {
        val y = size.height / 2f
        val stroke = 2.dp.toPx()
        if (dashed) {
            drawLine(color, Offset(0f, y), Offset(size.width * 0.42f, y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.62f, y), Offset(size.width, y), strokeWidth = stroke, cap = StrokeCap.Round)
        } else {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/** 趋势数据的文本等价物（读屏 / 要精确数字时用） */
@Composable
private fun TrendDataTable(trend: List<MonthStat>) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("月份", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.width(68.dp))
            Text(
                "支出",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
            Text(
                "收入",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
        trend.forEach { stat ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    "${stat.month.year}/${stat.month.monthValue}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(68.dp),
                )
                Text(
                    formatMoney(stat.expenseCents),
                    style = MaterialTheme.typography.bodySmall,
                    // 全局约定：消费红色
                    color = ExpenseAmount,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
                Text(
                    formatMoney(stat.incomeCents),
                    style = MaterialTheme.typography.bodySmall,
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * 月度收支趋势折线图。
 *
 * - 左侧刻度（max / 中值 / 0）+ 水平网格，解决「旧图没有量级参照」的问题；
 * - 支出、收入都带**对称**的渐变面积，避免只有支出被强调；
 * - 数据点保留，月份标签按密度抽稀（>12 个月只留首/中/末）。
 */
@Composable
private fun MonthLineChart(
    stats: List<MonthStat>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stats.isEmpty()) return
    val max = maxOf(
        stats.maxOf { it.expenseCents },
        stats.maxOf { it.incomeCents },
        0L,
    ).coerceAtLeast(1L)
    // Canvas 的 DrawScope 取不到 MaterialTheme，颜色先在这里解析
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val guideColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val expenseColor = MiuixPrimary
    val incomeColor = IncomeGreen
    // 收入用虚线：颜色之外的第二编码，色盲也能区分两条线
    val incomeDash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(compactMoney(max), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(compactMoney(max / 2), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("0", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(Modifier.width(6.dp))
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // 点按换算成最近的数据点：x / 步长 四舍五入
                    .pointerInput(stats.size) {
                        detectTapGestures { offset ->
                            val last = stats.lastIndex
                            if (last <= 0) {
                                onSelect(0)
                                return@detectTapGestures
                            }
                            val step = size.width / last.toFloat()
                            onSelect((offset.x / step).roundToInt().coerceIn(0, last))
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                val stepX = w / (stats.size - 1).coerceAtLeast(1)

                fun points(selector: (MonthStat) -> Long) = stats.mapIndexed { index, stat ->
                    val value = selector(stat).toFloat() / max * h
                    Offset(index * stepX, (h - value).coerceIn(0f, h))
                }

                // 网格：0 / 中值 / 峰值三条，与左侧刻度对齐
                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    val y = h * fraction
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val expenses = points { it.expenseCents }
                val incomes = points { it.incomeCents }

                fun areaPath(points: List<Offset>) = Path().apply {
                    moveTo(points.first().x, h)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, h)
                    close()
                }

                drawPath(
                    areaPath(expenses),
                    brush = Brush.verticalGradient(
                        0f to expenseColor.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                    ),
                )
                drawPath(
                    areaPath(incomes),
                    brush = Brush.verticalGradient(
                        0f to incomeColor.copy(alpha = 0.12f),
                        1f to Color.Transparent,
                    ),
                )

                // 选中月份的竖向参考线，压在折线下方
                selectedIndex?.let { index ->
                    val x = index * stepX
                    drawLine(
                        color = guideColor,
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                (0 until expenses.lastIndex).forEach { i ->
                    drawLine(expenseColor, expenses[i], expenses[i + 1], strokeWidth = 2.5.dp.toPx())
                }
                (0 until incomes.lastIndex).forEach { i ->
                    drawLine(
                        color = incomeColor,
                        start = incomes[i],
                        end = incomes[i + 1],
                        strokeWidth = 2.5.dp.toPx(),
                        pathEffect = incomeDash,
                    )
                }
                expenses.forEachIndexed { index, point ->
                    drawCircle(
                        expenseColor,
                        if (index == selectedIndex) 5.dp.toPx() else 3.5.dp.toPx(),
                        point,
                    )
                }
                incomes.forEachIndexed { index, point ->
                    drawCircle(
                        incomeColor,
                        if (index == selectedIndex) 5.dp.toPx() else 3.5.dp.toPx(),
                        point,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val labelIndices = if (stats.size > 12) {
            listOf(0, stats.size / 2, stats.lastIndex)
        } else {
            stats.indices.toList()
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(58.dp))
            // 标签中心必须落在数据点上（i / (n-1) 比例）。用 SpaceBetween 会让首尾标签
            // 各向内偏半个标签宽，看起来浮在数据点旁边而不是在正下方。
            Layout(
                modifier = Modifier.weight(1f),
                content = {
                    labelIndices.forEach { index ->
                        Text(
                            "${stats[index].month.monthValue}月",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                },
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val step = width / (stats.size - 1).coerceAtLeast(1).toFloat()
                val placeables = measurables.map { it.measure(Constraints()) }
                val labelHeight = placeables.maxOfOrNull { it.height } ?: 0
                layout(width, labelHeight) {
                    placeables.forEachIndexed { i, placeable ->
                        // 首尾标签允许溢出画布半个标签宽（左右各落在刻度列 / 卡片内边距里），
                        // 换来的才是中心精确对齐
                        placeable.place(
                            (labelIndices[i] * step - placeable.width / 2f).roundToInt(),
                            0,
                        )
                    }
                }
            }
        }
    }
}

/** 占比取整：整数除法会系统性偏小（多行相加明显不到 100%），这里四舍五入 */
private fun percentOf(value: Long, total: Long): Long =
    if (total <= 0L) 0L else (value * 100.0 / total).roundToInt().toLong()

/** 图表刻度用的紧凑金额：¥1.2万 / ¥3,200（符号跟随账本币种，「万」只是数量级） */
private fun compactMoney(cents: Long): String {
    val symbol = CurrencyDisplay.current.symbol
    val yuan = abs(cents) / 100.0
    return when {
        yuan >= 10_000 -> "$symbol${"%.1f".format(yuan / 10_000)}万"
        yuan >= 1_000 -> "$symbol${"%,.0f".format(yuan)}"
        else -> "$symbol${yuan.roundToInt()}"
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, heightDp = 900)
@Composable
private fun StatsPreview() {
    IjiTheme { StatsScreen() }
}
