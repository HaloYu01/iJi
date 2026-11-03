package com.iji.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.iji.app.ui.components.OnboardingDialog
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
import com.iji.app.ui.mine.CrashLogScreen
import com.iji.app.ui.mine.ChangelogScreen
import com.iji.app.ui.mine.LicenseScreen
import com.iji.app.ui.mine.PrivacyPolicyScreen
import com.iji.app.ui.mine.BackupSettingsScreen
import com.iji.app.ui.mine.BillImportScreen
import com.iji.app.ui.mine.CloudBackupScreen
import com.iji.app.ui.mine.CategoryManageScreen
import com.iji.app.ui.mine.CategoryEditScreen
import com.iji.app.data.model.TransactionType
import com.iji.app.ui.mine.TagManageScreen
import com.iji.app.ui.mine.TemplateManageScreen
import com.iji.app.ui.mine.TemplateEditScreen
import com.iji.app.ui.budget.BudgetDetailScreen
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
import com.iji.app.ui.components.navbar.GlassBottomNavItem
import com.iji.app.ui.components.navbar.GlassBottomNavBar
import com.iji.app.ui.components.navbar.GlassAddButton
import com.iji.app.ui.util.isAppDarkTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.icon.MiuixIcons
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
    const val SCREEN_CAPTURE = "screen_capture"          // 识屏与授权（Shizuku / 无障碍 / 音量键）
    const val NOTIFICATION_RULES = "notification_rules"  // 通知识别规则（过滤关键词 + 支持来源）
    const val BOOKKEEPING_PREFS = "bookkeeping_prefs"    // 记账偏好（落账账本 / 备注格式 / 去重窗口）
    const val PASSWORD_PROTECT = "password_protect"
    const val CATEGORY_MANAGE = "category_manage"
    const val CATEGORY_EDIT =
        "category_edit?categoryId={categoryId}&type={type}&parentId={parentId}&ledger={ledger}"
    const val TAG_MANAGE = "tag_manage"
    const val BUDGET = "budget"
    const val BUDGET_DETAIL = "budget_detail/{budgetId}"  // 预算详情（通知点击落地页）
    const val CATEGORY_BUDGET = "category_budget"  // 分类预算管理
    const val TEMPLATE_MANAGE = "template_manage"  // 记账模板管理
    const val TEMPLATE_EDIT = "template_edit/{templateId}"  // 记账模板编辑（templateId = new 表示新建）
    const val REIMBURSE_MANAGE = "reimburse_manage"  // 应收 / 垫付
    const val FONT_SIZE = "font_size"
    const val AUTO_BOOKKEEPING = "auto_bookkeeping"
    const val PREFERENCES = "preferences"
    const val PERMISSION_CHECK = "permission_check"  // 权限检验（通知 / 后台 / 电池优化 / Shizuku / 无障碍）
    const val ABOUT = "about"
    const val CRASH_LOG = "crash_log"
    const val CHANGELOG = "changelog"        // 更新日志
    const val LICENSE = "license"            // 开源许可
    const val PRIVACY = "privacy"            // 隐私政策
    const val BILL_IMPORT = "bill_import"
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
 * 不能用 MainViewModel 上的字段：它在 NavHost 里是每个目的地各一份实例，
 * 添加页写进去的状态资产页根本读不到。
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
    Routes.BUDGET,
    Routes.BILL_IMPORT,
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
    Routes.CHANGELOG,
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
    Routes.SCREEN_CAPTURE,
    Routes.NOTIFICATION_RULES,
    Routes.BOOKKEEPING_PREFS,
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
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        navController.navigate(route)
        NavIntentHolder.consume()
    }

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var prefillBill by remember { mutableStateOf<com.iji.app.ai.AiBookkeepingService.BillResult?>(null) }

    // 「我的 → 新手教程」等页面也能打开记账面板：它们拿不到这里的 showAddSheet，
    // 用 AddSheetRequestHolder 中转（同 NavIntentHolder 的一次性信号）。
    val addSheetRequested by AddSheetRequestHolder.requested.collectAsStateWithLifecycle()
    LaunchedEffect(addSheetRequested) {
        if (!addSheetRequested) return@LaunchedEffect
        showAddSheet = true
        AddSheetRequestHolder.consume()
    }

    // 首次引导：只在没看过时弹一次（标记写 DataStore）
    var showOnboarding by remember {
        mutableStateOf(
            !com.iji.app.data.repository.SettingsRepository.getBoolean(
                context,
                com.iji.app.data.repository.SettingsRepository.KEY_ONBOARDING_DONE,
                false,
            )
        )
    }
    fun finishOnboarding() {
        showOnboarding = false
        com.iji.app.data.repository.SettingsRepository.setBoolean(
            context,
            com.iji.app.data.repository.SettingsRepository.KEY_ONBOARDING_DONE,
            true,
        )
    }

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

    // 液态玻璃采样层：录制主内容，供底栏玻璃模糊透出
    val backdrop = rememberLayerBackdrop()

    // 统一轻量提示（Snackbar）宿主：替代各处 Toast
    val snackbarHostState = rememberSnackbarHostState()
    val snackbarController = rememberSnackbarController(snackbarHostState)

    CompositionLocalProvider(
        LocalSnackbarController provides snackbarController,
        // 全 App 弹窗返回栈：NavHost 内外所有弹窗共用同一个栈，才能正确判定层级。
        LocalOverlayBackStack provides remember { OverlayBackStack() },
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
                startDestination = Routes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    // 采样层节流：recordKey 传 null 时 layerBackdrop 每帧全屏录制，
                    // 切 tab 时与页面重组 + 底栏 vibrancy/blur/lens shader 叠加，是切换卡顿的主因。
                    // 传 currentRoute 后只在切页（路由变化）时重录，静止时不录、底栏也不重采不重跑 shader。
                    .layerBackdrop(backdrop, recordKey = currentRoute),
                // 转场统一由 NavMotion 提供：
                // - 主 tab：按底栏顺序 250ms 横向平移，方向跟索引；
                // - push：新页从右推入、旧页同步向左推走，400ms；
                // - pop：当前页向右推走、上一页从左侧同步回位，250ms；
                // - 每次转场两页共用同一时长与缓动（lockstep，见 NavMotion 注释），
                //   全程只用位移（不做 alpha 混合），低内存设备自动缩短/关掉 tab 动画。
                enterTransition = { motion.enter(this) },
                exitTransition = { motion.exit(this) },
                popEnterTransition = { motion.popEnter(this) },
                popExitTransition = { motion.popExit(this) },
            ) {
                ijiRoute(Routes.HOME) {
                    HomeScreen(
                        onNavigateMine = { navController.navigate(Routes.MINE) },
                        onNavigateBudgetDetail = { navController.navigate("budget_detail/$it") },
                        onNavigateLedgerManage = { navController.navigate(Routes.LEDGER_MANAGE) },
                    )
                }
                ijiRoute(Routes.ASSETS) { entry ->
                    // 新建账户后要定位高亮：MainViewModel 是**每个目的地各一份实例**，
                    // 状态不能放在它上面（写的是另一份）。用 Navigation 的
                    // savedStateHandle 把「新建的账户 id」回传给资产页最稳。
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
                        onAddBill = { showAddSheet = true },
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
                        onNavigateBillImport = { navController.navigate(Routes.BILL_IMPORT) },
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
                ijiRoute(Routes.TAG_MANAGE) { TagManageScreen(onBack = { navController.popBackStack() }) }
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
                ijiRoute(
                    Routes.BUDGET_DETAIL,
                    arguments = listOf(navArgument("budgetId") { type = NavType.LongType }),
                ) { backStack ->
                    BudgetDetailScreen(
                        budgetId = backStack.arguments?.getLong("budgetId") ?: 0L,
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.FONT_SIZE) { FontSizeScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.AUTO_BOOKKEEPING) {
                    AutoBookkeepingScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateScreenCapture = { navController.navigate(Routes.SCREEN_CAPTURE) },
                        onNavigateNotificationRules = { navController.navigate(Routes.NOTIFICATION_RULES) },
                        onNavigatePrefs = { navController.navigate(Routes.BOOKKEEPING_PREFS) },
                    )
                }
                ijiRoute(Routes.BOOKKEEPING_PREFS) {
                    com.iji.app.ui.mine.BookkeepingPrefsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                ijiRoute(Routes.SCREEN_CAPTURE) {
                    com.iji.app.ui.mine.ScreenCaptureSettingsScreen(
                        onBack = { navController.popBackStack() },
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
                        onPermissionCheck = { navController.navigate(Routes.PERMISSION_CHECK) },
                    )
                }
                ijiRoute(Routes.PERMISSION_CHECK) {
                    PermissionCheckScreen(
                        onBack = { navController.popBackStack() },
                        onOpenScreenCapture = { navController.navigate(Routes.SCREEN_CAPTURE) },
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
                ijiRoute(Routes.CRASH_LOG) { CrashLogScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.CHANGELOG) { ChangelogScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.LICENSE) { LicenseScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.PRIVACY) { PrivacyPolicyScreen(onBack = { navController.popBackStack() }) }
                ijiRoute(Routes.BILL_IMPORT) {
                    BillImportScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateScreenshotImport = { navController.navigate(Routes.SCREENSHOT_IMPORT) },
                    )
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
                        onConfirm = { result ->
                            prefillBill = result
                            navController.popBackStack()
                            showAddSheet = true
                        },
                    )
                }
            }

            // 液态玻璃底部导航（4 tab + 加号）：与 NavHost 同级、悬浮于内容之上，
            // 不在 NavHost 的 AnimatedContentScope 内 —— 切 tab 的横向滑动带不走它，
            // 也不会触发它自己的动画，因此 4 个 tab 之间位置恒定。
            // 只有进出主 tab 区（如进入「我的」等子页）才单独滑动淡出，
            // 时长与 NavMotion 的 push(400ms) / pop(250ms) 对齐，与页面转场同起同落。
            AnimatedVisibility(
                visible = showBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(NavMotion.POP_MS)) { it } + fadeIn(tween(NavMotion.POP_MS)),
                exit = slideOutVertically(tween(NavMotion.PUSH_MS)) { it } + fadeOut(tween(NavMotion.PUSH_MS)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassBottomNavBar(
                        // 用 remember 固定 lambda 实例：否则每次重组都换新引用，
                        // 底栏内部（drawBackdrop 的 effects 比对是引用相等）会重建整条模糊/RenderEffect 链
                        selectedTabIndex = remember(selectedTab) { { selectedTab } },
                        onTabSelected = remember(navController) {
                            { index: Int -> navigateToTab(navController, routeForTabIndex(index)) }
                        },
                        backdrop = backdrop,
                        tabsCount = 4,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        containerHeight = 56.dp,
                        highlightHeight = 48.dp,
                        selectorHeight = 48.dp,
                    ) {
                        // 四个 tab 统一用 Miuix 图标（Regular 变体，
                        // 即 MiuixIcons.xxx 不带 Light/Normal/Medium/Demibold 前缀）。
                        // 官方 miuix-icons 0.9.4 已内置这 4 个，直接引用即可，**不要**把源码复制进工程。
                        GlassBottomNavItem(index = 0) { BottomTabContent("账本", MiuixIcons.ListView, selectedTab == 0) }
                        GlassBottomNavItem(index = 1) { BottomTabContent("资产", MiuixIcons.BankCards, selectedTab == 1) }
                        GlassBottomNavItem(index = 2) { BottomTabContent("日历", MiuixIcons.Months, selectedTab == 2) }
                        GlassBottomNavItem(index = 3) { BottomTabContent("统计", MiuixIcons.Notes, selectedTab == 3) }
                    }
                    Spacer(Modifier.width(12.dp))
                    GlassAddButton(
                        onClick = { showAddSheet = true },
                        backdrop = backdrop,
                    )
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

        // 首次引导：解锁后弹一次（IjiDialog 必须在 Scaffold 子树内）
        OnboardingDialog(
            show = showOnboarding,
            onDismiss = ::finishOnboarding,
            onAddFirst = {
                finishOnboarding()
                showAddSheet = true
            },
            onImport = {
                finishOnboarding()
                navController.navigate(com.iji.app.ui.Routes.BILL_IMPORT)
            },
            onBackup = {
                finishOnboarding()
                navController.navigate(com.iji.app.ui.Routes.BACKUP_SETTINGS)
            },
            onFinish = ::finishOnboarding,
        )
    }
    }
}

/**
 * 底栏图标的视觉下移量。
 *
 * `GlassBottomNavItem` 内部 Column 是 `Arrangement.spacedBy(2.dp, CenterVertically)`，
 * 即图标与文字本来就有 2dp 间距。这里用 `offset` 再让图标下移一点，使其**更贴近文字**；
 * `offset` 只做位移、不占布局空间，因此不会破坏 tab 内容的整体垂直居中。
 * 想更紧/更松直接调这个值（0.dp = 恢复原位）。
 */
private val BOTTOM_TAB_ICON_OFFSET_Y = 1.5.dp

/**
 * 单个 tab 的内容：图标 + 文字，颜色跟随主题黑/白（与液态玻璃胶囊一致）。
 *
 * 选中为实色、未选中降到 45% 透明度以区分状态。[GlassBottomNavItem] 不向 content 暴露选中态，
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
    // TODO(bisect-restore): 诊断临时版本——去掉 popUpTo/saveState/restoreState
    navController.navigate(route) {
        launchSingleTop = true
    }
}
