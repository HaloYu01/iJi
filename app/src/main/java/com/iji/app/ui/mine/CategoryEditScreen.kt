package com.iji.app.ui.mine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.data.model.Category
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.CategoryIconStore
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiFormDivider
import com.iji.app.ui.components.IjiFormSectionTitle
import com.iji.app.ui.components.IjiFormTextField
import com.iji.app.ui.components.IjiPrimaryButton
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.currentCategoryIconName
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.listCategoryIconNames
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary

/** 参照实现里 parentId 为 "-1" 表示「无父级 / 一级分类」 */
private const val NO_PARENT = "-1"

/** 名称长度上限：分类名要出现在记账选择器与统计图例里，太长会被截断 */
private const val MAX_NAME_LENGTH = 12

/** 图标网格单格最小宽度：按可用宽度自适应列数（手机上通常 4 列） */
private val ICON_CELL_MIN_WIDTH = 74.dp

/** 新建分类没有兜底色时的中性灰（与 CategoryCatalog.add 的默认色一致） */
private const val DEFAULT_CATEGORY_COLOR = 0xFF9CA3AF

/**
 * 分类编辑页（新增 / 编辑合一）。
 *
 * 【结构】对齐「添加账户页」（[com.iji.app.ui.assets.AddAccountScreen]）——一页只做三件事：
 * 1. **分类信息卡**：名称（外置字段名 + 字数计数 + 就地错误）+ 账本 / 父分类（卡内只读行）；
 * 2. **图标卡**：当前图标 + 「更换」。图标选择器是独立面板（[IjiDialog] 内 `LazyVerticalGrid`），
 *    不再是"453 个图标铺满整页"——这也是旧版最致命的问题：底部保存按钮被网格顶到 90 屏之后。
 * 3. **底部固定主按钮**：唯一保存入口，键盘弹出随之上浮。
 *
 * 【为什么图标要放进面板】旧版把整个素材库（453 项）内联在表单中间：
 * - 保存按钮要滚到底才找得到；顶栏那个是裸蓝字，和标题同级，不像按钮 → 两个入口都不好用；
 * - 网格用普通 Column 逐行组合 + `loadCategoryIcon` **同步解码**，进页即卡；
 * - 白线条 PNG 画在 25% 透明色圆上，对比度约 1.1:1，整屏糊成一片。
 *
 * 【图标可读性】单元格统一用 [CategoryIconImage]（异步 Coil + **实色**圆底），
 * 名称 10sp 单行；排序固定为「当前图标 → 本类型分类已在用的图标 → 其余」，
 * 因此当前选中的图标恒在第一格，不必滚动寻找。
 *
 * 【行为口径】
 * - 新建：名称留空（不再把素材名 "115网盘" 塞进名称框），默认图标取同类型常用图标；
 * - 编辑：名称可改（出厂分类也一样），图标按 id 存，改名不丢图标；
 * - 有改动时返回（顶栏 / 系统返回）先确认，避免白改一场。
 */
@Composable
fun CategoryEditScreen(
    categoryId: String,
    type: TransactionType,
    parentId: String,
    ledger: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val icons = remember(context) { listCategoryIconNames(context) }

    val isEdit = categoryId.isNotBlank() && categoryId != NO_PARENT
    val target: Category? = remember(categoryId, isEdit) {
        if (isEdit) runCatching { CategoryCatalog.of(categoryId) }.getOrNull() else null
    }
    // 出厂分类只是"来源"标记，同样可改名/删除；这里仅用于给出提示文案
    val builtin = target?.let { CategoryCatalog.isBuiltin(it.id) } == true

    // 同类型分类**已经在用**的图标：既做"常用"前置，也做新建分类的默认图标，
    // 免得新分类一进来就顶着「115网盘」这种和分类八竿子打不着的素材。
    val usedIcons = remember(context, type) {
        val source = when (type) {
            TransactionType.EXPENSE -> CategoryCatalog.expenses
            TransactionType.INCOME -> CategoryCatalog.incomes
            TransactionType.TRANSFER -> CategoryCatalog.transfers
        }
        source.mapNotNull { currentCategoryIconName(context, it.name, it.id) }.distinct()
    }
    val defaultIcon = remember(icons, usedIcons) { usedIcons.firstOrNull() ?: icons.firstOrNull().orEmpty() }

    // 顺序：用户覆盖 → 名字/别名解析出的当前图标 → 同类型常用 → 无（新建且素材库为空）
    val initialIcon = remember {
        target?.let {
            CategoryIconStore.get(context, it.id, it.name)
                ?: currentCategoryIconName(context, it.name, it.id)
        } ?: defaultIcon
    }
    val initialName = target?.name.orEmpty()

    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var name by remember { mutableStateOf(initialName) }
    var showNameError by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val parentName = remember(parentId) {
        if (parentId == NO_PARENT) null else CategoryCatalog.of(parentId).name
    }
    val accent = Color(target?.color ?: DEFAULT_CATEGORY_COLOR)

    val nameError = when {
        name.isBlank() -> "请填写分类名称"
        name.length > MAX_NAME_LENGTH -> "分类名称最多 $MAX_NAME_LENGTH 个字"
        else -> null
    }
    // 与回填基线逐字段比较：只改了图标也算有改动
    val dirty = name != initialName || selectedIcon != initialIcon

    fun requestBack() {
        if (dirty) showDiscardDialog = true else onBack()
    }

    fun save() {
        val newName = name.trim()
        if (nameError != null) {
            showNameError = true
            return
        }
        if (selectedIcon.isBlank()) {
            snackbar.show("必须选择图标")
            return
        }

        val current = target
        if (current == null) {
            // 新建：parentId 为 "-1" 时创建一级分类
            val created = CategoryCatalog.add(
                context = context,
                name = newName,
                type = type,
                parentId = parentId.takeIf { it != NO_PARENT },
            )
            if (created == null) {
                snackbar.show("保存失败：已存在同名分类")
                return
            }
            // 新分类拿到 id 之后才写图标（图标从此按 id 存，改名不会丢）
            CategoryIconStore.setById(context, created.id, selectedIcon)
        } else {
            // 编辑：id 不变，账单/预算/模板的引用自动跟随新名字
            if (current.name != newName) {
                if (!CategoryCatalog.update(context, current.copy(name = newName))) {
                    snackbar.show("保存失败：已存在同名分类")
                    return
                }
            }
            CategoryIconStore.setById(context, current.id, selectedIcon)
        }
        snackbar.show("保存成功")
        onBack()
    }

    // 系统返回键与顶栏返回同一套逻辑；面板打开时把返回让给面板自己（先关面板，再谈放弃）
    BackHandler(enabled = dirty && !showIconPicker && !showDiscardDialog) { showDiscardDialog = true }

    MineSubPage(title = if (isEdit) "编辑分类" else "添加分类", onBack = { requestBack() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 键盘弹出时整列让出 IME：底部主按钮浮在键盘上，而不是被挡住
                .imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = LocalDimens.current.screenPadding,
                    end = LocalDimens.current.screenPadding,
                    top = ijiTopBarContentPadding(),
                    bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap),
            ) {
                item(key = "info") {
                    NameSection(
                        name = name,
                        onNameChange = {
                            name = it.take(MAX_NAME_LENGTH)
                            showNameError = false
                        },
                        error = nameError.takeIf { showNameError },
                        ledger = ledger,
                        parentName = parentName,
                        builtin = builtin,
                        onSubmit = { save() },
                    )
                }
                item(key = "icon") {
                    IconSection(
                        icon = selectedIcon,
                        accent = accent,
                        total = icons.size,
                        onOpenPicker = { showIconPicker = true },
                    )
                }
            }

            // 底部主按钮：全页唯一保存入口（旧版还有一个在网格末尾的按钮，已经被删掉）
            Column(modifier = Modifier.fillMaxWidth()) {
                IjiFormDivider(startPadding = 0.dp)
                IjiPrimaryButton(
                    text = if (isEdit) "保存修改" else "创建分类",
                    onClick = { save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalDimens.current.screenPadding, vertical = 12.dp),
                )
            }
        }
    }

    // ── 图标选择面板 ──
    IjiDialog(
        show = showIconPicker,
        onDismissRequest = { showIconPicker = false },
        title = "选择图标",
        summary = "排序：当前图标 → 本类型常用 → 其他。点一下即替换。",
        confirmText = "完成",
        showDismiss = false,
        onConfirm = { showIconPicker = false },
    ) {
        IconPicker(
            all = icons,
            used = usedIcons,
            selected = selectedIcon,
            accent = accent,
            // 每次打开都从空搜索开始，避免看到上次留下的过滤结果却以为素材少了
            resetKey = showIconPicker,
            onPick = {
                selectedIcon = it
                showIconPicker = false
            },
        )
    }

    // ── 未保存确认 ──
    IjiDialog(
        show = showDiscardDialog,
        onDismissRequest = { showDiscardDialog = false },
        title = "放弃修改？",
        summary = "已填写的内容不会保存",
        confirmText = "放弃",
        onConfirm = {
            showDiscardDialog = false
            onBack()
        },
    )
}

/** 分类信息卡：名称（必填）+ 账本 / 父分类（只读） */
@Composable
private fun NameSection(
    name: String,
    onNameChange: (String) -> Unit,
    error: String?,
    ledger: String,
    parentName: String?,
    builtin: Boolean,
    onSubmit: () -> Unit,
) {
    Column {
        IjiFormSectionTitle("分类信息")
        IjiFormCard {
            Column(Modifier.padding(16.dp)) {
                // 字段名与字数分列两端，字数不单独占一行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("分类名称 *", Modifier.weight(1f))
                    Text(
                        "${name.length}/$MAX_NAME_LENGTH",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                    )
                }
                IjiFormTextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = "如：早餐 / 打车",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                )
                if (error != null) {
                    Text(
                        error,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleRed,
                    )
                }
            }

            IjiFormDivider()
            InfoRow(label = "账本", value = ledger)
            IjiFormDivider()
            InfoRow(
                label = "父分类",
                value = parentName ?: "无父分类",
                valueColor = if (parentName == null) TextSecondary else MaterialTheme.colorScheme.onSurface,
            )

            if (builtin) {
                IjiFormDivider()
                Text(
                    "出厂分类：可改名或删除，删掉后能从「恢复默认分类」找回；已记账单的归属不受改名影响。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

/** 图标卡：当前图标 + 更换入口（真正的选择器在面板里） */
@Composable
private fun IconSection(
    icon: String,
    accent: Color,
    total: Int,
    onOpenPicker: () -> Unit,
) {
    Column {
        IjiFormSectionTitle("图标")
        IjiFormCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPicker)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIconImage(name = icon, color = accent, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        icon.ifBlank { "未选择图标" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (total > 0) "点此更换，共 $total 个图标可选" else "点此选择图标",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                IjiTextAction(text = "更换", onClick = onOpenPicker, compact = true)
            }
        }
    }
}

/**
 * 图标选择面板内容：搜索 + 自适应网格。
 *
 * 排序与搜索都只在内存里对 453 个名字做一次过滤（比旧版"边滚边同步解码"便宜得多），
 * 因此不做防抖——输入即刷新，反而更跟手。
 */
@Composable
private fun IconPicker(
    all: List<String>,
    used: List<String>,
    selected: String,
    accent: Color,
    resetKey: Boolean,
    onPick: (String) -> Unit,
) {
    var query by remember(resetKey) { mutableStateOf("") }

    val ordered = remember(all, used, selected) {
        LinkedHashSet<String>().apply {
            if (selected.isNotBlank()) add(selected)
            addAll(used)
            addAll(all)
        }.toList()
    }
    val visible = remember(ordered, query) {
        val q = query.trim()
        if (q.isEmpty()) ordered else ordered.filter { matchesIconQuery(it, q) }
    }

    Column(Modifier.fillMaxWidth()) {
        IjiFormTextField(
            value = query,
            onValueChange = { query = it.take(20) },
            placeholder = "搜索图标（如 打车 / 吃饭 / 话费）",
        )
        Spacer(Modifier.height(10.dp))

        if (visible.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "没有匹配的图标，换个词试试（如 吃 / 车 / 房）",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // 面板内自带滚动：给一个有界高度，避免把弹窗撑出屏幕
            LazyVerticalGrid(
                columns = GridCells.Adaptive(ICON_CELL_MIN_WIDTH),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(visible, key = { it }) { icon ->
                    IconCell(
                        icon = icon,
                        accent = accent,
                        selected = icon == selected,
                        onClick = { onPick(icon) },
                    )
                }
            }
        }
    }
}

/** 图标单元：选中态 = 主色描边 + 浅主色底（同账户页），图标本身一律实色圆底 */
@Composable
private fun IconCell(
    icon: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.lg)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MiuixPrimary.copy(alpha = 0.10f) else Color.Transparent)
            // 读屏软件按"单选组里的一项"播报，并带上图标名
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "图标：$icon" }
            .then(if (selected) Modifier.border(1.5.dp, MiuixPrimary, shape) else Modifier)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CategoryIconImage(name = icon, color = accent, size = 40.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                icon,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = if (selected) MiuixPrimary else TextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/** 卡内只读行：左字段名，右取值（贴右对齐，长账本名省略） */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

/** 字段名：固定在输入框外，输入框内只留一行占位文字 */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        fontWeight = FontWeight.Medium,
    )
}

/**
 * 图标搜索匹配：素材名包含关键词，或命中日常用语的别名。
 *
 * 素材名多是品牌 / 书面词（「餐饮」「交通」），用户脑子里是「吃饭」「打车」，
 * 所以补一张小别名表；别名命中与名字命中同样处理，不做优先级区分（排序已把常用项前置）。
 */
private val ICON_SEARCH_ALIASES: Map<String, List<String>> = mapOf(
    "餐饮" to listOf("吃饭", "吃", "美食", "外卖", "饭", "餐厅", "夜宵"),
    "午餐" to listOf("正餐", "午饭"),
    "交通" to listOf("打车", "地铁", "公交", "出行", "通勤", "车", "加油", "停车"),
    "购物" to listOf("买", "超市", "商场", "日用品", "淘宝"),
    "居住" to listOf("房租", "住房", "水电", "物业", "家"),
    "住房居家" to listOf("房租", "住房", "水电", "物业", "家"),
    "娱乐" to listOf("玩", "电影", "游戏", "唱歌", "KTV"),
    "医疗" to listOf("看病", "医院", "药", "挂号", "门诊"),
    "人情" to listOf("红包", "礼物", "随礼", "请客", "份子"),
    "教育" to listOf("学费", "培训", "课程", "上课", "学习"),
    "教育学习" to listOf("学费", "培训", "课程", "上课", "学习"),
    "旅行" to listOf("旅游", "机票", "酒店", "门票", "出游"),
    "工资" to listOf("薪水", "奖金", "发工资", "津贴"),
    "理财" to listOf("利息", "分红", "投资", "基金"),
    "红包" to listOf("中奖", "压岁钱"),
    "报销款" to listOf("报销"),
    "书籍" to listOf("书", "看书", "阅读"),
    "二手" to listOf("闲置", "转卖", "闲鱼"),
    "云存储" to listOf("网盘", "云盘", "备份"),
    "中介" to listOf("租房", "佣金"),
    "人工费" to listOf("人工", "人工成本"),
    "应收" to listOf("借出", "借钱给人"),
)

private fun matchesIconQuery(iconName: String, query: String): Boolean {
    if (iconName.contains(query, ignoreCase = true)) return true
    return ICON_SEARCH_ALIASES[iconName]?.any { it.contains(query, ignoreCase = true) } == true
}
