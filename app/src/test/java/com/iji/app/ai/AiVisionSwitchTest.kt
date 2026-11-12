package com.iji.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 图片是否发模型只看一个开关：AI 可用 + 开关打开；否则一律只跑本地 OCR。 */
class AiVisionSwitchTest {

    @Test
    fun aiReadyAndSwitchOn_usesModel() {
        assertTrue(AiConfig.visionUsesAi(aiReady = true, visionEnabled = true))
    }

    @Test
    fun aiNotReady_neverSendsImage() {
        assertFalse(AiConfig.visionUsesAi(aiReady = false, visionEnabled = true))
    }

    @Test
    fun switchOff_keepsLocalOcrOnly() {
        assertFalse(AiConfig.visionUsesAi(aiReady = true, visionEnabled = false))
    }
}
