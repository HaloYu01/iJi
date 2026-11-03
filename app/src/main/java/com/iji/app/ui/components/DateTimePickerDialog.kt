package com.iji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iji.app.ui.theme.Radius
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDateTime
import java.time.YearMonth

// ───────────────────────── 滚轮尺寸（五个滚轮共用一套常量，改一处即整体同步）─────────────────────────
// 排版：五个滚轮排在**同一行** —— 左边一组「年 月 日」，右边一组「时 分」，组内 2dp、组间 14dp。
// 需求背景：原先三列用 `weight` 平分弹窗整宽，文字居中后列与列之间空出一大截（很散）；
// 行高又跟随库默认（45dp），显得松散。现在列宽只比文字略宽，整行居中，宽屏不散、窄屏不溢出。
/** 每项行高：40dp（库默认 45dp）。 */
private val PickerItemHeight = 40.dp
/** 组内列间距（年↔月、月↔日、时↔分）：2dp，仅作最小分隔。 */
private val PickerColumnGap = 2.dp
/**
 * 日期组与时间组之间的间距：14dp，明显大于组内列间距，
 * 让「年 月 日 | 时 分」在视觉上分成两组、又同属一行。
 */
private val DateToTimeGap = 14.dp
// 列宽 = 该列文字宽 + 两侧各约 9~10dp 余量，所以相邻列的字距在任何位置都约 21dp（视觉整齐）。
/** 「年」列宽（4 位年份，24sp 加粗下约 52dp）。 */
private val YearColumnWidth = 72.dp
/** 「月 / 日」列宽（如「10月」「15日」约 45dp）。 */
private val MonthDayColumnWidth = 64.dp
/** 「时 / 分」列宽（如「06」「28」约 25dp）。 */
private val HourMinuteColumnWidth = 44.dp
/** 整行所需宽度（含全部列间距）；窄屏时按 `可用宽度 / 此值` 等比缩小，保证不溢出。 */
private val PickerRowWidth = YearColumnWidth + MonthDayColumnWidth * 2 +
    HourMinuteColumnWidth * 2 + PickerColumnGap * 4 + DateToTimeGap

/**
 * Miuix 风格的「日期 + 时间」选择弹窗。
 *
 * **背景**：Miuix 未提供日期 / 时间选择器；此前用 Material3 `DatePickerDialog`，
 * 既只能选日期（无法改时分）又与整体 Miuix 风格不一致。这里基于 Miuix [NumberPicker]（滚轮）
 * 组合出年 / 月 / 日 / 时 / 分五项，颜色、圆角、字体、滚轮动效均来自 Miuix，风格统一。
 *
 * **字体**：显式使用 `MiuixTheme.textStyles.title2`（24sp）。`NumberPicker` 的默认样式是
 * `title1`（32sp），单看尚可、五个滚轮并排时明显偏大且与 app 内其他选择器
 * （[WheelOptionDialog]、原时段选择器）不一致，故统一为 `title2`。
 * 选中项（居中那行）由 Miuix 自动加粗并用主文字色，上下未选中项自动变浅变灰——不要覆盖。
 *
 * **排版**：五个滚轮排在**同一行** —— 左「年 月 日」、右「时 分」，中间用 [DateToTimeGap]
 * （14dp，远大于组内 [PickerColumnGap] 的 2dp）分组。列宽用固定值
 * （[YearColumnWidth] / [MonthDayColumnWidth] / [HourMinuteColumnWidth]）而不是 `weight`，
 * 整行居中：宽屏不散开，窄屏按 [PickerRowWidth] 等比缩小不溢出。
 * 要整体放大 / 收紧，只改这几个常量 + [PickerItemHeight] 即可。
 *
 * **截止感**：`NumberPicker` 的 `wrapAround` 保持库默认 `false`，滚到第一项 / 最后一项即停，
 * 不会无限循环（这与 app 内普通选项弹窗 [WheelOptionDialog] 的约定一致）。
 *
 * **上限**：日期锁定在**今天**为止，不允许选择未来 ——
 * 年份 ≤ 今年；选中今年时月份 ≤ 本月；选中本月时日期 ≤ 今天；
 * 选中今天时小时 ≤ 当前小时、当前小时内的分钟 ≤ 当前分钟（即最终结果永不晚于此刻）。
 * 若只想限制到「日期」而放开当天时间，去掉 [maxHour] / [maxMinute] 两处约束即可。
 *
 * **基础用法**
 * ```
 * DateTimePickerDialog(
 *     show = showPicker,
 *     initial = dateTime,
 *     onDismiss = { showPicker = false },
 *     onConfirm = { dateTime = it },   // 回调后组件会自动关闭
 * )
 * ```
 *
 * @param show 是否显示。
 * @param initial 打开时的初始日期时间（每次 [show] 变为 true 时按它重置，并自动收敛到合法范围）。
 * @param onDismiss 关闭回调（点遮罩 / 取消 / 确定后都会触发）。
 * @param onConfirm 确定回调，回传用户选择的 [LocalDateTime]（保证不晚于当前时刻）。
 * @param title 弹窗标题。
 * @param yearRange 可选年份范围（其上限会被收敛到今年）。
 */
@Composable
fun DateTimePickerDialog(
    show: Boolean,
    initial: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
    title: String = "选择日期时间",
    yearRange: IntRange = 2000..2100,
    /** 是否显示「今天 / 昨天 / 前天」快捷项（补记场景；点一下直接选定并关闭） */
    quickDays: Boolean = false,
) {
    val now = remember { LocalDateTime.now() }
    val today = now.toLocalDate()

    // 年份上限：不晚于今年（同时保证区间非空）
    val maxYear = minOf(yearRange.last, today.year).coerceAtLeast(yearRange.first)
    val years = yearRange.first..maxYear

    var year by remember { mutableIntStateOf(initial.year.coerceIn(years)) }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    var day by remember { mutableIntStateOf(initial.dayOfMonth) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }

    // ── 上限推导（逐级收敛，保证不产生未来时间）──
    val isCurrentYear = year >= today.year
    val isCurrentMonth = year == today.year && month == today.monthValue
    val isToday = isCurrentMonth && day == today.dayOfMonth

    val monthRange = if (isCurrentYear) 1..today.monthValue else 1..12

    val daysInMonth = remember(year, month) { YearMonth.of(year, month).lengthOfMonth() }
    val maxDay = if (isCurrentMonth) minOf(daysInMonth, today.dayOfMonth) else daysInMonth
    val maxHour = if (isToday) now.hour else 23
    val maxMinute = if (isToday && hour >= now.hour) now.minute else 59

    // 每次打开按 initial 重置（就近收敛到合法范围）
    LaunchedEffect(show) {
        if (show) {
            val safeYear = initial.year.coerceIn(years)
            val safeMonth = initial.monthValue.coerceAtMost(if (safeYear == today.year) today.monthValue else 12)
            val lastDay = YearMonth.of(safeYear, safeMonth).lengthOfMonth()
            val safeMaxDay = if (safeYear == today.year && safeMonth == today.monthValue) {
                minOf(lastDay, today.dayOfMonth)
            } else {
                lastDay
            }
            val safeDay = initial.dayOfMonth.coerceAtMost(safeMaxDay)
            val safeIsToday = safeYear == today.year && safeMonth == today.monthValue && safeDay == today.dayOfMonth
            val safeMaxHour = if (safeIsToday) now.hour else 23
            val safeHour = initial.hour.coerceAtMost(safeMaxHour)
            val safeMaxMinute = if (safeIsToday && safeHour >= now.hour) now.minute else 59

            year = safeYear
            month = safeMonth
            day = safeDay
            hour = safeHour
            minute = initial.minute.coerceAtMost(safeMaxMinute)
        }
    }

    // 滚轮联动收敛：改了年/月/日/时之后，下级自动不越界
    LaunchedEffect(monthRange) { if (month > monthRange.last) month = monthRange.last }
    LaunchedEffect(maxDay) { if (day > maxDay) day = maxDay }
    LaunchedEffect(maxHour) { if (hour > maxHour) hour = maxHour }
    LaunchedEffect(maxMinute) { if (minute > maxMinute) minute = maxMinute }

    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = title,
        onConfirm = {
            // 最终再收敛一次，确保绝不产生未来时间
            onConfirm(
                LocalDateTime.of(
                    year,
                    month.coerceIn(monthRange),
                    day.coerceAtMost(maxDay),
                    hour.coerceAtMost(maxHour),
                    minute.coerceAtMost(maxMinute),
                )
            )
            onDismiss()
        },
    ) {
        // 快捷补记：今天 / 昨天 / 前天，点一下即选定并关闭（时分沿用当前值）
        if (quickDays) {
            fun jumpTo(offset: Long) {
                val target = today.minusDays(offset)
                year = target.year
                month = target.monthValue
                day = target.dayOfMonth
                val isToday = offset == 0L
                hour = hour.coerceAtMost(if (isToday) now.hour else 23)
                minute = minute.coerceAtMost(if (isToday && hour >= now.hour) now.minute else 59)
                onConfirm(LocalDateTime.of(year, month, day.coerceAtMost(maxDay), hour, minute))
                onDismiss()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("今天" to 0L, "昨天" to 1L, "前天" to 2L).forEach { (label, offset) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { jumpTo(offset) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        // 年 / 月 / 日 / 时 / 分 排在同一行：左边「日期」组，右边「时间」组
        // 注意：刻意**不用 `weight` 平分整宽** —— 那样每列都占满等分宽度，
        // 文字居中后字与字之间会空出一大截（视觉上很散）。这里用固定列宽 + 整行居中，
        // 列宽只比文字略宽一点，五列自然聚拢；两组之间留 [DateToTimeGap] 分开。
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 宽屏（可用宽度够放整行）时 1:1 用固定列宽 → 紧凑不散开；
            // 窄屏按比例整体缩小 → 绝不溢出弹窗（文字本身比列宽窄不少，缩一点也放得下）。
            val fit = (maxWidth / PickerRowWidth).coerceAtMost(1f)
            fun col(base: Dp) = base * fit

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberPicker(
                    value = year,
                    onValueChange = { year = it },
                    modifier = Modifier.width(col(YearColumnWidth)),
                    range = years,
                    label = { "$it" },
                    visibleItemCount = 3,
                    itemHeight = PickerItemHeight,
                    textStyle = MiuixTheme.textStyles.title2,
                )
                Spacer(Modifier.width(col(PickerColumnGap)))
                NumberPicker(
                    value = month,
                    onValueChange = { month = it },
                    modifier = Modifier.width(col(MonthDayColumnWidth)),
                    range = monthRange,
                    label = { "${it}月" },
                    visibleItemCount = 3,
                    itemHeight = PickerItemHeight,
                    textStyle = MiuixTheme.textStyles.title2,
                )
                Spacer(Modifier.width(col(PickerColumnGap)))
                NumberPicker(
                    value = day,
                    onValueChange = { day = it },
                    modifier = Modifier.width(col(MonthDayColumnWidth)),
                    range = 1..maxDay,
                    label = { "${it}日" },
                    visibleItemCount = 3,
                    itemHeight = PickerItemHeight,
                    textStyle = MiuixTheme.textStyles.title2,
                )
                // 组间距：远大于组内 2dp，让「日期 | 时间」在视觉上分成两组
                Spacer(Modifier.width(col(DateToTimeGap)))
                NumberPicker(
                    value = hour,
                    onValueChange = { hour = it },
                    modifier = Modifier.width(col(HourMinuteColumnWidth)),
                    range = 0..maxHour,
                    label = { "%02d".format(it) },
                    visibleItemCount = 3,
                    itemHeight = PickerItemHeight,
                    textStyle = MiuixTheme.textStyles.title2,
                )
                Spacer(Modifier.width(col(PickerColumnGap)))
                NumberPicker(
                    value = minute,
                    onValueChange = { minute = it },
                    modifier = Modifier.width(col(HourMinuteColumnWidth)),
                    range = 0..maxMinute,
                    label = { "%02d".format(it) },
                    visibleItemCount = 3,
                    itemHeight = PickerItemHeight,
                    textStyle = MiuixTheme.textStyles.title2,
                )
            }
        }

    }
}
