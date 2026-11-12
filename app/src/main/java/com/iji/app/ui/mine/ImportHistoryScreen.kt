package com.iji.app.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iji.app.MainViewModel
import com.iji.app.data.model.ImportBatch
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiSettingValue
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.IjiSettingsRow
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.util.sharedMainViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 导入历史页：一条 = 一个 [ImportBatch]。
 *
 * 点开某条才展开操作：撤销按批次记下的账单 id 删除并回滚余额，重新导入用批次快照重放，
 * 删除记录只删历史行、不动已导入的账单和余额。
 * 老版本留下的记录没有 id / 快照，对应按钮置灰——不假装能恢复一批没记下来的账单。
 */
@Composable
fun ImportHistoryScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = sharedMainViewModel(),
) {
    val batches by viewModel.importHistory.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarController.current
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var pendingUndo by remember { mutableStateOf<ImportBatch?>(null) }
    var pendingDelete by remember { mutableStateOf<ImportBatch?>(null) }

    MineSubPage(title = "导入记录", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ImportSection("全部批次") {
                if (batches.isEmpty()) {
                    IjiSettingsRow("暂无导入记录", subtitle = "完成一次导入后会在这里留下记录")
                } else {
                    batches.forEachIndexed { index, batch ->
                        IjiSettingsRow(
                            title = batch.source.ifBlank { "导入" },
                            subtitle = "${batch.importedAtMillis.batchTime()} · ${batch.count} 笔" +
                                if (batch.undone) " · 已撤销" else "",
                            onClick = {
                                expandedId = if (expandedId == batch.id) null else batch.id
                            },
                            trailing = {
                                IjiSettingValue(if (expandedId == batch.id) "收起" else "展开")
                            },
                        )
                        if (expandedId == batch.id) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                IjiTextAction(
                                    text = "撤销本次导入",
                                    onClick = { pendingUndo = batch },
                                    enabled = batch.canUndo,
                                    color = AppleRed,
                                    compact = true,
                                )
                                IjiTextAction(
                                    text = "重新导入",
                                    onClick = {
                                        viewModel.reimportBatch(batch) { _, msg ->
                                            snackbar.show(msg)
                                        }
                                    },
                                    enabled = batch.canReimport,
                                    compact = true,
                                )
                                IjiTextAction(
                                    text = "删除记录",
                                    onClick = { pendingDelete = batch },
                                    compact = true,
                                    color = AppleRed,
                                )
                            }
                        }
                        if (index < batches.lastIndex) IjiSettingsDivider()
                    }
                }
            }
        }
    }

    IjiDialog(
        show = pendingUndo != null,
        onDismissRequest = { pendingUndo = null },
        title = "撤销本次导入",
        summary = "删除这一批账单并回滚账户余额。账单快照会保留，可随时重新导入。",
        confirmText = "撤销",
        confirmColor = AppleRed,
        onConfirm = {
            pendingUndo?.let { batch ->
                pendingUndo = null
                viewModel.undoImportBatch(batch) { _, msg -> snackbar.show(msg) }
            }
        },
    )

    IjiDialog(
        show = pendingDelete != null,
        onDismissRequest = { pendingDelete = null },
        title = "删除这条导入记录？",
        summary = "只删除这条记录，已导入的账单和账户余额都不会改变。删除后无法再从这里撤销或重新导入这批账单。",
        confirmText = "删除",
        confirmColor = AppleRed,
        onConfirm = {
            pendingDelete?.let { batch ->
                pendingDelete = null
                if (expandedId == batch.id) expandedId = null
                viewModel.deleteImportBatch(batch) { _, msg -> snackbar.show(msg) }
            }
        },
    )
}

private val BATCH_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

private fun Long.batchTime(): String =
    BATCH_TIME_FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault()))
