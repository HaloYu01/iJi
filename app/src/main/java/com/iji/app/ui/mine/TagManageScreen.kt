package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iji.app.MainViewModel
import com.iji.app.data.repository.TagStore
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.IjiIconSize
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.util.sharedMainViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField

/** 标签管理页当前打开的操作（同一时刻只开一个） */
private sealed interface TagDialog {
    data object Add : TagDialog
    data class Rename(val tag: String) : TagDialog
    data class Delete(val tag: String) : TagDialog
}

/**
 * 标签管理页。
 *
 * 规则：标签只是 DataStore 里的字符串池，账单把标签名以逗号分隔写进 `transactions.tags`，
 * 是记账时的快照、没有外键。**已被账单使用的标签锁定「编辑 / 删除」**——改名或删除只动
 * 标签池，账单仍显示旧字符串，会留下「池里没有、账单里有」的不一致。
 *
 * 未被使用的标签才允许改名 / 删除，两者都是破坏性操作，弹窗里必须写明结果及影响。
 * 点整行始终可查看该标签下的关联账单。
 */
@Composable
fun TagManageScreen(
    onBack: () -> Unit,
    onOpenBills: (String) -> Unit,
    viewModel: MainViewModel = sharedMainViewModel(),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    val tags by TagStore.observe(context)
        .collectAsStateWithLifecycle(initialValue = TagStore.DEFAULT)

    // null = 笔数还没统计出来。此时按「锁定」处理，避免首帧短暂露出删除按钮被误点。
    var usage by remember { mutableStateOf<Map<String, Int>?>(null) }
    LaunchedEffect(tags) { usage = viewModel.tagUsageCounts() }

    var dialog by remember { mutableStateOf<TagDialog?>(null) }
    var input by remember { mutableStateOf("") }
    val inputFocus = remember { FocusRequester() }

    MineSubPage(title = "标签管理", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SmallTitle(
                text = "全部标签（${tags.size}）",
                insideMargin = PaddingValues(0.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RuleLine("锁定：", "有账单在用就不能改名、删除；点整行可查看关联账单。")
                RuleLine("可修改 / 删除：", "0 笔账单在用时才显示「编辑」「删除」，操作前会弹窗说明结果。")
                RuleLine("原因：", "账单里的标签是记账时写入的，改标签池不会同步改账单，避免两边对不上。")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                if (tags.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("还没有标签", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "点下方「添加标签」建第一个",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                } else {
                    Column {
                        tags.forEachIndexed { index, tag ->
                            TagRow(
                                tag = tag,
                                usage = usage,
                                onOpenBills = { onOpenBills(tag) },
                                onRename = {
                                    input = tag
                                    dialog = TagDialog.Rename(tag)
                                },
                                onDelete = { dialog = TagDialog.Delete(tag) },
                            )
                            if (index < tags.lastIndex) IjiSettingsDivider()
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xxl))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .clickable {
                        input = ""
                        dialog = TagDialog.Add
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+ 添加标签",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MiuixPrimary,
                )
            }
        }
    }

    val current = dialog
    IjiDialog(
        show = current != null,
        onDismissRequest = { dialog = null },
        title = when (current) {
            is TagDialog.Add -> "添加标签"
            is TagDialog.Rename -> "编辑标签"
            is TagDialog.Delete -> "删除标签"
            null -> null
        },
        summary = when (current) {
            is TagDialog.Rename -> "确定把「#${current.tag}」改名吗？"
            is TagDialog.Delete -> "确定删除「#${current.tag}」吗？该标签当前没有账单使用。"
            else -> null
        },
        confirmText = when (current) {
            is TagDialog.Rename -> "保存"
            is TagDialog.Delete -> "删除"
            else -> "添加"
        },
        confirmColor = if (current is TagDialog.Delete) AppleRed else null,
        autoFocusRequester = inputFocus.takeIf {
            current is TagDialog.Add || current is TagDialog.Rename
        },
        onConfirm = {
            when (current) {
                is TagDialog.Add -> {
                    val name = input
                    if (name.isBlank()) {
                        snackbar.show("请输入标签名称")
                    } else {
                        scope.launch {
                            if (!TagStore.add(context, name)) snackbar.show("标签已存在")
                        }
                        dialog = null
                    }
                }
                is TagDialog.Rename -> {
                    val old = current.tag
                    val now = input.trim()
                    when {
                        now.isBlank() -> snackbar.show("请输入标签名称")
                        // 改名 = remove(old) + add(now)：新名已存在会让 remove 先成功、
                        // add 静默失败，旧标签凭空消失，必须先拦
                        now != old && tags.contains(now) -> snackbar.show("已存在同名标签")
                        else -> {
                            scope.launch {
                                if (now != old) {
                                    TagStore.remove(context, old)
                                    TagStore.add(context, now)
                                }
                            }
                            dialog = null
                        }
                    }
                }
                is TagDialog.Delete -> {
                    val name = current.tag
                    dialog = null
                    scope.launch { TagStore.remove(context, name) }
                }
                null -> Unit
            }
        },
    ) {
        when (current) {
            is TagDialog.Add, is TagDialog.Rename -> {
                TextField(
                    value = input,
                    onValueChange = { input = it.take(12) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocus),
                    label = "标签名称",
                )
                if (current is TagDialog.Rename) {
                    Spacer(Modifier.height(10.dp))
                    ImpactHint(
                        result = "标签池里的名称改为新名字，列表立即刷新；以后记一笔只能选新名。",
                        impact = "已记账单上的标签是记账时写入的字符串，不会跟着改；若以后有账单用了新名，再改名时旧账单仍显示旧名。",
                    )
                }
            }
            is TagDialog.Delete -> ImpactHint(
                result = "该标签从标签池移除，不再出现在管理列表和记一笔的标签选择里。",
                impact = "账单不会被删除，已记账单上的标签显示不受影响；日后重新添加同名标签，旧账单会重新归入该标签。",
            )
            null -> Unit
        }
    }
}

/** 标签行：整行查看账单；只有「未被使用」的标签才给编辑 / 删除入口 */
@Composable
private fun TagRow(
    tag: String,
    usage: Map<String, Int>?,
    onOpenBills: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val counting = usage == null
    val count = usage?.get(tag) ?: 0
    val locked = counting || count > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenBills)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagBadge()
        Column(Modifier.weight(1f)) {
            Text(tag, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    counting -> "正在统计账单…"
                    count > 0 -> "$count 笔账单在用 · 编辑与删除已锁定"
                    else -> "暂无账单 · 点击查看"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        if (!locked) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IjiTextAction(text = "编辑", compact = true, onClick = onRename)
                IjiTextAction(
                    text = "删除",
                    compact = true,
                    color = AppleRed,
                    onClick = onDelete,
                )
            }
        }
    }
}

/** 破坏性操作的「结果 / 影响」说明，编辑与删除共用 */
@Composable
private fun ImpactHint(result: String, impact: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("结果：$result", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text("影响：$impact", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

/** 规则块的一行：加粗前缀 + 说明 */
@Composable
private fun RuleLine(label: String, text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
            append(text)
        },
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )
}

/** 行首 `#` 徽章，色调用标签选中色 */
@Composable
private fun TagBadge() {
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(IjiIconSize.BadgeBox)
            .background(IncomeGreen.copy(alpha = 0.14f), RoundedCornerShape(Radius.sm)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "#",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = IncomeGreen,
        )
    }
}
