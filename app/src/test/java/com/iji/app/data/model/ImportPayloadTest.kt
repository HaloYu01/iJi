package com.iji.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPayloadTest {

    @Test
    fun `payload round trips through gzip and base64`() {
        val json = "[{\"date\":\"2026-11-09\",\"note\":\"午餐, 带逗号\",\"amountCents\":1234}]"
        val encoded = ImportPayload.encode(json)
        assertTrue(encoded.isNotEmpty())
        assertNotEquals(json, encoded)
        assertEquals(json, ImportPayload.decode(encoded))
    }

    @Test
    fun `broken payload decodes to empty instead of throwing`() {
        assertEquals("", ImportPayload.decode("not-base64!!"))
        assertEquals("", ImportPayload.decode(""))
    }

    @Test
    fun `batch exposes undo and reimport only when it really can`() {
        val batch = ImportBatch(count = 3, recordIds = listOf(1L, 2L, 3L), payload = "x")
        assertTrue(batch.canUndo)
        assertTrue(batch.canReimport)

        val undone = batch.copy(recordIds = emptyList(), undone = true)
        assertFalse(undone.canUndo)
        assertTrue(undone.canReimport)

        // 升级前的老记录没有 id 也没有快照：两个按钮都不该可用
        val legacy = ImportBatch(count = 3)
        assertFalse(legacy.canUndo)
        assertFalse(legacy.canReimport)
    }
}
