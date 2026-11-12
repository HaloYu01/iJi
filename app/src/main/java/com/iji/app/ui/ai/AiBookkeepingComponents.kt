package com.iji.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iji.app.ai.AiBookkeepingService
import com.iji.app.ai.LocalRule
import com.iji.app.ai.LocalRuleStore
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiSegmentedRow
import com.iji.app.ui.components.IjiSwitchRow
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.StateSize
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.ijiButtonFocusRing
import com.iji.app.ui.components.ijiPrimaryButtonColors
import com.iji.app.ui.theme.AppleTertiaryBackground
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import kotlin.math.roundToLong

/**
 * AI 记账页的展示组件：可编辑确认卡、动作按钮，以及送云端前的图片压缩。
 *
 * 与 `AiBookkeepingScreen.kt` 分开是为了让页面文件只留状态机与布局骨架——
 * 之前一屏 500 行里，取图/语音/解析/结果卡混在一起，改文案要往上翻半屏。
 */

/**
 * 可编辑确认卡：类型 / 金额 / 分类 / 账户 / 备注 / 标签**全部就地可改**。
 *
 * 双轨确认：
 * - **保存**：页面直接入库（快存），留在本页可连续记下一笔。转账没有转入账户字段，
 *   快存拦下、引导走详细编辑；
 * - **详细编辑**：带预填跳「记一笔」面板（不离开本页），保留面板里的完整字段。
 *
 * 「记住这个分类」（AutoAccounting 自动记忆的落点）：用户把识别错/没识别的分类改成真实分类后
 * 勾选它，保存 / 详细编辑时把备注关键词 + 该分类存成一条 [LocalRule.auto] 规则——下次同样的备注
 * 本地规则直接归类，不再依赖模型猜。备注里清洗掉金额数字（对齐参考项目 sanitizeForRule：
 * 动态数字会影响匹配稳定性），清不出关键词（<2 字）时不记忆。
 */
@Composable
internal fun ParseResultCard(
    bill: AiBookkeepingService.BillResult,
    /** 真实账户名列表（资产页里已添加的账户）；账户行候选与预选校验的来源 */
    accounts: List<String>,
    /** 快存：金额 / 分类校验通过后直接入库 */
    onSave: (AiBookkeepingService.BillResult) -> Unit,
    /** 详细编辑：把编辑后的结果带回记一笔面板预填 */
    onDetailEdit: (AiBookkeepingService.BillResult) -> Unit,
    /** 清除本条结果（标题行右侧文字动作） */
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current

    // 就地编辑的各字段：remember(bill) 让新结果到达时整体复位
    var type by remember(bill) { mutableStateOf(bill.type) }
    var amountText by remember(bill) {
        mutableStateOf(if (bill.amountCents > 0L) yuanTextOf(bill.amountCents) else "")
    }
    var category by remember(bill) { mutableStateOf(bill.categoryHint) }
    // accountHint 对不上真实账户就不预选（与记一笔面板同口径）：显示「未指定」，由用户点选
    var account by remember(bill) {
        mutableStateOf(bill.accountHint.takeIf { it in accounts }.orEmpty())
    }
    var note by remember(bill) { mutableStateOf(bill.note) }
    var tagsText by remember(bill) {
        mutableStateOf(bill.tags.joinToString(" ") { it.removePrefix("#") })
    }
    var rememberCategory by remember(bill) { mutableStateOf(false) }
    var showCategoryPicker by remember(bill) { mutableStateOf(false) }
    var showAccountPicker by remember(bill) { mutableStateOf(false) }

    // 分类目录名（快存校验的依据）：模型的通用词对不上用户分类表时，不许静默入库
    val catalogNames = remember(type) {
        val list = if (type == "income") CategoryCatalog.incomes else CategoryCatalog.expenses
        list.map { it.name }
    }
    // 选择器候选：目录 + 当前值（当前值不在目录里也要显示出来，让用户看到并改掉）
    val pickerNames = remember(type, category) { (catalogNames + category).distinct() }
    val categoryValid = type == "transfer" || category in catalogNames

    /** 切类型：分类候选跟着收支方向换；当前分类在新方向里不存在就落「其他」 */
    fun changeType(new: String) {
        if (new == type) return
        type = new
        if (new != "transfer") {
            val names = (if (new == "income") CategoryCatalog.incomes else CategoryCatalog.expenses)
                .map { it.name }
            if (category !in names) category = "其他"
        }
    }

    val amountCents = amountText.trim().toDoubleOrNull()
        ?.let { (it * 100).roundToLong() }
        ?: 0L
    // 快存门槛：金额 > 0；非转账的分类必须是真实分类。拦截原因如实写在按钮上方
    val saveEnabled = amountCents > 0L && categoryValid
    val saveHint = when {
        type == "transfer" -> "转账请在「详细编辑」里选择转入账户后保存"
        !categoryValid -> "分类「$category」不在分类表里，请点分类行重新选择"
        amountCents <= 0L && amountText.isNotBlank() -> "金额要大于 0 才能保存"
        else -> ""
    }

    /** 备注清洗成规则关键词：去金额数字与货币符号、压缩空白；太短的关键词会误伤，不存 */
    fun keywordForAutoRule(): String = note.trim()
        .replace(Regex("[¥￥$€£]\\s*\\d+(?:\\.\\d+)?"), "")
        .replace(Regex("\\d+(?:\\.\\d+)?\\s*[元块]"), "")
        .replace(Regex("\\d+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(12)
        .takeIf { it.length >= 2 }
        .orEmpty()

    /** 「记住分类」两条确认路径共用：keyword 清不出来时如实告知，不静默丢 */
    fun maybeRememberCategory() {
        if (!rememberCategory || type == "transfer" || !categoryValid) return
        val keyword = keywordForAutoRule()
        if (keyword.isBlank()) {
            snackbar.show("备注里没有能当关键词的文字，没有记住")
            return
        }
        val ruleType = if (type == "income") LocalRule.TYPE_INCOME else LocalRule.TYPE_EXPENSE
        val ok = LocalRuleStore.rememberCategory(context, keyword, category, ruleType)
        snackbar.show(if (ok) "已记住：$keyword → $category" else "记住分类失败")
    }

    /** 编辑后的完整结果：保存与详细编辑带同一份，双轨不改字面 */
    fun editedBill(): AiBookkeepingService.BillResult = bill.copy(
        type = type,
        categoryHint = category,
        amountCents = amountCents,
        note = note.trim(),
        accountHint = account,
        tags = tagsText.split(Regex("\\s+"))
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() },
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.xxl),
        colors = CardDefaults.cardColors(containerColor = AppleTertiaryBackground),
    ) {
        Column(Modifier.padding(20.dp)) {
            // 识别来源并入标题：这条结果从哪条链路来的（通知规则/AI/本地规则/本地识别/云端识别），
            // 用户有权知道，也方便排查
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallTitle(
                    text = if (bill.source.isBlank()) "解析结果" else "解析结果 · ${bill.source}",
                    insideMargin = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f),
                )
                IjiTextAction(text = "清除", compact = true, onClick = onReset)
            }
            Spacer(Modifier.height(12.dp))

            // 类型：支出 / 收入 / 转账三段；转账的分类与快存都被面板语义接管
            IjiSegmentedRow(
                options = listOf("expense", "income", "transfer"),
                selected = type,
                onSelect = ::changeType,
                label = { t ->
                    when (t) {
                        "income" -> "收入"
                        "transfer" -> "转账"
                        else -> "支出"
                    }
                },
                trackColor = MaterialTheme.colorScheme.surface,
                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("金额（元）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(Radius.xxl),
            )
            Spacer(Modifier.height(10.dp))

            if (type != "transfer") {
                EditableRow(
                    label = "分类",
                    value = category,
                    valueColor = if (categoryValid) Color.Unspecified else MaterialTheme.colorScheme.error,
                    onClick = { showCategoryPicker = true },
                )
            }
            EditableRow(
                label = "账户",
                value = account.ifBlank { "未指定" },
                onClick = { showAccountPicker = true },
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                singleLine = true,
                shape = RoundedCornerShape(Radius.xxl),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标签（空格分隔）") },
                singleLine = true,
                shape = RoundedCornerShape(Radius.xxl),
            )

            // 记住分类：改完分类才会出现「这个分类值得记」的场景；记「其他」没有意义
            if (type != "transfer" && category != "其他" && categoryValid) {
                Spacer(Modifier.height(4.dp))
                IjiSwitchRow(
                    title = "记住这个分类",
                    desc = "把备注关键词存成本地规则，下次自动归到「$category」",
                    checked = rememberCategory,
                    onCheckedChange = { rememberCategory = it },
                )
            }

            Spacer(Modifier.height(8.dp))
            if (saveHint.isNotBlank()) {
                Text(
                    saveHint,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("详细编辑", icon = null, compact = false, enabled = true) {
                    maybeRememberCategory()
                    onDetailEdit(editedBill())
                }
                ActionButton(
                    text = "保存",
                    icon = null,
                    compact = false,
                    enabled = saveEnabled,
                    primary = true,
                ) {
                    maybeRememberCategory()
                    onSave(editedBill())
                }
            }
        }
    }

    // 分类就地修正：单选弹窗（与规则编辑页同款交互）
    IjiDialog(
        show = showCategoryPicker,
        onDismissRequest = { showCategoryPicker = false },
        title = "选择分类",
        showDismiss = false,
        confirmText = "关闭",
        onConfirm = { showCategoryPicker = false },
    ) {
        LazyColumn(Modifier.height(360.dp)) {
            items(pickerNames) { item ->
                PickerRow(
                    label = item,
                    selected = item == category,
                    onClick = {
                        category = item
                        showCategoryPicker = false
                    },
                )
            }
        }
    }

    // 账户就地修正：「不指定」= 落库不带账户（余额不联动，与批量导入同口径）
    IjiDialog(
        show = showAccountPicker,
        onDismissRequest = { showAccountPicker = false },
        title = "选择账户",
        showDismiss = false,
        confirmText = "关闭",
        onConfirm = { showAccountPicker = false },
    ) {
        LazyColumn(Modifier.height(360.dp)) {
            val accountOptions = listOf("") + accounts
            items(accountOptions) { item ->
                val name = item.ifBlank { "不指定" }
                PickerRow(
                    label = name,
                    selected = item == account,
                    onClick = {
                        account = item
                        showAccountPicker = false
                    },
                )
            }
        }
    }
}

/** 确认卡里可点选的行（分类 / 账户）：左灰标签、右值 + 箭头 */
@Composable
private fun EditableRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "修改$label",
            tint = TextSecondary,
            modifier = Modifier.padding(start = 4.dp).size(16.dp),
        )
    }
}

/** 选择弹窗里的一行（分类 / 账户共用） */
@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/** 分 → 元的编辑用文本：整数金额去掉小数尾巴（3500 → "35"），其余固定两位 */
private fun yuanTextOf(cents: Long): String =
    if (cents % 100 == 0L) {
        (cents / 100).toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", cents / 100.0)
    }

/**
 * 四个动作按钮共用一套尺寸 / 焦点圈 / 图标排版。
 *
 * 原先每个按钮各写 12 行（同样的 weight + focusRing + Icon + Spacer + Text），
 * 改一次样式要动四处；窄屏（<380dp）自动只留文字，避免按钮里的字被挤没。
 */
@Composable
internal fun RowScope.ActionButton(
    text: String,
    icon: ImageVector?,
    compact: Boolean,
    enabled: Boolean,
    primary: Boolean = false,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .ijiButtonFocusRing(shape = ContinuousRoundedRectangle(ButtonDefaults.CornerRadius)),
        enabled = enabled,
        colors = if (primary) ijiPrimaryButtonColors() else ButtonDefaults.buttonColors(),
    ) {
        if (loading) {
            StateView(state = ContentState.Loading("解析中"), size = StateSize.Inline)
            return@Button
        }
        if (icon != null && !compact) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = if (primary) Color.White else Color.Unspecified, maxLines = 1)
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.width(60.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

