package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.DeleteBillsOutcome
import com.iji.app.MainViewModel
import com.iji.app.ui.components.DateTimePickerDialog
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.AppleTertiaryBackground
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Months
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** 日期展示格式（与 app 内其它位置的中文日期保持一致） */
private val CN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

/**
 * 「按时间条件删除账单 / 删除全部账单」弹窗。
 *
 * ## 流程
 * 1. 选条件（单日 / 整月 / 自定义区间 / 全部）→ 点「下一步」；
 * 2. 先用**无副作用**的计数接口查命中笔数：
 *    - 命中 0 → 直接提示「该条件下没有账单」，**不弹二次确认**（没有可确认的东西）；
 *    - 命中 N → 弹二次确认，明确写出「将删除 N 笔账单」；
 * 3. 确认后执行删除，结果（成功笔数 / 无命中 / 失败原因）统一走 Snackbar 提示。
 *
 * ## 为什么先查数量再确认
 * 只给日期范围让用户盲确认是有风险的：用户以为删几笔，实际可能删掉几百笔。
 * 把「将删除 N 笔」写进确认文案，是这个操作唯一真正的安全阀。
 *
 * ## 数据一致性
 * 删除与账户余额回滚都由 [MainViewModel] 完成；账单表是 Room Flow，
 * 删完自动推送新列表，首页列表、分类汇总、收支趋势等派生统计随之刷新，本弹窗无需手动刷新。
 *
 * @param show 是否显示（由调用方持有，通常是页面里某个入口 Card 的点击）
 * @param onDismiss 关闭回调（取消、完成、无命中后都会触发）
 */
@Composable
fun DeleteBillsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val snackbar = LocalSnackbarController.current
    val ledger by viewModel.currentLedger.collectAsStateWithLifecycle()

    // 0 = 单日，1 = 整月，2 = 自定义区间，3 = 全部
    var scope by remember { mutableIntStateOf(0) }
    var singleDay by remember { mutableStateOf(LocalDate.now()) }
    var monthDay by remember { mutableStateOf(LocalDate.now()) }
    var rangeStart by remember { mutableStateOf(LocalDate.now().minusDays(30)) }
    var rangeEnd by remember { mutableStateOf(LocalDate.now()) }
    /** 正在选哪个日期：single / month / start / end；null = 未打开日期选择器 */
    var picking by remember { mutableStateOf<String?>(null) }
    /** 进行中：查数量 / 执行删除共用（两个弹窗不会同时显示，无需各记一份） */
    var busy by remember { mutableStateOf(false) }
    /** 已进入二次确认步骤（此时条件弹窗隐藏、确认弹窗显示） */
    var confirming by remember { mutableStateOf(false) }
    /** 待删范围：**null = 当前账本全部账单** */
    var confirmRange by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var confirmCount by remember { mutableIntStateOf(0) }

    // 每次打开重置条件：避免沿用上次残留的日期区间（用户很容易忽略"上次选的还是上个月"）
    LaunchedEffect(show) {
        if (show) {
            scope = 0
            singleDay = LocalDate.now()
            monthDay = LocalDate.now()
            rangeStart = LocalDate.now().minusDays(30)
            rangeEnd = LocalDate.now()
            picking = null
            confirming = false
            busy = false
        }
    }

    /** 当前条件对应的日期区间（闭区间）；「全部账单」模式返回 null */
    fun currentRange(): Pair<LocalDate, LocalDate>? = when (scope) {
        0 -> singleDay to singleDay
        1 -> {
            val ym = YearMonth.of(monthDay.year, monthDay.monthValue)
            ym.atDay(1) to ym.atEndOfMonth()
        }
        2 -> rangeStart to rangeEnd
        else -> null
    }

    // ── 第一步：选择删除条件 ──
    IjiDialog(
        show = show && !confirming,
        onDismissRequest = onDismiss,
        title = "删除账单",
        summary = "按时间条件删除，或清空当前账本。删除后相关账户余额会同步回滚。",
        dismissText = "取消",
        confirmText = if (busy) "查询中…" else "下一步",
        confirmEnabled = !busy,
        onConfirm = {
            val range = currentRange()
            if (scope == 2 && range != null && range.first > range.second) {
                // 区间非法：这是输入错误，不该等到点了删除才报错
                snackbar.show("开始日期不能晚于结束日期", duration = SnackbarDuration.Long)
            } else {
                busy = true
                // null 区间 = 全部账单，VM 侧同义
                viewModel.countBills(range?.first, range?.second) { n ->
                    busy = false
                    if (n <= 0) {
                        snackbar.show("该条件下没有账单", duration = SnackbarDuration.Long)
                        onDismiss()
                    } else {
                        confirmRange = range
                        confirmCount = n
                        confirming = true
                    }
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopeChip(
                    text = "按单日",
                    selected = scope == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { scope = 0 },
                )
                ScopeChip(
                    text = "按整月",
                    selected = scope == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { scope = 1 },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopeChip(
                    text = "自定义区间",
                    selected = scope == 2,
                    modifier = Modifier.weight(1f),
                    onClick = { scope = 2 },
                )
                ScopeChip(
                    text = "全部账单",
                    selected = scope == 3,
                    modifier = Modifier.weight(1f),
                    onClick = { scope = 3 },
                )
            }

            Spacer(Modifier.height(2.dp))

            when (scope) {
                0 -> DateRow("日期", singleDay.format(CN_DATE)) { picking = "single" }
                1 -> DateRow("月份", "${monthDay.year}年${monthDay.monthValue}月") { picking = "month" }
                2 -> {
                    DateRow("开始日期", rangeStart.format(CN_DATE)) { picking = "start" }
                    DateRow("结束日期", rangeEnd.format(CN_DATE)) { picking = "end" }
                }
                else -> Text(
                    "将删除当前账本「$ledger」的全部账单，删除后无法撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleRed,
                )
            }
        }
    }

    // ── 日期选择器（复用记账面板同款滚轮，风格统一）──
    DateTimePickerDialog(
        show = picking != null,
        initial = when (picking) {
            "single" -> singleDay
            "month" -> monthDay
            "start" -> rangeStart
            "end" -> rangeEnd
            else -> LocalDate.now()
        }.atStartOfDay(),
        onDismiss = { picking = null },
        onConfirm = { dateTime ->
            when (picking) {
                "single" -> singleDay = dateTime.toLocalDate()
                "month" -> monthDay = dateTime.toLocalDate()
                "start" -> rangeStart = dateTime.toLocalDate()
                "end" -> rangeEnd = dateTime.toLocalDate()
            }
            picking = null
        },
        title = when (picking) {
            "month" -> "选择月份（选该月任意一天）"
            "start" -> "选择开始日期"
            "end" -> "选择结束日期"
            else -> "选择日期"
        },
    )

    // ── 第二步：二次确认 ──
    IjiDialog(
        show = confirming,
        onDismissRequest = { confirming = false },
        title = "确认删除",
        summary = buildString {
            append("将删除 ")
            append(confirmCount)
            append(" 笔账单")
            val r = confirmRange
            if (r == null) {
                append("（当前账本「$ledger」的全部账单）")
            } else {
                append("（${r.first.format(CN_DATE)} 至 ${r.second.format(CN_DATE)}）")
            }
            append("，并同步回滚相关账户余额。此操作无法撤销。")
        },
        dismissText = "取消",
        confirmText = if (busy) "删除中…" else "确认删除",
        confirmColor = AppleRed,
        confirmEnabled = !busy,
        onConfirm = {
            busy = true
            viewModel.deleteBills(confirmRange?.first, confirmRange?.second) { outcome ->
                busy = false
                confirming = false
                onDismiss()
                snackbar.show(outcome.userMessage(), duration = SnackbarDuration.Long)
            }
        },
    )
}

/** 执行结果 → 给用户看的一句话（成功要说明"余额已同步"，失败要给出可读原因） */
private fun DeleteBillsOutcome.userMessage(): String = when (this) {
    is DeleteBillsOutcome.Success -> "已删除 $count 笔账单，账户余额与统计已同步"
    DeleteBillsOutcome.NoMatch -> "没有找到可删除的账单（可能已被删除）"
    is DeleteBillsOutcome.Failure -> message
}

/** 条件选择胶囊：选中为主色浅底 + 主色文字，未选中为浅灰底 */
@Composable
private fun ScopeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.xl))
            .background(if (selected) MiuixPrimary.copy(alpha = 0.14f) else AppleTertiaryBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (selected) MiuixPrimary else TextSecondary,
        )
    }
}

/** 日期选择行：标签 + 当前值，点击唤起滚轮选择器 */
@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(AppleTertiaryBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MiuixIcons.Months,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MiuixPrimary,
        )
    }
}
