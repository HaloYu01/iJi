package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.backup.AutoBackupScheduler
import com.iji.app.backup.BackupManager
import com.iji.app.backup.CloudBackupManager
import com.iji.app.backup.WebDavClient
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.ui.components.ChoiceRow
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.WheelOptionDialog
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/**
 * 网盘备份（WebDAV）独立页。
 *
 * 从「数据与备份」页拆出来：连接配置 + 备份目标 + 备份内容 + 上传 + 恢复列表 + 自动备份，
 * 六块内容放在一个面板里滚动体验太差（要滚到底才能看到"从网盘恢复"），所以单开一页。
 *
 * 现在「每日自动备份」只出现在这里，语义也统一了：它做的是
 * **本机加密备份 + 上传网盘**，没有网盘就失去意义，因此未配置时开关禁用。
 */
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    /** 恢复完成后的回调（页面自己就地报结果，这里只用于外层刷新状态） */
    onRestored: () -> Unit = {},
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var configured by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(CloudBackupManager.TYPE_JIANGUO) }
    var url by remember { mutableStateOf("") }
    var dir by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(true) }
    var content by remember { mutableStateOf(CloudBackupManager.Content.FULL) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(false) }

    var remoteFiles by remember { mutableStateOf<List<WebDavClient.RemoteFile>>(emptyList()) }
    var remoteBusy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<WebDavClient.RemoteFile?>(null) }

    var autoOn by remember { mutableStateOf(AutoBackupScheduler.isEnabled(context)) }
    var hour by remember { mutableIntStateOf(AutoBackupScheduler.hour(context)) }
    var showHourPicker by remember { mutableStateOf(false) }

    val userFocus = remember { FocusRequester() }

    fun saveConfig(): Boolean {
        if (url.isBlank() || user.isBlank() || pass.isBlank()) return false
        CloudBackupManager.save(
            context,
            CloudBackupManager.Config(type = type, url = url, user = user, pass = pass, dir = dir),
        )
        configured = true
        return true
    }

    fun loadRemote() {
        remoteBusy = true
        scope.launch {
            CloudBackupManager.listRemote(context)
                .onSuccess {
                    remoteFiles = it
                    if (it.isEmpty()) {
                        messageOk = true
                        message = "当前目录下还没有备份，先点上面的「上传到网盘」。"
                    }
                }
                .onFailure {
                    messageOk = false
                    message = it.message ?: "读取网盘目录失败"
                }
            remoteBusy = false
        }
    }

    /** 网盘操作统一入口：先落配置，再执行；结果就地显示，不弹二次确认 */
    fun run(successText: (String) -> String, block: suspend () -> Result<String>) {
        if (!saveConfig()) {
            messageOk = false
            message = "请先填写服务器地址、账号与应用密码"
            return
        }
        busy = true
        message = null
        scope.launch {
            block()
                .onSuccess {
                    messageOk = true
                    message = successText(it)
                    loadRemote()
                }
                .onFailure {
                    messageOk = false
                    message = it.message ?: "操作失败"
                }
            busy = false
        }
    }

    // 首帧回填已保存配置；已配置就顺手拉一次远端列表，免得用户还要点「刷新」
    LaunchedEffect(Unit) {
        val cfg = withContext(Dispatchers.IO) { CloudBackupManager.load(context) }
        type = cfg.type
        url = cfg.url
        dir = cfg.dir
        user = cfg.user
        pass = cfg.pass
        configured = cfg.complete
        editing = !cfg.complete
        if (cfg.complete) loadRemote()
    }

    MineSubPage(title = "网盘备份", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                top = ijiTopBarContentPadding(),
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // ── 状态 ──
            item {
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (configured) "已连接网盘" else "未配置网盘",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (configured) MaterialTheme.colorScheme.onSurface else AppleRed,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (configured) {
                                    CloudBackupManager.Config(type, url, user, pass, dir).displayTarget
                                } else {
                                    "填好地址与账号后即可上传加密备份"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (configured && !editing) {
                            IjiTextAction(
                                text = "修改",
                                onClick = { editing = true },
                                color = MiuixPrimary,
                                compact = true,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // ── 连接配置（已配置时收起） ──
            if (editing || !configured) {
                item {
                    PanelCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(CloudBackupManager.TYPE_JIANGUO, CloudBackupManager.TYPE_GENERIC).forEach { t ->
                                ChoiceChip(text = t, selected = type == t) {
                                    type = t
                                    if (t == CloudBackupManager.TYPE_JIANGUO && url.isBlank()) {
                                        url = CloudBackupManager.JIANGUO_URL
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        top.yukonga.miuix.kmp.basic.TextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "服务器地址（WebDAV 目录）",
                        )
                        Spacer(Modifier.height(10.dp))
                        top.yukonga.miuix.kmp.basic.TextField(
                            value = dir,
                            onValueChange = { dir = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "备份子目录（可留空，需已存在）",
                        )
                        Spacer(Modifier.height(10.dp))
                        top.yukonga.miuix.kmp.basic.TextField(
                            value = user,
                            onValueChange = { user = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(userFocus),
                            label = "账号 / 邮箱",
                        )
                        Spacer(Modifier.height(10.dp))
                        top.yukonga.miuix.kmp.basic.TextField(
                            value = pass,
                            onValueChange = { pass = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "应用密码（不是登录密码）",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("保存配置", enabled = !busy) {
                                val ok = saveConfig()
                                messageOk = ok
                                message = if (ok) "配置已保存" else "请填写地址、账号与应用密码"
                                if (ok) {
                                    editing = false
                                    loadRemote()
                                }
                            }
                            PillButton("测试连接", enabled = !busy) {
                                run({ "连接成功：$it" }) { CloudBackupManager.testConnection(context) }
                            }
                            if (configured) {
                                PillButton("取消", enabled = !busy) { editing = false }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "坚果云：地址填 ${CloudBackupManager.JIANGUO_URL}，密码在「账户信息 → 安全选项」里生成应用密码。" +
                                "备份先在本机 AES 加密再上传，网盘上只有密文。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            // ── 备份内容 + 上传 ──
            item {
                SectionLabel("备份内容")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CloudBackupManager.Content.entries.forEach { option ->
                        ChoiceRow(
                            selected = content == option,
                            title = option.label,
                            subtitle = if (option == CloudBackupManager.Content.FULL) {
                                "含全部流水，体积随账单增长"
                            } else {
                                "不含流水，只搬分类/账户/预算等设置"
                            },
                        ) { content = option }
                    }
                }
            }
            item {
                PrimaryButton(
                    text = when {
                        busy -> "处理中…"
                        content == CloudBackupManager.Content.FULL -> "上传全量备份"
                        else -> "上传仅配置备份"
                    },
                    enabled = !busy,
                ) { run({ "已上传：$it" }) { CloudBackupManager.uploadNow(context, content) } }
            }

            // ── 结果反馈（就地显示，成功绿 / 失败红） ──
            message?.let { text ->
                item {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (messageOk) IncomeGreen else AppleRed,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // ── 从网盘恢复 ──
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("从网盘恢复", modifier = Modifier.weight(1f))
                    IjiTextAction(
                        text = if (remoteBusy) "读取中…" else "刷新",
                        onClick = { if (!remoteBusy && configured) loadRemote() },
                        color = if (configured) MiuixPrimary else TextSecondary,
                        compact = true,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (remoteBusy && remoteFiles.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在读取网盘目录…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            } else if (remoteFiles.isEmpty()) {
                item {
                    Text(
                        if (!configured) "配置好网盘后，这里会列出可恢复的备份。" else "当前目录下还没有备份文件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(remoteFiles.size) { index ->
                    val file = remoteFiles[index]
                    PanelCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (!busy) pendingRestore = file }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    buildString {
                                        append("%.1f KB".format(file.sizeBytes / 1024f))
                                        if (file.lastModified.isNotBlank()) append(" · ${file.lastModified}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                )
                            }
                            Text("恢复", color = MiuixPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // ── 自动备份（只服务网盘） ──
            item { Spacer(Modifier.height(4.dp)) }
            item {
                SectionLabel("自动备份")
            }
            item {
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("每天自动上传", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    !configured -> "先配置网盘，开启后才会生效"
                                    autoOn -> "每天 ${hour.toString().padStart(2, '0')}:00 上传（本机同时留一份）"
                                    else -> "关闭后只能手动上传"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Switch(
                            checked = autoOn && configured,
                            enabled = configured,
                            onCheckedChange = { on ->
                                autoOn = on
                                SettingsRepository.setBoolean(context, AutoBackupScheduler.KEY_ENABLED, on)
                                AutoBackupScheduler.sync(context)
                            },
                        )
                    }
                    if (configured) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.lg))
                                .clickable { showHourPicker = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "备份时间 ${hour.toString().padStart(2, '0')}:00",
                                style = MaterialTheme.typography.bodySmall,
                                color = MiuixPrimary,
                            )
                            Icon(
                                MiuixIcons.ChevronForward,
                                contentDescription = null,
                                tint = MiuixPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.lg))
                                .clickable(enabled = !busy) {
                                    scope.launch {
                                        busy = true
                                        val local = BackupManager.writeAutoBackup(context)
                                        val remote = CloudBackupManager.uploadNow(context, content)
                                        busy = false
                                        messageOk = local && remote.isSuccess
                                        message = when {
                                            !local -> "本机备份失败：请检查存储空间"
                                            else -> remote.fold(
                                                onSuccess = { "已备份：本机一份 + 网盘 $it" },
                                                onFailure = { "本机已备份，但上传失败：${it.message}" },
                                            )
                                        }
                                        if (remote.isSuccess) loadRemote()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "立即备份一次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MiuixPrimary,
                            )
                        }
                    }
                }
            }

            // ── 清除配置 ──
            if (configured) {
                item {
                    Text(
                        "清除网盘配置",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .clickable {
                                CloudBackupManager.clear(context)
                                configured = false
                                editing = true
                                url = ""
                                dir = ""
                                user = ""
                                pass = ""
                                remoteFiles = emptyList()
                                autoOn = false
                                SettingsRepository.setBoolean(context, AutoBackupScheduler.KEY_ENABLED, false)
                                AutoBackupScheduler.sync(context)
                                messageOk = true
                                message = "已清除配置，自动备份已关闭"
                            }
                            .padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppleRed,
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // 恢复确认
    IjiDialog(
        show = pendingRestore != null,
        onDismissRequest = { pendingRestore = null },
        title = "从网盘恢复",
        summary = "会下载并解密这份备份，然后合并还原到本机（配置同名跳过，流水追加）。",
        confirmText = "恢复",
        confirmEnabled = !busy,
        onConfirm = {
            val file = pendingRestore ?: return@IjiDialog
            pendingRestore = null
            busy = true
            scope.launch {
                CloudBackupManager.downloadSnapshot(context, file)
                    .onSuccess { snapshot ->
                        val summary = runCatching {
                            // 配置合并还原 + 流水追加，和本机备份恢复共用同一套逻辑
                            BackupManager.restoreSnapshot(context, snapshot) { arrayText ->
                                var ok = 0
                                var bad = 0
                                viewModel.importBackupJson(arrayText) { o, b ->
                                    ok = o
                                    bad = b
                                }
                                ok to bad
                            }
                        }.getOrFailMessage()
                        messageOk = true
                        message = "已从网盘恢复：$summary"
                        onRestored()
                    }
                    .onFailure {
                        messageOk = false
                        message = it.message ?: "恢复失败"
                    }
                busy = false
            }
        },
    ) {
        Text(
            "文件：${pendingRestore?.name.orEmpty()}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    // 时间选择
    WheelOptionDialog(
        show = showHourPicker,
        options = (0..23).toList(),
        selected = hour,
        title = "每天几点自动备份",
        label = { "${it.toString().padStart(2, '0')}:00" },
        onDismissRequest = { showHourPicker = false },
        onConfirm = {
            hour = it
            showHourPicker = false
            SettingsRepository.setInt(context, AutoBackupScheduler.KEY_HOUR, it)
            if (autoOn) AutoBackupScheduler.schedule(context)
        },
    )
}

// ── 小组件 ──

@Composable
private fun PanelCard(content: @Composable () -> Unit) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { content() }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        fontWeight = FontWeight.Medium,
    )
}

/** 类型胶囊（坚果云 / 通用）：选中用主色填充 + 加粗，未选中用次要底 */
@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(
                if (selected) MiuixPrimary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MiuixPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PillButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.10f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else TextSecondary,
        )
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(Radius.xl))
            .background(
                if (enabled) MiuixPrimary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MiuixPrimary else TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun Result<String>.getOrFailMessage(): String =
    getOrElse { "恢复失败：${it.message ?: "文件内容异常"}" }
