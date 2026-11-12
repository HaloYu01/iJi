package com.iji.app.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「AI 摘要」的输入与二次映射的取值逻辑。
 *
 * 摘要这段是全项目唯一把**汇总数据**发出去的地方，payload 一旦多带字段（比如原始备注、
 * 单笔流水）就是隐私回归；少带字段模型又会瞎编。所以字段集合直接锁在测试里。
 */
class AiSummaryPayloadTest {

    private fun input(prev: Long = 0L) = AiSummaryService.Input(
        dimension = "月",
        period = "2026年10月",
        expenseCents = 234_567,
        incomeCents = 1_500_000,
        billCount = 42,
        expenseCount = 30,
        daysInPeriod = 31,
        prevExpenseCents = prev,
        prevIncomeCents = 1_400_000,
        budgetCents = 300_000L,
        budgetSpentCents = 260_000L,
        topCategories = listOf(
            AiSummaryService.CategoryLine("餐饮", 98_000),
            AiSummaryService.CategoryLine("交通", 45_600),
            AiSummaryService.CategoryLine("购物", 30_000),
        ),
    )

    @Test
    fun `payload 只带聚合字段`() {
        // 环比要有上一周期数据才会写：prev = 0 的用例在下面单独钉（等价于「没有上一周期」）
        val json = JSONObject(AiSummaryService.payloadJson(input(prev = 200_000L)))
        assertEquals("月", json.getString("dimension"))
        assertEquals("2026年10月", json.getString("period"))
        assertEquals(2345.67, json.getDouble("expense"), 0.001)
        assertEquals(15000.0, json.getDouble("income"), 0.001)
        assertEquals(12654.33, json.getDouble("balance"), 0.001)
        assertEquals(42, json.getInt("billCount"))
        // 预计算衍生指标：数值在本地算死，模型只引用不计算
        assertEquals(78.2, json.getDouble("expensePerBill"), 0.001)
        assertEquals(75.7, json.getDouble("dailyAvgExpense"), 0.001)
        assertEquals(17.3, json.getDouble("expenseChangePct"), 0.001)
        assertEquals(7.1, json.getDouble("incomeChangePct"), 0.001)
        assertEquals(3000.0, json.getDouble("budget"), 0.001)
        assertEquals(86.7, json.getDouble("budgetUsedPct"), 0.001)
        val top = json.getJSONArray("topCategories").getJSONObject(0)
        assertEquals(41.8, top.getDouble("share"), 0.001)
        // 明确不含原始流水字段
        assertFalse(json.has("note"))
        assertFalse(json.has("transactions"))
    }

    @Test
    fun `上一周期支出为 0 时不写进 payload`() {
        // 写 0 会让模型算出一个假的「环比 -100%」
        val empty = JSONObject(AiSummaryService.payloadJson(input(prev = 0L)))
        assertFalse(empty.has("prevExpense"))
        assertFalse(empty.has("expenseChangePct"))
        val withPrev = JSONObject(AiSummaryService.payloadJson(input(prev = 200_000L)))
        assertEquals(2000.0, withPrev.getDouble("prevExpense"), 0.001)
        assertEquals(17.3, withPrev.getDouble("expenseChangePct"), 0.001)
    }

    @Test
    fun `衍生指标缺分母时一律不写`() {
        // 分母缺失还硬写就会得到 0 或 NaN，模型会把它当成真实数字引用
        val json = JSONObject(
            AiSummaryService.payloadJson(
                input().copy(
                    expenseCount = 0,
                    daysInPeriod = 0,
                    prevIncomeCents = 0L,
                    budgetCents = null,
                    budgetSpentCents = null,
                ),
            ),
        )
        assertFalse(json.has("expensePerBill"))
        assertFalse(json.has("dailyAvgExpense"))
        assertFalse(json.has("incomeChangePct"))
        assertFalse(json.has("budget"))
        assertFalse(json.has("budgetUsedPct"))
    }

    @Test
    fun `预算额度与已用必须成对才写`() {
        val json = JSONObject(AiSummaryService.payloadJson(input().copy(budgetSpentCents = null)))
        assertFalse(json.has("budget"))
        assertFalse(json.has("budgetUsedPct"))
    }

    @Test
    fun `分类最多带五条且保持金额顺序`() {
        val many = listOf(
            "餐饮" to 5000L, "交通" to 4000L, "购物" to 3000L,
            "居住" to 2000L, "娱乐" to 1000L, "医疗" to 500L,
        )
        val json = JSONObject(
            AiSummaryService.payloadJson(
                input().copy(topCategories = many.map { AiSummaryService.CategoryLine(it.first, it.second) }),
            ),
        )
        val arr = json.getJSONArray("topCategories")
        assertEquals(5, arr.length())
        assertEquals("餐饮", arr.getJSONObject(0).getString("name"))
        assertEquals(50.0, arr.getJSONObject(0).getDouble("amount"), 0.001)
    }

    @Test
    fun `二次映射只接受列表里已有的名字`() {
        val candidates = listOf("餐饮", "交通", "其他")
        // 模型多带引号 / 句号 / 前缀都要能认出来
        assertEquals("餐饮", AiBookkeepingService.pickFromList("\"餐饮\"", candidates))
        assertEquals("交通", AiBookkeepingService.pickFromList("交通。", candidates))
        assertEquals("其他", AiBookkeepingService.pickFromList("分类：其他", candidates))
        // 自造的名字绝不接受（否则记账面板会出现「未识别分类」）
        assertNull(AiBookkeepingService.pickFromList("吃饭", candidates))
        assertNull(AiBookkeepingService.pickFromList("", candidates))
        assertNull(AiBookkeepingService.pickFromList("   \n  ", candidates))
    }

    @Test
    fun `二次映射优先精确相等再退到包含`() {
        val candidates = listOf("微信零钱", "微信", "支付宝")
        // 「微信」是候选之一，不能因为「微信零钱」包含它就先返回长的
        assertEquals("微信", AiBookkeepingService.pickFromList("微信", candidates))
        // 线索是候选名的子串时才退到包含匹配
        assertEquals("微信零钱", AiBookkeepingService.pickFromList("零钱", candidates))
        assertTrue(candidates.contains(AiBookkeepingService.pickFromList("支付宝", candidates)))
    }
}
