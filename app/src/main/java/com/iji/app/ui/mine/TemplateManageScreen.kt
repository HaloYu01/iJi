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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.TemplateRepository
import com.iji.app.data.repository.TransactionTemplate
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.IjiPrimaryButtonBlue
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.IjiIconSize
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/**
 * 记账模板管理页（设置 → 记账设置 → 模板设置）。
 *
 * 模板解决的是真实高频场景：每天的早餐、通勤、房租这类**完全重复**的账单，
 * 不必每次重选分类和金额。
 *
 * 交互口径：
 * - **点行 = 进入编辑页**（新建 / 编辑都在 [TemplateEditScreen] 整页完成）。
 *   旧版点行会直接记一笔，用户在列表里想改模板却总是多记一笔账，已纠正。
 * - 删除在编辑页里做且带确认——旧版列表行上裸露「删除」且无确认，误触即删。
 * - 底部「新建模板」= 空白表单。
 * - 真正"按模板记一笔"的入口在记一笔面板（模板选择弹窗），本页不做一键入账。
 */
@Composable
fun TemplateManageScreen(
    onBack: () -> Unit,
    onCreateTemplate: () -> Unit,
    onEditTemplate: (Long) -> Unit,
) {
    val context = LocalContext.current

    val templates by TemplateRepository.observe(context)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    MineSubPage(title = "记账模板", onBack = onBack) {
        if (templates.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "还没有模板\n把每天都要记的账（早餐、通勤…）固化成一键录入",
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
                    // 顶部让位给浮层顶栏（安全区 + 52dp 折叠态）
                    top = ijiTopBarContentPadding(),
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(templates, key = { it.id }) { t ->
                    TemplateRow(
                        template = t,
                        onClick = { onEditTemplate(t.id) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        // 新建模板
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalDimens.current.screenPadding, vertical = 16.dp)
                .clip(RoundedCornerShape(Radius.xl))
                .background(IjiPrimaryButtonBlue)
                .clickable(onClick = onCreateTemplate)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("新建模板", color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

/** 模板行：图标 + 名称 + 摘要 + 右箭头（点行进入编辑页） */
@Composable
private fun TemplateRow(
    template: TransactionTemplate,
    onClick: () -> Unit,
) {
    val category = CategoryCatalog.of(template.categoryId)
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconImage(
                name = category.name,
                color = Color(category.color),
                size = IjiIconSize.ListRow,
                categoryId = category.id,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.bodyLarge)
                // 副行只拼非空项：分类 · 金额（· 账户）；拆分模板的金额按合计展示。
                // 必须用 listOfNotNull：`listOf(a, b) + nullable` 会被推断成 List<String?>，
                // joinToString 把空账户渲染成字面量 "null"。
                Text(
                    listOfNotNull(
                        category.name,
                        formatMoney(template.amountCents),
                        template.account.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Icon(
                MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(IjiIconSize.Action),
            )
        }
    }
}
