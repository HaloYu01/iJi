package com.iji.app.ui.mine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.input.pointer.pointerInput
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
import com.iji.app.data.repository.CategoryStore
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiDialogActionButton
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
import com.iji.app.ui.theme.IjiIconSize
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** parentId 为 "-1" 表示无父级（一级分类） */
private const val NO_PARENT = "-1"

private const val MAX_NAME_LENGTH = 12

private val ICON_CELL_MIN_WIDTH = 74.dp

private const val DEFAULT_CATEGORY_COLOR = 0xFF9CA3AF

private val CATEGORY_COLORS = listOf(
    0xFF3B82F6L, 0xFFFF6B35L, 0xFF10B981L, 0xFF8B5CF6L, 0xFFEF4444L, 0xFF9CA3AFL,
)

private val HUE_COLORS = (0..6).map { Color(hsvColor(floatArrayOf(it * 60f, 1f, 1f))) }

private fun hsvColor(hsv: FloatArray): Long =
    java.lang.Integer.toUnsignedLong(android.graphics.Color.HSVToColor(hsv))

/** 分类编辑页（新增 / 编辑合一） */
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
    val builtin = target?.let { CategoryCatalog.isBuiltin(it.id) } == true

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

    val initialColor = remember {
        target?.color
            ?: parentId.takeIf { it != NO_PARENT }?.let { CategoryCatalog.of(it).color }
            ?: DEFAULT_CATEGORY_COLOR
    }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var name by remember { mutableStateOf(initialName) }
    var showNameError by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var savedColors by remember { mutableStateOf(CategoryStore.savedColors(context)) }
    var hsv by remember { mutableStateOf(floatArrayOf(210f, 0.7f, 0.95f)) }
    val pickedColor = hsvColor(hsv)
    val palette = remember(savedColors) { (CATEGORY_COLORS + savedColors).distinct() }

    fun openColorPicker() {
        hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(selectedColor.toInt(), it) }
        showColorPicker = true
    }

    val parentName = remember(parentId) {
        if (parentId == NO_PARENT) null else CategoryCatalog.of(parentId).name
    }
    val accent = Color(selectedColor)

    val nameError = when {
        name.isBlank() -> "请填写分类名称"
        name.length > MAX_NAME_LENGTH -> "分类名称最多 $MAX_NAME_LENGTH 个字"
        else -> null
    }
    val dirty = name != initialName || selectedIcon != initialIcon || selectedColor != initialColor

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
            val created = CategoryCatalog.add(
                context = context,
                name = newName,
                type = type,
                parentId = parentId.takeIf { it != NO_PARENT },
                color = selectedColor,
            )
            if (created == null) {
                snackbar.show("保存失败：已存在同名分类")
                return
            }
            CategoryIconStore.setById(context, created.id, selectedIcon)
        } else {
            if (current.name != newName || current.color != selectedColor) {
                if (!CategoryCatalog.update(context, current.copy(name = newName, color = selectedColor))) {
                    snackbar.show("保存失败：已存在同名分类")
                    return
                }
            }
            CategoryIconStore.setById(context, current.id, selectedIcon)
        }
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
                item(key = "color") {
                    ColorSection(
                        colors = palette,
                        selected = selectedColor,
                        onSelect = { selectedColor = it },
                        onOpenPicker = { openColorPicker() },
                    )
                }
            }

            // 底部主按钮：全页唯一保存入口
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
            resetKey = showIconPicker,
            onPick = {
                selectedIcon = it
                showIconPicker = false
            },
        )
    }

    IjiDialog(
        show = showColorPicker,
        onDismissRequest = { showColorPicker = false },
        title = "调色盘",
    ) {
        ColorPickerContent(
            color = pickedColor,
            hsv = hsv,
            onHsvChange = { hsv = it },
            onCancel = { showColorPicker = false },
            onSave = {
                val picked = hsvColor(hsv)
                CategoryStore.saveColor(context, picked)
                savedColors = CategoryStore.savedColors(context)
                selectedColor = picked
                showColorPicker = false
            },
            onUseOnce = {
                selectedColor = hsvColor(hsv)
                showColorPicker = false
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
                CategoryIconImage(name = icon, color = accent, size = IjiIconSize.EditPreview)
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

@Composable
private fun ColorSection(
    colors: List<Long>,
    selected: Long,
    onSelect: (Long) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.pill)
    Column {
        IjiFormSectionTitle("图标颜色")
        IjiFormCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                colors.forEachIndexed { index, value ->
                    val active = value == selected
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(shape)
                            .background(Color(value))
                            .border(
                                2.dp,
                                if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape,
                            )
                            .selectable(
                                selected = active,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) },
                            )
                            .semantics { contentDescription = "颜色 ${index + 1}" },
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(shape)
                        .clickable(onClick = onOpenPicker)
                        .semantics { contentDescription = "自定义颜色" },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val radius = size.minDimension / 2f
                        drawCircle(brush = Brush.sweepGradient(HUE_COLORS), radius = radius)
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color.White, Color.White.copy(alpha = 0f)),
                                center = center,
                                radius = radius,
                            ),
                            radius = radius,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerContent(
    color: Long,
    hsv: FloatArray,
    onHsvChange: (FloatArray) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onUseOnce: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorWheel(hsv = hsv, onHsvChange = onHsvChange)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(Color(color)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "#${color.toString(16).takeLast(6).uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IjiDialogActionButton("取消", onCancel, Modifier.weight(1f))
            IjiDialogActionButton("保存颜色", onSave, Modifier.weight(1f), prominent = true)
            IjiDialogActionButton("本次使用", onUseOnce, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColorWheel(hsv: FloatArray, onHsvChange: (FloatArray) -> Unit) {
    val latestHsv by rememberUpdatedState(hsv)
    val hueColor = Color(hsvColor(floatArrayOf(hsv[0], 1f, 1f)))
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width, size.height) / 2f
                val ring = 28.dp.toPx()
                val half = (radius - ring - 8.dp.toPx()) / sqrt(2f)
                fun update(pos: Offset) {
                    val dx = pos.x - c.x
                    val dy = pos.y - c.y
                    val next = latestHsv.copyOf()
                    if (sqrt(dx * dx + dy * dy) > radius - ring) {
                        next[0] = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
                    } else {
                        next[1] = ((pos.x - c.x) / (2f * half) + 0.5f).coerceIn(0f, 1f)
                        next[2] = (0.5f - (pos.y - c.y) / (2f * half)).coerceIn(0f, 1f)
                    }
                    onHsvChange(next)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        update(change.position)
                    }
                }
            },
    ) {
        val c = center
        val radius = size.minDimension / 2f
        val ring = 28.dp.toPx()
        drawCircle(
            brush = Brush.sweepGradient(HUE_COLORS),
            radius = radius - ring / 2f,
            center = c,
            style = Stroke(ring),
        )
        val half = (radius - ring - 8.dp.toPx()) / sqrt(2f)
        val topLeft = Offset(c.x - half, c.y - half)
        val square = Size(half * 2f, half * 2f)
        val corner = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.White, hueColor)),
            topLeft = topLeft,
            size = square,
            cornerRadius = corner,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            topLeft = topLeft,
            size = square,
            cornerRadius = corner,
        )
        val svCenter = Offset(c.x - half + hsv[1] * 2f * half, c.y + half - hsv[2] * 2f * half)
        drawCircle(Color.White, 7.dp.toPx(), svCenter, style = Stroke(2.dp.toPx()))
        drawCircle(Color.Black, 5.dp.toPx(), svCenter, style = Stroke(1.dp.toPx()))
        val angle = Math.toRadians(hsv[0].toDouble())
        val hueRadius = radius - ring / 2f
        val hueCenter = Offset(
            c.x + cos(angle).toFloat() * hueRadius,
            c.y + sin(angle).toFloat() * hueRadius,
        )
        drawCircle(Color.White, 9.dp.toPx(), hueCenter, style = Stroke(2.dp.toPx()))
        drawCircle(Color.Black, 7.dp.toPx(), hueCenter, style = Stroke(1.dp.toPx()))
    }
}

/** 图标选择面板：搜索 + 自适应网格 */
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
            CategoryIconImage(name = icon, color = accent, size = IjiIconSize.Grid)
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

/** 图标搜索匹配：素材名或日常用语别名 */
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
