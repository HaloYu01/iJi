package com.iji.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * 按 EXIF 方向标记旋转。
 *
 * 竖拍照片若不做修正，会以横躺的姿态送进识别模型，识别率明显下降。
 *
 * 原先这是 `AiBookkeepingScreen.kt` 里的 private 顶层函数，截屏导入页也要用同一套逻辑，
 * 于是提出来共享 —— 复制一份的话，两边将来会各自演化。
 */
internal fun applyExifRotation(ctx: Context, uri: Uri, bmp: Bitmap): Bitmap {
    val normal = android.media.ExifInterface.ORIENTATION_NORMAL
    val orientation = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use {
            android.media.ExifInterface(it)
                .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, normal)
        } ?: normal
    }.getOrDefault(normal)
    val degrees = when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bmp
    }
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}
