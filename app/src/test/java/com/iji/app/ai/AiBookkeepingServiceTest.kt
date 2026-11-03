package com.iji.app.ai

import com.iji.app.accessibility.PaymentNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 规则解析结果 → 界面用的 BillResult 的字段映射。
 *
 * 只测这个纯函数，不碰网络：三级管线里「规则命中」这一层是不联网的，
 * 也是最容易被改坏又不容易在真机上发现的一层。
 */
class AiBookkeepingServiceTest {

    @Test
    fun billResultOf_mapsRuleFields() {
        val parsed = PaymentNotificationParser.parse(
            "com.unionpay",
            "您尾号为1234的银行卡于10月27日消费88.00元",
        )
        assertNotNull(parsed)
        val bill = AiBookkeepingService.billResultOf(parsed!!, note = "原始通知")

        assertEquals("expense", bill.type)
        assertEquals(8_800L, bill.amountCents)
        assertEquals("银行卡(尾号1234)", bill.accountHint)
        assertEquals("原始通知", bill.note)
        // 通知里没有分类信息，不猜
        assertEquals("其他", bill.categoryHint)
    }

    @Test
    fun billResultOf_keepsIncomeType() {
        val parsed = PaymentNotificationParser.parse(
            "com.sankuai.meituan",
            "退款成功，19.90元已退回美团月付",
        )
        assertNotNull(parsed)
        val bill = AiBookkeepingService.billResultOf(parsed!!, note = "退款通知")

        assertEquals("income", bill.type)
        assertEquals(1_990L, bill.amountCents)
    }

    @Test
    fun categoryHint_keepsModelCategoryWhenEnabled() {
        assertEquals("餐饮", categoryHintFor(keepCategory = true, raw = "餐饮"))
        // 模型没给分类时也不能是空串，落「其他」
        assertEquals("其他", categoryHintFor(keepCategory = true, raw = ""))
    }

    @Test
    fun categoryHint_forcesOtherWhenDisabled() {
        // 关掉「AI 识别分类」后，账单里不该出现模型猜的分类
        assertEquals("其他", categoryHintFor(keepCategory = false, raw = "购物"))
    }
}
