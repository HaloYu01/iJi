package com.iji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.iji.app.ui.PerfState
import com.iji.app.ui.components.topbar.LiquidTopBarButton
import com.iji.app.ui.components.topbar.CollapsibleTopBar
import com.iji.app.ui.components.topbar.BlurTopBar
import com.iji.app.ui.components.topbar.TopBarScrollBehavior
import com.kyant.backdrop.Backdrop
import com.iji.app.ui.mine.ProfileState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 折叠态顶栏固定高度，四页一致。 */
val IjiTopBarHeight: Dp = 52.dp

/**
 * 模糊玻璃带的带高（**不含**顶部安全区，[IjiTopBar] 会自动叠加安全区）。
 *
 * 取 120dp：带子比「折叠态 52dp + 展开态大标题
 * ~48dp」略高，保证大标题完整落在带内、滚过顶栏的内容始终被糊到。带子只是装饰层，内容顶部
 * 让位由 [ijiTopBarContentPadding] + `topBarContentInset` 负责，带高不参与内容排版。
 */
val IjiTopBarBlurBandHeight: Dp = 120.dp

/**
 * 顶部安全区高度：**状态栏与刘海/挖孔（displayCutout）取较大者**。
 *
 * 沉浸式下内容会绘制到状态栏下方，但顶栏的标题与按钮必须避开这块区域，
 * 否则会被状态栏图标（时间 / 电量 / 信号）压住。
 *
 * 注意：不能读 Compose 的 `WindowInsets.systemBars` / `statusBars`——内容在 Scaffold 的
 * content 里，inset 已被上层消费，读出来是 0（实测三种写法均为 0）。这里直接从 View 的
 * rootWindowInsets 取，不受 Compose inset 消费影响，也顺带拿到 displayCutout。
 */
@Composable
fun ijiTopBarSafeTopPadding(): Dp {
    val view = LocalView.current
    val topPx = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        )?.top ?: 0
    val density = LocalDensity.current
    return (topPx.toFloat() / density.density).dp
}

/**
 * 顶栏预留的内容顶部内边距（LazyColumn 的 top contentPadding）：**顶部安全区 + 折叠态高度**。
 *
 * [com.iji.app.ui.components.topbar.topBarContentInset] 只补齐「展开态比折叠态多出的大标题高度」，
 * 不含状态栏 / 安全区，所以内容必须自己让出安全区，否则列表滚到头时会继续上穿进顶栏，
 * 透过半透明模糊与顶栏按钮/标题叠在一起。
 */
@Composable
fun ijiTopBarContentPadding(): Dp = ijiTopBarSafeTopPadding() + IjiTopBarHeight

/**
 * 页面采样层（`rememberLayerBackdrop` 挂滚动容器）的**条带录制高度**。
 *
 * 本页采样层的消费方只有顶栏玻璃件（[BlurTopBar] 模糊带 + 三颗 [LiquidTopBarButton] 圆钮），
 * 全部落在「安全区 + 120dp 玻璃带 + 模糊采样余量」的顶部条带内。采样层录制时只录这段
 * 条带（层原点 = 滚动容器原点 = 窗口顶），滚动帧的重录从整屏降到条带——GPU 层内存与
 * 填充按高度比例下降（1280×2856 整屏 ≈ 14.6MB/帧 → 条带 ≈ 3MB/帧），这是顶栏玻璃
 * 管线在滚动期间最大的单项 GPU 开销。条带以下在采样图中为透明，玻璃采样不到。
 */
@Composable
fun ijiTopBarRecordHeight(): Dp =
    ijiTopBarSafeTopPadding() + IjiTopBarBlurBandHeight + 16.dp

/**
 * 四页共用的折叠顶栏：**背景毛玻璃（backdrop blur）** + 大标题滚动收起、小标题淡入、滚动阴影。
 * 固定浮层，滚动时自身不动，只有内容从下方穿过。
 *
 * ## 毛玻璃管线
 * AGSL 32 tap 黄金角抖动径向采样 → 小半径去噪 → 染色。半径随 y 衰减：
 * `radius = maxRadius * (1 - smoothstep(0, 1, y / 带高))`，即**顶边最糊、带底收为 0**，
 * 底端再叠一层 `softerstep(edgeFadeStart, 1, t)` 的透明淡出（所以没有硬边）。
 * 模糊强度不随滚动变化（与参照实现一致）。
 *
 * 调用方需自行准备好：
 * - [com.kyant.backdrop.backdrops.rememberLayerBackdrop] 采样层（建议 `onDraw = { drawRect(surface); drawContent() }`），
 *   挂到**同一页面的滚动容器**（`Modifier.layerBackdrop(backdrop)`）；
 * - [com.iji.app.ui.components.topbar.rememberTopBarScrollBehavior] 滚动行为，挂到滚动容器的
 *   `.nestedScroll(scrollBehavior.nestedScrollConnection)` 与 `.topBarContentInset(scrollBehavior)`。
 *
 * 只有 4 个主 tab 页与「设置」页走毛玻璃；**设置子页面及其下级传 `backdrop = null`**
 * 退化为纯色实底 —— 子页内容紧贴顶栏（让位只有 52dp），玻璃带那 120dp 会糊住首行。
 *
 * 右侧操作区由 [endAction]（页面级控件，如「周/月/年」「今」）+ 固定的「我的」按钮组成；
 * 左侧操作区由 [startAction] 提供（如首页账本切换）。两者均接收 `(backdropAlpha, shadowAlpha)` 供液态玻璃按钮同步动画。
 */
@Composable
fun IjiTopBar(
    /** 毛玻璃采样层；**null = 不要模糊**，退化为纯色实底（设置子页面用） */
    backdrop: Backdrop?,
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    /**
     * 大标题字号：默认 32sp（Miuix `textStyles.title1`）。
     *
     * Miuix 的 `CollapsibleTopBar` 只从 `MiuixTheme.textStyles.title1` 取字号、不接受
     * style 参数（字重写死 `FontWeight.Normal`），所以这里靠**局部覆盖 MiuixTheme 的
     * textStyles** 来改，只作用于本顶栏内部。收起态小标题（`title3`）按 0.85 倍同步缩小。
     */
    largeTitleFontSize: TextUnit = MiuixTheme.textStyles.title1.fontSize,
    scrollBehavior: TopBarScrollBehavior? = null,
    startAction: @Composable ((backdropAlpha: Float, shadowAlpha: Float) -> Unit)? = null,
    endAction: @Composable ((backdropAlpha: Float, shadowAlpha: Float) -> Unit)? = null,
    onMineClick: (() -> Unit)? = null,
) {
    val combinedEndAction: @Composable ((Float, Float) -> Unit)? = if (
        endAction != null || onMineClick != null
    ) {
        { backdropAlpha: Float, shadowAlpha: Float ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                endAction?.invoke(backdropAlpha, shadowAlpha)
                if (onMineClick != null) {
                    // 「我的」按钮跟的是玻璃 / 背景进度（backdropAlpha），不是折叠阴影（shadowAlpha）
                    TopBarMineButton(
                        onClick = onMineClick,
                        backdrop = backdrop,
                        backdropAlpha = backdropAlpha,
                        shadowAlpha = shadowAlpha,
                    )
                }
            }
        }
    } else {
        null
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (backdrop != null) {
            // 玻璃层先画：层级 = 页面内容之上、顶栏标题与按钮之下（同一 Box 的绘制顺序）
            BlurTopBar(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                // 带高含顶部安全区：沉浸式下内容会绘制到状态栏下方，带子必须从 y=0 铺满状态栏区域
                height = ijiTopBarSafeTopPadding() + IjiTopBarBlurBandHeight,
                // 「降低动态效果」开启 → 走纯色渐变降级
                enabled = !PerfState.reducedEffects,
            ) {}
        } else {
            // 不要模糊：纯色实底，且只到折叠态栏底（用玻璃带的 120dp 会压住子页首行）。
            //
            // 底色必须取**页面底色**（M3 的 background，[com.iji.app.ui.mine.MineSubPage] 铺的
            // 就是它），不能取 Miuix 的 background：后者是纯白 #FFFFFF，与页面底色 #F5F6F8
            // 不同，会在状态栏下方留出一条「悬空的白带」，看起来像顶栏整体错位了。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ijiTopBarSafeTopPadding() + IjiTopBarHeight)
                    .background(MaterialTheme.colorScheme.background),
            )
        }
        // 局部覆盖 textStyles：只影响下面这颗 CollapsibleTopBar 的标题字号
        val topBarTextStyles = MiuixTheme.textStyles.copy(
            title1 = MiuixTheme.textStyles.title1.copy(fontSize = largeTitleFontSize),
            title3 = MiuixTheme.textStyles.title3.copy(
                fontSize = (largeTitleFontSize.value * 0.85f).sp,
            ),
        )
        MiuixTheme(textStyles = topBarTextStyles) {
            CollapsibleTopBar(
                title = title,
                largeTitle = largeTitle,
                scrollBehavior = scrollBehavior,
                // 子页（[MineSubPage]）传 largeTitle = ""：没有大标题时强制常显小标题，
                // 否则 vendored 默认「折叠到 0.45 才显示小标题」在无滚动行为时等于整条标题不显示。
                showSmallTitle = if (largeTitle.isBlank()) true else null,
                // 沉浸式：模糊背景从 y=0 铺到状态栏区域，但标题 / 按钮必须避开状态栏与刘海
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = ijiTopBarSafeTopPadding()),
                // 只保留 BlurTopBar 那一道 AGSL 模糊；CollapsibleTopBar 自带的
                // 渐变遮罩会在模糊之上再叠一道，视觉上呈现「两道模糊」，关掉保证只有一道。
                showGradientOverlay = false,
                startAction = startAction,
                endAction = combinedEndAction,
            )
        }
    }
}

/** 右上角「我的」入口：毛玻璃顶栏（[backdrop] 非 null）时随模糊带淡入液态玻璃材质。
 *  用户设置过头像时入口显示圆形头像（读 [ProfileState]，改头像即时重组），否则保持人形图标。 */
@Composable
fun TopBarMineButton(
    onClick: () -> Unit,
    backdrop: Backdrop? = null,
    backdropAlpha: Float = 0f,
    shadowAlpha: Float = 0f,
) {
    LiquidTopBarButton(
        onClick = onClick,
        icon = MiuixIcons.Contacts,
        contentDescription = "我的",
        backdrop = backdrop,
        backdropAlpha = backdropAlpha,
        shadowAlpha = shadowAlpha,
        avatarUri = ProfileState.avatarUri.takeIf { it.isNotBlank() },
    )
}
