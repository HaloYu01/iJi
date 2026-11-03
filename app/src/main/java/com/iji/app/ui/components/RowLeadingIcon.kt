package com.iji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iji.app.ui.theme.Radius

/**
 * 设置行的左图标（P2-3 定稿：**彩色方块**）。
 *
 * 规格：`boxSize`（默认 28dp）方块 + `Radius.sm`(8dp) 圆角 + `accent.copy(alpha = 0.14f)` 底色
 * + `boxSize * 0.64`（默认约 18dp）的**同色**图标；与标题的间距 12dp 由自带 `padding(end)` 提供
 * （外层不要再补 Spacer，否则整行点击区的语义会被拆散）。
 *
 * **颜色约定**：`accent` 是"入口语义色"，按**分组复用**而不是每行一个色。当前分配：
 * - 「我的」页：记账对象=`MiuixPrimary` / 自动化=`ApplePurple` / 数据与备份=`AppleGreen`
 *   / 偏好=`AppleOrange` / 其他=`AppleIndigo`
 * - 「偏好设置」页**不用**左图标（纯文字 + 值/开关，行更矮、更好扫），所以没有配色要维护
 *
 * **已知取舍**：底色是 `accent` 的透明度叠加，深色模式下会明显变淡——14% 叠在 `AppSurfaceDark(#1C1C1E)`
 * 上只偏离卡片底 3~24 个色阶（实测 `(49,36,57)/(60,45,26)/(31,52,39)`），即"深色下几乎没有色块，
 * 只剩图标本身是全色"。若将来要求深色也成明显色块，需要按模式取不同 alpha（多一条要维护的分支）。
 *
 * **行高影响**：方块 28dp 高于 `bodyLarge` 的 24dp，两行式布局的行高会 +4dp
 * （「我的」页 14 行 = 页面多约 56dp 高度）；如需完全不变，可传 `boxSize = 24.dp`。
 */
@Composable
fun RowLeadingIcon(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    boxSize: Dp = 28.dp,
) {
    Box(
        modifier = modifier
            .padding(end = 12.dp)
            .size(boxSize)
            .background(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(Radius.sm),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(boxSize * 0.64f),
        )
    }
}
