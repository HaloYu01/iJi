package com.iji.app.ui.theme

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 集中式圆角 Token。
 *
 * 所有圆角必须从这里取值，禁止在业务代码里硬编码圆角数值（如 `RoundedCornerShape(12.dp)`），
 * 便于统一调整与维护。
 */
object Radius {
    /** 4dp：小控件内部（进度条、微型标签） */
    val xs = 4.dp

    /** 6dp：图例小圆点等微型元素 */
    val dot = 6.dp

    /** 8dp：小卡片、chip、输入框 */
    val sm = 8.dp

    /** 10dp：列表条目、按钮 */
    val md = 10.dp

    /** 12dp：卡片 */
    val lg = 12.dp

    /** 16dp：大卡片、弹层内容区 */
    val xl = 16.dp

    /** 20dp：设置 / 列表页的大卡片、底部弹层顶部圆角 */
    val xxl = 20.dp

    /** 24dp：底部抽屉顶部圆角 */
    val sheet = 24.dp

    /** 全圆角（胶囊 / 圆形按钮） */
    val pill = 999.dp
}

/**
 * 响应式尺寸集合。随屏幕宽度切换，保证手机 / 平板 / 大屏都有舒适留白。
 *
 * @param screenPadding 屏幕左右两侧内边距
 * @param cardGap 卡片 / 栏目之间的间距
 */
data class Dimens(
    val screenPadding: Dp,
    val cardGap: Dp,
)

/** 手机（< 600dp） */
private val CompactDimens = Dimens(
    screenPadding = 20.dp,
    cardGap = 12.dp,
)

/** 小平板 / 展开折叠屏（600dp ~ 839dp） */
private val MediumDimens = Dimens(
    screenPadding = 28.dp,
    cardGap = 14.dp,
)

/** 平板 / 大屏（>= 840dp）：进一步加宽留白，避免内容被拉伸到贴边 */
private val ExpandedDimens = Dimens(
    screenPadding = 32.dp,
    cardGap = 16.dp,
)

/** 当前生效的响应式尺寸；未提供时使用 [CompactDimens]。 */
val LocalDimens = compositionLocalOf { CompactDimens }

/**
 * **最小可点击区**（无障碍基线，WCAG / Material 建议 48dp）。
 *
 * 以前各处手写 28 / 32 / 40dp，图标画得小、点击区也跟着小，手指容易点空。
 * 统一口径：**视觉尺寸照旧，点击区兜到 48dp** —— 用
 * `Modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)`
 * 而不是 `size(...)`（`size` 会同时锁死 min/max，兜不住）。
 */
val MIN_TOUCH_TARGET = 48.dp

/** 把点击区兜到 [MIN_TOUCH_TARGET]，视觉尺寸不变 */
fun Modifier.minTouchTarget(): Modifier =
    this.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)

/**
 * 根据屏幕宽度注入 [LocalDimens]，供全局使用。
 * 放在 `IjiTheme` 内部即可让所有页面拿到响应式尺寸。
 */
@Composable
fun ProvideDimens(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val dimens = remember(screenWidthDp) {
        when {
            screenWidthDp >= 840 -> ExpandedDimens
            screenWidthDp >= 600 -> MediumDimens
            else -> CompactDimens
        }
    }
    CompositionLocalProvider(LocalDimens provides dimens, content = content)
}
