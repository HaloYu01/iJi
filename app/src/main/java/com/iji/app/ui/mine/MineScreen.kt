package com.iji.app.ui.mine
import com.iji.app.ui.components.LocalSnackbarController

import com.iji.app.lock.AppLockManager
import com.iji.app.ui.Routes
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.Radius

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import com.iji.app.ui.components.topbar.LiquidTopBarButton
import com.iji.app.ui.components.topbar.topBarContentInset
import com.iji.app.ui.components.topbar.rememberTopBarScrollBehavior
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiSettingsDivider
import com.iji.app.ui.components.RowLeadingIcon
import com.iji.app.ui.components.IjiTopBar
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.ijiTopBarRecordHeight
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ui.components.IjiTopBarHeight
import com.iji.app.ui.components.IjiTrailingIcon
import com.iji.app.ui.components.ijiTopBarSafeTopPadding
import com.iji.app.ui.theme.TextSecondary
import com.iji.app.ui.theme.AppleSeparator
import com.iji.app.ui.theme.AppleGreen
import com.iji.app.ui.theme.AppleIndigo
import com.iji.app.ui.theme.AppleOrange
import com.iji.app.ui.theme.ApplePurple
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.MiuixPrimaryDark
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Weeks

/**
 * 我的页面（设置主页）
 * 布局：顶部返回+标题 → 头像+昵称 → 分组卡片列表
 */
@Composable
fun MineScreen(
    onBack: () -> Unit = {},
    /**
     * 统一按路由跳转：本页只负责声明「去哪里」，真正的导航由 AppRoot 注入。
     * P1 把入口从 6 个增加到 14 个，具名回调会膨胀到无法维护，故改为传路由常量。
     */
    onNavigate: (route: String) -> Unit = {},
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    // 读取本地已保存的个人资料（幂等，可重复调用）
    ProfileState.ensureInit(context)

    var showNicknameDialog by remember { mutableStateOf(false) }
    var nicknameInput by remember { mutableStateOf("") }

    // 密码保护行的右侧状态摘要：不点进二级页也能看到是否开启
    val lockSummary = if (AppLockManager.isEnabled(context)) "已开启" else "未开启"

    // 头像图片选择：Photo Picker 选图后经路由进入「调整头像」独立子页（缩放/移动 → 确定裁剪保存），
    // 不直接写 URI —— 裁剪结果落盘到应用私有目录，不依赖相册 URI 的持久授权
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // uri 含特殊字符，必须编码后再作为导航参数，否则路由解析会截断
        onNavigate(Routes.AVATAR_EDIT.replace("{uri}", Uri.encode(uri.toString())))
    }

    // 沉浸式：与首页 HomeScreen 完全同一套结构
    // - 背景铺满整屏（含状态栏与挖孔区），状态栏区域与页面背景自然融合；
    // - 顶栏是浮层（IjiTopBar：AGSL 渐进模糊采样下方滚过的内容），本身不占位；
    // - 顶部高度 = 安全区 + 折叠态 52dp + 大标题展开量（topBarContentInset），与首页齐平。
    val topBarSurface = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(topBarSurface)
        drawContent()
    }
    val scrollBehavior = rememberTopBarScrollBehavior()
    val listState = rememberLazyListState()
    // 顶栏模糊只在滚动中重录采样层。原来 layerBackdrop 没传 recordKey，
    // 静止时仍会每帧全屏录制一整层（约 14MB/帧），是设置页滑动掉帧的主要来源。
    val topBarRecordGate = remember { com.iji.app.ui.effects.glass.BackdropRecordGate() }
    val topBarMustRecord = remember(listState) { { topBarRecordGate.shouldRecord(listState.isScrollInProgress) } }

    // 屏幕尺寸 / 方向适配：平板（≥600dp）与横屏加宽内容两侧留白，
    // 避免大屏上内容被拉得过宽，也避免横屏时内容被侧边挖孔压住
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val extraHorizontal = when {
        isTablet -> 40.dp
        isLandscape -> 56.dp
        else -> 0.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(topBarSurface),
    ) {
        // 必须用 LazyColumn（与首页一致），不能用 Column + verticalScroll：
        // topBarContentInset 内部做 constraints.copy(maxHeight = maxHeight - extraPx)，
        // 而 verticalScroll 给子节点的是**无界**高度约束（maxHeight = Int.MAX_VALUE），
        // 相减后超出 Constraints 可表示范围会抛
        // IllegalArgumentException: Can't represent a width of ... and height of 2147483527 —— 实测「我的」页一打开就崩。
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .topBarContentInset(scrollBehavior)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .layerBackdrop(backdrop, recordKey = Unit, mustRecord = topBarMustRecord, recordHeight = ijiTopBarRecordHeight()),
            contentPadding = PaddingValues(
                top = ijiTopBarContentPadding(),
                start = extraHorizontal,
                end = extraHorizontal,
                bottom = 32.dp,
            ),
            // 组间统一 12dp（「上一张卡片底边 → 下一个小标题文字」的留白，实测约 17dp）。
            // 注意：小标题与**自己这张卡片**之间的间距不在这里，而是由 SettingsGroup 里
            // SmallTitle 的 insideMargin.bottom 给（见那里的注释），两者别混。
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 头像 + 昵称：点击即可修改
            // - 头像：点击头像本身调起系统 Photo Picker → 「调整头像」子页缩放/移动/确定
            // - 昵称：点击弹 IjiDialog 输入
            // 两者都写 ProfileState（mutableStateOf），改完本处立即重组，无需手动刷新
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalDimens.current.screenPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 整块可点击（无右下角角标），点击调起选图
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                avatarLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AvatarImage(uri = ProfileState.avatarUri, size = 80.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = ProfileState.nickname.ifBlank { "点击设置昵称" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (ProfileState.nickname.isBlank()) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                nicknameInput = ProfileState.nickname
                                showNicknameDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    // 头像区与首个分组之间留出适度呼吸空间（组间距 12dp 之外再加一点）
                    Spacer(Modifier.height(8.dp))
                }
            }

            // 分组重排（P1 分组重排的后续收口）：
            // 1) 「时间设置」（仅 3 项）并入「偏好设置」子页；「数据管理」并入「备份与恢复」并改名「数据与备份」
            //    —— 两个低频子页不再单独占一行；
            // 2) 「密码保护」从「其他」挪进「偏好」（安全偏好），「其他」改名「关于」，不再是大杂烩；
            // 3) 「周期账单」归入「预算与账单」（它生成账单，不是识别类自动化），「自动化」只剩
            //    自动记账 / AI 记账这对识别类功能，语义更干净。
            //
            // 左图标：颜色按**分组**给（6 组 6 色），不是逐行一色 ——
            // 记账=主色蓝 / 预算与账单=靛 / 自动化=紫 / 数据与备份=绿 / 偏好=橙 / 关于=浅蓝。
            // 换图标前先按 `reference_miuix_icon_enum` 的方式枚举可用名，别猜。
            item {
                SettingsGroup(
                    title = "记账",
                    accent = MiuixPrimary,
                    modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding),
                ) {
                    SettingsRow(MiuixIcons.GridView, "分类管理") { onNavigate(Routes.CATEGORY_MANAGE) }
                    IjiSettingsDivider()
                    SettingsRow(MiuixIcons.BankCards, "账本管理") { onNavigate(Routes.LEDGER_MANAGE) }
                    IjiSettingsDivider()
                    SettingsRow(MiuixIcons.Copy, "记账模板") { onNavigate(Routes.TEMPLATE_MANAGE) }
                    IjiSettingsDivider()
                    SettingsRow(MiuixIcons.Pin, "标签管理") { onNavigate(Routes.TAG_MANAGE) }
                }
            }

            item {
                SettingsGroup(
                    title = "预算与账单",
                    accent = AppleIndigo,
                    modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding),
                ) {
                    SettingsRow(MiuixIcons.Months, "预算") { onNavigate(Routes.BUDGET) }
                    IjiSettingsDivider()
                    SettingsRow(MiuixIcons.Weeks, "周期账单") { onNavigate(Routes.RECURRING_MANAGE) }
                    IjiSettingsDivider()
                    SettingsRow(MiuixIcons.Send, "应收 / 垫付") { onNavigate(Routes.REIMBURSE_MANAGE) }
                }
            }

            item {
                SettingsGroup(
                    title = "自动化",
                    accent = ApplePurple,
                    modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding),
                ) {
                    SettingsRow(MiuixIcons.Refresh, "自动记账") { onNavigate(Routes.AUTO_BOOKKEEPING) }
                    IjiSettingsDivider()
                    SettingsRow(MiuixIcons.Notes, "智能识别") { onNavigate(Routes.SMART_CAPTURE) }
                    IjiSettingsDivider()
                    SettingsRow(Icons.Default.AutoAwesome, "AI 记账") { onNavigate(Routes.AI) }
                    IjiSettingsDivider()
                    SettingsRow(MiuixIcons.Copy, "提示词与规则") { onNavigate(Routes.PROMPTS) }
                }
            }

            item {
                SettingsGroup(
                    title = "数据与备份",
                    accent = AppleGreen,
                    modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding),
                ) {
                    SettingsRow(MiuixIcons.Backup, "数据与备份") {
                        onNavigate(Routes.BACKUP_SETTINGS)
                    }
                    IjiSettingsDivider()
                    SettingsRow(
                        MiuixIcons.Download,
                        "账单导入/导出",
                        summary = "CSV / 截图",
                    ) { onNavigate(Routes.BILL_IMPORT) }
                }
            }

            item {
                SettingsGroup(
                    title = "偏好",
                    accent = AppleOrange,
                    modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding),
                ) {
                    SettingsRow(MiuixIcons.Settings, "偏好设置") { onNavigate(Routes.PREFERENCES) }
                    IjiSettingsDivider()
                    SettingsRow(MiuixIcons.Lock, "密码保护", summary = lockSummary) { onNavigate(Routes.PASSWORD_PROTECT) }
                }
            }

            item {
                SettingsGroup(
                    title = "关于",
                    accent = MiuixPrimaryDark,
                    modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding),
                ) {
                    SettingsRow(MiuixIcons.Help, "引导教程") { onNavigate(Routes.GUIDE_TUTORIAL) }
                    IjiSettingsDivider()
                    SettingsRow(MiuixIcons.Notes, "捐赠支出") { onNavigate(Routes.DONATION) }
                    IjiSettingsDivider()
                    SettingsRow(MiuixIcons.Info, "关于应用") { onNavigate(Routes.ABOUT) }
                }
            }
        }
        // 浮层顶栏（不占位，最后绘制）：与首页同款 IjiTopBar，含大标题折叠
        IjiTopBar(
            backdrop = backdrop,
            title = "设置",
            largeTitle = "设置",
            scrollBehavior = scrollBehavior,
            startAction = { backdropAlpha, shadowAlpha ->
                // 顶栏上滑变糊时按钮浮出液态玻璃材质（backdropAlpha 随模糊带弹簧升降）
                LiquidTopBarButton(
                    onClick = onBack,
                    icon = MiuixIcons.ChevronBackward,
                    contentDescription = "返回",
                    backdrop = backdrop,
                    backdropAlpha = backdropAlpha,
                    shadowAlpha = shadowAlpha,
                )
            },
        )
    }

    // 昵称编辑弹窗（全站弹窗统一走 IjiDialog）
    IjiDialog(
        show = showNicknameDialog,
        onDismissRequest = { showNicknameDialog = false },
        title = "修改昵称",
        confirmText = "保存",
        onConfirm = {
            ProfileState.setNickname(context, nicknameInput.trim())
            showNicknameDialog = false
        },
    ) {
        top.yukonga.miuix.kmp.basic.TextField(
            nicknameInput,
            { input -> nicknameInput = input.take(20) },
            Modifier.fillMaxWidth(),
        )
    }

}

/**
 * 头像：有自定义图片时由 Coil 异步加载（带采样/缓存，避免主线程同步解码大图），
 * 否则显示默认字母头像。加载中/失败统一回退到字母头像，保证不破版。
 */
@Composable
private fun AvatarImage(uri: String, size: androidx.compose.ui.unit.Dp) {
    if (uri.isBlank()) {
        DefaultLetterAvatar(size)
        return
    }
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(uri)
            .crossfade(true)
            .build(),
        contentDescription = "头像",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        loading = { DefaultLetterAvatar(size) },
        error = { DefaultLetterAvatar(size) },
    )
}

/** 默认字母头像（无自定义图片 / 加载失败兜底） */
@Composable
private fun DefaultLetterAvatar(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(AppleSeparator),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "i",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(content = content)
    }
}

/**
 * 组内左图标的统一语义色：由 [SettingsGroup] 的 `accent` 提供，行自身不必重复传参 ——
 * 目的是"一组一个色"而不是 14 行各自一色（后者会变成一张要逐项维护的色板）。
 */
private val LocalSettingsAccent = compositionLocalOf { MiuixPrimary }

/**
 * 一个设置分组：小标题 + 卡片。
 * 两段间距分开控制：标题与卡片之间 = [top.yukonga.miuix.kmp.basic.SmallTitle] 的
 * `insideMargin.bottom`（10dp）；组与组之间 = 外层 LazyColumn 的 `spacedBy`（12dp）。
 *
 * @param accent 本组左图标的语义色，经 [LocalSettingsAccent] 下发到组内每个 [SettingsRow]
 */
@Composable
private fun SettingsGroup(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        top.yukonga.miuix.kmp.basic.SmallTitle(
            text = title,
            // 只留「标题 → 自己这张卡片」的间距：insideMargin 的 start/end 会破坏标题与卡片
            // 左边缘的对齐，所以置 0；bottom = 10dp 让文字不再贴着白块（原来 0 时文字底边到
            // 卡片顶边只有约 5dp，视觉上就是压在卡片上）。组与组之间的距离由 LazyColumn 给。
            insideMargin = PaddingValues(bottom = 10.dp),
        )
        CompositionLocalProvider(LocalSettingsAccent provides accent) {
            SettingsCard(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    /** 右侧状态摘要：让用户不点进二级页也能看到当前状态（无摘要则只显示标题） */
    summary: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            // 行高 52dp → 56dp：P2-3 加了左图标方块（28dp 高于 bodyLarge 的 24dp），
            // 14 行合计约多 56dp 高度。想回到 52dp 就让 RowLeadingIcon 用 boxSize = 24.dp。
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLeadingIcon(icon, LocalSettingsAccent.current)
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    // 摘要限宽，避免长文本把标题挤没
                    .widthIn(max = 150.dp)
                    .padding(start = 12.dp),
            )
        }
        IjiTrailingIcon(
            MiuixIcons.Basic.ArrowRight,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}


