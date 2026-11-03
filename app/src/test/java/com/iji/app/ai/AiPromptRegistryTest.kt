package com.iji.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示词注册表：`AiPromptKind` 是「提示词管理」的唯一真值来源。
 *
 * 这里锁的是三件事，任何一件破了都会让用户改完提示词却没效果（假开关）：
 * 1. 每种提示词都有非空默认值，且写着「什么时候生效」；
 * 2. key 唯一且可逆（路由参数靠它回查）；
 * 3. 账单两段提示词仍然要求模型只返回 JSON —— 不然解析器会拿到散文。
 */
class AiPromptRegistryTest {

    @Test
    fun `每种提示词都有标题、说明、生效条件和默认值`() {
        AiPromptKind.entries.forEach { kind ->
            assertTrue("${kind.key} 缺标题", kind.title.isNotBlank())
            assertTrue("${kind.key} 缺用途说明", kind.usage.isNotBlank())
            assertTrue("${kind.key} 缺生效条件", kind.effect.isNotBlank())
            assertTrue("${kind.key} 默认提示词太短，可能被清空过", kind.defaultText.length > 30)
        }
    }

    @Test
    fun `key 唯一且可反查`() {
        val keys = AiPromptKind.entries.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
        AiPromptKind.entries.forEach { assertEquals(it, AiPromptKind.ofKey(it.key)) }
        assertNull(AiPromptKind.ofKey("不存在的 key"))
        assertNull(AiPromptKind.ofKey(null))
    }

    @Test
    fun `覆盖了账单、资产、分类、摘要四类提示词`() {
        // 与 AutoAccounting 的四种提示词对齐：账单识别 / 资产（账户）映射 / 分类识别 / 摘要
        assertNotNull(AiPromptKind.ofKey("bill_text"))
        assertNotNull(AiPromptKind.ofKey("bill_image"))
        assertNotNull(AiPromptKind.ofKey("category"))
        assertNotNull(AiPromptKind.ofKey("account"))
        assertNotNull(AiPromptKind.ofKey("summary"))
    }

    @Test
    fun `账单提示词仍要求只返回 JSON`() {
        listOf(AiPromptKind.BILL_TEXT, AiPromptKind.BILL_IMAGE).forEach { kind ->
            assertTrue(kind.defaultText.contains("JSON"))
            assertTrue(kind.defaultText.contains("type"))
            assertTrue(kind.defaultText.contains("amount"))
        }
    }

    @Test
    fun `分类与账户提示词只允许输出一个已有名字`() {
        // 这两段是"二次映射"，只要模型自由发挥，结果就没法落到真实分类 / 账户上
        assertTrue(AiPromptKind.CATEGORY.defaultText.contains("只能从"))
        assertTrue(AiPromptKind.ACCOUNT.defaultText.contains("只能从"))
        assertTrue(AiPromptKind.ACCOUNT.defaultText.contains("空字符串"))
    }

    @Test
    fun `自定义为空时回落到默认提示词`() {
        assertEquals("默认", AiConfig.resolvePrompt("", "默认"))
        assertEquals("自定义", AiConfig.resolvePrompt("自定义", "默认"))
        assertEquals("默认", AiConfig.resolvePrompt("   ", "默认"))
    }
}
