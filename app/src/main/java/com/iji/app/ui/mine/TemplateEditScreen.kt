package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.Category
import com.iji.app.data.model.PaymentSplit
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.TemplateRepository
import com.iji.app.data.repository.TransactionTemplate
import com.iji.app.ui.components.AccountIconImage
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.IjiDangerTextAction
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiFormDivider
import com.iji.app.ui.components.IjiSwitchRow
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.DividerGray
import com.iji.app.ui.theme.IjiIconSize
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import com.iji.app.ui.util.sharedMainViewModel

/**
 * 记账模板编辑页（新建 / 修改同页；路由 `template_edit/{templateId}`，`new` = 新建）。
 *
 * 为什么从弹窗改成整页：旧版把「名称 + 类型 + 两级分类 + 金额 + 备注」塞进一个
 * 460dp 可滚动弹窗，键盘一弹、分类一铺，表单就挤成一团；本 App 其他编辑流
 * （本地规则 / 提示词 / 账户）都是整页子页，这里对齐同一交互口径。
 *
 * 保存口径与旧版一致：名称、金额必填，校验不过只提示不关页。
 * 删除入口放在页内（带确认弹窗）——旧版列表行上裸露「删除」且无确认，误触即删。
 */
@Composable
fun TemplateEditScreen(templateId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarController.current
    // 资产账户：组合付款拆分行的账户候选（与记一笔面板同一来源）
    val app: MainViewModel = sharedMainViewModel()
    val accounts by app.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val accountOptions = remember(accounts) {
        accounts.map { it.name }.filter { it.isNotBlank() }.distinct()
    }

    val editId = templateId.toLongOrNull()
    val templates by TemplateRepository.observe(context)
        .collectAsStateWithLifecycle(initialValue = null)
    val existing = editId?.let { id -> templates?.firstOrNull { it.id == id } }

    // 新建立即给空草稿；编辑等 DataStore 首次发射后回填**一次**，
    // 之后用户的改动不会被后续发射覆盖（draft 已非 null 就不再赋值）
    var draft by remember(editId) {
        mutableStateOf(if (editId == null) TransactionTemplate().normalized() else null)
    }
    LaunchedEffect(editId, existing) {
        if (draft == null && existing != null) draft = existing.normalized()
    }

    // 路由参数指到不存在的模板（编辑中被删 / 手改路由）：提示后退出，不留空壳页。
    // 必须以 draft == null 为前提：本页自己删除后流会重发成「不存在」，此时删除回调
    // 已经在退出，这里再触发就是双重 snackbar + 双重 popBackStack（会退过头）。
    LaunchedEffect(editId, templates) {
        if (editId != null && templates != null && existing == null && draft == null) {
            snackbar.show("模板不存在或已被删除")
            onBack()
        }
    }

    var showDelete by remember { mutableStateOf(false) }

    fun save() {
        val d = draft ?: return
        if (d.name.isBlank()) {
            snackbar.show("请填写模板名称")
            return
        }
        if (d.amountCents <= 0L) {
            snackbar.show("请填写金额")
            return
        }
        // 组合付款：与记一笔面板同规则——至少两个账户、合计等于模板金额；
        // 不完整的行（没选账户 / 金额为 0）直接剔除
        val validSplits = d.splits.filter { it.account.isNotBlank() && it.amountCents > 0L }
        if (validSplits.isNotEmpty()) {
            if (validSplits.size < 2) {
                snackbar.show("组合付款至少需要两个账户")
                return
            }
            if (validSplits.sumOf { it.amountCents } != d.amountCents) {
                snackbar.show("组合付款合计需等于模板金额")
                return
            }
        }
        scope.launch {
            TemplateRepository.upsert(
                context,
                d.copy(splits = validSplits.takeIf { it.size >= 2 }.orEmpty()),
            )
        }
        snackbar.show(if (editId == null) "已创建模板" else "已保存修改")
        onBack()
    }

    MineSubPage(
        title = if (editId == null) "新建模板" else "编辑模板",
        onBack = onBack,
        endAction = {
            IjiTextAction(text = "保存", onClick = { save() }, compact = true)
        },
    ) {
        val d = draft
        if (d == null) {
            // 编辑态等 DataStore 首帧发射（毫秒级），不需要骨架屏
            Box(Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LocalDimens.current.screenPadding)
                    .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── 基本：名称 / 类型 / 分类 ──
                IjiFormCard {
                    Column(Modifier.padding(16.dp)) {
                        TemplateForm(
                            draft = d,
                            onChange = { draft = it },
                        )
                    }
                }

                // ── 金额与支付：优惠 / 组合付款仅支出、收入有意义（转账走转出→转入）──
                IjiFormCard {
                    Column(Modifier.padding(16.dp)) {
                        AmountField(
                            amountCents = d.amountCents,
                            onChanged = { draft = d.copy(amountCents = it) },
                        )
                        if (d.type != TransactionType.TRANSFER) {
                            Spacer(Modifier.height(12.dp))
                            AccountField(
                                account = d.account,
                                accountOptions = accountOptions,
                                onChange = { draft = d.copy(account = it) },
                            )
                            Spacer(Modifier.height(12.dp))
                            SplitsEditor(
                                splits = d.splits,
                                totalCents = d.amountCents,
                                accountOptions = accountOptions,
                                onChange = { draft = d.copy(splits = it) },
                            )
                            Spacer(Modifier.height(12.dp))
                            AmountField(
                                amountCents = d.discountCents,
                                onChanged = { draft = d.copy(discountCents = it) },
                                label = "优惠金额（可选）",
                            )
                        }
                    }
                }

                // ── 计入标记 + 备注：转账天然不进收支 / 预算统计，只留备注 ──
                IjiFormCard {
                    Column {
                        if (d.type != TransactionType.TRANSFER) {
                            // 与记一笔面板「计入标记」同款：开关语义是「计入」，排除项取反
                            IjiSwitchRow(
                                title = "计入收支",
                                desc = "关闭后不计入本月收支统计与分类汇总",
                                checked = !d.excludeFromStats,
                                onCheckedChange = { draft = d.copy(excludeFromStats = !it) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            IjiFormDivider()
                            IjiSwitchRow(
                                title = "计入预算",
                                desc = "关闭后不计入预算进度（仍计入收支）",
                                checked = !d.excludeFromBudget,
                                onCheckedChange = { draft = d.copy(excludeFromBudget = !it) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            IjiFormDivider()
                        }
                        Column(Modifier.padding(16.dp)) {
                            TextField(
                                value = d.note,
                                onValueChange = { draft = d.copy(note = it.take(50)) },
                                modifier = Modifier.fillMaxWidth(),
                                label = "备注（可选）",
                                useLabelAsPlaceholder = true,
                            )
                        }
                    }
                }

                if (editId != null) {
                    IjiFormCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("删除这条模板", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "仅移除模板本身，已记录的账单不受影响",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            IjiDangerTextAction(
                                text = "删除",
                                compact = true,
                                onClick = { showDelete = true },
                            )
                        }
                    }
                }
            }
        }
    }

    IjiDialog(
        show = showDelete,
        onDismissRequest = { showDelete = false },
        title = "删除模板",
        summary = "「${draft?.name.orEmpty()}」将从模板列表移除，已记录的账单不受影响。",
        confirmText = "删除",
        confirmColor = MaterialTheme.colorScheme.error,
        onConfirm = {
            showDelete = false
            val id = editId ?: return@IjiDialog
            scope.launch { TemplateRepository.remove(context, id) }
            snackbar.show("已删除模板")
            onBack()
        },
    )
}

/** 类型 → 该类型全部分类（含二级），按用户排序 */
private fun categoriesOf(type: TransactionType): List<Category> = when (type) {
    TransactionType.EXPENSE -> CategoryCatalog.expenses
    TransactionType.INCOME -> CategoryCatalog.incomes
    TransactionType.TRANSFER -> CategoryCatalog.transfers
}

/**
 * 归一化分类：分类被删 / 改类型后 categoryId 可能悬空。
 *
 * 悬空时退回该类型第一个分类，而不是留空 —— 留空在界面上就是一个灰底"未分类"，
 * 既难看，也会让模板记出来的账落进未分类。
 */
private fun TransactionTemplate.normalized(): TransactionTemplate {
    val list = categoriesOf(type)
    if (list.any { it.id == categoryId }) return this
    return copy(categoryId = list.firstOrNull()?.id.orEmpty())
}

/**
 * 模板基本表单：名称 → 类型 → 分类（图标网格），必填在前。
 *
 * 分类是**带图标的网格**而不是文字胶囊：分类本身是带图标的数据（列表行、
 * 记一笔面板都在用图标），纯文字认不出谁是谁。交互与记一笔面板一致——
 * 点一级 = 直接选中它并展示它的二级；选中的图标转白、文字染分类色。
 */
@Composable
private fun TemplateForm(
    draft: TransactionTemplate,
    onChange: (TransactionTemplate) -> Unit,
) {
    val categories = remember(draft.type) { categoriesOf(draft.type) }
    val parents = remember(categories) { categories.filter { it.parentId == null } }
    val current = categories.firstOrNull { it.id == draft.categoryId }

    // 当前选中的一级分类：选二级时高亮它所属的一级
    var pickedParentId by remember(draft.type, draft.id) {
        mutableStateOf(current?.parentId ?: current?.id ?: parents.firstOrNull()?.id)
    }
    LaunchedEffect(current?.id) {
        pickedParentId = current?.parentId ?: current?.id
    }
    val children = remember(categories, pickedParentId) {
        categories.filter { it.parentId != null && it.parentId == pickedParentId }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it.take(20))) },
            modifier = Modifier.fillMaxWidth(),
            label = "模板名称",
            useLabelAsPlaceholder = true,
        )

        // 类型
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionType.entries.forEach { type ->
                TypeChip(
                    label = when (type) {
                        TransactionType.EXPENSE -> "支出"
                        TransactionType.INCOME -> "收入"
                        TransactionType.TRANSFER -> "转账"
                    },
                    selected = draft.type == type,
                    // 换类型必须重新归一化分类：否则会带着上一个类型的分类 id
                    onClick = { onChange(draft.copy(type = type, categoryId = "").normalized()) },
                )
            }
        }

        // 分类：一级图标网格 → 选中项的二级浅底面板
        Column {
            Text(
                "分类",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            if (parents.isEmpty()) {
                Text(
                    "该类型下还没有分类，请先到「分类管理」里添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            } else {
                CategoryIconGrid(
                    items = parents,
                    selectedId = pickedParentId,
                    onSelect = {
                        pickedParentId = it.id
                        // 点一级 = 直接选它（模板允许挂在一级分类上）
                        onChange(draft.copy(categoryId = it.id))
                    },
                )
                if (children.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.xxl))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    ) {
                        CategoryIconGrid(
                            items = children,
                            selectedId = draft.categoryId,
                            onSelect = { onChange(draft.copy(categoryId = it.id)) },
                        )
                    }
                }
            }
        }
    }
}

/** 一级图标网格一行几个：与「记一笔」面板的分类网格同密度 */
private const val CATEGORY_GRID_SPAN = 5

/** 带图标的分类网格：一行 [CATEGORY_GRID_SPAN] 个，不足补空位避免单项被拉满整行 */
@Composable
private fun CategoryIconGrid(
    items: List<Category>,
    selectedId: String?,
    onSelect: (Category) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(CATEGORY_GRID_SPAN).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    CategoryIconCell(
                        category = category,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(CATEGORY_GRID_SPAN - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 单个分类格子：圆形图标 + 名字，选中时图标转白、文字染分类色（与记一笔面板同款） */
@Composable
private fun CategoryIconCell(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(category.color)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIconImage(
            name = category.name,
            color = accent,
            size = IjiIconSize.Grid,
            selected = selected,
            categoryId = category.id,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 分 → 输入框文本（整数金额不带 .0） */
private fun centsToText(cents: Long): String {
    if (cents == 0L) return ""
    val v = cents / 100.0
    return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}

/**
 * 单账户选择行：模板的主付款账户，点开弹窗选择；「不指定」= 留空，
 * 应用模板时不覆盖面板当前账户。组合付款生效（≥2 行）时，面板会以
 * 第一条拆分作首选账户，本字段只在无拆分时预填。
 */
@Composable
private fun AccountField(
    account: String,
    accountOptions: List<String>,
    onChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { showPicker = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account.isNotBlank()) {
            AccountIconImage(iconKey = account, size = IjiIconSize.Inline)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            account.ifBlank { "选择账户（可选）" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (account.isBlank()) TextSecondary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }

    if (showPicker) {
        IjiDialog(
            show = true,
            onDismissRequest = { showPicker = false },
            title = "选择账户",
            showDismiss = false,
            confirmText = "关闭",
            onConfirm = { showPicker = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 「不指定」= 清空：模板不带账户，应用时保留面板当前的账户
                PickerRow(
                    label = "不指定",
                    selected = account.isBlank(),
                    onClick = {
                        onChange("")
                        showPicker = false
                    },
                )
                accountOptions.forEach { name ->
                    PickerRow(
                        label = name,
                        selected = name == account,
                        onClick = {
                            onChange(name)
                            showPicker = false
                        },
                    )
                }
                if (accountOptions.isEmpty()) {
                    Text(
                        "还没有资产账户，请先到「资产」页添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** 账户 / 分类选择弹窗里的一行：左图标（可选）+ 名称 + 选中勾 */
@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconKey: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconKey != null) {
            AccountIconImage(iconKey = iconKey, size = IjiIconSize.Action)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 组合付款拆分编辑：每行「账户 + 金额」，合计必须与模板金额一致（与记一笔面板同规则）。
 * 没有任何有效行 = 不拆分，按单账户记账。
 *
 * 拆分在保存时校验（≥2 个有效账户、合计一致），这里只做实时合计提示；
 * 账户候选来自资产页的真实账户，与记一笔面板同一来源。
 */
@Composable
private fun SplitsEditor(
    splits: List<PaymentSplit>,
    totalCents: Long,
    accountOptions: List<String>,
    onChange: (List<PaymentSplit>) -> Unit,
) {
    // 非 null = 正在为第 idx 行选账户
    var pickerIndex by remember { mutableStateOf<Int?>(null) }
    val sumCents = splits.sumOf { it.amountCents }
    val diffCents = totalCents - sumCents

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "组合付款（可选）",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp),
        )
        splits.forEachIndexed { index, split ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 账户：点开选择弹窗（与记一笔面板的组合付款行同款形态）
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { pickerIndex = index }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (split.account.isNotBlank()) {
                        AccountIconImage(iconKey = split.account, size = IjiIconSize.Inline)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        split.account.ifBlank { "选择账户" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (split.account.isBlank()) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(modifier = Modifier.width(104.dp)) {
                    AmountField(
                        amountCents = split.amountCents,
                        onChanged = { cents ->
                            onChange(
                                splits.mapIndexed { i, s -> if (i == index) s.copy(amountCents = cents) else s },
                            )
                        },
                        label = "金额",
                    )
                }
                // 至少保留一行，否则编辑器会空掉
                if (splits.size > 1) {
                    Text(
                        "×",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable { onChange(splits.filterIndexed { i, _ -> i != index }) }
                            .padding(6.dp),
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IjiTextAction(
                text = "＋ 添加账户",
                compact = true,
                onClick = { onChange(splits + PaymentSplit()) },
            )
            Spacer(Modifier.weight(1f))
            if (splits.isNotEmpty()) {
                Text(
                    when {
                        totalCents == 0L -> "先填模板金额"
                        diffCents == 0L -> "合计 ${formatMoney(sumCents)} · 与金额一致"
                        diffCents > 0L -> "还差 ${formatMoney(diffCents)}"
                        else -> "超出 ${formatMoney(-diffCents)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (totalCents > 0L && diffCents == 0L) IncomeGreen else TextSecondary,
                )
            }
        }
    }

    // 账户选择弹窗：一行一个账户，点击即填回当前行
    val picked = pickerIndex
    if (picked != null) {
        IjiDialog(
            show = true,
            onDismissRequest = { pickerIndex = null },
            title = "选择账户",
            showDismiss = false,
            confirmText = "关闭",
            onConfirm = { pickerIndex = null },
        ) {
            if (accountOptions.isEmpty()) {
                Text(
                    "还没有资产账户，请先到「资产」页添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    accountOptions.forEach { name ->
                        PickerRow(
                            label = name,
                            selected = name == splits.getOrNull(picked)?.account,
                            iconKey = name,
                            onClick = {
                                onChange(
                                    splits.mapIndexed { i, s ->
                                        if (i == picked) s.copy(account = name) else s
                                    },
                                )
                                pickerIndex = null
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 金额录入：元 → 分。
 * 文本是输入过程中的唯一真源，仅当外部 [amountCents] 与文本解析值不一致时才覆盖，
 * 否则每敲一个字符都会被重写成 "3000.0"，导致无法连续输入。
 *
 * 挂数字键盘（旧版漏了，弹出的是全键盘）；小数点只保留第一个，避免 "1.2.3" 解析成 0。
 */
@Composable
private fun AmountField(
    amountCents: Long,
    onChanged: (Long) -> Unit,
    label: String = "金额（元）",
) {
    var text by remember { mutableStateOf(centsToText(amountCents)) }
    LaunchedEffect(amountCents) {
        val parsed = ((text.toDoubleOrNull() ?: 0.0) * 100).toLong()
        if (parsed != amountCents) text = centsToText(amountCents)
    }
    TextField(
        value = text,
        onValueChange = { raw ->
            val filtered = buildString {
                var dotSeen = false
                for (c in raw) {
                    when {
                        c.isDigit() -> append(c)
                        c == '.' && !dotSeen -> {
                            append(c)
                            dotSeen = true
                        }
                    }
                }
            }
            text = filtered
            onChanged((filtered.toDoubleOrNull() ?: 0.0).let { (it * 100).toLong() })
        },
        modifier = Modifier.fillMaxWidth(),
        label = label,
        useLabelAsPlaceholder = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** 类型选择小胶囊 */
@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) MiuixPrimary else DividerGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}
