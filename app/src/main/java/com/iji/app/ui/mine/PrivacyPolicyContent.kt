package com.iji.app.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary

/**
 * 隐私政策正文（头部信息卡 + 各章节卡片）。
 *
 * 抽成 LazyListScope 扩展，让「首次启动同意页」与「我的 → 关于 → 隐私政策」共用同一份渲染，
 * 避免两处各自维护文案后出现不一致（尤其政策修订时）。
 */
fun LazyListScope.privacyPolicyItems(includeHeader: Boolean = true) {
    if (includeHeader) {
        item(key = "privacy_header") {
            top.yukonga.miuix.kmp.basic.Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                    MaterialTheme.colorScheme.surface,
                ),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                ) {
                    Text(
                        text = "i记 隐私政策",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "生效日期：$privacyEffectiveDate",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "联系邮箱：$contactEmail",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "本政策说明本应用如何处理你的信息、信息何时会离开设备，以及你可以行使的权利。",
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
    privacySections.forEachIndexed { index, section ->
        item(key = "privacy_$index") {
            top.yukonga.miuix.kmp.basic.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                cornerRadius = Radius.xxl,
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                    MaterialTheme.colorScheme.surface,
                ),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                ) {
                    Text(
                        text = section.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    section.paragraphs.forEachIndexed { pIndex, paragraph ->
                        val itemIndent = paragraph.startsWith("·") || paragraph.startsWith("(")
                        Text(
                            text = paragraph,
                            fontSize = if (itemIndent) 13.5.sp else 14.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = if (itemIndent) Modifier.padding(start = 8.dp) else Modifier,
                        )
                        if (pIndex < section.paragraphs.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}
