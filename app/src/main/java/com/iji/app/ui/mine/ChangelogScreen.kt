package com.iji.app.ui.mine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleOrange
import com.iji.app.ui.theme.ApplePurple
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/**
 * 更新日志页。
 *
 * 格式统一为「版本 + 发布日期 + 类型标签条目」：
 * - 标题行：`v1.1.0` +（当前版本）+ `2026-11-03`；
 * - 展开后先给一句话概览，再逐条列出「新增 / 优化 / 修复 / 调整 / 文档」；
 * - 默认展开前 3 个版本，更早的收起，避免一进页面就是一屏长文。
 */
@Composable
fun ChangelogScreen(
    onBack: () -> Unit,
    initialExpandCount: Int = 3,
) {
    val context = LocalContext.current
    val currentVersion = remember { currentVersionInfo(context).versionName }
    val expandedStates = remember {
        mutableStateListOf<Boolean>().apply {
            repeat(changelogData.size) { index -> add(index < initialExpandCount) }
        }
    }
    val rotations = List(changelogData.size) { index ->
        val rotation by animateFloatAsState(
            targetValue = if (expandedStates[index]) 90f else -90f,
            animationSpec = tween(durationMillis = 200),
            label = "changelogRotation$index",
        )
        rotation
    }

    MineSubPage(title = "更新日志", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = ijiTopBarContentPadding(),
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
        ) {
            item(key = "header") {
                Text(
                    text = "共 ${changelogData.size} 个版本 · 当前 " +
                        (currentVersion.takeIf { it.isNotBlank() }?.let { "v$it" } ?: "未知版本"),
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                )
            }
            item(key = "list") {
                top.yukonga.miuix.kmp.basic.Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = Radius.xxl,
                    colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                        MaterialTheme.colorScheme.surface,
                    ),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        changelogData.forEachIndexed { index, entry ->
                            val isCurrent = entry.version.removePrefix("v") == currentVersion
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedStates[index] = !expandedStates[index] },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = 20.dp,
                                            end = 18.dp,
                                            top = if (index == 0) 20.dp else 17.dp,
                                            bottom = if (index == changelogData.lastIndex) 20.dp else 17.dp,
                                        ),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = entry.version,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (isCurrent) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "当前版本",
                                            fontSize = 10.5.sp,
                                            color = MiuixPrimary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(Radius.pill))
                                                .background(MiuixPrimary.copy(alpha = 0.1f))
                                                .padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = entry.date,
                                        fontSize = 14.sp,
                                        color = TextSecondary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = MiuixIcons.ChevronForward,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .graphicsLayer { rotationZ = rotations[index] },
                                        tint = TextSecondary,
                                    )
                                }
                                AnimatedVisibility(
                                    visible = expandedStates[index],
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                                    ) {
                                        if (entry.summary.isNotBlank()) {
                                            Text(
                                                text = entry.summary,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp,
                                                color = TextSecondary,
                                                modifier = Modifier.padding(bottom = 8.dp),
                                            )
                                        }
                                        entry.items.forEach { item ->
                                            Row(
                                                modifier = Modifier.padding(bottom = 4.dp),
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                ChangeKindTag(item.kind)
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = item.text,
                                                    fontSize = 14.sp,
                                                    lineHeight = 22.sp,
                                                    color = TextSecondary,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 分割线（最后一条不显示）
                            if (index < changelogData.lastIndex) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp)
                                        .height(0.5.dp)
                                        .background(TextSecondary.copy(alpha = 0.12f)),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "footer") {
                Text(
                    text = "更早的版本未在应用内保留。版本号规则：主版本.次版本.修订号，功能新增升次版本，修复与打磨升修订号。",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
                )
            }
        }
    }
}

/** 变更类型标签：固定宽度不给，靠内容撑开，保证中文标签不换行 */
@Composable
private fun ChangeKindTag(kind: ChangeKind) {
    val color = kind.color()
    Text(
        text = kind.label,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .padding(top = 3.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun ChangeKind.color(): Color = when (this) {
    ChangeKind.FEATURE -> MiuixPrimary
    ChangeKind.IMPROVE -> AppleOrange
    ChangeKind.FIX -> AppleRed
    ChangeKind.CHANGE -> ApplePurple
    ChangeKind.DOCS -> TextSecondary
}
