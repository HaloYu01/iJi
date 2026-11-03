package com.iji.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「一句话 → 一笔账单」的本地链路（离线兜底），也就是用户没配 Key 时的全部能力：
 * 金额（正则）+ 收支方向（语义词）+ 分类 / 账户（[LocalRuleEngine] + 用户规则）。
 *
 * 这条链路的结果会直接进结果卡 → 确认 → 记账面板 → 落库，所以每一步都必须对：
 * 分类错 = 记到错的分类；金额少一分 = 对不上小票；方向错 = 收支统计反了。
 */
class LocalRecognitionPipelineTest {

    private val rules = LocalRuleDefaults.all

    @Test
    fun `中文金额与分类`() {
        val bill = AiBookkeepingService.localRuleParse("今天午饭花了35块", rules)
        assertEquals("expense", bill.type)
        assertEquals("餐饮", bill.categoryHint)
        assertEquals(3500L, bill.amountCents)
        // 原文整句当备注，用户确认时能看到自己说了什么
        assertEquals("今天午饭花了35块", bill.note)
    }

    @Test
    fun `带币种符号的金额与两位小数`() {
        val bill = AiBookkeepingService.localRuleParse("淘宝 ¥299.90", rules)
        assertEquals("购物", bill.categoryHint)
        // 299.90 在 double 里是 299.89999…，必须 round 而不是截断
        assertEquals(29_990L, bill.amountCents)
    }

    @Test
    fun `收入方向与收入分类`() {
        val bill = AiBookkeepingService.localRuleParse("工资到账15000元", rules)
        assertEquals("income", bill.type)
        assertEquals("工资", bill.categoryHint)
        assertEquals(1_500_000L, bill.amountCents)
    }

    @Test
    fun `认不出分类时落其他但金额照样解析`() {
        val bill = AiBookkeepingService.localRuleParse("嗯嗯啊啊 12元", rules)
        assertEquals("其他", bill.categoryHint)
        assertEquals(1200L, bill.amountCents)
    }

    @Test
    fun `完全没有金额时金额为 0 而不是崩`() {
        val bill = AiBookkeepingService.localRuleParse("KTV", rules)
        assertEquals(0L, bill.amountCents)
        assertEquals("娱乐", bill.categoryHint)
    }

    @Test
    fun `用户自建规则立刻生效`() {
        val custom = rules + LocalRule(
            id = "user-latte",
            name = "咖啡",
            type = LocalRule.TYPE_EXPENSE,
            category = "饮品",
            account = "微信",
            keywords = listOf("拿铁"),
        )
        val bill = AiBookkeepingService.localRuleParse("一杯拿铁 25元", custom)
        assertEquals("饮品", bill.categoryHint)
        assertEquals("微信", bill.accountHint)
        assertEquals(2500L, bill.amountCents)
    }

    @Test
    fun `停用的规则不参与识别`() {
        val disabled = listOf(LocalRuleDefaults.all.first { it.id == "builtin-food" }.copy(enabled = false))
        val bill = AiBookkeepingService.localRuleParse("午饭 30元", disabled)
        assertEquals("其他", bill.categoryHint)
    }

    @Test
    fun `金额解析认得出常见写法`() {
        assertEquals(35.0, AiBookkeepingService.extractAmount("午饭35块") ?: -1.0, 0.001)
        assertEquals(35.5, AiBookkeepingService.extractAmount("午饭 35.5 元") ?: -1.0, 0.001)
        assertEquals(199.0, AiBookkeepingService.extractAmount("¥199") ?: -1.0, 0.001)
        assertEquals(null, AiBookkeepingService.extractAmount("没有金额"))
    }
}
