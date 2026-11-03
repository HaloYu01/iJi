package com.iji.app.ui.mine

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iji.app.budget.NotificationHelper
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.reminder.ReminderScheduler
import com.iji.app.reminder.SuperIslandNotifier
import com.iji.app.ui.FontScaleState
import com.iji.app.ui.HapticsState
import com.iji.app.ui.PerfState
import com.iji.app.ui.components.IjiSettingSwitch
import com.iji.app.ui.components.IjiSettingValue
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.IjiSettingsRow
import com.iji.app.ui.components.IjiSettingsSection
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.WheelOptionDialog
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.ThemeState
import com.iji.app.ui.util.PredictiveBackSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面里需要滚轮选择器的四类取值（同一时刻只会开一个） */
private enum class Picker { Theme, Hour, WeekStart, MonthStart }

private val THEME_VALUES = listOf("system", "light", "dark")

private fun themeLabel(value: String) = when (value) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}

private fun weekStartLabel(day: Int) = if (day == 7) "周日" else "周一"

/**
 * 偏好设置：外观 / 提醒 / 超级岛 / 时间 / 动效与手感。
 *
 * 归类原则：**同一件事只出现在一个分组里** —— 记账提醒的样式（小米超级岛 / 原生通知）、
 * 开关、时刻、试发都在「提醒」；「超级岛」只放**其他**业务（预算 / 周期账单 / 识屏）的
 * 上岛开关和岛自身的光效，不再重复记账提醒的样式。
 * 文字只留标题 + 一句必要说明；行构造与分隔线走 [IjiSettingsSection] / [IjiSettingsRow]。
 */
@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    onFontSize: () -> Unit,
    onPermissionCheck: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    var picker by remember { mutableStateOf<Picker?>(null) }

    // 外观
    var theme by remember { mutableStateOf(SettingsRepository.getString(context, "theme_mode", "system")) }

    // 提醒
    var reminderOn by remember { mutableStateOf(ReminderScheduler.isEnabled(context)) }
    var reminderHour by remember { mutableStateOf(ReminderScheduler.currentHour(context)) }
    // 通知样式：小米超级岛 / 原生通知，与发送侧的 ReminderScheduler.currentStyle 同一个键
    var notifyStyle by remember { mutableStateOf(ReminderScheduler.currentStyle(context)) }

    // 超级岛：每类通知一个开关，键与本页之外的发送侧一一对应
    var islandBudget by remember { mutableStateOf(NotificationHelper.isBudgetIslandEnabled(context)) }
    var islandProgress by remember { mutableStateOf(NotificationHelper.isScreenshotProgressIslandEnabled(context)) }
    var islandResult by remember { mutableStateOf(NotificationHelper.isScreenshotIslandEnabled(context)) }
    var islandRecurring by remember { mutableStateOf(NotificationHelper.isRecurringIslandEnabled(context)) }
    var islandGlow by remember { mutableStateOf(SuperIslandNotifier.isGlowEnabled(context)) }

    // 时间
    var is24Hour by remember { mutableStateOf(SettingsRepository.getBoolean(context, "is_24_hour", true)) }
    var weekStartDay by remember { mutableStateOf(SettingsRepository.getInt(context, "week_start_day", 1)) }
    var monthStartDay by remember { mutableStateOf(SettingsRepository.getInt(context, "month_start_day", 1)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) snackbar.show("未授予通知权限，提醒不会响")
    }

    // 开岛上开关时的唯一前置：焦点通知没开就只会收到普通通知（发送侧会自动降级，不报错）
    fun warnIfNoFocus() {
        if (!SuperIslandNotifier.hasFocusPermission(context)) {
            snackbar.show("未开启「焦点通知」，可能只收到普通通知（系统设置 → 通知与控制中心）")
        }
    }

    // 试发走真实链路（含 Shizuku 焦点校验切换），放 IO 线程
    val sendTestNotification: () -> Unit = {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                NotificationHelper.sendRecordReminder(
                    context,
                    title = "测试提醒",
                    content = "样式与记账提醒一致",
                    testMode = true,
                )
            }
            snackbar.show(if (result.sent) "已发送：${result.detail}" else "发送失败：${result.detail}")
        }
    }

    MineSubPage(title = "偏好设置", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IjiSettingsSection("外观") {
                IjiSettingsRow("主题颜色", onClick = { picker = Picker.Theme }) {
                    IjiSettingValue(themeLabel(theme))
                }
                IjiSettingsDivider()
                IjiSettingsRow("字体大小", onClick = onFontSize) {
                    IjiSettingValue("${FontScaleState.label()} ${FontScaleState.fontSizeSp.toInt()}sp")
                }
            }

            IjiSettingsSection("提醒") {
                IjiSettingsRow("记账提醒", subtitle = "当天还没记账时才提醒") {
                    IjiSettingSwitch(reminderOn) { on ->
                        reminderOn = on
                        ReminderScheduler.setEnabled(context, on, reminderHour)
                        // 开启时顺带申请通知权限，否则系统层面收不到
                        if (on) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                IjiSettingsDivider()
                // 样式是提醒的一部分（决定到点后长什么样），所以留在本组；
                // 「超级岛」组只放各类通知的上岛开关，不再重复这一个。
                // 开关 = 小米超级岛；关 = 原生通知（两行副标题把当前值说明白，
                // 不再需要点开弹窗才知道自己选的是哪种）
                IjiSettingsRow(
                    "通知样式",
                    subtitle = if (notifyStyle == ReminderScheduler.STYLE_ISLAND) {
                        "小米超级岛 · 事件发生时浮到状态栏"
                    } else {
                        "原生通知 · 系统实时通知，不占用超级岛"
                    },
                ) {
                    IjiSettingSwitch(notifyStyle == ReminderScheduler.STYLE_ISLAND) { on ->
                        val style = if (on) {
                            ReminderScheduler.STYLE_ISLAND
                        } else {
                            ReminderScheduler.STYLE_NATIVE
                        }
                        notifyStyle = style
                        ReminderScheduler.setStyle(context, style)
                        if (on) warnIfNoFocus()
                    }
                }
                IjiSettingsDivider()
                IjiSettingsRow("提醒时间", onClick = { picker = Picker.Hour }) {
                    IjiSettingValue("%02d:00".format(reminderHour))
                }
                IjiSettingsDivider()
                IjiSettingsRow("发送测试通知", subtitle = "按当前设置立即试发一条", onClick = sendTestNotification) {
                    IjiSettingValue()
                }
                IjiSettingsDivider()
                IjiSettingsRow("权限检验", subtitle = "通知 / 保活 / Shizuku / 无障碍", onClick = onPermissionCheck) {
                    IjiSettingValue()
                }
            }

            IjiSettingsSection("超级岛") {
                IjiSettingsRow("预算提醒上岛") {
                    IjiSettingSwitch(islandBudget) { on ->
                        islandBudget = on
                        SettingsRepository.setBoolean(context, "island_budget", on)
                        if (on) warnIfNoFocus()
                    }
                }
                IjiSettingsDivider()
                IjiSettingsRow("周期账单上岛") {
                    IjiSettingSwitch(islandRecurring) { on ->
                        islandRecurring = on
                        SettingsRepository.setBoolean(context, "island_recurring", on)
                        if (on) warnIfNoFocus()
                    }
                }
                IjiSettingsDivider()
                IjiSettingsRow("识屏识别中上岛") {
                    IjiSettingSwitch(islandProgress) { on ->
                        islandProgress = on
                        NotificationHelper.setScreenshotProgressIslandEnabled(context, on)
                        if (on) warnIfNoFocus()
                    }
                }
                IjiSettingsDivider()
                IjiSettingsRow("识屏识别结果上岛") {
                    IjiSettingSwitch(islandResult) { on ->
                        islandResult = on
                        SettingsRepository.setBoolean(context, "island_screenshot", on)
                        if (on) warnIfNoFocus()
                    }
                }
                IjiSettingsDivider()
                IjiSettingsRow("岛展开光效") {
                    IjiSettingSwitch(islandGlow) { on ->
                        islandGlow = on
                        SettingsRepository.setBoolean(context, SuperIslandNotifier.KEY_ISLAND_GLOW, on)
                    }
                }
            }

            IjiSettingsSection("时间") {
                IjiSettingsRow("24小时制") {
                    IjiSettingSwitch(is24Hour) { on ->
                        is24Hour = on
                        SettingsRepository.setBoolean(context, "is_24_hour", on)
                    }
                }
                IjiSettingsDivider()
                IjiSettingsRow("周开始日", onClick = { picker = Picker.WeekStart }) {
                    IjiSettingValue(weekStartLabel(weekStartDay))
                }
                IjiSettingsDivider()
                IjiSettingsRow("月开始日", onClick = { picker = Picker.MonthStart }) {
                    IjiSettingValue("$monthStartDay 号")
                }
            }

            IjiSettingsSection("动效与手感") {
                IjiSettingsRow("降低动态效果", subtitle = "关掉液态玻璃的模糊，滚动与切页更稳") {
                    IjiSettingSwitch(PerfState.reducedEffects) { PerfState.setReducedEffects(context, it) }
                }
                IjiSettingsDivider()
                IjiSettingsRow("预测性返回动画", subtitle = "返回手势跟手预览") {
                    IjiSettingSwitch(PredictiveBackSettings.enabled) { PredictiveBackSettings.setEnabled(context, it) }
                }
                IjiSettingsDivider()
                IjiSettingsRow("触感反馈", subtitle = "操作与选择时的轻微震动") {
                    IjiSettingSwitch(HapticsState.enabled) { HapticsState.setEnabled(context, it) }
                }
            }
        }
    }

    // ── 四个滚轮选择器（同一时刻只显示一个）──

    WheelOptionDialog(
        show = picker == Picker.Theme,
        options = THEME_VALUES,
        selected = theme,
        title = "主题颜色",
        label = { themeLabel(it) },
        onDismissRequest = { picker = null },
        onConfirm = { value ->
            picker = null
            theme = value
            SettingsRepository.setString(context, "theme_mode", value)
            ThemeState.themeMode = value
        },
    )

    WheelOptionDialog(
        show = picker == Picker.Hour,
        options = (0..23).toList(),
        selected = reminderHour,
        title = "提醒时间",
        label = { "%02d:00".format(it) },
        onDismissRequest = { picker = null },
        onConfirm = { hour ->
            picker = null
            reminderHour = hour
            ReminderScheduler.setHour(context, hour)
        },
    )

    WheelOptionDialog(
        show = picker == Picker.WeekStart,
        options = listOf(1, 7),
        selected = weekStartDay,
        title = "周开始日",
        label = { weekStartLabel(it) },
        onDismissRequest = { picker = null },
        onConfirm = { day ->
            picker = null
            weekStartDay = day
            SettingsRepository.setInt(context, "week_start_day", day)
        },
    )

    WheelOptionDialog(
        show = picker == Picker.MonthStart,
        options = (1..28).toList(),
        selected = monthStartDay,
        title = "月开始日",
        label = { "$it 号" },
        onDismissRequest = { picker = null },
        onConfirm = { day ->
            picker = null
            monthStartDay = day
            SettingsRepository.setInt(context, "month_start_day", day)
        },
    )
}
