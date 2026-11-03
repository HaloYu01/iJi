package com.iji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults

/**
 * 分组卡片的**组标题**：卡片外的一行 12sp 次要色文字（如「账户信息」「余额」）。
 *
 * 与卡片内的字段名（14sp onSurface）形成明确的两级层级 ——
 * 之前分组标题、字段名、帮助说明全用同一个 12sp 灰字，视觉上是一堵灰墙。
 */
@Composable
fun IjiFormSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, bottom = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )
}

/**
 * 表单分组卡片：圆角对齐全站（[Radius.sheet]），底色用 surface。
 *
 * 组内多行之间用 [IjiFormDivider] 分隔，形成 iOS/设置页那种"一张卡里几行"的结构，
 * 比"每个字段自带一个输入框底色"更容易扫读分组。
 */
@Composable
fun IjiFormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sheet))
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

/** 组内发丝线：左侧留 16dp 对齐文字起点（图标列之后的缩进由调用方自己给） */
@Composable
fun IjiFormDivider(startPadding: androidx.compose.ui.unit.Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startPadding),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    )
}

/**
 * 表单里统一的「开关行」：左标题 + 说明，右开关。
 *
 * 此前同一形态在 [com.iji.app.ui.add.AddTransactionSheet]（MarkSwitchRow）与
 * 添加账户页各写一份，样式已经开始漂移（一个用 onSurface、一个用默认色）。
 * 收敛到一处后，两个页面的开关行完全一致。
 *
 * @param title 主标题（如「计入净资产」）
 * @param desc  副说明（可空）
 */
@Composable
fun IjiSwitchRow(
    title: String,
    desc: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!desc.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * 表单里统一的「分类胶囊」：**换行铺满**的一排小圆角标签，单选。
 *
 * 用 [FlowRow] 而不是横向 LazyRow：分类只有 8 项，横向滚动会把最后几项藏在屏幕外
 * （右缘硬切、没有可滚动的暗示），换行则一次全部可见。
 *
 * 点击区统一兜到 48dp（外层 [heightIn]），胶囊本身的视觉高度保持紧凑 ——
 * 背景与文字放在内层，两者分离才能做到"点得准但不变高"。
 *
 * @param items    选项
 * @param selected 当前选中项；传 null 表示"这一排没有选中任何项"（如二级分类在父级被选中时）
 * @param onSelect 选中回调
 * @param labelOf  选项文案
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> IjiCategoryChips(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Radius.xxl))
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.xxl))
                        .background(
                            if (isSelected) MiuixPrimary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surface,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        labelOf(item),
                        color = if (isSelected) MiuixPrimary else TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * 主要操作按钮（页面底部的主按钮，如「创建账户」「保存修改」）。
 *
 * 统一三件事：形状与聚焦描边一致（[ijiButtonFocusRing]）、水波纹与按压缩放由主题层
 * `LocalIndication` 提供、禁用态由调用方自己控制。
 *
 * @param text    按钮文案
 * @param onClick 点击回调
 * @param enabled 是否可点（提交中置 false，防连点）
 */
@Composable
fun IjiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        // Miuix 的 Button 默认用 secondaryVariant（浅灰）作底色，**禁用态与可用态的差别
        // 只有一点点灰度**，主操作按钮看起来永远像不可点。主按钮改用 primary 配色，
        // 禁用态落到 disabledPrimaryButton，一眼能看出「现在能不能点」。
        colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = modifier.ijiButtonFocusRing(
            shape = ContinuousRoundedRectangle(ButtonDefaults.CornerRadius),
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 表单输入框：**无彩色焦点边框**，靠淡底色 + 聚焦时底色加深表达焦点。
 *
 * 为什么不用 Miuix 自带的聚焦描边：
 * - 1.5dp 的蓝色描边在卡片里像"框里嵌了一个框"，与卡片/发丝线的视觉语言打架；
 * - 描边宽度是组件内部写死的（`if (isFocused) BorderWidth else 0.dp`），
 *   外部只能改颜色、改不了宽度，留着半透明描边反而更脏。
 *
 * 具体做法：`borderColor` 设为透明（聚焦时那 1.5dp 直接不可见），
 * 用 `onFocusChanged` 记录焦点，把底色从 8% 提到 13% —— 没有描边之后，
 * 浅底色就是唯一的输入区锚点，太淡（5% 以下）会让整块表单变成"一堆浮着的文字"。
 *
 * 字段名不放这里 —— 用 [IjiFormSectionTitle] 之外的 `FieldLabel` 放在框外，
 * 这里只放**占位符**（`useLabelAsPlaceholder`），避免浮动小字贴住输入值。
 *
 * @param placeholder 占位文字（输入后自动让位）
 */
@Composable
fun IjiFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    // 底色由外层自己画：Miuix 的 TextField 内部背景在这套主题下画不出来（实测纯白），
    // 而"输入区"是去掉描边后唯一的视觉锚点，必须可控。
    val background = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (focused) 0.12f else 0.07f,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                labelColor = TextSecondary,
                // 透明 = 聚焦时那圈 1.5dp 描边不可见（宽度写死在组件内，只能从颜色上抹掉）
                borderColor = Color.Transparent,
            ),
            // 左右 14dp：文字与色块边缘留白；上下 12dp：输入值上下留白均匀
            insideMargin = DpSize(14.dp, 12.dp),
            label = placeholder,
            useLabelAsPlaceholder = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
    }
}
