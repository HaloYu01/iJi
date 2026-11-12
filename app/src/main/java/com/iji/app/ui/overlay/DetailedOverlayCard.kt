package com.iji.app.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ai.BillDraft
import com.iji.app.data.db.DbProvider
import com.iji.app.data.model.Category
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.QuickBillWriter
import com.iji.app.data.repository.TagStore
import com.iji.app.ui.components.AccountIconImage
import com.iji.app.ui.components.CategoryIconImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 「详细版」悬浮卡内容 —— 按 Figma 设计稿（927 × 607）还原，配色跟 app 主色统一（冷蓝）。
 * 卡片外壳（渐变底 / 描边 / 出退场动效）与品牌行由 [OverlayShell] / [OverlayBrandRow]
 * 在父层提供，这里只负责字段区与底部按钮。
 *
 * ## 尺寸口径
 * 卡片自身用**固定 dp**（不乘设计稿 scale）：悬浮窗宽度固定为屏宽 − 24dp，
 * 固定 dp 在任何屏宽下都保持可读、可点，不会被压成 8sp 小字。
 *
 * ## 与设计稿的差异（刻意）
 * 1. 设计稿只画了单笔；多笔时父层顶部有进度条，保存/跳过逐笔推进。
 * 2. 展开编辑区（金额/备注键盘、分类/账户/日期/标签面板）在稿里没有 —— 这部分按「少滑动」重做：
 *    账户、分类、日期全部走**横向宫格 / 横向一级条**，面板高度固定，卡框与按钮位置不动。
 * 3. 稿中圆形是占位：账户用真实素材图标，分类填真实分类色。
 *
 * ## 编辑状态归属（关键）
 * [BillDraft] 本体由父层持有：本卡每次修改都通过 [onDraftChange] 写回，
 * 这样「详细 ↔ 简洁」切换排版时，已改的金额/分类/账户/备注/日期都不会丢；
 * 转入账户、勾选项、标签、时分是逐笔的辅助状态，留在卡内（切走再回来重置为默认，可接受）。
 */
@Composable
internal fun DetailedOverlayCard(
    drafts: List<BillDraft>,
    initialIndex: Int,
    onDraftChange: (Int, BillDraft) -> Unit,
    onIndexChange: (Int) -> Unit,
    onDone: (Int) -> Unit,
    ledgerId: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // 跨样式切换保留进度：index 由父层持有，这里只在首次组合时取初值
    var index by remember {
        mutableIntStateOf(initialIndex.coerceIn(0, (drafts.size - 1).coerceAtLeast(0)))
    }
    /** 当前笔 = 父层列表的对应项；样式切换回来也能拿到改过的值 */
    val draft = drafts[index.coerceAtMost((drafts.size - 1).coerceAtLeast(0))]
    /** 转账的转入账户（[BillDraft] 只有转出账户） */
    var transferTo by remember { mutableStateOf("") }
    var excludeFromBudget by remember { mutableStateOf(false) }
    var includeAsset by remember { mutableStateOf(true) }
    var selectedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var editing by remember { mutableStateOf<OverlayField?>(null) }
    var busy by remember { mutableStateOf(false) }
    var writtenCount by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    /** 忽略整卡的二次确认：第一次点击只进入确认态，超时自动退出 */
    var confirmDiscard by remember { mutableStateOf(false) }
    /** 当天的第几分钟；默认取此刻 —— 识屏多发生在刚付完款，时分基本就是真实时间 */
    var minuteOfDay by remember { mutableIntStateOf(nowMinuteOfDay()) }

    var accounts by remember { mutableStateOf<List<AccountOption>>(emptyList()) }
    var tagPool by remember { mutableStateOf(TagStore.DEFAULT) }

    /** 统一修改当前笔：写回父层，让「详细 ↔ 简洁」共享同一份编辑 */
    fun updateDraft(transform: (BillDraft) -> BillDraft) {
        val i = index.coerceAtMost((drafts.size - 1).coerceAtLeast(0))
        onDraftChange(i, transform(drafts[i]))
    }

    LaunchedEffect(Unit) {
        accounts = withContext(Dispatchers.IO) {
            runCatching {
                DbProvider.database.accountDao().getAllOnce()
                    .filter { it.name.isNotBlank() }
                    .map { AccountOption(name = it.name, iconKey = it.iconKey.ifBlank { it.name }) }
            }.getOrDefault(emptyList())
        }
        tagPool = runCatching { TagStore.observe(context).first() }.getOrDefault(TagStore.DEFAULT)
        transferTo = accounts.firstOrNull()?.name.orEmpty()
        // 识别没给出账户时补默认：同样走父层写回，保证两个模式看到一致的数据
        if (draft.account.isBlank() && accounts.isNotEmpty()) {
            updateDraft { it.copy(account = accounts.first().name) }
        }
    }

    // 保存完最后一笔：让「已记 N 笔」被看见再收卡
    LaunchedEffect(finished) {
        if (finished) {
            onDone(writtenCount)
            delay(800)
            onClose()
        }
    }

    // 「确认忽略？」只在短时间内有效，超时自动退回普通按钮，避免误触窗口开太久
    LaunchedEffect(confirmDiscard) {
        if (confirmDiscard) {
            delay(2600)
            confirmDiscard = false
        }
    }

    fun stopEditing() {
        editing = null
        BillOverlay.setWindowFocusable(false)
        BillOverlay.hideIme()
    }

    fun startEditing(field: OverlayField) {
        if (field == OverlayField.Amount || field == OverlayField.Note) {
            BillOverlay.setWindowFocusable(true)
        }
        editing = if (editing == field) null else field
    }

    /** 推进到下一笔（保存成功或主动跳过都走这里）；最后一笔则进入完成态 */
    fun advance() {
        if (index + 1 < drafts.size) {
            index += 1
            onIndexChange(index)
            transferTo = accounts.firstOrNull()?.name.orEmpty()
            selectedTags = emptyList()
            excludeFromBudget = false
            includeAsset = true
            editing = null
            confirmDiscard = false
            minuteOfDay = nowMinuteOfDay()
        } else {
            finished = true
        }
    }

    fun commit() {
        if (busy || finished || draft.amountCents == 0L) return
        stopEditing()
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                QuickBillWriter.addDetailed(
                    ledgerId = ledgerId,
                    type = draft.type,
                    categoryId = draft.categoryId,
                    amountCents = draft.amountCents,
                    note = draft.note,
                    account = if (includeAsset) draft.account else "",
                    transferToAccount = if (draft.type == TransactionType.TRANSFER) transferTo else "",
                    tags = selectedTags,
                    date = draft.date,
                    excludeFromBudget = excludeFromBudget,
                    minuteOfDay = minuteOfDay,
                )
            }
            busy = false
            if (!ok) return@launch
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            writtenCount += 1
            advance()
        }
    }

    // 金额 / 备注是**行内编辑**，不占展开区；只有下面这几项才把中部整块换成选择面板。
    // 之前这里直接拿 `editing` 当 AnimatedContent 的 key，点备注时中部会切到空分支，
    // 整个字段区瞬间变空白 —— 就是"点备注显示异常"的根因。
    val panel = editing?.takeIf { it.isPanel() }

    Column(Modifier.fillMaxWidth()) {
        // ── 金额 + 类型 ──
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (finished) {
                Text(
                    text = "已记 $writtenCount 笔",
                    style = TextStyle(
                        fontFamily = InterMedium,
                        fontSize = 24.sp,
                        color = OverlayPalette.Accent,
                    ),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(Modifier.weight(1f).clickable { startEditing(OverlayField.Amount) }) {
                    if (editing == OverlayField.Amount) {
                        AmountInlineEditor(
                            initialCents = draft.amountCents,
                            color = amountColor(draft.type),
                            onCommit = { cents ->
                                updateDraft { it.copy(amountCents = cents) }
                                stopEditing()
                            },
                        )
                    } else {
                        Text(
                            // 金额为 0（识别失败兜底）时给明确指引，别让 "-0.00" 假装是数据
                            text = if (draft.amountCents == 0L) "待输入" else OverlayFormat.amountLabel(draft),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontFamily = InterMedium,
                                fontSize = 31.sp,
                                letterSpacing = (-0.6).sp,
                                color = if (draft.amountCents == 0L) {
                                    OverlayPalette.Hint
                                } else {
                                    amountColor(draft.type)
                                },
                            ),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                TypePill(
                    type = draft.type,
                    onClick = {
                        stopEditing()
                        confirmDiscard = false
                        val next = when (draft.type) {
                            TransactionType.EXPENSE -> TransactionType.INCOME
                            TransactionType.INCOME -> TransactionType.TRANSFER
                            TransactionType.TRANSFER -> TransactionType.EXPENSE
                        }
                        val fallback = when (next) {
                            TransactionType.INCOME -> CategoryCatalog.incomes
                            TransactionType.TRANSFER -> CategoryCatalog.transfers
                            else -> CategoryCatalog.expenses
                        }.firstOrNull()?.id.orEmpty()
                        updateDraft {
                            it.copy(
                                type = next,
                                categoryId = fallback,
                                account = it.account.ifBlank { accounts.firstOrNull()?.name.orEmpty() },
                            )
                        }
                        if (next == TransactionType.TRANSFER) {
                            transferTo = transferTo.ifBlank { accounts.firstOrNull()?.name.orEmpty() }
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── 中部：收起时是字段区，展开时同一块区域换成选择面板（高度固定，卡框不跳） ──
        Box(Modifier.fillMaxWidth().height(BodyHeight)) {
            AnimatedContent(
                targetState = panel,
                transitionSpec = {
                    (fadeIn(tween(170)) + slideInVertically(tween(190)) { it / 14 }) togetherWith
                        fadeOut(tween(120))
                },
                label = "overlayBody",
            ) { field ->
                if (field == null) {
                    Column(Modifier.fillMaxSize()) {
                        val category = runCatching { CategoryCatalog.of(draft.categoryId) }.getOrNull()
                        if (draft.type == TransactionType.TRANSFER) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                FieldCell(
                                    modifier = Modifier.weight(1f),
                                    label = "转出",
                                    value = draft.account.ifBlank { "选择账户" },
                                    dimmed = draft.account.isBlank(),
                                    icon = { AccountIcon(accounts, draft.account) },
                                    onClick = { startEditing(OverlayField.Account) },
                                )
                                Text(
                                    text = "→",
                                    style = TextStyle(fontSize = 14.sp, color = OverlayPalette.Tertiary),
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                )
                                FieldCell(
                                    modifier = Modifier.weight(1f),
                                    label = "转入",
                                    value = transferTo.ifBlank { "选择账户" },
                                    dimmed = transferTo.isBlank(),
                                    icon = { AccountIcon(accounts, transferTo) },
                                    onClick = { startEditing(OverlayField.TransferTo) },
                                )
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                FieldCell(
                                    modifier = Modifier.weight(1f),
                                    label = "账户",
                                    value = draft.account.ifBlank { "选择账户" },
                                    dimmed = draft.account.isBlank(),
                                    icon = { AccountIcon(accounts, draft.account) },
                                    onClick = { startEditing(OverlayField.Account) },
                                )
                                Spacer(Modifier.width(8.dp))
                                FieldCell(
                                    modifier = Modifier.weight(1f),
                                    label = "分类",
                                    value = category?.name ?: "选择分类",
                                    icon = {
                                        CategoryIconImage(
                                            name = category?.name ?: "分类",
                                            color = category?.let { Color(it.color) }
                                                ?: OverlayPalette.PlaceholderDot,
                                            size = 17.dp,
                                            categoryId = draft.categoryId,
                                        )
                                    },
                                    onClick = { startEditing(OverlayField.Category) },
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            FieldCell(
                                modifier = Modifier.weight(1f),
                                label = "时间",
                                value = OverlayFormat.dateTimeLabel(draft.date, minuteOfDay),
                                icon = {
                                    // 时间 / 备注改用 Miuix 图标，与 app 其余页面同一套字形
                                    Icon(
                                        MiuixIcons.Alarm,
                                        contentDescription = null,
                                        tint = OverlayPalette.Secondary,
                                        modifier = Modifier.size(17.dp),
                                    )
                                },
                                onClick = { startEditing(OverlayField.Date) },
                            )
                            Spacer(Modifier.width(8.dp))
                            FieldCell(
                                modifier = Modifier.width(104.dp),
                                label = "标签",
                                value = selectedTags.firstOrNull() ?: "添加",
                                dimmed = selectedTags.isEmpty(),
                                icon = null,
                                onClick = { startEditing(OverlayField.Tag) },
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        NoteRow(
                            note = draft.note,
                            editing = editing == OverlayField.Note,
                            onClick = { startEditing(OverlayField.Note) },
                            onCommit = { note ->
                                updateDraft { it.copy(note = note) }
                                stopEditing()
                            },
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CheckPill(
                                label = "不计入预算",
                                checked = excludeFromBudget,
                                onClick = {
                                    stopEditing()
                                    excludeFromBudget = !excludeFromBudget
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            CheckPill(
                                label = "计入资产",
                                checked = includeAsset,
                                onClick = {
                                    stopEditing()
                                    includeAsset = !includeAsset
                                },
                            )
                        }
                    }
                } else {
                    when (field) {
                        OverlayField.Account, OverlayField.TransferTo -> {
                            val isTransferTo = field == OverlayField.TransferTo
                            AccountPanel(
                                title = if (isTransferTo) "转入账户" else "账户",
                                accounts = accounts,
                                selected = if (isTransferTo) transferTo else draft.account,
                                onSelect = { name ->
                                    if (isTransferTo) {
                                        transferTo = name
                                    } else {
                                        updateDraft { it.copy(account = name) }
                                    }
                                    stopEditing()
                                },
                                onDismiss = { stopEditing() },
                            )
                        }

                        OverlayField.Category -> CategoryPanel(
                            type = draft.type,
                            selectedKey = draft.categoryId,
                            onSelect = { id ->
                                updateDraft { it.copy(categoryId = id) }
                                stopEditing()
                            },
                            onPickParent = { id ->
                                updateDraft { it.copy(categoryId = id) }
                            },
                            onDismiss = { stopEditing() },
                        )

                        OverlayField.Date -> DatePanel(
                            selected = draft.date,
                            minuteOfDay = minuteOfDay,
                            onSelectDate = { date ->
                                updateDraft { it.copy(date = date) }
                                stopEditing()
                            },
                            onMinuteChange = { minuteOfDay = it },
                            onDismiss = { stopEditing() },
                        )

                        OverlayField.Tag -> TagPanel(
                            options = tagPool,
                            selected = selectedTags,
                            onToggle = { tag ->
                                selectedTags = if (selectedTags.contains(tag)) {
                                    selectedTags - tag
                                } else {
                                    selectedTags + tag
                                }
                            },
                            onDismiss = { stopEditing() },
                        )

                        // 行内编辑的字段不会走到这里（panel 已过滤），编译器需要穷尽
                        OverlayField.Amount, OverlayField.Note -> Unit
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── 取消（跳过/忽略） / 保存 ──
        // 多笔时「跳过此笔」只影响当前笔、可继续处理下一笔；单笔或最后一笔的
        // 「忽略」会整卡丢弃，所以做二次确认（按钮短暂变红），防手指一偏全没了。
        val canSkipCurrent = drafts.size > 1 && index + 1 < drafts.size
        val canSave = finished || (!busy && draft.amountCents > 0L)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FooterButton(
                text = when {
                    finished -> "已记完"
                    canSkipCurrent -> "跳过此笔"
                    confirmDiscard -> "确认忽略？"
                    else -> "忽略"
                },
                modifier = Modifier.weight(1f),
                container = if (confirmDiscard) OverlayPalette.ExpenseText else Color.Transparent,
                content = if (confirmDiscard) OverlayPalette.OnAccent else OverlayPalette.Secondary,
                outlined = !confirmDiscard,
                enabled = !finished,
                onClick = {
                    when {
                        finished -> Unit
                        canSkipCurrent -> {
                            stopEditing()
                            confirmDiscard = false
                            advance()
                        }
                        confirmDiscard -> {
                            stopEditing()
                            onClose()
                        }
                        else -> confirmDiscard = true
                    }
                },
            )
            Spacer(Modifier.width(10.dp))
            FooterButton(
                text = when {
                    finished -> "完成"
                    busy -> "保存中…"
                    index + 1 < drafts.size -> "保存并下一笔"
                    else -> "保存"
                },
                modifier = Modifier.weight(1.55f),
                container = OverlayPalette.Accent,
                content = OverlayPalette.OnAccent,
                outlined = false,
                enabled = canSave,
                onClick = {
                    if (finished) {
                        onClose()
                    } else {
                        commit()
                    }
                },
            )
        }
    }
}

// ── 子组件 ──────────────────────────────────────────────────────────────

/** 展开区的固定高度：收起态字段行正好占满，任何面板展开都不会把卡框顶高 */
private val BodyHeight = 208.dp

@Composable
private fun TypePill(type: TransactionType, onClick: () -> Unit) {
    val background by animateColorAsState(chipBg(type), tween(180), label = "pillBg")
    val foreground by animateColorAsState(chipInk(type), tween(180), label = "pillFg")
    Box(
        Modifier
            .heightIn(min = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .pressableScale(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = OverlayFormat.typeLabel(type),
            style = TextStyle(
                fontFamily = InterRegular,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                color = foreground,
            ),
        )
    }
}

/** 字段卡里的账户图标：账户名 → 素材 key，查不到就用名字兜底 */
@Composable
private fun AccountIcon(accounts: List<AccountOption>, name: String) {
    AccountIconImage(
        iconKey = accounts.firstOrNull { it.name == name }?.iconKey ?: name,
        size = 17.dp,
    )
}

/**
 * 字段卡（账户 / 分类 / 时间 / 标签共用）。
 *
 * 用「小标签 + 值」两行结构而不是单行「图标 + 文字」：
 * 收起态就能读出这格是什么，不必猜；两行也顺手把高度抬到 52dp，触控更好按。
 */
@Composable
private fun FieldCell(
    label: String,
    value: String,
    icon: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    Column(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OverlayPalette.SurfaceRaised)
            .border(1.dp, OverlayPalette.CardBorder, RoundedCornerShape(14.dp))
            .pressableScale(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 9.5.sp,
                letterSpacing = 0.4.sp,
                color = OverlayPalette.Tertiary,
            ),
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = if (dimmed) OverlayPalette.Hint else OverlayPalette.Ink,
                ),
            )
        }
    }
}

/**
 * 备注行：点一下**就地变成输入框**（不再弹第二个弹窗，也不清空其它字段）。
 *
 * 这一行同时也是"点备注显示异常"的修复点：行内编辑只替换这一行的内容，
 * 中部字段区保持原样，不会像以前那样整块切换成空白。
 */
@Composable
private fun NoteRow(
    note: String,
    editing: Boolean,
    onClick: () -> Unit,
    onCommit: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OverlayPalette.SurfaceRaised)
            .border(1.dp, OverlayPalette.CardBorder, RoundedCornerShape(14.dp))
            .pressableScale(enabled = !editing, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MiuixIcons.Notes,
            contentDescription = null,
            tint = if (note.isBlank()) OverlayPalette.Hint else OverlayPalette.Secondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(9.dp))
        if (editing) {
            NoteInlineEditor(initial = note, onCommit = onCommit)
        } else {
            Text(
                text = note.ifBlank { "备注" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = if (note.isBlank()) OverlayPalette.Hint else OverlayPalette.Ink,
                ),
                modifier = Modifier.weight(1f),
            )
            if (note.isNotBlank()) {
                Text(
                    text = "备注",
                    style = TextStyle(
                        fontSize = 10.5.sp,
                        color = OverlayPalette.Tertiary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CheckPill(label: String, checked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    val background by animateColorAsState(
        if (checked) OverlayPalette.Accent.copy(alpha = 0.12f) else OverlayPalette.SurfaceRaised,
        tween(160),
        label = "checkBg",
    )
    val borderColor by animateColorAsState(
        if (checked) OverlayPalette.Accent.copy(alpha = 0.45f) else OverlayPalette.CardBorder,
        tween(160),
        label = "checkBorder",
    )
    Row(
        Modifier
            .height(34.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .pressableScale(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val boxShape = RoundedCornerShape(4.5.dp)
        Box(
            Modifier
                .size(15.dp)
                .clip(boxShape)
                .background(if (checked) OverlayPalette.Accent else Color.White)
                .border(
                    1.2.dp,
                    if (checked) OverlayPalette.Accent else OverlayPalette.CheckBorder,
                    boxShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = OverlayPalette.OnAccent,
                    ),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.5.sp,
                color = if (checked) OverlayPalette.Ink else OverlayPalette.Secondary,
            ),
        )
    }
}

@Composable
private fun AmountInlineEditor(
    initialCents: Long,
    color: Color,
    onCommit: (Long) -> Unit,
) {
    var text by remember { mutableStateOf(OverlayFormat.centsToPlain(initialCents)) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
        BillOverlay.showIme()
    }
    BasicTextField(
        value = text,
        onValueChange = { raw -> text = OverlayFormat.sanitizeAmount(raw) },
        singleLine = true,
        cursorBrush = SolidColor(color),
        textStyle = TextStyle(
            fontFamily = InterMedium,
            fontSize = 31.sp,
            letterSpacing = (-0.6).sp,
            color = color,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = {
            onCommit(((text.toDoubleOrNull() ?: 0.0) * 100).toLong())
        }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

/**
 * 展开面板的公共骨架：标题行 + 分隔线 + 内容。
 *
 * 整块固定占满中部 208dp（不再把卡片撑高），所以面板再长也只是内部滚动，
 * 卡片外框、底部按钮位置都不动 —— 这是"展开后依然紧凑"的关键。
 */
@Composable
private fun PanelScaffold(
    title: String,
    onDismiss: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 12.sp,
                    letterSpacing = 0.3.sp,
                    color = OverlayPalette.Secondary,
                ),
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
            // 明确的 28dp 命中区：之前只给文字加了 padding，实际可点高度不到 20dp，
            // 悬浮窗上手指一偏就点不中（真机实测"完成"点四五次才关掉）。
            Box(
                Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(OverlayPalette.Accent.copy(alpha = 0.08f))
                    .pressableScale(onClick = onDismiss)
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "完成",
                    style = TextStyle(fontSize = 12.sp, color = OverlayPalette.Accent),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OverlayPalette.CardBorder),
        )
        Spacer(Modifier.height(2.dp))
        content()
    }
}

/** 账户面板：横向 3 列宫格，一屏看完、不用上下滑 */
@Composable
private fun AccountPanel(
    title: String,
    accounts: List<AccountOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PanelScaffold(title = title, onDismiss = onDismiss) {
        if (accounts.isEmpty()) {
            Text(
                text = "还没有资产账户，可先在应用内添加",
                style = TextStyle(fontSize = 12.sp, color = OverlayPalette.Hint),
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@PanelScaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(accounts, key = { it.name }) { account ->
                val isSelected = account.name == selected
                TileShape(
                    selected = isSelected,
                    onClick = { onSelect(account.name) },
                ) {
                    AccountIconImage(iconKey = account.iconKey, size = 22.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = account.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = if (isSelected) OverlayPalette.Accent else OverlayPalette.Secondary,
                        ),
                    )
                }
            }
        }
    }
}

/** 宫格单格：默认浅底 + 描边，选中变主色淡底 + 主色描边 */
@Composable
private fun TileShape(
    selected: Boolean,
    onClick: () -> Unit,
    height: Dp = 54.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(
        if (selected) OverlayPalette.Accent.copy(alpha = 0.10f) else OverlayPalette.SurfaceRaised,
        tween(140),
        label = "tileBg",
    )
    val borderColor by animateColorAsState(
        if (selected) OverlayPalette.Accent.copy(alpha = 0.45f) else OverlayPalette.CardBorder,
        tween(140),
        label = "tileBorder",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .pressableScale(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * 分类面板：**一级横向条 + 二级横向宫格**。
 *
 * 目标是"少操作、少滑动"：
 * - 打开时一级条自动定位到当前分类所属一级，二级宫格立刻可见 → 选同级子分类只要 1 次点击；
 * - 一级条横向滚动，不再把十几个一级纵向铺满整屏；
 * - 点一级 chip = 选中该一级（和记一笔面板一致：一级本身也能作为分类），同时把二级宫格切过去。
 */
@Composable
private fun CategoryPanel(
    type: TransactionType,
    selectedKey: String,
    onSelect: (String) -> Unit,
    /** 点一级 chip：既把它当分类选中（一级本身也能记账），又把二级宫格切过去 */
    onPickParent: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val parents = remember(type) { CategoryCatalog.parentsOfType(type) }
    // 当前分类所属一级作为默认展开项；分类被删空时退回第一条
    val initialParent = remember(selectedKey, parents) {
        runCatching { CategoryCatalog.of(selectedKey) }.getOrNull()
            ?.let { it.parentId ?: it.id }
            ?: parents.firstOrNull()?.id.orEmpty()
    }
    var activeParent by remember(initialParent) { mutableStateOf(initialParent) }
    val children = remember(activeParent) { CategoryCatalog.childrenOf(activeParent) }
    val activeParentCategory = remember(activeParent) { parents.firstOrNull { it.id == activeParent } }

    PanelScaffold(title = "分类", onDismiss = onDismiss) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(34.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                parents.forEach { parent ->
                    item(key = parent.id) {
                        ParentChip(
                            category = parent,
                            active = parent.id == activeParent,
                            selected = parent.id == selectedKey,
                            onClick = {
                                // 点一级：选中它（可只记到一级）+ 二级宫格切到它
                                activeParent = parent.id
                                onPickParent(parent.id)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (children.isEmpty()) {
                Text(
                    text = activeParentCategory?.let { "「${it.name}」没有子分类，点「完成」就用它记账" }
                        ?: "这一类还没有子分类",
                    style = TextStyle(fontSize = 12.sp, color = OverlayPalette.Hint),
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(children, key = { it.id }) { child ->
                        val isSelected = child.id == selectedKey
                        TileShape(
                            selected = isSelected,
                            height = 62.dp,
                            onClick = { onSelect(child.id) },
                        ) {
                            CategoryIconImage(
                                name = child.name,
                                color = runCatching { Color(child.color) }
                                    .getOrDefault(OverlayPalette.PlaceholderDot),
                                size = 22.dp,
                                categoryId = child.id,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = child.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = TextStyle(
                                    fontSize = 10.5.sp,
                                    color = if (isSelected) OverlayPalette.Accent else OverlayPalette.Secondary,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一级分类 chip：横向条里的一格，选中/展开用主色实心，其余浅底 */
@Composable
private fun ParentChip(
    category: Category,
    active: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(17.dp)
    Row(
        Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (active) OverlayPalette.Accent else OverlayPalette.SurfaceRaised)
            .border(
                1.dp,
                if (active) OverlayPalette.Accent else OverlayPalette.CardBorder,
                shape,
            )
            .pressableScale(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconImage(
            name = category.name,
            color = runCatching { Color(category.color) }
                .getOrDefault(OverlayPalette.PlaceholderDot),
            size = 16.dp,
            categoryId = category.id,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = category.name,
            maxLines = 1,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                color = if (active) OverlayPalette.OnAccent else OverlayPalette.Secondary,
            ),
        )
        // 当前账单真正落的是这个一级分类：加个勾，避免"选中态"和"展开态"混淆
        if (selected) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "✓",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = if (active) OverlayPalette.OnAccent else OverlayPalette.Accent,
                ),
            )
        }
    }
}

/**
 * 日期面板：月历 + 时分（精确到分钟）。
 *
 * 时分不挤占高度：标题行右侧的「时间 / 日历」开关把同一块区域在两者间切换，
 * 滚轮直接复用 Miuix `NumberPicker`（与 app 内的日期时间选择器同一套手感）。
 */
@Composable
private fun DatePanel(
    selected: LocalDate,
    minuteOfDay: Int,
    onSelectDate: (LocalDate) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    var timeMode by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    PanelScaffold(
        title = "日期",
        onDismiss = onDismiss,
        action = {
            Box(
                Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { timeMode = !timeMode }
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (timeMode) "日历" else "时间",
                    style = TextStyle(fontSize = 12.sp, color = OverlayPalette.Accent),
                )
            }
        },
    ) {
        if (timeMode) {
            TimeWheels(minuteOfDay = minuteOfDay, onChange = onMinuteChange)
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "‹",
                        fontSize = 17.sp,
                        color = OverlayPalette.Secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { month = month.minusMonths(1) }
                            .padding(horizontal = 10.dp, vertical = 1.dp),
                    )
                    Text(
                        text = "${month.year} 年 ${month.monthValue} 月",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = OverlayPalette.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "›",
                        fontSize = 17.sp,
                        color = OverlayPalette.Secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { month = month.plusMonths(1) }
                            .padding(horizontal = 10.dp, vertical = 1.dp),
                    )
                }
                Row(Modifier.fillMaxWidth().height(15.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(
                            text = label,
                            fontSize = 9.5.sp,
                            color = OverlayPalette.Tertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val firstDay = month.atDay(1)
                val leading = (firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
                val dayCount = month.lengthOfMonth()
                Column(Modifier.fillMaxWidth()) {
                    repeat(6) { week ->
                        Row(Modifier.fillMaxWidth().height(21.dp)) {
                            repeat(7) { column ->
                                val dayNumber = week * 7 + column - leading + 1
                                Box(
                                    Modifier.weight(1f).fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (dayNumber in 1..dayCount) {
                                        val date = month.atDay(dayNumber)
                                        val isSelected = date == selected
                                        val shape = RoundedCornerShape(7.dp)
                                        Box(
                                            Modifier
                                                .size(20.dp)
                                                .clip(shape)
                                                .background(
                                                    if (isSelected) OverlayPalette.Accent else Color.Transparent,
                                                )
                                                .border(
                                                    width = if (date == today && !isSelected) 1.dp else 0.dp,
                                                    color = OverlayPalette.Accent.copy(alpha = 0.5f),
                                                    shape = shape,
                                                )
                                                .clickable { onSelectDate(date) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "$dayNumber",
                                                fontSize = 11.sp,
                                                color = when {
                                                    isSelected -> OverlayPalette.OnAccent
                                                    date == today -> OverlayPalette.Accent
                                                    else -> OverlayPalette.Label
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 时 / 分滚轮。
 *
 * 外层强制切到浅色 Miuix 主题：这张卡片永远是浅底，
 * 系统深色下若沿用全局（深色）滚轮配色，选中项会是白字 —— 等于看不见。
 */
@Composable
private fun TimeWheels(minuteOfDay: Int, onChange: (Int) -> Unit) {
    val hour = (minuteOfDay.coerceAtLeast(0)) / 60
    val minute = (minuteOfDay.coerceAtLeast(0)) % 60
    val lightTheme = remember { ThemeController(ColorSchemeMode.Light) }
    MiuixTheme(controller = lightTheme) {
        Row(
            Modifier.fillMaxSize().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberPicker(
                value = hour,
                onValueChange = { onChange(it * 60 + minute) },
                modifier = Modifier.width(64.dp),
                range = 0..23,
                label = { "%02d".format(it) },
                visibleItemCount = 3,
                itemHeight = 36.dp,
                textStyle = MiuixTheme.textStyles.title4,
            )
            Text(
                text = ":",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = OverlayPalette.Secondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            NumberPicker(
                value = minute,
                onValueChange = { onChange(hour * 60 + it) },
                modifier = Modifier.width(64.dp),
                range = 0..59,
                label = { "%02d".format(it) },
                visibleItemCount = 3,
                itemHeight = 36.dp,
                textStyle = MiuixTheme.textStyles.title4,
            )
        }
    }
}

/** 标签面板：横向换行的胶囊，不再是一列竖排（可选项多时省掉大量滑动） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPanel(
    options: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PanelScaffold(title = "选择标签", onDismiss = onDismiss) {
        if (options.isEmpty()) {
            Text(
                text = "还没有标签，可先在应用内添加",
                style = TextStyle(fontSize = 12.sp, color = OverlayPalette.Hint),
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@PanelScaffold
        }
        FlowRow(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val checked = selected.contains(option)
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .height(32.dp)
                        .clip(shape)
                        .background(
                            if (checked) OverlayPalette.Accent.copy(alpha = 0.12f)
                            else OverlayPalette.SurfaceRaised,
                        )
                        .border(
                            1.dp,
                            if (checked) OverlayPalette.Accent.copy(alpha = 0.45f)
                            else OverlayPalette.CardBorder,
                            shape,
                        )
                        .pressableScale(onClick = { onToggle(option) })
                        .padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option,
                        fontSize = 12.sp,
                        color = if (checked) OverlayPalette.Accent else OverlayPalette.Secondary,
                    )
                }
            }
        }
    }
}

// ── 数据与常量 ──────────────────────────────────────────────────────────

/** 账户名 + 素材图标 key（`BillDraft.account` 只有名字） */
private data class AccountOption(val name: String, val iconKey: String)

/** 展开区字段；[Amount] / [Note] 是行内编辑，不占用中部面板 */
private enum class OverlayField {
    Amount, Note, Category, Account, TransferTo, Date, Tag,
}

private fun OverlayField.isPanel(): Boolean = when (this) {
    OverlayField.Account, OverlayField.TransferTo,
    OverlayField.Category, OverlayField.Date, OverlayField.Tag,
    -> true

    OverlayField.Amount, OverlayField.Note -> false
}

private fun chipBg(type: TransactionType): Color = when (type) {
    TransactionType.INCOME -> OverlayPalette.IncomeChipBg
    TransactionType.TRANSFER -> OverlayPalette.TransferChipBg
    else -> OverlayPalette.ExpenseChipBg
}

private fun chipInk(type: TransactionType): Color = when (type) {
    TransactionType.INCOME -> OverlayPalette.IncomeChipInk
    TransactionType.TRANSFER -> OverlayPalette.TransferChipInk
    else -> OverlayPalette.ExpenseChipInk
}
