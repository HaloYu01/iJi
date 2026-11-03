package com.iji.app.ai

import com.iji.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 截屏账单列表页解析单测。
 *
 * 这些用例是从真实版式里提炼的「整屏 OCR 行」，覆盖最容易出错的三处：
 * 金额与日期/时间的混淆、独立成行的商户名被误当收支标签、以及分类关键词的贪婪匹配。
 */
class ScreenshotBillParserTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 14)

    private fun parse(vararg lines: String) = ScreenshotBillParser.parse(lines.toList(), today)

    // ── 支付宝列表页：商户一行、日期时间一行、金额一行 ──

    @Test
    fun alipayStyle_parsesEveryRow() {
        val out = parse(
            "支付宝账单", "全部", "筛选",
            "2026年10月",
            "星巴克咖啡", "10-14 12:30", "-32.00",
            "地铁出行", "10-14 09:15", "-3.00",
            "美团外卖", "10-13 20:01", "-45.50",
            "工资", "10-10 10:00", "+12,000.00",
        )

        assertEquals(4, out.size)

        assertEquals(3200L, out[0].amountCents)
        assertEquals(TransactionType.EXPENSE, out[0].type)
        assertEquals("星巴克咖啡", out[0].note)
        assertEquals("food", out[0].categoryId)
        assertEquals(LocalDate.of(2026, 10, 14), out[0].date)

        assertEquals(300L, out[1].amountCents)
        assertEquals("transport", out[1].categoryId)

        // 第三行的日期换成了 10-13，应逐行跟随，不能全用同一个日期
        assertEquals(4550L, out[2].amountCents)
        assertEquals(LocalDate.of(2026, 10, 13), out[2].date)

        assertEquals(TransactionType.INCOME, out[3].type)
        assertEquals(1_200_000L, out[3].amountCents)
        assertEquals("salary", out[3].categoryId)

        // 整屏文字里有「支付宝」，账户应被自动识别
        assertEquals("支付宝", out[0].account)
    }

    // ── 微信账单：商户 / 收支标签 / 金额 三行式 ──

    @Test
    fun weChatStyle_labelLineSetsDirection() {
        val out = parse(
            "微信支付", "账单", "本月账单",
            "瑞幸咖啡", "支出", "¥18.00",
            "转账-张三", "转账", "¥200.00",
            "红包", "收入", "¥66.60",
        )

        assertEquals(3, out.size)

        assertEquals(TransactionType.EXPENSE, out[0].type)
        assertEquals(1800L, out[0].amountCents)

        // 独立成行的「转账-张三」是**商户名**，不能被「转账」二字当成标签吞掉
        assertEquals(TransactionType.TRANSFER, out[1].type)
        assertEquals("转账-张三", out[1].note)

        assertEquals(TransactionType.INCOME, out[2].type)
        assertEquals("redpacket", out[2].categoryId)
    }

    // ── 噪声与防护 ──

    @Test
    fun chromeOnlyScreen_yieldsNothing() {
        val out = parse("账单", "全部账单", "收支统计", "筛选", "没有更多了", "统计", "2026", "12:30")
        assertTrue(out.isEmpty())
    }

    @Test
    fun overLimitAmount_isIgnoredAndDoesNotStealMerchant() {
        val out = parse(
            "订单号 20261014000123",
            "12:30",
            "2026-10-14",
            "真实消费",
            "¥1,200,000.00",
            "正常消费",
            "¥1,200.00",
        )

        // 超过 100 万的那一行金额不可信，必须丢掉；且它不能退化成"商户候选"把真正的商户顶掉
        assertEquals(1, out.size)
        assertEquals(120_000L, out[0].amountCents)
        assertEquals("正常消费", out[0].note)
    }

    @Test
    fun yearlessDate_beforeToday_keepsCurrentYear() {
        val out = parse("1-05", "超市购物", "-88.80")
        assertEquals(1, out.size)
        assertEquals(LocalDate.of(2026, 1, 5), out[0].date)
        assertEquals(8880L, out[0].amountCents)
    }

    @Test
    fun yearlessDate_afterToday_rollsBackOneYear() {
        // 今天 10-14，截图里出现 12-25 —— 只能是去年的
        val out = parse("12-25", "圣诞礼物", "-120.00")
        assertEquals(1, out.size)
        assertEquals(LocalDate.of(2025, 12, 25), out[0].date)
    }

    // ── 判重：计数式，不能把"两笔一样的消费"吞掉一笔 ──

    @Test
    fun markDuplicates_isCountAware() {
        val drafts = listOf(
            draft(LocalDate.of(2026, 10, 14), 3200L),
            draft(LocalDate.of(2026, 10, 14), 3200L),
        )
        // 库里只有 1 笔同键账单 -> 只应标掉 1 笔
        val existing = listOf(existingTx(7001L, LocalDate.of(2026, 10, 14), 3200L))

        val marked = markDuplicates(drafts, existing)

        // 只应标掉 1 笔；具体是哪一笔由遍历顺序决定（实现按顺序取走 id 池），断言不应绑定下标
        assertEquals(1, marked.count { it.duplicateOfId != null })
        val dupIndex = marked.indexOfFirst { it.duplicateOfId != null }
        assertEquals(7001L, marked[dupIndex].duplicateOfId)
        // 疑似重复默认不勾选，其余保持勾选
        assertTrue(!marked[dupIndex].selected)
        marked.filterIndexed { i, _ -> i != dupIndex }.forEach { assertTrue(it.selected) }
    }

    @Test
    fun markDuplicates_keyIgnoresNoteAndAccount() {
        // 判重键是 日期+类型+金额：账户/备注文字很难逐字一致，纳入键会让判重彻底失效
        val drafts = listOf(draft(LocalDate.of(2026, 10, 14), 3200L).copy(account = "微信", note = "星巴克"))
        val existing = listOf(
            existingTx(1L, LocalDate.of(2026, 10, 14), 3200L).copy(account = "现金", note = "其它备注"),
        )
        assertEquals(1, markDuplicates(drafts, existing).count { it.duplicateOfId != null })
    }

    private fun draft(date: LocalDate, cents: Long) = BillDraft(
        type = TransactionType.EXPENSE,
        categoryId = "food",
        amountCents = cents,
        note = "星巴克",
        account = "支付宝",
        date = date,
    )

    private fun existingTx(id: Long, date: LocalDate, cents: Long) = com.iji.app.data.model.Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        categoryId = "food",
        amountCents = cents,
        date = date,
    )
}
