package com.iji.app.ui.mine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 「调整头像」子页：中央圆形高亮 + 四周压暗。
 * 图片按 fit 完整显示（圆外压暗可见），1x 禁止平移；放大后拖动限制在「盖满圆形」范围内。
 * 保存时按当前缩放/平移裁出 512 圆形头像；[onSaved] 为 null（「我的」页）时立即回写
 * [ProfileState] 并返回，非 null（引导配置页）时结果只交给回调 —— 引导是页内草稿，
 * 跳过 / 取消不能改掉真实头像，也不能删掉现役头像文件。
 */
@Composable
fun AvatarEditScreen(uri: String, onBack: () -> Unit, onSaved: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val boxSize = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { decodeSampled(uri, context) }
        failed = bitmap == null
    }
    // 尺寸变化后重新收敛位移，避免超出「图片盖满圆」的范围
    LaunchedEffect(boxSize.floatValue) {
        offset = clampOffset(offset, boxSize.floatValue, bitmap, scale)
    }

    val canConfirm = bitmap != null && !saving
    val zoomBy: (Float) -> Unit = { factor ->
        scale = (scale * factor).coerceIn(1f, 5f)
        offset = clampOffset(offset, boxSize.floatValue, bitmap, scale)
    }

    MineSubPage(
        title = "调整头像",
        onBack = onBack,
        endAction = {
            val onConfirm: () -> Unit = {
                val src = bitmap
                if (src != null) {
                    val s = scale
                    val o = offset
                    val box = boxSize.floatValue
                    saving = true
                    // 裁剪/压缩/落盘必须离开主线程
                    scope.launch(Dispatchers.IO) {
                        val saved = saveAvatar(context, cropCircle(src, s, o, box), deleteOthers = onSaved == null)
                        withContext(Dispatchers.Main) {
                            saving = false
                            if (saved != null) {
                                if (onSaved != null) onSaved(saved) else ProfileState.setAvatarUri(context, saved)
                                onBack()
                            } else {
                                snackbar.show("保存失败，请重试")
                            }
                        }
                    }
                }
            }
            IjiTextAction(
                text = if (saving) "保存中…" else "确定",
                onClick = onConfirm,
                enabled = canConfirm,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = ijiTopBarContentPadding()),
        ) {
            Text(
                text = when {
                    failed -> "图片加载失败，请换一张试试"
                    bitmap == null -> "正在加载图片…"
                    else -> "整图完整显示 · 双指或按钮缩放，放大后可拖动"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) AppleRed else TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                textAlign = TextAlign.Center,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // 只裁到压暗区范围内：放大后溢出的图片不会盖到提示文字/底部按钮上
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val src = bitmap
                            val box = boxSize.floatValue
                            if (saving || src == null || box <= 0f) return@detectTransformGestures
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = clampOffset(offset + pan, box, src, scale)
                        }
                    },
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val side = minOf(maxWidth, maxHeight) * AVATAR_CIRCLE_RATIO

                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(side)
                            .onGloballyPositioned { boxSize.floatValue = it.size.width.toFloat() },
                        contentAlignment = Alignment.Center,
                    ) {
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "头像预览",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    },
                            )
                        }
                    }

                    // 压暗 → 暗角 → 圆形镂空 → 描边（Clear 只作用于本层，不影响下方图片）
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                    ) {
                        val r = size.minDimension / 2f * AVATAR_CIRCLE_RATIO
                        val outer = maxOf(size.width, size.height).coerceAtLeast(1f)
                        drawRect(Color.Black.copy(alpha = 0.70f))
                        drawRect(
                            Brush.radialGradient(
                                0f to Color.Transparent,
                                (r / outer).coerceIn(0f, 0.95f) to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.30f),
                                center = center,
                                radius = outer,
                            )
                        )
                        drawCircle(Color.Transparent, r, center, blendMode = BlendMode.Clear)
                        drawCircle(Color.White.copy(alpha = 0.92f), r, center, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScaleCtl("-", scale > 1f) { zoomBy(1 / 1.15f) }
                Text(
                    text = "${(scale * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(72.dp),
                    textAlign = TextAlign.Center,
                )
                ScaleCtl("+", scale < 5f) { zoomBy(1.15f) }
                Text(
                    text = "重置",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(CircleShape)
                        .clickable {
                            scale = 1f
                            offset = Offset.Zero
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 圆形缩放按钮 */
@Composable
private fun ScaleCtl(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else TextSecondary.copy(alpha = 0.5f),
        )
    }
}

/** 圆形直径 / 可用区域短边 */
private const val AVATAR_CIRCLE_RATIO = 0.86f

/** 图片 fit 进方框的缩放系数 */
private fun fitScale(bmp: Bitmap, box: Float): Float =
    if (box <= 0f || bmp.width <= 0 || bmp.height <= 0) 1f
    else minOf(box / bmp.width, box / bmp.height)

/** 位移收敛到「图片始终盖满圆」的范围；1x（图片未铺满）时只能居中 */
private fun clampOffset(offset: Offset, box: Float, bmp: Bitmap?, scale: Float): Offset {
    if (bmp == null || box <= 0f) return Offset.Zero
    val s = fitScale(bmp, box) * scale
    val maxX = ((bmp.width * s - box) / 2f).coerceAtLeast(0f)
    val maxY = ((bmp.height * s - box) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** 按预览的缩放/平移裁出 512x512 圆形头像（圆外透明），坐标系与预览一致 */
private fun cropCircle(src: Bitmap, scale: Float, offset: Offset, box: Float): Bitmap {
    val s = fitScale(src, box.coerceAtLeast(1f))
    val k = if (box > 0f) 512 / box else 1f
    val w = src.width * s / 2f
    val h = src.height * s / 2f
    return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { out ->
        Canvas(out).apply {
            clipPath(Path().apply { addCircle(256f, 256f, 256f, Path.Direction.CW) })
            scale(k, k)
            translate(box / 2f + offset.x, box / 2f + offset.y)
            scale(scale, scale)
            drawBitmap(src, null, RectF(-w, -h, w, h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }
}

/** 采样解码（最长边 ≤2048），失败返回 null */
private fun decodeSampled(uriString: String, context: Context): Bitmap? =
    try {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / (sample * 2) >= 2048) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (t: Throwable) {
        Log.w("AvatarEdit", "decode failed: $uriString", t)
        null
    }

/**
 * 写入私有目录并返回 file:// URI；文件名带时间戳以避免 Coil 命中旧缓存。
 * [deleteOthers] 控制是否顺手清理其它 avatar_ 文件：引导页的裁剪结果只是页内草稿，
 * ProfileState 此刻仍指向现役头像文件（重配场景下取消配置要能原样回去），因此不能删；
 * 引导过程产生的孤儿文件沿用既有约定，待下次在「我的」页换头像时统一清理。
 */
private fun saveAvatar(context: Context, bmp: Bitmap, deleteOthers: Boolean): String? =
    runCatching {
        val dir = File(context.filesDir, "avatar").apply { mkdirs() }
        val file = File(dir, "avatar_${System.currentTimeMillis()}.png")
        // 先写成功再清理旧文件：反过来一旦压缩失败，用户会凭空丢失原头像
        if (!file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }) {
            file.delete()
            return@runCatching null
        }
        if (deleteOthers) {
            dir.listFiles()?.filter { it.isFile && it.name.startsWith("avatar_") && it != file }
                ?.forEach { it.delete() }
        }
        Uri.fromFile(file).toString()
    }.getOrNull()
