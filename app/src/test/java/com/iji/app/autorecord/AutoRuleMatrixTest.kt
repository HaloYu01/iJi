package com.iji.app.autorecord

import com.iji.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 8 平台规则矩阵。
 *
 * 每个平台至少覆盖：
 * 1. 一条主力详情/成功页（正向）；
 * 2. 一条方向容易记反的页面（转账/收款/提现）；
 * 3. 一条负向（列表页 / 密码页 / 缺金额），确保不会为了"能识别"而放宽到误识别。
 *
 * 页面文本都取自钱迹 4.5.5 无障碍资料里的页面特征（标题、状态标签、字段标签），
 * 但结构按本项目 [AutoNode] 的拍平模型重新组织。
 */
class AutoRuleMatrixTest {

    private fun nodes(vararg texts: String): List<AutoNode> =
        texts.mapIndexed { index, text -> AutoNode(text, "android.widget.TextView", null, index) }

    private fun parse(pkg: String, vararg texts: String): AutoBillDraft? {
        val platform = AutoPlatforms.byPackage(pkg) ?: error("未注册平台 $pkg")
        return AutoBillEngine.parse(platform, nodes(*texts))
    }

    // ── 微信 ────────────────────────────────────────────────────────────────

    @Test
    fun `微信转账待收款记为支出`() {
        val draft = parse(
            "com.tencent.mm",
            "待 张三 收款", "转账金额", "¥100.00",
            "转账时间", "2026-11-14 10:00",
            "支付方式", "零钱",
        )
        assertNotNull(draft)
        assertEquals("WeChatTransferDetailWaiting", draft!!.pageType)
        assertEquals(10000L, draft.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals("张三", draft.merchant)
    }

    @Test
    fun `微信商家转账记为收入`() {
        val draft = parse(
            "com.tencent.mm",
            "商家转账-来自 张三", "付款商家", "张三",
            "收款方式", "零钱", "转账时间", "2026-11-14 12:00",
            "转账金额", "¥200.00",
        )
        assertNotNull(draft)
        assertEquals("WeChatTransferInDetail", draft!!.pageType)
        assertEquals(TransactionType.INCOME, draft.type)
        assertEquals(20000L, draft.amountCents)
        assertEquals("零钱", draft.inAsset)
    }

    @Test
    fun `微信零钱提现成功记为转账`() {
        val draft = parse(
            "com.tencent.mm",
            "零钱提现", "发起提现申请", "提现金额", "￥100.00",
            "到账银行卡", "招商银行",
        )
        assertNotNull(draft)
        assertEquals("WechatWithdrawSuccess", draft!!.pageType)
        assertEquals(TransactionType.TRANSFER, draft.type)
        assertEquals(10000L, draft.amountCents)
        assertEquals("招商银行", draft.inAsset)
    }

    @Test
    fun `微信交易详情页金额在状态上方且带负号`() {
        // 真机截图：交易详情 / -6.00 / 扫二维码付款-给XX / 当前状态 / 支付成功
        val draft = parse(
            "com.tencent.mm",
            "主页", "交易详情", "-6.00",
            "扫二维码付款-给朱友亚代办车辆年审13952207393",
            "当前状态", "支付成功",
            "收款方备注", "二维码收款",
            "支付方式", "零钱",
            "转账时间", "2026年11月15日 19:44:10",
            "转账单号", "10001073012026111501710034600534",
        )
        assertNotNull(draft)
        assertEquals("WeChatBillDetail", draft!!.pageType)
        assertEquals(600L, draft.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals("零钱", draft.outAsset)
        assertEquals("二维码收款", draft.remark)
    }

    @Test
    fun `微信支付账单页没有当前状态也能识别`() {
        // 真机截图版式：标题「支付账单」、副标题「支付成功」、金额紧跟其后，没有「当前状态」
        val draft = parse(
            "com.tencent.mm",
            "支付账单", "支付成功", "￥1.00",
            "支付时间", "2026-11-16 19:10:22",
            "商品", "2026中国甲辰（龙）年贺岁普通纪念钞",
            "商户全称", "中国邮政储蓄银行",
            "支付方式", "零钱",
        )
        assertNotNull(draft)
        assertEquals("WeChatPayBillDetail", draft!!.pageType)
        assertEquals(100L, draft.amountCents)
        assertEquals("中国邮政储蓄银行", draft.merchant)
        assertEquals("零钱", draft.outAsset)
        assertEquals(TransactionType.EXPENSE, draft.type)
    }

    @Test
    fun `微信支付成功页不会被自身条件排除`() {
        // 钱迹这里用 ForbiddenCoexist：只有同时出现「支付成功 + 去下单」才是引导页
        val draft = parse(
            "com.tencent.mm",
            "支付成功", "¥25.00", "支付方式", "零钱", "完成",
        )
        assertNotNull(draft)
        assertEquals("WeChatPaySuccess", draft!!.pageType)
        assertEquals(2500L, draft.amountCents)
        assertEquals("零钱", draft.outAsset)
    }

    // ── 支付宝 ──────────────────────────────────────────────────────────────

    @Test
    fun `支付宝支付成功页`() {
        val draft = parse(
            "com.eg.android.AlipayGphone",
            "支付成功", "¥18.50", "交易方式", "余额宝", "完成",
        )
        assertNotNull(draft)
        assertEquals("AlipayPaySuccess", draft!!.pageType)
        assertEquals(1850L, draft.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
    }

    @Test
    fun `支付宝转账成功带收款方`() {
        val draft = parse(
            "com.eg.android.AlipayGphone",
            "转账成功", "收款方", "张三", "付款方式", "余额宝", "¥50.00",
        )
        assertNotNull(draft)
        assertEquals("AlipayTransfer", draft!!.pageType)
        assertEquals(5000L, draft.amountCents)
        assertEquals("张三", draft.merchant)
        assertEquals("转账给张三", draft.remark)
    }

    @Test
    fun `支付宝余额充值记为转账`() {
        val draft = parse(
            "com.eg.android.AlipayGphone",
            "余额明细详情", "余额充值", "支付时间 2026-11-14 12:00",
            "对方账户", "招商银行", "¥100.00",
        )
        assertNotNull(draft)
        assertEquals("AlipayChargeDetail", draft!!.pageType)
        assertEquals(TransactionType.TRANSFER, draft.type)
        assertEquals(10000L, draft.amountCents)
    }

    @Test
    fun `支付宝账单详情没有订单金额标签时取交易成功上方的金额`() {
        // 真机截图版式：商户名 / -10.00 / 交易成功；页面上没有「订单金额」这个标签
        val draft = parse(
            "com.eg.android.AlipayGphone",
            "账单详情", "全部账单", "江苏师范大学", "-10.00", "交易成功",
            "支付时间", "2026-11-14 12:08:45", "付款方式", "余额宝",
            "商品说明", "江苏师范大学1_食堂_民族餐厅-6号窗口",
            "收款方全称", "江苏师范大学",
        )
        assertNotNull(draft)
        assertEquals("AlipayBillDetail", draft!!.pageType)
        assertEquals(1000L, draft.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
    }

    @Test
    fun `零宽字符与异体负号不影响金额识别`() {
        // 真机部分页面金额节点带零宽空格或全角负号，原样匹配会整条规则失配
        assertTrue(AutoValues.isAmount("\u200B-10.00"))
        assertEquals(1000L, AutoValues.amountCents("\u221210.00"))
        assertEquals("-10.00", AutoValues.normalize("\u200B\uFF0D10.00"))
    }

    // ── 云闪付 ──────────────────────────────────────────────────────────────

    @Test
    fun `云闪付账单详情取订单金额`() {
        val draft = parse(
            "com.unionpay",
            "账单详情", "当前状态", "交易成功", "订单金额", "-¥12.00",
            "订单时间", "2026-11-14 12:00", "付款方式", "招商银行",
        )
        assertNotNull(draft)
        assertEquals("UnionpayBillDetail", draft!!.pageType)
        assertEquals(1200L, draft.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
    }

    @Test
    fun `云闪付入账交易记为收入`() {
        val draft = parse(
            "com.unionpay",
            "交易详情", "卡号", "6222****1234", "交易时间", "2026-11-14 12:00",
            "交易类别", "入账", "¥500.00",
        )
        assertNotNull(draft)
        assertEquals("UnionpayMessageOrderDetail", draft!!.pageType)
        assertEquals(TransactionType.INCOME, draft.type)
        assertEquals(50000L, draft.amountCents)
    }

    // ── 京东 ────────────────────────────────────────────────────────────────

    @Test
    fun `京东钱包账单V2`() {
        val draft = parse(
            "com.jingdong.app.mall",
            "账单详情", "支付立减", "交易成功", "创建时间", "总订单编号",
            "京东平台商户 -19.90 交易成功", "¥19.90", "支付方式", "京东支付",
        )
        assertNotNull(draft)
        assertEquals("JingDongWalletBillDetailV2", draft!!.pageType)
        assertEquals(1990L, draft.amountCents)
        assertEquals("京东支付", draft.outAsset)
    }

    @Test
    fun `京东支付成功页带商户`() {
        val draft = parse(
            "com.jingdong.app.mall",
            "支付成功", "京东商城 ¥99.00",
        )
        assertNotNull(draft)
        assertEquals("JingDongPaySuccess", draft!!.pageType)
        assertEquals(9900L, draft.amountCents)
        assertEquals("京东商城", draft.merchant)
    }

    @Test
    fun `京东订单详情页`() {
        val draft = parse(
            "com.jingdong.app.mall",
            "订单详情", "待收货", "实付款", "¥59.00", "订单编号", "123456789",
            "支付时间", "2026-11-14 12:00",
        )
        assertNotNull(draft)
        assertEquals("JingDongOrderDetail", draft!!.pageType)
        assertEquals(5900L, draft.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
    }

    // ── 美团 ────────────────────────────────────────────────────────────────

    @Test
    fun `美团外卖订单`() {
        val draft = parse(
            "com.sankuai.meituan",
            "订单已完成", "支付方式", "微信支付", "实付", "¥45.00",
            "下单时间", "2026-11-14 18:20",
        )
        assertNotNull(draft)
        assertEquals("MeiTuanDeliveryDetail", draft!!.pageType)
        assertEquals(4500L, draft.amountCents)
        assertEquals("微信支付", draft.outAsset)
    }

    @Test
    fun `美团买单成功页`() {
        val draft = parse(
            "com.sankuai.meituan",
            "买单成功！", "实付金额：", "¥32.00", "复制",
            "消费时间：", "2026-11-14 20:00",
        )
        assertNotNull(draft)
        assertEquals("MeiTuanVoucherSuccessDetail", draft!!.pageType)
        assertEquals(3200L, draft.amountCents)
        assertTrue(draft.timeMillis > 0)
    }

    // ── 淘宝 ────────────────────────────────────────────────────────────────

    @Test
    fun `淘宝商户取状态词上方`() {
        // 钱迹的 PrevTextNotEmpty：状态词上方是店铺名，金额上方是商品名
        val draft = parse(
            "com.taobao.taobao",
            "商品名A", "¥88.00", "实付款", "店铺A", "交易成功",
        )
        assertNotNull(draft)
        assertEquals("TaoBaoOrderDetailSuccess", draft!!.pageType)
        assertEquals(8800L, draft.amountCents)
        assertEquals("店铺A", draft.merchant)
        assertEquals("商品名A", draft.remark)
    }

    @Test
    fun `淘宝话费充值`() {
        val draft = parse(
            "com.taobao.taobao",
            "话费充值-13800138000", "实付款", "¥50.00",
        )
        assertNotNull(draft)
        assertEquals("TaoBaoOrderDetailHuafei", draft!!.pageType)
        assertEquals(5000L, draft.amountCents)
    }

    // ── 拼多多 ──────────────────────────────────────────────────────────────

    @Test
    fun `拼多多订单详情`() {
        val draft = parse(
            "com.xunmeng.pinduoduo",
            "下单时间： 2026-11-14 12:00", "交易成功", "申请退款",
            "商品名称： 数据线", "¥9.90", "实付：¥9.90元", "支付方式： 微信支付",
        )
        assertNotNull(draft)
        assertEquals("PddOrderDetail", draft!!.pageType)
        assertEquals(990L, draft.amountCents)
        assertEquals("数据线", draft.remark)
    }

    @Test
    fun `拼多多先用后付`() {
        val draft = parse(
            "com.xunmeng.pinduoduo",
            "先用后付", "0 元下单", "确认收货后，自动付款¥39.90",
        )
        assertNotNull(draft)
        assertEquals("PddPayFirstDialog", draft!!.pageType)
        assertEquals(3990L, draft.amountCents)
    }

    @Test
    fun `拼多多钱包账单详情`() {
        val draft = parse(
            "com.xunmeng.pinduoduo",
            "账单详情", "当前状态", "支付成功", "¥9.90",
            "商品详情", "数据线", "支付时间", "2026-11-14 09:00",
            "支付方式", "微信支付",
        )
        assertNotNull(draft)
        assertEquals("PddWalletBillDetail", draft!!.pageType)
        assertEquals(990L, draft.amountCents)
        assertEquals("数据线", draft.remark)
    }

    @Test
    fun `拼多多订单详情没有售后按钮也能识别`() {
        // 钱迹把「申请退款/取消订单」当页面条件，但订单状态不同这组按钮并不在首屏
        val draft = parse(
            "com.xunmeng.pinduoduo",
            "下单时间： 2026-11-14 12:00", "交易成功",
            "商品名称： 数据线", "¥9.90", "实付：¥9.90元",
        )
        assertNotNull(draft)
        assertEquals("PddOrderDetail", draft!!.pageType)
        assertEquals(990L, draft.amountCents)
    }

    // ── 抖音 ────────────────────────────────────────────────────────────────

    @Test
    fun `抖音转账待收款记为支出`() {
        val draft = parse(
            "com.ss.android.ugc.aweme",
            "转账详情", "待 张三 收款", "¥100.00",
            "转账时间", "2026-11-14 10:00", "转账附言", "还款",
        )
        assertNotNull(draft)
        assertEquals("DouyingTransferWaiting", draft!!.pageType)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals(10000L, draft.amountCents)
    }

    @Test
    fun `抖音转账已收款记为收入`() {
        val draft = parse(
            "com.ss.android.ugc.aweme",
            "转账详情", "张三已收款", "转账金额", "¥50.00",
            "收款方式", "抖音零钱", "转账时间", "2026-11-14 10:00",
        )
        assertNotNull(draft)
        assertEquals("DouyingTransferReceived", draft!!.pageType)
        assertEquals(TransactionType.INCOME, draft.type)
        assertEquals("抖音零钱", draft.inAsset)
    }

    @Test
    fun `抖音订单详情页`() {
        val draft = parse(
            "com.ss.android.ugc.aweme",
            "订单详情", "支付成功", "商品订单", "商品A", "¥30.00",
            "支付方式", "抖音支付（抖音零钱）",
        )
        assertNotNull(draft)
        assertEquals("DouyingOrderDetail", draft!!.pageType)
        assertEquals(3000L, draft.amountCents)
    }

    // ── 负向 ────────────────────────────────────────────────────────────────

    @Test
    fun `支付密码页不识别`() {
        assertNull(parse("com.tencent.mm", "请输入支付密码", "￥25.00", "零钱"))
    }

    @Test
    fun `订单列表没有金额时不识别`() {
        assertNull(parse("com.taobao.taobao", "商品名A", "实付款", "交易成功"))
    }

    @Test
    fun `聊天页不识别`() {
        assertNull(parse("com.tencent.mm", "当前状态", "支付时间", "2026-11-14", "你好"))
    }

    @Test
    fun `美团列表卡片不识别`() {
        // 列表卡片有「订单已完成 + 支付方式 + 金额」，但没有「实付」标签
        assertNull(parse("com.sankuai.meituan", "订单已完成", "支付方式", "¥45.00"))
    }

    // ── 结构约束 ────────────────────────────────────────────────────────────

    @Test
    fun `页面类型全局唯一`() {
        val pageTypes = AutoPlatforms.all.flatMap { platform -> platform.rules.map { it.pageType } }
        assertEquals(pageTypes.size, pageTypes.toSet().size)
        assertTrue(AutoPlatforms.all.all { it.rules.size >= 2 })
    }

    @Test
    fun `八个平台都已注册`() {
        assertEquals(
            setOf(
                "com.tencent.mm", "com.eg.android.AlipayGphone", "com.unionpay",
                "com.jingdong.app.mall", "com.sankuai.meituan", "com.taobao.taobao",
                "com.xunmeng.pinduoduo", "com.ss.android.ugc.aweme",
            ),
            AutoPlatforms.packages(),
        )
    }
}