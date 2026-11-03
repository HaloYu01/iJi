package com.iji.app.ui.assets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import com.iji.app.ui.components.IjiTopBar
import com.iji.app.ui.components.ijiIconFocusRing
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.topbar.topBarContentInset
import com.iji.app.ui.components.topbar.rememberTopBarScrollBehavior
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.input.nestedscroll.nestedScroll

import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiDangerTextAction
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.menu.AnchoredIconDropdownMenu
import top.yukonga.miuix.kmp.basic.DropdownItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.Account
import com.iji.app.data.model.AccountType
import com.iji.app.data.model.TransactionType
import java.time.LocalDate
import com.iji.app.ui.components.AccountIconImage
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.parseCents
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.minTouchTarget
import com.iji.app.ui.theme.AppleRed
import kotlinx.coroutines.delay

import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import com.iji.app.ui.theme.IjiTheme
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add

/**
 * 资产页（仿设计稿）：净资产总览卡 + 资金账户列表 + 左上角加号添加账户。
 * 内置图标：微信 / 支付宝 / 10 个主流银行（AccountIcon 自绘品牌徽章）。
 */
@Composable
fun AssetsScreen(
    onNavigateMine: () -> Unit = {},
    onNavigateAddAccount: () -> Unit = {},
    onNavigateAccount: (Long) -> Unit = {},
    onNavigateEditAccount: (Long) -> Unit = {},
    onNavigateBorrowLend: (BorrowDirection) -> Unit = {},
    /** 新建后需要定位并高亮的账户 id（由添加页通过 savedStateHandle 回传） */
    highlightAccountId: Long? = null,
    /** 高亮消费完毕（清掉回传值，避免下次进页面又滚一次） */
    onHighlightConsumed: () -> Unit = {},
    app: MainViewModel = viewModel(),
    viewModel: AssetsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AssetsViewModel(app) as T
        },
    ),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    // 首帧未就绪时不显示「还没有账户」/¥0.00，避免冷启动闪一下空态
    val accountsReady by app.accountsReady.collectAsStateWithLifecycle()
    var transferFrom by remember { mutableStateOf<Account?>(null) }
    var hiddenManageOpen by remember { mutableStateOf(false) }

    // 这些聚合原先直接写在组合里，每次重组都重算（含每次切 tab 引起的重组）；用 remember 按依赖缓存
    // 口径见 computeAssetTotals：资产 = 非信用类余额（含信用类溢缴），负债 = 信用类欠款
    val totals = remember(accounts) { computeAssetTotals(accounts) }
    val totalAssets = totals.assetsCents
    val totalLiabilities = totals.liabilitiesCents
    val transactions by app.transactions.collectAsStateWithLifecycle()
    val transactionsReady by app.transactionsReady.collectAsStateWithLifecycle()
    val now = remember { LocalDate.now() }
    val monthIncome = remember(transactions, now) {
        transactions.filter {
            it.type == TransactionType.INCOME && it.date.year == now.year && it.date.month == now.month
        }.sumOf { it.amountCents }
    }
    val monthExpense = remember(transactions, now) {
        transactions.filter {
            it.type == TransactionType.EXPENSE && it.date.year == now.year && it.date.month == now.month
        }.sumOf { it.amountCents }
    }
    // 借入 / 借出：口径见 computeBorrowLend（按分类树归集，跳过不计入统计的账单）
    val borrowLend = remember(transactions) { computeBorrowLend(transactions) }
    // 筛选用 rememberSaveable：进账户详情再返回、或旋转屏幕后不该把用户的选择清掉
    var filter by rememberSaveable { mutableStateOf(AccountFilter.ALL) }

    // 每个账户本月收支净额（转账不计入收支），用于行内小字；组合付款按 split 分摊到各账户。
    val monthNetByAccount = remember(transactions, now) {
        val net = HashMap<String, Long>()
        transactions.forEach { tx ->
            if (tx.date.year != now.year || tx.date.month != now.month) return@forEach
            val sign = when (tx.type) {
                TransactionType.INCOME -> 1L
                TransactionType.EXPENSE -> -1L
                else -> return@forEach
            }
            if (tx.splits.isEmpty()) {
                net[tx.account] = (net[tx.account] ?: 0L) + sign * tx.amountCents
            } else {
                tx.splits.forEach { split ->
                    net[split.account] = (net[split.account] ?: 0L) + sign * split.amountCents
                }
            }
        }
        net
    }
    // 净资产：只计算计入净资产的账户
    val netAssets = totals.netWorthCents

    // 隐藏的账户不在列表展示（仍计入净资产），编辑/删除从数据库移除或标记。
    val visibleAccounts = remember(accounts) { accounts.filter { !it.hidden } }
    val hiddenAccounts = remember(accounts) { accounts.filter { it.hidden } }
    val typeById = remember(visibleAccounts) {
        visibleAccounts.associate { it.id to AccountType.ofAccount(it.category, it.iconKey, it.name) }
    }

    val filteredAccounts = remember(visibleAccounts, typeById, filter) {
        visibleAccounts.filter { acc -> filter.matches(acc, typeById.getValue(acc.id)) }
    }

    // 按账户分类分组：分类取自持久化的 category，**存量账户为空时按 iconKey/名称推断**
    // （AccountType.ofAccount），顺序跟随 AccountType 声明顺序（现金→银行→网银→充值→投资→信用→其他）。
    val groupedAccounts = remember(filteredAccounts, typeById) {
        filteredAccounts
            .groupBy { typeById.getValue(it.id) }
            .toList()
            .sortedBy { (type, _) -> AccountType.entries.indexOf(type) }
    }

    // 新建账户后要滚到它并高亮（新建的排在最后，不定位的话用户看不到）
    val highlightId = highlightAccountId

    /**
     * LazyColumn 的 item 顺序表：**必须与下面 LazyColumn 的 item 声明严格一致**，
     * 滚动定位靠 key 换算出下标。新增 item 时记得同步这里。
     */
    val listKeys = remember(
        groupedAccounts, accountsReady, visibleAccounts, filteredAccounts, hiddenAccounts,
    ) {
        buildList {
            add("overview")
            add("borrow-lend")
            add("accounts-header")
            if (accountsReady && visibleAccounts.isNotEmpty()) add("accounts-filters")
            if (!accountsReady) add("accounts-loading")
            if (accountsReady && visibleAccounts.isEmpty()) add("accounts-empty")
            if (visibleAccounts.isNotEmpty() && filteredAccounts.isEmpty()) add("accounts-no-match")
            groupedAccounts.forEach { (type, _) ->
                add("group-${type.name}")
                add("group-card-${type.name}")
            }
            if (hiddenAccounts.isNotEmpty()) add("hidden-entry")
        }
    }

    // ── 固定顶栏：整页内容（总览卡 + 账户列表）统一在一个 LazyColumn 里滚动，
    // 这样内容会从横栏下方滑过并被 Miuix 渐进式模糊柔化；横栏自身不随滚动移动。
    val listState = rememberLazyListState()
    val topBarSurface = MaterialTheme.colorScheme.background
    val topBarBackdrop = rememberLayerBackdrop { drawRect(topBarSurface); drawContent() }
    // 顶栏采样层节流：滚动中才重录，静止不录、不重跑顶栏模糊 shader（见 GlassBottomNavBar / AppRoot 同款优化）。
    val topBarMustRecord = remember(listState) { { listState.isScrollInProgress } }
    val listScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val scrollBehavior = rememberTopBarScrollBehavior()
    // LocalDensity 只能在组合期读：LaunchedEffect 块里直接读会报「@Composable invocation」
    val density = LocalDensity.current

    // 新建账户后要滚到它并高亮：从添加页返回时账户 Flow 往往还没 emit 新账户，
    // 所以把「目标账户所在分组」本身作为 effect key —— 等数据到达、分组解析出来后重试一次。
    // （只依赖 listKeys 不行：新增账户不会改变 key 列表的结构相等性，effect 不会重启。）
    val highlightGroupType = remember(highlightId, groupedAccounts) {
        highlightId?.let { id ->
            groupedAccounts.firstOrNull { (_, list) -> list.any { it.id == id } }?.first
        }
    }
    LaunchedEffect(highlightId, highlightGroupType, listKeys) {
        val id = highlightId ?: return@LaunchedEffect
        val type = highlightGroupType ?: return@LaunchedEffect
        val groupIndex = listKeys.indexOf("group-card-${type.name}")
        if (groupIndex < 0) return@LaunchedEffect
        listState.scrollToItem(groupIndex)
        // 组卡是一个整体 item，先把组卡顶对齐视口；只有目标行落在视口下方时才继续向下滚，
        // 把它带到视口中部（组内第 k 行大概在 k*68dp 处）。
        val rowInGroup = groupedAccounts.first { it.first == type }.second.indexOfFirst { it.id == id }
        if (rowInGroup > 0) {
            val rowOffsetPx = with(density) { (rowInGroup * 68).dp.toPx() }
            val viewport = listState.layoutInfo.viewportSize.height.toFloat()
            // 视口底部要让出底栏 + snackbar，否则目标行会被压在底栏后面
            val safeBottom = with(density) { 160.dp.toPx() }
            if (viewport > 0 && rowOffsetPx > viewport - safeBottom) {
                // 把目标行带到视口上部 35% 处，而不是正中间
                listState.scroll { scrollBy(rowOffsetPx - viewport * 0.35f) }
            }
        }
        delay(2500)
        onHighlightConsumed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .topBarContentInset(scrollBehavior)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .then(if (listScrolled) Modifier.layerBackdrop(topBarBackdrop, recordKey = Unit, mustRecord = topBarMustRecord) else Modifier),
            // bottom 要让出底栏（96dp）+ 一点富余：滚到底时最后一个账户不能被底栏压住
            // （新建的账户就排在最后，创建后要能完整看到）
            contentPadding = PaddingValues(top = ijiTopBarContentPadding(), bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        // ── 资产总览卡：净资产 + 总资产 / 总负债 / 本月净额（借入借出见下方两张卡） ──
        item(key = "overview") {
        top.yukonga.miuix.kmp.basic.Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalDimens.current.screenPadding),
            cornerRadius = Radius.sheet,
            colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    "净资产",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // 首帧用占位符，别让 ¥0.00 冒充「净资产为零」
                    if (accountsReady) formatMoney(netAssets) else "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (netAssets < 0) AppleRed else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                // 一行三格：资产 / 负债 / 本月净额。原来的「本月收入 / 本月支出」与统计页重复，已移出。
                Row(Modifier.fillMaxWidth()) {
                    MetricCell("资产", totalAssets, Modifier.weight(1f), ready = accountsReady)
                    MetricCell("负债", totalLiabilities, Modifier.weight(1f), ready = accountsReady)
                    MetricCell(
                        "本月净额",
                        monthIncome - monthExpense,
                        Modifier.weight(1f),
                        ready = transactionsReady,
                    )
                }
                // 本月还没有任何收支时给一句说明：三个 ¥0.00 否则看不出是「没记账」还是「算错了」
                if (transactionsReady && monthIncome == 0L && monthExpense == 0L) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "本月还没有收支记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        }

        // ── 总借入 / 总借出 ──
        item(key = "borrow-lend") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LocalDimens.current.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BorrowLendCard(
                    label = "总借入",
                    pointsDown = true,
                    badgeColor = BorrowBadgeColor,
                    cents = borrowLend.borrowedCents,
                    onClick = { onNavigateBorrowLend(BorrowDirection.BORROW) },
                    modifier = Modifier.weight(1f),
                )
                BorrowLendCard(
                    label = "总借出",
                    pointsDown = false,
                    badgeColor = LendBadgeColor,
                    cents = borrowLend.lentCents,
                    onClick = { onNavigateBorrowLend(BorrowDirection.LEND) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── 资金账户列表 ──
        item(key = "accounts-header") {
            Text(
                // 计数跟随筛选结果；首帧未就绪时先不给数字，避免「资金账户（0）」闪一下
                if (accountsReady) "资金账户（${filteredAccounts.size}）" else "资金账户",
                modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // ── 类型筛选：等宽铺满整行，没有任何账户时整块不出现（空态页自己给出口） ──
        if (accountsReady && visibleAccounts.isNotEmpty()) {
            item(key = "accounts-filters") {
                AccountFilterBar(filter = filter, onFilterChange = { filter = it })
            }
        }

        if (!accountsReady) {
            item(key = "accounts-loading") {
                StateView(state = ContentState.Loading("加载中"), areaVerticalPadding = 20.dp)
            }
        }

        // ── 空态：一个账户都没有 / 账户全部被隐藏，两种场景给不同的出口 ──
        if (accountsReady && visibleAccounts.isEmpty()) {
            item(key = "accounts-empty") {
                AccountsEmptyCard(
                    allHidden = hiddenAccounts.isNotEmpty(),
                    onAddAccount = onNavigateAddAccount,
                    onManageHidden = { hiddenManageOpen = true },
                )
            }
        }

        // ── 有账户但被筛选清空：给出原因和恢复动作，别让页面看起来像坏了 ──
        if (visibleAccounts.isNotEmpty() && filteredAccounts.isEmpty()) {
            item(key = "accounts-no-match") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalDimens.current.screenPadding)
                        .clip(RoundedCornerShape(Radius.xxl))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "没有匹配的账户",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "当前筛选条件下没有账户",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    IjiTextAction(
                        text = "看全部账户",
                        onClick = { filter = AccountFilter.ALL },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        groupedAccounts.forEach { (type, list) ->
            // 分组头降级为小标签（12sp 次要色），避免与「资金账户（N）」形成两级同权标题
            item(key = "group-${type.name}") {
                AccountGroupHeader(
                    type = type,
                    count = list.size,
                    totalCents = list.sumOf { it.balanceCents },
                )
            }
            // 同组账户合成一张卡：组内相邻、无间隙
            item(key = "group-card-${type.name}") {
                AccountGroupCard(
                    accounts = list,
                    monthNetByAccount = monthNetByAccount,
                    highlightId = highlightId,
                    onClick = onNavigateAccount,
                    onEdit = onNavigateEditAccount,
                    onTransfer = { transferFrom = it },
                    onHide = { viewModel.hideAccount(it) },
                    onDelete = { viewModel.deleteAccount(it) },
                    onReorder = { ordered ->
                        // 本组新顺序合回整页顺序后写库：其余组保持原样，按展示顺序重新编号
                        viewModel.reorderAccounts(
                            groupedAccounts.flatMap { (groupType, group) ->
                                if (groupType == type) ordered.map { it.id } else group.map { it.id }
                            },
                        )
                    },
                )
            }
        }

        // ── 已隐藏账户入口：仅存在隐藏账户时出现，点击进入管理弹窗 ──
        if (hiddenAccounts.isNotEmpty()) {
            item(key = "hidden-entry") {
                HiddenAccountsEntryRow(
                    count = hiddenAccounts.size,
                    onClick = { hiddenManageOpen = true },
                )
            }
        }
    }

        // 固定顶栏（浮层，最后绘制）
        IjiTopBar(
            backdrop = topBarBackdrop,
            scrollBehavior = scrollBehavior,
            onMineClick = onNavigateMine,
            title = "资产",
            startAction = { _, _ ->
                top.yukonga.miuix.kmp.basic.IconButton(
                    onClick = onNavigateAddAccount,
                    modifier = Modifier.minTouchTarget().ijiIconFocusRing(),
                ) {
                    Icon(
                        MiuixIcons.Add,
                        contentDescription = "添加账户",
                        // 跟随主题：写死深色在深色模式下会直接看不见
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
    }

    // 转账弹窗
    TransferDialog(
        show = transferFrom != null,
        fromAccount = transferFrom?.name ?: "",
        accounts = accounts.map { it.name },
        onDismiss = { transferFrom = null },
        onConfirm = { to, amount, note ->
            viewModel.transfer(transferFrom?.name ?: "", to, amount, note)
            transferFrom = null
        },
    )

    // 已隐藏账户管理弹窗
    HiddenAccountsDialog(
        show = hiddenManageOpen,
        accounts = hiddenAccounts,
        onDismiss = { hiddenManageOpen = false },
        onOpenAccount = { id ->
            hiddenManageOpen = false
            onNavigateAccount(id)
        },
        onShow = { viewModel.showAccount(it) },
        onDelete = { viewModel.deleteAccount(it) },
    )
}

/** 借入 / 借出图标底色：低饱和的蓝灰与青绿，白箭头下对比足够又不刺眼 */
private val BorrowBadgeColor = Color(0xFF78818A)
private val LendBadgeColor = Color(0xFF5CA98B)

/** 借入 / 借出：左圆角色块图标 + 右侧「标签 / 金额」两行，两卡等权并排 */
@Composable
private fun BorrowLendCard(
    label: String,
    pointsDown: Boolean,
    badgeColor: Color,
    cents: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sheet))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            SolidArrow(
                pointsDown = pointsDown,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatMoney(cents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                // 0 元走中性灰：没有记录时别用主文字色强调一个空数字
                color = if (cents == 0L) TextSecondary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 实心方向箭头：整块填充、无描边，箭头与箭杆连成一体，比线性图标更厚实饱满。
 *
 * 用 Canvas 画而不用内置图标，是因为 Miuix / Material 的箭头都是细线条造型，
 * 与实心色块的量感不匹配。两枚箭头共用同一套比例，只翻转方向，风格天然一致。
 */
@Composable
private fun SolidArrow(
    pointsDown: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val stemHalf = w * 0.11f
        val headHalf = w * 0.32f
        val tail = if (pointsDown) 0f else h
        val neck = if (pointsDown) h * 0.52f else h * 0.48f
        val tip = if (pointsDown) h else 0f
        val arrow = Path().apply {
            moveTo(cx - stemHalf, tail)
            lineTo(cx + stemHalf, tail)
            lineTo(cx + stemHalf, neck)
            lineTo(cx + headHalf, neck)
            lineTo(cx, tip)
            lineTo(cx - headHalf, neck)
            lineTo(cx - stemHalf, neck)
            close()
        }
        drawPath(arrow, tint)
    }
}

/**
 * 总览卡内的指标单元格：标签 + 金额（负数以红色标识）。三格并排，金额单行省略避免挤爆。
 * 账户详情页复用；[ready] 为 false 时显示占位符（首帧尚未算出真实数值）。
 */
@Composable
internal fun MetricCell(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
    ready: Boolean = true,
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            if (ready) formatMoney(cents) else "—",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (cents < 0) AppleRed else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 同类别账户卡：组内账户上下紧邻、只有外层一个圆角，行间用发丝线分隔。
 *
 * 拖动排序：长按某行上下拖动，越过相邻行中点即换位（只在本组内生效），松手才写库，
 * 所以跨行时不会一秒写十几次 Room。其余类别互不影响。
 */
@Composable
private fun AccountGroupCard(
    accounts: List<Account>,
    monthNetByAccount: Map<String, Long>,
    /** 需要高亮的账户 id（新建账户后定位过去） */
    highlightId: Long? = null,
    onClick: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onTransfer: (Account) -> Unit,
    onHide: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onReorder: (List<Account>) -> Unit,
) {
    // 拖拽期间用本地顺序即时换位；写库只在松手时发生一次
    var order by remember { mutableStateOf(accounts) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<Long, Int>() }
    val haptics = LocalHapticFeedback.current

    // 非拖拽时跟随外部数据（新增/删除/持久化后的顺序），拖拽中不打断手势
    LaunchedEffect(accounts) {
        if (draggingId == null) order = accounts
    }

    fun move(from: Int, to: Int) {
        if (from == to || to !in order.indices) return
        order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun handleDrag(id: Long, dy: Float) {
        if (draggingId != id) return
        dragOffset += dy
        val index = order.indexOfFirst { it.id == id }
        if (index < 0) return
        val nextHeight = order.getOrNull(index + 1)?.let { rowHeights[it.id] }?.toFloat() ?: 0f
        val prevHeight = order.getOrNull(index - 1)?.let { rowHeights[it.id] }?.toFloat() ?: 0f
        when {
            dragOffset > 0f && nextHeight > 0f && dragOffset > nextHeight * 0.5f -> {
                move(index, index + 1)
                dragOffset -= nextHeight
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
            dragOffset < 0f && prevHeight > 0f && -dragOffset > prevHeight * 0.5f -> {
                move(index, index - 1)
                dragOffset += prevHeight
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }

    fun finishDrag() {
        val moved = draggingId != null && order.map { it.id } != accounts.map { it.id }
        draggingId = null
        dragOffset = 0f
        // 顺序没变就不写库，避免点一下也产生一堆 UPDATE
        if (moved) onReorder(order)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalDimens.current.screenPadding)
            .clip(RoundedCornerShape(Radius.sheet))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        order.forEachIndexed { index, account ->
            key(account.id) {
                val dragging = draggingId == account.id
                // pointerInput 的 block 只在 key 变化时重启，闭包会抓到过期状态；
                // 用 rememberUpdatedState 把每帧最新的回调喂进手势
                val startDrag = rememberUpdatedState<() -> Unit> {
                    draggingId = account.id
                    dragOffset = 0f
                }
                val dragBy = rememberUpdatedState<(Float) -> Unit> { dy -> handleDrag(account.id, dy) }
                val endDrag = rememberUpdatedState<() -> Unit> { finishDrag() }
                AccountRow(
                    account = account,
                    monthNetCents = monthNetByAccount[account.name],
                    highlighted = highlightId != null && highlightId == account.id,
                    modifier = Modifier
                        .onGloballyPositioned { rowHeights[account.id] = it.size.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .pointerInput(account.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    startDrag.value()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragBy.value(amount.y)
                                },
                                onDragEnd = { endDrag.value() },
                                onDragCancel = { endDrag.value() },
                            )
                        },
                    onClick = { onClick(account.id) },
                    onEdit = { onEdit(account.id) },
                    onTransfer = { onTransfer(account) },
                    onHide = { onHide(account) },
                    onDelete = { onDelete(account) },
                )
                if (index < order.lastIndex) AccountRowDivider()
            }
        }
    }
}

/** 组内分隔线：与文字左缘对齐（16 内边距 + 44 图标 + 14 间距），不额外占高度 */
@Composable
private fun AccountRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 74.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

/**
 * 单个资金账户行：圆形图标 + 名称/副名 + 右侧余额，右上角 Miuix 风格点状菜单。
 * 整行点击进入账户详情；点状菜单提供编辑 / 转账 / 隐藏 / 删除。
 */
@Composable
private fun AccountRow(
    account: Account,
    monthNetCents: Long? = null,
    /** 新建后定位高亮 */
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onEdit: () -> Unit = {},
    onTransfer: () -> Unit = {},
    onHide: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 底色与卡片一致：拖拽时整行会浮到相邻行之上，需要自己的不透明底
            .background(
                if (highlighted) MiuixPrimary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIconImage(iconKey = account.iconKey, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (account.subName.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    account.subName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatMoney(account.balanceCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (account.balanceCents < 0) AppleRed else MaterialTheme.colorScheme.onSurface,
            )
            // 本月收支净额：不进详情也能判断这张卡这个月是"花得多"还是"进得多"
            if (monthNetCents != null && monthNetCents != 0L) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "本月收支 ${formatMoney(monthNetCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (monthNetCents > 0) IncomeGreen else TextSecondary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        // 右上角 Miuix 风格点状菜单（统一走 AnchoredIconDropdownMenu，行为与全站下拉菜单一致）
        AnchoredIconDropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            // 触控区 48dp。必须用固定 size 而不是 minTouchTarget()：
            // 该菜单内部是 fillMaxSize 的 Box，只给 minWidth 会让它把行内剩余宽度全吃掉
            modifier = Modifier.size(48.dp),
            // 每次重组直接构建（不缓存）：onClick 始终捕获最新回调与状态，
            // 避免 remember key 变化导致回调过期、点击表现为「无反应」
            items = listOf(
                DropdownItem(text = "编辑", onClick = onEdit),
                DropdownItem(text = "转账", onClick = onTransfer),
                DropdownItem(text = "隐藏", onClick = onHide),
                DropdownItem(text = "删除", onClick = onDelete),
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                ThreeDotsIcon(tint = TextSecondary)
            }
        }
    }
}

/**
 * 已隐藏账户入口行：出现在账户列表末尾，仅当有隐藏账户时显示。
 * 眼睛关闭图标 + 文案 + 数量 + 右箭头，点击打开管理弹窗。
 */
@Composable
private fun HiddenAccountsEntryRow(
    count: Int,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalDimens.current.screenPadding)
            .clip(RoundedCornerShape(Radius.xxl))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.VisibilityOff,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            "已隐藏账户",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$count",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 已隐藏账户管理弹窗：列出所有隐藏账户。
 * 整行点击进入账户详情；每行提供「显示」（恢复）与「删除」操作。
 */
@Composable
private fun HiddenAccountsDialog(
    show: Boolean,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onShow: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = "已隐藏账户",
        summary = "点击进入详情，或恢复显示到账户列表",
    ) {
        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("没有已隐藏的账户", color = TextSecondary)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                accounts.forEachIndexed { index, account ->
                    HiddenAccountRow(
                        account = account,
                        onOpen = { onOpenAccount(account.id) },
                        onShow = { onShow(account) },
                        onDelete = { onDelete(account) },
                    )
                    if (index < accounts.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                        )
                    }
                }
            }
        }
    }
}

/** 单个隐藏账户行：图标 + 名称/副名/余额，右侧「显示」「删除」操作 */
@Composable
private fun HiddenAccountRow(
    account: Account,
    onOpen: () -> Unit = {},
    onShow: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIconImage(iconKey = account.iconKey, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (account.subName.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    account.subName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        Text(
            formatMoney(account.balanceCents),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (account.balanceCents < 0) AppleRed else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        IjiTextAction(
            text = "显示",
            onClick = onShow,
            compact = true,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(6.dp))
        IjiDangerTextAction(
            text = "删除",
            onClick = onDelete,
            compact = true,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Miuix 风格点状图标：三个横向小圆点 */
@Composable
private fun ThreeDotsIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextSecondary,
) {
    Row(
        modifier = modifier.size(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .background(tint, CircleShape),
            )
        }
    }
}

/**
 * 账户分类吸顶标签：分类名 + 账户数 · 小计。
 *
 * 作为「小标签」降级（12sp 次要色），避免和上方的「资金账户（N）」形成两个同权标题。
 * 必须自带背景色：吸顶时下方内容会从它背后滑过。
 */
@Composable
private fun AccountGroupHeader(
    type: AccountType,
    count: Int,
    totalCents: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                top = 6.dp,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            type.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count 个",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary.copy(alpha = 0.75f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            formatMoney(totalCents),
            style = MaterialTheme.typography.bodySmall,
            color = if (totalCents < 0) AppleRed else TextSecondary,
        )
    }
}

/** 转账弹窗：选择转入账户 + 金额 + 备注 */
@Composable
private fun TransferDialog(
    show: Boolean,
    fromAccount: String,
    accounts: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (to: String, amountCents: Long, note: String) -> Unit,
) {
    // 只有一个账户时没有可转入对象：不能放开「转账」按钮，否则会以空账户提交
    val targets = remember(accounts, fromAccount) { accounts.filter { it != fromAccount } }
    var toAccount by remember { mutableStateOf(targets.firstOrNull() ?: "") }
    var amountInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val transferAmountFocus = remember { FocusRequester() }

    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = "转账",
        confirmText = "转账",
        confirmEnabled = targets.isNotEmpty(),
        autoFocusRequester = transferAmountFocus,
        onConfirm = {
            val cents = parseCents(amountInput)
            if (cents > 0 && toAccount.isNotBlank()) onConfirm(toAccount, cents, note)
        },
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("从：", style = MaterialTheme.typography.bodyMedium)
                Text("转到：", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (targets.isEmpty()) {
                    Text(
                        "至少需要两个账户才能转账",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(targets) { name ->
                        val selected = name == toAccount
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.xxl))
                                // 未选中态跟随主题，深色下不会是浅灰底
                                .background(
                                    if (selected) {
                                        MiuixPrimary.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .clickable { toAccount = name }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(name, color = if (selected) MiuixPrimary else TextSecondary)
                        }
                    }
                }
                Text("金额", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                top.yukonga.miuix.kmp.basic.TextField(
                    amountInput,
                    { input ->
                        if (input.length <= 12) amountInput = input.filter { it.isDigit() || it == '.' }
                    },
                    Modifier.fillMaxWidth().focusRequester(transferAmountFocus),
                )
                Text("备注（可选）", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                top.yukonga.miuix.kmp.basic.TextField(
                    note,
                    { note = it.take(30) },
                    Modifier.fillMaxWidth(),
                )
            }
    }
}

/**
 * 账户区空态：区分「还没有账户」与「账户全部被隐藏」。
 * 后者再加一个账户解决不了问题，出口应当是管理已隐藏账户。
 */
@Composable
private fun AccountsEmptyCard(
    allHidden: Boolean,
    onAddAccount: () -> Unit,
    onManageHidden: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalDimens.current.screenPadding)
            .clip(RoundedCornerShape(Radius.xxl))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (allHidden) "账户都已隐藏" else "还没有账户",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (allHidden) {
                "当前没有显示中的账户；净资产仍按全部账户计算"
            } else {
                "加上微信、支付宝或银行卡，净资产才准"
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = if (allHidden) onManageHidden else onAddAccount) {
            Text(
                if (allHidden) "管理已隐藏账户" else "添加第一个账户",
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * 类型筛选：整行等宽分段控件（与统计页「日/周/月」同一套视觉语言）。
 *
 * 删掉搜索框后不再需要横向滚动：4 个选项等分整行宽度，左右留白与卡片一致，
 * 选中块铺满自己的格子，任何屏宽下都不会出现半截截断或右侧空档。
 */
@Composable
private fun AccountFilterBar(
    filter: AccountFilter,
    onFilterChange: (AccountFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalDimens.current.screenPadding)
            // 未选中项落在浅灰槽里（onSurface 8%），深色模式下不会糊成一片
            .clip(RoundedCornerShape(Radius.xxl))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountFilter.entries.forEach { option ->
            val selected = option == filter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(Radius.xxl))
                    .background(if (selected) MiuixPrimary else Color.Transparent)
                    // 已选中不再响应点击：省掉一次无意义的同值写入
                    .clickable(enabled = !selected) { onFilterChange(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) Color.White else TextSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    // 单行不换行：字少时不会被系统字体放大撑成两行
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AssetsPreview() {
    IjiTheme { AssetsScreen() }
}
