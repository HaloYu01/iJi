package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.iji.app.ui.components.IjiTopBar
import com.iji.app.ui.theme.LocalDimens

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward

/**
 * 「我的」子页外壳：与主页面完全同一套**沉浸式**顶栏 + 内容区。
 *
 * 沉浸式要点：
 * - 页面背景铺满整屏（含状态栏与挖孔区），内容绘制在系统栏之下；
 * - 顶栏是**浮层**，本身不占位；
 * - 只有交互元素（返回按钮 / 标题）避开状态栏与刘海 —— 用 `ijiTopBarSafeTopPadding()`，
 *   不能用 Compose 的 `WindowInsets.statusBars`（内容在 Box 里，inset 已被上层消费，读出来是 0）；
 * - 内容区用 `ijiTopBarContentPadding()`（安全区 + 52dp 折叠态高度）让出顶部，
 *   与四页主页面的 `contentPadding.top` 完全一致 —— 跨页面切换时标题与首行不会上下跳动。
 *
 * 顶栏不做毛玻璃（`backdrop = null`，纯色实底）：省掉每帧重录整屏的采样开销，也不会糊住首行。
 * 代价是顶栏只有 52dp 实底，内容让位后正好贴着它的下沿 —— 因此这里统一补一段
 * [LocalDimens.cardGap] 留白，避免首行卡片贴顶，并让所有子页间距一致。详见 [IjiTopBar]。
 *
 * 标题复用 [IjiTopBar] 的小标题样式（19sp / Medium），保证与主页面视觉连贯。
 * 子页没有大标题，故不传 scrollBehavior（小标题常显，无折叠动画）。
 *
 * ## 顶栏对齐口径（子页统一，别再引入第二种约定）
 * - **水平**：返回在左（距边 16dp）、标题在**整条栏**上居中（不是「左右按钮之间」居中）、
 *   [endAction] 在右（距边 16dp）；
 * - **垂直**：三者都取折叠态栏（52dp）的 y 中心，由 `CollapsibleTopBar` 统一摆放 ——
 *   所以页面提供的 [endAction] 高度不要超过 52dp，也不要在里面自己加 padding/margin 上下偏移；
 * - **标题长度**：`CollapsibleTopBar` 会按左右操作区的实测宽度收紧标题上限（超出省略号，不换行），
 *   因此长标题（账本名 / 分类名）不会伸到返回键底下，栏高也恒定不变。
 */
@Composable
fun MineSubPage(
    title: String,
    onBack: () -> Unit,
    /** 顶栏右侧操作（如「解析」「保存」）；不传则只有返回按钮 */
    endAction: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.background

    // 屏幕尺寸 / 方向适配：平板（≥600dp）与横屏加宽两侧留白，
    // 避免大屏内容被拉得过宽、横屏时被侧边挖孔压住
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
            .background(surface),
    ) {
        // 顶部只补"呼吸间距"（cardGap，与页内卡片间距同源）；让出顶栏高度仍由各页
        // 自己的 ijiTopBarContentPadding() 负责，否则会双重加高。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = extraHorizontal,
                    top = LocalDimens.current.cardGap,
                    end = extraHorizontal,
                ),
            content = content,
        )
        IjiTopBar(
            // 子页面不要毛玻璃 → 纯色实底顶栏（见 IjiTopBar 的 backdrop 注释）
            backdrop = null,
            title = title,
            largeTitle = "",
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
            endAction = if (endAction != null) {
                { _, _ -> endAction() }
            } else {
                null
            },
        )
    }
}
