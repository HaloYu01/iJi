package com.iji.app.autorecord

import com.iji.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节点识别引擎的纯 JVM 用例。
 *
 * 引擎不依赖无障碍框架（只吃 [AutoNode] 列表），所以可以在没有支付 App 的环境里
 * 用「页面文本快照」直接验证 8 个平台的规则是否成立——真机上出问题时，
 * 先把那屏的文本抄进来复现，比反复切 App 快得多。
 */
class AutoBillEngineTest {

    private fun nodes(vararg texts: String): List<AutoNode> =
        texts.mapIndexed { index, text -> AutoNode(text, "android.widget.TextView", null, index) }

    private fun parse(pkg: String, vararg texts: String): AutoBillDraft? {
        val platform = AutoPlatforms.byPackage(pkg) ?: error("未注册平台 $pkg")
        return AutoBillEngine.parse(platform, nodes(*texts))
    }

    @Test
    fun `微信账单详情识别为支出`() {
        val draft = parse(
            "com.tencent.mm",
            "当前状态", "支付成功",
            "支付金额", "¥25.00",
            "支付时间", "2026-11-14 12:30",
            "商户全称", "瑞幸咖啡",
            "支付方式", "零钱",
        )
        assertNotNull(draft)
        assertEquals(2500L, draft!!.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals("瑞幸咖啡", draft.merchant)
        assertEquals("微信", draft.platform)
        assertTrue(draft.timeMillis > 0)
    }

    @Test
    fun `微信收款详情识别为收入`() {
        val draft = parse(
            "com.tencent.mm",
            "当前状态", "收款成功",
            "收款金额", "12.00",
            "收款时间", "2026-11-14 09:15",
            "付款方留言", "早餐",
        )
        assertNotNull(draft)
        assertEquals(1200L, draft!!.amountCents)
        assertEquals(TransactionType.INCOME, draft.type)
        assertEquals("早餐", draft.remark)
    }

    @Test
    fun `微信首页不参与识别`() {
        val platform = AutoPlatforms.byPackage("com.tencent.mm")!!
        assertTrue(!platform.isCandidatePage("com.tencent.mm.ui.LauncherUI"))
        assertTrue(platform.isCandidatePage("com.tencent.mm.plugin.wallet.ui.WalletHomeUI"))
    }

    @Test
    fun `支付宝支付成功页识别金额`() {
        val draft = parse(
            "com.eg.android.AlipayGphone",
            "支付成功", "¥18.50",
            "交易方式", "余额宝",
            "完成",
        )
        assertNotNull(draft)
        assertEquals(1850L, draft!!.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals("支付宝", draft.platform)
    }

    @Test
    fun `美团订单详情识别实付与时间`() {
        val draft = parse(
            "com.sankuai.meituan",
            "已消费", "订单信息",
            "实付", "¥45.00",
            "下单时间：", "2026-11-14 18:20",
            "订单编号：", "1234567890",
        )
        assertNotNull(draft)
        assertEquals(4500L, draft!!.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertTrue(draft.timeMillis > 0)
    }

    @Test
    fun `拼多多账单详情识别金额与备注`() {
        val draft = parse(
            "com.xunmeng.pinduoduo",
            "账单详情", "当前状态", "支付成功",
            "¥9.90",
            "商品详情", "数据线",
            "支付时间", "2026-11-14 09:00",
            "支付方式", "微信支付",
        )
        assertNotNull(draft)
        assertEquals(990L, draft!!.amountCents)
        assertEquals("数据线", draft.remark)
    }

    @Test
    fun `抖音转账待收款记为支出`() {
        val draft = parse(
            "com.ss.android.ugc.aweme",
            "转账详情", "待 张三 收款", "¥100.00",
            "转账时间", "2026-11-14 10:00",
            "转账附言", "还款",
        )
        assertNotNull(draft)
        assertEquals(10000L, draft!!.amountCents)
        // 「待对方收款」是付款方视角，钱迹默认 billType=0（支出），不是收入
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals("还款", draft.remark)
    }

    @Test
    fun `没有金额的页面不产出账单`() {
        val draft = parse(
            "com.tencent.mm",
            "当前状态", "支付成功",
            "支付时间", "2026-11-14 12:30",
        )
        assertNull(draft)
    }

    @Test
    fun `未注册的包名直接返回空`() {
        assertNull(AutoBillEngine.parse("com.example.unknown", null))
    }

    @Test
    fun `金额清洗支持货币符号与千分位`() {
        assertEquals(123456L, AutoValues.amountCents("¥1,234.56"))
        assertEquals(1000L, AutoValues.amountCents("-10.00"))
        assertEquals(888L, AutoValues.amountCents("已支付 8.88 元"))
        assertNull(AutoValues.amountCents("2026-11-14"))
    }

    @Test
    fun `时间清洗支持年月日与斜杠`() {
        assertTrue(AutoValues.isTime("2026-11-14 12:30:00"))
        assertTrue(AutoValues.isTime("2026/11/14 12:30"))
        assertTrue(AutoValues.isTime("2026年11月14日"))
        assertTrue(AutoValues.parseTime("2026-11-14 12:30") > 0)
    }
}
