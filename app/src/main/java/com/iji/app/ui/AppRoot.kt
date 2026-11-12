package com.iji.app.ui

import com.iji.app.ui.mine.StartupUpdateCheck
import com.iji.app.ui.mine.UpdateDialog
import com.iji.app.ui.mine.privacyPolicyVersion
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.ui.components.exitApp

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.iji.app.ui.components.ijiTopBarSafeTopPadding
import com.iji.app.ui.nav.NavMotion
import com.iji.app.ui.add.AddTransactionSheet
import com.iji.app.ui.ai.AiBookkeepingScreen
import com.iji.app.ui.assets.AssetsScreen
import com.iji.app.ui.calendar.CalendarScreen
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.rememberSnackbarController
import com.iji.app.ui.components.rememberSnackbarHostState
import com.iji.app.ui.home.HomeScreen
import com.iji.app.ui.mine.AvatarEditScreen
import com.iji.app.ui.mine.MineScreen
import com.iji.app.ui.mine.PasswordProtectScreen
import com.iji.app.ui.mine.AutoBookkeepingScreen
import com.iji.app.ui.mine.PreferencesScreen
import com.iji.app.ui.mine.LedgerManageScreen
import com.iji.app.ui.mine.RecurringManageScreen
import com.iji.app.ui.mine.ReimburseManageScreen
import com.iji.app.ui.mine.AboutScreen
import com.iji.app.ui.mine.DonationScreen
import com.iji.app.ui.mine.CrashLogScreen
import com.iji.app.ui.mine.ChangelogScreen
import com.iji.app.ui.mine.GuideTutorialScreen
import com.iji.app.ui.mine.LicenseScreen
import com.iji.app.ui.mine.PrivacyPolicyScreen
import com.iji.app.ui.mine.BackupSettingsScreen
import com.iji.app.ui.mine.BillImportScreen
import com.iji.app.ui.mine.CloudBackupScreen
import com.iji.app.ui.mine.CategoryManageScreen
import com.iji.app.ui.mine.CategoryEditScreen
import com.iji.app.data.model.TransactionType
import com.iji.app.ui.mine.TagBillsScreen
import com.iji.app.ui.mine.TagManageScreen
import com.iji.app.ui.mine.TemplateManageScreen
import com.iji.app.ui.mine.TemplateEditScreen
import com.iji.app.ui.budget.CategoryBudgetScreen
import com.iji.app.ui.mine.BudgetScreen
import com.iji.app.ui.mine.FontSizeScreen
import com.iji.app.ui.mine.PermissionCheckScreen
import com.iji.app.lock.AppLockManager
import com.iji.app.lock.LockScreen
import com.iji.app.ui.components.LocalOverlayBackStack
import com.iji.app.ui.components.OverlayBackHandler
import com.iji.app.ui.components.OverlayBackStack
import androidx.activity.compose.PredictiveBackHandler
import com.iji.app.ui.util.PredictiveBackSettings
import kotlin.coroutines.cancellation.CancellationException
import com.iji.app.ui.stats.CategoryBillsScreen
import com.iji.app.ui.stats.StatsScreen
import com.iji.app.ui.components.navbar.LiquidBottomTab
import com.iji.app.ui.components.navbar.LiquidBottomTabs
import com.iji.app.ui.components.navbar.LiquidBottomTrailing
import com.iji.app.ui.effects.glass.LocalAppBackdrop
import com.iji.app.ui.util.isAppDarkTheme
import com.kyant.backdrop.AppThemeSnapshotSync
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Notes

/** 路由表 */
object Routes {
    const val HOME = "home"          // 账本
    const val ASSETS = "assets"      // 资产
    const val CALENDAR = "calendar"  // 日历
    const val STATS = "stats"        // 统计
    /** 统计下钻：某分类在指定区间内的账单（start/end 为 yyyy-MM-dd） */
    const val STATS_CATEGORY = "stats_category?categoryId={categoryId}&start={start}&end={end}"
    const val ADD_ACCOUNT = "add_account"  // 添加账户（独立页面）
    const val ACCOUNT_DETAIL = "account_detail/{accountId}"  // 账户详情
    const val EDIT_ACCOUNT = "edit_account/{accountId}"      // 编辑账户
    const val BORROW_LEND = "borrow_lend/{direction}"        // 借贷记录（借入 / 借出）
    const val BORROW_LEND_DETAIL = "borrow_lend_detail/{txId}"  // 借贷订单详情
    const val MINE = "mine"          // 我的
    const val AI = "ai"              // AI 记账
    const val PROMPTS = "prompts"          // 提示词与规则（AI 提示词 + 本地识别规则）
    const val PROMPT_EDIT = "prompt_edit/{kind}"          // 单条提示词编辑（kind = AiPromptKind.key）
    const val LOCAL_RULE_EDIT = "local_rule_edit/{ruleId}" // 本地识别规则编辑（ruleId = new 表示新增）
    const val NOTIFICATION_RULES = "notification_rules"  // 通知识别规则（过滤关键词 + 支持来源）
    const val SMART_CAPTURE = "smart_capture"            // 智能识别（一句话 / 小票 / 语音，手动触发）
    const val PASSWORD_PROTECT = "password_protect"
    const val CATEGORY_MANAGE = "category_manage"
    const val CATEGORY_EDIT =
        "category_edit?categoryId={categoryId}&type={type}&parentId={parentId}&ledger={ledger}"
    const val TAG_MANAGE = "tag_manage"
    /** 标签下钻：带该标签的全部账单（tag 需 Uri.encode 后拼接） */
    const val TAG_BILLS = "tag_bills?tag={tag}"
    const val BUDGET = "budget"
    const val CATEGORY_BUDGET = "category_budget"  // 分类预算管理
    const val TEMPLATE_MANAGE = "template_manage"  // 记账模板管理
    const val TEMPLATE_EDIT = "template_edit/{templateId}"  // 记账模板编辑（templateId = new 表示新建）
    const val REIMBURSE_MANAGE = "reimburse_manage"  // 应收 / 垫付
    const val FONT_SIZE = "font_size"
    const val AUTO_BOOKKEEPING = "auto_bookkeeping"
    const val PREFERENCES = "preferences"
    const val PERMISSION_CHECK = "permission_check"  // 权限检验（通知 / 后台 / 电池优化 / Shizuku / 无障碍）
    const val ABOUT = "about"
    const val DONATION = "donation"           // 捐赠支出（赞赏码 + 手工公示）
    const val CRASH_LOG = "crash_log"
    const val CHANGELOG = "changelog"        // 更新日志
    const val GUIDE_TUTORIAL = "guide_tutorial"  // 引导教程（App 使用说明文档）
    const val ONBOARDING = "onboarding"                    // 首次启动的引导配置（同意隐私后）
    const val ONBOARDING_RECONFIGURE = "onboarding_reconfigure"  // 引导教程页底部「重新配置引导」
    const val LICENSE = "license"            // 开源许可
    const val PRIVACY = "privacy"            // 隐私政策
    const val BILL_IMPORT = "bill_import"
    const val BILL_IMPORT_PREVIEW = "bill_import_preview"  // 导入预览（CSV / 截图识别共用）
    const val IMPORT_HISTORY = "import_history"          // 导入记录与恢复
    const val SCREENSHOT_IMPORT = "screenshot_import"  // 截屏批量导入
    const val LEDGER_MANAGE = "ledger_manage"
    const val BACKUP_SETTINGS = "backup_settings"  // 数据与备份（原「备份与恢复」，已并入数据管理）
    const val CLOUD_BACKUP = "cloud_backup"  // 网盘备份（WebDAV）：配置 / 上传 / 恢复 / 自动备份
    const val RECURRING_MANAGE = "recurring_manage"  // 周期账单（recurring_rules）
    const val RECONCILE = "reconcile?account={account}"  // 对账（reconciled）；不传 account = 全部账户
    const val BUDGET_ALERT_HISTORY = "budget_alert_history"  // 预算提醒记录（budget_alerts）
    const val AVATAR_EDIT = "avatar_edit?uri={uri}"  // 调整头像（选图后裁剪保存）
}

/**
 * 新建账户后回传给资产页的「待高亮账户 id」键（放在上一页的 savedStateHandle 里）。
 *
 * 不放在 MainViewModel 上：这是一次性导航结果，跟着返回栈走更清楚，
 * 也不会在用户下次进入资产页时重复触发高亮。
 */
private const val KEY_HIGHLIGHT_ACCOUNT = "highlightAccountId"

/** 主 tab 路由（顺序 = 底栏顺序）：决定底栏显隐，也决定 tab 转场的滑动方向 */
private val TabOrder = listOf(Routes.HOME, Routes.ASSETS, Routes.CALENDAR, Routes.STATS)
private val TabRouteSet: Set<String> = TabOrder.toSet()

/**
 * 自行处理安全区的路由：主 tab（各页 IjiTopBar 内部已避让）+ 已接入 IjiTopBar 的
 * 「我的」/ 关于 / 更新日志 / 开源许可 / 隐私政策 / 崩溃日志等。
 *
 * 这些页面内部用 ijiTopBarContentPadding()（= 安全区 + 折叠态高度）让位，
 * 若外面再垫一次安全区就会**双重加高**，顶部元素比首页低一截、不与首页齐平。
 * 其余旧式子页面没有 IjiTopBar，仍必须让出安全区，否则顶栏会顶到状态栏/挖孔。
 *
 * 注意：这份清单只被 [ijiRoute] 使用，**不再**参与 NavHost 外层的 padding ——
 * 见 [ijiRoute] 注释里的「切页抖动」说明。
 */
private val ImmersiveRoutes: Set<String> = TabRouteSet + setOf(
    Routes.MINE,
    Routes.PREFERENCES,
    Routes.PERMISSION_CHECK,
    Routes.FONT_SIZE,
    Routes.PASSWORD_PROTECT,
    Routes.TAG_MANAGE,
    Routes.TAG_BILLS,
    Routes.BUDGET,
    Routes.BILL_IMPORT,
    Routes.BILL_IMPORT_PREVIEW,
    Routes.IMPORT_HISTORY,
    Routes.AUTO_BOOKKEEPING,
    Routes.CATEGORY_EDIT,
    Routes.STATS_CATEGORY,
    Routes.TEMPLATE_MANAGE,
    Routes.TEMPLATE_EDIT,
    Routes.REIMBURSE_MANAGE,
    Routes.LEDGER_MANAGE,
    Routes.RECURRING_MANAGE,
    Routes.RECONCILE,
    Routes.BUDGET_ALERT_HISTORY,
    Routes.BACKUP_SETTINGS,
    Routes.CLOUD_BACKUP,
    Routes.CATEGORY_MANAGE,
    Routes.SCREENSHOT_IMPORT,
    Routes.ABOUT,
    Routes.DONATION,
    Routes.CHANGELOG,
    Routes.GUIDE_TUTORIAL,
    // 引导页顶栏是自定义短横条进度，安全区由页面自己吃（statusBarsPadding）；
    // 登记进 ImmersiveRoutes 才不会被 ijiRoute 再垫一次顶部安全区。
    Routes.ONBOARDING,
    Routes.ONBOARDING_RECONFIGURE,
    Routes.LICENSE,
    Routes.PRIVACY,
    Routes.CRASH_LOG,
    Routes.AVATAR_EDIT,
    // AI 记账及其相关子页都用 MineSubPage（浮层 IjiTopBar + 自己消费安全区）。
    // Routes.AI 必须在这里：它同样用 MineSubPage，漏登记会让外层再垫一次安全区，
    // 整条顶栏连着内容一起下移一个状态栏高度（实测 52dp），与其它子页不齐平。
    Routes.AI,
    Routes.PROMPTS,
    Routes.PROMPT_EDIT,
    Routes.LOCAL_RULE_EDIT,
    Routes.NOTIFICATION_RULES,
    Routes.SMART_CAPTURE,
)

/**
 * 与 Navigation Compose 的 `composable` 等价，区别只有一点：**安全区在目的地内部消化**。
 *
 * 为什么不能像以前那样在 NavHost 外层按 `currentRoute` 加 padding：
 * `currentRoute` 在转场**开始那一帧**就变成新路由，而旧页面还要在屏幕上滑出 400ms。
 * 于是「主 tab（padding 0）→ 旧式子页（padding = 状态栏高度）」这类跳转里，
 * 正在滑出的原页面会在第一帧整体向下位移一个状态栏高度（返回时向上），
 * 表现为「切页瞬间页面突然上下抖一下」。把 padding 放进每个目的地后，
 * 两个页面各自在自己的容器里完成入场/退场，任何一帧都不会改变高度或位置。
 */
private fun NavGraphBuilder.ijiRoute(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    val routePattern = entry.destination.route
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = if (routePattern in ImmersiveRoutes) 0.dp else ijiTopBarSafeTopPadding()),
    ) {
        content(entry)
    }
}

/** 底部 4 个主 tab 对应的路由索引 */
private fun tabIndexForRoute(route: String): Int = when (route) {
    Routes.ASSETS -> 1
    Routes.CALENDAR -> 2
    Routes.STATS -> 3
    else -> 0
}

private fun routeForTabIndex(index: Int): String = when (index) {
    1 -> Routes.ASSETS
    2 -> Routes.CALENDAR
    3 -> Routes.STATS
    else -> Routes.HOME
}

@Composable
fun IjiApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

    // 隐私政策门禁：首次使用 / 政策版本升级后必须先显式同意。
    // 与锁屏同构地提前 return —— 未同意时 NavHost / Scaffold / 底栏都不存在，
    // 返回键在 PrivacyConsentScreen 内被接成「不同意并退出」，绕不过去。
    var privacyAgreed by remember {
        mutableStateOf(
            SettingsRepository.getInt(context, SettingsRepository.KEY_PRIVACY_AGREED_VERSION, 0)
                >= privacyPolicyVersion
        )
    }
    if (!privacyAgreed) {
        PrivacyConsentScreen(
            onAgree = { privacyAgreed = true },
            onDecline = { context.exitApp() },
        )
        return
    }

    // 引导状态：FRESH = 存量用户 / 尚未同意隐私；PENDING = 刚同意隐私、引导待完成；
    // DONE = 完成或跳过。读的是 SettingsRepository 内存缓存（Application 已 warmUp），组合期零磁盘 IO。
    // 不用 remember 固定：同意隐私会触发重组，此时要读到刚写入的 PENDING。
    val onboardingState = SettingsRepository.getInt(
        context,
        SettingsRepository.KEY_ONBOARDING_STATE,
        SettingsRepository.ONBOARDING_FRESH,
    )

    // 应用锁状态：只在**进程冷启动**时锁一次（从后台划掉 / 被系统杀掉后再进会走这里）。
    // 回前台不再重新验证——每次切出去看条通知回来都要输密码太折磨（用户要求）。
    var unlocked by remember { mutableStateOf(!AppLockManager.isEnabled(context)) }
    val lockEnabled = remember { AppLockManager.isEnabled(context) }
    val pin = remember { AppLockManager.getPin(context) ?: "" }
    val fingerprintAvailable = remember { AppLockManager.isFingerprintAvailable(context) }
    val fingerprintEnabled = remember { AppLockManager.isFingerprintEnabled(context) }

    if (!unlocked) {
        LockScreen(
            onUnlocked = { unlocked = true },
            pin = pin,
            fingerprintAvailable = fingerprintAvailable,
            fingerprintEnabled = fingerprintEnabled,
            onFingerprintClick = {
                // 平台 BiometricPrompt 只需要 Activity 作 Context，无需 FragmentActivity。
                // 这两处**刻意用 Toast**：此时界面还停在 LockScreen，Scaffold 与 SnackbarHost
                // 尚未渲染，改成 snackbar 会变成"什么都没出现"。
                val act = activity
                if (act != null) {
                    AppLockManager.showFingerprintPrompt(
                        act,
                        onSuccess = { unlocked = true },
                        onError = { msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "指纹解锁不可用，请使用密码",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        return
    }

    val navController = rememberNavController()

    // 通知点击带来的目标路由（见 NavIntentHolder）：导航一次后立即清空，
    // 避免重组/旋转屏幕时重复跳转。Activity 冷启动与前台 onNewIntent 两条路径都会写入。
    val pendingRoute by NavIntentHolder.pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute, onboardingState) {
        val route = pendingRoute ?: return@LaunchedEffect
        // 引导未完成时，通知冷启动不得把首屏顶成目标页；放行后状态变 DONE，这里再消费
        if (onboardingState == SettingsRepository.ONBOARDING_PENDING) return@LaunchedEffect
        navController.navigate(route)
        NavIntentHolder.consume()
    }

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var prefillBill by remember { mutableStateOf<com.iji.app.ai.AiBookkeepingService.BillResult?>(null) }

    // 自动记账：用户点了通知里的「记一笔」→ 打开记账面板并预填（金额/商家/账户已解析好）
    val pendingBill by PendingBillHolder.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingBill) {
        val bill = pendingBill ?: return@LaunchedEffect
        // 记账偏好指定了账本 → 通知账单落到那本账；留空则保持用户当前账本
        val preferred = com.iji.app.accessibility.AutoBookkeepingPrefs.ledger(context)
        if (preferred.isNotBlank()) {
            com.iji.app.data.repository.LedgerSelection.select(preferred)
        }
        prefillBill = bill
        showAddSheet = true
        PendingBillHolder.consume()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME

    // 主 tab 路由（顺序 = 底栏顺序）：决定底栏显隐，也决定 tab 转场的滑动方向
    val tabOrder = TabOrder
    val tabRoutes = TabRouteSet
    // 停在主 tab 区（4 个 tab 都是）→ 底栏常驻；子页面才隐藏
    val showBottomBar = currentRoute in tabRoutes
    // 选中项取返回栈里最靠上的主 tab：停在「我的」等子页时沿用进入前的 tab。
    // 不能直接用 tabIndexForRoute(currentRoute)——它对非 tab 路由回落到 0，
    // 底栏退场动画期间指示器会跳回「账本」。
    val selectedTab = remember(backStackEntry) {
        navController.currentBackStack.value
            .lastOrNull { it.destination.route in tabRoutes }
            ?.destination?.route?.let(::tabIndexForRoute) ?: 0
    }

    // 全站转场（push 400ms / pop 250ms / tab 250ms；低内存设备自动降级）
    val motion = NavMotion.remember(tabOrder)

    // 液态玻璃采样层：录制主内容，供底栏/顶栏玻璃模糊透出。
    // onDraw 先铺页面底色再录内容，让录出来的层**不透明**——
    // 此前录的是「透明底 + 内容」，玻璃糊到的是透明底上的文字：文字滚进底栏/顶栏
    // 玻璃后面会直接透出来（表现为玻璃后面的文字变透明、发虚），且各页进场旧帧
    // 内容不同，模糊观感页页不一。铺底色后文字被糊进底色里，所有页面的玻璃
    // 都糊的是同一套「底色 + 内容」材质。
    val pageBackground = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop(
        onDraw = remember(pageBackground) {
            {
                drawRect(pageBackground)
                drawContent()
            }
        },
    )

    // 玻璃高光（InteractiveHighlight）在 draw 期读全局主题快照，根部同步一次深浅色
    AppThemeSnapshotSync()

    // 采样层录制：tab 平推期间逐帧录制，让底栏玻璃同步糊到「正在移动的内容」；
    // 平推窗口的最后一帧就是落定帧，**不再额外补录一次** —— 页面显示完再渲染一遍玻璃
    // 会表现为玻璃突然刷新，正是要去掉的二次渲染。
    //
    // 平推只改 graphicsLayer 位移：两页内容各组合一次、动画帧只写矩阵，逐帧录制
    // 比旧的「交叉淡入淡出 + 两页半透明叠加」便宜；滚动帧仍靠 epoch 重录，
    // 静止页零录制。进出子页仍是视差 + alpha 两页叠加，保持落定后只补录一次。
    val backdropScrollEpoch = remember { mutableIntStateOf(0) }
    val backdropScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (abs(available.y) > abs(available.x)) backdropScrollEpoch.intValue++
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                backdropScrollEpoch.intValue++
                return available
            }
        }
    }
    // 用普通数组而非 State：mustRecord 在 draw 阶段读，避免读快照状态触发重组。
    val backdropRecordedEpoch = remember { intArrayOf(-1) }
    val backdropSliding = remember { booleanArrayOf(false) }
    // 平推帧 tick：窗口内每帧写入（withFrameNanos 循环），窗口收口时单发一次。
    // mustRecordBackdrop 在 draw 作用域里读它 → draw 订阅该 state → 写入即重放 draw：
    // 这是逐帧续链与「停帧补录」的唯一可靠通道（invalidateDraw() 在 draw 过程中调用
    // 不产生下一帧重绘，2026-11-05 验证）。
    val backdropFrameTick = remember { mutableLongStateOf(0L) }
    // 录制闸门：内容变化帧隔帧录制 + 停帧后补录一帧落定画面。
    // 玻璃模糊 ≥6dp，采样图半帧率刷新肉眼无感（tab 平推同款实测），而整屏重录
    // （1280×2856 ≈ 14.6MB GPU 层/帧）是滚动期间最重的单项开销——滚动帧此前
    // 每帧都录，是「滑动卡顿」的主因之一。隔帧后录制成本减半，静息结果不变。
    val backdropRecordGate = remember { com.iji.app.ui.effects.glass.BackdropRecordGate() }
    val firstRoute = remember { booleanArrayOf(true) }
    LaunchedEffect(currentRoute) {
        // 首帧不启动平推窗口：采样层由 LayerBackdropNode 创建时的 needsRecord 录好
        if (firstRoute[0]) {
            firstRoute[0] = false
            return@LaunchedEffect
        }
        // 路由再变（含切到子页）时先收起窗口，避免 delay 被取消后标志残留 true
        backdropSliding[0] = false
        if (currentRoute in tabRoutes) {
            // 与 slideSpring（约 350~400ms）同节奏并留余量：窗口内逐帧推 tick（驱动
            // draw 重放，录制密度由 mustRecordBackdrop 隔帧控制），落定后窗口收口，
            // 收口后写一帧 tick 补录落定画面，玻璃与页面内容最终一致。
            backdropSliding[0] = true
            var frames = 0
            while (backdropSliding[0] && frames < 40) {
                withFrameNanos { backdropFrameTick.longValue = it }
                frames++
            }
            backdropSliding[0] = false
            // 收口补录：窗口内最后一帧可能轮到「跳」，补一帧落定画面。
            // 写 tick → mustRecordBackdrop draw 期订阅 → 重放 draw → 补录咨询。
            backdropFrameTick.longValue = System.nanoTime()
        } else {
            // 子页视差转场：两页叠加绘制贵，只在落定后补录一次
            delay(400L)
            backdropScrollEpoch.intValue++
        }
    }
    // 滚动停帧补录：滚动（含惯性）停止后 epoch 不再推进，被跳过的最后一帧需要
    // 补一帧落定画面。这里在 epoch 安静 40ms 后写一帧 tick，强制重放 draw 触发补录；
    // 滚动期间每帧都有新 emission，collectLatest 会取消挂起的补录。
    LaunchedEffect(Unit) {
        snapshotFlow { backdropScrollEpoch.intValue }
            .collectLatest {
                delay(40L)
                backdropFrameTick.longValue = System.nanoTime()
            }
    }
    // lambda 引用必须稳定（LayerBackdropElement.equals 按引用比较）
    val mustRecordBackdrop: () -> Boolean = remember(backdropRecordGate) {
        {
            val epoch = backdropScrollEpoch.intValue
            // draw 期订阅 tick：平推窗口逐帧推进、滚动停帧补录单发推进，都靠它续链
            val tick = backdropFrameTick.longValue
            val changed = epoch != backdropRecordedEpoch[0]
            if (changed) backdropRecordedEpoch[0] = epoch
            // 滚动推进 / 平推窗口内 = 内容在变 → 隔帧录制；停帧 → 补录一帧（见闸门）
            backdropRecordGate.shouldRecord(changed || backdropSliding[0])
        }
    }

    // 统一轻量提示（Snackbar）宿主：替代各处 Toast
    val snackbarHostState = rememberSnackbarHostState()
    val snackbarController = rememberSnackbarController(snackbarHostState)

    CompositionLocalProvider(
        LocalSnackbarController provides snackbarController,
        // 全站液态玻璃采样层：弹层卡片（Scaffold popup 层）采样 NavHost 录制内容做真玻璃
        LocalAppBackdrop provides backdrop,
        // 全 App 弹窗返回栈：NavHost 内外所有弹窗共用同一个栈，才能正确判定层级。
        LocalOverlayBackStack provides remember { OverlayBackStack() },
        // 弹窗卡片材质档（NexioSchedule 移植件在 root host 渲染，读不到页面级 local，
        // 必须提供在 Scaffold 之上）：折射开关目前写死开启，并不跟随任何材质档；
        // 「降低动态效果」= 无材质档（玻璃整体关闭，退半透明底）、
        // 预测性返回全局开关直连偏好设置。
        // LocalGlassDialogBlurDp / LocalEdgeLightResolver / LocalScreenCornerRadius
        // 均未注入，卡片走各自默认值。
        com.iji.app.ui.components.dialog.LocalChromeLensEnabled provides true,
        com.iji.app.ui.components.dialog.LocalMaterialNoneEnabled provides com.iji.app.ui.PerfState.reducedEffects,
        com.iji.app.ui.components.dialog.LocalPredictiveBackEnabled provides com.iji.app.ui.util.PredictiveBackSettings.enabled,
    ) {
    top.yukonga.miuix.kmp.basic.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            // 上移避让底部液态玻璃导航栏 + 悬浮记一笔按钮
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.padding(bottom = 96.dp),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 背景铺满（含状态栏区域），否则二级页面顶部会露出窗口底色形成色带。
                .background(MaterialTheme.colorScheme.background)
                // 只垫底部：顶部安全区改由每个目的地在自己的容器里处理（见 ijiRoute），
                // 否则切页第一帧整个 NavHost 就会跳一个状态栏高度。
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            NavHost(
                navController = navController,
                // 同意隐私后落地的第一屏就是引导页（隐私门禁尚未 return 时 NavController 还不存在，
                // 用 startDestination 等价于「同意后跳转」；同意后未完成就被杀进程也能兜住）
                startDestination = if (onboardingState == SettingsRepository.ONBOARDING_PENDING) {
                    Routes.ONBOARDING
                } else {
                    Routes.HOME
                },
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(backdropScrollConnection)
                    // recordKey 用常量：路由变化不额外触发强制录制；tab 平推期间由
                    // mustRecord 逐帧录制，底栏玻璃同步跟随正在移动的内容。
                    // 仍传非 null —— 传 null 会**永久**每帧全屏录制。
                    .layerBackdrop(backdrop, recordKey = Unit, mustRecord = mustRecordBackdrop),
                // 转场统一由 NavMotion 提供：
                // - 主 tab：按底栏顺序整页平推，两页同速锁步、无 alpha，方向跟索引；
                // - push：新页从右推入 + 淡入，旧页左移 1/4 + 淡出（视差）；
                // - pop：方向相反的视差回位；
                // - 低内存设备自动关掉动画；系统「移除动画」由 MotionDurationScale 处理。
                enterTransition = { motion.enter(this) },
                exitTransition = { motion.exit(this) },
                popEnterTransition = { motion.popEnter(this) },
                popExitTransition = { motion.popExit(this) },
            ) {
                ijiRoute(Routes.HOME) {
                    HomeScreen(
                        onNavigateMine = { navController.navigate(Routes.MINE) },
                        onNavigateLedgerManage = { navController.navigate(Routes.LEDGER_MANAGE) },
                    )
                }
                // 首次启动引导：完成 / 跳过都把引导出栈后落主页（onCancel 在首启模式下不会被调用）
                ijiRoute(Routes.ONBOARDING) {
                    OnboardingWizardScreen(
                        reconfigure = false,
                        onFinish = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onCancel = {},
                    )
                }
                // 重配：完成覆盖现有配置后回主页；取消不写任何东西、回引导教程页
                ijiRoute(Routes.ONBOARDING_RECONFIGURE) {
                    OnboardingWizardScreen(
                        reconfigure = true,
                        onFinish = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.ASSETS) { entry ->
                    // 新建账户后要定位高亮：即使 MainViewModel 现在全 App 共享，
                    // 「一次性导航结果」仍放 Navigation 的 savedStateHandle——
                    // 它跟着返回栈走，进程重建后还在，也不会污染全局状态。
                    val highlight by entry.savedStateHandle
                        .getStateFlow<Long?>(KEY_HIGHLIGHT_ACCOUNT, null)
                        .collectAsStateWithLifecycle()
                    AssetsScreen(
                        onNavigateMine = { navController.navigate(Routes.MINE) },
                        onNavigateAddAccount = { navController.navigate(Routes.ADD_ACCOUNT) },
                        onNavigateAccount = { navController.navigate("account_detail/$it") },
                        onNavigateEditAccount = { navController.navigate("edit_account/$it") },
                        onNavigateBorrowLend = {
                            navController.navigate("borrow_lend/${it.name}")
                        },
                        highlightAccountId = highlight,
                        onHighlightConsumed = {
                            entry.savedStateHandle.remove<Long>(KEY_HIGHLIGHT_ACCOUNT)
                        },
                    )
                }
                ijiRoute(Routes.ADD_ACCOUNT) {
                    com.iji.app.ui.assets.AddAccountScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { newId ->
                            // 把新账户 id 交给资产页（返回后再滚动并高亮它）
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(KEY_HIGHLIGHT_ACCOUNT, newId)
                            navController.popBackStack()
                        },
                    )
                }
                ijiRoute(
                    Routes.ACCOUNT_DETAIL,
                    arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
                ) { backStack ->
                    val id = backStack.arguments?.getLong("accountId") ?: 0L
                    com.iji.app.ui.assets.AccountDetailScreen(
                        accountId = id,
                        onBack = { navController.popBackStack() },
                        onNavigateEdit = { navController.navigate("edit_account/$it") },
                        onNavigateReconcile = { name ->
                            navController.navigate(
                                "reconcile?account=${java.net.URLEncoder.encode(name, "UTF-8")}",
                            )
                        },
                    )
                }
                ijiRoute(
                    Routes.EDIT_ACCOUNT,
                    arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
                ) { backStack ->
                    val id = backStack.arguments?.getLong("accountId") ?: 0L
                    // 只把 id 交给页面：账户数据由页面自己从 MainViewModel 取，
                    // 并区分「还没读出来」（加载态）与「账户已不存在」（空态），
                    // 避免首帧把编辑页闪成「添加账户」再退化成新建
                    com.iji.app.ui.assets.AddAccountScreen(
                        onBack = { navController.popBackStack() },
                        accountId = id,
                    )
                }
                ijiRoute(
                    Routes.BORROW_LEND,
                    arguments = listOf(navArgument("direction") { type = NavType.StringType }),
                ) { backStack ->
                    com.iji.app.ui.assets.BorrowLendScreen(
                        initialDirection = backStack.arguments?.getString("direction").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenOrder = { navController.navigate("borrow_lend_detail/$it") },
                    )
                }
                ijiRoute(
                    Routes.BORROW_LEND_DETAIL,
                    arguments = listOf(navArgument("txId") { type = NavType.LongType }),
                ) { backStack ->
                    com.iji.app.ui.assets.BorrowLendDetailScreen(
                        txId = backStack.arguments?.getLong("txId") ?: 0L,
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.CALENDAR) {
                    CalendarScreen(
                        onNavigateMine = { navController.navigate(Routes.MINE) },
                    )
                }
                ijiRoute(Routes.STATS) {
                    StatsScreen(
                        onNavigateMine = { navController.navigate(Routes.MINE) },
                        onNavigateCategory = { categoryId, start, end ->
                            navController.navigate(
                                "stats_category?categoryId=${Uri.encode(categoryId)}" +
                                    "&start=$start&end=$end",
                            )
                        },
                    )
                }
                ijiRoute(
                    Routes.STATS_CATEGORY,
                    arguments = listOf(
                        navArgument("categoryId") { type = NavType.StringType },
                        navArgument("start") { type = NavType.StringType },
                        navArgument("end") { type = NavType.StringType },
                    ),
                ) { backStack ->
                    val args = backStack.arguments
                    fun dateArg(key: String): LocalDate =
                        args?.getString(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                            ?: LocalDate.now()
                    CategoryBillsScreen(
                        categoryId = args?.getString("categoryId").orEmpty(),
                        start = dateArg("start"),
                        end = dateArg("end"),
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.MINE) {
                    MineScreen(
                        onBack = { navController.popBackStack() },
                        onNavigate = { route -> navController.navigate(route) },
                    )
                }
                ijiRoute(
                    Routes.AVATAR_EDIT,
                    arguments = listOf(navArgument("uri") { type = NavType.StringType }),
                ) { backStack ->
                    AvatarEditScreen(
                        // Navigation 已对路径参数做过 URL 解码，这里直接用
                        uri = backStack.arguments?.getString("uri").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.PASSWORD_PROTECT) { PasswordProtectScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.BACKUP_SETTINGS) {
                    BackupSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateCloudBackup = { navController.navigate(Routes.CLOUD_BACKUP) },
                    )
                }
                ijiRoute(Routes.CLOUD_BACKUP) {
                    CloudBackupScreen(onBack = { navController.popBackStack() })
                }
                ijiRoute(Routes.REIMBURSE_MANAGE) {
                    ReimburseManageScreen(onBack = { navController.popBackStack() })
                }
                ijiRoute(Routes.TEMPLATE_MANAGE) {
                    TemplateManageScreen(
                        onBack = { navController.popBackStack() },
                        onCreateTemplate = { navController.navigate("template_edit/new") },
                        onEditTemplate = { id -> navController.navigate("template_edit/$id") },
                    )
                }
                ijiRoute(Routes.TEMPLATE_EDIT) { entry ->
                    TemplateEditScreen(
                        templateId = entry.arguments?.getString("templateId").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.CATEGORY_MANAGE) {
                    CategoryManageScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateEdit = { categoryId, type, parentId, ledger ->
                            navController.navigate(
                                Routes.CATEGORY_EDIT
                                    .replace("{categoryId}", Uri.encode(categoryId))
                                    .replace("{type}", type.name)
                                    .replace("{parentId}", Uri.encode(parentId))
                                    .replace("{ledger}", Uri.encode(ledger)),
                            )
                        },
                    )
                }
                ijiRoute(
                    Routes.CATEGORY_EDIT,
                    arguments = listOf(
                        navArgument("categoryId") { type = NavType.StringType },
                        navArgument("type") { type = NavType.StringType },
                        navArgument("parentId") { type = NavType.StringType },
                        navArgument("ledger") { type = NavType.StringType },
                    ),
                ) { backStack ->
                    val args = backStack.arguments
                    CategoryEditScreen(
                        categoryId = args?.getString("categoryId").orEmpty(),
                        type = runCatching {
                            TransactionType.valueOf(args?.getString("type").orEmpty())
                        }.getOrDefault(TransactionType.EXPENSE),
                        parentId = args?.getString("parentId").orEmpty(),
                        ledger = args?.getString("ledger").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.TAG_MANAGE) {
                    TagManageScreen(
                        onBack = { navController.popBackStack() },
                        // 标签名可能含空格 / # / & 等字符，必须编码后放进 query 参数；
                        // Navigation 解析参数时会自动解码，页面里直接用 getString 即可
                        onOpenBills = { tag ->
                            navController.navigate("tag_bills?tag=${Uri.encode(tag)}")
                        },
                    )
                }
                ijiRoute(
                    Routes.TAG_BILLS,
                    arguments = listOf(navArgument("tag") { type = NavType.StringType }),
                ) { backStack ->
                    TagBillsScreen(
                        tag = backStack.arguments?.getString("tag").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.LEDGER_MANAGE) {
                    LedgerManageScreen(onBack = { navController.popBackStack() })
                }
                ijiRoute(Routes.RECURRING_MANAGE) {
                    RecurringManageScreen(onBack = { navController.popBackStack() })
                }
                ijiRoute(
                    Routes.RECONCILE,
                    arguments = listOf(
                        navArgument("account") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { backStack ->
                    com.iji.app.ui.mine.ReconcileScreen(
                        accountName = backStack.arguments?.getString("account")
                            ?: com.iji.app.ui.mine.RECONCILE_ALL_ACCOUNTS,
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.BUDGET) {
                    BudgetScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateCategoryBudget = { navController.navigate(Routes.CATEGORY_BUDGET) },
                        onNavigateAlertHistory = { navController.navigate(Routes.BUDGET_ALERT_HISTORY) },
                    )
                }
                ijiRoute(Routes.BUDGET_ALERT_HISTORY) {
                    com.iji.app.ui.mine.BudgetAlertScreen(onBack = { navController.popBackStack() })
                }
                ijiRoute(Routes.CATEGORY_BUDGET) {
                    CategoryBudgetScreen(onBack = { navController.popBackStack() })
                }
                ijiRoute(Routes.FONT_SIZE) { FontSizeScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.AUTO_BOOKKEEPING) {
                    AutoBookkeepingScreen(
                        onBack = { navController.popBackStack() },
                        onNavigate = { route -> navController.navigate(route) },
                    )
                }
                ijiRoute(Routes.SMART_CAPTURE) {
                    com.iji.app.ui.mine.SmartCaptureScreen(
                        onBack = { navController.popBackStack() },
                        // 详细编辑：带预填弹「记一笔」面板，不退出本页
                        onConfirmDetailEdit = { result ->
                            prefillBill = result
                            showAddSheet = true
                        },
                    )
                }
                ijiRoute(Routes.NOTIFICATION_RULES) {
                    com.iji.app.ui.mine.NotificationRulesScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.PREFERENCES) {
                    PreferencesScreen(
                        onBack = { navController.popBackStack() },
                        onFontSize = { navController.navigate(Routes.FONT_SIZE) },
                    )
                }
                ijiRoute(Routes.PERMISSION_CHECK) {
                    PermissionCheckScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateCrashLog = { navController.navigate(Routes.CRASH_LOG) },
                        onNavigateChangelog = { navController.navigate(Routes.CHANGELOG) },
                        onNavigateLicense = { navController.navigate(Routes.LICENSE) },
                        onNavigatePrivacy = { navController.navigate(Routes.PRIVACY) },
                    )
                }
                ijiRoute(Routes.DONATION) { DonationScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.CRASH_LOG) { CrashLogScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.CHANGELOG) { ChangelogScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.GUIDE_TUTORIAL) {
                    GuideTutorialScreen(
                        onBack = { navController.popBackStack() },
                        onReconfigure = { navController.navigate(Routes.ONBOARDING_RECONFIGURE) },
                    )
                }
                ijiRoute(Routes.LICENSE) { LicenseScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.PRIVACY) { PrivacyPolicyScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.BILL_IMPORT) {
                    BillImportScreen(
                        onBack = { navController.popBackStack() },
                        onNavigatePreview = { navController.navigate(Routes.BILL_IMPORT_PREVIEW) },
                        onNavigateHistory = { navController.navigate(Routes.IMPORT_HISTORY) },
                    )
                }
                ijiRoute(Routes.BILL_IMPORT_PREVIEW) {
                    com.iji.app.ui.mine.BillImportPreviewScreen(onBack = { navController.popBackStack() })
                }
                ijiRoute(Routes.IMPORT_HISTORY) {
                    com.iji.app.ui.mine.ImportHistoryScreen(onBack = { navController.popBackStack() })
                }
                ijiRoute(Routes.SCREENSHOT_IMPORT) {
                    com.iji.app.ui.mine.ScreenshotImportScreen(onBack = { navController.popBackStack() })
                }

                // 「提示词与规则」总览 → 二级页（提示词编辑 / 本地规则编辑）
                ijiRoute(Routes.PROMPTS) {
                    com.iji.app.ui.ai.PromptRuleCenterScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPrompt = { kind -> navController.navigate("prompt_edit/${kind.key}") },
                        onOpenRule = { id -> navController.navigate("local_rule_edit/$id") },
                        onCreateRule = { navController.navigate("local_rule_edit/new") },
                    )
                }
                ijiRoute(Routes.PROMPT_EDIT) { entry ->
                    com.iji.app.ui.ai.PromptEditScreen(
                        kindKey = entry.arguments?.getString("kind").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.LOCAL_RULE_EDIT) { entry ->
                    com.iji.app.ui.ai.LocalRuleEditScreen(
                        ruleId = entry.arguments?.getString("ruleId").orEmpty(),
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.AI) {
                    com.iji.app.ui.ai.AiBookkeepingScreen(
                        onBack = { navController.popBackStack() },
                        onNavigatePrompts = { navController.navigate(Routes.PROMPTS) },
                    )
                }
            }

            // 液态玻璃底部导航（4 tab + 加号）：与 NavHost 同级、悬浮于内容之上，
            // 不在 NavHost 的 AnimatedContentScope 内 —— 切 tab 的整页平推带不走它，
            // 也不会触发它自己的动画，因此 4 个 tab 之间位置恒定。
            // 只有进出主 tab 区（如进入「我的」等子页）才单独滑出 / 滑入：
            // 纯位移、不做透明度混合，位移弹簧与 NavMotion 的 push/pop 同组，同起同落。
            //
            // 加号圆钮是底栏形状的一部分（LiquidBottomTrailing，胶囊 ∪ 圆 ∪ 融球颈同一条轮廓），
            // 不再是贴在旁边的一颗独立按钮。
            AnimatedVisibility(
                visible = showBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(motion.slideSpring()) { it },
                exit = slideOutVertically(motion.slideSpring()) { it },
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .height(56.dp),
                ) {
                    LiquidBottomTabs(
                        // 用 remember 固定 lambda 实例：否则每次重组都换新引用，
                        // 内部 snapshotFlow 会反复重启、指示器被冲回起点
                        selectedTabIndex = remember(selectedTab) { { selectedTab } },
                        // 组件在手势松手与外部路由同步两条路径都会回调 onTabSelected，
                        // 这里做去重：重复点当前 tab 不再 navigate 一次
                        //（navigate 带着 popUpTo(saveState)+restoreState 会把目标页拆掉重建，
                        // 表现为「点 tab 顿一下」）。
                        onTabSelected = remember(navController) {
                            var reportedIndex = Int.MIN_VALUE
                            { index: Int ->
                                if (index != reportedIndex) {
                                    reportedIndex = index
                                    navigateToTab(navController, routeForTabIndex(index))
                                }
                            }
                        },
                        backdrop = backdrop,
                        tabsCount = 4,
                        containerHeight = 56.dp,
                        trailing = LiquidBottomTrailing(
                            icon = MiuixIcons.Demibold.Add,
                            contentDescription = "记一笔",
                            onClick = { showAddSheet = true },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // 四个 tab 统一用 Miuix 图标（Regular 变体，
                        // 即 MiuixIcons.xxx 不带 Light/Normal/Medium/Demibold 前缀）。
                        // 官方 miuix-icons 0.9.4 已内置这 4 个，直接引用即可，**不要**把源码复制进工程。
                        LiquidBottomTab { BottomTabContent("账本", MiuixIcons.ListView, selectedTab == 0) }
                        LiquidBottomTab { BottomTabContent("资产", MiuixIcons.BankCards, selectedTab == 1) }
                        LiquidBottomTab { BottomTabContent("日历", MiuixIcons.Months, selectedTab == 2) }
                        LiquidBottomTab { BottomTabContent("统计", MiuixIcons.Notes, selectedTab == 3) }
                    }
                }
            }
        }
        // 预测性返回动画总闸（偏好设置「预测性返回动画」开关）：
        // NavHost 自带预测性返回转场（手势进度 seekTo 驱动页面回退，无配置项可关），
        // 开关关闭时由本 handler 在 NavHost 之前拦下手势（组合顺序在 NavHost 之后 =
        // 注册更晚 = 优先级更高），进度被吞掉、页面不再跟手；松手提交时正常出栈，
        // 仍播放常规 pop 转场——与 PredictiveBackSettings「关闭行为不变、无跟手动画」的语义一致。
        //
        // 优先级链（同一 OnBackPressedDispatcher，后注册者先响应；Miuix Scaffold 的 popup 槽
        // 最先 subcompose，故弹窗内 handler 优先级最低，弹窗打开时实际由 OverlayBackHandler 兜底）：
        //   OverlayBackHandler（栈非空时启用） > 本闸（栈空、可出栈、开关关闭时启用）
        //   > NavHost 内置 handler（可出栈时启用） > 弹窗自身 handler（兜底）
        // 本闸与 OverlayBackHandler 按返回栈是否为空互斥启用，二者不会同时生效。
        PredictiveBackHandler(
            enabled = !PredictiveBackSettings.enabled &&
                LocalOverlayBackStack.current.isEmpty &&
                backStackEntry != null &&
                navController.previousBackStackEntry != null,
        ) { progress ->
            try {
                progress.collect { /* 吞掉进度：不 seek、不驱动任何动画 */ }
                navController.popBackStack()
            } catch (e: CancellationException) {
                // 手势取消：本闸没有产生任何动画状态，无需复位
            }
        }

        // 全 App 返回键总入口（见 OverlayBackHandler）：栈里有弹窗时只关闭栈顶那一个。
        // 「记一笔」抽屉自身不拦截返回键（Miuix OverlayBottomSheet 未处理，过去会直接退回桌面），
        // 现在由 OverlayBackStack 统一兜底；抽屉里再开选择器时，选择器更晚打开、位于栈顶，
        // 返回键先关选择器、再关抽屉。全部关闭后 enabled=false，返回键交还系统默认行为。
        OverlayBackHandler()

        // 应用更新：A 启动即检查（静默，每天一次）+ A/B 共用的更新弹窗。
        // 必须留在 Scaffold 内容 lambda 内，IjiDialog 的弹层宿主由它提供。
        StartupUpdateCheck()
        UpdateDialog()

        AddTransactionSheet(
            show = showAddSheet,
            onDismiss = { showAddSheet = false; prefillBill = null },
            prefillType = prefillBill?.let { if (it.type == "income") com.iji.app.data.model.TransactionType.INCOME else com.iji.app.data.model.TransactionType.EXPENSE },
            prefillCategoryId = prefillBill?.categoryHint?.let { catName ->
                (com.iji.app.data.repository.CategoryCatalog.expenses + com.iji.app.data.repository.CategoryCatalog.incomes)
                    .firstOrNull { it.name == catName.trim() }?.id
            },
            // 名称对不上时由面板提示「未识别分类」，不要静默落到默认分类
            prefillCategoryHint = prefillBill?.categoryHint,
            prefillAmountCents = prefillBill?.amountCents,
            prefillNote = prefillBill?.note,
            prefillAccount = prefillBill?.accountHint,
            prefillTags = prefillBill?.tags,
        )

    }
    }
}

/**
 * 底栏图标的视觉下移量。
 *
 * `LiquidBottomTab` 内部 Column 是 `Arrangement.spacedBy(2.dp, CenterVertically)`，
 * 即图标与文字本来就有 2dp 间距。这里用 `offset` 再让图标下移一点，使其**更贴近文字**；
 * `offset` 只做位移、不占布局空间，因此不会破坏 tab 内容的整体垂直居中。
 * 想更紧/更松直接调这个值（0.dp = 恢复原位）。
 */
private val BOTTOM_TAB_ICON_OFFSET_Y = 1.5.dp

/**
 * 单个 tab 的内容：图标 + 文字，颜色跟随主题黑/白（与液态玻璃胶囊一致）。
 *
 * 选中为实色、未选中降到 45% 透明度以区分状态。[LiquidBottomTab] 不向 content 暴露选中态，
 * 由调用方按当前路由算出 selected 后传入。
 */
@Composable
private fun ColumnScope.BottomTabContent(
    label: String,
    icon: ImageVector,
    selected: Boolean,
) {
    val base = if (isAppDarkTheme()) Color.White else Color.Black
    val alpha = if (selected) 1f else 0.45f
    Icon(
        icon,
        contentDescription = label,
        tint = base.copy(alpha = alpha),
        modifier = Modifier
            .size(22.dp)
            .offset(y = BOTTOM_TAB_ICON_OFFSET_Y),
    )
    Text(
        label,
        color = base.copy(alpha = alpha),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
    )
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
