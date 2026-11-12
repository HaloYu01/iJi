// 展示用文字：宿主注册了换字动效就走它，否则原样 Text。
//
// 1:1 移植自 NexioSchedule miuix/src/commonMain/.../basic/MotionAwareText.kt。
// 它消费的 LocalMiuixTextMotion 见 [LocalMiuixTextMotion]；本工程当前不注册宿主，
// 恒走 Text 分支（与参考项目未开动效时的行为一致），保留结构以便后续接换字动效。

package com.iji.app.ui.components.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import top.yukonga.miuix.kmp.basic.Text

/** 换字动效宿主签名：带模糊过渡的换字渲染委托。 */
typealias MiuixTextMotionHost = @Composable (
    text: String,
    modifier: Modifier,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    textAlign: TextAlign?,
) -> Unit

/**
 * 换字动效宿主：非 null 时 [MotionAwareText] 把文案渲染委托给它（带模糊过渡的换字动效）。
 * 参考项目由 App 在根部注册；本工程暂未注册（null），文字原样渲染。
 */
val LocalMiuixTextMotion = compositionLocalOf<MiuixTextMotionHost?> { null }

/**
 * 展示用文字：宿主注册了换字动效就走它，否则原样 [Text]。
 *
 * 只用于**不会由用户逐字输入**的文案（偏好项标题 / 副标题、弹窗标题 / 摘要）；组件内所有
 * 这类文字都必须经过这里，避免同一段文案有的换字带模糊、有的硬切。
 */
@Composable
internal fun MotionAwareText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    val host = LocalMiuixTextMotion.current
    if (host != null) {
        host(text, modifier, color, fontSize, fontWeight, textAlign)
    } else {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
        )
    }
}
