package com.iji.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iji.app.ai.AiConfig
import com.iji.app.ai.AiPromptKind
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.mine.MineSubPage
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 单条提示词的编辑页（提示词与规则 → 某一种提示词）。
 *
 * 语义约定（与 [AiConfig.resolvePrompt] 一致）：
 * - 编辑框里看到的**就是实际生效的**（未自定义时显示默认文本）；
 * - 保存时若内容与默认逐字相同，就存空串，等于「使用默认」，避免产生一份复制品；
 * - 「恢复默认」立即写盘，不等保存 —— 与旧版提示词页的行为保持一致。
 *
 * 为什么不在一页里用 chips 切换多种提示词：提示词从 2 条涨到 5 条，chips 会横向溢出，
 * 而每条都要 300dp 以上的编辑高度；真实生效的上下文（[AiPromptKind.effect]）也需要独立位置说明。
 */
@Composable
fun PromptEditScreen(kindKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val kind = remember(kindKey) { AiPromptKind.ofKey(kindKey) }

    if (kind == null) {
        // 路由参数被手改坏时不要崩，也不要默默编辑别人的提示词
        LaunchedEffect(Unit) {
            snackbar.show("未找到这段提示词")
            onBack()
        }
        return
    }

    val initial = remember(kind) { AiConfig.getPromptCustom(context, kind) }
    var draft by remember(kind) { mutableStateOf(initial) }
    val effective = draft.ifBlank { kind.defaultText }
    val isCustom = draft.isNotBlank()
    val dirty = draft != initial

    fun save() {
        // 与默认逐字一致 → 存空，视为「使用默认」，不制造无意义的自定义副本
        val value = draft.trim().let { if (it == kind.defaultText.trim()) "" else it }
        AiConfig.setPromptCustom(context, kind, value)
        onBack()
    }

    fun resetToDefault() {
        AiConfig.setPromptCustom(context, kind, "")
        draft = ""
    }

    MineSubPage(
        title = kind.title,
        onBack = onBack,
        endAction = {
            IjiTextAction(text = "保存", onClick = { save() }, enabled = dirty, compact = true)
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IjiFormCard {
                Column(Modifier.padding(16.dp)) {
                    Text(kind.usage, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        kind.effect,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    if (isCustom) "当前：自定义" else "当前：默认",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                IjiTextAction(
                    text = "恢复默认",
                    compact = true,
                    enabled = isCustom,
                    onClick = { resetToDefault() },
                )
            }

            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sheet))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                ) {
                    TextField(
                        value = effective,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${effective.length} 字符",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
