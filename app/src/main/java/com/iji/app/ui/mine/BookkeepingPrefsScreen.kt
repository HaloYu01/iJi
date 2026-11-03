package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.accessibility.AutoBookkeepingPrefs
import com.iji.app.ui.components.IjiCategoryChips
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiFormSectionTitle
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 记账偏好：自动记账真正会用到、且 iji 有数据模型的几项（对照 AutoAccounting「记账设置」）。
 *
 * 只列有落点的设置：
 * - **落账账本**：识屏悬浮窗与通知确认记到哪本账；
 * - **备注格式**：`{商户}` / `{来源}` 模板，带实时预览；
 * - **重复通知合并**：同一条通知重复推送时的去重窗口。
 *
 * 改一项立刻生效（不需要「保存」按钮），与系统设置页的习惯一致。
 * 手续费 / 债务 / 资产映射 / 自动建分类在 iji 没有对应模型，不列。
 */
@Composable
fun BookkeepingPrefsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    var ledger by remember { mutableStateOf(AutoBookkeepingPrefs.ledger(context)) }
    var template by remember { mutableStateOf(AutoBookkeepingPrefs.noteTemplate(context)) }
    var dedup by remember { mutableStateOf(AutoBookkeepingPrefs.dedupSeconds(context)) }

    // 第一个空串 = 「跟随当前账本」，与 AutoBookkeepingPrefs.effectiveLedger 的约定一致
    val ledgerOptions = remember(viewModel.ledgers.toList()) {
        listOf("") + viewModel.ledgers.toList()
    }

    MineSubPage(title = "记账偏好", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 24.dp),
            // 组内 8dp 基准 + 组间补 12dp：与「偏好设置」页的分组节奏一致
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column {
                IjiFormSectionTitle("落账账本")
                // chip 左缘与标题 / 说明对齐（两者都带 4dp 起始缩进）；
                // 标题自带 6dp 底距，这里补 6dp 凑成 12dp——与子页面卡片间距（cardGap）一致
                IjiCategoryChips(
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    items = ledgerOptions,
                    selected = ledger,
                    onSelect = {
                        ledger = it
                        AutoBookkeepingPrefs.setLedger(context, it)
                    },
                    labelOf = { if (it.isBlank()) "跟随当前账本" else it },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "识屏悬浮窗与通知确认的账单记到这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Column {
                IjiFormSectionTitle("备注格式")
                // 顶部同样补 6dp：三个块的「标题→内容」间距统一为 12dp
                IjiFormCard(modifier = Modifier.padding(top = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "支持 {商户} 与 {来源} 两个占位符；留空则只用商户名。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sheet))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                        ) {
                            TextField(
                                value = template,
                                onValueChange = {
                                    template = it
                                    AutoBookkeepingPrefs.setNoteTemplate(context, it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "预览：" + AutoBookkeepingPrefs.buildNote(template, "瑞幸咖啡", "微信支付")
                                .ifBlank { "（空，将使用商户名）" },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Column {
                IjiFormSectionTitle("重复通知合并")
                IjiCategoryChips(
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    items = AutoBookkeepingPrefs.DEDUP_OPTIONS,
                    selected = dedup,
                    onSelect = {
                        dedup = it
                        AutoBookkeepingPrefs.setDedupSeconds(context, it)
                    },
                    labelOf = { "$it 秒" },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "同来源、同金额的通知在窗口内只提示一次，避免重复入账。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
