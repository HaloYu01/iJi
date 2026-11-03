package com.iji.app.data.repository

import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * BackupRepository 纯逻辑单测（P1-4 基线）。
 *
 * 目的：锁定 CSV/JSON 解析与金额换算行为，防止后续重构（如抽 SettingsRepository、
 * 账本去硬编码、Coil 迁移）时无意回归数据导入导出这条关键链路。
 * 覆盖：通用 CSV 双向、微信/支付宝 CSV、本地备份 JSON 双向、损坏兜底、失败计数。
 */
class BackupRepositoryTest {

    private val repo = BackupRepository()

    // ── 通用 CSV 解析 ──

    @Test
    fun parseCsv_parsesFullLine() {
        val csv = """
            日期,类型,分类,金额,账户,标签,备注
            2026-01-15,支出,餐饮,12.50,微信,午餐|外卖,买菜
        """.trimIndent()

        val (txs, failed) = repo.parseCsv(csv)
        assertEquals(0, failed)
        assertEquals(1, txs.size)
        val tx = txs[0]
        assertEquals(LocalDate.parse("2026-01-15"), tx.date)
        assertEquals(TransactionType.EXPENSE, tx.type)
        // 分类列按**名称**回解成内部 id（resolveCategoryId：先 id 后名称）
        assertEquals("food", tx.categoryId)
        assertEquals(1250L, tx.amountCents)
        assertEquals("微信", tx.account)
        assertEquals(listOf("午餐", "外卖"), tx.tags)
        assertEquals("买菜", tx.note)
    }

    @Test
    fun parseCsv_handlesMissingOptionalColumns() {
        // 只有前 4 列：账户/标签/备注应回落为空
        val csv = "日期,类型,分类,金额\n2026-02-01,收入,工资,3000"

        val (txs, failed) = repo.parseCsv(csv)
        assertEquals(0, failed)
        assertEquals(1, txs.size)
        val tx = txs[0]
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("", tx.account)
        assertTrue(tx.tags.isEmpty())
        assertEquals("", tx.note)
        assertEquals(300000L, tx.amountCents)
    }

    @Test
    fun parseCsv_countsFailedOnMalformedAndShortLines() {
        val csv = """
            日期,类型,分类,金额,账户,标签,备注
            2026-02-01,收入,工资,3000
            不是日期,收入,工资,3000
            太短了只有三列
        """.trimIndent()

        val (txs, failed) = repo.parseCsv(csv)
        assertEquals(1, txs.size) // 仅首条合法
        assertEquals(2, failed)   // 日期非法 + 列不足
    }

    @Test
    fun parseCsv_skipsBlankLinesAndHeader() {
        val csv = "日期,类型,分类,金额\n\n2026-02-01,支出,餐饮,9.9\n   \n"

        val (txs, failed) = repo.parseCsv(csv)
        assertEquals(0, failed)
        assertEquals(1, txs.size)
    }

    // ── 通用 CSV 导出 → 回灌金额守恒 ──

    @Test
    fun toCsv_thenParse_preservesAmountAndFields() {
        val src = listOf(
            Transaction(
                id = 0L, type = TransactionType.EXPENSE, categoryId = "food",
                amountCents = 1250, note = "午饭", account = "微信",
                tags = listOf("午餐"), date = LocalDate.parse("2026-03-03"),
            ),
        )
        val (txs, failed) = repo.parseCsv(repo.toCsv(src))
        assertEquals(0, failed)
        assertEquals(1, txs.size)
        assertEquals(1250L, txs[0].amountCents)
        assertEquals("微信", txs[0].account)
        assertEquals(listOf("午餐"), txs[0].tags)
        assertEquals("午饭", txs[0].note)
        // toCsv 写的是分类**名**（如「餐饮」），parseCsv 会再解回内部 id，因此这里仍是 "food"
        assertEquals("food", txs[0].categoryId)
    }

    @Test
    fun toCsv_writesCurrencyColumnWithGivenCode() {
        val src = listOf(
            Transaction(
                id = 0L, type = TransactionType.EXPENSE, categoryId = "food",
                amountCents = 1250, account = "微信", date = LocalDate.parse("2026-03-03"),
            ),
        )
        val lines = repo.toCsv(src, currencyCode = "usd").removePrefix("\uFEFF").lines()
        // 金额列只有裸数字，币种必须单独成列，否则导出后无从判断这些数字是什么钱
        assertTrue(lines[0].endsWith(",币种"))
        // 代码按 ISO 大写写回，非法代码回落 CNY（见 Currency.of）
        assertTrue(lines[1].endsWith(",USD"))
        assertTrue(repo.toCsv(src, currencyCode = "XYZ").lines()[1].endsWith(",CNY"))
    }

    // ── 导出转义：Excel 可读 + 能原样导回 ──

    @Test
    fun toCsv_writesBomAndEscapesDelimiters() {
        val src = listOf(
            Transaction(
                id = 0L, type = TransactionType.EXPENSE, categoryId = "food",
                amountCents = 1234, note = "午饭,带\"引号\"", account = "微信",
                tags = listOf("a,b"), date = LocalDate.parse("2026-05-05"),
            ),
        )

        val csv = repo.toCsv(src)
        // BOM 是 Excel 正确识别 UTF-8 的前提
        assertTrue(csv.startsWith("\uFEFF"))
        // 含逗号的字段必须整体加引号，否则列会被冲散
        assertTrue(csv.contains("\"午饭,带\"\"引号\"\"\""))

        val (txs, failed) = repo.parseCsv(csv)
        assertEquals(0, failed)
        assertEquals(1234L, txs[0].amountCents)
        assertEquals("午饭,带\"引号\"", txs[0].note)
        assertEquals(listOf("a,b"), txs[0].tags)
    }

    // ── 本地备份 JSON 双向 ──

    @Test
    fun parseBackupJson_roundTrip() {
        val src = listOf(
            Transaction(
                id = 0L, type = TransactionType.TRANSFER, categoryId = "transfer",
                amountCents = 50000, note = "还款", account = "招商银行",
                tags = listOf("a", "b"), date = LocalDate.parse("2026-04-04"),
                ledgerId = "默认账本",
            ),
        )
        val (txs, failed) = repo.parseBackupJson(repo.toBackupJson(src))
        assertEquals(0, failed)
        assertEquals(1, txs.size)
        val tx = txs[0]
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals("transfer", tx.categoryId)
        assertEquals(50000L, tx.amountCents)
        assertEquals("还款", tx.note)
        assertEquals("招商银行", tx.account)
        assertEquals(listOf("a", "b"), tx.tags)
        assertEquals("默认账本", tx.ledgerId)
        assertEquals(LocalDate.parse("2026-04-04"), tx.date)
    }

    @Test
    fun parseBackupJson_keepsCounterpartyProfileAndReimburseState() {
        // 借入借出的「对方资料」与核销状态必须能随备份恢复，否则换机后欠谁 / 还没还全丢
        val src = listOf(
            Transaction(
                id = 0L, type = TransactionType.INCOME, categoryId = "borrow",
                amountCents = 100_00, note = "周转", account = "现金",
                date = LocalDate.parse("2026-10-01"),
                counterparty = "张三",
                counterpartyContact = "13800000000",
                counterpartyOrg = "某某公司",
                reimburseState = com.iji.app.data.model.ReimburseState.PENDING_CONFIRM,
            ),
        )
        val (txs, failed) = repo.parseBackupJson(repo.toBackupJson(src))
        assertEquals(0, failed)
        assertEquals(1, txs.size)
        assertEquals("张三", txs[0].counterparty)
        assertEquals("13800000000", txs[0].counterpartyContact)
        assertEquals("某某公司", txs[0].counterpartyOrg)
        assertEquals(
            com.iji.app.data.model.ReimburseState.PENDING_CONFIRM,
            txs[0].reimburseState,
        )
    }

    @Test
    fun parseBackupJson_emptyInput() {
        val (txs, failed) = repo.parseBackupJson("")
        assertTrue(txs.isEmpty())
        assertEquals(1, failed) // 整体 JSON 损坏：标记一次失败
    }

    @Test
    fun parseBackupJson_corruptReturnsPartialAndCountsFailure() {
        // 合法数组但元素缺字段：已解析保留，坏元素计数
        val (txs, failed) = repo.parseBackupJson(
            """[{"date":"2026-04-04","type":"EXPENSE","categoryId":"food","amountCents":100,"tags":[]}]""",
        )
        assertEquals(0, failed)
        assertEquals(1, txs.size)
        assertEquals(100L, txs[0].amountCents)

        // 完全非法 JSON：整体失败，已解析为空
        val bad = repo.parseBackupJson("not a json at all")
        assertTrue(bad.transactions.isEmpty())
        assertEquals(1, bad.failedCount)
    }

    // ── 微信账单 CSV ──

    @Test
    fun parseWeChatCsv_detectsHeaderAndParses() {
        val csv = """
            微信支付账单明细
            交易时间,交易类型,交易对方,商品,收/支,金额(元),当前状态,支付方式
            "2026-01-15 10:30:00","转账","商户A","一杯咖啡","支出","¥12.50","支付成功","零钱"
        """.trimIndent()

        val (txs, failed) = repo.parseWeChatCsv(csv)
        assertEquals(0, failed)
        assertEquals(1, txs.size)
        val tx = txs[0]
        assertEquals(LocalDate.parse("2026-01-15"), tx.date)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(1250L, tx.amountCents)
        assertEquals("一杯咖啡", tx.note)
        assertEquals("零钱", tx.account)
    }

    @Test
    fun parseWeChatCsv_noHeaderReturnsAllFailed() {
        val csv = "2026-01-15 10:30:00,转账,商户A,一杯咖啡,支出,¥12.50"
        val (_, failed) = repo.parseWeChatCsv(csv)
        assertEquals(1, failed) // 未找到表头：整段视为失败
    }

    // ── 支付宝账单 CSV ──

    @Test
    fun parseAlipayCsv_detectsHeaderAndParses() {
        val csv = """
            支付宝交易记录明细
            交易号,商家订单号,付款时间,交易创建时间,最近修改时间,交易来源地,类型,交易对方,商品名称,金额,收/支,交易状态
            2026010123456789,abcd1234,2026-01-15 10:30:00,2026-01-15 10:31:00,2026-01-15 10:32:00,支付宝,即时到账,商户B,商品名,12.50,支出,已完成
        """.trimIndent()

        val (txs, failed) = repo.parseAlipayCsv(csv)
        assertEquals(0, failed)
        assertEquals(1, txs.size)
        val tx = txs[0]
        assertEquals(LocalDate.parse("2026-01-15"), tx.date)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(1250L, tx.amountCents)
        assertEquals("商品名", tx.note)
        assertEquals("支付宝", tx.account)
    }

    @Test
    fun parseAlipayCsv_noHeaderReturnsAllFailed() {
        val csv = "2026010123456789,abcd1234,2026-01-15 10:30:00,2026-01-15 10:31:00,2026-01-15 10:32:00,支付宝,即时到账,商户B,商品名,12.50,支出,已完成"
        val (_, failed) = repo.parseAlipayCsv(csv)
        assertEquals(1, failed)
    }
}
