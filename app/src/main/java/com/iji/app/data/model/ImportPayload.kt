package com.iji.app.data.model

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 导入批次的账单快照编解码：备份 JSON → gzip → Base64。
 *
 * 快照进 Room 的一个 TEXT 列，不压缩时几百笔账单就能让历史列表查询把数 MB 一起读进内存。
 * 纯 JDK 实现，不依赖 Android API，便于单测。
 */
object ImportPayload {
    fun encode(json: String): String = runCatching {
        val bytes = ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            out.toByteArray()
        }
        Base64.getEncoder().encodeToString(bytes)
    }.getOrDefault("")

    fun decode(encoded: String): String = runCatching {
        val bytes = Base64.getDecoder().decode(encoded)
        GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrDefault("")
}
