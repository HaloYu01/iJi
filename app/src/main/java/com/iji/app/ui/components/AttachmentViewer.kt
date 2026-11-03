package com.iji.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Notes

/** 缩略图边长 */
private val THUMB_SIZE = 44.dp

/**
 * 账单附件缩略图条（最多 3 张，多出的用 `+N` 兜底）。
 *
 * `attachments` 存的是带**持久化读取权限**的 URI 字符串，可长期访问；
 * 但权限失效 / 文件被删时会加载失败，因此必须给 error 占位（否则行里出现一块空白）。
 */
@Composable
fun AttachmentThumbs(
    attachments: List<String>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    Row(
        modifier = modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        attachments.take(3).forEachIndexed { index, uri ->
            AttachmentThumb(uri = uri, onClick = { onOpen(index) })
        }
        if (attachments.size > 3) {
            Box(
                modifier = Modifier
                    .size(THUMB_SIZE)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpen(3) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+${attachments.size - 3}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AttachmentThumb(uri: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val parsed = runCatching { Uri.parse(uri) }.getOrNull()
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(parsed)
            .crossfade(true)
            .build(),
        contentDescription = "附件",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(THUMB_SIZE)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick),
        // 加载中 / 失败都要占位：URI 权限失效时给用户看得懂的兜底，而不是一块空白
        loading = { Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) },
        error = {
            Box(
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MiuixIcons.Notes,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

/**
 * 附件查看器：左右滑动翻页看大图。
 *
 * 从缩略图点进来时[startIndex]定位到对应张，避免"点了第 3 张却从第一张开始看"。
 */
@Composable
fun AttachmentViewerDialog(
    show: Boolean,
    attachments: List<String>,
    startIndex: Int = 0,
    onDismissRequest: () -> Unit,
) {
    IjiDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = if (attachments.size > 1) "附件（${attachments.size}）" else "附件",
        confirmText = "关闭",
        onConfirm = onDismissRequest,
    ) {
        if (attachments.isEmpty()) {
            Text(
                "没有附件",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            return@IjiDialog
        }
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, attachments.lastIndex),
        ) { attachments.size }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val context = LocalContext.current
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(runCatching { Uri.parse(attachments[page]) }.getOrNull())
                    .crossfade(true)
                    .build(),
                contentDescription = "附件 ${page + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(Radius.lg)),
                loading = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "图片已失效（原文件可能被删除）",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                },
            )
        }
        // 页码指示
        if (attachments.size > 1) {
            Text(
                "${pagerState.currentPage + 1} / ${attachments.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MiuixPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
