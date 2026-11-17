package com.iji.app.ui.mine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.autorecord.AutoDiag
import com.iji.app.autorecord.AutoListenPrefs
import com.iji.app.autorecord.AutoPlatforms
import com.iji.app.autorecord.AutoRecordStore
import com.iji.app.ui.components.WheelOptionDialog
import com.iji.app.ui.overlay.BillOverlay
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

/**
 * 全自动记账的界面构件——**与半自动完全分离，不复用半自动的卡片/设置行**。
 *
 * 两块，自上而下回答两个问题：
 * 1. [AutoRecordPanel] 在不在干活、监听谁？（状态 + 8 个平台 chip + 需要时才出现的修复按钮，一张卡）
 * 2. [AutoRecordRecentCard] 干过什么？（最近识别的几笔，含金额与账户；没有记录就整块不显示）
 *
 * 全自动不截屏、不需要 Shizuku，所以这里没有任何截屏来源/磁贴/音量键的概念。
 */

/**
 * 一张卡回答全部：在不在跑、监听谁、下一步点哪。
 *
 * 按设计稿把「状态 hero」和「平台 chip 卡」合成一张：
 * 大数字 + 状态胶囊一行，8 个 chip 直接跟下面，底部常驻一个全宽按钮。
 * 按钮是「开关」语义：未授权时开启 / 恢复，运行中是关闭——但后者用次要样式，
 * 避免主色按钮在全自动跑着的时候诱使用户误点断线。
 */
@Composable
fun AutoRecordPanel(
    running: Boolean,
    statusLabel: String,
    platformCount: Int,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    action: Pair<String, () -> Unit>?,
    /** false = 次要样式（浅底 + 主色文字），用于「关闭无障碍」这类破坏性动作 */
    actionPrimary: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = platformCount.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (running) MiuixPrimary else TextSecondary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    // 没跑起来时不说「正在监听」：选了平台 ≠ 真的在监听
                    text = if (running) "个平台监听中" else "个平台已选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(text = statusLabel, active = running)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoPlatforms.all.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { platform ->
                            PlatformChip(
                                name = platform.name,
                                selected = platform.packageName in selected,
                                modifier = Modifier.weight(1f),
                                onClick = { onToggle(platform.packageName) },
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (action != null) {
                ChipButton(text = action.first, onClick = action.second, primary = actionPrimary)
            }
            OverlayPermissionEntry()
            DiagLogEntry()
        }
    }
}

/** 识别后没弹窗时的自助入口：跳系统悬浮窗设置（canDrawOverlays 在云闪付下会误报，不做硬判断）。 */
@Composable
private fun OverlayPermissionEntry() {
    val context = LocalContext.current
    Text(
        text = "识别后没弹窗？检查悬浮窗权限",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { BillOverlay.openPermissionSettings(context) }
            .padding(vertical = 4.dp),
    )
}

/** 一键复制诊断日志：不用 adb 也能拿到 miss / deliver / overlay 的失败原因。 */
@Composable
private fun DiagLogEntry() {
    val context = LocalContext.current
    Text(
        text = "识别异常？复制诊断日志",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                val text = AutoDiag.readTail(context)
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("auto-bill-diag", text))
                Toast.makeText(context, "诊断日志已复制", Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 4.dp),
    )
}



/**
 * 最近识别：只证明「它真的在跑」，所以没有记录时**整块都不出现**。
 *
 * 原来空态写了两行操作说明，正常使用时永远用不到；现在把位置留给真正的账单。
 */
@Composable
fun AutoRecordRecentCard(entries: List<AutoRecordStore.Entry>) {
    if (entries.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = "最近",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            entries.take(3).forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = recentTime(entry.at),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.width(58.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = entry.platform,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            // 账户一起显示：用户能立刻看出「这笔记到了哪个账户」，
                            // 匹配不上时这里会是空的，比默默记错更早被发现
                            text = if (entry.account.isBlank()) {
                                "${entry.status} · 账户待选"
                            } else {
                                "${entry.status} · ${entry.account}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Text(
                        text = (if (entry.income) "+" else "-") + AutoRecordStore.money(entry.amountCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (entry.income) MiuixPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * 账户待指定：**只在确实有账户没匹配上时**才出现的一行操作。
 *
 * 之前它是常驻卡，带一段「自动认哪些词」的说明——正常记账时永远用不到，纯占地方。
 * 现在无未匹配就完全不渲染；用户指定一次即落成 110 级映射，下次同一个词直接命中。
 */
@Composable
fun AutoAssetMappingCard(
    unmatched: String,
    accounts: List<String>,
    onMap: (String) -> Unit,
) {
    if (unmatched.isNotBlank()) {
        var picking by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = Radius.xxl,
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "账户待指定",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (accounts.isEmpty()) {
                            "「$unmatched」没匹配上，先去资产页建账户"
                        } else {
                            "「$unmatched」没匹配上，指定一次以后自动认"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MiuixPrimary.copy(alpha = 0.12f))
                        .clickable(enabled = accounts.isNotEmpty()) { picking = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "指定",
                        style = MaterialTheme.typography.labelLarge,
                        color = MiuixPrimary,
                    )
                }
            }
        }
        WheelOptionDialog(
            show = picking,
            options = accounts,
            selected = accounts.firstOrNull().orEmpty(),
            title = "记到哪个账户",
            onDismissRequest = { picking = false },
            onConfirm = {
                picking = false
                onMap(it)
            },
        )
    }
}

/**
 * 后台保护：把「为什么会被系统清理」翻译成三件可执行的事。
 *
 * 三项里只有前两项能自动检测（省电策略、通知权限），后台锁定系统不提供查询，
 * 只能写操作路径。不把它们塞进入口流程，是因为全自动不配置也能用——
 * 这一卡是「想更稳」的加分项，不是使用前提。
 */
@Composable
fun AutoBackgroundProtectionCard(
    batteryUnrestricted: Boolean,
    notificationsEnabled: Boolean,
    onOpenBattery: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "后台保护",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (batteryUnrestricted && notificationsEnabled) {
                    "已就绪：清理后台后自动记账仍会继续运行"
                } else {
                    "国产系统的「清理后台」会强制停止应用，顺手把无障碍也关掉"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            ProtectionsRow(
                title = "省电策略",
                value = if (batteryUnrestricted) "无限制" else "受限制",
                fixable = !batteryUnrestricted,
                action = "去设置",
                onAction = onOpenBattery,
            )
            Spacer(Modifier.height(10.dp))
            ProtectionsRow(
                title = "通知权限",
                value = if (notificationsEnabled) "已允许" else "未允许",
                fixable = !notificationsEnabled,
                action = "去开启",
                onAction = onOpenNotifications,
            )
            Spacer(Modifier.height(10.dp))
            ProtectionsRow(
                title = "后台锁定",
                value = "需在最近任务里长按卡片加锁",
                fixable = false,
                action = null,
                onAction = null,
            )
        }
    }
}

@Composable
private fun ProtectionsRow(
    title: String,
    value: String,
    fixable: Boolean,
    action: String?,
    onAction: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = if (fixable) MiuixPrimary else TextSecondary,
                lineHeight = 16.sp,
            )
        }
        if (action != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (fixable) MiuixPrimary.copy(alpha = 0.12f) else TextSecondary.copy(alpha = 0.08f),
                    )
                    .clickable(onClick = onAction)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (fixable) MiuixPrimary else TextSecondary,
                )
            }
        } else {
            Text(
                text = "手动",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
    }
}

/** 状态胶囊：运行中填充主色，未运行走中性灰，避免「暂停」比「运行」还显眼 */
@Composable
private fun StatusPill(text: String, active: Boolean) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (active) MiuixPrimary.copy(alpha = 0.12f) else TextSecondary.copy(alpha = 0.10f),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(active = active)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MiuixPrimary else TextSecondary,
        )
    }
}

@Composable
private fun Dot(active: Boolean) {
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (active) MiuixPrimary else TextSecondary.copy(alpha = 0.4f)),
    )
}

/** 平台 chip：选中态用主色底 + 勾，未选中只有描边色块的 10% 底 */
@Composable
private fun PlatformChip(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 实心填充是唯一的状态表达：亮 = 监听，灰 = 不监听。
    // 原来每个 chip 带「已监听 / 未监听」小字，8 个 chip 就是 8 行重复信息。
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MiuixPrimary else TextSecondary.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * 全自动自己的按钮，不与半自动的 IjiPrimaryButton 混用。
 *
 * primary=false 走「浅底 + 主色文字」：同一个位置承担开启与关闭两种语义，
 * 关闭不需要抢眼，但必须可发现，所以保留尺寸只降权重。
 */
@Composable
private fun ChipButton(text: String, onClick: () -> Unit, primary: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) MiuixPrimary else MiuixPrimary.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MiuixPrimary,
        )
    }
}

/** 最近记录的时间：今天只给时分，跨天补月日 */
private fun recentTime(at: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = at }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(
        if (sameDay) "HH:mm" else "MM-dd HH:mm",
        Locale.getDefault(),
    ).format(Date(at))
}
