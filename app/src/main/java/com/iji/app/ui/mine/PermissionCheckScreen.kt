package com.iji.app.ui.mine

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iji.app.accessibility.PaymentAccessibilityStatus
import com.iji.app.reminder.SuperIslandNotifier
import com.iji.app.shizuku.ShizukuBridge
import com.iji.app.shizuku.ShizukuState
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.overlay.BillOverlay
import com.iji.app.ui.theme.AppleOrange
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import org.lsposed.hiddenapibypass.HiddenApiBypass
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/** 单项检测结论三态：OK 就绪 · WARN 需要处理 · SKIP 无法检测 / 不强制（不计入失败） */
internal enum class CheckStatus { OK, WARN, SKIP }

/**
 * 一项运行前提的检测结果。开局引导页的「权限」步骤复用同一份检测，避免两处口径漂移。
 *
 * @param key 动作分发用的稳定 id（见 [PermissionCheckScreen] 内的 runAction）
 * @param actionLabel 右侧修复动作文案；null = 当前状态无需动作
 */
internal data class CheckItem(
    val key: String,
    val title: String,
    val detail: String,
    val status: CheckStatus,
    val actionLabel: String? = null,
)

internal const val KEY_NOTIFICATION = "notification"
private const val KEY_OVERLAY = "overlay"
private const val KEY_BATTERY = "battery"
private const val KEY_BACKGROUND = "background"
private const val KEY_AUTOSTART = "autostart"
private const val KEY_SHIZUKU = "shizuku"
private const val KEY_ACCESSIBILITY = "accessibility"

/** MIUI/HyperOS 自启动开关的 AppOps 编号（无公开名字，历代 MIUI/HyperOS 通用） */
private const val MIUI_OP_AUTO_START = 10008

/**
 * 权限检验页（自动记账 → 权限就绪度 / 检查并开启权限）。
 *
 * 这个 App 的核心链路对系统权限有一串「缺一个就静默失效」的运行前提：
 * 通知被关 → 所有提醒无声消失；悬浮窗没授权 → 识屏结果弹不出确认卡片；
 * 电池优化没忽略 / 自启动被禁 → 进程被杀后提醒停摆；
 * Shizuku 没运行/没授权 → 超级岛绕不过云端校验；无障碍没开 → 支付通知无法自动记账。
 * 这些前提散落在系统设置各处，出问题时用户无从排查 —— 本页把它们一次性亮出来。
 *
 * 设计约定：
 * - **只读检测**：本页不替用户改任何系统设置，只显示状态 + 跳转对应开关页；
 * - **三态**而非布尔：[CheckStatus.SKIP] 表示「无法检测 / 不强制」（如非小米机型的
 *   自启动开关、可选的 Shizuku），不计入失败，避免把没有的东西误报成故障；
 * - 回到前台自动重查（ON_RESUME）：用户去系统设置授完权返回即见最新状态，无需手动刷新。
 */
@Composable
fun PermissionCheckScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current

    var items by remember { mutableStateOf(detectPermissions(context)) }
    // 首次说明卡：第一次进本页（含从开局引导跳进来）自动展开，点「知道了」后收起，
    // 页底「查看说明」随时能重新展开 —— 配置必须能清除，不做一次性跑不掉的提示。
    var guideVisible by remember {
        mutableStateOf(
            !SettingsRepository.getBoolean(
                context,
                SettingsRepository.KEY_PERMISSION_GUIDE_SHOWN,
                false,
            ),
        )
    }

    // 去系统设置授权后回来（ON_RESUME）自动重查；通知权限的运行时弹窗关闭也走 launcher 回调刷新
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                items = detectPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) snackbar.show("未授予通知权限，提醒将无法送达")
        items = detectPermissions(context)
    }

    // 汇总口径：SKIP（无法检测 / 可选项）不算失败，否则非小米机型永远「不通过」
    val okCount = items.count { it.status == CheckStatus.OK }
    val neededCount = items.count { it.status != CheckStatus.SKIP }
    val allOk = okCount == neededCount

    // 修复动作：与开局引导第 4 步共用同一份，逐项跳对应系统开关、失败逐级回落
    val runAction: (CheckItem) -> Unit = { item ->
        runPermissionAction(
            context = context,
            item = item,
            onRequestNotificationPermission = {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
            onMessage = { message -> snackbar.show(message) },
        )
    }

    MineSubPage(title = "权限检验", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 首次说明卡：把「为什么缺一个就静默失效」讲清楚，避免用户把 7 项当成强制授权清单
            if (guideVisible) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = Radius.xxl,
                    colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            "为什么需要这些权限",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "记账本身不需要任何权限。下面这些只影响提醒送达、识屏确认卡片、" +
                                "后台保活与自动记账：缺一个，对应功能会静默失效而不会弹错。" +
                                "Shizuku 与自启动是可选增强，未安装或读不到不算故障。" +
                                "本页只做只读检测，点击条目会带你去系统开关，不会替你修改设置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        IjiTextAction(
                            text = "知道了",
                            compact = true,
                            onClick = {
                                guideVisible = false
                                SettingsRepository.setBoolean(
                                    context,
                                    SettingsRepository.KEY_PERMISSION_GUIDE_SHOWN,
                                    true,
                                )
                            },
                        )
                    }
                }
            }

            // 汇总卡（克制的一行结论，不做大数字，避免和下方行内文字抢层级）
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        if (allOk) "全部就绪" else "$okCount / $neededCount 项就绪",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (allOk) IncomeGreen else AppleOrange,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (allOk) {
                            "提醒、识屏确认、后台保活与自动记账的运行前提全部满足"
                        } else {
                            "逐项处理「需处理」的行，回到本页会自动重新检测"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            // 明细卡：一行一项（状态徽标 + 标题/说明 + 修复动作）
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                Column {
                    items.forEachIndexed { index, item ->
                        CheckRow(item = item, onAction = { runAction(item) })
                        if (index < items.lastIndex) IjiSettingsDivider()
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本页只做只读状态查询；点击「需处理」的行跳转对应开关页，完成后返回自动刷新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                IjiTextAction(text = "查看说明", compact = true, onClick = { guideVisible = true })
            }
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem, onAction: () -> Unit) {
    val (statusText, statusColor) = when (item.status) {
        CheckStatus.OK -> "已就绪" to IncomeGreen
        CheckStatus.WARN -> "需处理" to AppleOrange
        CheckStatus.SKIP -> "无法检测" to TextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 已就绪的项整行不可点，避免「点了没反应」
            .clickable(enabled = item.actionLabel != null, onClick = onAction)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        // 单一右列：状态词回答「现在怎么样」，箭头提示「能不能点」——
        // 不再叠加徽标 + 胶囊两套彩色元素，行内颜色只出现在状态词上
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
        if (item.actionLabel != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── 检测逻辑（纯函数，ON_RESUME 时整表重算；开局引导页复用同一份）──────────────

internal fun detectPermissions(context: Context): List<CheckItem> = listOf(
    notificationItem(context),
    overlayItem(context),
    batteryItem(context),
    backgroundItem(context),
    autoStartItem(context),
    shizukuItem(context),
    accessibilityItem(context),
)

/**
 * 悬浮窗权限（识屏记账的确认卡片要浮在其他应用之上）。
 * 复用 [BillOverlay.canShow] 的口径，保证权限页的结论与实际弹窗判断一致：
 * 未授权时 BillOverlay.show 会退回通知，用户看到的就是「识屏结果没有卡片」。
 */
private fun overlayItem(context: Context): CheckItem {
    val granted = runCatching { BillOverlay.canShow(context) }.getOrDefault(false)
    return CheckItem(
        key = KEY_OVERLAY,
        title = "悬浮窗权限",
        detail = if (granted) {
            "已授权，识屏记账的确认卡片可浮在其他应用上"
        } else {
            "未授权，识屏结果无法弹出确认卡片，会退回通知"
        },
        status = if (granted) CheckStatus.OK else CheckStatus.WARN,
        actionLabel = if (granted) null else "去开启",
    )
}

/**
 * 通知权限。用 areNotificationsEnabled 而不是 checkSelfPermission：
 * 它同时覆盖 Android 13+ 的运行时权限和低版本系统设置里的通知总开关
 * （低版本把通知关掉后权限查询仍是 GRANTED，会误报）。
 */
private fun notificationItem(context: Context): CheckItem {
    val enabled = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
        .getOrDefault(false)
    return CheckItem(
        key = KEY_NOTIFICATION,
        title = "通知权限",
        detail = if (enabled) {
            "已允许通知，记账提醒与预算告警可正常送达"
        } else {
            "未允许通知，所有提醒都会无声消失"
        },
        status = if (enabled) CheckStatus.OK else CheckStatus.WARN,
        actionLabel = if (enabled) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "去授权" else "去设置",
    )
}

/** 忽略电池优化：白名单内的应用后台不会被 Doze 冻结网络与任务调度 */
private fun batteryItem(context: Context): CheckItem {
    val ignoring = runCatching {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
    return CheckItem(
        key = KEY_BATTERY,
        title = "忽略电池优化",
        detail = if (ignoring) {
            "已在电池优化白名单，锁屏后提醒不会被推迟"
        } else {
            "锁屏后提醒可能被推迟到下次亮屏才送达"
        },
        status = if (ignoring) CheckStatus.OK else CheckStatus.WARN,
        actionLabel = if (ignoring) null else "去设置",
    )
}

/**
 * 后台运行限制（Android 9+ 的「受限后台」开关，应用信息 → 电池 → 后台限制）。
 * 该开关一开，应用的后台服务与任务调度全部停摆；9 以下没有这个机制，视为可用。
 */
private fun backgroundItem(context: Context): CheckItem {
    val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && runCatching {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted
    }.getOrDefault(false)
    return CheckItem(
        key = KEY_BACKGROUND,
        title = "后台运行",
        detail = when {
            restricted -> "后台活动已被系统限制，提醒与自动记账会停摆"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> "未开启「受限后台」，可正常后台运行"
            else -> "此系统版本不存在后台限制机制"
        },
        status = if (restricted) CheckStatus.WARN else CheckStatus.OK,
        actionLabel = if (restricted) "去设置" else null,
    )
}

/**
 * 自启动（后台保活）。MIUI/HyperOS 的自启动开关是 AppOps 隐藏 op 10008，
 * 没有公开 API，反射读取；读不到（非小米 / 系统限制）返回 null → 显示「无法检测」
 * 而不是误报。这是 MIUI 杀后台后提醒失效的最常见元凶。
 */
private fun autoStartItem(context: Context): CheckItem {
    val autoStart = detectAutoStart(context)
    return CheckItem(
        key = KEY_AUTOSTART,
        title = "自启动（后台保活）",
        detail = when (autoStart) {
            true -> "已允许自启动，进程被杀后可重新拉起"
            false -> "自启动被禁止，进程被杀后定时提醒会失效"
            null -> "无法检测（非 MIUI/HyperOS），请自行确认"
        },
        status = when (autoStart) {
            true -> CheckStatus.OK
            false -> CheckStatus.WARN
            null -> CheckStatus.SKIP
        },
        actionLabel = "去设置",
    )
}

/** MIUI/HyperOS 自启动开关读取；true/false = 已知状态，null = 读不到 */
private fun detectAutoStart(context: Context): Boolean? {
    // 先按厂商短路：非小米系统上这个 op 没有意义，避免个别 ROM 恰好返回 0 造成误报
    if (!SuperIslandNotifier.isMiui()) return null
    return runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val uid = context.packageManager.getApplicationInfo(context.packageName, 0).uid
        val mode = runCatching {
            // API 29+ 公开的 String-op 重载查不到自定义编号，int-op 版本是隐藏方法 ——
            // 统一走 HiddenApiBypass（对非隐藏方法等价于普通反射，对新旧系统都适用）
            HiddenApiBypass.invoke(
                AppOpsManager::class.java,
                appOps,
                "checkOpNoThrow",
                MIUI_OP_AUTO_START,
                uid,
                context.packageName,
            ) as Int
        }.getOrElse {
            runCatching {
                // 老 MIUI 上 HiddenApiBypass 不可用时再退普通反射（该版本上方法还是公开的）
                val method = AppOpsManager::class.java.getMethod(
                    "checkOpNoThrow",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                )
                method.invoke(appOps, MIUI_OP_AUTO_START, uid, context.packageName) as Int
            }.getOrElse { return null }
        }
        when (mode) {
            0 -> true // MODE_ALLOWED
            1, 2 -> false // MODE_IGNORED / MODE_ERRORED
            else -> null // MODE_DEFAULT / ERR 等未知语义，宁可显示无法检测
        }
    }.getOrNull()
}

/**
 * Shizuku 运行状态（五态来自 [ShizukuState]，严格递进：没装 → 没运行 → 没授权 → 可用）。
 * Shizuku 是可选增强（识屏、超级岛 bypass），未装算 SKIP 不算故障；
 * 但装了没开 / 没授权说明用户想用它而没走完流程，算 WARN 提示补完。
 */
private fun shizukuItem(context: Context): CheckItem {
    val state = runCatching { ShizukuBridge.state(context) }.getOrDefault(ShizukuState.NOT_INSTALLED)
    val (status, detail, action) = when (state) {
        ShizukuState.READY ->
            Triple(CheckStatus.OK, "已安装并授权，识屏与超级岛 bypass 可用", null)
        ShizukuState.NO_PERMISSION ->
            Triple(CheckStatus.WARN, "Shizuku 正在运行，但尚未授权本应用", "去授权")
        ShizukuState.NOT_RUNNING ->
            Triple(CheckStatus.WARN, "已安装但未运行，请先完成启动", "去启动")
        ShizukuState.NOT_INSTALLED ->
            Triple(CheckStatus.SKIP, "未安装（可选增强，缺省不影响基本功能）", "去了解")
        ShizukuState.UNSUPPORTED ->
            Triple(CheckStatus.WARN, "版本过低（需 v11+），借不到 shell 服务", "去了解")
    }
    return CheckItem(
        key = KEY_SHIZUKU,
        title = "Shizuku 运行状态",
        detail = detail,
        status = status,
        actionLabel = action,
    )
}

/** 无障碍服务（自动记账主链路）：无公开授权 API，读系统设置判断（见 PaymentAccessibilityStatus） */
private fun accessibilityItem(context: Context): CheckItem {
    val enabled = runCatching { PaymentAccessibilityStatus.isEnabled(context) }.getOrDefault(false)
    return CheckItem(
        key = KEY_ACCESSIBILITY,
        title = "无障碍服务（自动记账）",
        detail = if (enabled) {
            "已开启，支付通知可自动记账"
        } else {
            "未开启，通知自动记账不可用"
        },
        status = if (enabled) CheckStatus.OK else CheckStatus.WARN,
        actionLabel = if (enabled) null else "去开启",
    )
}

// ── 跳转工具（runCatching 在调用侧统一包）──────────────────────────────────

/**
 * 执行一项运行前提的修复动作：每项一个稳定跳转目标，系统没有对应入口时逐级回落，
 * 不允许「点了没反应」。
 *
 * 抽成共享函数是因为开局引导第 4 步也直接调用它（点某项 → 直接进系统开关），
 * 两处若各写一份，跳转目标与回落链必然漂移。
 *
 * @param onRequestNotificationPermission Android 13+ 用运行时弹窗申请通知权限；
 *        由调用侧传自己的 launcher，保证授权结果回调后能重新检测
 * @param onMessage 无法跳转 / 无可跳页面时向用户交代原因（调用侧的 snackbar）
 */
internal fun runPermissionAction(
    context: Context,
    item: CheckItem,
    onRequestNotificationPermission: () -> Unit,
    onMessage: (String) -> Unit,
) {
    runCatching {
        when (item.key) {
            KEY_NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    onRequestNotificationPermission()
                } else {
                    openAppNotificationSettings(context)
                }
            }

            KEY_OVERLAY ->
                // 个别 ROM 没有 ACTION_MANAGE_OVERLAY_PERMISSION 入口，回落应用详情页
                if (!BillOverlay.openPermissionSettings(context)) openAppDetails(context)

            KEY_BATTERY -> runCatching {
                // 直接申请弹窗（需 manifest 声明 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS）
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.getOrElse {
                // 个别 ROM 移除了直接申请入口，回落到白名单总列表（无需权限声明）
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }

            KEY_BACKGROUND -> openAppDetails(context)

            KEY_AUTOSTART -> {
                val opened = runCatching {
                    // MIUI/HyperOS 的自启动管理页（安全中心内）；签名/包名随版本变化，失败即回落
                    context.startActivity(
                        Intent().setComponent(
                            ComponentName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity",
                            ),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                if (!opened) openAppDetails(context)
            }

            KEY_SHIZUKU -> when (
                runCatching { ShizukuBridge.state(context) }
                    .getOrDefault(ShizukuState.NOT_INSTALLED)
            ) {
                ShizukuState.NOT_RUNNING ->
                    if (!ShizukuBridge.openManager(context)) {
                        onMessage("无法打开 Shizuku，请手动启动它")
                    }

                ShizukuState.NO_PERMISSION -> ShizukuBridge.requestPermission()
                // 未装 / 版本过低：没有应用内引导页可跳，如实提示安装要求
                ShizukuState.NOT_INSTALLED, ShizukuState.UNSUPPORTED ->
                    onMessage("需要安装 Shizuku 11 及以上才能用 Shizuku 截屏")
                ShizukuState.READY -> {}
            }

            KEY_ACCESSIBILITY ->
                if (!PaymentAccessibilityStatus.openSettings(context)) {
                    onMessage("无法打开无障碍设置，请手动前往")
                }
        }
    }.onFailure { onMessage("无法打开对应设置页，请手动前往系统设置") }
}

private fun openAppNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
