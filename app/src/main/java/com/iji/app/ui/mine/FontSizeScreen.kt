package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ui.FontScaleState
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults

/**
 * 字体大小快捷档：锚点值与 [FontScaleState.label] 的分档口径一致
 * （≤13 小 / ≤17 标准 / ≤20 大 / 其余特大），胶囊高亮跟随当前所在档位。
 */
private val FONT_PRESETS = listOf(
    "小" to FontScaleState.MIN_SP,
    "标准" to FontScaleState.BASE_SP,
    "大" to 20f,
    "特大" to FontScaleState.MAX_SP,
)

/** 字体大小设置页 */
@Composable
fun FontSizeScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    // 以全局状态为准（同一份主题消费的值），避免"这页显示的和实际生效的不是一个数"
    var fontSize by remember { mutableStateOf(FontScaleState.fontSizeSp) }

    MineSubPage(title = "字体大小", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 预览卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                ) {
                    Text("预览", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "本月收入 ¥15,200.00",
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "餐饮 -¥28.50",
                        fontSize = (fontSize - 2).sp,
                        color = AppleRed,
                    )
                }
            }

            // 滑动条卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("字体大小", style = MaterialTheme.typography.bodyLarge)
                        // 档位 + 具体值一起给：光有 sp 数字用户不知道算大算小
                        Text(
                            "${FontScaleState.label(fontSize)} · ${fontSize.toInt()}sp",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MiuixPrimary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        // 松手才落盘（拖动过程中每个刻度都写 DataStore 太浪费），
                        // 但**立刻生效**：写进 FontScaleState 后主题的 fontScale 马上跟着变，
                        // 用户当场就能看到字号变化，而不是保存后回退页面才发现。
                        onValueChangeFinished = {
                            FontScaleState.setFontSize(context, fontSize)
                        },
                        valueRange = FontScaleState.MIN_SP..FontScaleState.MAX_SP,
                        // 12..24 每 1sp 一档（13 档）
                        steps = 11,
                        // 只在基准档（16sp）留一个键点：铺 13 个点视觉太碎，
                        // 档位吸附仍由 steps 保证，键点只做「默认值在这」的参照
                        keyPoints = listOf(FontScaleState.BASE_SP),
                        showKeyPoints = true,
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                    )
                    Spacer(Modifier.height(12.dp))
                    // 快捷档：一键落到常用字号，写入即生效（与拖动松手同一条落盘路径）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FONT_PRESETS.forEach { (name, presetSp) ->
                            PresetChip(
                                label = name,
                                active = FontScaleState.label(fontSize) == name,
                                onClick = {
                                    fontSize = presetSp
                                    FontScaleState.setFontSize(context, presetSp)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 保存按钮
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 拖动/点档位时已写入，这里再写一次是保险（键盘/无障碍调整等场景）
                            FontScaleState.setFontSize(context, fontSize)
                            snackbar.show("字体大小已保存")
                            onBack()
                        }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("保存", color = MiuixPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 快捷档胶囊：与对账页筛选胶囊同一套视觉 —— 选中态主色浅底 + 描边 + 勾号（状态不只靠颜色） */
@Composable
private fun PresetChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.xl)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (active) MiuixPrimary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
            .then(if (active) Modifier.border(1.dp, MiuixPrimary.copy(alpha = 0.4f), shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { selected = active }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (active) "✓ $label" else label,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) MiuixPrimary else TextSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
}
