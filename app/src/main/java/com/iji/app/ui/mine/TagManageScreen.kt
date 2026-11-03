package com.iji.app.ui.mine
import com.iji.app.ui.components.LocalSnackbarController

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.repository.TagStore
import kotlinx.coroutines.launch
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.AppleSeparator
import com.iji.app.ui.theme.IncomeGreen
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/**
 * 标签管理页。
 *
 * 行结构：`#` 徽章 + 标签名 + 使用笔数（跨账本统计）+ 编辑箭头 + 删除 ×。
 * 使用笔数帮用户在删前判断影响面；标签是标签池，删除只从池里摘掉，
 * 已记账单上的标签字符串保留不动。
 */
@Composable
fun TagManageScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    // 标签为 DataStore 持久化（此前是硬编码常量，重启即丢）
    val tags by TagStore.observe(context)
        .collectAsStateWithLifecycle(initialValue = TagStore.DEFAULT)
    // 各标签使用笔数：进页 / 标签增删改后查一次（DAO 只取 tags 一列，轻量）
    var usage by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(tags) { usage = viewModel.tagUsageCounts() }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf("") }
    var newTagName by remember { mutableStateOf("") }

    MineSubPage(title = "标签管理", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            top.yukonga.miuix.kmp.basic.SmallTitle(
                text = "全部标签（${tags.size}）",
                insideMargin = PaddingValues(0.dp),
            )

            // 标签列表
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                if (tags.isEmpty()) {
                    // 空状态：删光后列表卡不再是空白一块，说清下一步去哪
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingTag = tag
                                        newTagName = tag
                                        showEditDialog = true
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TagBadge()
                                Column(Modifier.weight(1f)) {
                                    Text(tag, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        (usage[tag] ?: 0).let { n ->
                                            if (n > 0) "$n 笔账单在用" else "暂无账单使用"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                                Icon(
                                    MiuixIcons.ChevronForward,
                                    contentDescription = "编辑",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(20.dp),
                                )
                                // 删除按钮：48dp 命中区（图标 24dp + 四周留白）
                                Icon(
                                    MiuixIcons.Close,
                                    contentDescription = "删除",
                                    tint = AppleRed,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable {
                                            scope.launch {
                                                TagStore.remove(context, tag)
                                                snackbar.show("已删除：$tag")
                                            }
                                        }
                                        .padding(12.dp),
                                )
                            }
                            if (index < tags.size - 1) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp)
                                        .height(0.5.dp)
                                        .background(AppleSeparator),
                                )
                            }
                        }
                    }
                }
            }

            // 添加标签：主操作按钮卡（与「数据与备份」页的 ActionCard 同款样式）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xxl))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .clickable {
                        newTagName = ""
                        showAddDialog = true
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

    // 添加标签弹窗（Miuix 弹窗：大屏自动居中 + 限宽）
    val addTagFocus = remember { FocusRequester() }
    IjiDialog(
        show = showAddDialog,
        onDismissRequest = { showAddDialog = false },
        title = "添加标签",
        autoFocusRequester = addTagFocus,
        onConfirm = {
            if (newTagName.isNotBlank()) {
                val name = newTagName
                scope.launch {
                    if (TagStore.add(context, name)) {
                        snackbar.show("已添加：$name")
                    } else {
                        snackbar.show("标签已存在")
                    }
                }
                showAddDialog = false
            } else {
                snackbar.show("请输入标签名称")
            }
        },
    ) {
        TextField(
            value = newTagName,
            onValueChange = { newTagName = it.take(12) },
            modifier = Modifier.fillMaxWidth().focusRequester(addTagFocus),
            label = "标签名称",
        )
    }

    // 编辑标签弹窗（Miuix 弹窗：大屏自动居中 + 限宽）
    val editTagFocus = remember { FocusRequester() }
    IjiDialog(
        show = showEditDialog,
        onDismissRequest = { showEditDialog = false },
        title = "编辑标签",
        confirmText = "保存",
        autoFocusRequester = editTagFocus,
        onConfirm = {
            val old = editingTag
            val now = newTagName.trim()
            if (now.isBlank()) {
                snackbar.show("请输入标签名称")
            } else if (now != old && tags.contains(now)) {
                // 重名必须拦截：下面是「删旧 + 加新」两步，若新名已存在，
                // remove 会先删掉旧名、add 再静默失败 —— 旧标签就凭空丢了
                snackbar.show("已存在同名标签")
            } else {
                scope.launch {
                    // 改名 = 删除旧名 + 追加新名，保持 TagStore 接口简单（无需额外 rename 方法）
                    if (now != old) {
                        TagStore.remove(context, old)
                        TagStore.add(context, now)
                    }
                    snackbar.show("已更新：$now")
                }
                showEditDialog = false
            }
        },
    ) {
        TextField(
            value = newTagName,
            onValueChange = { newTagName = it.take(12) },
            modifier = Modifier.fillMaxWidth().focusRequester(editTagFocus),
            label = "标签名称",
        )
    }
}

/** 行首 `#` 徽章：几何与 RowLeadingIcon 一致（28dp 圆角方块），色调用标签选中色（记账面板同款绿） */
@Composable
private fun TagBadge() {
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(28.dp)
            .background(IncomeGreen.copy(alpha = 0.14f), RoundedCornerShape(Radius.sm)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "#",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = IncomeGreen,
        )
    }
}
