package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.iji.app.ui.components.ijiTopBarContentPadding
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.Currency
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiPrimaryButtonBlue
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.WheelOptionDialog
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import com.iji.app.ui.util.sharedMainViewModel

/**
 * 账本管理页（设置 → 账本管理）。
 *
 * 此前账本只能「创建 + 切换」，没有改名也没有删除：
 * - 名称是主键，改名若不级联，该账本账单会「凭空消失」
 * - 没有删除入口，误建的账本只能留着
 *
 * 这里补齐三件事，并遵循两条安全底线：
 * 1. 改名走仓库的**单事务级联**（transactions / budgets / recurring_rules 一起改）
 * 2. **只允许删空账本**；有账单时先显示笔数并拒绝删除，避免误操作抹掉整本账
 *    （含账单的账本应走「合并到其他账本」，那是后续批次的能力）
 */
@Composable
fun LedgerManageScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = sharedMainViewModel(),
) {
    val snackbar = LocalSnackbarController.current
    val current by viewModel.currentLedger.collectAsStateWithLifecycle()

    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newCurrency by remember { mutableStateOf(Currency.CNY) }
    var pickingNewCurrency by remember { mutableStateOf(false) }
    // 改币种：目标账本 + 是否正在滚轮里选
    var currencyTarget by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var deleteTxCount by remember { mutableStateOf(-1) }
    // 合并账本：源账本 + 选中的目标账本 + 源账本账单数（确认前先查清楚要搬多少）
    var merging by remember { mutableStateOf<String?>(null) }
    var mergeTarget by remember { mutableStateOf("") }
    var mergeTxCount by remember { mutableStateOf(-1) }
    var pickingMergeTarget by remember { mutableStateOf(false) }

    MineSubPage(title = "账本管理", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                // 顶部让位给浮层顶栏（安全区 + 52dp 折叠态）
                top = ijiTopBarContentPadding(),
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            items(viewModel.ledgers.toList(), key = { it }) { name ->
                // 币种跟着实体走：改完立刻在行里看到新符号
                val currency = Currency.of(viewModel.ledgerInfos.firstOrNull { it.name == name }?.currency)
                Card {
                    Column {
                        // 信息区独占整行：不再和一排按钮挤在同一行里
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (name == current) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("当前", style = MaterialTheme.typography.bodySmall, color = MiuixPrimary)
                                    }
                                }
                                Text(
                                    "${currency.symbol} ${currency.name}（${currency.code}）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        )
                        // 操作条：等宽分段 + 竖向发丝线，取代宽窄随字数变化的胶囊小按钮
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LedgerActionCell(Modifier.weight(1f), "币种") { currencyTarget = name }
                            LedgerActionDivider()
                            LedgerActionCell(Modifier.weight(1f), "改名") {
                                renameInput = name
                                renaming = name
                            }
                            // 合并：有账单的账本删不掉，这里是它唯一的出路
                            if (viewModel.ledgers.size > 1) {
                                LedgerActionDivider()
                                LedgerActionCell(Modifier.weight(1f), "合并") {
                                    mergeTxCount = -1
                                    mergeTarget = ""
                                    merging = name
                                    viewModel.ledgerTransactionCount(name) { mergeTxCount = it }
                                }
                            }
                            LedgerActionDivider()
                            LedgerActionCell(Modifier.weight(1f), "删除", AppleRed) {
                                deleteTxCount = -1
                                deleting = name
                                // 打开确认弹窗时才查笔数：避免每行都跑一次查询
                                viewModel.ledgerTransactionCount(name) { deleteTxCount = it }
                            }
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.xxl))
                        .background(IjiPrimaryButtonBlue)
                        .clickable {
                            newName = ""
                            // 默认沿用当前账本的币种：多数人新建账本并不是为了换币种
                            newCurrency = Currency.of(
                                viewModel.ledgerInfos.firstOrNull { it.name == current }?.currency,
                            )
                            creating = true
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("新建账本", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Medium)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // 新建账本
    IjiDialog(
        show = creating,
        onDismissRequest = { creating = false },
        title = "新建账本",
        confirmText = "创建",
        onConfirm = {
            val n = newName.trim()
            if (n.isBlank()) {
                snackbar.show("请输入账本名称")
            } else if (viewModel.ledgers.contains(n)) {
                snackbar.show("已存在同名账本")
            } else {
                viewModel.addLedger(n, newCurrency.code)
                creating = false
            }
        },
    ) {
        Column {
            TextField(
                value = newName,
                onValueChange = { newName = it.take(20) },
                modifier = Modifier.fillMaxWidth(),
                label = "账本名称",
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { pickingNewCurrency = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    "币种：${newCurrency.symbol} ${newCurrency.name}（${newCurrency.code}）",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                "币种只决定金额显示成什么符号，不做汇率换算；同一账本内的金额始终同币种。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    // 重命名
    val renameTarget = renaming
    IjiDialog(
        show = renameTarget != null,
        onDismissRequest = { renaming = null },
        title = "重命名账本",
        summary = "改名会同步更新该账本下的账单、预算与周期记账",
        confirmText = "保存",
        onConfirm = {
            val target = renaming ?: return@IjiDialog
            val input = renameInput.trim()
            if (input.isBlank()) {
                snackbar.show("请输入账本名称")
                return@IjiDialog
            }
            viewModel.renameLedger(target, input) { ok ->
                if (ok) snackbar.show("已重命名") else snackbar.show("名称为空、重复或与原名相同")
            }
            renaming = null
        },
    ) {
        TextField(
            value = renameInput,
            onValueChange = { renameInput = it.take(20) },
            modifier = Modifier.fillMaxWidth(),
            label = "账本名称",
        )
    }

    // 删除（仅空账本）
    val deleteTarget = deleting
    IjiDialog(
        show = deleteTarget != null,
        onDismissRequest = { deleting = null },
        title = "删除账本",
        confirmText = "删除",
        confirmColor = AppleRed,
        onConfirm = {
            val target = deleting ?: return@IjiDialog
            viewModel.deleteLedger(target) { ok ->
                if (ok) snackbar.show("已删除账本「$target」") else snackbar.show("该账本仍有账单，不能删除")
            }
            deleting = null
        },
    ) {
        Text(
            when {
                deleteTarget == null -> ""
                deleteTxCount < 0 -> "正在读取账单数量…"
                deleteTxCount > 0 ->
                    "「$deleteTarget」下还有 $deleteTxCount 笔账单，删除后这些账单将失去归属。" +
                        "请先处理这些账单，或改用「合并到其他账本」。"
                else -> "确定删除空账本「$deleteTarget」？其预算与周期记账规则会一并删除。"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    // 合并账本：源账本的账单 / 预算 / 周期规则整体并入目标账本
    val mergeSource = merging
    IjiDialog(
        show = mergeSource != null,
        onDismissRequest = { merging = null },
        title = "合并账本",
        confirmText = "合并",
        confirmColor = AppleRed,
        onConfirm = {
            val from = merging ?: return@IjiDialog
            if (mergeTarget.isBlank()) {
                snackbar.show("请先选择要并入的账本")
                return@IjiDialog
            }
            viewModel.mergeLedger(from, mergeTarget) { result ->
                if (result == null) {
                    snackbar.show("合并失败，请重试")
                } else {
                    val dropped = if (result.droppedBudgetCount > 0) {
                        "；${result.droppedBudgetCount} 条预算因目标已有同类配置未搬入"
                    } else {
                        ""
                    }
                    snackbar.show(
                        "已把 ${result.transactionCount} 笔账单并入「$mergeTarget」$dropped",
                    )
                }
            }
            merging = null
        },
    ) {
        Column {
            Text(
                when {
                    mergeSource == null -> ""
                    mergeTxCount < 0 -> "正在读取账单数量…"
                    else -> "把「$mergeSource」下的 $mergeTxCount 笔账单、" +
                        "以及其预算与周期记账规则全部并入目标账本，之后删除「$mergeSource」。"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            // 目标账本选择：点开滚轮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        val others = viewModel.ledgers.filter { it != mergeSource }
                        if (others.isNotEmpty() && mergeTarget.isBlank()) mergeTarget = others.first()
                        pickingMergeTarget = true
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    mergeTarget.ifBlank { "选择要并入的账本" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (mergeTarget.isBlank()) TextSecondary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                "账单会原样保留（金额、日期、分类都不变），只是归属改到目标账本；" +
                    "合并不可撤销。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    WheelOptionDialog(
        show = pickingMergeTarget,
        options = viewModel.ledgers.filter { it != merging }.ifEmpty { listOf("无可用账本") },
        selected = mergeTarget,
        onDismissRequest = { pickingMergeTarget = false },
        onConfirm = { mergeTarget = it; pickingMergeTarget = false },
        title = "并入到",
    )

    // 新建账本时的币种
    WheelOptionDialog(
        show = pickingNewCurrency,
        options = Currency.all,
        selected = newCurrency,
        onDismissRequest = { pickingNewCurrency = false },
        onConfirm = { newCurrency = it; pickingNewCurrency = false },
        title = "账本币种",
        label = { "${it.symbol}  ${it.name}（${it.code}）" },
    )

    // 已有账本改币种：只换展示符号，账单金额不动
    val currencySource = currencyTarget
    WheelOptionDialog(
        show = currencySource != null,
        options = Currency.all,
        selected = Currency.of(viewModel.ledgerInfos.firstOrNull { it.name == currencySource }?.currency),
        onDismissRequest = { currencyTarget = null },
        onConfirm = { picked ->
            val target = currencySource
            if (target != null) {
                viewModel.setLedgerCurrency(target, picked.code)
                // 改的是当前账本才需要提示「金额没变」—— 否则用户会以为做了换算
                if (target == current) snackbar.show("「$target」改用 ${picked.symbol} 显示，已有金额未换算")
            }
            currencyTarget = null
        },
        title = "账本币种",
        label = { "${it.symbol}  ${it.name}（${it.code}）" },
    )
}

/**
 * 操作条上的一个分段按钮：宽度由外层 [Row] 均分（[modifier] 传 `Modifier.weight(1f)`），
 * 文字居中、按语义着色（普通 = 主色，危险 = [AppleRed]），点击区兜到 48dp。
 */
@Composable
private fun LedgerActionCell(
    modifier: Modifier = Modifier,
    text: String,
    color: androidx.compose.ui.graphics.Color = MiuixPrimary,
    onClick: () -> Unit,
) {
    // 同分段控件：不继承全局 LocalIndication（整格矩形遮罩「方框」），
    // 反馈改为按压时轻微压暗文字，避免闪出与卡片圆角冲突的方块。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (pressed) color.copy(alpha = 0.55f) else color,
        )
    }
}

/** 相邻分段之间的竖向发丝线（比按钮矮一截，避免顶到卡片圆角） */
@Composable
private fun LedgerActionDivider() {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column { content() }
    }
}