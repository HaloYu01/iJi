package com.iji.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 识别方式的决策规则：**默认本地、未配 AI 强制本地**。
 *
 * 这两个函数都是纯函数（不读 DataStore、不碰 Context），所以能在裸 JVM 单测里直接验证；
 * 真正的存储读写仍由 `AiConfig.parseMode(context)` 负责，它内部只是把存储值喂给
 * [AiConfig.resolveMode]。
 */
class AiParseModeTest {

    // ---- AiConfig.resolveMode：存储值 + 可用性 → 实际方式 ----

    @Test
    fun defaultIsLocal() {
        // 用户从没选过（存储里没有这个键）→ 本地
        assertEquals(AiConfig.MODE_LOCAL, AiConfig.resolveMode(null, aiReady = true))
    }

    @Test
    fun blankOrUnknownValueFallsBackToLocal() {
        assertEquals(AiConfig.MODE_LOCAL, AiConfig.resolveMode("", aiReady = true))
        assertEquals(AiConfig.MODE_LOCAL, AiConfig.resolveMode("garbage", aiReady = true))
        assertEquals(AiConfig.MODE_LOCAL, AiConfig.resolveMode(AiConfig.MODE_LOCAL, aiReady = true))
    }

    @Test
    fun aiWithoutKeyIsForcedLocal() {
        // 选过 AI 但清空了 Key / 关了总开关 → 强制本地，不给必然失败的选项
        assertEquals(AiConfig.MODE_LOCAL, AiConfig.resolveMode(AiConfig.MODE_AI, aiReady = false))
    }

    @Test
    fun aiWithKeyStaysAi() {
        assertEquals(AiConfig.MODE_AI, AiConfig.resolveMode(AiConfig.MODE_AI, aiReady = true))
    }

    // ---- AiBookkeepingService.useAiFor：这一次到底联不联网 ----

    @Test
    fun useAiFor_followsConfiguredMode() {
        assertFalse(AiBookkeepingService.useAiFor(null, AiConfig.MODE_LOCAL, aiReady = true))
        assertTrue(AiBookkeepingService.useAiFor(null, AiConfig.MODE_AI, aiReady = true))
    }

    @Test
    fun useAiFor_neverAllowsAiWhenNotReady() {
        // 未配 AI 时，调用方传什么都不能把内容发出去
        assertFalse(AiBookkeepingService.useAiFor(null, AiConfig.MODE_AI, aiReady = false))
        assertFalse(AiBookkeepingService.useAiFor(true, AiConfig.MODE_AI, aiReady = false))
    }

    @Test
    fun useAiFor_explicitLocalWins() {
        assertFalse(AiBookkeepingService.useAiFor(false, AiConfig.MODE_AI, aiReady = true))
    }

    @Test
    fun useAiFor_trueCannotBypassLocalMode() {
        // useAi = true 只是"想用"，配置说本地就得本地 —— 否则任一页面写死 true 就能偷偷上云
        assertFalse(AiBookkeepingService.useAiFor(true, AiConfig.MODE_LOCAL, aiReady = true))
    }

    // ---- AiBookkeepingService.preferTextResult：开 = AI 优先，失效一律降级本地 ----

    private fun result(amount: Long, source: String) = AiBookkeepingService.BillResult(
        type = "expense",
        categoryHint = "其他",
        amountCents = amount,
        note = "",
        accountHint = "",
        tags = emptyList(),
        source = source,
    )

    @Test
    fun preferTextResult_usesAiWhenItHasAmount() {
        val ai = result(3500, AiBookkeepingService.SOURCE_AI)
        val local = result(1200, AiBookkeepingService.SOURCE_LOCAL_RULE)
        assertEquals(ai, AiBookkeepingService.preferTextResult(ai, local))
    }

    @Test
    fun preferTextResult_fallsBackToLocalWhenAiFails() {
        val local = result(1200, AiBookkeepingService.SOURCE_LOCAL_RULE)
        // 没开 AI / 请求失败返回 null
        assertEquals(local, AiBookkeepingService.preferTextResult(null, local))
        // AI 返回了结果但没识别出金额，同样算失效
        assertEquals(
            local,
            AiBookkeepingService.preferTextResult(result(0, AiBookkeepingService.SOURCE_AI), local),
        )
        // 两边都没结果时不伪造结果
        assertNull(AiBookkeepingService.preferTextResult(null, null))
    }
}
