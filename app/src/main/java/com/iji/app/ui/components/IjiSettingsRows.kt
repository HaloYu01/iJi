package com.iji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.iji.app.ui.theme.AppleSeparator
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown

/**
 * 设置页的「小标题 + 卡片」分组，以及三种标准行（纯设置项 / 取值 / 开关）。
 *
 * 抽出来是为了让页面只描述**内容**：改一页设置不该再抄十遍
 * `Card + Row + Column(标题+副标题) + Box(0.5dp 分隔线)`（偏好设置页原来 888 行，60% 是这层样板）。
 *
 * 视觉口径（与「我的」页一致）：
 * - 分组小标题距自己那张卡片 10dp，组与组之间由外层 `Arrangement.spacedBy(12.dp)` 给；
 * - 行内边距左右 20dp；有副标题 14dp、单行 16dp；分隔线从 20dp 处起画（左侧留白，右侧通到卡片边）。
 */
@Composable
fun IjiSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SmallTitle(text = title, insideMargin = PaddingValues(bottom = 10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = Radius.xxl,
            colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
        ) {
            Column(content = content)
        }
    }
}

/** 组内行分隔线（画在行与行之间，别放在最后一行后面） */
@Composable
fun IjiSettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp)
            .height(0.5.dp)
            .background(AppleSeparator),
    )
}

/**
 * 一行设置项。
 *
 * @param subtitle 补充说明，**只在标题不足以说清时**才给（尽量一行写完）
 * @param onClick  整行可点时为非空；开关行传 null，避免滑动误触
 * @param trailing 右侧内容：[IjiSettingSwitch] / [IjiSettingValue] / 空
 */
@Composable
fun IjiSettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(horizontal = 20.dp, vertical = if (subtitle == null) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        trailing()
    }
}

/** 开关行尾：只在用户主动拨动时生效（整行不响应点击） */
@Composable
fun IjiSettingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onCheckedChange)
}

/** 取值行尾：当前值 + 箭头（纯展示，点击由整行承担）；[text] 为空时只留箭头 */
@Composable
fun IjiSettingValue(text: String? = null, chevron: Boolean = true) {
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    if (chevron) {
        Spacer(Modifier.width(8.dp))
        IjiTrailingIcon(MiuixIcons.Basic.ArrowRight)
    }
}

/**
 * 下拉选择行尾：当前值 + **上下双箭头**（Miuix basic 的 ArrowUpDown）。
 *
 * 与 [IjiSettingValue] 的 ›（前进箭头，语义「进入下一层」）区分：
 * 上下箭头的语义是「原地展开面板选择」，用于主题颜色 / 周开始日这类
 * 点击后原地弹出浮层面板的行。
 *
 * 别用 extended 的 ExpandLess / ExpandMore 叠放：那两个字形是四角括号，
 * 叠起来会看着像图标渲染残了。
 */
@Composable
fun IjiSettingDropdownValue(text: String? = null, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        IjiTrailingIcon(MiuixIcons.Basic.ArrowUpDown)
    }
}

/**
 * 行尾图标统一规格：高度跟随正文字号（含用户字体缩放），与旁边的取值文字一样高；
 * 宽度按 basic 图标 10×16 的 viewport 比例给，塞进正方形会被横向拉伸、笔画显粗。
 * 设置页与「我的」页的行尾箭头都走这里，保证高度、色调一致。
 */
@Composable
fun IjiTrailingIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
) {
    val h = MaterialTheme.typography.bodyMedium.fontSize.value.dp * LocalDensity.current.fontScale
    Icon(
        imageVector,
        contentDescription = null,
        tint = TextSecondary,
        modifier = modifier.size(width = h * 0.625f, height = h),
    )
}
