package com.iji.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 关键词闸门：既要挡住闲聊，也不能把真实账单挡在门外。
 *
 * 第二点尤其重要——闸门误杀的表现是「开了自动记账却毫无反应」，
 * 比多跑几条正则难排查得多。
 */
class PaymentNotificationFilterTest {

    private val whitelist = PaymentNotificationFilter.DEFAULT_WHITELIST

    @Test
    fun defaultWhitelist_passesRealPaymentNotices() {
        val notices = listOf(
            "微信支付 已支付¥35.00",
            "您尾号为1234的银行卡于10月27日消费88.00元，余额1000.00元",
            "您有一笔19.90元的支出，使用花呗支付",
            "【招商银行】您账户1234于10月27日在【星巴克-咖啡】发生快捷支付扣款，人民币35.00",
            "您的尾号为5678的农行借记卡于10月27日发生一笔收入1200.00元",
        )
        notices.forEach { assertTrue(it, PaymentNotificationFilter.allowed(it, whitelist, "")) }
    }

    @Test
    fun defaultWhitelist_blocksChatterWithoutMoneyWords() {
        val texts = listOf(
            "今晚一起吃饭吗",
            "在吗？看到回我一下",
            "明天上午十点开会，别迟到",
            "这个视频太好笑了哈哈哈",
        )
        texts.forEach { assertFalse(it, PaymentNotificationFilter.allowed(it, whitelist, "")) }
    }

    @Test
    fun blacklistWins_overWhitelist() {
        val text = "张三向你转账100元"
        assertFalse(PaymentNotificationFilter.allowed(text, whitelist, "张三"))
        // 去掉黑名单就放行 —— 证明拦住它的确实是黑名单，而不是恰好没命中白名单
        assertTrue(PaymentNotificationFilter.allowed(text, whitelist, ""))
    }

    @Test
    fun blankWhitelist_allowsEverything() {
        assertTrue(PaymentNotificationFilter.allowed("今晚一起吃饭吗", "", ""))
    }

    @Test
    fun blankText_rejected() {
        assertFalse(PaymentNotificationFilter.allowed("   ", whitelist, ""))
    }

    @Test
    fun separators_acceptChineseCommaNewlinePipeAndSpace() {
        val text = "消费 35 元"
        assertTrue(PaymentNotificationFilter.allowed(text, "消费，餐饮", ""))
        assertTrue(PaymentNotificationFilter.allowed(text, "消费\n餐饮", ""))
        assertTrue(PaymentNotificationFilter.allowed(text, "餐饮|消费", ""))
        assertTrue(PaymentNotificationFilter.allowed(text, "餐饮 消费", ""))
    }

    @Test
    fun wordCount_ignoresBlankAndDuplicatedSeparators() {
        assertEquals(2, PaymentNotificationFilter.wordCount(" 消费 , ,\n餐饮 "))
        assertEquals(0, PaymentNotificationFilter.wordCount(""))
        assertEquals(
            PaymentNotificationFilter.DEFAULT_WHITELIST.split(',').size,
            PaymentNotificationFilter.wordCount(PaymentNotificationFilter.DEFAULT_WHITELIST),
        )
    }
}
