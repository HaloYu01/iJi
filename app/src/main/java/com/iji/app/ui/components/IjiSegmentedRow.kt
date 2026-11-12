package com.iji.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iji.app.ui.theme.Radius

/**
 * 等宽分段控件：统计页「周/月/年」、资产页类型筛选、借入/借出切换的共用底座。
 *
 * 视觉：白色胶囊底 + 浅灰滑动指示器 + 深色文字（选中段加粗），切换时 250ms 缓动平移。
 * 记一笔页显式传入「反色」参数（surfaceVariant 槽 + surface 指示器 + 次级文字色 + xl 圆角），
 * 保持原有外观，不受这里的默认样式影响。
 *
 * 性能：整行只有 1 个动画值（指示器 x 偏移），动画期间只移动一个色块、
 * 不重建文字层；指示器用 matchParentSize 垫在文字层下，不参与本容器定高。
 */
@Composable
fun <T> IjiSegmentedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** 全应用统一 34dp；记一笔反色风格显式传 30dp */
    minSegmentHeight: Dp = 34.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    label: (T) -> String = { it.toString() },
    /** 槽底色（默认白色 surface）；记一笔页的反色风格传 surfaceVariant */
    trackColor: Color = MaterialTheme.colorScheme.surface,
    /** 选中块颜色（默认浅灰 surfaceVariant）；反色风格传 surface */
    indicatorColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    /** 选中文字颜色（默认主文字色 onSurface）；反色风格同样传 onSurface */
    selectedContentColor: Color = MaterialTheme.colorScheme.onSurface,
    /** 未选中文字颜色（默认主文字色 onSurface）；记一笔反色风格传 TextSecondary 保持原样 */
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurface,
    /** 槽与选中块的圆角（默认全胶囊；记一笔页传 xl） */
    cornerRadius: Dp = Radius.pill,
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val shape = RoundedCornerShape(cornerRadius)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(trackColor)
            .padding(2.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorX by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            label = "ijiSegmentedOffset",
        )
        // 滑动指示器：先声明 = 画在文字层下面；fillMaxHeight 生效于外层最终高度
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .offset(x = indicatorX)
                    .fillMaxHeight()
                    .width(segmentWidth)
                    .clip(shape)
                    .background(indicatorColor),
            )
        }
        Row {
            options.forEach { option ->
                val selectedNow = option == selected
                // 不走全局 LocalIndication（水波纹遮罩 + 缩放）：那会在整格上闪出
                // 矩形「方框」，与圆角浅槽的视觉语言冲突。切换反馈主体是滑动指示器，
                // 这里仅在按压时轻微压暗文字作为即时反馈。
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = minSegmentHeight)
                        // 已选中不再响应点击：省掉一次无意义的同值写入
                        .clickable(
                            enabled = !selectedNow,
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = textStyle,
                        color = when {
                            selectedNow -> selectedContentColor
                            pressed -> unselectedContentColor.copy(alpha = 0.55f)
                            else -> unselectedContentColor
                        },
                        fontWeight = if (selectedNow) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
