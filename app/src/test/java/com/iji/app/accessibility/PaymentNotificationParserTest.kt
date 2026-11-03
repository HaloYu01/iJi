package com.iji.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 支付通知识别规则单测。
 *
 * 用例文案取自 AutoAccounting（AutoAccountingOrg/AutoAccounting）规则包 v0.5.6
 * 的 notice 目录规则模板与其测试数据（test_data_didi.json），即各 App / 银行
 * 真实通知的标准写法；另覆盖本地自有的防误判约束（验证码、余额、金额上限）。
 */
class PaymentNotificationParserTest {

    private fun parse(text: String) = PaymentNotificationParser.parse(null, text)

    private fun parse(app: String?, text: String) = PaymentNotificationParser.parse(app, text)

    // ── App 专属规则：微信 ──

    @Test
    fun wechatPay_withPackage_parsesFirstYuanAmount() {
        val parsed = parse("com.tencent.mm", "微信支付凭证 已支付¥48.10")
        assertNotNull(parsed)
        parsed!!
        assertEquals("expense", parsed.type)
        assertEquals(4810L, parsed.amountCents)
        assertEquals("微信支付", parsed.accountHint)
    }

    @Test
    fun wechatPay_withoutPackage_needsWechatWord() {
        // 正文同时含「微信支付」+「已支付」时，即使拿不到来源包名也能识别
        val parsed = parse("微信支付凭证已支付¥1,234.56")
        assertNotNull(parsed)
        assertEquals(123456L, parsed!!.amountCents)
        assertEquals("expense", parsed.type)

        // 只有「已支付」但没有包名 → 专属规则不放行（有 ¥ 金额时走通用兜底为支出）
        assertNull(parse("com.unknown.app", "订单已支付，请勿重复提交"))
    }

    // ── App 专属规则：支付宝 ──

    @Test
    fun alipayHuabei_parsesExpense() {
        val parsed = parse(
            "com.eg.android.AlipayGphone",
            "你有一笔48.10元的支出，点击领取5个支付宝积分。使用花呗支付，更多优惠尽在支付宝",
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("expense", parsed.type)
        assertEquals(4810L, parsed.amountCents)
        assertEquals("支付宝", parsed.merchant)
        assertEquals("支付宝花呗", parsed.accountHint)
    }

    @Test
    fun mybankDidi_deduction_parsesExpense() {
        val parsed = parse("com.eg.android.AlipayGphone", "滴滴支付 23.50元，网商银行交易提醒")
        assertNotNull(parsed)
        parsed!!
        assertEquals("expense", parsed.type)
        assertEquals(2350L, parsed.amountCents)
        assertEquals("滴滴", parsed.merchant)
        assertEquals("网商银行", parsed.accountHint)
    }

    // ── App 专属规则：云闪付 ──

    @Test
    fun unionpay_purchase_parsesExpenseWithCardTail() {
        val parsed = parse(
            "com.unionpay",
            "支付助手：付款成功 您尾号为1234的银行卡于10月7日8时29分消费48.10元",
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("expense", parsed.type)
        assertEquals(4810L, parsed.amountCents)
        assertEquals("银行卡(尾号1234)", parsed.accountHint)
    }

    @Test
    fun unionpay_refund_parsesIncome() {
        val parsed = parse(
            "com.unionpay",
            "您尾号为5678的银行卡于7日8时8分退款100.00元，请注意查收",
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("income", parsed.type)
        assertEquals(10000L, parsed.amountCents)
    }

    @Test
    fun unionpay_transferIn_parsesCounterparty() {
        val parsed = parse(
            "com.unionpay",
            "您的招商银行卡收到来自张三的转账500.00元，已于10月7日成功到账！",
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("income", parsed.type)
        assertEquals(50000L, parsed.amountCents)
        assertEquals("张三", parsed.merchant)
    }

    @Test
    fun unionpay_transferOut_parsesCounterparty() {
        val parsed = parse(
            "com.unionpay",
            "您向李四的储蓄卡转账300.00元，已于10月7日成功到账！",
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("expense", parsed.type)
        assertEquals(30000L, parsed.amountCents)
        assertEquals("李四", parsed.merchant)
        assertEquals("储蓄卡", parsed.accountHint)
    }

    // ── App 专属规则：招商银行 ──

    @Test
    fun cmbCreditCard_purchase() {
        val parsed = parse(
            "cmb.pb",
            "信用卡通知：您尾号6688的招行信用卡消费1,234.56元，可用额度¥58,539.79",
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("expense", parsed.type)
        assertEquals(123456L, parsed.amountCents)
        assertEquals("招行信用卡(尾号6688)", parsed.accountHint)
    }

    @Test
    fun cmbQuickPay_extractsShopFromBrackets() {
        val parsed = parse(
            "cmb.pb",
            "您账户6688于10月7日08:29在【肯德基-宅急送】发生快捷支付扣款，金额48.10",
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("expense", parsed.type)
        assertEquals(4810L, parsed.amountCents)
        assertEquals("肯德基", parsed.merchant)
    }

    @Test
    fun cmb_income_parsesRmbAmount() {
        val parsed = parse("cmb.pb", "您尾号6688的一卡通账户入账人民币500.00元")
        assertNotNull(parsed)
        parsed!!
        assertEquals("income", parsed.type)
        assertEquals(50000L, parsed.amountCents)

        val income2 = parse("cmb.pb", "您账户6688于10月7日收款人民币88.88")
        assertNotNull(income2)
        assertEquals(8888L, income2!!.amountCents)
        assertEquals("income", income2.type)
    }

    @Test
    fun zhangshangLife_realSample_parsesShopName() {
        // AutoAccounting 测试数据（test_data_didi.json）里的真实文案
        val parsed = parse(
            "com.cmbchina.ccd.pluto.cmbActivity",
            "您在支付宝-北京嘎嘎无限科技发展有限公司有一笔48.10人民币的消费已成功，点击查看详情",
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("expense", parsed.type)
        assertEquals(4810L, parsed.amountCents)
        // splitShop：商户取「-」前的主体
        assertEquals("支付宝", parsed.merchant)
    }

    // ── App 专属规则：其他银行 / App ──

    @Test
    fun icbc_expenseAndIncome_byVerb() {
        val expense = parse(
            "com.icbc",
            "您尾号1234卡10月7日8:29消费(北京某某餐饮有限公司)48.10元，余额1,000.00元",
        )
        assertNotNull(expense)
        expense!!
        assertEquals("expense", expense.type)
        assertEquals(4810L, expense.amountCents)
        assertEquals("北京某某餐饮有限公司", expense.merchant)

        val income = parse(
            "com.icbc",
            "您尾号1234卡10月8日9:00入账(某某公司工资)5,000.00元",
        )
        assertNotNull(income)
        assertEquals("income", income!!.type)
    }

    @Test
    fun abc_income() {
        val parsed = parse(
            "com.android.bankabc",
            "您尾号为1234的农行借记卡于10月07日08:29发生一笔收入3,000.00元",
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("income", parsed.type)
        assertEquals(300000L, parsed.amountCents)
    }

    @Test
    fun citic_purchaseRefundAndCashback() {
        val purchase = parse("com.citiccard.mobilebank", "您有一笔48.10元的消费(尾号6688)")
        assertNotNull(purchase)
        assertEquals("expense", purchase!!.type)
        assertEquals(4810L, purchase.amountCents)

        val cashback = parse(
            "com.citiccard.mobilebank",
            "您48.10元现金奖励笔笔返0.48元已成功返现至您的信用卡账户中",
        )
        assertNotNull(cashback)
        assertEquals("income", cashback!!.type)
        assertEquals(48L, cashback.amountCents)

        val refund = parse("com.citiccard.mobilebank", "您有一笔人民币48.10元的退款（尾号6688）")
        assertNotNull(refund)
        assertEquals("income", refund!!.type)
    }

    @Test
    fun meituanMonthlyPay_expenseAndRefund() {
        val expense = parse("com.sankuai.meituan", "【美团月付】成功支付48.10元")
        assertNotNull(expense)
        assertEquals("expense", expense!!.type)
        assertEquals(4810L, expense.amountCents)
        assertEquals("美团月付", expense.accountHint)

        val refund = parse(
            "com.sankuai.meituan",
            "外卖退款成功，48.10元已退回美团月付，并抵扣10月账单。点击查看详情>",
        )
        assertNotNull(refund)
        assertEquals("income", refund!!.type)
        assertEquals(4810L, refund.amountCents)
    }

    @Test
    fun miSmartCard_zeroAmountIgnored() {
        assertNull(parse("com.miui.tsmclient", "刷卡完成，交易金额：0.00元"))
        val parsed = parse("com.miui.tsmclient", "刷卡完成，交易金额：2.00元")
        assertNotNull(parsed)
        assertEquals("expense", parsed!!.type)
        assertEquals(200L, parsed.amountCents)
    }

    @Test
    fun digitalRmb_payment() {
        val parsed = parse(
            "cn.gov.pbc.dcep",
            "付款通知 您的运营机构数字人民币钱包在北京某超市支付¥23.90",
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("expense", parsed.type)
        assertEquals(2390L, parsed.amountCents)
        assertEquals("北京某超市", parsed.merchant)
        assertEquals("数字人民币", parsed.accountHint)
    }

    @Test
    fun walletPay_finshell() {
        val parsed = parse("com.finshell.wallet", "本次消费：15.00 元")
        assertNotNull(parsed)
        assertEquals("expense", parsed!!.type)
        assertEquals(1500L, parsed.amountCents)
    }

    // ── 专属规则与包名的绑定关系 ──

    @Test
    fun appScopedRule_notAppliedToOtherApps() {
        // 云闪付文案模板出现在别的包名下不放行专属规则；通用兜底也能兜住
        val parsed = parse("com.other", "您尾号为1234的银行卡于10月7日消费48.10元")
        assertNotNull(parsed)
        assertEquals("expense", parsed!!.type)
        assertEquals(4810L, parsed.amountCents)
    }

    // ── 通用兜底规则 ──

    @Test
    fun generic_rmbAndYuanAndSymbolAmounts() {
        val rmb = parse("【招商银行】您尾号1234的账户入账人民币500.00元")
        assertNotNull(rmb)
        assertEquals("income", rmb!!.type)
        assertEquals(50000L, rmb.amountCents)
        // 短信签名「【招商银行】」成为账户提示
        assertEquals("招商银行", rmb.accountHint)

        val yuan = parse("您在中国石化消费358.20元")
        assertNotNull(yuan)
        assertEquals("expense", yuan!!.type)
        assertEquals(35820L, yuan.amountCents)

        val symbol = parse("微信支付：付款¥9.90成功")
        assertNotNull(symbol)
        assertEquals("expense", symbol!!.type)
        assertEquals(990L, symbol.amountCents)
    }

    @Test
    fun generic_prefersAmountNotPrecededByBalance() {
        val parsed = parse("您消费48.10元，可用余额58,539.79元")
        assertNotNull(parsed)
        assertEquals(4810L, parsed!!.amountCents)
    }

    @Test
    fun generic_incomeWinsWhenBothVerbsPresent() {
        // 「消费的48元已退回」：退款语义优先于句中残留的消费字样
        val parsed = parse("您消费的48.00元已退回原支付渠道")
        assertNotNull(parsed)
        assertEquals("income", parsed!!.type)
    }

    @Test
    fun generic_merchantKeyword() {
        val parsed = parse("支付成功。商家：瑞幸咖啡，金额¥19.90")
        assertNotNull(parsed)
        assertEquals("瑞幸咖啡", parsed!!.merchant)
        assertEquals(1990L, parsed.amountCents)
    }

    // ── 防误判约束 ──

    @Test
    fun blacklist_otpAndPrizeMessagesRejected() {
        assertNull(parse("【支付宝】您的验证码是483920，请勿泄露给他人"))
        assertNull(parse("您消费的账单短信校验码为8823"))
        assertNull(parse("恭喜您中奖88,000.00元，点击领取"))
    }

    @Test
    fun rejected_noVerbOrNoAmount() {
        // 无收支语义 → 不猜
        assertNull(parse("您的账户余额为58,539.79元"))
        assertNull(parse("招商银行 App 升级公告"))
        // 有语义但无明确金额（余额不算账单金额）→ 不猜
        assertNull(parse("您的消费额度已提升，当前额度50,000.00元"))
    }

    @Test
    fun rejected_amountAboveCap() {
        assertNull(parse("您消费3,000,000.00元"))
    }

    @Test
    fun rejected_zeroAmount() {
        assertNull(parse("您支出0.00元"))
    }
}
