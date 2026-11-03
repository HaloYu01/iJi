package com.iji.app.ui.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * 写入系统下载目录（minSdk 33，无需存储权限）。
 *
 * 同名文件由系统自动加 `(1)` 后缀，不会覆盖已有文件。
 *
 * @return 写入成功的 Uri；失败返回 null（调用方负责提示）
 */
fun saveToDownloads(
    context: Context,
    fileName: String,
    mimeType: String,
    content: String,
): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    return runCatching {
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val stream = resolver.openOutputStream(uri) ?: return null
        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        uri
    }.getOrNull()
}
