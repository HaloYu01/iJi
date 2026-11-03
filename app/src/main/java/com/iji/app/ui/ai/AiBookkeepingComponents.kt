package com.iji.app.ui.ai

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iji.app.ai.AiBookkeepingService
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.StateSize
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.ijiButtonFocusRing
import com.iji.app.ui.theme.AppleTertiaryBackground
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import java.io.ByteArrayOutputStream

/**
 * AI 记账页的展示组件：结果卡、动作按钮、空态示例，以及送云端前的图片压缩。
 *
 * 与 `AiBookkeepingScreen.kt` 分开是为了让页面文件只留状态机与布局骨架——
 * 之前一屏 500 行里，取图/语音/解析/结果卡混在一起，改文案要往上翻半屏。
 */

/** 识别来源标记：只有图片路径会设置（文本可能命中本地规则，无法确定是否上云，故不标注） */
internal const val SOURCE_LOCAL = "本地识别"
internal const val SOURCE_CLOUD = "云端识别"

/**
 * 结果卡：类型 / 金额 / 分类 / 账户 / 备注 / 标签 + 两个动作。
 */
@Composable
internal fun ParseResultCard(
    bill: AiBookkeepingService.BillResult,
    ocrSource: String,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.xxl),
        colors = CardDefaults.cardColors(containerColor = AppleTertiaryBackground),
    ) {
        Column(Modifier.padding(20.dp)) {
            // 识别来源并入标题（只有图片路径会带来源）：图片去了哪里，用户有权知道
            SmallTitle(
                text = if (ocrSource.isBlank()) "解析结果" else "解析结果 · $ocrSource",
                insideMargin = PaddingValues(0.dp),
            )
            Spacer(Modifier.height(16.dp))

            // 转账既不是收入也不是支出：颜色不跟收支走，避免被误读成收入
            val (typeText, typeColor) = when (bill.type) {
                "income" -> "收入" to IncomeGreen
                "transfer" -> "转账" to TextSecondary
                else -> "支出" to MiuixPrimary
            }
            ResultRow("类型", typeText, typeColor)
            ResultRow("金额", formatMoney(bill.amountCents))
            ResultRow("分类", bill.categoryHint)
            if (bill.accountHint.isNotBlank()) ResultRow("账户", bill.accountHint)
            ResultRow("备注", bill.note)
            if (bill.tags.isNotEmpty()) ResultRow("标签", bill.tags.joinToString(" #") { "#$it" })

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("重新输入", Icons.Default.Refresh, compact = false, enabled = true) { onReset() }
                ActionButton("确认记账", icon = null, compact = false, enabled = true, primary = true) { onConfirm() }
            }
        }
    }
}

/**
 * 四个动作按钮共用一套尺寸 / 焦点圈 / 图标排版。
 *
 * 原先每个按钮各写 12 行（同样的 weight + focusRing + Icon + Spacer + Text），
 * 改一次样式要动四处；窄屏（<380dp）自动只留文字，避免按钮里的字被挤没。
 */
@Composable
internal fun RowScope.ActionButton(
    text: String,
    icon: ImageVector?,
    compact: Boolean,
    enabled: Boolean,
    primary: Boolean = false,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .ijiButtonFocusRing(shape = ContinuousRoundedRectangle(ButtonDefaults.CornerRadius)),
        enabled = enabled,
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
    ) {
        if (loading) {
            StateView(state = ContentState.Loading("解析中"), size = StateSize.Inline)
            return@Button
        }
        if (icon != null && !compact) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, maxLines = 1)
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.width(60.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

/**
 * 降采样 + JPEG 压缩 + base64。
 *
 * 手机原图动辄 4000px，直送模型会让请求体达到数 MB（接口拒收 / 超时），
 * 而小票上的金额数字在 1280px 长边下依然清晰，故统一压到 1280。
 */
internal fun encodeForOcr(bmp: Bitmap, maxPx: Int = 1280): String {
    val scale = (maxPx.toFloat() / maxOf(bmp.width, bmp.height)).coerceAtMost(1f)
    val target = if (scale < 1f) {
        Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    } else {
        bmp
    }
    val baos = ByteArrayOutputStream()
    target.compress(Bitmap.CompressFormat.JPEG, 80, baos)
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}
