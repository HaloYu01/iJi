package com.iji.app.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.iji.app.accessibility.AutoBookkeepingPrefs
import com.iji.app.ai.AiConfig
import com.iji.app.ai.AiPromptKind
import com.iji.app.ai.LocalRule
import com.iji.app.ai.LocalRuleStore
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.IjiSettingsSection
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.mine.MineSubPage
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/**
 * 「提示词与规则」总览页（参照 AutoAccounting 的「提示词管理」+「分类规则」列表）。
 *
 * 三个板块（标题即分类，与「我的」页同一套 SmallTitle + 分组卡节奏）：
 * 1. **AI 提示词**：5 种各一行，摘要显示自定义内容预览（自定义时截前 48 字，对齐参考项目的
 *    `prompt.take(100)`）；AI 未配 Key 时卡片顶部给一条「暂不生效」提示；
 * 2. **本地识别规则**：与「我的」页同款的一张分组卡——自建规则在上（**行内长按拖拽排序**，
 *    顺序即并列命中时的优先级，拖拽机制同资产页账户组卡）、「新增规则」入口、内置规则折叠、
 *    导出 / 导入 / 恢复内置；
 * 3. **备注格式**：识屏 / 通知落账时备注的模板（占位符 + 预览 + 校验）。2026-11-10 从
 *    自动记账页迁来，与提示词、识别规则同属「文本规则」类配置，改一项即时写盘。
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
    val haptics = LocalHapticFeedback.current

    fun readCustoms(): Map<AiPromptKind, String> =
        AiPromptKind.entries.associateWith { AiConfig.getPromptCustom(context, it) }

    // 初值**同步预填**：两个数据源都是进程内缓存（LocalRuleStore.rules 返回 cache 实例，
    // getPromptCustom 走 SettingsRepository 内存缓存），主线程零磁盘 IO。
    var rules by remember { mutableStateOf(LocalRuleStore.rules(context)) }
    /** 每种提示词的自定义文本（空串 = 默认） */
    var customs by remember { mutableStateOf(readCustoms()) }
    var showRestore by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var builtinsOpen by rememberSaveable { mutableStateOf(false) }
    /** 备注模板：与提示词/规则一样，改一项即时写盘，不做「保存」按钮 */
    var template by remember { mutableStateOf(AutoBookkeepingPrefs.noteTemplate(context)) }
    val unknownPlaceholders = remember(template) {
        Regex("\\{[^}]*\\}").findAll(template)
            .map { it.value }
            .filter { it != "{商户}" && it != "{来源}" }
            .toList()
    }

    // ── 拖拽排序（资产页账户组卡同款：本地顺序即时换位，松手才写盘）──
    var order by remember { mutableStateOf(rules.filter { !it.builtin }) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }

    // 非拖拽时跟随外部数据（子页改动 / 落盘后的新顺序），拖拽中不打断手势
    LaunchedEffect(rules) {
        if (draggingId == null) order = rules.filter { !it.builtin }
    }

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

    fun move(from: Int, to: Int) {
        if (from == to || to !in order.indices) return
        order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun handleDrag(id: String, dy: Float) {
        if (draggingId != id) return
        dragOffset += dy
        val index = order.indexOfFirst { it.id == id }
        if (index < 0) return
        val nextHeight = order.getOrNull(index + 1)?.let { rowHeights[it.id] }?.toFloat() ?: 0f
        val prevHeight = order.getOrNull(index - 1)?.let { rowHeights[it.id] }?.toFloat() ?: 0f
        when {
            dragOffset > 0f && nextHeight > 0f && dragOffset > nextHeight * 0.5f -> {
                move(index, index + 1)
                dragOffset -= nextHeight
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
            dragOffset < 0f && prevHeight > 0f && -dragOffset > prevHeight * 0.5f -> {
                move(index, index - 1)
                dragOffset += prevHeight
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }

    fun finishDrag() {
        val moved = draggingId != null && order.map { it.id } != rules.filter { !it.builtin }.map { it.id }
        draggingId = null
        dragOffset = 0f
        if (!moved) return
        // 落盘后同步刷新 rules（缓存已更新，同步读即可），LaunchedEffect 会把 order 对齐
        scope.launch {
            withContext(Dispatchers.IO) { LocalRuleStore.reorder(context, order.map { it.id }) }
            reload()
        }
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
            if (!ok) snackbar.show("导出失败：无法写入文件")
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
            // 组间距与设置页一致；标题→卡片由 SmallTitle 自己给 10dp，不再叠加在这里
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 1. AI 提示词：全部走联网大模型 ──
            IjiSettingsSection("AI 提示词") {
                if (!AiConfig.aiReady(context)) {
                    // 对齐参考项目的「AI 不可用时提示词不生效」：不置灰（允许提前写），
                    // 但必须让用户知道改了暂时不会生效
                    Text(
                        "未配置 AI Key：AI 提示词可以编辑，配置后自动生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                    IjiSettingsDivider()
                }
                AiPromptKind.entries.forEachIndexed { index, kind ->
                    val custom = customs[kind].orEmpty()
                    // 自定义时摘要给内容预览（对齐参考项目 prompt.take(100)），默认时给说明
                    val summary = if (custom.isBlank()) {
                        "默认"
                    } else {
                        custom.replace('\n', ' ').let { it.take(48) + if (it.length > 48) "…" else "" }
                    }
                    RuleRow(
                        title = kind.title,
                        summary = summary,
                        onClick = { onOpenPrompt(kind) },
                    )
                    if (index < AiPromptKind.entries.lastIndex) IjiSettingsDivider()
                }
            }

            // ── 2. 本地识别规则：离线关键词匹配，与 AI 提示词互不相干 ──
            IjiSettingsSection("本地识别规则") {
                // 自建规则行（行内长按拖拽）；「自动 · ××」是记忆规则（AI 结果卡勾选生成）
                order.forEachIndexed { index, rule ->
                    val dragging = draggingId == rule.id
                    val startDrag = rememberUpdatedState<() -> Unit> {
                        draggingId = rule.id
                        dragOffset = 0f
                    }
                    val dragBy = rememberUpdatedState<(Float) -> Unit> { dy -> handleDrag(rule.id, dy) }
                    val endDrag = rememberUpdatedState<() -> Unit> { finishDrag() }
                    SortableRuleRow(
                        rule = rule,
                        onClick = { onOpenRule(rule.id) },
                        modifier = Modifier
                            .onGloballyPositioned { rowHeights[rule.id] = it.size.height }
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragging) dragOffset else 0f
                                shadowElevation = if (dragging) 12.dp.toPx() else 0f
                            }
                            .pointerInput(rule.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        startDrag.value()
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragBy.value(amount.y)
                                    },
                                    onDragEnd = { endDrag.value() },
                                    onDragCancel = { endDrag.value() },
                                )
                            },
                    )
                    IjiSettingsDivider()
                }
                RuleRow(title = "新增规则", summary = "", onClick = onCreateRule)
                IjiSettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { builtinsOpen = !builtinsOpen }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "内置规则 · ${rules.count { it.builtin }} 条",
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
                        rules.filter { it.builtin }.forEachIndexed { index, rule ->
                            IjiSettingsDivider()
                            RuleRow(
                                title = if (rule.enabled) rule.name else "${rule.name}（已停用）",
                                summary = rule.summary(),
                                onClick = { onOpenRule(rule.id) },
                            )
                        }
                    }
                }
                IjiSettingsDivider()
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

            // ── 3. 备注格式：落账备注的生成规则（2026-11-10 从自动记账页迁入）──
            IjiSettingsSection("备注格式") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        "识屏与通知落账时备注的写法。支持 {商户} 与 {来源} 两个占位符；留空则只用商户名。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = template,
                        onValueChange = {
                            template = it
                            AutoBookkeepingPrefs.setNoteTemplate(context, it)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (unknownPlaceholders.isNotEmpty()) {
                        Text(
                            "未知占位符 ${unknownPlaceholders.joinToString("、")} 会原样写进备注，请检查",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        "预览：" + AutoBookkeepingPrefs.buildNote(template, "瑞幸咖啡", "微信支付")
                            .ifBlank { "（空，将使用商户名）" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
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
            LocalRuleStore.restoreBuiltins(context)
            reload()
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
            if (count < 0) snackbar.show("导入失败：文件格式不对")
        },
    )
}

/**
 * 自建规则行：与普通 [RuleRow] 同款排版（标题 + 摘要 + 右箭头），行尾多一个拖拽把手，
 * 长按整行拖动排序；「自动 · ××」为记忆规则（AI 结果卡勾选「记住分类」生成）。
 */
@Composable
private fun SortableRuleRow(rule: LocalRule, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (rule.enabled) rule.name else "${rule.name}（已停用）",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                rule.summary(),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp),
        )
        // 拖拽把手：纯装饰，实际拖拽作用在整行上
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .size(width = 22.dp, height = 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(width = 16.dp, height = 2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(TextSecondary.copy(alpha = 0.6f)),
                    )
                }
            }
        }
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
