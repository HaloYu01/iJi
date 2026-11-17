package com.iji.app.ui.mine
import com.iji.app.ui.components.LocalSnackbarController

import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.budget.BudgetCalculator
import com.iji.app.budget.BudgetComponents
import com.iji.app.budget.NotificationHelper
import com.iji.app.data.model.BudgetForecast
import com.iji.app.data.model.PaceStatus
import com.iji.app.ui.budget.BudgetProgressBar
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import kotlin.math.roundToInt
import com.iji.app.ui.util.sharedMainViewModel

/**
 * 预算功能页（设置 → 记账设置 → 预算功能）。
 *
 * 数据来源与首页预算卡片、预算详情页**完全一致**：都用 MainViewModel.monthBudgetProgress
 * （持久化的「全分类 + 自然月」Budget，按预算自身周期统计），
 * 进度条也统一用 [BudgetProgressBar]，不再各画一套。
 */
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    onNavigateCategoryBudget: () -> Unit = {},
    onNavigateAlertHistory: () -> Unit = {},
    app: MainViewModel = sharedMainViewModel(),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    // 与首页共用同一个 Flow，数值不会打架
    val budgetProgress by app.monthBudgetProgress.collectAsStateWithLifecycle()
    val bp = budgetProgress
    val budgetId = bp?.budget?.id ?: 0L

    // 自定义结转金额（元，整数）。null = 尚未从已保存值初始化；只在预算 id 变化时回填一次，
    // 否则写库回流会覆盖正在输入的文本（输「10」刚落库就被回填，后续输入会被打断）。
    var carryOverAmountInput by remember { mutableStateOf<String?>(null) }
    val savedCarryOverCents = bp?.budget?.rolloverCents ?: 0L
    LaunchedEffect(bp?.budget?.id) {
        if (carryOverAmountInput == null && bp != null) {
            carryOverAmountInput = (savedCarryOverCents / 100L).takeIf { it > 0L }?.toString() ?: ""
        }
    }

    // 提醒记录条数（budget_alerts）：给「提醒记录」行做摘要
    val alertCount = remember { BudgetComponents.repository().alerts }
        .collectAsStateWithLifecycle(initialValue = emptyList()).value.size

    var showEditDialog by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf("") }
    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderOn by remember { mutableStateOf(true) }
    var thresholdPercent by remember { mutableStateOf(80) }
    // 输入框原始文本：允许留空 / 未输完，不与 Int 强绑，避免输「7」时立刻被当成 7%
    var thresholdInput by remember { mutableStateOf("80") }

    // Android 13+ 通知权限：开启提醒时才请求
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            snackbar.show("未授予通知权限，将无法收到预算提醒")
        }
    }

    MineSubPage(title = "预算功能", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 本月预算卡片（真实数据 + 统一进度条）
            top.yukonga.miuix.kmp.basic.Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("本月预算", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        formatMoney(bp?.amountCents ?: 0L),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))

                    if (bp != null) {
                        BudgetProgressBar(progress = bp)
                    } else {
                        // 尚未设置预算：占位空条，避免布局跳动
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("已用", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                formatMoney(bp?.spentCents ?: 0L),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val remaining = bp?.remainingCents ?: 0L
                            // 状态**不只靠颜色**：色弱用户和读屏用户看不出红色代表超支，
                            // 所以把「剩余 / 已超支」写进文字，颜色只作为辅助。
                            val overspent = remaining < 0
                            Text(
                                if (overspent) "已超支" else "剩余",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Text(
                                formatMoney(if (overspent) -remaining else remaining),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (overspent) AppleRed else IncomeGreen,
                            )
                        }
                    }

                    // ── 超支预测：把提醒从事后通知变成事前可干预 ──
                    if (bp != null) {
                        val forecast = BudgetCalculator.forecast(bp)
                        if (forecast.isReliable) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                forecastText(forecast),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (forecast.pace == PaceStatus.OVER_PACED) AppleRed else TextSecondary,
                            )
                        }
                        // ── 结转：可自动按上期结余，也可固定按自定义金额 ──
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("结转上期结余", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    // 开了且真有结转时把金额说出来，否则用户不知道这个开关到底生效没有
                                    if (bp.rolloverCents > 0L) {
                                        "已结转 ${formatMoney(bp.rolloverCents)} 到本期预算"
                                    } else {
                                        "未用完的额度滚入本期（超支不累计）"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            Switch(
                                checked = bp.budget.carryOver,
                                onCheckedChange = { on ->
                                    // 预算还没落库（id == 0）时 setCarryOver 找不到行、改了个寂寞，
                                    // 开关会立刻弹回、看起来像"点了没反应"。与「预算提醒」一致：先提示去设预算。
                                    if (budgetId == 0L) {
                                        snackbar.show("请先设置本月预算")
                                        return@Switch
                                    }
                                    scope.launch {
                                        BudgetComponents.repository().setCarryOver(bp.budget.id, on)
                                    }
                                },
                            )
                        }
                        // 自定义结转金额：留空 = 自动按上期结余；填整数元 = 每期固定结转该金额
                        if (bp.budget.carryOver) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("结转金额", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "留空按上期结余自动结转，填数字则固定结转该金额",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                BasicTextField(
                                    value = carryOverAmountInput ?: "",
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(7)
                                        carryOverAmountInput = digits
                                        if (budgetId != 0L) {
                                            scope.launch {
                                                BudgetComponents.repository()
                                                    .setCarryOverAmount(budgetId, (digits.toLongOrNull() ?: 0L) * 100L)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .width(72.dp)
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MiuixPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("元", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // 编辑预算（写入持久化的 Budget，重启不丢）
            SettingsRow(
                title = "编辑预算",
                subtitle = budgetId.takeIf { it > 0L }?.let { "当前 ${formatMoney(bp?.amountCents ?: 0L)}" },
                onClick = {
                    budgetInput = ((bp?.amountCents ?: 0L) / 100).toString()
                    showEditDialog = true
                },
            )

            // 分类预算：按月为每个一级支出分类单独设额度
            SettingsRow(
                title = "分类预算",
                subtitle = "为单个分类设置月度额度",
                onClick = onNavigateCategoryBudget,
            )

            // 提醒记录：budget_alerts 一直在写，但之前没有任何读取方，
            // 用户被提醒过几次、哪一期超的支，App 里查不到 → 补一个出口
            SettingsRow(
                title = "提醒记录",
                subtitle = alertCount.takeIf { it > 0 }?.let { "$it 条" } ?: "暂无记录",
                onClick = onNavigateAlertHistory,
            )

            // 预算提醒：点击可设置开关与阈值
            SettingsRow(
                title = "预算提醒",
                subtitle = when {
                    budgetId == 0L -> "请先设置预算"
                    bp?.budget?.reminderEnabled == true -> "已开启 · 用量达 ${bp.budget.alertThresholdPercent}% 时提醒"
                    else -> "已关闭"
                },
                onClick = {
                    if (budgetId == 0L) {
                        snackbar.show("请先设置本月预算")
                        return@SettingsRow
                    }
                    reminderOn = bp?.budget?.reminderEnabled ?: true
                    thresholdPercent = bp?.budget?.alertThresholdPercent ?: 80
                    thresholdInput = thresholdPercent.toString()
                    showReminderDialog = true
                },
            )
        }
    }

    // 编辑预算弹窗
    IjiDialog(
        show = showEditDialog,
        onDismissRequest = { showEditDialog = false },
        title = "编辑预算",
        confirmText = "保存",
        onConfirm = {
            val yuan = budgetInput.toDoubleOrNull()
            if (yuan != null && yuan > 0) {
                // 写入持久化的 Budget（全分类 + 自然月），首页卡片与提醒随即生效
                app.setMonthlyBudget((yuan * 100).toLong())
                showEditDialog = false
            } else {
                snackbar.show("请输入有效金额")
            }
        },
    ) {
        top.yukonga.miuix.kmp.basic.TextField(
            value = budgetInput,
            onValueChange = { budgetInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = "月度预算（元）",
        )
    }

    // 预算提醒弹窗：开关 + 阈值
    IjiDialog(
        show = showReminderDialog,
        onDismissRequest = { showReminderDialog = false },
        title = "预算提醒",
        confirmText = "保存",
        onConfirm = {
            // 开启提醒但无通知权限时，先申请权限（不影响设置落库）
            if (reminderOn &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationHelper.canPostNotifications(context)
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            scope.launch {
                runCatching {
                    BudgetComponents.repository().setReminder(budgetId, reminderOn, thresholdPercent)
                }.onFailure {
                    snackbar.show("保存失败，请重试")
                }
            }
            showReminderDialog = false
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("启用提醒", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = reminderOn, onCheckedChange = { reminderOn = it })
            }
            Spacer(Modifier.height(12.dp))
            // 阈值区：标题 + 数字输入同一行，滑块、说明各留一点呼吸间距，避免上下贴死
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "提醒阈值",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                BasicTextField(
                    value = thresholdInput,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(3)
                        thresholdInput = digits
                        digits.toIntOrNull()?.let { thresholdPercent = it.coerceIn(1, 100) }
                    },
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = if (reminderOn) MiuixPrimary else TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (reminderOn) MiuixPrimary else TextSecondary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Slider(
                value = thresholdPercent.toFloat(),
                onValueChange = {
                    thresholdPercent = it.roundToInt().coerceIn(1, 100)
                    thresholdInput = thresholdPercent.toString()
                },
                valueRange = 1f..100f,
                enabled = reminderOn,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "用量达到 ${thresholdPercent}% 时提醒「接近超支」；达 100% 时始终提醒「已超支」",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

/** 超支预测文案：只说用户能据此行动的信息 */
private fun forecastText(f: BudgetForecast): String = when (f.pace) {
    PaceStatus.OVER_PACED ->
        "按当前速度（日均 ${formatMoney(f.dailyPaceCents)}），预计周期末花 ${formatMoney(f.projectedCents)}，将超支 ${formatMoney(f.projectedOverspendCents)}"
    PaceStatus.UNDER_PACED ->
        "按当前速度，预计周期末花 ${formatMoney(f.projectedCents)}，预计结余 ${formatMoney(f.projectedRemainingCents)}"
    PaceStatus.ON_PACE ->
        "按当前速度，预计周期末花 ${formatMoney(f.projectedCents)}，与预算基本持平"
}

/** 设置行：标题 + 右侧副标题 + 箭头 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Icon(
                MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
