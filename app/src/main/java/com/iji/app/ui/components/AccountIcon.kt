package com.iji.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 资产账户图标目录：微信 / 支付宝 + 10 个主流银行（品牌色圆徽 + 白色简称） */
object AccountCatalog {

    data class Meta(
        val iconKey: String,
        val label: String,
        val color: Long,
        val glyph: String,
    )

    val all = listOf(
        Meta("wechat", "微信", 0xFF07C160, "微"),
        Meta("alipay", "支付宝", 0xFF1677FF, "支"),
        Meta("icbc", "工商银行", 0xFFC7000B, "工"),
        Meta("ccb", "建设银行", 0xFF003C71, "建"),
        Meta("abc", "农业银行", 0xFF009B4C, "农"),
        Meta("boc", "中国银行", 0xFFB52125, "中"),
        Meta("bocom", "交通银行", 0xFF003DA5, "交"),
        Meta("cmb", "招商银行", 0xFFC41230, "招"),
        Meta("psbc", "邮储银行", 0xFF008C44, "邮"),
        Meta("citic", "中信银行", 0xFFB40016, "信"),
        Meta("spdb", "浦发银行", 0xFF004098, "浦"),
        Meta("cmbc", "民生银行", 0xFFEF1B23, "民"),
    )

    /**
     * 按 iconKey 取账户元信息。
     *
     * **找不到时返回兜底元信息，不再抛 NoSuchElementException**：
     * 新账户的 iconKey 是中文资产名（如「招商银行」「零钱通」），不在内置 12 项里，
     * 旧实现用 `all.first { ... }` 会直接崩溃。
     */
    fun of(key: String): Meta = all.firstOrNull { it.iconKey == key } ?: fallbackOf(key)

    private fun fallbackOf(key: String): Meta = Meta(key, key, 0xFF9CA3AF, key.take(1))
}

/** 账户圆形图标：微信为气泡图形，其余为品牌色圆底 + 白色简称 */
@Composable
fun AccountIcon(
    iconKey: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val meta = AccountCatalog.of(iconKey)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(meta.color)),
        contentAlignment = Alignment.Center,
    ) {
        if (iconKey == "wechat") {
            WeChatGlyph(bg = Color(meta.color))
        } else {
            Text(
                meta.glyph,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.42f).value.sp,
            )
        }
    }
}

/** 微信风格气泡：白气泡 + 绿眼睛（随容器尺寸自适应） */
@Composable
private fun WeChatGlyph(bg: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width * 0.70f
        val h = size.height * 0.58f
        val left = (size.width - w) / 2f
        val top = size.height * 0.13f
        val radius = h * 0.32f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(radius, radius),
        )
        // 气泡小尾巴
        val tail = Path().apply {
            moveTo(left + w * 0.16f, top + h * 0.88f)
            lineTo(left + w * 0.06f, top + h * 1.18f)
            lineTo(left + w * 0.40f, top + h * 0.92f)
            close()
        }
        drawPath(tail, Color.White)
        // 眼睛（绿色点）
        val eyeR = size.width * 0.052f
        drawCircle(bg, radius = eyeR, center = Offset(left + w * 0.32f, top + h * 0.42f))
        drawCircle(bg, radius = eyeR, center = Offset(left + w * 0.68f, top + h * 0.42f))
    }
}
