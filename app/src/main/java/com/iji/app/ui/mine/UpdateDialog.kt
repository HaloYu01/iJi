package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiDialogActions
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.update.UpdateChecker
import com.iji.app.update.UpdateFlow
import com.iji.app.update.UpdateInstaller
import com.iji.app.update.UpdateState
import java.util.Locale

/**
 * 「检查更新」弹窗：A（启动自动检查）与 B（关于页手动点「检查更新」）共用这一个视图。
 *
 * 状态与下载任务都在 [UpdateFlow]（进程级），不在组合里：
 * - 下载中点「后台下载」只关窗，任务继续，回来还能看到进度；
 * - 安装中也能关（系统安装器可能被取消），关闭时把「安装中」收回「下载完成」，
 *   避免下次打开永远卡住；
 * - 发现新版本**不会自动下载**，必须点「立即下载」。
 *
 * 更新源固定在 [UpdateChecker.DEFAULT_FEED]，界面不提供自定义源；
 * 「自动检查更新 / 更新提醒」两个开关在偏好设置页，不在本弹窗。
 * 弹窗挂在 AppRoot 的 Miuix Scaffold 子树内（IjiDialog 的宿主约束），由 [UpdateFlow.show] 控制显隐。
 */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val show by UpdateFlow.show.collectAsState()
    val state by UpdateFlow.state.collectAsState()
    val appVersion = remember { UpdateFlow.currentVersion(context) }

    IjiDialog(
        show = show,
        onDismissRequest = { UpdateFlow.close() },
        title = titleOf(state),
        summary = summaryOf(state, appVersion),
        // 操作行按状态自定义，这里不渲染默认的「取消/确定」
        showDismiss = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (val s = state) {
                // 检查中只有副标题一句话，正文不再重复
                UpdateState.Idle, UpdateState.Checking -> Unit

                UpdateState.UpToDate -> Text(
                    "已是最新版本（$appVersion）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixPrimary,
                )

                is UpdateState.Failed -> Text(
                    s.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleRed,
                )

                is UpdateState.Available -> ReleaseBody(s.info)

                is UpdateState.Downloading -> {
                    ProgressBar(if (s.total > 0) s.progressOf() else 0f)
                    Spacer(Modifier.height(6.dp))
                    StatusLine(
                        buildString {
                            append("下载中 ")
                            if (s.total > 0) append("${(s.received * 100 / s.total).toInt()}%") else append("…")
                            append("  ")
                            append(formatSize(s.received))
                            if (s.total > 0) append(" / ${formatSize(s.total)}")
                        },
                    )
                }

                is UpdateState.Ready -> Text(
                    "安装包已下载并通过校验，点「安装」交给系统安装器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                is UpdateState.NeedPermission -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "系统还没允许本应用「安装未知应用」，安装器会被直接拦下。" +
                            "开启后回来点「安装」即可，安装包已留在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    IjiTextAction(
                        text = "去系统设置开启",
                        onClick = { UpdateInstaller.openInstallPermission(context) },
                        compact = true,
                    )
                }

                is UpdateState.Installing -> {
                    ProgressBar(0f)
                    Spacer(Modifier.height(8.dp))
                    StatusLine("正在安装应用，请稍候…")
                }
            }
        }

        // ── 操作行：主按钮随状态换文案，次按钮始终留关闭入口 ──
        when (val s = state) {
            UpdateState.Idle, UpdateState.Checking -> ActionRow(
                dismissText = "取消",
                onDismiss = { UpdateFlow.cancelActive(); UpdateFlow.close() },
            )

            UpdateState.UpToDate -> ActionRow(dismissText = "关闭", onDismiss = { UpdateFlow.close() })

            is UpdateState.Failed -> ActionRow(
                dismissText = "关闭",
                onDismiss = { UpdateFlow.close() },
                confirmText = "重试",
                onConfirm = { UpdateFlow.check(context) },
            )

            is UpdateState.Available -> ActionRow(
                dismissText = "稍后",
                onDismiss = { UpdateFlow.close() },
                confirmText = "立即下载",
                onConfirm = { UpdateFlow.download(context, s.info) },
                confirmEnabled = s.info.hasApk,
            )

            is UpdateState.Downloading -> ActionRow(
                dismissText = "后台下载",
                onDismiss = { UpdateFlow.close() },
                // 主按钮原地从「立即下载」切换为「取消下载」，不另起一行；
                // 取消后状态收回 Available，按钮再变回「立即下载」
                confirmText = "取消下载",
                onConfirm = { UpdateFlow.cancelActive() },
            )

            is UpdateState.Ready -> ActionRow(
                dismissText = "稍后",
                onDismiss = { UpdateFlow.close() },
                confirmText = "安装",
                onConfirm = { UpdateFlow.install(context, s.info, s.file) },
            )

            is UpdateState.NeedPermission -> ActionRow(
                dismissText = "稍后",
                onDismiss = { UpdateFlow.close() },
                confirmText = "安装",
                onConfirm = { UpdateFlow.install(context, s.info, s.file) },
            )

            is UpdateState.Installing -> ActionRow(
                dismissText = "关闭",
                onDismiss = { UpdateFlow.close() },
                confirmText = "安装中",
                onConfirm = {},
                confirmEnabled = false,
            )
        }
    }
}

/** A：进入主界面即静默检查一次（每天最多一次，可在偏好设置里关） */
@Composable
fun StartupUpdateCheck() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { UpdateFlow.startupCheck(context) }
}

private fun titleOf(state: UpdateState): String = when (state) {
    UpdateState.Idle, UpdateState.Checking -> "检查更新"
    UpdateState.UpToDate -> "已是最新版本"
    is UpdateState.Failed -> "检查更新失败"
    is UpdateState.Available -> "发现新版本"
    is UpdateState.Downloading -> "正在下载"
    is UpdateState.Ready -> "下载完成"
    is UpdateState.NeedPermission -> "需要安装权限"
    is UpdateState.Installing -> "安装中"
}

private fun summaryOf(state: UpdateState, appVersion: String): String? = when (state) {
    UpdateState.Idle, UpdateState.Checking -> "正在检查更新…"
    UpdateState.UpToDate -> "当前版本 $appVersion"
    is UpdateState.Failed -> null
    // 新版本各状态共用一行副标题：当前版本 → 最新版本（大小跟着放），正文不再重复
    is UpdateState.Available -> versionLine(appVersion, state.info)
    is UpdateState.Downloading -> versionLine(appVersion, state.info)
    is UpdateState.Ready -> versionLine(appVersion, state.info)
    is UpdateState.NeedPermission -> versionLine(appVersion, state.info)
    is UpdateState.Installing -> "正在安装应用，请稍候…"
}

/** 「当前版本 x → 最新版本 y（· 大小）」；接口没给大小时只到版本号 */
private fun versionLine(appVersion: String, info: UpdateChecker.UpdateInfo): String =
    "当前版本 $appVersion → 最新版本 ${info.version}${info.sizeSuffix}"

private fun UpdateState.Downloading.progressOf(): Float =
    if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f

@Composable
private fun StatusLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
}

/** 更新日志正文；版本号与大小已上移到副标题，正文只留日志 */
@Composable
private fun ReleaseBody(info: UpdateChecker.UpdateInfo) {
    if (info.notes.isNotBlank()) {
        Text(
            info.notes.take(NOTES_MAX_CHARS) +
                if (info.notes.length > NOTES_MAX_CHARS) "\n…" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 副标题里的大小后缀（如「 · 12.3 MB」）；接口没给大小时为空串 */
private val UpdateChecker.UpdateInfo.sizeSuffix: String
    get() = sizeBytes?.let { " · ${formatSize(it)}" } ?: ""

/** 细进度条：不引 M3 的 LinearProgressIndicator，避免版本差异带来的 API 变动 */
@Composable
private fun ProgressBar(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MiuixPrimary.copy(alpha = 0.15f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(p)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MiuixPrimary),
        )
    }
}

/** 操作行：有主操作时「次操作 / 主操作」两键，没有主操作时只留一个关闭键 */
@Composable
private fun ActionRow(
    dismissText: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
) {
    if (confirmText != null) {
        IjiDialogActions(
            onConfirm = onConfirm ?: {},
            onDismiss = onDismiss,
            dismissText = dismissText,
            confirmText = confirmText,
            confirmEnabled = confirmEnabled,
            topPadding = 14,
        )
    } else {
        IjiDialogActions(
            onConfirm = onDismiss,
            onDismiss = null,
            confirmText = dismissText,
            topPadding = 14,
        )
    }
}

/** 字节数转可读大小（1 位小数，B/KB/MB）；固定 Locale.US，避免某些区域把小数点写成逗号 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "未知大小"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** 更新日志最多展示这么多字符（远端可能返回整篇日志） */
private const val NOTES_MAX_CHARS = 400
