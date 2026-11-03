package com.iji.app.ui.mine

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.CategoryUsage
import com.iji.app.MainViewModel
import com.iji.app.data.model.Category
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.CategoryIconStore
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiDialogActionButton
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 网格列数：与参照实现 CategoryComponent 的 `line = 5` 保持一致 */
private const val GRID_LINE = 5

/** 参照实现里 parentId 为 "-1" 表示「无父级 / 一级分类」 */
private const val NO_PARENT = "-1"

/** 网格显示项：普通分类格 / 子分类面板（占满整行） */
private sealed interface GridItem {
    /** [add] 为 true 时是「添加」伪分类格（对应参照实现 createAddButtonItem） */
    data class Cell(val category: Category?, val add: Boolean) : GridItem

    /** 子分类面板：内部为二级分类的同款网格 */
    data class Panel(val parent: Category) : GridItem
}

/**
 * 分类管理页 —— 对照 AutoAccounting `CategoryFragment` + `CategoryPageFragment` + `CategoryComponent` 复刻。
 *
 * 结构对应关系：
 * - `MaterialToolbar`（返回 + 标题 + `@menu/category_menu`）→ [MineSubPage]
 * - `TabLayout`（支出 / 收入）→ [TabRow]（本项目多一个「转账」页，为数据模型所需）
 * - `ViewPager2` + `CategoryComponent` → 同一 Tab 下切换的 5 列网格
 * - 点击父分类就地插入/移除子面板 → [expandedParentId] 单状态
 * - `onItemLongClick` → 弹出「编辑 / 删除」
 * - overflow「恢复默认分类」/「排序」→ 确认弹窗 / 排序模式
 *
 * **与参照实现的行为差异（本次改造）**：
 * 1. 出厂分类不再是"不可编辑的常量"，与自建分类同权：可改名、可删除、可排序。
 * 2. 删除走「引用迁移」：该分类（含子分类）下的账单 / 预算 / 周期账单 / 模板
 *    一起改挂到「其他」，删除前在弹窗里明确告知影响面。
 * 3. 新增「排序」模式：单列拖动，松手才写盘（复用资产页已验证的拖拽实现）。
 * 4. 移除「切换账本」：分类是全局目录，那个入口什么都不会改，属假入口。
 */
@Composable
fun CategoryManageScreen(
    onBack: () -> Unit,
    onNavigateEdit: (categoryId: String, type: TransactionType, parentId: String, ledger: String) -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    // 目录存在 SharedPreferences + 内存缓存，增删改后靠 refresh 触发重读
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    // 当前展开的子面板（同一时刻仅一个）
    var expandedParentId by remember { mutableStateOf<String?>(null) }
    // 长按弹出的操作菜单目标
    var menuTarget by remember { mutableStateOf<Category?>(null) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var deleteUsage by remember { mutableStateOf<CategoryUsage?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    // 排序模式：null = 未进入；进入后 sortParentId 为 null 表示排一级分类，否则排该父下的二级
    var sortMode by remember { mutableStateOf(false) }
    var sortParentId by remember { mutableStateOf<String?>(null) }

    val currentType = when (selectedTab) {
        0 -> TransactionType.EXPENSE
        1 -> TransactionType.INCOME
        else -> TransactionType.TRANSFER
    }
    val isTransfer = currentType == TransactionType.TRANSFER
    val ledger = remember { viewModel.currentLedger.value }

    // 分类列表：转账不分类层级，单列展示
    val parents = remember(refresh, selectedTab) {
        if (isTransfer) CategoryCatalog.transfers else CategoryCatalog.parentsOfType(currentType)
    }
    // 搜索过滤：父名命中保留全部子项；仅子项命中则保留该父（作为上下文）
    val filteredParents = remember(parents, query) {
        val q = query.trim()
        if (q.isBlank()) {
            parents
        } else {
            parents.filter { p ->
                p.name.contains(q, true) ||
                    CategoryCatalog.childrenOf(p.id).any { it.name.contains(q, true) }
            }
        }
    }

    // 行模型：先把分类切成每行 5 格，再在「展开父分类所在行」之后插入整行子面板
    val rows = remember(filteredParents, expandedParentId, isTransfer) {
        val cells: List<GridItem> = filteredParents.map { GridItem.Cell(it, false) } +
            // 「添加一级分类」伪分类格，追加到列表末尾
            if (isTransfer) emptyList() else listOf(GridItem.Cell(null, true))
        buildList {
            cells.chunked(GRID_LINE).forEach { row ->
                add(row)
                row.filterIsInstance<GridItem.Cell>()
                    .firstOrNull { it.category?.id == expandedParentId }
                    ?.category
                    ?.let { add(listOf(GridItem.Panel(it))) }
            }
        }
    }

    fun openEditor(categoryId: String, parentId: String) {
        menuTarget = null
        onNavigateEdit(categoryId, currentType, parentId, ledger)
    }

    // 删除前统计影响面（含子分类）：账单 / 预算 / 周期账单 / 模板
    LaunchedEffect(pendingDelete) {
        deleteUsage = null
        pendingDelete?.let { deleteUsage = viewModel.categoryUsage(context, it) }
    }

    MineSubPage(
        title = when {
            !sortMode -> "分类管理"
            sortParentId == null -> "排序分类"
            else -> "排序 · ${CategoryCatalog.of(sortParentId!!).name}"
        },
        onBack = {
            when {
                sortMode && sortParentId != null -> sortParentId = null
                sortMode -> sortMode = false
                else -> onBack()
            }
        },
        endAction = {
            if (sortMode) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { sortMode = false; sortParentId = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("完成", color = MiuixPrimary, fontWeight = FontWeight.SemiBold)
                }
            } else {
                var menuOpen by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MiuixIcons.More,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp),
                    )
                    if (menuOpen) {
                        OverlayCascadingListPopup(
                            show = true,
                            entries = listOf(
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(text = "排序分类", onClick = {
                                            menuOpen = false
                                            sortMode = true
                                            sortParentId = null
                                        }),
                                        DropdownItem(text = "恢复默认分类", onClick = {
                                            menuOpen = false
                                            showRestoreConfirm = true
                                        }),
                                    ),
                                ),
                            ),
                            onDismissRequest = { menuOpen = false },
                            renderInRootScaffold = true,
                        )
                    }
                }
            }
        },
    ) {
        if (sortMode) {
            // ── 排序模式：单列拖动，松手才写盘 ──
            val level = remember(refresh, selectedTab, sortParentId) {
                if (sortParentId == null) parents else CategoryCatalog.childrenOf(sortParentId!!)
            }
            val parentOfLevel = sortParentId?.let { CategoryCatalog.of(it) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = ijiTopBarContentPadding())
                    .padding(horizontal = LocalDimens.current.screenPadding)
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                Text(
                    if (parentOfLevel == null) {
                        "按住分类上下拖动调整顺序，一级分类的顺序会同步到记一笔面板。"
                    } else {
                        "正在调整「${parentOfLevel.name}」下的二级分类顺序。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                if (level.isEmpty()) {
                    Text(
                        "这一层还没有分类",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    SortableCategoryList(
                        categories = level,
                        onOpenChildren = if (parentOfLevel == null) {
                            { parent -> sortParentId = parent.id }
                        } else {
                            null
                        },
                        onPersist = { orderedIds ->
                            val ok = CategoryCatalog.reorder(context, orderedIds)
                            if (ok) {
                                refresh++
                                snackbar.show("排序已保存")
                            } else {
                                snackbar.show("排序保存失败，请重试")
                            }
                        },
                    )
                }
            }
            return@MineSubPage
        }

        // ── Tab：支出 / 收入 / 转账 ──
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(top = ijiTopBarContentPadding()),
        ) {
            listOf("支出", "收入", "转账").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        query = ""
                        expandedParentId = null
                        menuTarget = null
                    },
                    text = { Text(title) },
                )
            }
        }

        // 搜索框（参照实现的列表页无搜索，此项为既有设计保留）
        if (!isTransfer) {
            top.yukonga.miuix.kmp.basic.TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LocalDimens.current.screenPadding)
                    .padding(top = 8.dp),
                label = "搜索分类",
            )
        }

        if (filteredParents.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (query.isBlank()) "还没有分类" else "未找到匹配的分类",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (query.isBlank()) {
                    Spacer(Modifier.height(14.dp))
                    IjiDialogActionButton(
                        text = "新建分类",
                        onClick = { openEditor("", NO_PARENT) },
                        prominent = true,
                        modifier = Modifier.padding(horizontal = 60.dp),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LocalDimens.current.screenPadding)
                    .padding(top = 10.dp, bottom = 24.dp),
            ) {
                rows.forEach { row ->
                    val panel = row.filterIsInstance<GridItem.Panel>().firstOrNull()
                    if (panel != null) {
                        ChildCategoryPanel(
                            parent = panel.parent,
                            onAddChild = { parent -> openEditor("", parent.id) },
                            onChildClick = { },
                            onChildLongClick = { menuTarget = it },
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            row.filterIsInstance<GridItem.Cell>().forEach { cell ->
                                Box(modifier = Modifier.weight(1f)) {
                                    CategoryGridCell(
                                        category = cell.category,
                                        isAdd = cell.add,
                                        hasChild = cell.category?.let {
                                            CategoryCatalog.childrenOf(it.id).isNotEmpty()
                                        } ?: false,
                                        onClick = {
                                            if (cell.add) {
                                                openEditor("", NO_PARENT)
                                            } else if (cell.category != null) {
                                                expandedParentId =
                                                    if (expandedParentId == cell.category.id) null
                                                    else cell.category.id
                                            }
                                        },
                                        onLongClick = { cell.category?.let { menuTarget = it } },
                                    )
                                }
                            }
                            repeat(GRID_LINE - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }

    // ── 长按分类后的操作面板 ──
    //
    // 刻意不用 OverlayCascadingListPopup：长按是在**手指还按着**的时候触发的，
    // 松手那一下会被弹窗当作"点击外部"直接关掉（真机上表现为菜单一闪而过）。
    // 换成弹窗式对话框：残余的抬手事件不会关它，点「编辑 / 删除 / 取消」才走。
    menuTarget?.let { target ->
        IjiDialog(
            show = true,
            onDismissRequest = { menuTarget = null },
            title = target.name,
            summary = "改名不会影响已记账单的分类归属；删除前会告诉你影响了多少笔账单。",
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                IjiDialogActionButton(
                    text = "编辑",
                    onClick = { openEditor(target.id, target.parentId ?: NO_PARENT) },
                    prominent = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                IjiDialogActionButton(
                    text = "删除",
                    onClick = {
                        menuTarget = null
                        pendingDelete = target
                    },
                    // prominent 才会用 color 上色；非 prominent 会退回次要文字色，"危险"感就没了
                    prominent = true,
                    color = AppleRed,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // ── 恢复默认分类确认 ──
    if (showRestoreConfirm) {
        IjiDialog(
            show = true,
            onDismissRequest = { showRestoreConfirm = false },
            title = "恢复默认分类",
            summary = "将删除全部自建分类与你的改名/排序改动，回到出厂分类目录；已记账单的分类归属不变。此操作无法撤销。",
            confirmText = "恢复",
            confirmColor = AppleRed,
            onConfirm = {
                val ok = CategoryCatalog.restoreDefaults(context)
                showRestoreConfirm = false
                expandedParentId = null
                refresh++
                snackbar.show(if (ok) "已恢复默认分类" else "恢复失败，请重试")
            },
        )
    }

    // ── 删除确认（含影响面）──
    pendingDelete?.let { category ->
        val childCount = CategoryCatalog.childrenOf(category.id).size
        val usage = deleteUsage
        IjiDialog(
            show = true,
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = "删除分类",
            summary = buildString {
                append("「${category.name}」")
                if (childCount > 0) append("下有 $childCount 个二级分类，将一并删除；")
                if (usage == null) {
                    append("正在统计受影响的账单…")
                } else {
                    if (usage.bills > 0) append("${usage.bills} 笔账单将改挂「其他」；")
                    if (usage.budgets > 0) append("${usage.budgets} 项预算将改挂「其他」；")
                    if (usage.recurring > 0) append("${usage.recurring} 条周期账单将改挂「其他」；")
                    if (usage.templates > 0) append("${usage.templates} 个模板将改挂「其他」；")
                    if (usage.bills + usage.budgets + usage.recurring + usage.templates == 0) {
                        append("没有账单或配置在使用它；")
                    }
                }
                append("此操作无法撤销。")
            },
            confirmText = if (deleting) "删除中…" else "删除",
            confirmColor = AppleRed,
            confirmEnabled = usage != null && !deleting,
            onConfirm = {
                if (!deleting) {
                    scope.launch {
                        deleting = true
                        val ok = viewModel.deleteCategory(context, category)
                        deleting = false
                        if (ok) {
                            pendingDelete = null
                            expandedParentId = null
                            refresh++
                            snackbar.show("已删除「${category.name}」")
                        } else {
                            // 失败时保留弹窗与统计：用户重试即可，不会出现"以为删了其实没删"
                            snackbar.show("删除失败，请重试")
                        }
                    }
                }
            },
        )
    }
}

/**
 * 排序模式列表：单列，长按整行拖动，越过相邻行中点即换位，松手才写盘。
 *
 * 直接复用资产页 `AccountGroupCard` 的成熟做法（同项目内已验证）：
 * 本地顺序即时换位 + `graphicsLayer.translationY` 跟随手指 + `rememberUpdatedState`
 * 避免 `pointerInput` 闭包抓到过期状态。
 */
@Composable
private fun SortableCategoryList(
    categories: List<Category>,
    onOpenChildren: ((Category) -> Unit)?,
    onPersist: (List<String>) -> Unit,
) {
    var order by remember { mutableStateOf(categories) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    val haptics = LocalHapticFeedback.current

    // 非拖拽时跟随外部数据（排序落盘后的新顺序），拖拽中不打断手势
    LaunchedEffect(categories) {
        if (draggingId == null) order = categories
    }

    fun move(from: Int, to: Int) {
        if (from == to || to !in order.indices) return
        order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun handleDrag(id: String, dy: Float) {
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
        val moved = draggingId != null && order.map { it.id } != categories.map { it.id }
        draggingId = null
        dragOffset = 0f
        // 顺序没变就不写盘，避免点一下也产生一堆写操作
        if (moved) onPersist(order.map { it.id })
    }

    /**
     * 上下微调一格（行尾 ↑↓ 按钮）。
     *
     * 拖动是主交互，但"长按再拖"对手指不灵活的用户、以及无障碍读屏用户都不友好；
     * 补一对按钮后，排序既可以用拖的，也可以一次挪一格，且点一下就能落盘、可预期。
     */
    fun nudge(from: Int, delta: Int) {
        val to = from + delta
        if (to !in order.indices) return
        move(from, to)
        onPersist(order.map { it.id })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        order.forEachIndexed { index, category ->
            key(category.id) {
                val dragging = draggingId == category.id
                val startDrag = rememberUpdatedState<() -> Unit> {
                    draggingId = category.id
                    dragOffset = 0f
                }
                val dragBy = rememberUpdatedState<(Float) -> Unit> { dy -> handleDrag(category.id, dy) }
                val endDrag = rememberUpdatedState<() -> Unit> { finishDrag() }
                SortableCategoryRow(
                    category = category,
                    childCount = CategoryCatalog.childrenOf(category.id).size,
                    trailingArrow = onOpenChildren != null && CategoryCatalog.childrenOf(category.id).isNotEmpty(),
                    onOpenChildren = { onOpenChildren?.invoke(category) },
                    canMoveUp = index > 0,
                    canMoveDown = index < order.lastIndex,
                    onMoveUp = { nudge(order.indexOfFirst { it.id == category.id }, -1) },
                    onMoveDown = { nudge(order.indexOfFirst { it.id == category.id }, 1) },
                    modifier = Modifier
                        .onGloballyPositioned { rowHeights[category.id] = it.size.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .pointerInput(category.id) {
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
                )
                if (index < order.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 68.dp)
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortableCategoryRow(
    category: Category,
    childCount: Int,
    trailingArrow: Boolean,
    onOpenChildren: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconImage(
            name = category.name,
            color = Color(category.color),
            size = 36.dp,
            categoryId = category.id,
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (childCount > 0) {
                Text(
                    "$childCount 个子分类",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        if (trailingArrow) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.lg))
                    .clickable(onClick = onOpenChildren)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    MiuixIcons.ChevronForward,
                    contentDescription = "排序子分类",
                    tint = MiuixPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // 上下微调：拖不动、或者只想挪一格时用；点一下立刻落盘
        MoveStepButton(text = "↑", enabled = canMoveUp, description = "上移", onClick = onMoveUp)
        MoveStepButton(text = "↓", enabled = canMoveDown, description = "下移", onClick = onMoveDown)
        // 拖拽把手：纯装饰，实际拖拽作用在整行上
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(width = 22.dp, height = 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(width = 16.dp, height = 2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(TextSecondary.copy(alpha = 0.6f)),
                    )
                }
            }
        }
    }
}

/**
 * 排序行的上/下移按钮：**48×48dp** 命中区（无障碍最小触摸目标），
 * 禁用时置灰但仍占位（避免行内元素跳动）。
 *
 * 原先只有 36×40dp：视觉上够用，但拖动排序时手本来就抖，差 8dp 就容易点空。
 */
@Composable
private fun MoveStepButton(
    text: String,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.25f),
        )
    }
}

/**
 * 分类网格格：圆形图标 + 名称；有二级分类时右上角显示「更多」提示。
 * 对应参照实现 `CategorySelectorAdapter` 的普通分类项（root 同时绑定点击与长按）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryGridCell(
    category: Category?,
    isAdd: Boolean,
    hasChild: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isAdd) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MiuixPrimary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MiuixIcons.Add,
                        contentDescription = "添加",
                        tint = MiuixPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else if (category != null) {
                CategoryIconImage(
                    name = category.name,
                    color = Color(category.color),
                    size = 40.dp,
                    categoryId = category.id,
                )
            }
            // 「更多」提示（对应 categoryIconMore）
            if (hasChild && category != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(category.color)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MiuixIcons.ChevronForward,
                        contentDescription = "含二级分类",
                        tint = Color.White,
                        modifier = Modifier.size(9.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            if (isAdd) "添加" else category?.name.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (isAdd) MiuixPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
        )
    }
}

/**
 * 子分类面板：占满整行，内部是二级分类的同款 5 列网格，末尾带「添加」格。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChildCategoryPanel(
    parent: Category,
    onAddChild: (Category) -> Unit,
    onChildClick: (Category) -> Unit,
    onChildLongClick: (Category) -> Unit,
) {
    val children = remember(parent.id) { CategoryCatalog.childrenOf(parent.id) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(Radius.xl))
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        children.chunked(GRID_LINE).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { child ->
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryGridCell(
                            category = child,
                            isAdd = false,
                            hasChild = false,
                            onClick = { onChildClick(child) },
                            onLongClick = { onChildLongClick(child) },
                        )
                    }
                }
                repeat(GRID_LINE - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                CategoryGridCell(
                    category = null,
                    isAdd = true,
                    hasChild = false,
                    onClick = { onAddChild(parent) },
                    onLongClick = { },
                )
            }
            repeat(GRID_LINE - 1) { Spacer(Modifier.weight(1f)) }
        }
    }
}


