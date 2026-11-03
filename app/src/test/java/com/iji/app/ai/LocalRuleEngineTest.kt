package com.iji.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地识别规则引擎：命中口径、类型过滤、优先级，以及规则包的读写往返。
 *
 * 这一层是"没配 Key 也能记账"的唯一保障，而且规则现在是用户可编辑的数据 ——
 * 改错一处不会有编译错误，只会在真机上把账单记到别的分类，所以全部用纯函数测住。
 */
class LocalRuleEngineTest {

    private fun rule(
        id: String,
        category: String,
        keywords: List<String>,
        type: String = LocalRule.TYPE_EXPENSE,
        account: String = "",
        enabled: Boolean = true,
        builtin: Boolean = false,
    ) = LocalRule(
        id = id,
        name = category,
        type = type,
        category = category,
        account = account,
        keywords = keywords,
        enabled = enabled,
        builtin = builtin,
    )

    @Test
    fun `内置规则认得出常见支出`() {
        val rules = LocalRuleDefaults.all
        assertEquals("餐饮", LocalRuleEngine.match("今天午饭花了35块", rules)?.rule?.category)
        assertEquals("交通", LocalRuleEngine.match("地铁充值50元", rules)?.rule?.category)
        assertEquals("购物", LocalRuleEngine.match("淘宝买衣服花了299", rules)?.rule?.category)
        assertEquals("居住", LocalRuleEngine.match("交房租3000", rules)?.rule?.category)
        assertEquals("医疗", LocalRuleEngine.match("买药花了20", rules)?.rule?.category)
    }

    @Test
    fun `内置规则认得出常见收入且带收入类型`() {
        val rules = LocalRuleDefaults.all
        val match = LocalRuleEngine.match("工资到账15000", rules)
        assertEquals("工资", match?.rule?.category)
        assertEquals(LocalRule.TYPE_INCOME, match?.rule?.type)

        assertEquals("报销", LocalRuleEngine.match("出差报销300", rules)?.rule?.category)
    }

    @Test
    fun `支出文本不会命中收入规则`() {
        // 「药」是支出规则的关键词；即使文本里出现「到账」以外什么都不含，也不该落到收入类
        val rules = listOf(rule("income-only", "工资", listOf("工资"), type = LocalRule.TYPE_INCOME))
        assertNull(LocalRuleEngine.match("地铁充值50元", rules))
    }

    @Test
    fun `停用的规则不参与匹配`() {
        val rules = listOf(rule("off", "餐饮", listOf("饭"), enabled = false))
        assertNull(LocalRuleEngine.match("今天午饭花了35块", rules))
    }

    @Test
    fun `命中词数多的规则胜出`() {
        val rules = listOf(
            rule("one", "购物", listOf("淘宝")),
            rule("two", "服饰", listOf("淘宝", "衣服")),
        )
        assertEquals("服饰", LocalRuleEngine.match("淘宝买衣服299", rules)?.rule?.category)
    }

    @Test
    fun `同分时长关键词胜出`() {
        val rules = listOf(
            rule("short", "医疗", listOf("药")),
            rule("long", "药品", listOf("药店")),
        )
        val match = LocalRuleEngine.match("在药店买的", rules)
        assertEquals("药品", match?.rule?.category)
        assertEquals(1, match?.hits ?: 0)
    }

    @Test
    fun `同分时用户规则优先于内置规则`() {
        val rules = listOf(
            rule("builtin", "餐饮", listOf("奶茶"), builtin = true),
            rule("user", "饮品", listOf("奶茶"), builtin = false),
        )
        assertEquals("饮品", LocalRuleEngine.match("一杯奶茶18块", rules)?.rule?.category)
    }

    @Test
    fun `规则可以预填账户`() {
        val rules = listOf(rule("wx", "餐饮", listOf("饭"), account = "微信"))
        assertEquals("微信", LocalRuleEngine.match("午饭30", rules)?.rule?.account)
    }

    @Test
    fun `空文本与全不命中都返回 null`() {
        assertNull(LocalRuleEngine.match("   ", LocalRuleDefaults.all))
        assertNull(LocalRuleEngine.match("嗯嗯啊啊", LocalRuleDefaults.all))
    }

    @Test
    fun `关键词支持换行逗号顿号混写且去重`() {
        assertEquals(
            listOf("地铁", "公交", "打车"),
            LocalRuleEngine.keywordsOf("地铁, 公交\n打车、地铁"),
        )
    }

    @Test
    fun `收入语义词判定`() {
        assertEquals(LocalRule.TYPE_INCOME, LocalRuleEngine.detectType("工资到账15000"))
        assertEquals(LocalRule.TYPE_INCOME, LocalRuleEngine.detectType("收到红包200"))
        // 退款在 iji 里是「负数支出」，不能算成收入
        assertEquals(LocalRule.TYPE_EXPENSE, LocalRuleEngine.detectType("订单退款19.9"))
    }

    @Test
    fun `内置规则每条都有关键词和分类`() {
        assertTrue(LocalRuleDefaults.all.size >= 10)
        LocalRuleDefaults.all.forEach {
            assertTrue("${it.id} 缺关键词", it.keywords.isNotEmpty())
            assertTrue("${it.id} 缺分类", it.category.isNotBlank())
            assertTrue("${it.id} 缺名字", it.name.isNotBlank())
        }
        // id 必须唯一，否则 upsert 会互相覆盖
        assertEquals(LocalRuleDefaults.all.size, LocalRuleDefaults.all.map { it.id }.distinct().size)
    }

    @Test
    fun `规则包 JSON 往返不丢字段`() {
        val rules = listOf(
            rule("user-1", "咖啡", listOf("拿铁", "美式"), account = "支付宝"),
            rule("user-2", "奖金", listOf("年终奖"), type = LocalRule.TYPE_INCOME, enabled = false),
        )
        val restored = rulesFromJson(rulesToJson(rules))
        assertEquals(rules, restored)
    }

    @Test
    fun `导入兼容裸数组并忽略坏条目`() {
        val raw = """
            [
              {"id":"a","name":"餐饮","type":"expense","category":"餐饮","keywords":["饭"],"enabled":true},
              {"name":"缺 id 的条目","keywords":["x"]},
              {"id":"b","name":"未知类型","type":"weird","category":"交通","keywords":["地铁"]}
            ]
        """.trimIndent()
        val parsed = rulesFromJson(raw)
        assertEquals(2, parsed.size)
        // 不认识的类型按支出处理，不丢规则
        assertEquals(LocalRule.TYPE_EXPENSE, parsed[1].type)
        assertNotNull(parsed.firstOrNull { it.id == "b" })
    }

    @Test
    fun `空内容解析为空列表而不是抛异常`() {
        assertTrue(rulesFromJson("").isEmpty())
        assertTrue(rulesFromJson("{}").isEmpty())
        assertTrue(rulesFromJson("not json").isEmpty())
    }
}
