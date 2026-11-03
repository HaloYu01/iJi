package com.iji.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iji.app.ai.LocalRule
import com.iji.app.ai.LocalRuleEngine
import com.iji.app.ai.LocalRuleStore
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.components.IjiCategoryChips
import com.iji.app.ui.components.IjiDangerTextAction
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiFormDivider
import com.iji.app.ui.components.IjiSwitchRow
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.mine.MineSubPage
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/**
 * 本地识别规则的编辑页（新增 / 修改同页）。
 *
 * 一条规则 = **关键词 → 分类**（可选再预填账户）；命中后由 [LocalRuleEngine] 决定用哪条，
 * 识别结果直接进「AI 记账」页的结果卡，确认后落到记账面板 —— 全程不联网。
 *
 * 几个刻意的设计：
 * - 分类只给**下拉胶囊**而不是自由输入：手写一个不存在的分类名，记账面板会提示「未识别分类」，
 *   等于让用户自己埋坑；分类名是数据（可改名），所以列表实时取自 [CategoryCatalog]。
 * - 关键词支持「一行一个」也支持逗号分隔：从别处粘贴词表是最常见的用法。
 * - 内置规则同样可改可删（「恢复内置规则」能补回来），不做只读白名单。
 */
@Composable
fun LocalRuleEditScreen(ruleId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current

    // ruleId 不在存储里 = 新增（路由传 "new"）
    val existing = remember(ruleId) { LocalRuleStore.rules(context).firstOrNull { it.id == ruleId } }
    val isNew = existing == null

    var name by remember(ruleId) { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember(ruleId) { mutableStateOf(existing?.type ?: LocalRule.TYPE_EXPENSE) }
    var category by remember(ruleId) { mutableStateOf(existing?.category ?: "其他") }
    var account by remember(ruleId) { mutableStateOf(existing?.account.orEmpty()) }
    var keywordsText by remember(ruleId) {
        mutableStateOf(LocalRuleEngine.keywordsText(existing?.keywords.orEmpty()))
    }
    var enabled by remember(ruleId) { mutableStateOf(existing?.enabled ?: true) }
    var showDelete by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val keywords = LocalRuleEngine.keywordsOf(keywordsText)
    val categoryNames = categoryCandidates(type, category)

    fun save() {
        if (name.isBlank()) {
            snackbar.show("先给规则起个名字")
            return
        }
        if (keywords.isEmpty()) {
            snackbar.show("至少要有一个关键词，否则这条规则永远不会命中")
            return
        }
        val rule = LocalRule(
            id = existing?.id ?: LocalRuleStore.newRuleId(),
            name = name.trim(),
            type = type,
            category = category,
            account = account.trim(),
            keywords = keywords,
            enabled = enabled,
            builtin = existing?.builtin ?: false,
        )
        if (!LocalRuleStore.upsert(context, rule)) {
            snackbar.show("保存失败，请重试")
            return
        }
        snackbar.show("规则已保存")
        onBack()
    }

    MineSubPage(
        title = if (isNew) "新增规则" else existing.name,
        onBack = onBack,
        endAction = {
            IjiTextAction(text = "保存", onClick = { save() }, compact = true)
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IjiFormCard {
                Column(Modifier.padding(16.dp)) {
                    TextField(
                        value = name,
                        onValueChange = { name = it.take(12) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "规则名称",
                        useLabelAsPlaceholder = true,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("收支类型", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    IjiCategoryChips(
                        items = listOf(LocalRule.TYPE_EXPENSE, LocalRule.TYPE_INCOME, LocalRule.TYPE_ANY),
                        selected = type,
                        onSelect = { picked ->
                            type = picked
                            // 换类型后原来的分类可能不属于这一类，自动落到新列表的第一项
                            val candidates = categoryCandidates(picked, category)
                            if (category !in candidates) {
                                category = candidates.firstOrNull { it == "其他" } ?: candidates.firstOrNull().orEmpty()
                            }
                        },
                        labelOf = { LocalRule.typeLabel(it) },
                    )
                    Spacer(Modifier.height(10.dp))
                    // 分类可能有几十个（含二级），铺成胶囊会占满整屏，所以走「选择」弹窗
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCategoryPicker = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("记到哪个分类", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            category.ifBlank { "未选择" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            MiuixIcons.ChevronForward,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(18.dp),
                        )
                    }
                }
            }

            IjiFormCard {
                Column(Modifier.padding(16.dp)) {
                    TextField(
                        value = keywordsText,
                        onValueChange = { keywordsText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        label = "命中任意一个即算匹配",
                        useLabelAsPlaceholder = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "逗号或换行分隔，不区分大小写，共 ${keywords.size} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                IjiFormDivider()
                Column(Modifier.padding(16.dp)) {
                    TextField(
                        value = account,
                        onValueChange = { account = it.take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "命中后预填账户（可选）",
                        useLabelAsPlaceholder = true,
                    )
                }
                IjiFormDivider()
                IjiSwitchRow(
                    title = "启用这条规则",
                    desc = "停用后不参与本地识别，配置保留",
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            if (!isNew) {
                IjiFormCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("删除这条规则", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (existing.builtin) {
                                    "删掉后可在「恢复内置规则」补回"
                                } else {
                                    "删除后不可恢复，可先导出备份"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        IjiDangerTextAction(text = "删除", compact = true, onClick = { showDelete = true })
                    }
                }
            }
        }
    }

    // 分类选择：几十个选项（含二级）铺成胶囊会占满整屏，改成可滚动的单选弹窗
    IjiDialog(
        show = showCategoryPicker,
        onDismissRequest = { showCategoryPicker = false },
        title = "选择分类",
        // 点一行即选中并关闭，所以只留一个「关闭」按钮
        showDismiss = false,
        confirmText = "关闭",
        onConfirm = { showCategoryPicker = false },
    ) {
        LazyColumn(Modifier.height(360.dp)) {
            items(categoryNames) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            category = item
                            showCategoryPicker = false
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (item == category) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    IjiDialog(
        show = showDelete,
        onDismissRequest = { showDelete = false },
        title = "删除规则",
        summary = "「${existing?.name.orEmpty()}」将不再参与本地识别。已记的账单不受影响。",
        confirmText = "删除",
        confirmColor = MaterialTheme.colorScheme.error,
        onConfirm = {
            showDelete = false
            val ok = existing?.let { LocalRuleStore.remove(context, it.id) } ?: false
            snackbar.show(if (ok) "已删除" else "删除失败，请重试")
            if (ok) onBack()
        },
    )
}

/**
 * 分类候选：按收支类型给列表，并保证**当前值一定在列表里**（分类被改名 / 删除时，
 * 直接换成第一项会让用户没看清就把规则改到了别的分类上）。
 */
private fun categoryCandidates(type: String, current: String): List<String> {
    val all = when (type) {
        LocalRule.TYPE_INCOME -> CategoryCatalog.incomes
        LocalRule.TYPE_EXPENSE -> CategoryCatalog.expenses
        else -> CategoryCatalog.expenses + CategoryCatalog.incomes
    }.map { it.name }.distinct()
    return if (current.isNotBlank() && current !in all) listOf(current) + all else all
}
