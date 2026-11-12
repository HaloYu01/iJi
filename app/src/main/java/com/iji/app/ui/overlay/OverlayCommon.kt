package com.iji.app.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.R
import com.iji.app.data.model.TransactionType
import kotlinx.coroutines.delay

// ── 两张悬浮卡共用的字体（品牌字 / 正文 / 中量级数字）──
internal val ActorFamily = FontFamily(Font(R.font.actor_regular, FontWeight.Normal))
internal val InterRegular = FontFamily(Font(R.font.inter_regular, FontWeight.Normal))
internal val InterMedium = FontFamily(Font(R.font.inter_medium, FontWeight.Medium))

/** 金额语义色：消费红 / 收入绿 / 转账蓝。详细、简洁两张卡共用同一套口径 */
internal fun amountColor(type: TransactionType): Color = when (type) {
    TransactionType.INCOME -> OverlayPalette.IncomeText
    TransactionType.TRANSFER -> OverlayPalette.TransferText
    else -> OverlayPalette.ExpenseText
}

/**
 * 卡片配色：全部围绕 app 主色 `#2177E8`（冷蓝）。
 *
 * 之前是绿色系，悬浮在别人应用上既像微信又和 app 内其它页面撞色；
 * 现在只有**一个**强调色（主色蓝），红/绿仅用于金额语义，不再参与选中态。
 */
internal object OverlayPalette {

    /** 卡片底：白 → 极淡冷蓝的竖向渐变（不用实色才不显灰） */
    val CardGradient = listOf(Color(0xFFFDFEFF), Color(0xFFECF2FC))

    /** 行/备注的浅色卡底（白 80%，压在渐变上形成一级层次） */
    val SurfaceRaised = Color(0xCCFFFFFF)

    /** 统一的 1dp 描边：所有可点区域共用，靠边界而不是靠颜色区分"能点" */
    val CardBorder = Color(0xFFDEE7F5)

    val Ink = Color(0xFF16202E)
    val Label = Color(0xFF33415A)
    val Secondary = Color(0xFF5D6B80)

    /** 说明性小字：对比度 ≥ 4.5:1 */
    val Hint = Color(0xFF6B7A8C)
    val Tertiary = Color(0xFF8A97A8)

    /** 全卡唯一强调色 = app 主色；保存按钮、选中态、勾选态共用 */
    val Accent = Color(0xFF2177E8)
    val OnAccent = Color.White
    val CheckBorder = Color(0xFFCBD7E6)
    val PlaceholderDot = Color(0xFFD5DDEA)

    /** 金额：红涨绿跌之外，转账用品牌蓝，三种语义互不混淆 */
    val ExpenseText = Color(0xFFE5484D)
    val IncomeText = Color(0xFF0E8A64)
    val TransferText = Color(0xFF2557D6)

    /** 类型胶囊：浅底 + 深字，小字对比度达标 */
    val ExpenseChipBg = Color(0xFFFDECEC)
    val ExpenseChipInk = Color(0xFFC0393E)
    val IncomeChipBg = Color(0xFFE8F5EE)
    val IncomeChipInk = Color(0xFF0B7A5A)
    val TransferChipBg = Color(0xFFEAF0FF)
    val TransferChipInk = Color(0xFF2557D6)

    /** 疑似重复标记：橙色警示，独立于主色与红绿语义 */
    val WarningInk = Color(0xFF9A5B00)
    val WarningBg = Color(0xFFFFF1DE)
}

/** 当前时刻折算成「当天第几分钟」，用作卡片里时分的默认值 */
internal fun nowMinuteOfDay(): Int {
    val now = java.time.LocalTime.now()
    return now.hour * 60 + now.minute
}

/**
 * 按压缩放反馈：按下轻微缩小（0.97），抬起回位，替代默认涟漪。
 *
 * 悬浮窗浮在别人应用上，涟漪在渐变卡底上显得"糊"；缩放反馈更贴近
 * 玻璃卡的材质感，也是整卡可点区域统一的手势语言。[enabled] = false
 * 时不缩放也不响应点击。
 */
@Composable
internal fun Modifier.pressableScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = tween(120),
        label = "pressScale",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * 卡片外壳：渐变底 + 白描边 + 阴影 + 出/退场动效，详细与简洁两种模式共用。
 *
 * - 出现：轻微上浮 + 淡入，避免悬浮窗"啪"地砸出来；
 * - 退出：内容调 [content] 收到的 `onRequestClose` → 整卡淡出下沉，
 *   动画播完才回调 [onDismissed]（真正摘掉窗口），关卡片不再是瞬间消失。
 *
 * 外层留 6dp 给阴影：窗口宽 = 屏宽 − 24dp，不留白的话左右阴影会被窗口边界切掉。
 */
@Composable
internal fun OverlayShell(
    onDismissed: () -> Unit,
    content: @Composable ColumnScope.(onRequestClose: () -> Unit) -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    LaunchedEffect(exiting) {
        if (exiting) {
            delay(150)
            onDismissed()
        }
    }
    val visible = appeared && !exiting
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (exiting) 140 else 200),
        label = "shellAlpha",
    )
    val cardOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else (-12).dp,
        animationSpec = tween(if (exiting) 140 else 240),
        label = "shellY",
    )
    val shape = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxWidth().padding(6.dp)) {
        Column(
            Modifier
                .offset(y = cardOffsetY)
                .graphicsLayer { alpha = cardAlpha }
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = shape, clip = false)
                .clip(shape)
                .background(Brush.verticalGradient(OverlayPalette.CardGradient))
                .border(1.dp, Color.White.copy(alpha = 0.75f), shape)
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 12.dp),
        ) {
            content { exiting = true }
        }
    }
}

/** 品牌行：logo + i记，右侧徽章由调用方按模式传入（多笔序号 / 笔数 / 完成勾） */
@Composable
internal fun OverlayBrandRow(badge: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_iji_logo),
            contentDescription = "i记",
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "i记",
            maxLines = 1,
            style = TextStyle(
                fontFamily = ActorFamily,
                fontSize = 17.sp,
                letterSpacing = 0.2.sp,
                color = OverlayPalette.Ink,
            ),
        )
        Spacer(Modifier.weight(1f))
        if (badge != null) {
            Text(
                text = badge,
                style = TextStyle(
                    fontFamily = InterRegular,
                    fontSize = 10.5.sp,
                    color = OverlayPalette.Secondary,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(OverlayPalette.Accent.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** 细进度条：多笔确认时的"处理到第几笔"，存/跳都算已处理 */
@Composable
internal fun OverlayProgress(done: Int, total: Int) {
    if (total <= 0) return
    val fraction by animateFloatAsState(
        targetValue = (done.toFloat() / total).coerceIn(0f, 1f),
        animationSpec = tween(220),
        label = "overlayProgress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(OverlayPalette.Accent.copy(alpha = 0.10f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(OverlayPalette.Accent.copy(alpha = 0.65f)),
        )
    }
}

/**
 * 底部主/次按钮（两张卡共用）。
 *
 * [container] / [content] 完全由调用方给：主按钮主色实底、次按钮描边，
 * 以及"确认忽略"时的红色警示变体，都靠传参组合，不再各自造按钮。
 */
@Composable
internal fun FooterButton(
    text: String,
    modifier: Modifier = Modifier,
    container: Color,
    content: Color,
    outlined: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val background by animateColorAsState(
        if (enabled) container else container.copy(alpha = 0.35f),
        tween(160),
        label = "btnBg",
    )
    val foreground by animateColorAsState(
        if (enabled) content else content.copy(alpha = 0.6f),
        tween(160),
        label = "btnFg",
    )
    Box(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(background)
            .then(
                if (outlined) {
                    Modifier.border(1.dp, OverlayPalette.CardBorder, shape)
                } else {
                    Modifier
                },
            )
            .pressableScale(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = InterRegular,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                color = foreground,
            ),
        )
    }
}

/**
 * 备注行内编辑（两张卡共用）：聚焦 + 弹输入法的时序都在这里处理，上限 50 字。
 *
 * 焦点链与窗口 flag 生效都有几百毫秒级的延迟，所以先 delay 80ms 再请求焦点、
 * [BillOverlay.showIme] 内部还会分两次补试。
 */
@Composable
internal fun NoteInlineEditor(initial: String, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
        BillOverlay.showIme()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(50) },
            singleLine = true,
            cursorBrush = SolidColor(OverlayPalette.Accent),
            textStyle = TextStyle(fontSize = 13.sp, color = OverlayPalette.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(text.trim()) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
        Text(
            text = "确定",
            style = TextStyle(fontSize = 13.sp, color = OverlayPalette.Accent),
            modifier = Modifier
                .clickable { onCommit(text.trim()) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
