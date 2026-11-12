package com.iji.app.ui.mine

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

/**
 * 更新日志页：一个版本一张卡，只写「改了什么」——版本号 + 日期 + 逐条一行。
 * 头部可点展开 / 收起；最近一次更新默认展开，其余默认收起。
 */
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentVersion = remember { currentVersionInfo(context).versionName }

    MineSubPage(title = "更新日志", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = ijiTopBarContentPadding(),
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap),
        ) {
            items(items = changelogData, key = { it.version }) { entry ->
                ChangelogEntryCard(
                    entry = entry,
                    isCurrent = entry.version.removePrefix("v") == currentVersion,
                    initiallyExpanded = entry.version == changelogData.first().version,
                )
            }
        }
    }
}

/** 一个版本一张卡：头部行可点切换展开 / 收起，展开时逐条列出变更 */
@Composable
private fun ChangelogEntryCard(
    entry: ChangelogEntry,
    isCurrent: Boolean,
    initiallyExpanded: Boolean,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) } // item 以 version 为 key，状态跨滑动 / 旋转不丢
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "changelogChevron")
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // padding 在 clickable 之后：热区含整行上下留白；indication = null：无水波纹
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = !expanded }
                    .padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = if (expanded) 8.dp else 16.dp),
            ) {
                // 版本号与日期占满左侧：区间版本号再长也不会把日期挤到右侧与之争宽度
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.version,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                    Text(
                        text = entry.date,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
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
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation),
                )
            }
            if (expanded) {
                Column(
                    // 与头部的 8dp 间距由头部行 bottom padding 提供
                    modifier = Modifier.padding(start = 20.dp, end = 18.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entry.items.forEach { line ->
                        // 列表符号与正文分列：折行时文字仍对齐在符号右侧，不会缩进回左边缘
                        Row {
                            Text(
                                text = "·",
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = TextSecondary,
                                modifier = Modifier.width(14.dp),
                            )
                            Text(
                                text = line,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
