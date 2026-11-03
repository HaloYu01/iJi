package com.iji.app.ui.mine

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 许可与授权页。
 *
 * 顺序刻意如此：**先本应用授权、再第三方组件**。
 * 之前整页只有第三方清单，读者很容易反推出「这个应用也是开源的」——
 * 但它目前并不开源，所以版权与使用边界必须排在最前面。
 *
 * 版式与「隐私政策」一致：分组小标题 + 20dp 圆角卡片 + 淡分割线。
 */
@Composable
fun LicenseScreen(
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    MineSubPage(title = "许可与授权", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = ijiTopBarContentPadding(),
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ── 1. 本应用授权条款 ──
            item(key = "app_header") {
                SmallTitle(
                    text = "本应用（i记）授权条款",
                    insideMargin = PaddingValues(top = 4.dp, bottom = 10.dp),
                )
            }
            item(key = "app_body") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = Radius.xxl,
                    colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        appLicenseSections.forEachIndexed { index, section ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
                            ) {
                                Text(
                                    text = section.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(6.dp))
                                section.paragraphs.forEachIndexed { pIndex, paragraph ->
                                    Text(
                                        text = paragraph,
                                        fontSize = 13.5.sp,
                                        lineHeight = 21.sp,
                                        color = TextSecondary,
                                    )
                                    if (pIndex < section.paragraphs.lastIndex) {
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                            }
                            if (index < appLicenseSections.lastIndex) {
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

            // ── 2. 第三方开源组件 ──
            item(key = "third_header") {
                Column {
                    SmallTitle(
                        text = "第三方开源组件",
                        insideMargin = PaddingValues(top = 16.dp, bottom = 6.dp),
                    )
                    Text(
                        text = "以下组件按各自许可证使用，与本应用授权条款相互独立。" +
                            "「源码内置」表示该项目源码已直接并入本应用并做过裁剪改编。",
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                    )
                }
            }

            licenseGroups.forEach { group ->
                item(key = "group_title_${group.title}") {
                    Text(
                        text = group.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
                    )
                }
                item(key = "group_body_${group.title}") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = Radius.xxl,
                        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            group.items.forEachIndexed { index, item ->
                                LicenseRow(
                                    item = item,
                                    uriHandler = uriHandler,
                                    topPadding = if (index == 0) 16.dp else 13.dp,
                                    bottomPadding = if (index == group.items.lastIndex) 16.dp else 13.dp,
                                )
                                if (index < group.items.lastIndex) {
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
            }

            // ── 3. 尾部说明 ──
            item(key = "footer") {
                Text(
                    text = "以上组件均按其原始许可证使用，本应用不对第三方组件作任何担保。" +
                        "需要各许可证全文时，可在对应项目仓库查看，也可通过「关于应用 - 问题反馈」向作者索取。\n\n" +
                        "如果你认为本应用某处使用侵犯了你的权利，请联系 feedback@iji.app，我们会尽快核实处理。",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp),
                )
            }
        }
    }
}

/**
 * 单个组件条目：名称 + 许可证标签 + 作者 +「怎么用进来的」说明。
 *
 * 说明文字是重点：只说「用了某某库」读者无法判断它到底在哪儿、是不是源码内置，
 * 出现授权争议时也无从核对。
 */
@Composable
private fun LicenseRow(
    item: OpenSourceItem,
    uriHandler: UriHandler,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val linked = item.url.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (linked) Modifier.clickable { uriHandler.openUri(item.url) } else Modifier)
            .padding(start = 18.dp, end = 16.dp, top = topPadding, bottom = bottomPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    // 有链接才用主色：主色 + 可点 = 链接
                    color = if (linked) MiuixPrimary else MaterialTheme.colorScheme.onSurface,
                )
                if (item.license.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    LicenseChip(item.license)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.author,
                fontSize = 13.sp,
                color = TextSecondary,
            )
            if (item.contribution.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.contribution,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = TextSecondary,
                )
            }
        }
        if (linked) {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = "访问项目",
                tint = TextSecondary,
                modifier = Modifier
                    .padding(start = 8.dp, top = 2.dp)
                    .size(16.dp),
            )
        }
    }
}

/** 许可证小标签：浅主色底，一眼能和标题区分开 */
@Composable
private fun LicenseChip(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MiuixPrimary,
        maxLines = 1,
        modifier = Modifier
            .widthIn(max = 150.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(MiuixPrimary.copy(alpha = 0.1f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
