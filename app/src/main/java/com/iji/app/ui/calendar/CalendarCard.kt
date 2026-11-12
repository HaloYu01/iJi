package com.iji.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.DaySummary
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiIconFocusRing
import com.iji.app.ui.theme.ExpenseAmount
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.theme.minTouchTarget
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 固定 6 行：每月行数不同，跟随内容变高会让卡片在横滑时上下跳 */
private const val CALENDAR_ROWS = 6
private val CELL_HEIGHT = 46.dp
private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 可横滑 ±50 年；不用 Int.MAX_VALUE，避免页码换算时的边界坑 */
private const val PAGE_SPAN = 600

private fun monthOfPage(base: YearMonth, page: Int): YearMonth =
    base.plusMonths((page - PAGE_SPAN).toLong())

private fun pageOfMonth(base: YearMonth, month: YearMonth): Int =
    (PAGE_SPAN + ChronoUnit.MONTHS.between(base, month)).toInt().coerceIn(0, PAGE_SPAN * 2)

/**
 * 日历卡：月份头（‹ 2026年10月 ›）+ 星期表头 + 可横滑的月网格 + 本月合计。
 *
 * 几个刻意的取舍：
 * - 相邻月淡显：让 6 行永远填满，同时给"跨月翻页"一个可点的入口（点一下即跳过去）。
 * - 选中用主色实心、今天用主色描边：两者可叠加（今天且选中 = 实心 + 白字）。
 *   不沿用旧的绿色选中：绿色在本 App 已固定表示"收入"。
 */
@Composable
internal fun CalendarCard(
    month: YearMonth,
    summaries: List<DaySummary>,
    selectedDate: LocalDate,
    today: LocalDate,
    /** 首帧数据未就绪时用占位符，避免把「还没读出来」显示成 0 */
    ready: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 基准月固定在首次组合：翻页页码才有稳定含义
    val base = remember { YearMonth.now() }
    val pagerState = rememberPagerState(initialPage = pageOfMonth(base, month)) { PAGE_SPAN * 2 + 1 }
    // 手势 collect 里要读到最新月份，否则往回滑时会拿旧值比较
    val currentMonth by rememberUpdatedState(month)
    val summaryByDate = remember(summaries) { summaries.associateBy { it.date } }

    // 手势翻页 → 停稳后才写回 ViewModel，避免滑动过程中一帧一次切换
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val target = monthOfPage(base, page)
            if (target != currentMonth) onMonthChange(target)
        }
    }
    // 外部改月（点「今天」/ 点相邻月日期 / 首页切月）→ 让 Pager 跟上。
    // 这里**不能**判断 isScrollInProgress 再跳过：一旦因为"正在滑动"跳过，就没有任何后续
    // 事件再来重试（month 值没变，LaunchedEffect 不会重跑），Pager 与 ViewModel 会永久错位
    // —— 表现为日历停在 9 月、账本却显示 10 月（已踩）。scrollToPage 自身会打断旧动画。
    LaunchedEffect(month) {
        val target = pageOfMonth(base, month)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }

    // 标题跟着手指走：读 currentPage（滑过一格的中线就换），而不是等 settle。
    // 合计仍按已加载的 month 算（数据只有当月的），避免出现「标题 11 月、合计还写着 0」。
    val shownMonth = monthOfPage(base, pagerState.currentPage)
    val monthIncome = summaries.sumOf { it.incomeCents }
    val monthExpense = summaries.sumOf { it.expenseCents }

    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Radius.sheet,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NavArrow(isBack = true) { onMonthChange(shownMonth.minusMonths(1)) }
                Text(
                    "${shownMonth.year}年${shownMonth.monthValue}月",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                NavArrow(isBack = false) { onMonthChange(shownMonth.plusMonths(1)) }
            }
            Spacer(Modifier.height(2.dp))
            WeekHeader()
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CELL_HEIGHT * CALENDAR_ROWS),
                // 预组合左右各一页：只靠手势触发首次组合的话，快速滑动第一帧会掉
                beyondViewportPageCount = 1,
            ) { page ->
                MonthGrid(
                    month = monthOfPage(base, page),
                    summaryByDate = summaryByDate,
                    selectedDate = selectedDate,
                    today = today,
                    onSelectDate = onSelectDate,
                    onMonthChange = onMonthChange,
                )
            }
            Spacer(Modifier.height(6.dp))
            CalendarDivider()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("本月", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.width(8.dp))
                if (!ready) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                } else if (summaries.isEmpty()) {
                    // 整月没有任何账单：三个 ¥0.00 会让人以为是加载失败
                    Text(
                        "本月还没有账单",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    SummaryPill("收", formatMoney(monthIncome), IncomeGreen, Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    SummaryPill(
                        "支",
                        formatMoney(monthExpense),
                        // 全局约定：消费红色
                        ExpenseAmount,
                        Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    SummaryPill(
                        "余",
                        formatMoney(monthIncome - monthExpense),
                        TextSecondary,
                        Modifier.weight(1f),
                    )
                }
            }
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
            contentDescription = if (isBack) "上个月" else "下个月",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun WeekHeader() {
    Row(Modifier.fillMaxWidth()) {
        WEEK_LABELS.forEach { label ->
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    summaryByDate: Map<LocalDate, DaySummary>,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    val firstOfMonth = month.atDay(1)
    // 周一为一周起点
    val leading = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val gridStart = firstOfMonth.minusDays(leading.toLong())

    Column(Modifier.fillMaxSize()) {
        for (row in 0 until CALENDAR_ROWS) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val date = gridStart.plusDays((row * 7 + col).toLong())
                    val inMonth = YearMonth.from(date) == month
                    DayCell(
                        date = date,
                        summary = if (inMonth) summaryByDate[date] else null,
                        inMonth = inMonth,
                        isSelected = date == selectedDate,
                        isToday = date == today,
                        onClick = {
                            onSelectDate(date)
                            if (!inMonth) onMonthChange(YearMonth.from(date))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    summary: DaySummary?,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val expense = summary?.expenseCents ?: 0L
    val income = summary?.incomeCents ?: 0L
    val shape = RoundedCornerShape(Radius.md)

    val dayColor = when {
        isSelected -> Color.White
        !inMonth -> onSurface.copy(alpha = 0.30f)
        isToday -> primary
        else -> onSurface
    }

    // 读屏文案：一整格只念一句「几月几日 + 今天/已选中 + 收支」，不要逐个念日期/金额
    val description = buildString {
        append(date.monthValue).append("月").append(date.dayOfMonth).append("日")
        if (isToday) append("，今天")
        if (isSelected) append("，已选中")
        when {
            expense > 0 && income > 0 ->
                append("，支出").append(formatMoney(expense))
                    .append("，收入").append(formatMoney(income))
            expense > 0 -> append("，支出").append(formatMoney(expense))
            income > 0 -> append("，收入").append(formatMoney(income))
            else -> append("，无账单")
        }
    }

    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .padding(1.dp)
            .clip(shape)
            .then(
                when {
                    isSelected -> Modifier.background(primary, shape)
                    isToday -> Modifier.border(1.dp, primary, shape)
                    else -> Modifier
                }
            )
            .clickable(onClickLabel = "查看当天账单", onClick = onClick)
            // clearAndSetSemantics：把「描述」和「点击动作」合到同一个节点上。
            // 只写 semantics(mergeDescendants) 会把两者拆成父子节点，读屏只剩描述、没有动作。
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                // 不设 selected：它会被映射成 checkable/checked，读屏会念成"复选框"；
                // 选中态已经写进 description 里了
                onClick(label = "查看当天账单") { onClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 子项统一 fillMaxWidth + 居中：只靠 Column 的 horizontalAlignment 时，
        // 金额宽度与日号不同会看着左右偏，这样才是严格的同一条中线
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = dayColor,
            )
            val amountColor = if (isSelected) Color.White.copy(alpha = 0.92f) else null
            if (expense > 0) {
                // 全局约定：消费红色（选中态保持白色，保证与选中底色对比）
                CellAmount(prefix = "支", cents = expense, color = amountColor ?: ExpenseAmount)
            }
            if (income > 0) {
                CellAmount(prefix = "收", cents = income, color = amountColor ?: IncomeGreen)
            }
        }
    }
}

/**
 * 格子里的金额行：带「支 / 收」单字前缀。
 *
 * 只有颜色和正负号时，两行数字叠在日期下很难分辨谁是谁；加一个字成本最低、
 * 也不依赖颜色（色盲可读）。9sp 下「支12.5」约 27dp，44dp 宽的格子放得下。
 */
@Composable
private fun CellAmount(prefix: String, cents: Long, color: Color) {
    Text(
        "$prefix${formatShortMoney(cents)}",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        color = color,
    )
}

/** 卡内的 0.5dp 分隔线：日历卡底部、日期卡表头、账单行之间共用一套参数 */
@Composable
internal fun CalendarDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    )
}

/** 只读信息胶囊：收/支/余共用一套观感，颜色承载语义（与统计页的 chip 同族） */
@Composable
internal fun SummaryPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.xxl))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.85f),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 短金额（格子内显示）：12.5 / 1234 / 1.2万。调用方已保证非负（只有 expense/income > 0 才渲染） */
private fun formatShortMoney(cents: Long): String = shortNumber(cents / 100.0)

private fun shortNumber(yuan: Double): String = when {
    yuan >= 10000 -> String.format(Locale.US, "%.1f万", yuan / 10000)
    yuan >= 1000 -> String.format(Locale.US, "%.0f", yuan)
    yuan >= 1 -> String.format(Locale.US, "%.1f", yuan).trimEnd('0').trimEnd('.')
    else -> String.format(Locale.US, "%.2f", yuan).trimEnd('0').trimEnd('.')
}

/** 星期文案：今天以外用在全天标题副文案里 */
internal fun weekdayLabel(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)
