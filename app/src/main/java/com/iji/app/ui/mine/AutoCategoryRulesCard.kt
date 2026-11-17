package com.iji.app.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iji.app.autorecord.AutoCategoryRuleStore
import com.iji.app.data.model.Category
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

/** 「关键词 → 分类」规则管理卡：用户自定义规则优先级高于内置表，只影响分类建议，不改历史账单。 */
@Composable
fun AutoCategoryRulesCard() {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val rules = remember(refresh) { AutoCategoryRuleStore.list(context) }

    var keyword by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var chosen by remember { mutableStateOf<Category?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val categories = remember(type, refresh) { CategoryCatalog.ofType(type) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("分类规则", style = MaterialTheme.typography.titleMedium)
            Text(
                "命中商户或备注里的关键词时优先用指定分类，优先级高于内置规则",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            rules.forEach { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(rule.keyword, modifier = Modifier.weight(1f))
                    Text(
                        "→ ${CategoryCatalog.of(rule.categoryId).name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .clickable {
                                AutoCategoryRuleStore.remove(context, rule.keyword)
                                refresh++
                            }
                            .padding(start = 12.dp),
                    )
                }
            }

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("关键词（如：瑞幸）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TransactionType.EXPENSE,
                    onClick = { type = TransactionType.EXPENSE; chosen = null },
                    label = { Text("支出") },
                )
                FilterChip(
                    selected = type == TransactionType.INCOME,
                    onClick = { type = TransactionType.INCOME; chosen = null },
                    label = { Text("收入") },
                )
                TextButton(onClick = { showPicker = true }) {
                    Text(chosen?.name ?: "选择分类")
                }
            }

            TextButton(
                enabled = keyword.isNotBlank() && chosen != null,
                onClick = {
                    val target = chosen ?: return@TextButton
                    AutoCategoryRuleStore.add(context, keyword, target.id)
                    keyword = ""
                    chosen = null
                    refresh++
                },
            ) {
                Text("添加规则")
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择分类") },
            text = {
                LazyColumn {
                    items(categories, key = { it.id }) { category ->
                        Text(
                            text = category.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chosen = category
                                    showPicker = false
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        )
    }
}