package com.iji.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.DaySummary
import com.iji.app.MainViewModel
import com.iji.app.data.model.Transaction
import com.iji.app.ui.add.AddTransactionSheet
import com.iji.app.ui.components.BillDetailDialog
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.IjiTopBar
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.TransactionRow
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.topbar.rememberTopBarScrollBehavior
import com.iji.app.ui.components.topbar.topBarContentInset
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历页：月份卡（可横滑切月）+ 选中日汇总与账单。
 *
 * 与其它三页共用折叠顶栏，但标题固定为「日历」——月份归属交给日历卡头部，
 * 避免出现「顶栏一个大月份、卡内又一个小月份」的重复。
 */
@Composable
fun CalendarScreen(
    onNavigateMine: () -> Unit = {},
    viewModel: MainViewModel = viewModel(),
) {
    val monthSummaries by viewModel.monthSummaries.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    // 选中的日期，默认今天
    var selectedDate by remember { mutableStateOf(today) }
    // 切月后把选中日收敛到目标月（保持同一天号，越界取月末）。
    // 不做这一步，切月后日历上没有高亮、下方会误报「当天没有账单」。
    LaunchedEffect(selectedMonth) {
        if (YearMonth.from(selectedDate) != selectedMonth) {
            selectedDate = selectedMonth.atDay(
                selectedDate.dayOfMonth.coerceAtMost(selectedMonth.lengthOfMonth()),
            )
        }
    }
    // 只有一条要查（选中日），不必建整月索引
    val daySummary = remember(monthSummaries, selectedDate) {
        monthSummaries.firstOrNull { it.date == selectedDate }
    }

    // 点账单行先看只读详情（与账本页一致），编辑在详情里
    var detailTx by remember { mutableStateOf<Transaction?>(null) }
    // 编辑面板：与账本页同一套面板与回调
    var editTx by remember { mutableStateOf<Transaction?>(null) }
    // 「当天没有账单」空态里的「记一笔」：带上选中日期，避免记到别的日子
    var addDate by remember { mutableStateOf<LocalDate?>(null) }
    // 首帧未就绪时不给「当天没有账单」，否则冷启动会误报空态
    val transactionsReady by viewModel.transactionsReady.collectAsStateWithLifecycle()

    // ── 固定顶栏（浮层）：与其它三页一致的折叠横栏 ──
    val listState = rememberLazyListState()
    val topBarSurface = MaterialTheme.colorScheme.background
    val topBarBackdrop = rememberLayerBackdrop { drawRect(topBarSurface); drawContent() }
    // 顶栏采样层节流：滚动中才重录，静止不录、不重跑顶栏模糊 shader
    val topBarMustRecord = remember(listState) { { listState.isScrollInProgress } }
    val listScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val scrollBehavior = rememberTopBarScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = LocalDimens.current.screenPadding)
                .topBarContentInset(scrollBehavior)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .then(
                    if (listScrolled) {
                        Modifier.layerBackdrop(topBarBackdrop, recordKey = Unit, mustRecord = topBarMustRecord)
                    } else {
                        Modifier
                    }
                ),
            // top 额外 +12dp：折叠顶栏的大标题与第一张卡之间要留一口气
            // bottom 96dp：与统计页一致，给悬浮底栏让位，末条账单不会被遮住
            contentPadding = PaddingValues(
                top = ijiTopBarContentPadding() + 12.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "calendar") {
                CalendarCard(
                    month = selectedMonth,
                    summaries = monthSummaries,
                    selectedDate = selectedDate,
                    today = today,
                    ready = transactionsReady,
                    onSelectDate = { selectedDate = it },
                    onMonthChange = { viewModel.setMonth(it) },
                )
            }

            item(key = "day") {
                DayBillsCard(
                    date = selectedDate,
                    today = today,
                    summary = daySummary,
                    ready = transactionsReady,
                    onBillClick = { detailTx = it },
                    onAddBill = { addDate = selectedDate },
                )
            }
        }

        IjiTopBar(
            backdrop = topBarBackdrop,
            scrollBehavior = scrollBehavior,
            onMineClick = onNavigateMine,
            title = "日历",
            largeTitle = "日历",
            endAction = { _, _ ->
                IjiTextAction(
                    text = "今天",
                    onClick = {
                        selectedDate = LocalDate.now()
                        viewModel.backToThisMonth()
                    },
                    compact = true,
                )
            },
        )
    }

    // 只读详情：点日期卡里的账单行先看这里
    BillDetailDialog(
        tx = detailTx,
        onDismiss = { detailTx = null },
        onEdit = {
            detailTx = null
            editTx = it
        },
    )

    // 编辑账单面板：从详情里的「编辑」打开（与账本页同一个面板、同一套更新逻辑）
    AddTransactionSheet(
        show = editTx != null,
        existing = editTx,
        onDismiss = { editTx = null },
        onConfirm = { draft ->
            editTx?.let { tx ->
                viewModel.updateTransaction(
                    tx, draft.type, draft.categoryId, draft.amountCents, draft.note,
                    draft.account, draft.tags, draft.date, draft.excludeFromStats,
                    draft.excludeFromBudget, draft.reimburse, draft.discountCents, draft.attachments,
                    draft.merchant, draft.counterparty, draft.counterpartyContact, draft.counterpartyOrg,
                    draft.reimburseState, draft.reconciled,
                    ledgerId = draft.ledgerId, transferToAccount = draft.transferToAccount,
                    splits = draft.splits, refundOfId = draft.refundOfId,
                    minuteOfDay = draft.minuteOfDay,
                )
            }
            editTx = null
        },
    )

    // 新增：空态里的「记一笔」把选中日期带进面板（onConfirm 为 null 时面板自己写库）
    AddTransactionSheet(
        show = addDate != null,
        onDismiss = { addDate = null },
        prefillDate = addDate,
    )
}

/** 选中日：日期标题 + 收/支/余胶囊 + 当天账单（空则给空态） */
@Composable
private fun DayBillsCard(
    date: LocalDate,
    today: LocalDate,
    summary: DaySummary?,
    ready: Boolean,
    onBillClick: (Transaction) -> Unit,
    onAddBill: () -> Unit,
) {
    val income = summary?.incomeCents ?: 0L
    val expense = summary?.expenseCents ?: 0L
    val bills = summary?.transactions.orEmpty()
    val weekday = weekdayLabel(date)

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.sheet,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (date == today) "今天" else "${date.monthValue}月${date.dayOfMonth}日",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (bills.isEmpty()) weekday else "$weekday · ${bills.size} 笔",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 首帧未就绪时用占位符，避免瞬间显示收/支/余都是 0
                    val money = { cents: Long -> if (ready) formatMoney(cents) else "—" }
                    SummaryPill("收", money(income), IncomeGreen)
                    SummaryPill("支", money(expense), MaterialTheme.colorScheme.primary)
                    SummaryPill("余", money(income - expense), TextSecondary)
                }
            }

            if (!ready) {
                StateView(state = ContentState.Loading("加载中"), areaVerticalPadding = 18.dp)
            } else if (bills.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StateView(
                        state = ContentState.Empty("当天没有账单"),
                        areaVerticalPadding = 14.dp,
                    )
                    IjiTextAction(text = "记一笔", onClick = onAddBill, compact = true)
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                CalendarDivider()
                bills.forEachIndexed { index, tx ->
                    if (index > 0) CalendarDivider()
                    // subtitle 留空 = 交给 TransactionRow 自己渲染「时间 · 备注」+ 标签/优惠胶囊；
                    // 账户名改到金额下方（与账本页同款），点行进入编辑面板
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBillClick(tx) },
                    ) {
                        TransactionRow(
                            tx = tx,
                            // 日历页分类标题用中等字重（其余页面维持 SemiBold）
                            titleFontWeight = FontWeight.Medium,
                            showAccountUnderAmount = true,
                        )
                    }
                }
            }
        }
    }
}
