package com.iji.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary

/**
 * 隐私政策页：按章节分组，章节标题 + 正文段落，整体为可滚动卡片列表。
 */
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
) {
    MineSubPage(title = "隐私政策", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = ijiTopBarContentPadding(),
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
        ) {
            privacySections.forEachIndexed { index, section ->
                item(key = "privacy_$index") {
                    top.yukonga.miuix.kmp.basic.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (index == 0) 0.dp else 12.dp),
                        cornerRadius = Radius.xxl,
                        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                            MaterialTheme.colorScheme.surface,
                        ),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
                        ) {
                            Text(
                                text = section.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            section.paragraphs.forEachIndexed { pIndex, paragraph ->
                                Text(
                                    text = paragraph,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = TextSecondary,
                                )
                                if (pIndex < section.paragraphs.lastIndex) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
