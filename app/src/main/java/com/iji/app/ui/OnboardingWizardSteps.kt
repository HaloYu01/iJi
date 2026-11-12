package com.iji.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iji.app.ai.AiConfig
import com.iji.app.data.model.Currency
import com.iji.app.ui.components.IjiPrimaryButtonBlue
import com.iji.app.ui.mine.CheckItem
import com.iji.app.ui.mine.CheckStatus
import com.iji.app.ui.theme.MiuixPrimary
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults

/**
 * 开局引导页的专属配色：**只有蓝、黑、白**（白是背景）。
 *
 * 不读 MaterialTheme.colorScheme —— 引导页固定浅色，深色模式 / 跟随系统下也必须是
 * 纯白底黑字。蓝统一用全局主按钮蓝 [IjiPrimaryButtonBlue]（视觉稿 #5293FC），
 * 次级文字与中性填充用「黑」的低透明度表达，所有淡态不引入灰阶 token 或绿 / 橙语义色。
 */
internal object OnboardingPalette {
    val Blue: Color = IjiPrimaryButtonBlue

    /** 头像环等渐变的深端：退回主题蓝，让亮蓝不显得浮 */
    val BlueDeep: Color = MiuixPrimary

    val Black: Color = Color.Black
    val White: Color = Color.White

    /** 「黑」的透明度只用于次级文字（激活页的排版层次），不作为新的色相 */
    val Black70: Color = Color.Black.copy(alpha = 0.72f)
    val Black55: Color = Color.Black.copy(alpha = 0.55f)
    val Black40: Color = Color.Black.copy(alpha = 0.40f)

    /** 蓝的透明度家族：填充 / 选中底 / 描边 / 发丝线 / 阴影 */
    val FaintBlue: Color = Blue.copy(alpha = 0.14f)
    val SofterBlue: Color = Blue.copy(alpha = 0.06f)
    val BorderBlue: Color = Blue.copy(alpha = 0.32f)
    val Hairline: Color = Blue.copy(alpha = 0.10f)
    val Shadow: Color = Blue.copy(alpha = 0.14f)
}

private val CardShape = ContinuousRoundedRectangle(24.dp)
private val RowShape = ContinuousRoundedRectangle(16.dp)
private val BadgeShape = ContinuousRoundedRectangle(22.dp)

/**
 * 每步开头的「激活页头图」：**左右结构** —— 放大的图标徽章在左，标题（+ 角标）与说明在右，
 * 整行以图标高度为基准垂直居中，行高随文字自然对齐，视觉上高度统一。
 */
@Composable
internal fun StepHero(meta: OnboardingStepMeta) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(BadgeShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            OnboardingPalette.Blue.copy(alpha = 0.16f),
                            OnboardingPalette.Blue.copy(alpha = 0.07f),
                        ),
                    ),
                )
                .border(1.dp, OnboardingPalette.Hairline, BadgeShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = meta.icon,
                contentDescription = null,
                tint = OnboardingPalette.Blue,
                modifier = Modifier.size(34.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = meta.title,
                    color = OnboardingPalette.Black,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                meta.badge?.let { badge ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(OnboardingPalette.FaintBlue)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = badge,
                            color = OnboardingPalette.Blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                text = meta.desc,
                color = OnboardingPalette.Black55,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

/** 分组白卡：squircle + 发丝线边界 + 极淡的蓝色柔和投影，激活页里承载表单的「面」。 */
@Composable
internal fun StepGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = CardShape,
                ambientColor = OnboardingPalette.Shadow,
                spotColor = OnboardingPalette.Shadow,
            )
            .clip(CardShape)
            .background(OnboardingPalette.White)
            .border(1.dp, OnboardingPalette.Hairline, CardShape)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 卡片组上方的小节标签：主蓝小字，给连续的卡片流一个节奏点。 */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        color = OnboardingPalette.Blue,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 卡片内部的字段标题。 */
@Composable
internal fun FieldLabel(text: String) {
    Text(
        text,
        color = OnboardingPalette.Black,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun StepHint(text: String) {
    Text(text, color = OnboardingPalette.Black55, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
internal fun StepDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(OnboardingPalette.Hairline),
    )
}

/**
 * 单选项：淡蓝填充的 squircle，选中时边框点亮 + 右端出现蓝底白勾徽章。
 * 颜色全程动画过渡；描边宽度恒定 1.5dp，切换时不跳尺寸。
 */
@Composable
internal fun ChoiceRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) OnboardingPalette.FaintBlue else OnboardingPalette.SofterBlue,
        animationSpec = tween(180),
        label = "choiceBackground",
    )
    val border by animateColorAsState(
        targetValue = if (selected) OnboardingPalette.Blue else Color.Transparent,
        animationSpec = tween(180),
        label = "choiceBorder",
    )
    Row(
        modifier = modifier
            .clip(RowShape)
            .border(1.5.dp, border, RowShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = OnboardingPalette.Black,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) OnboardingPalette.Blue else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (selected) OnboardingPalette.Blue else OnboardingPalette.Black40,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(
                    "✓",
                    color = OnboardingPalette.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun ChoiceGrid(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    columns: Int = 2,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label) ->
                    ChoiceRow(
                        text = label,
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 小开关：轨道颜色动画 + 弹簧滑动拇指（带一点投影），比原来的「色块 + 圆点」更像系统件。 */
@Composable
internal fun MiniSwitch(checked: Boolean) {
    val track by animateColorAsState(
        targetValue = if (checked) OnboardingPalette.Blue else OnboardingPalette.SofterBlue,
        animationSpec = tween(180),
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "switchThumb",
    )
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, OnboardingPalette.Hairline, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = CircleShape,
                    ambientColor = OnboardingPalette.Shadow,
                    spotColor = OnboardingPalette.Shadow,
                )
                .clip(CircleShape)
                .background(OnboardingPalette.White),
        )
    }
}

@Composable
internal fun ToggleRow(
    text: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = OnboardingPalette.Black,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        MiniSwitch(checked)
    }
}

/**
 * 输入框：淡蓝填充 + 聚焦点亮主蓝描边（颜色动画过渡），失焦时退回发丝线。
 * 仍然自画底色与描边——Miuix 的 TextField 内部背景在这套主题下画不出来。
 */
@Composable
internal fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val border by animateColorAsState(
        targetValue = if (focused) OnboardingPalette.Blue else OnboardingPalette.Hairline,
        animationSpec = tween(150),
        label = "inputBorder",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = OnboardingPalette.Black, fontSize = 15.sp),
            cursorBrush = SolidColor(OnboardingPalette.Blue),
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            interactionSource = interactionSource,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RowShape)
                        .border(
                            width = if (focused) 1.5.dp else 1.dp,
                            color = border,
                            shape = RowShape,
                        )
                        .background(OnboardingPalette.SofterBlue)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = OnboardingPalette.Black40, fontSize = 14.sp)
                    }
                    inner()
                }
            },
        )
        trailing()
    }
}

/** 32dp 圆形小按钮：淡蓝底 + 主蓝符号，用于步进器与清除。 */
@Composable
internal fun SmallButton(
    text: String,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(OnboardingPalette.FaintBlue)
            // 纯符号按钮没有可读文本，必须补无障碍描述，否则读屏只会念出符号
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = OnboardingPalette.Blue,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun NumberRow(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = OnboardingPalette.Black,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        SmallButton("−") { onChange((value - 1).coerceIn(range)) }
        Text(
            "$value$suffix",
            color = OnboardingPalette.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        SmallButton("+") { onChange((value + 1).coerceIn(range)) }
    }
}

/**
 * 主题三选一：同一个 Row 里的三张独立卡片，缩略图在上、文字在卡片内下方。
 * 选中 = 主蓝描边 + 淡蓝底 + 标题加粗带勾；缩略图按明暗分别绘制导航行 / 正文行 / 卡片块。
 */
@Composable
internal fun ThemeCards(theme: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            "system" to "跟随系统",
            "light" to "浅色",
            "dark" to "深色",
        ).forEach { (value, label) ->
            ThemeCard(
                label = label,
                dark = value == "dark",
                selected = theme == value,
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeCard(
    label: String,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) OnboardingPalette.FaintBlue else OnboardingPalette.SofterBlue,
        animationSpec = tween(180),
        label = "themeCardBackground",
    )
    val border by animateColorAsState(
        targetValue = if (selected) OnboardingPalette.Blue else Color.Transparent,
        animationSpec = tween(180),
        label = "themeCardBorder",
    )
    // 迷你屏幕预览里的三种「内容色」：深浅主题各自成立
    val previewLineStrong =
        if (dark) OnboardingPalette.White.copy(alpha = 0.45f) else OnboardingPalette.Black.copy(alpha = 0.35f)
    val previewBlock =
        if (dark) OnboardingPalette.White.copy(alpha = 0.08f) else OnboardingPalette.Black.copy(alpha = 0.06f)
    Column(
        modifier = modifier
            .clip(RowShape)
            .border(1.5.dp, border, RowShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(ContinuousRoundedRectangle(12.dp))
                .background(if (dark) OnboardingPalette.Black else OnboardingPalette.White)
                .border(
                    1.dp,
                    if (dark) OnboardingPalette.Black.copy(alpha = 0.25f) else OnboardingPalette.Hairline,
                    ContinuousRoundedRectangle(12.dp),
                )
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.70f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(OnboardingPalette.Blue),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.50f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(previewLineStrong),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(ContinuousRoundedRectangle(5.dp))
                        .background(previewBlock),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = OnboardingPalette.Black,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── 1. 个性化 ──────────────────────────────────────────────────────────────

@Composable
internal fun StepPersonalize(
    draft: OnboardingDraft,
    update: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
    onPickAvatar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("外观")
        StepGroup {
            FieldLabel("主题")
            ThemeCards(
                theme = draft.theme,
                onSelect = { value -> update { d -> d.copy(theme = value) } },
            )
            StepDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldLabel("字号")
                Spacer(Modifier.weight(1f))
                // 档位 + 具体值一起给：光有 sp 数字用户不知道算大算小
                Text(
                    "${FontScaleState.label(draft.fontSizeSp)} · ${draft.fontSizeSp.toInt()}sp",
                    color = OnboardingPalette.Blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            // 与设置里的「字体大小」页同一个 Miuix 滑杆：只在基准档留一个键点，不铺满刻度；
            // 颜色显式指定，避免深色主题下的 Miuix 配色把纯白引导页染花
            Slider(
                value = draft.fontSizeSp,
                onValueChange = { value -> update { d -> d.copy(fontSizeSp = value) } },
                valueRange = FontScaleState.MIN_SP..FontScaleState.MAX_SP,
                steps = 11,
                keyPoints = listOf(FontScaleState.BASE_SP),
                showKeyPoints = true,
                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                colors = SliderDefaults.sliderColors(
                    foregroundColor = OnboardingPalette.Blue,
                    backgroundColor = OnboardingPalette.FaintBlue,
                    thumbColor = OnboardingPalette.White,
                    keyPointColor = OnboardingPalette.BorderBlue,
                    keyPointForegroundColor = OnboardingPalette.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "预览 ¥ 128.00 · 午餐",
                color = OnboardingPalette.Black,
                fontSize = draft.fontSizeSp.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SectionLabel("个人资料")
        StepGroup {
            FieldLabel("昵称")
            InputRow(
                value = draft.nickname,
                onValueChange = { v -> update { d -> d.copy(nickname = v) } },
                placeholder = "留空则显示默认昵称",
            )
            StepDivider()
            FieldLabel("头像")
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 双层圆环：主蓝 → 主题蓝的渐变描边 + 白色内芯，比单色描边更有「激活页」质感
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(OnboardingPalette.Blue, OnboardingPalette.BlueDeep),
                            ),
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(OnboardingPalette.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (draft.avatarUri.isNotBlank()) {
                            AsyncImage(
                                model = draft.avatarUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = OnboardingPalette.Blue,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RowShape)
                        .background(OnboardingPalette.SofterBlue)
                        .clickable(onClick = onPickAvatar)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddAPhoto,
                            contentDescription = null,
                            tint = OnboardingPalette.Blue,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "选择头像",
                            color = OnboardingPalette.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            StepHint("头像可以随时在「我的」页更换。")
        }
    }
}

// ── 2. 记账口径 ────────────────────────────────────────────────────────────

@Composable
internal fun StepAccounting(
    draft: OnboardingDraft,
    update: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("币种")
        StepGroup {
            ChoiceGrid(
                options = Currency.all.map { it.code to "${it.code} ${it.name}" },
                selected = draft.currencyCode,
                onSelect = { update { d -> d.copy(currencyCode = it) } },
                columns = 2,
            )
            StepHint("币种只换金额符号，不做汇率换算、不动已记录的金额。")
        }

        SectionLabel("统计区间")
        StepGroup {
            NumberRow(
                label = "每月从几号开始算",
                value = draft.monthStartDay,
                range = 1..28,
                suffix = " 号",
                onChange = { update { d -> d.copy(monthStartDay = it) } },
            )
            StepDivider()
            FieldLabel("周开始日")
            ChoiceGrid(
                options = listOf("1" to "周一", "7" to "周日"),
                selected = draft.weekStartDay.toString(),
                onSelect = { update { d -> d.copy(weekStartDay = it.toInt()) } },
            )
            StepDivider()
            ToggleRow("24 小时制", draft.is24Hour) { update { d -> d.copy(is24Hour = it) } }
        }
    }
}

// ── 3. AI 服务 ─────────────────────────────────────────────────────────────

@Composable
internal fun StepAi(
    draft: OnboardingDraft,
    update: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
    onClearKey: () -> Unit,
    testing: Boolean,
    testMessage: String?,
    onTest: () -> Unit,
) {
    val provider = AiConfig.providers.firstOrNull { it.id == draft.aiProviderId } ?: AiConfig.providers.first()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("接入配置")
        StepGroup {
            FieldLabel("服务商")
            ChoiceGrid(
                options = AiConfig.providers.map { it.id to it.name },
                selected = draft.aiProviderId,
                onSelect = { id ->
                    val next = AiConfig.providers.firstOrNull { it.id == id } ?: AiConfig.providers.first()
                    update { d ->
                        d.copy(
                            aiProviderId = id,
                            aiUrl = if (id == "custom") "" else next.defaultUrl,
                            aiModel = if (id == "custom") "" else next.defaultModel,
                        )
                    }
                },
            )
            StepDivider()
            FieldLabel("API Key")
            InputRow(
                value = draft.aiKey,
                onValueChange = { update { d -> d.copy(aiKey = it) } },
                placeholder = if (draft.aiKey.isBlank()) "留空 = 不使用 AI" else "已配置",
                // 密钥一律掩码，不提供「显」的切换：没有在屏幕上明文回显的必要
                password = true,
                trailing = {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        SmallButton(text = "✕", contentDescription = "清除密钥") { onClearKey() }
                    }
                },
            )
            if (draft.aiKey.isNotBlank()) {
                StepHint("密钥始终以掩码显示；点右侧「✕」可清除，清空后不再联网。")
            }
            StepDivider()
            FieldLabel("接口地址")
            InputRow(
                value = draft.aiUrl,
                onValueChange = { update { d -> d.copy(aiUrl = it) } },
                placeholder = provider.defaultUrl.ifBlank { "自定义服务必填" },
            )
            FieldLabel("模型名称")
            InputRow(
                value = draft.aiModel,
                onValueChange = { update { d -> d.copy(aiModel = it) } },
                placeholder = provider.defaultModel.ifBlank { "自定义服务必填" },
            )
            // 测试连接：淡蓝大按钮，测试中在左侧转一个细圈
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RowShape)
                    .background(OnboardingPalette.FaintBlue)
                    .clickable { if (!testing) onTest() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            color = OnboardingPalette.Blue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = if (testing) "测试中…" else "测试连接",
                        color = OnboardingPalette.Blue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            testMessage?.let { message -> StepHint(message) }
        }

        SectionLabel("AI 功能")
        StepGroup {
            ToggleRow("文本识别用 AI", draft.aiText) { update { d -> d.copy(aiText = it) } }
            ToggleRow("图片识别用 AI", draft.aiVision) { update { d -> d.copy(aiVision = it) } }
            ToggleRow("AI 识别分类", draft.aiCategory) { update { d -> d.copy(aiCategory = it) } }
            ToggleRow("账户识别用 AI", draft.aiAccount) { update { d -> d.copy(aiAccount = it) } }
            StepHint("没有配置 API Key 时，以上开关都不会触发联网；文本识别自动降级本地规则。")
        }
    }
}

// ── 4. 权限 ────────────────────────────────────────────────────────────────

@Composable
internal fun StepPermissions(
    items: List<CheckItem>,
    onAction: (CheckItem) -> Unit,
) {
    val okCount = items.count { it.status == CheckStatus.OK }
    val neededCount = items.count { it.status != CheckStatus.SKIP }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("权限状态")
        StepGroup {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$okCount / $neededCount 项就绪",
                    color = OnboardingPalette.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (neededCount > 0 && okCount == neededCount) {
                    Text(
                        "全部就绪 ✓",
                        color = OnboardingPalette.Blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            // 就绪进度条：授权回来后进度平滑涨过去
            val fraction = if (neededCount == 0) 1f else okCount.toFloat() / neededCount
            val progress by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(400),
                label = "permissionProgress",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(OnboardingPalette.FaintBlue),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(OnboardingPalette.Blue),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    PermissionRow(item = item, onAction = onAction)
                }
            }
            StepHint(
                "点「需处理 / 可选」的项会直接打开对应的系统开关，不经过中间页；" +
                    "授权后返回本页会自动重新检测。",
            )
        }
    }
}

@Composable
private fun PermissionRow(item: CheckItem, onAction: (CheckItem) -> Unit) {
    // 无需动作（已就绪）的行不可点，避免「点了没反应」
    val actionable = item.actionLabel != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            // 就绪项淡蓝底 + 蓝徽标，未就绪项中性底，不再用 2dp 粗描边区分
            .background(
                if (item.status == CheckStatus.OK) OnboardingPalette.SofterBlue else Color.Transparent,
            )
            .clickable(enabled = actionable) { onAction(item) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.title,
            color = OnboardingPalette.Black,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        StatusBadge(item.status)
        if (actionable) {
            Spacer(Modifier.width(6.dp))
            Text(
                "›",
                color = OnboardingPalette.Blue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: CheckStatus) {
    val (background, foreground, label) = when (status) {
        CheckStatus.OK -> Triple(OnboardingPalette.FaintBlue, OnboardingPalette.Blue, "已就绪")
        CheckStatus.WARN -> Triple(OnboardingPalette.Black.copy(alpha = 0.08f), OnboardingPalette.Black70, "需处理")
        CheckStatus.SKIP -> Triple(OnboardingPalette.Black.copy(alpha = 0.08f), OnboardingPalette.Black55, "可选")
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── 5. 提醒 ────────────────────────────────────────────────────────────────

@Composable
internal fun StepReminder(
    draft: OnboardingDraft,
    update: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
    notificationReady: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("每日提醒")
        StepGroup {
            ToggleRow("每天提醒我记一笔", draft.reminderOn) { update { d -> d.copy(reminderOn = it) } }
            // 打开后时刻选择平滑展开，而不是突然长出一行
            AnimatedVisibility(
                visible = draft.reminderOn,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepDivider()
                    NumberRow(
                        label = "提醒时刻",
                        value = draft.reminderHour,
                        range = 0..23,
                        suffix = ":00",
                        onChange = { update { d -> d.copy(reminderHour = it) } },
                    )
                }
            }
            StepHint(
                if (notificationReady) {
                    "通知权限已就绪，提醒可以正常送达。"
                } else {
                    "通知权限尚未授予，提醒不会送达；可回到上一步去检查权限。"
                },
            )
        }
    }
}
