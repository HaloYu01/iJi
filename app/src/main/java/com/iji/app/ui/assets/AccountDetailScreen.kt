package com.iji.app.ui.assets

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.iji.app.ui.components.menu.LiquidGlassDropdownMenu
import com.iji.app.ui.components.menu.LiquidGlassDropdownOverlayHost
import com.iji.app.ui.components.menu.LiquidGlassDropdownMenuItem
import com.iji.app.ui.effects.glass.LocalAppBackdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.add.AddTransactionSheet
import com.iji.app.ui.components.BillDetailDialog
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.TransactionRow
import com.iji.app.ui.components.formatDay
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiButtonFocusRing
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import java.time.LocalDate
import com.iji.app.ui.util.sharedMainViewModel

/** 账户详情页 */
@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit = {},
    onNavigateEdit: (Long) -> Unit = {},
    onNavigateReconcile: (String) -> Unit = {},
    app: MainViewModel = sharedMainViewModel(),
    viewModel: AssetsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AssetsViewModel(app) as T
        },
    ),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val accounts by app.accounts.collectAsStateWithLifecycle()
    val accountsReady by app.accountsReady.collectAsStateWithLifecycle()
    val transactions by app.transactions.collectAsStateWithLifecycle()
    val transactionsReady by app.transactionsReady.collectAsStateWithLifecycle()

    val account = accounts.find { it.id == accountId }
    val accountTxs = remember(transactions, account) {
        if (account == null) {
            emptyList()
        } else {
            transactions.filter { tx ->
                tx.account == account.name || tx.splits.any { it.account == account.name }
            }.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
        }
    }

    /** 本账户在这笔账里实际承担的分额（分） */
    fun accountShareCents(tx: Transaction): Long {
        val name = account?.name ?: return tx.amountCents
        if (tx.splits.isEmpty()) return tx.amountCents
        return tx.splits.firstOrNull { it.account == name }?.amountCents ?: tx.amountCents
    }

    // 本月流入 / 流出（按本账户分担额算；转账不计入收支，与资产页口径一致）
    val thisMonth = remember { LocalDate.now() }
    val monthInCents = remember(accountTxs, thisMonth) {
        accountTxs.filter {
            it.type == TransactionType.INCOME &&
                it.date.year == thisMonth.year && it.date.month == thisMonth.month
        }.sumOf { accountShareCents(it) }
    }
    val monthOutCents = remember(accountTxs, thisMonth) {
        accountTxs.filter {
            it.type == TransactionType.EXPENSE &&
                it.date.year == thisMonth.year && it.date.month == thisMonth.month
        }.sumOf { accountShareCents(it) }
    }

    var menuOpen by remember { mutableStateOf(false) }
    // 下拉面板锚点（占位槽左上角，root 坐标）与展开 fraction（外部持有，1:1 控件要求）
    val menuAnchor = remember { mutableStateOf(Rect.Zero) }
    val menuFraction = remember { Animatable(0f) }
    var showAdd by remember { mutableStateOf(false) }
    // 交易分页：原先直接 take(50) 静默截断，用户会以为只有 50 笔
    var visibleCount by remember(accountId) { mutableStateOf(DETAIL_PAGE_SIZE) }
    val groupedTxs = remember(accountTxs, visibleCount) {
        accountTxs.take(visibleCount).groupBy { it.date }.entries.sortedByDescending { it.key }
    }
    // 附件查看器：账单 + 从第几张开始看
    var viewingAttachments by remember { mutableStateOf<Pair<Transaction, Int>?>(null) }
    var detailTx by remember { mutableStateOf<Transaction?>(null) }
    var editTx by remember { mutableStateOf<Transaction?>(null) }

    // 导出该账户全部交易为 CSV（保存位置由系统选择器决定）
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let {
            try {
                // 组合付款按本账户分担额导出，否则导出的金额与本账户实际流水对不上
                val csv = app.backupRepository.toCsv(
                    accountTxs.map { tx ->
                        val share = accountShareCents(tx)
                        if (tx.splits.size > 1 && share != tx.amountCents) {
                            tx.copy(amountCents = share)
                        } else {
                            tx
                        }
                    },
                    currencyCode = com.iji.app.data.model.CurrencyDisplay.current.code,
                )
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(csv.toByteArray())
                }
            } catch (e: Exception) {
                snackbar.show("导出失败：${e.message}")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶栏：返回 + 居中标题（账户名）+ 右上角点状菜单
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
                    account?.name ?: "账户",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // 占位槽：下拉面板的定位锚 + 点击源（popup 层的收起态玻璃钮在收起后即卸载，
            // 展开点击必须由本锚点承接——与 AssetsScreen 的 onMenuTap 同模式）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        // 触发钮无默认涟漪（NexioSchedule 同款）：反馈由面板原地弹出承接
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { menuOpen = true }
                    .onGloballyPositioned { menuAnchor.value = it.boundsInWindow() },
                contentAlignment = Alignment.Center,
            ) {
                ThreeDotsIcon(
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer {
                        // 一镜到底（NexioSchedule）：图标随 fraction 向面板内部（左下）
                        // 漂移并提前淡完（k=2.5）；收起以更小系数更早浮现（k=1.7）
                        val iconK = if (menuOpen) 2.5f else 1.7f
                        val p = (menuFraction.value * iconK).coerceIn(0f, 1f)
                        alpha = 1f - p
                        translationX = -p * 10.dp.toPx()
                        translationY = p * 10.dp.toPx()
                    },
                )
            }
        }

        // 首帧账户列表还没读出来时先给加载态，否则会误报「账户不存在」
        if (!accountsReady) {
            StateView(state = ContentState.Loading("加载中"), areaVerticalPadding = 48.dp)
            return@Column
        }
        if (account == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StateView(state = ContentState.Empty("账户不存在或已被删除"), areaVerticalPadding = 8.dp)
                IjiTextAction(text = "返回资产", onClick = onBack)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = LocalDimens.current.screenPadding,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 当前余额卡
            item(key = "balance") {
                top.yukonga.miuix.kmp.basic.Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = Radius.sheet,
                    colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                        MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Text("当前余额", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            formatMoney(account.balanceCents),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (account.includeInNetWorth) "计入净资产" else "不计入净资产",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth()) {
                            MetricCell("本月流入", monthInCents, Modifier.weight(1f))
                            MetricCell("本月流出", monthOutCents, Modifier.weight(1f))
                        }
                    }
                }
            }

            // 新增一笔交易（高频动作排在对账之前）
            item(key = "add") {
                Button(
                    onClick = { showAdd = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .ijiButtonFocusRing(shape = ContinuousRoundedRectangle(ButtonDefaults.CornerRadius)),
                ) {
                    Text("＋ 新增一笔交易", fontWeight = FontWeight.SemiBold)
                }
            }

            // 对账入口：reconciled 字段很早就有，但只能逐笔进编辑面板勾选，
            // 没有集中核对的出口 → 这里给一个带待对账笔数的入口（低频，排在高频动作之后）
            item(key = "reconcile") {
                val unreconciled = remember(accountTxs) { accountTxs.count { !it.reconciled } }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sheet))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { account?.let { onNavigateReconcile(it.name) } }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "对账",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            if (unreconciled == 0) "全部账单都已对账" else "$unreconciled 笔未对账",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unreconciled == 0) TextSecondary else MiuixPrimary,
                        )
                    }
                    Icon(
                        MiuixIcons.ChevronForward,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // 最近交易
            item(key = "recent-title") {
                Text(
                    if (transactionsReady) "最近交易（${accountTxs.size}）" else "最近交易",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (!transactionsReady) {
                item(key = "recent-loading") {
                    StateView(state = ContentState.Loading("加载中"), areaVerticalPadding = 20.dp)
                }
            } else if (accountTxs.isEmpty()) {
                item(key = "recent-empty") {
                    // 空态只说明状态：记账入口由上方「＋ 新增一笔交易」和底部全局「＋」承担，
                    // 这里不再重复放「记一笔」。
                    StateView(
                        state = ContentState.Empty("这个账户还没有交易"),
                        areaVerticalPadding = 20.dp,
                    )
                }
            } else {
                groupedTxs.forEach { (date, txs) ->
                    item(key = "day-$date") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sheet))
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            Text(
                                text = formatDay(date),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                            txs.forEachIndexed { index, tx ->
                                if (index > 0) Box(
                                    Modifier.fillMaxWidth().height(0.5.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                                )
                                TransactionRow(
                                    modifier = Modifier.clickable { detailTx = tx },
                                    tx = tx,
                                    subtitle = runCatching { CategoryCatalog.of(tx.categoryId).name }
                                        .getOrDefault(tx.categoryId),
                                    amountOverrideCents = if (tx.splits.size > 1) accountShareCents(tx) else null,
                                    onOpenAttachments = if (tx.attachments.isNotEmpty()) {
                                        { i -> viewingAttachments = tx to i }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (accountTxs.size > visibleCount) {
                item(key = "load-more") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IjiTextAction(
                            text = "加载更多（还有 ${accountTxs.size - visibleCount} 条）",
                            onClick = { visibleCount += DETAIL_PAGE_SIZE },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }

        // 下拉菜单 overlay：NexioSchedule 接入模式——面板长在全屏覆盖层（不占顶栏布局），
        // graphicsLayer 平移到占位槽位置（对齐 48dp 占位中心，控件自带 24dp 阴影内缩）。
        // ⚠️ 必须包 LiquidGlassDropdownOverlayHost（popup 层）：菜单的 drawBackdrop 采样
        // LocalAppBackdrop（NavHost 录制层），节点若落在本页布局树内会被录进采样图 →
        // RenderNode 自引用 → RenderThread 无限递归 → SIGSEGV（点开详情页即崩，实测）。
        val backdrop = LocalAppBackdrop.current
        if (backdrop != null) {
            LiquidGlassDropdownOverlayHost(expanded = menuOpen, onOutsideTap = { menuOpen = false }) {
                LiquidGlassDropdownMenu(
                    show = menuOpen,
                    backdrop = backdrop,
                    fraction = menuFraction,
                    onDismiss = { menuOpen = false },
                    // 覆盖式（End）：控制盒**右上角**恒 = 锚右 + 26dp——面板从三点按钮
                    // 位置向左下生长覆盖触发器，收起缩回按钮位置与图标衔接
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = (menuFraction.value.coerceAtLeast(0f) / 0.3f).coerceIn(0f, 1f)
                            translationX = menuAnchor.value.right + 26.dp.toPx() - size.width
                            translationY = menuAnchor.value.top - 26.dp.toPx()
                        },
                ) {
                    LiquidGlassDropdownMenuItem(
                        text = "编辑账户",
                        onClick = {
                            menuOpen = false
                            account?.let { onNavigateEdit(it.id) }
                        },
                    )
                    LiquidGlassDropdownMenuItem(
                        text = "导出 CSV",
                        onClick = {
                            menuOpen = false
                            if (accountTxs.isEmpty()) {
                                snackbar.show("该账户暂无交易可导出")
                            } else {
                                exportLauncher.launch("${account?.name ?: "账户"}_交易.csv")
                            }
                        },
                    )
                }
            }
        }
    }

    AddTransactionSheet(
        show = showAdd,
        onDismiss = { showAdd = false },
        prefillAccount = account?.name,
    )

    BillDetailDialog(
        tx = detailTx,
        onDismiss = { detailTx = null },
        onEdit = {
            detailTx = null
            editTx = it
        },
    )

    AddTransactionSheet(
        show = editTx != null,
        existing = editTx,
        onDismiss = { editTx = null },
        onConfirm = { draft ->
            editTx?.let { tx ->
                app.updateTransaction(
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

    val viewing = viewingAttachments
    com.iji.app.ui.components.AttachmentViewerDialog(
        show = viewing != null,
        attachments = viewing?.first?.attachments.orEmpty(),
        startIndex = viewing?.second ?: 0,
        onDismissRequest = { viewingAttachments = null },
    )
}

/** 账户详情首屏交易条数；每次「加载更多」再追加同样条数 */
private const val DETAIL_PAGE_SIZE = 30

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
            Box(modifier = Modifier.size(3.dp).background(tint, CircleShape))
        }
    }
}
