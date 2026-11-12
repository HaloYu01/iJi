package com.iji.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入时的字节分流：明文 JSON 与加密 `.ijbak` 不能互相误判。
 */
class BackupImportBytesTest {

    @Test
    fun `明文 JSON 原样返回`() {
        val json = """{"kind":"iji-full","version":1,"transactions":[]}"""

        assertEquals(json, BackupManager.decodeSnapshot(json.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `裸流水数组也按明文读`() {
        val json = """[{"amountCents":1200}]"""

        assertEquals(json, BackupManager.decodeSnapshot(json.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `明文 JSON 不会被误判成加密备份`() {
        assertFalse(BackupCipher.isEncrypted("""{"kind":"iji-full"}""".toByteArray(Charsets.UTF_8)))
        // 带 UTF-8 BOM 的文件同样不能撞上密文版本字节
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "{".toByteArray()
        assertFalse(BackupCipher.isEncrypted(bom))
    }

    @Test
    fun `两个密文版本都认，残缺文件不算密文`() {
        val legacy = ByteArray(1 + 12 + 16).also { it[0] = BackupPayload.VERSION_PLAIN }
        val current = ByteArray(1 + 12 + 16).also { it[0] = BackupPayload.VERSION_GZIP }

        assertTrue("升级前写下的版本 1 备份也要能识别", BackupCipher.isEncrypted(legacy))
        assertTrue(BackupCipher.isEncrypted(current))
        // 只有版本头、没有 IV + 密文的文件不该当成备份去解密
        assertFalse(BackupCipher.isEncrypted(legacy.copyOf(5)))
    }
}
