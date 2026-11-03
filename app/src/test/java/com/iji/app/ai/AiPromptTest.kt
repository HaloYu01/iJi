package com.iji.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示词的回退规则与默认内容。
 *
 * 「留空 = 默认」是页面与存储之间的约定：用户清空后保存，存的是空串，
 * 真正的提示词由 [AiConfig.resolvePrompt] 在调用时补上默认值。
 */
class AiPromptTest {

    @Test
    fun blankCustomFallsBackToDefault() {
        assertEquals("默认", AiConfig.resolvePrompt("", "默认"))
        assertEquals("默认", AiConfig.resolvePrompt("   ", "默认"))
    }

    @Test
    fun customPromptWins() {
        assertEquals("自定义", AiConfig.resolvePrompt("自定义", "默认"))
    }

    @Test
    fun defaultTextPromptKeepsJsonContract() {
        // 模型必须返回可解析的 JSON；缺了这两个关键字，billFromJson 会直接失败
        assertTrue(AiConfig.DEFAULT_PROMPT_TEXT.contains("JSON"))
        assertTrue(AiConfig.DEFAULT_PROMPT_TEXT.contains("\"amount\""))
        assertTrue(AiConfig.DEFAULT_PROMPT_TEXT.contains("\"type\""))
    }

    @Test
    fun defaultImagePromptKeepsJsonContract() {
        assertTrue(AiConfig.DEFAULT_PROMPT_IMAGE.contains("JSON"))
        assertTrue(AiConfig.DEFAULT_PROMPT_IMAGE.contains("\"amount\""))
    }
}
