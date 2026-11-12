package com.iji.app.backup

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 备份明文的压缩层（AES 在它外面，见 [BackupCipher]）。
 *
 * 整库快照 JSON 动辄数 MB，gzip 后通常只剩 1/5。
 * 版本语义放这里是为了能跑纯 JVM 单测——Keystore 测不了。
 */
internal object BackupPayload {

    /** 旧格式：明文即 JSON，没有压缩层（只读，兼容已有备份） */
    const val VERSION_PLAIN: Byte = 1

    /** 当前格式：明文是 gzip 流 */
    const val VERSION_GZIP: Byte = 2

    /** 当前写入用的版本 */
    const val VERSION: Byte = VERSION_GZIP

    private val KNOWN_VERSIONS = setOf(VERSION_PLAIN, VERSION_GZIP)

    fun isKnownVersion(version: Byte): Boolean = version in KNOWN_VERSIONS

    fun compress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size / 4 + 64)
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    /** 版本 1 必须原样返回，否则旧备份升级后会集体变成「解密失败」 */
    fun unpack(version: Byte, payload: ByteArray): ByteArray {
        require(isKnownVersion(version)) { "不支持的备份版本：$version" }
        return if (version == VERSION_GZIP) decompress(payload) else payload
    }

    private fun decompress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size * 4 + 64)
        GZIPInputStream(data.inputStream()).use { it.copyTo(out) }
        return out.toByteArray()
    }
}
