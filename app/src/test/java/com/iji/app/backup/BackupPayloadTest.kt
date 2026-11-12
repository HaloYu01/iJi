package com.iji.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 压缩层与版本兼容单测（Keystore 在 JVM 上跑不了，这里只钉纯字节行为）。
 * 关键在于版本 1 的旧备份必须原样还原。
 */
class BackupPayloadTest {

    /** 模拟一份整库快照：字段重复度高，正是 gzip 能压下去的形状 */
    private val snapshotJson: String = buildString {
        append("""{"kind":"iji-full","version":1,"app":"i记","transactions":[""")
        append(
            (1..500).joinToString(",") { i ->
                """{"id":$i,"amountCents":${i * 100},"categoryId":"food","merchant":"便利店","note":"午饭"}"""
            },
        )
        append("]}")
    }

    @Test
    fun compressThenUnpack_roundTripsCurrentVersion() {
        val raw = snapshotJson.toByteArray(Charsets.UTF_8)

        val packed = BackupPayload.compress(raw)
        val restored = BackupPayload.unpack(BackupPayload.VERSION_GZIP, packed)

        assertArrayEquals(raw, restored)
        assertEquals(
            snapshotJson,
            String(restored, Charsets.UTF_8),
        )
    }

    @Test
    fun compress_actuallyShrinksRepeatedJson() {
        val raw = snapshotJson.toByteArray(Charsets.UTF_8)

        val packed = BackupPayload.compress(raw)

        assertTrue("gzip 后应明显变小：${packed.size} vs ${raw.size}", packed.size < raw.size / 2)
        // gzip 魔数：版本 2 的载荷确实是被压缩过的，不是原样透传
        assertEquals(0x1f, packed[0].toInt() and 0xff)
        assertEquals(0x8b, packed[1].toInt() and 0xff)
    }

    @Test
    fun unpack_versionPlainReturnsLegacyJsonUntouched() {
        // 升级前写下的备份：版本 1 = 明文就是 JSON，没有 gzip 头
        val legacy = snapshotJson.toByteArray(Charsets.UTF_8)

        val restored = BackupPayload.unpack(BackupPayload.VERSION_PLAIN, legacy)

        assertArrayEquals(legacy, restored)
    }

    @Test
    fun unpack_unknownVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupPayload.unpack(3, ByteArray(8))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupPayload.unpack(0, ByteArray(8))
        }
    }

    @Test
    fun emptyPayload_roundTrips() {
        val packed = BackupPayload.compress(ByteArray(0))

        assertArrayEquals(ByteArray(0), BackupPayload.unpack(BackupPayload.VERSION_GZIP, packed))
    }
}
