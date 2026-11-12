package com.iji.app.ui.mine

import com.iji.app.update.UpdateFlow
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.Radius

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import com.iji.app.ui.components.topbar.topBarContentInset
import com.iji.app.ui.components.topbar.rememberTopBarScrollBehavior
import com.iji.app.ui.effects.background.GlowBackground
import com.iji.app.ui.components.IjiTopBarHeight
import com.iji.app.ui.components.IjiTopBar
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.components.ijiTopBarSafeTopPadding
import com.iji.app.ui.components.RowLeadingIcon
import com.iji.app.ui.theme.AppleGreen
import com.iji.app.ui.theme.AppleIndigo
import com.iji.app.ui.theme.AppleOrange
import com.iji.app.ui.theme.ApplePurple
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.TextSecondary

import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Update
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 顶部头部（图标/名称/版本）与背景光效的淡出滚动距离（px） */
private const val HEADER_FADE_DISTANCE = 300f

/**
 * 内容区最大宽度：手机上可用宽度更小，自动等于全宽；
 * 平板 / 桌面窗口里内容保持单列阅读宽度并居中，不被拉成超长行。
 */
private val CONTENT_MAX_WIDTH = 720.dp

/** 崩溃日志管理 */
object CrashLogger {
    private const val PREFS_NAME = "crash_logs"
    private const val KEY_LOGS = "logs"

    fun saveCrashLog(context: android.content.Context, throwable: Throwable) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val existingLogs = prefs.getString(KEY_LOGS, "") ?: ""

        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val newLog = "=== $date ===\n$sw\n\n"

        // 只保留最近10条
        val logs = (existingLogs + newLog).split("\n\n").filter { it.isNotBlank() }.takeLast(10).joinToString("\n\n")
        prefs.edit().putString(KEY_LOGS, logs).apply()
    }

    fun getCrashLogs(context: android.content.Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOGS, "") ?: ""
    }

    fun clearCrashLogs(context: android.content.Context) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE).edit().remove(KEY_LOGS).apply()
    }
}

/** 崩溃日志查看页 */
@Composable
fun CrashLogScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val logs = remember { CrashLogger.getCrashLogs(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶部栏（沉浸式：避开状态栏与刘海，高度与主页面一致）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ijiTopBarSafeTopPadding())
                .height(IjiTopBarHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MiuixIcons.ChevronBackward,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp),
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "崩溃日志",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // 清除按钮
            IjiTextAction(
                text = "清除",
                onClick = {
                    CrashLogger.clearCrashLogs(context)
                    snackbar.show("日志已清除")
                    onBack()
                },
                compact = true,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(16.dp))

        // 日志内容
        if (logs.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无崩溃日志", color = TextSecondary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = LocalDimens.current.screenPadding),
            ) {
                // 统计信息
                top.yukonga.miuix.kmp.basic.Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = Radius.xxl,
                    colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        val crashCount = logs.split(Regex("(?=== 崩溃时间:)")).filter { it.isNotBlank() }.size
                        Text("共记录 $crashCount 次崩溃", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 日志列表
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val logList = logs.split(Regex("(?=== 崩溃时间:)")).filter { it.isNotBlank() }
                    items(logList.size) { index ->
                        val log = logList[index]
                        top.yukonga.miuix.kmp.basic.Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = Radius.xl,
                            colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Text(
                                    log.take(200) + if (log.length > 200) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 关于应用页。
 *
 * 页面结构：
 * 顶部应用图标 + 名称 + 版本（滚动时淡出缩小），下方依次为
 * 「功能入口 / 更新日志（可展开最近 3 条）/ 特别致谢（可展开）/ 崩溃日志 / 页脚」分组卡片。
 *
 * 顶栏沿用本应用统一的折叠式 [IjiTopBar]（AGSL 渐进模糊 + 大标题收起），返回按钮由 startAction 提供。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateCrashLog: () -> Unit = {},
    onNavigateChangelog: () -> Unit = {},
    onNavigateLicense: () -> Unit = {},
    onNavigatePrivacy: () -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbar = LocalSnackbarController.current

    val scrollBehavior = rememberTopBarScrollBehavior()
    val listState = rememberLazyListState()

    // 文件创建器（导出日志）
    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            try {
                val logs = CrashLogger.getCrashLogs(context)
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(logs.toByteArray())
                }
                snackbar.show("日志导出成功")
            } catch (e: Exception) {
                snackbar.show("导出失败：${e.message}", duration = SnackbarDuration.Long)
            }
        }
    }

    // 应用信息（包名取真实图标与版本号，取不到时回退首字母）
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
    }
    val appName = remember {
        packageInfo?.applicationInfo?.labelRes?.let { context.getString(it) }
            ?: context.applicationInfo?.nonLocalizedLabel?.toString()
            ?: "i记"
    }
    // 版本号取自安装包，页面不写死任何版本常量（改版本只改 build.gradle.kts）。
    val appVersion = remember { currentVersionInfo(context).versionName }
    val appIcon = remember {
        val drawable = try {
            context.packageManager.getApplicationIcon(context.packageName)
        } catch (_: Exception) {
            context.applicationInfo.icon
                .takeIf { it != 0 }
                ?.let { id -> runCatching { context.getDrawable(id) }.getOrNull() }
        }
        if (drawable != null) {
            val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 1)
            val bitmap = createBitmap(size, size)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            cropBitmapToOpaque(bitmap).asImageBitmap()
        } else {
            null
        }
    }

    // 头部（图标 / 名称 / 版本）与背景光效随滚动淡出缩小。
    //
    // 关键：必须用 firstVisibleItemIndex > 0 兜底。若只按 firstVisibleItemScrollOffset 计算，
    // 滚过第一条后 index 递增而 offset 归零，进度会回落到 0 → 光晕在滑动过程中反复闪现
    // （表现为「滑动/点击时背后光晕意外闪现」）。越过首条后进度恒为 1，光晕保持隐藏。
    val headerProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / HEADER_FADE_DISTANCE).coerceIn(0f, 1f)
            }
        }
    }
    // 在组合期读取进度：进度变化 → 本组合重组 → 光效层 update() → invalidateDraw 重绘，
    // 不依赖动画帧也能及时刷新 alpha（避免动画暂停时 alpha 滞后造成闪跳）
    val haloAlpha = 1f - headerProgress

    // 光晕可见时卡片留出一点透明度，让背景光效透出来，避免实色块把光晕切成生硬的边。
    // 用两档而非随滚动连续插值：滚动中卡片颜色稳定，避免每帧重组整页卡片；
    // 光晕几乎淡尽时再切回实色，此刻视觉上无跳变，正文对比度也恢复。
    val cardColor = MaterialTheme.colorScheme.surface.copy(
        alpha = if (haloAlpha > 0.02f) 0.76f else 1f,
    )

    Scaffold(
        topBar = {
            IjiTopBar(
                // 设置子页面不要毛玻璃 → 纯色实底顶栏（见 IjiTopBar 的 backdrop 注释）
                backdrop = null,
                title = "关于应用",
                largeTitle = "",
                scrollBehavior = scrollBehavior,
                startAction = { _, _ ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) { _ ->
        // 背景光效：铺在内容之下，随页面滚动淡出
        GlowBackground(
            dynamicBackground = true,
            isFullSize = true,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            alpha = { haloAlpha },
        ) {
            // 宽屏（平板 / 桌面窗口）下限制阅读宽度并居中：
            // 少了这一层，卡片会被拉到整个窗口宽，一行说明横跨上千 dp，极难阅读。
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = CONTENT_MAX_WIDTH)
                        .fillMaxSize()
                        .topBarContentInset(scrollBehavior)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = ijiTopBarContentPadding(),
                        bottom = 32.dp,
                    ),
                    // 分组之间统一用主题卡间距：之前没有 verticalArrangement，卡片是紧贴的
                    verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap),
                ) {
                    // ── 1. 身份区：图标 / 名称 / 版本号 ──
                    item(key = "hero") {
                        AboutHero(
                            appIcon = appIcon,
                            appName = appName,
                            version = appVersion,
                            progress = headerProgress,
                        )
                    }

                    // ── 2. 更新：版本状态与检查动作放同一组 ──
                    // 原来 5 行塞在一张「应用支持」里，把「看版本」和「看条款」两类事混在一起。
                    item(key = "updates") {
                        AboutSection(title = "版本与更新") {
                            AboutCard(containerColor = cardColor) {
                                AboutEntryRow(
                                    icon = MiuixIcons.Notes,
                                    accent = MiuixPrimary,
                                    title = "更新日志",
                                    // version 本身已带 v 前缀，直接拼会变成「vv1.0.0」
                                    endText = changelogData.firstOrNull()?.version,
                                    onClick = onNavigateChangelog,
                                )
                                AboutDivider()
                                AboutEntryRow(
                                    icon = MiuixIcons.Update,
                                    accent = AppleIndigo,
                                    title = "检查更新",
                                    onClick = { UpdateFlow.openManual(context) },
                                )
                            }
                        }
                    }

                    // ── 3. 支持与信息：反馈入口 + 条款类页面 ──
                    item(key = "support") {
                        AboutSection(title = "支持与信息") {
                            AboutCard(containerColor = cardColor) {
                                AboutEntryRow(
                                    icon = MiuixIcons.Email,
                                    accent = AppleOrange,
                                    title = "问题反馈",
                                    endText = "邮件",
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = android.net.Uri.parse("mailto:$contactEmail")
                                            putExtra(Intent.EXTRA_SUBJECT, "$appName - 问题反馈")
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(intent, "问题反馈"))
                                        }.onFailure {
                                            snackbar.show("未找到邮件应用")
                                        }
                                    },
                                )
                                AboutDivider()
                                AboutEntryRow(
                                    icon = MiuixIcons.Lock,
                                    accent = AppleGreen,
                                    title = "隐私政策",
                                    onClick = onNavigatePrivacy,
                                )
                                AboutDivider()
                                AboutEntryRow(
                                    icon = MiuixIcons.File,
                                    accent = ApplePurple,
                                    title = "许可与授权",
                                    onClick = onNavigateLicense,
                                )
                            }
                        }
                    }

                    // ── 4. 特别致谢（折叠，收起时显示条目数）──
                    item(key = "thanks") {
                        var expanded by remember { mutableStateOf(false) }
                        AboutCollapsibleCard(
                            containerColor = cardColor,
                            modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding),
                            title = "特别致谢",
                            endText = "${thanksData.size} 个项目",
                            expanded = expanded,
                            onToggle = { expanded = !expanded },
                        ) {
                            Text(
                                // 按数据原顺序列出更准确，也不会漏掉作者。
                                text = "以下项目为本应用提供了参考，点击名称可访问仓库；" +
                                    "依赖库许可见「许可与授权」页。",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.height(4.dp))
                            thanksData.forEach { item ->
                                ThanksItem(item = item, uriHandler = uriHandler)
                            }
                        }
                    }

                    // ── 5. 运行诊断：崩溃日志（折叠；没有日志时不渲染按钮）──
                    item(key = "crash") {
                        var expanded by remember { mutableStateOf(false) }
                        val logs = remember { CrashLogger.getCrashLogs(context) }
                        AboutCollapsibleCard(
                            containerColor = cardColor,
                            modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding),
                            title = "崩溃日志",
                            endText = if (logs.isBlank()) "无记录" else "有记录",
                            expanded = expanded,
                            onToggle = { expanded = !expanded },
                        ) {
                            Text(
                                text = if (logs.isBlank()) {
                                    "暂无崩溃日志。发生异常时会记录在本机，可导出后发送给开发者。日志仅存本机，需你主动导出才会离开设备。"
                                } else {
                                    "已记录崩溃日志，可查看或导出后发送给开发者。日志仅存本机。"
                                },
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = TextSecondary,
                            )
                            if (logs.isNotBlank()) {
                                Spacer(Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // 统一走全站的小字按钮 IjiTextAction：自绘的浅底胶囊
                                    // 与其它页面的按钮观感不一致（描边、高度、字号都不同）
                                    IjiTextAction(
                                        text = "查看日志",
                                        onClick = onNavigateCrashLog,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    IjiTextAction(
                                        text = "导出日志",
                                        onClick = {
                                            createFileLauncher.launch("iji_crash_logs_${System.currentTimeMillis()}.txt")
                                        },
                                        modifier = Modifier.weight(1f),
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }

                    // ── 6. 页脚 ──
                    // 一行灰字即可；作者名不给主色（同色但不可点，容易被误认成失效链接）。
                    item(key = "footer") {
                        Text(
                            text = "© 2026 $appName · 设计与开发 HaloYu",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = LocalDimens.current.screenPadding,
                                    vertical = 24.dp,
                                ),
                        )
                    }
                }
            }
        }
    }

    // 更新弹窗统一挂在 AppRoot（A/B 共用一个实例），本页只负责触发手动检查。
}

/**
 * 可折叠卡片（标题行可点，右侧给条目数/状态，箭头随展开旋转）。
 *
 * 内容区的内边距由本组件统一提供（20dp，与标题行左边界对齐）：
 * 调用点不再各自写 `padding(horizontal = 18.dp, vertical = 16.dp)`，
 * 避免不同卡片的文字缩进差 2dp。
 */
@Composable
private fun AboutCollapsibleCard(
    containerColor: Color,
    title: String,
    endText: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(containerColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle)
                    // 展开/收起是状态而不是纯动作：读屏需要能听出当前是否展开
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (!endText.isNullOrBlank()) {
                    Text(
                        text = endText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 150.dp)
                            .padding(start = 8.dp),
                    )
                }
                val arrowRotation by animateFloatAsState(
                    targetValue = if (expanded) 90f else 0f,
                    animationSpec = tween(180),
                    label = "aboutCollapsibleArrow",
                )
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    // 装饰性箭头：展开状态由整行的 stateDescription 播报
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(20.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                    tint = TextSecondary,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                    content = content,
                )
            }
        }
    }
}

/** 关于页分组卡片（左右留白由 [AboutSection] 统一提供） */
@Composable
private fun AboutCard(
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(containerColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * 分组容器：小标题 + 卡片，和「我的」页的设置分组用同一套节奏
 * （标题与卡片之间留 10dp，组与组之间的间距交给外层 LazyColumn 的 spacedBy）。
 *
 * 左右留白取 [LocalDimens] 的 screenPadding：原来是写死的 16dp，
 * 比其它页窄 4dp，整页看起来比设置页「宽一圈」。
 */
@Composable
private fun AboutSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = LocalDimens.current.screenPadding)) {
        top.yukonga.miuix.kmp.basic.SmallTitle(
            text = title,
            // 与 MineScreen 的 SettingsGroup 保持一致：start/end 置 0 保持左边缘对齐，
            // bottom 10dp 避免小标题压在卡片上
            insideMargin = PaddingValues(bottom = 10.dp),
        )
        content()
    }
}

/**
 * 身份区：图标 + 名称 + 版本号。
 *
 * 宽屏（平板 / 桌面窗口）改为横向排布：竖排居中在宽屏上会留下大片空白，
 * 横向「图标 + 信息」更紧凑，也更像应用信息页而不是启动画面。
 * 滚动淡出沿用原行为（整体 alpha / 轻微缩放）。
 */
@Composable
private fun AboutHero(
    appIcon: ImageBitmap?,
    appName: String,
    version: String,
    progress: Float,
) {
    val screenPadding = LocalDimens.current.screenPadding
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 1f - progress
                scaleX = 1f - progress * 0.04f
                scaleY = 1f - progress * 0.04f
            }
            .padding(horizontal = screenPadding, vertical = 8.dp),
    ) {
        if (maxWidth >= 480.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AboutAppIcon(appIcon, appName, 84.dp)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(
                        text = appName,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    AboutVersionPill(version)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AboutAppIcon(appIcon, appName, 88.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = appName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                AboutVersionPill(version)
            }
        }
    }
}

/** 应用图标：有真实图标就裁圆角显示，取不到则用首字母兜底 */
@Composable
private fun AboutAppIcon(bitmap: ImageBitmap?, appName: String, size: Dp) {
    val shape = RoundedCornerShape(Radius.xxl)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MiuixPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = appName.take(1),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

/** 版本号胶囊：品牌色浅底，比一行灰字更能表明「这是当前版本」 */
@Composable
private fun AboutVersionPill(version: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MiuixPrimary.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = if (version.isBlank()) "版本未知" else "v$version",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixPrimary,
        )
    }
}

/**
 * 入口行：行首彩色图标方块 + 标题 + 可选右侧摘要 + 箭头。
 *
 * 规格对齐「我的」页的设置行：图标 28dp 淡色圆角方块（[RowLeadingIcon]）、
 * 标题 bodyLarge、箭头 20dp、左右 20/16dp。此前是 17sp 文字 + 16dp 箭头，
 * 与全站设置项不一个观感，且右侧摘要最多只有 13sp 灰字、缺省略处理。
 */
@Composable
private fun AboutEntryRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    endText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLeadingIcon(icon, accent)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!endText.isNullOrBlank()) {
            Text(
                text = endText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    // 摘要限宽，避免「版本 · 日期」把标题挤出边界
                    .widthIn(max = 150.dp)
                    .padding(start = 8.dp),
            )
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(20.dp),
        )
    }
}

/**
 * 组内分割线。
 *
 * 颜色从 onSurface 派生（8% α）：固定灰 [TextSecondary] 在深色主题里偏亮，
 * 分割线会抢正文；[startPadding] 用于让线从行内文字左边界开始（致谢列表传 0）。
 */
@Composable
private fun AboutDivider(startPadding: Dp = 20.dp) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

/** 裁掉 AdaptiveIcon 画布四周透明区域，避免关于页图标显得过小。 */
private fun cropBitmapToOpaque(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    if (width <= 0 || height <= 0) return source
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    var index = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (pixels[index] ushr 24 != 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            index++
        }
    }
    if (maxX < minX || maxY < minY) return source
    val croppedWidth = maxX - minX + 1
    val croppedHeight = maxY - minY + 1
    if (croppedWidth == width && croppedHeight == height) return source
    return Bitmap.createBitmap(source, minX, minY, croppedWidth, croppedHeight)
}

/** 致谢项目名的链接色（按视觉稿 #3175dd） */
private val ThanksLinkColor = Color(0xFF3175DD)

/**
 * 致谢项目单项：左侧项目名、右侧作者，整行可点开仓库。
 */
@Composable
private fun ThanksItem(
    item: OpenSourceItem,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    val linked = item.url.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (linked) {
                    Modifier.clickable { uriHandler.openUri(item.url) }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            // 有链接才用链接色：链接色 + 可点 = 链接；无链接保持正文色
            color = if (linked) ThanksLinkColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (item.author.isNotBlank()) {
            Text(
                text = item.author,
                fontSize = 12.5.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
