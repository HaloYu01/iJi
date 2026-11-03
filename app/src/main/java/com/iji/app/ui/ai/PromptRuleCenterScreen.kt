package com.iji.app.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.iji.app.ai.AiConfig
import com.iji.app.ai.AiPromptKind
import com.iji.app.ai.LocalRule
import com.iji.app.ai.LocalRuleStore
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.mine.MineSubPage
import com.iji.app.ui.theme.AppleSeparator
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/**
 * 「提示词与规则」总览页：**小节标题 + 分组卡片 + 一行一个入口（标题 / 状态摘要 / 右箭头）**，二级页再去编辑。
 *
 * 两段：
 * 1. **提示词**：5 种各一行，摘要只标「默认 / 自定义」——管什么、何时生效在编辑页顶部说明；
 * 2. **本地识别规则**：自建规则 + 折叠的内置规则（默认收起）+ 新增入口，卡底一行「导出 / 导入 / 恢复内置」。
 *
 * 内置规则默认折叠：13 条按分类铺开会把页面拉到三屏，而它们极少被逐条编辑；
 * 展开是显式动作，总览页保持一屏可扫。
 */
@Composable
fun PromptRuleCenterScreen(
    onBack: () -> Unit,
    onOpenPrompt: (AiPromptKind) -> Unit,
    onOpenRule: (ruleId: String) -> Unit,
    onCreateRule: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    fun readCustoms(): Map<AiPromptKind, String> =
        AiPromptKind.entries.associateWith { AiConfig.getPromptCustom(context, it) }

    // 初值**同步预填**：两个数据源都是进程内缓存（LocalRuleStore.rules 返回 cache 实例，
    // getPromptCustom 走 SettingsRepository 内存缓存），主线程零磁盘 IO。
    //
    // 不能先给空值再靠 ON_RESUME 回填——导航进子页后本页组合会被销毁，返回时重组从
    // remember 初值开始：空值会让「内置规则 · N 条」等先渲染成 0 / 空，等 ON_RESUME 写回
    // 真值再刷一遍，即每次进出子页都白跑一轮"先空后填"（多一次整表重组 + 可见闪烁）。
    var rules by remember { mutableStateOf(LocalRuleStore.rules(context)) }
    /** 每种提示词的自定义文本（空串 = 默认） */
    var customs by remember { mutableStateOf(readCustoms()) }
    var showRestore by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var builtinsOpen by rememberSaveable { mutableStateOf(false) }

    /** 子页 / 弹窗改动数据后重读；值没变时 structural equality 不触发重组 */
    fun reload() {
        rules = LocalRuleStore.rules(context)
        customs = readCustoms()
    }

    // 每次回到本页都重读：从二级页改完保存返回时，列表摘要必须立刻反映新值
    LifecycleResumeEffect(Unit) {
        reload()
        onPauseOrDispose { }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(LocalRuleStore.exportJson(context))
                    } ?: error("无法写入文件")
                }.isSuccess
            }
            snackbar.show(if (ok) "规则已导出" else "导出失败：无法写入文件")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (raw.isNullOrBlank()) snackbar.show("导入失败：读不到文件内容") else pendingImport = raw
        }
    }

    MineSubPage(title = "提示词与规则", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── 1. 提示词 ──
            SectionTitle("提示词")
            RuleGroup {
                AiPromptKind.entries.forEachIndexed { index, kind ->
                    RuleRow(
                        title = kind.title,
                        summary = if (customs[kind].isNullOrBlank()) "默认" else "自定义",
                        onClick = { onOpenPrompt(kind) },
                    )
                    if (index < AiPromptKind.entries.lastIndex) HairLine()
                }
            }

            // ── 2. 本地识别规则：自建在上，内置折叠，数据操作并进卡底 ──
            SectionTitle("本地识别规则")
            RuleGroup {
                val userRules = rules.filter { !it.builtin }
                val builtinRules = rules.filter { it.builtin }
                userRules.forEach { rule ->
                    RuleRow(
                        title = if (rule.enabled) rule.name else "${rule.name}（已停用）",
                        summary = rule.summary(),
                        onClick = { onOpenRule(rule.id) },
                    )
                    HairLine()
                }
                RuleRow(title = "新增规则", summary = "", onClick = onCreateRule)
                HairLine()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { builtinsOpen = !builtinsOpen }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "内置规则 · ${builtinRules.size} 条",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        MiuixIcons.ChevronForward,
                        contentDescription = if (builtinsOpen) "收起内置规则" else "展开内置规则",
                        tint = TextSecondary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                            .rotate(if (builtinsOpen) 90f else 0f),
                    )
                }
                AnimatedVisibility(visible = builtinsOpen) {
                    Column {
                        builtinRules.forEachIndexed { index, rule ->
                            RuleRow(
                                title = if (rule.enabled) rule.name else "${rule.name}（已停用）",
                                summary = rule.summary(),
                                onClick = { onOpenRule(rule.id) },
                            )
                            if (index < builtinRules.lastIndex) HairLine()
                        }
                    }
                }
                HairLine()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    IjiTextAction(
                        text = "导出",
                        compact = true,
                        onClick = { exportLauncher.launch("iji-本地识别规则.json") },
                    )
                    IjiTextAction(
                        text = "导入",
                        compact = true,
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    )
                    IjiTextAction(
                        text = "恢复内置",
                        compact = true,
                        onClick = { showRestore = true },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // 恢复内置（只补缺失项，所以不需要"会覆盖"的措辞）
    IjiDialog(
        show = showRestore,
        onDismissRequest = { showRestore = false },
        title = "恢复内置规则",
        summary = "只补回被删掉的内置项，你自建的规则不动。",
        confirmText = "恢复",
        onConfirm = {
            showRestore = false
            val ok = LocalRuleStore.restoreBuiltins(context)
            reload()
            snackbar.show(if (ok) "已补回内置规则" else "内置规则都在，无需恢复")
        },
    )

    // 导入是整表替换，必须确认：否则一次误点就把用户自建规则清空
    IjiDialog(
        show = pendingImport != null,
        onDismissRequest = { pendingImport = null },
        title = "导入规则",
        summary = "会整表替换现有规则（含自建的），建议先导出备份。",
        confirmText = "替换",
        onConfirm = {
            val raw = pendingImport
            pendingImport = null
            val count = raw?.let { LocalRuleStore.importJson(context, it) } ?: -1
            reload()
            snackbar.show(if (count >= 0) "已导入 $count 条规则" else "导入失败：文件格式不对")
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    // 与设置页同一套节奏：标题离开自己这张卡片 10dp，不要贴上去
    SmallTitle(text = text, insideMargin = PaddingValues(start = 4.dp, top = 10.dp, bottom = 10.dp))
}

/** 分组卡片：与「我的」页同款圆角与底色，一组一张卡 */
@Composable
private fun RuleGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column { content() }
    }
}

/**
 * 一行入口：标题 + 状态摘要 + 右箭头（参照项目的 settingsGroupItem 排布）。
 *
 * 摘要最多两行：提示词的自定义内容可能很长，截断比撑高整行更好扫读。
 */
@Composable
private fun RuleRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp),
        )
    }
}

/** 组内发丝线：左侧留 20dp 对齐文字起点（与「我的」页的分隔线同款） */
@Composable
private fun HairLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp)
            .height(0.5.dp)
            .background(AppleSeparator),
    )
}
