package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.Category
import com.iji.app.data.model.RecurrenceFreq
import com.iji.app.data.model.RecurringRule
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiDangerTextAction
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.WheelOptionDialog
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.DividerGray
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 频率文案 */
private val FREQ_LABEL: Map<RecurrenceFreq, String> = mapOf(
    RecurrenceFreq.DAILY to "每天",
    RecurrenceFreq.WEEKLY to "每周",
    RecurrenceFreq.MONTHLY to "每月",
    RecurrenceFreq.YEARLY to "每年",
)

private val MD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val YMD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
private val WEEKDAY_CN = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun dateLabel(d: LocalDate, today: LocalDate): String {
    val date = if (d.year == today.year) MD_FORMAT.format(d) else YMD_FORMAT.format(d)
    return "$date ${WEEKDAY_CN[d.dayOfWeek.value - 1]}"
}

/**
 * 周期账单管理页（我的 → 自动化 → 周期账单）。
 *
 * 底层（[RecurringRule] / `recurring_rules` 表 / 到期自动记账引擎）早已就绪，
 * 之前只是**没有入口**：规则只能由代码创建，用户既看不到也无法停用，
 * 于是「房租每月自动生成」这类需求实际不可用。本页补的就是这一层 UI。
 *
 * 引擎语义（见 [MainViewModel.processRecurringRules]）：
 * - 每次开屏 & 每次新增规则后扫描一次，`nextRunDate <= 今天` 就生成一笔交易；
 * - 生成后按频率把 `nextRunDate` 顺延一个周期并落库，因此重启不会重复生成；
 * - 规则**自带账本**（`ledgerId`）：切到别的账本时房租不会被记到工作账本里。
 *
 * 交互：
 * - 点行 = 编辑（改分类 / 金额 / 频率 / 下次执行日 / 账户 / 账本）
 * - 右侧开关 = 停用 / 启用（停用的规则不再生成，但保留配置）
 * - 「立即执行」= 不等到期日，马上按规则记一笔
 * - 「删除」= 移除规则
 *
 * ⚠️ 类型只提供「支出 / 收入」：周期规则没有转入账户字段，转账无法表达。
 */
@Composable
fun RecurringManageScreen(
    onBack: () -> Unit,
    app: MainViewModel = viewModel(),
) {
    val snackbar = LocalSnackbarController.current
    val accounts by app.accounts.collectAsStateWithLifecycle()
    val currentLedger by app.currentLedger.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    // 非 null 即弹窗打开；id == 0L 表示新建
    var editing by remember { mutableStateOf<RecurringRule?>(null) }
    var showCategory by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showLedger by remember { mutableStateOf(false) }
    var showFreq by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    // 可选日期窗口：允许回看 30 天（逾期未执行的规则），最长排到一年后
    val dateOptions = remember {
        buildList { for (i in -30..365) add(today.plusDays(i.toLong())) }
    }

    MineSubPage(title = "周期账单", onBack = onBack) {
        if (app.recurringRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "还没有周期账单\n把房租、订阅、工资这类固定收支设成规则，到期自动生成",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = LocalDimens.current.screenPadding,
                    end = LocalDimens.current.screenPadding,
                    top = ijiTopBarContentPadding(),
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(app.recurringRules, key = { it.id }) { rule ->
                    RecurringRuleRow(
                        rule = rule,
                        today = today,
                        onClick = { editing = rule },
                        onToggle = { on ->
                            app.toggleRecurringRule(rule, on)
                            snackbar.show(if (on) "已启用「${rule.title()}」" else "已停用「${rule.title()}」")
                        },
                        onRunNow = {
                            app.runRecurringRuleNow(rule)
                            snackbar.show("已生成一笔 ${formatMoney(rule.amountCents)}")
                        },
                        onDelete = {
                            app.deleteRecurringRule(rule)
                            snackbar.show("已删除「${rule.title()}」")
                        },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalDimens.current.screenPadding, vertical = 16.dp)
                .clip(RoundedCornerShape(Radius.xl))
                .background(MiuixPrimary)
                .clickable {
                    editing = RecurringRule(
                        type = TransactionType.EXPENSE,
                        categoryId = CategoryCatalog.expenses.firstOrNull()?.id ?: "",
                        amountCents = 0L,
                        freq = RecurrenceFreq.MONTHLY,
                        nextRunDate = today,
                        account = accounts.firstOrNull()?.name ?: "现金",
                        ledgerId = currentLedger,
                    )
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("新建周期账单", color = Color.White, fontWeight = FontWeight.Medium)
        }
    }

    // ── 编辑 / 新建弹窗 ──
    val draft = editing
    IjiDialog(
        show = draft != null,
        onDismissRequest = { editing = null },
        title = if (draft == null || draft.id == 0L) "新建周期账单" else "编辑周期账单",
        summary = if (draft == null || draft.id == 0L) "到期日到达时自动生成一笔，随后顺延一个周期" else null,
        confirmText = "保存",
        onConfirm = {
            val r = editing ?: return@IjiDialog
            if (r.amountCents <= 0L) {
                snackbar.show("请填写金额")
                return@IjiDialog
            }
            if (r.categoryId.isBlank()) {
                snackbar.show("请选择分类")
                return@IjiDialog
            }
            if (r.id == 0L) {
                app.addRecurringRule(
                    type = r.type,
                    categoryId = r.categoryId,
                    amountCents = r.amountCents,
                    note = r.note,
                    account = r.account,
                    freq = r.freq,
                    startDate = r.nextRunDate,
                    ledgerId = r.ledgerId,
                )
                snackbar.show("已创建，${FREQ_LABEL[r.freq]}自动生成")
            } else {
                app.updateRecurringRule(r)
                snackbar.show("已保存")
            }
            editing = null
        },
    ) {
        if (draft != null) {
            RecurringRuleForm(
                draft = draft,
                today = today,
                onChange = { editing = it },
                onPickCategory = { showCategory = true },
                onPickAccount = { showAccount = true },
                onPickLedger = { showLedger = true },
                onPickFreq = { showFreq = true },
                onPickDate = { showDate = true },
            )
        }
    }

    if (draft != null) {
        // 分类
        val categories: List<Category> =
            if (draft.type == TransactionType.INCOME) CategoryCatalog.incomes else CategoryCatalog.expenses
        val currentCategory = categories.find { it.id == draft.categoryId } ?: categories.firstOrNull()
        if (currentCategory != null) {
            WheelOptionDialog(
                show = showCategory,
                options = categories,
                selected = currentCategory,
                title = "选择分类",
                label = { it.name },
                onDismissRequest = { showCategory = false },
                onConfirm = {
                    editing = draft.copy(categoryId = it.id)
                    showCategory = false
                },
            )
        }

        // 账户（规则只存账户名，与交易一致）
        if (accounts.isNotEmpty()) {
            WheelOptionDialog(
                show = showAccount,
                options = accounts.map { it.name }.distinct(),
                selected = draft.account,
                title = "选择账户",
                onDismissRequest = { showAccount = false },
                onConfirm = {
                    editing = draft.copy(account = it)
                    showAccount = false
                },
            )
        }

        // 账本：决定自动生成的账单记到哪个账本
        WheelOptionDialog(
            show = showLedger,
            options = app.ledgers.toList(),
            selected = draft.ledgerId,
            title = "选择账本",
            onDismissRequest = { showLedger = false },
            onConfirm = {
                editing = draft.copy(ledgerId = it)
                showLedger = false
            },
        )

        // 频率
        WheelOptionDialog(
            show = showFreq,
            options = RecurrenceFreq.entries.toList(),
            selected = draft.freq,
            title = "重复频率",
            label = { FREQ_LABEL[it] ?: it.name },
            onDismissRequest = { showFreq = false },
            onConfirm = {
                editing = draft.copy(freq = it)
                showFreq = false
            },
        )

        // 下次执行日期
        WheelOptionDialog(
            show = showDate,
            options = dateOptions,
            selected = draft.nextRunDate,
            title = "下次执行日期",
            label = { dateLabel(it, today) },
            onDismissRequest = { showDate = false },
            onConfirm = {
                editing = draft.copy(nextRunDate = it)
                showDate = false
            },
        )
    }
}

/** 行标题：优先备注，其次分类名 */
private fun RecurringRule.title(): String =
    note.ifBlank { CategoryCatalog.of(categoryId).name.ifBlank { "周期账单" } }

/** 周期账单编辑表单 */
@Composable
private fun RecurringRuleForm(
    draft: RecurringRule,
    today: LocalDate,
    onChange: (RecurringRule) -> Unit,
    onPickCategory: () -> Unit,
    onPickAccount: () -> Unit,
    onPickLedger: () -> Unit,
    onPickFreq: () -> Unit,
    onPickDate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 类型：只给「支出 / 收入」—— 规则没有转入账户字段，转账无法表达
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                TypeChip(
                    label = if (type == TransactionType.EXPENSE) "支出" else "收入",
                    selected = draft.type == type,
                    onClick = { onChange(draft.copy(type = type, categoryId = "")) },
                )
            }
        }

        PickerRow(title = "分类", value = CategoryCatalog.of(draft.categoryId).name.ifBlank { "请选择" }, onClick = onPickCategory)
        AmountField(
            amountCents = draft.amountCents,
            onChanged = { onChange(draft.copy(amountCents = it)) },
        )
        PickerRow(title = "重复频率", value = FREQ_LABEL[draft.freq] ?: draft.freq.name, onClick = onPickFreq)
        PickerRow(title = "下次执行", value = dateLabel(draft.nextRunDate, today), onClick = onPickDate)
        PickerRow(title = "账户", value = draft.account.ifBlank { "请选择" }, onClick = onPickAccount)
        PickerRow(title = "记入账本", value = draft.ledgerId, onClick = onPickLedger)

        top.yukonga.miuix.kmp.basic.TextField(
            value = draft.note,
            onValueChange = { onChange(draft.copy(note = it.take(50))) },
            modifier = Modifier.fillMaxWidth(),
            label = "备注（可选）",
            useLabelAsPlaceholder = true,
        )
    }
}

/** 通用「点开选值」行 */
@Composable
private fun PickerRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(DividerGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
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
 * 金额录入：元 → 分。
 *
 * 文本是**输入过程中的唯一真源**：只有当外部 [amountCents] 与当前文本解析值不相等时
 * （切换到另一条规则、或首次打开编辑）才用外部值覆盖文本。
 * 否则每敲一个字符都会因为 `amountCents` 变化把文本重写成 "3000.0"，根本没法输入。
 */
@Composable
private fun AmountField(
    amountCents: Long,
    onChanged: (Long) -> Unit,
) {
    var text by remember { mutableStateOf(centsToText(amountCents)) }
    LaunchedEffect(amountCents) {
        val parsed = ((text.toDoubleOrNull() ?: 0.0) * 100).toLong()
        if (parsed != amountCents) text = centsToText(amountCents)
    }
    top.yukonga.miuix.kmp.basic.TextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || it == '.' }
            text = filtered
            onChanged((filtered.toDoubleOrNull() ?: 0.0).let { (it * 100).toLong() })
        },
        modifier = Modifier.fillMaxWidth(),
        label = "金额（元）",
        useLabelAsPlaceholder = true,
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

/** 周期账单行：图标 + 标题 + 摘要 + 开关 + 操作 */
@Composable
private fun RecurringRuleRow(
    rule: RecurringRule,
    today: LocalDate,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
) {
    val category = CategoryCatalog.of(rule.categoryId)
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconImage(
                    name = category.name,
                    color = Color(category.color),
                    size = 36.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rule.title(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (rule.enabled) Color.Unspecified else TextSecondary,
                    )
                    Text(
                        "${formatMoney(rule.amountCents)} · ${FREQ_LABEL[rule.freq] ?: rule.freq.name} · 下次 ${dateLabel(rule.nextRunDate, today)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Text(
                        listOf(rule.account, rule.ledgerId).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IjiTextAction(text = "立即执行", onClick = onRunNow, compact = true)
                IjiDangerTextAction(text = "删除", onClick = onDelete, compact = true)
                if (!rule.enabled) {
                    Text(
                        "已停用",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                } else if (rule.nextRunDate <= today) {
                    Text(
                        "今日待生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
