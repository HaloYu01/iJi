package com.iji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.Radius

/**
 * 小字操作按钮：替代「裸文本 + clickable」。
 *
 * 为什么要有它：App 里原来有十几种「蓝色 / 红色小字挂 clickable」的写法，
 * 视觉上和旁边的说明文字完全同质 —— 用户只能靠试点来发现入口。
 * 统一成「淡色胶囊 + 半个像素描边 + 胶囊形」后，小字自带「这是按钮」的语义，
 * 且底色取自文字色，蓝色 / 红色 / 灰色三种语义共用同一套观感。
 *
 * - [color] 决定语义：主色 = 普通操作、[AppleRed] = 危险操作、次要文字色 = 中性操作；
 *   底色/描边由它派生（12% / 20% α），深浅色模式都跟着主题走。
 * - [compact] 用于列表行、多选栏这类行高紧张的位置（32dp）；默认 40dp 保证好点。
 * - 禁用态统一降为 35% α 文字 + 5% α 底色，不再各页自己调透明度。
 */
@Composable
fun IjiTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    compact: Boolean = false,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val shape = RoundedCornerShape(Radius.pill)
    Box(
        modifier = modifier
            .heightIn(min = if (compact) 32.dp else 40.dp)
            .clip(shape)
            .background(color.copy(alpha = if (enabled) 0.12f else 0.05f))
            .border(0.5.dp, color.copy(alpha = if (enabled) 0.20f else 0.10f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 5.dp else 8.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = if (enabled) 1f else 0.35f),
            maxLines = 1,
        )
    }
}

/** 危险操作的便捷包装（删除 / 清空 / 解绑），语义色固定为 [AppleRed]。 */
@Composable
fun IjiDangerTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    IjiTextAction(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        color = AppleRed,
        compact = compact,
        style = style,
    )
}
