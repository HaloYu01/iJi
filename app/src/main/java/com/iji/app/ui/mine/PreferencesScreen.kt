package com.iji.app.ui.mine

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iji.app.budget.NotificationHelper
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.reminder.ReminderScheduler
import com.iji.app.reminder.SuperIslandNotifier
import com.iji.app.ui.FontScaleState
import com.iji.app.ui.HapticsState
import com.iji.app.ui.PerfState
import com.iji.app.ui.components.IjiSettingDropdownValue
import com.iji.app.ui.components.IjiSettingSwitch
import com.iji.app.ui.components.IjiSettingValue
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.IjiSettingsRow
import com.iji.app.ui.components.IjiSettingsSection
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.WheelOptionDialog
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.menu.LiquidGlassDropdownMenu
import com.iji.app.ui.components.menu.LiquidGlassDropdownMenuItem
import com.iji.app.ui.components.menu.LiquidGlassDropdownOverlayHost
import com.iji.app.ui.effects.glass.LocalAppBackdrop
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.ThemeState
import com.iji.app.ui.util.PredictiveBackSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面里需要滚轮选择器的取值（同一时刻只会开一个）——选项少的（主题/周开始日）走浮层卡片 */
private enum class Picker { Hour, MonthStart }

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
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    var picker by remember { mutableStateOf<Picker?>(null) }
    // 选项少的（主题颜色 3 项 / 周开始日 2 项）不再开滚轮弹窗，改为行旁浮出的 LiquidGlass 面板。
    // 锚点bounds / 宿主原点都用**窗口坐标**（popup 层与页面坐标系可能差状态栏高度，见 OverlayHost）
    var themeCardOpen by remember { mutableStateOf(false) }
    var weekStartCardOpen by remember { mutableStateOf(false) }
    val themeAnchorBounds = remember { mutableStateOf(Rect.Zero) }
    val weekAnchorBounds = remember { mutableStateOf(Rect.Zero) }
    val themeMenuFraction = remember { Animatable(0f) }
    val weekMenuFraction = remember { Animatable(0f) }
    val themeHostOrigin = remember { mutableStateOf(Offset.Zero) }
    val weekHostOrigin = remember { mutableStateOf(Offset.Zero) }

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
    // 应用更新：与 UpdateFlow 读的是同一组键，能开就要能关
    var autoCheckUpdate by remember {
        mutableStateOf(SettingsRepository.getBoolean(context, SettingsRepository.KEY_AUTO_CHECK_UPDATE, true))
    }
    var updateReminder by remember {
        mutableStateOf(SettingsRepository.getBoolean(context, SettingsRepository.KEY_UPDATE_REMINDER, true))
    }
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
                // 主题颜色：LiquidGlass 浮层面板（与全站下拉同款），3 个选项不再弹滚轮。
                // 锚点用 **窗口坐标**（boundsInWindow）+ 宿主原点修正：popup 层与页面的
                // 坐标系可能差一个状态栏高度，positionInRoot 会错位（实测）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { themeAnchorBounds.value = it.boundsInWindow() },
                ) {
                    IjiSettingsRow("主题颜色", onClick = { themeCardOpen = !themeCardOpen }) {
                        IjiSettingDropdownValue(
                            themeLabel(theme),
                            modifier = Modifier.graphicsLayer {
                                // 一镜到底（NexioSchedule）：面板从本值芯片长出，值文字+箭头
                                // 随 fraction 向面板内部（左下）漂移淡出；收起更早浮现
                                val iconK = if (themeCardOpen) 2.5f else 1.7f
                                val p = (themeMenuFraction.value * iconK).coerceIn(0f, 1f)
                                alpha = 1f - p
                                translationX = -p * 10.dp.toPx()
                                translationY = p * 10.dp.toPx()
                            },
                        )
                    }
                }
                IjiSettingsDivider()
                IjiSettingsRow("字体大小", onClick = onFontSize) {
                    IjiSettingValue("${FontScaleState.label()} ${FontScaleState.fontSizeSp.toInt()}sp")
                }
            }

            IjiSettingsSection("应用更新") {
                IjiSettingsRow("自动检查更新", subtitle = "每天首次进入应用时检查一次新版本") {
                    IjiSettingSwitch(autoCheckUpdate) { on ->
                        autoCheckUpdate = on
                        SettingsRepository.setBoolean(context, SettingsRepository.KEY_AUTO_CHECK_UPDATE, on)
                    }
                }
                IjiSettingsDivider()
                IjiSettingsRow("发现新版本时提醒", subtitle = "关闭后仍会检查，但不弹更新提示") {
                    IjiSettingSwitch(updateReminder) { on ->
                        updateReminder = on
                        SettingsRepository.setBoolean(context, SettingsRepository.KEY_UPDATE_REMINDER, on)
                    }
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
                // 周开始日：只有 2 个选项，同样走 LiquidGlass 浮层面板（窗口坐标锚点）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { weekAnchorBounds.value = it.boundsInWindow() },
                ) {
                    IjiSettingsRow("周开始日", onClick = { weekStartCardOpen = !weekStartCardOpen }) {
                        IjiSettingDropdownValue(
                            weekStartLabel(weekStartDay),
                            modifier = Modifier.graphicsLayer {
                                // 一镜到底（NexioSchedule）：同主题颜色行
                                val iconK = if (weekStartCardOpen) 2.5f else 1.7f
                                val p = (weekMenuFraction.value * iconK).coerceIn(0f, 1f)
                                alpha = 1f - p
                                translationX = -p * 10.dp.toPx()
                                translationY = p * 10.dp.toPx()
                            },
                        )
                    }
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

    // ── LiquidGlass 浮层面板（主题颜色 / 周开始日）：popup 层，锚点窗口坐标 − 宿主原点 ──
    val prefsBackdrop = LocalAppBackdrop.current
    if (prefsBackdrop != null) {
        // 主题颜色：面板挂在行下方，右缘对齐行右缘
        LiquidGlassDropdownOverlayHost(expanded = themeCardOpen, onOutsideTap = { themeCardOpen = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { themeHostOrigin.value = it.positionInWindow() },
            ) {
                LiquidGlassDropdownMenu(
                    show = themeCardOpen,
                    backdrop = prefsBackdrop,
                    fraction = themeMenuFraction,
                    onDismiss = { themeCardOpen = false },
                    modifier = Modifier
                        .graphicsLayer {
                            val b = themeAnchorBounds.value
                            // 覆盖式（End）：控制盒**右上角**恒 = 行右缘 + 26dp——面板从行首
                            // 位置向左下生长覆盖整行，收起缩回行位置与行内容衔接
                            alpha = (themeMenuFraction.value.coerceAtLeast(0f) / 0.3f).coerceIn(0f, 1f)
                            translationX = b.right + 26.dp.toPx() - size.width - themeHostOrigin.value.x
                            translationY = b.top - 26.dp.toPx() - themeHostOrigin.value.y
                        },
                ) {
                    THEME_VALUES.forEach { value ->
                        LiquidGlassDropdownMenuItem(
                            text = themeLabel(value),
                            selected = value == theme,
                            onClick = {
                                themeCardOpen = false
                                theme = value
                                SettingsRepository.setString(context, "theme_mode", value)
                                ThemeState.themeMode = value
                            },
                        )
                    }
                }
            }
        }
        // 周开始日：同款，面板挂在行下方
        LiquidGlassDropdownOverlayHost(expanded = weekStartCardOpen, onOutsideTap = { weekStartCardOpen = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { weekHostOrigin.value = it.positionInWindow() },
            ) {
                LiquidGlassDropdownMenu(
                    show = weekStartCardOpen,
                    backdrop = prefsBackdrop,
                    fraction = weekMenuFraction,
                    onDismiss = { weekStartCardOpen = false },
                    modifier = Modifier
                        .graphicsLayer {
                            val b = weekAnchorBounds.value
                            // 覆盖式（End）：同主题颜色（控制盒右上角恒 = 行右缘 + 26dp）
                            alpha = (weekMenuFraction.value.coerceAtLeast(0f) / 0.3f).coerceIn(0f, 1f)
                            translationX = b.right + 26.dp.toPx() - size.width - weekHostOrigin.value.x
                            translationY = b.top - 26.dp.toPx() - weekHostOrigin.value.y
                        },
                ) {
                    listOf(1, 7).forEach { day ->
                        LiquidGlassDropdownMenuItem(
                            text = weekStartLabel(day),
                            selected = day == weekStartDay,
                            onClick = {
                                weekStartCardOpen = false
                                weekStartDay = day
                                SettingsRepository.setInt(context, "week_start_day", day)
                            },
                        )
                    }
                }
            }
        }
    }

    // ── 滚轮选择器（选项多：时刻 24 档 / 月开始日 28 档）；主题与周开始日已改浮层卡片 ──

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
