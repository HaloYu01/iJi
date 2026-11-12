package com.iji.app.ui.home

import com.iji.app.data.repository.SettingsRepository
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import android.content.Context
import androidx.compose.ui.layout.positionInWindow
import com.iji.app.ui.components.menu.LiquidGlassDropdownMenu
import com.iji.app.ui.components.menu.LiquidGlassDropdownMenuItem
import com.iji.app.ui.components.menu.LiquidGlassDropdownOverlayHost
import com.iji.app.ui.effects.glass.LocalAppBackdrop
import org.json.JSONArray

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import com.iji.app.ui.components.IjiTopBar
import com.iji.app.ui.components.ijiIconFocusRing
import com.iji.app.ui.components.ijiPrimaryButtonColors
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.ijiTopBarRecordHeight
import com.iji.app.ui.components.topbar.LiquidTopBarButton
import com.iji.app.ui.components.topbar.LiquidTopBarButtonSize
import com.iji.app.ui.components.topbar.topBarContentInset
import com.iji.app.ui.components.topbar.rememberTopBarScrollBehavior
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiTextAction
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.DaySummary
import com.iji.app.MainViewModel
import com.iji.app.data.model.BudgetAlertLevel
import com.iji.app.data.model.BudgetProgress
import com.iji.app.data.model.Currency
import com.iji.app.data.model.CurrencyDisplay
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.budget.BudgetProgressBar
import com.iji.app.ui.budget.NearLimitColor
import com.iji.app.ui.components.TransactionRow

import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.ExpenseAmount
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.minTouchTarget
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.ApplePurple
import com.iji.app.ui.theme.AppleSeparator
import com.iji.app.ui.theme.AppleSecondaryBackground
import com.iji.app.ui.theme.DividerGray
import com.iji.app.ui.theme.AppleTertiaryBackground
import com.iji.app.ui.theme.TextSecondary
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import com.iji.app.DeleteBillsOutcome
import com.iji.app.ui.components.BillDetailDialog
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.util.saveToDownloads
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TextField
import com.iji.app.ui.util.sharedMainViewModel

/** 列表一页渲染多少天：首屏只组合一页，滚到底后再加载 */
private const val DAYS_PER_PAGE = 20

/** 账本页（首页） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateMine: () -> Unit = {},
    /** 账本管理：主页账本下拉菜单末尾的入口 */
    onNavigateLedgerManage: () -> Unit = {},
    app: MainViewModel = sharedMainViewModel(),
    viewModel: HomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(app) as T
        },
    ),
) {
    // 单一状态源：列表数据与「是否已加载」同一次发射到达，不可能出现
    // ready 先到、days 还是初始空列表 → 先渲染「无账单」再跳账单
    val monthState by viewModel.monthUiState.collectAsStateWithLifecycle()
    val days = monthState.summaries
    val expenseCents = monthState.expenseCents
    val incomeCents = monthState.incomeCents
    val budgetExpenseCents = monthState.budgetExpenseCents
    val monthBudgetProgress by viewModel.monthBudgetProgress.collectAsStateWithLifecycle()
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val ledger by viewModel.currentLedger.collectAsStateWithLifecycle()
    // 首帧是否就绪由 monthState.loaded 判定，与列表数据同帧到达
    val ready = monthState.loaded
    // 预算值在屏幕级收集一次：汇总卡和预算弹窗共用，避免同一个 Flow 订阅两遍
    val currentBudget by viewModel.monthlyBudgetCents.collectAsStateWithLifecycle()
    // 读库失败：不能伪装成「没有账单」
    val txError by viewModel.transactionsError.collectAsStateWithLifecycle()

    // 搜索 + 高级筛选（范围 / 类型 / 金额区间 / 日期区间 / 分类），统一放在一个条件对象里。
    // saveable：filter 决定列表内容，tab 切走再回来（含进程重建）不能丢，
    // 否则恢复的滚动位置对应的就是另一批数据。
    var filter by rememberSaveable(stateSaver = SearchFilterSaver) { mutableStateOf(SearchFilter()) }
    // 输入即时回显，过滤走 250ms 防抖：每敲一个字都重算整本账本是上一版卡顿的根因
    var queryText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(queryText) {
        if (queryText == filter.query) return@LaunchedEffect
        delay(250)
        filter = filter.copy(query = queryText)
    }
    // 搜索框默认收起：首屏多出一行账单；有生效条件时自动展开（否则摘要无从显示）。
    // 刻意不持久化：带筛选切走再回来，searchVisible 靠 filter.isActive 仍会展开；
    // 只为「空开着」而恢复它，还会连带自动唤起键盘，得不偿失。
    var searchOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            delay(150)
            runCatching { searchFocus.requestFocus() }
        }
    }
    var showFilterDialog by remember { mutableStateOf(false) }
    var ledgerMenuOpen by remember { mutableStateOf(false) }
    // 账本下拉面板锚点（占位图标左上角 root 坐标）与展开 fraction（1:1 控件要求外部持有）
    val ledgerMenuAnchor = remember { mutableStateOf(Rect.Zero) }
    val menuHostOrigin = remember { mutableStateOf(Offset.Zero) }
    val ledgerMenuFraction = remember { Animatable(0f) }
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }
    var editTx by remember { mutableStateOf<Transaction?>(null) }
    // 点行先看只读详情，编辑由详情里的按钮触发（避免手滑直接进编辑面板）
    var detailTx by remember { mutableStateOf<Transaction?>(null) }
    // 「记退款」入口选中的原账单；非 null 时打开退款面板
    var refundSource by remember { mutableStateOf<Transaction?>(null) }
    // 附件查看器：账单 + 从第几张开始看
    var viewingAttachments by remember { mutableStateOf<Pair<Transaction, Int>?>(null) }
    // 报销列筛选：仅看标记报销的账单（列表内容的一部分，随 Navigation 状态保存）
    var reimburseOnly by rememberSaveable { mutableStateOf(false) }
    // 多选模式（模态批量会话：切 tab 回来刻意不恢复，避免拿着过期选中集误批量操作）
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    // 批量操作的二次确认 / 分类选择
    var bulkDeleteConfirm by remember { mutableStateOf(false) }
    var bulkCategoryDialog by remember { mutableStateOf(false) }
    // 滑动刷选：行边界（窗口坐标）+ 列表坐标；只在多选模式下行才写入，避免平时给滚动加开销
    val rowBounds = remember { mutableMapOf<Long, Rect>() }
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val snackbar = LocalSnackbarController.current
    val context = LocalContext.current
    // 折叠状态：`账本|月|日期` -> 是否展开（默认展开）；带账本和月份，切走再切回还是原样。
    // 只把「收起」的 key 落盘，重启后仍是原样。
    val expandedDays = remember {
        mutableStateMapOf<String, Boolean>().apply {
            DayCollapseStore.load(context).forEach { put(it, false) }
        }
    }
    // 当前滑出操作区的账单卡片（点击账单以外区域时回弹复位）；随页面状态保存，切 tab 回来不收起
    var openedCardId by rememberSaveable { mutableStateOf<Long?>(null) }
    // 点击「编辑/复制/删除」胶囊按钮时置位，阻止容器级 detectTapGestures 把已开卡片回弹关闭
    val suppressCardClose = remember { mutableStateOf(false) }

    // ── 退款溯源 ──
    // 用**全量**账单而非本月汇总：退款可能落在别的月份，原账单也可能不在本月，
    // 只看本月会让跨月退款的关联凭空消失。索引由 ViewModel 在 Default 线程算一次。
    val allTx by viewModel.transactions.collectAsStateWithLifecycle()
    val refundView by viewModel.refundView.collectAsStateWithLifecycle()
    val txById = refundView.byId
    /** 原账单 id → 已退金额（分，正数，可能由多笔退款累加） */
    val refundedBySource = refundView.refundedBySource

    /** 账单行下方的退款说明；无关账单为空串 */
    fun refundNoteOf(tx: Transaction): String = when {
        // 退款行：反查它退的是哪一笔
        tx.refundOfId != 0L -> txById[tx.refundOfId]?.let { src ->
            val name = runCatching { CategoryCatalog.of(src.categoryId).name }
                .getOrDefault(src.categoryId)
            "退款自「$name ${formatMoney(src.amountCents)}」"
        }.orEmpty()
        // 原账单：显示已退与净成本
        (refundedBySource[tx.id] ?: 0L) > 0L -> {
            val refunded = refundedBySource.getValue(tx.id)
            "已退 ${formatMoney(refunded)} · 净 ${formatMoney(tx.amountCents - refunded)}"
        }
        else -> ""
    }

    /**
     * 列表数据源（两层缓存）：
     * 1. [scopeDays]：范围（本月 / 全部时间）决定的底表 —— 只有换范围才重新按天分组；
     * 2. [filteredDays]：在底表上逐笔套条件 —— 关键词变化只走这一层，不再重跑 groupBy。
     */
    val scopeDays = remember(filter.scope, days, allTx) {
        if (filter.scope == SearchScope.MONTH) {
            days
        } else {
            allTx.groupBy { it.date }
                .entries
                .sortedByDescending { it.key }
                .map { (date, txs) ->
                    DaySummary(
                        date = date,
                        transactions = txs.sortedByDescending { it.id },
                        expenseCents = txs
                            .filter { it.type == TransactionType.EXPENSE && !it.excludeFromStats }
                            .sumOf { it.amountCents },
                        incomeCents = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
                    )
                }
        }
    }
    val filteredDays = remember(scopeDays, filter, reimburseOnly) {
        scopeDays.mapNotNull { day ->
            val matched = day.transactions.filter { tx ->
                tx.matchesFilter(filter) && (!reimburseOnly || tx.reimburse)
            }
            if (matched.isEmpty()) {
                null
            } else {
                day.copy(
                    transactions = matched,
                    // 与 monthUiState 同口径：剔除「不计入收支」，否则日头相加 ≠ 汇总卡
                    expenseCents = matched
                        .filter { it.type == TransactionType.EXPENSE && !it.excludeFromStats }
                        .sumOf { it.amountCents },
                    incomeCents = matched.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
                )
            }
        }
    }

    // ── 概览派生数据（预算行）──
    // 卡片的预算数值（剩余 / 进度）始终基于「当前周期」，因此日均预算 / 今日剩余
    // 只在浏览当前月时展示，翻历史月份不给错位数据。
    val isCurrentMonth = month == java.time.YearMonth.now()
    // 日均预算 = **剩余预算 ÷ 剩余天数**（动态日均）：花得多，后面每天能用的就变少。
    // 剩余预算与卡片「预算剩余」同一数据源（含结转，超支为负）；剩余天数**含今天**——
    // 「今日剩余 = 日均 − 今日支出」里的今天也在日均口径里，两边才闭合。
    // 超支时剩余为负，日均钳到 0（红卡 + 已超支已经表达状态，不再显示负数日均）。
    val dailyBudgetCents = if (isCurrentMonth) {
        val remainingCents = monthBudgetProgress?.remainingCents
            ?: (currentBudget - budgetExpenseCents).takeIf { currentBudget > 0L }
        val remainingDays = month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
        if (remainingCents != null && remainingCents > 0L && remainingDays > 0) {
            remainingCents / remainingDays
        } else {
            0L
        }
    } else {
        0L
    }
    // 今日支出（与预算同一口径：剔除「不计入预算」）。今天必在本月汇总里，
    // 不必再扫一遍全量账单；浏览历史月份时 dailyBudgetCents 为 0，此处取 0 即可。
    val todayExpenseCents = remember(days) {
        val today = LocalDate.now()
        days.firstOrNull { it.date == today }?.transactions
            ?.filter { it.type == TransactionType.EXPENSE && !it.excludeFromBudget }
            ?.sumOf { it.amountCents } ?: 0L
    }
    // 今日剩余 = 日均预算 − 今日支出；可为负（今日已超）
    val todayRemainingCents = if (dailyBudgetCents > 0L) dailyBudgetCents - todayExpenseCents else null

    // ── 多选派生数据 ──
    // 用 allTx 而不是当前筛选结果：筛选条件变化不该让已选中的账单"凭空消失"
    val selectedTxs by remember { derivedStateOf { allTx.filter { it.id in selectedIds } } }
    val selectedExpenseCents = selectedTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
    val selectedIncomeCents = selectedTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
    val selectedTypes = selectedTxs.map { it.type }.toSet()
    // 全选只作用于当前可见列表
    val visibleIds = remember(filteredDays) { filteredDays.flatMap { day -> day.transactions.map { it.id } } }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }

    fun exitMultiSelect() {
        multiSelectMode = false
        selectedIds.clear()
        openedCardId = null
    }

    fun toggleSelection(id: Long) {
        if (!selectedIds.remove(id)) selectedIds.add(id)
    }

    // 返回键：多选状态下只退出多选（与「完成」同一入口：清空已选 → 收起操作栏 →
    // 行高亮消失 → 内容留白恢复 96dp），不离开首页、不退出应用。
    // 弹层打开时本回调自动让位给弹层（优先级见 MultiSelectBackHandler 注释）；
    // 普通状态下不注册回调，退出逻辑（NavHost 出栈 / 系统退出）保持原样。
    MultiSelectBackHandler(active = multiSelectMode, onExit = ::exitMultiSelect)

    // ── 固定顶栏：浮在列表之上，滚动时不动（内容从横栏下方开始滚过去）──
    val listState = rememberLazyListState()
    val topBarSurface = MaterialTheme.colorScheme.background
    val topBarBackdrop = rememberLayerBackdrop { drawRect(topBarSurface); drawContent() }
    // 顶栏采样层节流：滚动中隔帧重录 + 停帧补录（BackdropRecordGate）。滚动帧整屏
    // 重录（LazyColumn 全屏 GPU 层）是首页滑动最重的单项开销之一，玻璃模糊半帧率
    // 刷新肉眼无感，成本减半；isScrollInProgress true→false 的写入会 invalidate
    // 订阅它的 draw（mustRecord 在 draw 期读），天然产生停帧补录的那次咨询。
    // mustRecord lambda 必须 remember 稳定，否则每次重组 update→markNeedsRecord
    // 退化成每帧录。
    val topBarRecordGate = remember { com.iji.app.ui.effects.glass.BackdropRecordGate() }
    val topBarMustRecord = remember(listState) { { topBarRecordGate.shouldRecord(listState.isScrollInProgress) } }
    // 采样层只在列表已滚动时才挂：未滚动时横栏下方就是页面底色，没有可模糊的内容，
    // 此时不挂可省掉每帧一次全屏录制（Miuix 的 layerBackdrop 无 recordKey 门控）。
    val listScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val scrollBehavior = rememberTopBarScrollBehavior()

    // 分页：按天渐进加载。SQL 已按月取数，这一层是给「全部时间」兜住几百天的渲染量；
    // 用 saveable 保存，tab 切走再回来不丢分页深度。
    var visibleDays by rememberSaveable { mutableStateOf(DAYS_PER_PAGE) }

    // 列表内容标识：month / ledger / 筛选 / 报销任一变化 = 展示的是新一批数据，
    // 查看位置回顶、顶栏展开复位、分页收回首页天数。
    //
    // ⚠️ 这是「切 tab 回来就置顶」bug 的根修点：LaunchedEffect 在**每次进入组合**时都会执行
    // （键没变也拦不住），而 tab 切走再回来是全新组合——无条件 scrollToItem(0) 会把
    // 刚恢复的滚动位置打到顶部。所以复位只认「内容标识真的变了」：标识与滚动位置、
    // 分页深度一样走 Navigation 状态保存，切页往返、进程重建后两边对得上，不会误触发。
    val contentKey = "$month|$ledger|$filter|$reimburseOnly"
    var consumedContentKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(contentKey) {
        if (contentKey != consumedContentKey) {
            scrollBehavior.state.heightOffset = 0f
            listState.scrollToItem(0)
            visibleDays = DAYS_PER_PAGE
            consumedContentKey = contentKey
        }
    }

    // 搜索框是插在列表头部的 item（key = "search"）。列表带着滚动位置打开时，LazyColumn
    // 会锚住原第一项、把搜索框排进顶栏下方的让位区 —— 视觉上就是「搜索框卡在顶栏阴影底部、
    // 只露出一条」。打开瞬间（含筛选弹窗应用后框自动展开）先复位到列表顶部，并把顶栏折叠
    // 偏移归零（scrollToItem 是直接重排、不走嵌套滚动派发，不会自己把大标题展开回来），
    // 让搜索框稳定落在展开态顶栏的正下方。
    // 同样的回页重跑问题：带着已展开的搜索框（激活的筛选）切 tab 回来不该再复位——
    // saveable 消费标记只认「这次组合里 searchVisible 从无到有」。
    val searchVisible = searchOpen || filter.isActive
    var searchResetConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searchVisible) {
        if (searchVisible && !searchResetConsumed) {
            scrollBehavior.state.heightOffset = 0f
            listState.scrollToItem(0)
            searchResetConsumed = true
        } else if (!searchVisible) {
            searchResetConsumed = false
        }
    }

    val pagedDays = remember(filteredDays, visibleDays) { filteredDays.take(visibleDays) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            // 平板 / 折叠屏：内容限宽居中，账单行不再横跨 800dp（手机上 widthIn 不生效）
            .fillMaxHeight()
            .widthIn(max = 640.dp)
            .align(Alignment.TopCenter)
            .background(MaterialTheme.colorScheme.background)
            .topBarContentInset(scrollBehavior)
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .then(if (listScrolled) Modifier.layerBackdrop(topBarBackdrop, recordKey = Unit, mustRecord = topBarMustRecord, recordHeight = ijiTopBarRecordHeight()) else Modifier)
            .onGloballyPositioned { listCoordinates = it }
            // 多选模式下的长按刷选（单击仍由卡片自己的手势切换选中）
            .pointerInput(Unit) {
                billPaintGesture(
                    multiSelectMode = { multiSelectMode },
                    rowBounds = rowBounds,
                    listCoordinates = { listCoordinates },
                    listState = listState,
                    isSelected = { it in selectedIds },
                    applySelection = { id, select ->
                        if (select) {
                            if (id !in selectedIds) selectedIds.add(id)
                        } else {
                            selectedIds.remove(id)
                        }
                    },
                )
            }
            // 点击账单以外（未被消费的空白区域）时，滑出的操作区回弹复位；
            // 但点中「编辑/复制/删除」按钮时 suppressCardClose 已置位，需跳过以免误关
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { suppressCardClose.value = false },
                    onTap = {
                        if (suppressCardClose.value) {
                            suppressCardClose.value = false
                        } else {
                            openedCardId = null
                        }
                    },
                )
            },
        // 多选时底部多出一条操作栏，留白跟着加大，避免最后几行被压住
        contentPadding = PaddingValues(
            top = ijiTopBarContentPadding(),
            bottom = if (multiSelectMode) 208.dp else 96.dp,
        ),
    ) {
        // 搜索框常驻挂载（不可见时高度为 0）：挂载/卸载由 if 控制的话，收起瞬间 item
        // 直接从组合里移除，退出动画来不及播就消失了。显隐全权交给 AnimatedVisibility。
        item(key = "search") {
            // 弹出/收起动画与当日账单区同一套弹簧（高度展开 + 淡入）：从展开态顶栏
            // 下沿「垂下来」，而不是凭空闪现。visible 跟随 searchVisible（与顶栏复位
            // 逻辑同一个信号），filter.isActive 时保持显示（摘要行常驻）
            AnimatedVisibility(
                visible = searchVisible,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 480f),
                ) + fadeIn(animationSpec = spring(dampingRatio = 0.8f)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 480f),
                ) + fadeOut(animationSpec = spring(dampingRatio = 0.8f)),
            ) {
                SearchField(
                    query = queryText,
                    onQueryChange = { queryText = it },
                    onOpenFilter = { showFilterDialog = true },
                    filterActive = filter.isActive,
                    summary = filter.summary(),
                    focusRequester = searchFocus,
                    // 摘要行的「清除」= 清空全部条件（含关键词），字段内的小 × 只清关键词
                    onClearFilter = {
                        queryText = ""
                        filter = SearchFilter()
                        // 条件全清了，搜索框也收起来，把那一行还给列表
                        searchOpen = false
                    },
                )
            }
        }
        item(key = "month") {
            MonthSelector(
                month = month,
                onPrev = { viewModel.prevMonth() },
                onNext = { viewModel.nextMonth() },
                // 范围切到「全部时间」后月份不参与过滤：翻月箭头置灰，避免点了没反应
                monthRelevant = filter.scope == SearchScope.MONTH,
                // 「报销列」并入月份行右侧：原来单独占一行，纵向空间太浪费
                // 顶栏的「返回本月」也挪到这里，翻月后才出现，与筛选胶囊并排同高
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (month != java.time.YearMonth.now()) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .clip(CircleShape)
                                    .background(AppleSecondaryBackground)
                                    .clickable { viewModel.backToThisMonth() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    MiuixIcons.ChevronBackward,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("返回本月", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        ReimburseChip(
                            active = reimburseOnly,
                            onClick = { reimburseOnly = !reimburseOnly },
                        )
                    }
                },
            )
        }
        item(key = "summary") {
            SummaryCard4(
                expenseCents = expenseCents,
                incomeCents = incomeCents,
                budgetCents = currentBudget,
                budgetExpenseCents = budgetExpenseCents,
                budgetProgress = monthBudgetProgress,
                dailyBudgetCents = dailyBudgetCents,
                todayRemainingCents = todayRemainingCents,
            )
        }

        // 读取失败：给出原因和重试，而不是渲染成空列表
        if (txError != null) {
            item(key = "load-error") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalDimens.current.screenPadding, vertical = 4.dp)
                        .clip(RoundedCornerShape(Radius.xl))
                        .background(AppleRed.copy(alpha = 0.1f))
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "账单读取失败：$txError",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IjiTextAction(
                        text = "重试",
                        onClick = { viewModel.retryLoadTransactions() },
                        compact = true,
                    )
                }
            }
        }

        // 加载中：先给骨架行，不让「还没有账单」先闪一下
        if (!ready) {
            item(key = "skeleton") { BillListSkeleton() }
        } else if (txError == null && filteredDays.isEmpty()) {
            item(key = "empty") {
                BillsEmptyState(
                    // 「报销列」也是过滤条件：否则清空列表却说「本月还没有账单」
                    kind = when {
                        filter.isActive || reimburseOnly -> EmptyKind.FILTERED
                        allTx.isEmpty() -> EmptyKind.NO_BILL_AT_ALL
                        else -> EmptyKind.NO_BILL_THIS_MONTH
                    },
                    filterSummary = listOfNotNull(
                        filter.summary().takeIf { it.isNotBlank() },
                        "报销列".takeIf { reimburseOnly },
                    ).joinToString(" · "),
                    // 「清除筛选」要把快捷开关也复位，否则点了还是空
                    onClearFilter = {
                        queryText = ""
                        filter = SearchFilter()
                        reimburseOnly = false
                    },
                    onPrevMonth = { viewModel.prevMonth() },
                )
            }
        }

        pagedDays.forEach { day ->
            val collapseKey = "$ledger|$month|${day.date}"
            val expanded = expandedDays[collapseKey] ?: true
            // 日期头吸顶：长列表滚动时始终能看到"这是哪天"
            stickyHeader(key = "day-${day.date}") {
                DayHeaderV2(
                    day = day,
                    expanded = expanded,
                    onToggle = {
                        expandedDays[collapseKey] = !expanded
                        DayCollapseStore.save(context, expandedDays.filterValues { !it }.keys)
                    },
                )
            }
            // 当日账单区：展开/收起走 Miuix 弹簧过渡（高度收缩 + 淡入淡出）
            item(key = "day-${day.date}-txs") {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 480f),
                    ) + fadeIn(animationSpec = spring(dampingRatio = 0.8f)),
                    exit = shrinkVertically(
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 480f),
                    ) + fadeOut(animationSpec = spring(dampingRatio = 0.8f)),
                ) {
                    // 同一天的账单包进**一张**圆角卡：卡内 0 间距、行间用极细分割线贯穿，
                    // 不同日期的组之间靠日期头 + 留白分隔（对照目标样式，替代原先一笔一张卡）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LocalDimens.current.screenPadding, vertical = 4.dp)
                            .clip(RoundedCornerShape(Radius.sheet))
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        day.transactions.forEachIndexed { index, tx ->
                            if (index > 0) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = DividerGray.copy(alpha = 0.6f),
                                )
                            }
                            TransactionCard(
                                tx = tx,
                                grouped = true,
                                // 上报行边界供刷选命中判断。**不能只在多选时写**：
                                // 进入多选不会让行的布局发生变化，onGloballyPositioned 也就不再回调，
                                // 结果第一次长按拖动时命中表还是空的（已踩过）。纯 map 写入无重组开销。
                                modifier = Modifier.onGloballyPositioned {
                                    rowBounds[tx.id] = it.boundsInWindow()
                                },
                                isSelected = selectedIds.contains(tx.id),
                                multiSelectMode = multiSelectMode,
                                openCardId = openedCardId,
                                onOpenChange = { open ->
                                    openedCardId = if (open) tx.id else null
                                },
                                onClick = {
                                    if (multiSelectMode) {
                                        toggleSelection(tx.id)
                                    } else {
                                        detailTx = tx
                                    }
                                },
                                onLongClick = {
                                    // 长按进入多选：先清空残留，再选中当前这笔
                                    if (!multiSelectMode) {
                                        multiSelectMode = true
                                        openedCardId = null
                                        selectedIds.clear()
                                        selectedIds.add(tx.id)
                                    }
                                },
                                onDelete = { pendingDelete = tx },
                                // 记退款：只对「正向支出」开放（退款自己、转账、收入都没有「再退一次」的语义）
                                onRefund = if (tx.type == TransactionType.EXPENSE && tx.amountCents > 0L) {
                                    { refundSource = tx }
                                } else {
                                    null
                                },
                                refundNote = refundNoteOf(tx),
                                // 附件：列表里只看得见缩略图，点开是大图查看器
                                onOpenAttachments = if (tx.attachments.isNotEmpty()) {
                                    { index -> viewingAttachments = tx to index }
                                } else {
                                    null
                                },
                                suppressCardClose = suppressCardClose,
                                onCopy = {
                                    viewModel.addTransaction(
                                        type = tx.type,
                                        categoryId = tx.categoryId,
                                        amountCents = tx.amountCents,
                                        note = tx.note,
                                        account = tx.account,
                                        tags = tx.tags,
                                        date = tx.date,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // 还有更多天：一次性只渲染一页，避免「全部时间」把几百天全塞进组合
        if (ready && filteredDays.size > pagedDays.size) {
            item(key = "load-more") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = "加载更多（还有 ${filteredDays.size - pagedDays.size} 天）",
                        onClick = { visibleDays += DAYS_PER_PAGE },
                        cornerRadius = Radius.xxl,
                    )
                }
            }
        }
    }

        // 固定顶栏（浮层，最后绘制；滚动时不动，内容从它下方滑过并被渐进式模糊柔化）
        IjiTopBar(
            backdrop = topBarBackdrop,
            title = ledger,
            // 账本名默认 32sp 太重，压到 20sp（收起态小标题按 0.85 倍同步走）
            largeTitleFontSize = 20.sp,
            scrollBehavior = scrollBehavior,
            startAction = { backdropAlpha, shadowAlpha ->
                // 账本切换下拉（NexioSchedule LiquidGlassDropdownMenu；触发器在左缘，面板左对齐）。
                // 占位槽只负责定位与点击，玻璃圆钮与面板由页面根的 overlay 层绘制（不占顶栏布局）。
                // 收起态玻璃圆钮 = 本组件（采样 topBarBackdrop，页面内无自捕获先例）；
                // 面板展开时在 popup 层从本占位位置长出（onGloballyPositioned 只做定位上报）
                LiquidTopBarButton(
                    // 账本切换用「本子」图标：Notes 语义就是账本，与搜索/我的同尺寸（Action 22dp）
                    icon = MiuixIcons.Notes,
                    contentDescription = "切换账本",
                    iconSize = com.iji.app.ui.theme.IjiIconSize.Action,
                    onClick = { ledgerMenuOpen = true },
                    backdrop = topBarBackdrop,
                    backdropAlpha = backdropAlpha,
                    shadowAlpha = shadowAlpha,
                    iconModifier = Modifier.graphicsLayer {
                        // 一镜到底（NexioSchedule）：面板从本钮长出，图标随 fraction 向面板
                        // 内部（右下）漂移并提前淡完（k=2.5）；收起以更小系数更早浮现（k=1.7）
                        val iconK = if (ledgerMenuOpen) 2.5f else 1.7f
                        val p = (ledgerMenuFraction.value * iconK).coerceIn(0f, 1f)
                        alpha = 1f - p
                        translationX = p * 10.dp.toPx()
                        translationY = p * 10.dp.toPx()
                    },
                    modifier = Modifier.onGloballyPositioned { ledgerMenuAnchor.value = it.boundsInWindow() },
                )
            },
            onMineClick = onNavigateMine,
            endAction = { backdropAlpha, shadowAlpha ->
                // 搜索从常驻一行改为顶栏入口：点开才占列表空间。
                // 有条件生效时不能收起（摘要行要一直在），此时入口只负责聚焦
                val canCollapse = searchOpen && !filter.isActive && queryText.isBlank()
                LiquidTopBarButton(
                    icon = MiuixIcons.Search,
                    contentDescription = if (canCollapse) "收起搜索" else "搜索",
                    onClick = {
                        if (canCollapse) {
                            searchOpen = false
                            focusManager.clearFocus()
                        } else {
                            searchOpen = true
                            runCatching { searchFocus.requestFocus() }
                        }
                    },
                    backdrop = topBarBackdrop,
                    backdropAlpha = backdropAlpha,
                    shadowAlpha = shadowAlpha,
                )
            },
        )

        // 账本切换下拉 overlay（NexioSchedule LiquidGlassDropdownMenu）：面板长在全屏覆盖层，
        // graphicsLayer 平移到顶栏圆钮位置（控件自带 24dp 阴影内缩，对齐 44dp 占位中心）。
        // 放在 IjiTopBar 之后：面板展开时盖过顶栏其余部分，与上游 chrome 层叠放一致。
        val ledgerBackdrop = LocalAppBackdrop.current
        if (ledgerBackdrop != null) {
            // 宿主必须是 popup 层（LiquidGlassDropdownOverlayHost，内走 Miuix DialogLayout）：
            // 放 NavHost 内会自捕获（drawBackdrop 节点被 layerBackdrop 录进采样图）
            // → RenderNode 递归 SIGSEGV
            LiquidGlassDropdownOverlayHost(expanded = ledgerMenuOpen, onOutsideTap = { ledgerMenuOpen = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { menuHostOrigin.value = it.positionInWindow() },
                ) {
                    LiquidGlassDropdownMenu(
                        show = ledgerMenuOpen,
                        backdrop = ledgerBackdrop,
                        fraction = ledgerMenuFraction,
                        onDismiss = { ledgerMenuOpen = false },
                        // 三颗按钮字形必须同尺寸（Action 22dp），Notes 语义就是账本
                        panelAlignStart = true,
                        modifier = Modifier
                            .graphicsLayer {
                                // 覆盖式（Start）：控制盒左上角 = 锚点左上角 − 26dp
                                //（玻璃左缘 = 锚左 − 2dp、顶 = 锚顶 − 2dp）——面板从账本圆钮
                                // 位置向右下生长覆盖触发器，收起缩回圆钮与图标衔接。
                                // 锚点用窗口坐标 − 宿主原点（popup 层与页面坐标系可能差状态栏高度）
                                val b = ledgerMenuAnchor.value
                                translationX = b.left - 26.dp.toPx() - menuHostOrigin.value.x
                                translationY = b.top - 26.dp.toPx() - menuHostOrigin.value.y
                            },
                    ) {
                        viewModel.ledgers.forEach { name ->
                            val currency = Currency.of(
                                viewModel.ledgerInfos.firstOrNull { it.name == name }?.currency,
                            )
                            LiquidGlassDropdownMenuItem(
                                text = name +
                                    ("　${currency.symbol} ${currency.code}"
                                        .takeIf { currency != CurrencyDisplay.current } ?: ""),
                                selected = name == ledger,
                                onClick = {
                                    ledgerMenuOpen = false
                                    viewModel.switchLedger(name)
                                },
                            )
                        }
                        LiquidGlassDropdownMenuItem(
                            text = "账本管理…",
                            onClick = {
                                ledgerMenuOpen = false
                                onNavigateLedgerManage()
                            },
                        )
                    }
                }
            }
        }

        // 多选操作栏：与列表同级（根 Box 内）固定贴底，让出悬浮 tab 栏的高度 —— 既不随列表滚动，
        // 也不会和底部导航叠在一起（旧实现放在根 Box 之外，会把内容区挤成空白）。
        if (multiSelectMode) {
            BillMultiSelectBar(
                selectedCount = selectedIds.size,
                expenseCents = selectedExpenseCents,
                incomeCents = selectedIncomeCents,
                allSelected = allVisibleSelected,
                canSelectAll = visibleIds.isNotEmpty(),
                onToggleSelectAll = {
                    if (allVisibleSelected) {
                        selectedIds.clear()
                    } else {
                        selectedIds.clear()
                        selectedIds.addAll(visibleIds)
                    }
                },
                onClearSelection = { selectedIds.clear() },
                onExit = { exitMultiSelect() },
                onDelete = { bulkDeleteConfirm = true },
                onCategory = {
                    // 混选时支出/收入分类不通用，先提示，避免"只改了一半"
                    if (selectedTypes.size > 1) {
                        snackbar.show("批量改分类仅支持同一类型，请先取消另一种类型的账单")
                    } else {
                        bulkCategoryDialog = true
                    }
                },
                onExport = {
                    val rows = selectedTxs.sortedBy { it.date }
                    val name = "iji_bills_${DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())}.csv"
                    val uri = saveToDownloads(context, name, "text/csv", viewModel.toCsv(rows))
                    snackbar.show(
                        if (uri != null) "已导出 ${rows.size} 笔到 Download/$name" else "导出失败",
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = LocalDimens.current.screenPadding,
                        end = LocalDimens.current.screenPadding,
                        bottom = 76.dp,
                    ),
            )
        }
    }

    // 批量删除：二次确认（写明笔数，并提示余额会回滚）
    IjiDialog(
        show = bulkDeleteConfirm,
        onDismissRequest = { bulkDeleteConfirm = false },
        title = "删除所选账单",
        summary = "将删除 ${selectedIds.size} 笔账单，并同步回滚相关账户余额。此操作无法撤销。",
        confirmText = "确认删除",
        confirmColor = AppleRed,
        onConfirm = {
            bulkDeleteConfirm = false
            viewModel.deleteBillsByIds(selectedIds.toList()) { outcome ->
                exitMultiSelect()
                snackbar.show(
                    when (outcome) {
                        is DeleteBillsOutcome.Success -> "已删除 ${outcome.count} 笔账单，余额与统计已同步"
                        DeleteBillsOutcome.NoMatch -> "没有找到可删除的账单"
                        is DeleteBillsOutcome.Failure -> outcome.message
                    },
                    duration = top.yukonga.miuix.kmp.basic.SnackbarDuration.Long,
                )
            }
        },
    ) {
        Text(
            "账户余额会按删除的账单逐笔退回，统计与预算随之刷新。",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }

    // 批量改分类
    BulkCategoryDialog(
        show = bulkCategoryDialog,
        count = selectedIds.size,
        type = selectedTypes.firstOrNull() ?: TransactionType.EXPENSE,
        currentCategoryIds = selectedTxs.map { it.categoryId }.toSet(),
        onDismiss = { bulkCategoryDialog = false },
        onPick = { categoryId ->
            bulkCategoryDialog = false
            viewModel.updateBillsCategory(selectedIds.toList(), categoryId) { updated ->
                exitMultiSelect()
                snackbar.show(if (updated >= 0) "已更新 $updated 笔账单的分类" else "批量改分类失败")
            }
        },
    )

    // 高级筛选弹窗（范围 / 类型 / 金额区间 / 日期区间 / 分类）
    BillDetailDialog(
        tx = detailTx,
        onDismiss = { detailTx = null },
        onEdit = {
            detailTx = null
            editTx = it
        },
    )

    AdvancedFilterDialog(
        show = showFilterDialog,
        filter = filter,
        onDismiss = { showFilterDialog = false },
        onApply = {
            filter = it
            // 弹窗里的「重置」只改了草稿，应用后搜索框也要同步清空
            queryText = it.query
            showFilterDialog = false
        },
        // 预览基于全量流水：条件怎么改都能立刻知道会剩几笔
        previewCount = { candidate -> allTx.count { it.matchesFilter(candidate) } },
    )

    // 删除确认弹窗
    IjiDialog(
        show = pendingDelete != null,
        onDismissRequest = { pendingDelete = null },
        title = "删除账单",
        // 原来这里写死成 "确定删除这笔  ¥ 的账单吗？"，金额位置永远是空的：
        // 用户看不到自己要删的是哪一笔（同分类同金额时尤其容易误删）。
        summary = pendingDelete?.let { tx ->
            val name = runCatching { CategoryCatalog.of(tx.categoryId).name }
                .getOrDefault(tx.categoryId)
            "确定删除「$name ${com.iji.app.ui.components.formatSignedMoney(tx.type, tx.amountCents)}」这笔账单吗？删除后余额会同步回滚。"
        } ?: "确定删除这笔账单吗？",
        confirmText = "删除",
        confirmColor = AppleRed,
        onConfirm = {
            pendingDelete?.let { viewModel.deleteTransaction(it) }
            pendingDelete = null
        },
    )

    // 编辑账单弹窗
    com.iji.app.ui.add.AddTransactionSheet(
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

    // 「记退款」面板：从原账单带出分类 / 账户 / 标签，并直接进入退款模式。
    // 刻意**不预填金额**：退款多为部分退，给个全额默认值反而容易记错，金额这一步让用户亲手确认。
    val refundSrc = refundSource
    com.iji.app.ui.add.AddTransactionSheet(
        show = refundSrc != null,
        onDismiss = { refundSource = null },
        prefillType = TransactionType.EXPENSE,
        prefillCategoryId = refundSrc?.categoryId,
        prefillAccount = refundSrc?.account?.takeIf { it.isNotBlank() },
        prefillTags = refundSrc?.tags?.takeIf { it.isNotEmpty() },
        prefillRefundOfId = refundSrc?.id ?: 0L,
    )

    // 附件查看器：从列表行缩略图点进来
    val viewing = viewingAttachments
    com.iji.app.ui.components.AttachmentViewerDialog(
        show = viewing != null,
        attachments = viewing?.first?.attachments.orEmpty(),
        startIndex = viewing?.second ?: 0,
        onDismissRequest = { viewingAttachments = null },
    )
}

/**
 * 多选模式下的「长按刷选」手势（挂在列表上，不是每行各挂一份）。
 *
 * 手势冲突处理：
 * - **单击**仍由卡片自己的手势处理（切换选中），这里在长按成立前不做任何消费；
 * - 长按成立前一旦移动超过触摸阈值 → 直接放弃，交还列表纵向滚动，所以多选态照样能滚；
 * - 刷选期间消费事件，避免同时滚动列表或触发卡片点击；
 * - 手指靠近列表上下边缘时自动滚动，方便跨屏连续刷选。
 *
 * @param rowBounds 行 id → 窗口坐标矩形（多选时由每行 `onGloballyPositioned` 写入）
 * @param listCoordinates 列表自身坐标，用于把手指的局部坐标换算到窗口坐标
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.billPaintGesture(
    multiSelectMode: () -> Boolean,
    rowBounds: Map<Long, Rect>,
    listCoordinates: () -> LayoutCoordinates?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isSelected: (Long) -> Boolean,
    applySelection: (Long, Boolean) -> Unit,
) {
    val edge = 72.dp.toPx()
    val slop = viewConfiguration.touchSlop
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis

    fun idAt(position: Offset): Long? {
        val coords = listCoordinates() ?: return null
        val windowPosition = coords.localToWindow(position)
        return rowBounds.entries.firstOrNull { it.value.contains(windowPosition) }?.key
    }

    fun paint(position: Offset, select: Boolean) {
        val id = idAt(position) ?: return
        if (select != isSelected(id)) applySelection(id, select)
    }

    awaitEachGesture {
        // 必须先等按下再判断：直接 return 会让 awaitEachGesture 的
        // awaitAllPointersUp() 立刻返回，while 循环空转把主线程打满（实测 ANR）。
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // 只在已进入多选时接管；"长按进入多选"由卡片自身手势负责
        if (!multiSelectMode()) return@awaitEachGesture
        val downTime = android.os.SystemClock.uptimeMillis()
        var totalX = 0f
        var totalY = 0f
        var painting = false
        var selecting = true

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (!painting) {
                totalX += change.positionChange().x
                totalY += change.positionChange().y
                val moved = abs(totalX) > slop || abs(totalY) > slop
                val held = android.os.SystemClock.uptimeMillis() - downTime > longPressTimeout
                if (moved && !held) break   // 长按未成立就滑了 → 交还列表滚动
                if (!moved && !held) continue
                // 长按成立：从按下的那一行起刷（原本已选则取消，未选则选中）
                val id = idAt(down.position) ?: break
                painting = true
                selecting = !isSelected(id)
                applySelection(id, selecting)
            }

            change.consume()
            paint(change.position, selecting)

            val info = listState.layoutInfo
            val y = change.position.y
            val topEdge = info.viewportStartOffset + edge
            val bottomEdge = info.viewportEndOffset - edge
            val delta = when {
                y < topEdge -> (y - topEdge) * 0.5f
                y > bottomEdge -> (y - bottomEdge) * 0.5f
                else -> 0f
            }
            if (delta != 0f) {
                // 受限挂起作用域里不能调用 scrollBy（suspend），用同步的 dispatchRawDelta
                listState.dispatchRawDelta(delta.coerceIn(-80f, 80f))
                paint(change.position, selecting)
            }

            if (change.changedToUp()) break
        }
    }
}

@Composable
private fun CircleActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit = {},
) {
    top.yukonga.miuix.kmp.basic.IconButton(
        onClick = onClick,
        modifier = Modifier.minTouchTarget().ijiIconFocusRing(),
    ) {
        // 跟随主题：写死深灰在深色模式下等于隐身
        Icon(icon, contentDescription = desc, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
    }
}

/** 日头折叠状态：只存「收起」的 key（默认展开），跨进程保留，超上限丢最旧的 */
private object DayCollapseStore {
    private const val KEY = "home.collapsedDays"
    private const val MAX = 200

    fun load(context: Context): List<String> {
        val raw = SettingsRepository.getString(context, KEY)
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.optString(it) }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, keys: Collection<String>) {
        SettingsRepository.setString(context, KEY, JSONArray(keys.toList().takeLast(MAX)).toString())
    }
}

/** 可输入搜索框（标题已上移到固定顶栏）+ 高级筛选入口 + 生效条件摘要 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenFilter: () -> Unit,
    filterActive: Boolean,
    summary: String,
    onClearFilter: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val focusManager = LocalFocusManager.current
    Column(Modifier.padding(horizontal = LocalDimens.current.screenPadding)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                top.yukonga.miuix.kmp.basic.TextField(
                    query,
                    onQueryChange,
                    Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    // 压缩搜索框自身的内边距（默认 16x16 → 12x10），整块不再占那么多纵向空间
                    insideMargin = DpSize(12.dp, 10.dp),
                    // 空态提示可搜索范围，降低学习门槛。useLabelAsPlaceholder=true 时，
                    // 一旦输入 label 状态转为 Placeholder（渲染层跳过绘制），提示自动消失，
                    // 不会像默认的 Floating 那样浮动到上方长期占位。
                    label = "搜索备注、分类、账户或金额",
                    useLabelAsPlaceholder = true,
                )
                // 字段级清空：只清关键词，不动高级筛选（原来只能靠摘要行的「清除」一并清空）
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            MiuixIcons.Close,
                            contentDescription = "清空搜索",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            CircleActionButton(
                icon = MiuixIcons.Tune,
                desc = "筛选",
                onClick = {
                    // 先收键盘：否则输入法会把筛选弹窗顶成半屏
                    focusManager.clearFocus()
                    onOpenFilter()
                },
            )
        }
        // 条件生效时常驻一行摘要：不然"列表里怎么少了这么多"没人解释
        if (filterActive && summary.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 摘要只是"解释"，不该用主色：主色小字在别处都是按钮，这里不可点会误导
                Text(
                    summary,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                IjiTextAction(
                    text = "清除",
                    onClick = onClearFilter,
                    color = TextSecondary,
                    compact = true,
                )
            }
        }
    }
}

/** 空态成因：不同成因给不同出口，而不是一句「本月还没有账单」 */
private enum class EmptyKind { NO_BILL_AT_ALL, NO_BILL_THIS_MONTH, FILTERED }

@Composable
private fun BillsEmptyState(
    kind: EmptyKind,
    filterSummary: String,
    onClearFilter: () -> Unit,
    onPrevMonth: () -> Unit,
) {
    val title = when (kind) {
        EmptyKind.NO_BILL_AT_ALL -> "还没有账单"
        EmptyKind.NO_BILL_THIS_MONTH -> "本月还没有账单"
        EmptyKind.FILTERED -> "没有匹配的账单"
    }
    val desc = when (kind) {
        EmptyKind.NO_BILL_AT_ALL -> "记下第一笔，之后每月收支会自动汇总在这里"
        EmptyKind.NO_BILL_THIS_MONTH -> "可以查看上个月"
        EmptyKind.FILTERED -> filterSummary.ifBlank { "当前筛选条件下没有账单" }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 56.dp,
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (kind == EmptyKind.FILTERED) MiuixIcons.Search else MiuixIcons.ListView,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // 「记一笔」由底部全局悬浮按钮承担，空态不再重复放入口；只有「查看上月」
        // 「清除筛选」这两个真实出口才渲染按钮行，否则会留一块没有按钮的空白。
        if (kind != EmptyKind.NO_BILL_AT_ALL) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (kind == EmptyKind.NO_BILL_THIS_MONTH) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = "查看上月",
                        onClick = onPrevMonth,
                        cornerRadius = Radius.xxl,
                    )
                }
                if (kind == EmptyKind.FILTERED) {
                    top.yukonga.miuix.kmp.basic.Button(
                        onClick = onClearFilter,
                        cornerRadius = Radius.xxl,
                        colors = ijiPrimaryButtonColors(),
                    ) {
                        Text(
                            "清除筛选",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** 加载骨架：形状与真实账单行一致，避免首帧闪空态 */
@Composable
private fun BillListSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalDimens.current.screenPadding, vertical = 8.dp)
            .clip(RoundedCornerShape(Radius.sheet))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        repeat(4) { index ->
            if (index > 0) {
                HorizontalDivider(thickness = 0.5.dp, color = DividerGray.copy(alpha = 0.6f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBar(width = 40.dp, height = 40.dp, circle = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBar(width = 90.dp, height = 12.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBar(width = 150.dp, height = 10.dp)
                }
                SkeletonBar(width = 56.dp, height = 14.dp)
            }
        }
    }
}

@Composable
private fun SkeletonBar(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, circle: Boolean = false) {
    Box(
        Modifier
            .size(width = width, height = height)
            .clip(if (circle) CircleShape else RoundedCornerShape(Radius.sm))
            .background(AppleTertiaryBackground),
    )
}

/**
 * 月份选择：`2026年10月 < >` + 右侧挂件（首页是「报销列」）。
 *
 * 整行字重统一为 Medium（原来月份是默认字重、报销列按选中态在 Normal/SemiBold 之间跳，
 * 看着粗细不一）；上下内边距压到 6/2dp，行高整体收紧。
 */
@Composable
private fun MonthSelector(
    month: java.time.YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    /** 月份是否参与过滤；false 时翻月无效，箭头一并淡出 */
    monthRelevant: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val arrowTint = TextSecondary.copy(alpha = if (monthRelevant) 1f else 0.3f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${month.year}年${month.monthValue}月",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (monthRelevant) MaterialTheme.colorScheme.onSurface else TextSecondary,
        )
        Spacer(Modifier.width(8.dp))
        top.yukonga.miuix.kmp.basic.IconButton(
            onClick = { if (monthRelevant) onPrev() },
            modifier = Modifier.minTouchTarget().ijiIconFocusRing(),
        ) {
            Icon(MiuixIcons.ChevronBackward, contentDescription = "上月", tint = arrowTint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(6.dp))
        top.yukonga.miuix.kmp.basic.IconButton(
            onClick = { if (monthRelevant) onNext() },
            modifier = Modifier.minTouchTarget().ijiIconFocusRing(),
        ) {
            Icon(MiuixIcons.ChevronForward, contentDescription = "下月", tint = arrowTint, modifier = Modifier.size(16.dp))
        }
        // 挂件推到行尾：与月份、翻页箭头同一行，不另起一行
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 月份行右侧的「报销列」筛选胶囊：字重与月份行统一，只靠底色/字色表达选中 */
@Composable
private fun ReimburseChip(
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xl)
    Box(
        modifier = Modifier
            // 视觉高度不变，热区补到 44dp
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (active) MiuixPrimary.copy(alpha = 0.14f) else AppleTertiaryBackground)
            // 状态不只靠颜色：启用时加描边 + 勾号，色盲/深色下也能区分
            .then(if (active) Modifier.border(1.dp, MiuixPrimary.copy(alpha = 0.4f), shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { selected = active }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (active) "✓ 报销列" else "报销列",
            style = MaterialTheme.typography.bodySmall,
            color = if (active) MiuixPrimary else TextSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 本月概览大卡：**实色状态卡**——卡底即预算状态（正常=主色蓝 / 已超支=红），内容全白。
 *
 * 此前「白卡 + 状态色微染」试过平涂与渐变两版，都发闷：染色上红（支出）蓝（剩余）
 * 两个大数字互相打架，进度条轨道也发灰。实色卡是记账类概览的常规做法：
 * 状态一眼可辨，也彻底解决「与近白页面底色融为一体」。
 * 纯信息卡不带操作入口——预算的增改统一在「预算」页（那里有完整的编辑弹窗）。
 * 卡片只回答四个问题：本月收支（支出/收入并排）、还能花多少、每天可用多少、今天还能花多少。
 */
@Composable
private fun SummaryCard4(
    expenseCents: Long,
    /** 当前月总收入（分），与 [expenseCents] 同口径（已剔除「不计入收支」） */
    incomeCents: Long = 0L,
    budgetCents: Long,
    budgetExpenseCents: Long = expenseCents,
    /** 真实的预算进度（与预算页同一份数据）；为空时回退旧的内存算法 */
    budgetProgress: BudgetProgress? = null,
    /** 日均预算（分）= 剩余预算 ÷ 剩余天数（含今天）；0 表示未设置预算或已超支 */
    dailyBudgetCents: Long = 0L,
    /** 今日剩余可花（分）= 日均预算 − 今日支出；可为负（今日已超） */
    todayRemainingCents: Long? = null,
) {
    // 预算相关数值一律由 BudgetProgress 驱动，保证与预算页一致；
    // 只有「尚未持久化预算」的过渡场景才回退到旧的内存算法。
    val bp = budgetProgress
    val remainingBudget = when {
        bp != null -> bp.remainingCents
        budgetCents > 0L -> budgetCents - budgetExpenseCents
        else -> null
    }
    val percent = bp?.percent
        ?: if (budgetCents > 0L) ((budgetExpenseCents * 100) / budgetCents).toInt() else 0
    val overBudget = bp?.let { it.percent >= 100 }
        ?: (budgetCents > 0L && budgetExpenseCents > budgetCents)

    // 卡底=状态色：正常/未设置=主色蓝，已超支=红。「接近超支」不改卡底（琥珀上白字
    // 对比度不够），由进度条填充与「已用」文字变琥珀表达。
    val cardColor = if (overBudget) AppleRed else MiuixPrimary
    // 卡上内容统一白色系（标签 72% / 正文 100%），单一色调讲完整个卡片
    val onCardLabel = Color.White.copy(alpha = 0.72f)
    val onCardValue = Color.White
    // 进度条填充：接近超支时变琥珀（蓝色卡上可辨），其余白色；轨道恒白 25%
    val progressFill = if (bp?.level == BudgetAlertLevel.NEAR_LIMIT && !overBudget) {
        NearLimitColor
    } else {
        Color.White
    }

    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalDimens.current.screenPadding, vertical = 8.dp),
        cornerRadius = Radius.sheet,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(cardColor),
    ) {
        // 两个金额并排，字号比原先单个英雄数字小一档；系统字号放大后再降一档
        val amountSize = if (LocalDensity.current.fontScale > 1.3f) 14.sp else 18.sp

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            // 主行：本月收支（支出/收入）+ 预算剩余 —— 账本页要回答的是「进出多少、还能花多少」
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "本月收支",
                        style = MaterialTheme.typography.bodySmall,
                        color = onCardLabel,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${formatMoney(expenseCents)}/${formatMoney(incomeCents)}",
                        fontSize = amountSize,
                        fontWeight = FontWeight.Bold,
                        color = onCardValue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        // 超支时换标签：状态不只靠颜色传达
                        if (remainingBudget != null && remainingBudget < 0L) "已超支" else "预算剩余",
                        style = MaterialTheme.typography.bodySmall,
                        color = onCardLabel,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (remainingBudget != null) {
                            formatMoney(kotlin.math.abs(remainingBudget))
                        } else {
                            "未设置"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (remainingBudget == null) Color.White.copy(alpha = 0.6f) else onCardValue,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            when {
                bp != null && bp.amountCents > 0L -> {
                    // 百分比贴进度条右侧：条按剩余宽度缩短，省掉原来单独占的一行
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BudgetProgressBar(
                            progress = bp,
                            modifier = Modifier.weight(1f),
                            height = 6.dp,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            fillColor = progressFill,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${if (overBudget) "超支" else "已用"} $percent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (bp.level == BudgetAlertLevel.NEAR_LIMIT && !overBudget) {
                                NearLimitColor
                            } else {
                                Color.White.copy(alpha = 0.85f)
                            },
                            maxLines = 1,
                        )
                    }
                }
                budgetCents > 0L -> {
                    Text(
                        "已用 $percent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                else -> {
                    Text(
                        "还没设置本月预算，可在「预算」页设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            // 发丝线分隔：上方是「月度预算」区，下方是「日均 / 今日」区
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
            Spacer(Modifier.height(12.dp))
            // 底行：日均预算 / 今日剩余 —— 「每天可用多少、今天还能花多少」
            if (dailyBudgetCents > 0L) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "日均预算",
                            style = MaterialTheme.typography.bodySmall,
                            color = onCardLabel,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatMoney(dailyBudgetCents),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = onCardValue,
                            maxLines = 1,
                        )
                    }
                    // 今日剩余只在浏览当前月时由调用方传入；缺省时只显示日均预算一列
                    val todayLeft = todayRemainingCents
                    if (todayLeft != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                // 超了换标签：状态不只靠颜色传达
                                if (todayLeft < 0L) "今日已超" else "今日剩余",
                                style = MaterialTheme.typography.bodySmall,
                                color = onCardLabel,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                formatMoney(kotlin.math.abs(todayLeft)),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = onCardValue,
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/** 日期头：左日期，右 笔数 + 收入/支出金额 + 箭头（点击折叠/展开；吸顶时靠自身底色遮住下方内容） */
@Composable
private fun DayHeaderV2(day: DaySummary, expanded: Boolean, onToggle: () -> Unit) {
    // 直接用 DaySummary 的聚合值（已按「不计入收支」口径剔除），
    // 否则日头相加≠汇总卡，用户会以为账算错了
    val dayIncome = day.incomeCents
    val dayExpense = day.expenseCents
    // 折叠图标按右侧文字的行高给尺寸：居中后与文字等高、落在同一条中心线上，
    // 字体缩放时一起变大，不会出现「图标比文字高半格」。
    val rowStyle = MaterialTheme.typography.bodySmall
    val iconSize = rowStyle.lineHeight.value.dp * LocalDensity.current.fontScale
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 最小行高 + 上下对称 padding：收起时相邻两个日期头之间不会忽紧忽松
            .heightIn(min = 32.dp)
            // 吸顶后必须有实底：否则下方账单会从日期文字间透出来
            .background(MaterialTheme.colorScheme.background)
            // 折叠只切换显示，不要点击水波纹：整行闪一层灰条比折叠本身更抢眼
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = LocalDimens.current.screenPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dateStr = "${day.date.monthValue}/${day.date.dayOfMonth}  " +
            when (day.date.dayOfWeek.value) {
                1 -> "星期一"; 2 -> "星期二"; 3 -> "星期三"; 4 -> "星期四"
                5 -> "星期五"; 6 -> "星期六"; else -> "星期日"
            }
        Text(dateStr, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${day.transactions.size} 笔",
                style = rowStyle,
                color = TextSecondary.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(6.dp))
            // 只显示当日实际发生的方向：两个「¥0.00」并列只是噪音
            if (dayIncome > 0L) {
                Text("收 ${formatMoney(dayIncome)}", style = rowStyle, color = IncomeGreen)
                Spacer(Modifier.width(8.dp))
            }
            if (dayExpense > 0L) {
                Text(
                    "支 ${formatMoney(dayExpense)}",
                    style = rowStyle,
                    // 全局约定：消费红色
                    color = ExpenseAmount,
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                if (expanded) MiuixIcons.ExpandMore else MiuixIcons.ExpandLess,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * 单条账单卡片：左滑露出右侧胶囊操作区（编辑 / 退款 / 复制 / 删除），点击空白自动回弹。
 *
 * [onRefund] 为 null 时不渲染「退款」按钮（仅正向支出才有「再退一次」的语义），
 * 此时操作区少一个胶囊 —— [maxSwipe] 与按钮排布必须用**同一个**数量推导，否则会露不全或露出空白。
 */
@Composable
private fun TransactionCard(
    tx: Transaction,
    /**
     * 分组模式：本行被包进「当天一张大卡」里（HomeScreen 列表）。
     * 此时行自身不再画圆角卡片、不带外边距（容器统一负责），行间靠分割线分隔；
     * false = 独立一张卡（日历/账户详情等调用方）。
     */
    grouped: Boolean = false,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    multiSelectMode: Boolean = false,
    openCardId: Long? = null,
    onOpenChange: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    onRefund: (() -> Unit)? = null,
    /** 行下方的退款说明（「已退 ¥X · 净 ¥Y」或「退款自 …」） */
    refundNote: String = "",
    /** 点附件缩略图 → 打开大图查看器（参数是第几张） */
    onOpenAttachments: ((Int) -> Unit)? = null,
    suppressCardClose: MutableState<Boolean>,
) {
    val density = LocalDensity.current
    // 拖拽实时偏移：awaitEachGesture 是受限挂起作用域，只能用普通 State 即时赋值跟手
    val dragOffset = remember { mutableStateOf(0f) }
    // 松手 / 复位时的平滑过渡用 Animatable，由外部 LaunchedEffect 驱动（受限作用域内调不了 snapTo/animateTo）
    val animatable = remember { Animatable(0f) }
    var animating by remember { mutableStateOf(false) }
    // 触发平滑过渡的目标值；非 null 时下方 LaunchedEffect 接管动画，结束后回填 dragOffset
    var settleTarget by remember { mutableStateOf<Float?>(null) }
    // 右侧操作区胶囊：编辑 / (退款) / 复制 / 删除
    // dp 与 px 都从同一组常量推导，避免「滑出宽度」和「按钮实际占宽」对不上
    val actionPillWidth = 56.dp
    val actionPillGap = 7.dp
    val actionCount = if (onRefund != null) 4 else 3
    val actionWidth = with(density) { actionPillWidth.toPx() }
    val actionSpacing = with(density) { actionPillGap.toPx() }
    val maxSwipe = actionWidth * actionCount + actionSpacing * (actionCount - 1)
    // 松手吸附 / 回弹的弹性动画：平滑过渡，不再瞬间跳变（无过冲，避免操作区被多露出）
    val settleSpec = spring<Float>(stiffness = 380f, dampingRatio = Spring.DampingRatioNoBouncy)
    // 手势参数（Composable 作用域读取，供 pointerInput 捕获使用）
    val viewConfig = LocalViewConfiguration.current
    val tapSlop = viewConfig.touchSlop
    val longPressTimeout = viewConfig.longPressTimeoutMillis
    val dragSlop = with(density) { 16.dp.toPx() }

    // 松手 / 点击空白 / 其他卡片打开：派发 settleTarget，由这里平滑过渡到目标值
    LaunchedEffect(settleTarget) {
        val target = settleTarget ?: return@LaunchedEffect
        animating = true
        animatable.snapTo(dragOffset.value) // 从当前拖拽位置起步
        animatable.animateTo(target, settleSpec)
        dragOffset.value = target
        animating = false
        settleTarget = null
    }

    // 其他卡片打开或点击空白复位时，本卡片平滑回弹归零
    LaunchedEffect(openCardId) {
        if (openCardId != tx.id) {
            if (dragOffset.value != 0f || animatable.value != 0f) {
                settleTarget = 0f
            }
        } else {
            // 本卡片应处于打开态（如滚动时 item 被回收重建）但状态已归零：
            // 直接就位打开、不播弹簧，避免「纯竖向滑动时账单也回弹 / 打开的卡片滚走回来后变关」
            if (dragOffset.value == 0f && animatable.value == 0f) {
                animatable.snapTo(-maxSwipe)
                dragOffset.value = -maxSwipe
            }
        }
    }

    Box(
        modifier = modifier.then(
            if (grouped) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LocalDimens.current.screenPadding, vertical = 4.dp)
            },
        ),
    ) {
        // 背景操作区（仅右侧）：胶囊按钮 编辑 / (退款) / 复制 / 删除
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = if (grouped) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            ActionPill("编辑", MiuixPrimary, onClick = { suppressCardClose.value = true; onClick() }, modifier = Modifier.width(actionPillWidth))
            Spacer(Modifier.width(actionPillGap))
            if (onRefund != null) {
                ActionPill("退款", com.iji.app.ui.theme.AppleOrange, onClick = { suppressCardClose.value = true; onRefund() }, modifier = Modifier.width(actionPillWidth))
                Spacer(Modifier.width(actionPillGap))
            }
            ActionPill("复制", IncomeGreen, onClick = { suppressCardClose.value = true; onCopy() }, modifier = Modifier.width(actionPillWidth))
            Spacer(Modifier.width(actionPillGap))
            // 删除是破坏性操作：用全局约定的语义红，而不是行文字同色的深灰（与普通内容难区分）
            ActionPill("删除", AppleRed, onClick = { suppressCardClose.value = true; onDelete() }, modifier = Modifier.width(actionPillWidth))
        }

        // 前景卡片
        top.yukonga.miuix.kmp.basic.Card(
            modifier = Modifier
                .offset { IntOffset((if (animating) animatable.value else dragOffset.value).toInt(), 0) }
                // 单协程自实现手势（点击 / 长按 / 横向拖拽），避免与系统点击检测竞争
                .pointerInput(tx.id, multiSelectMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = android.os.SystemClock.uptimeMillis()
                        var dragged = false
                        var longPressed = false
                        var totalX = 0f
                        var totalY = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            if (!dragged) {
                                totalX += change.positionChange().x
                                totalY += change.positionChange().y
                                if (!multiSelectMode &&
                                    abs(totalX) > dragSlop && abs(totalX) > abs(totalY) * 2
                                ) {
                                    // 水平位移占优且超阈值：启动横向拖拽，并立即应用本次位移
                                    //（兼容事件稀疏场景：系统可能只下发一个 move，须一次到位）
                                    dragged = true
                                    // 中断可能正在进行的回弹动画，从当前可视位置继续跟手
                                    settleTarget = null
                                    animating = false
                                    dragOffset.value = animatable.value
                                    val dx = change.positionChange().x
                                    dragOffset.value =
                                        if (dragOffset.value < 0f) {
                                            // 已滑出：跟手（右滑收回，左滑继续打开）
                                            (dragOffset.value + dx).coerceIn(-maxSwipe, 0f)
                                        } else {
                                            // 未滑出：任意方向都露出右侧操作区
                                            (dragOffset.value - abs(dx)).coerceIn(-maxSwipe, 0f)
                                        }
                                } else if (!multiSelectMode && abs(totalY) > dragSlop) {
                                    // 垂直滚动占优：放弃本手势，交由列表滚动
                                    break
                                }
                                // 长按：按住超时且未明显移动
                                if (!longPressed && !multiSelectMode &&
                                    android.os.SystemClock.uptimeMillis() - downTime > longPressTimeout &&
                                    abs(totalX) < tapSlop && abs(totalY) < tapSlop
                                ) {
                                    longPressed = true
                                    onLongClick()
                                }
                            } else {
                                // 拖拽中若纵向明显占优（用户改为上下滑动），立即交还列表滚动，
                                // 并瞬间归位、不播放回弹动画，避免「上下滑动时账单也回弹」
                                if (abs(change.positionChange().y) > abs(change.positionChange().x) * 3) {
                                    dragOffset.value = 0f
                                    dragged = false
                                    break
                                }
                                dragOffset.value =
                                    if (dragOffset.value < 0f) {
                                        // 已滑出：反向滑动（向右）收回；同向滑动保持全开
                                        (dragOffset.value + change.positionChange().x).coerceIn(-maxSwipe, 0f)
                                    } else {
                                        // 未滑出：左滑 / 右滑都统一露出右侧操作区（方向不影响结果）
                                        (dragOffset.value - abs(change.positionChange().x)).coerceIn(-maxSwipe, 0f)
                                    }
                            }
                            if (change.changedToUp()) break
                        }
                        if (dragged) {
                            // 松手：滑过操作区 1/3 就吸附全开（阈值低一点，轻扫即开），
                            // 否则平滑回弹归零（派发 settleTarget 走动画）
                            val target = if (dragOffset.value < -maxSwipe / 3) -maxSwipe else 0f
                            settleTarget = target
                            onOpenChange(target != 0f)
                        } else if (!longPressed && abs(totalX) < tapSlop && abs(totalY) < tapSlop) {
                            // 多选态：单击 = 切换选中（不再进详情）；
                            // 普通态：已滑开则收回，否则进详情
                            if (multiSelectMode) {
                                onClick()
                            } else if (dragOffset.value != 0f) {
                                settleTarget = 0f
                                onOpenChange(false)
                            } else {
                                onClick()
                            }
                        }
                    }
                },
            cornerRadius = if (grouped) 0.dp else 24.dp,
            colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                when {
                    // 选中态：主色与底色**不透明**混合。不能用带 alpha 的主色 ——
                    // 行卡片半透明会让背后滑出的操作胶囊透出来（已踩过）
                    isSelected -> androidx.compose.ui.graphics.lerp(
                        MaterialTheme.colorScheme.surface,
                        MiuixPrimary,
                        0.14f,
                    )
                    // 分组模式必须**不透明**且与「当天大卡」同色：操作胶囊画在行的后面，
                    // 靠这张前景卡盖住；设成透明胶囊就会透出来叠在行上（已踩过）
                    grouped -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
        ) {
            TransactionRow(
                tx = tx,
                leadingContent = if (multiSelectMode) {
                    {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MiuixPrimary else AppleSeparator),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                } else null,
                showAccountUnderAmount = true,
                refundNote = refundNote,
                onOpenAttachments = onOpenAttachments,
            )
        }
    }
}

/**
 * 胶囊形操作按钮（滑出操作区使用）。
 *
 * 不再撑满行高（56 宽 × 全行高 ≈ 一个方块，视觉笨重）：固定 38dp 高、随行垂直居中，
 * 是真正的胶囊比例。按压缩放反馈（0.9 回弹）替代默认涟漪——色块上涟漪发糊，
 * 缩放手感与 app 其余可点元素（悬浮窗 pressableScale / 玻璃按钮）一致。
 */
@Composable
private fun ActionPill(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 600f, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "actionPillPress",
    )
    Box(
        modifier = modifier
            .height(ActionPillHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(Radius.pill))
            .background(color)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/** 操作胶囊的固定高度：窄于行高，让「圈」恢复成胶囊的比例 */
private val ActionPillHeight = 38.dp
