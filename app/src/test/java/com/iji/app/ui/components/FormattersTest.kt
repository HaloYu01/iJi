package com.iji.app.ui.components

import com.iji.app.data.model.Currency
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Formatters 纯逻辑单测（P1-4 基线）。
 *
 * 目的：锁定金额格式化 / 解析这条会被后续重构（Coil 迁移、账本去硬编码、
 * SettingsRepository 抽取）触达的关键链路，避免无意回归。
 */
class FormattersTest {

    private fun tx(
        type: TransactionType,
        amountCents: Long,
        categoryId: String = "餐饮",
    ): Transaction = Transaction(
        id = 0L,
        type = type,
        categoryId = categoryId,
        amountCents = amountCents,
    )

    // ── formatMoney ──

    @Test
    fun formatMoney_positiveWithCents() {
        assertEquals("¥12.50", formatMoney(1250))
    }

    @Test
    fun formatMoney_zero() {
        assertEquals("¥0.00", formatMoney(0))
    }

    @Test
    fun formatMoney_negativePlacesSignBeforeCurrency() {
        // 约定：负号放在货币符号前（-¥1,234.56 而非 ¥-1,234.56）
        assertEquals("-¥1,234.56", formatMoney(-123456))
    }

    @Test
    fun formatMoney_thousandsSeparator() {
        assertEquals("¥1,234,567.89", formatMoney(123456789))
    }

    @Test
    fun formatMoney_roundsViaIntegerCents() {
        // 金额以「分」整数存储，不存在浮点误差
        assertEquals("¥9.99", formatMoney(999))
        assertEquals("¥0.01", formatMoney(1))
    }

    // ── 币种（账本级展示符号，不做换算）──

    @Test
    fun formatMoney_usesGivenCurrencySymbol() {
        assertEquals("\$12.50", formatMoney(1250, Currency.USD))
        assertEquals("-€1,234.56", formatMoney(-123456, Currency.EUR))
        // 日元刻意不用「¥」：与人民币同符号会看不出记的是哪国钱
        assertEquals("JP¥12.50", formatMoney(1250, Currency.JPY))
    }

    @Test
    fun formatMoney_sameNumberAcrossCurrenciesNoConversion() {
        // 币种只换符号、绝不换算：同一分值在两种币种下数字必须一致
        assertEquals("¥100.00", formatMoney(10000, Currency.CNY))
        assertEquals("\$100.00", formatMoney(10000, Currency.USD))
    }

    @Test
    fun currencyOf_codeLookupIsCaseInsensitiveAndFallsBackToCny() {
        assertEquals(Currency.USD, Currency.of("usd"))
        assertEquals(Currency.USD, Currency.of(" USD "))
        // 老数据无币种字段 / 非法代码一律回落人民币
        assertEquals(Currency.CNY, Currency.of(null))
        assertEquals(Currency.CNY, Currency.of(""))
        assertEquals(Currency.CNY, Currency.of("XYZ"))
    }

    // ── parseCents ──

    @Test
    fun parseCents_plainYuan() {
        assertEquals(1200, parseCents("12"))
    }

    @Test
    fun parseCents_yuanAndCents() {
        assertEquals(1250, parseCents("12.50"))
    }

    @Test
    fun parseCents_stripsThousandsSeparator() {
        assertEquals(123450, parseCents("1,234.5"))
    }

    @Test
    fun parseCents_trimsWhitespace() {
        assertEquals(1250, parseCents("  12.50  "))
    }

    @Test
    fun parseCents_emptyReturnsZero() {
        assertEquals(0, parseCents(""))
    }

    @Test
    fun parseCents_illegalReturnsZero() {
        // 整数部分就非法 → 0
        assertEquals(0, parseCents("abc"))
        // 整数部分合法、小数部分非法 → **保留整数部分**（小数按 0 分）
        // 这是刻意宽松：金额输入框只可能送进数字与小数点，"12." 这种半成品必须仍算 12 元
        assertEquals(1200, parseCents("12.abc"))
        assertEquals(1200, parseCents("12."))
    }

    @Test
    fun parseCents_truncatesExcessDecimalDigits() {
        // 超过两位小数只取前两位：12.999 -> 1299 分
        assertEquals(1299, parseCents("12.999"))
    }

    @Test
    fun parseCents_negativeAmount() {
        // 负债账户（信用卡）初始余额为负
        assertEquals(-1200, parseCents("-12"))
        assertEquals(-1250, parseCents("-12.50"))
        // 负的小数：先算绝对值再取负，否则 -0.50 会被算成 +50 分
        assertEquals(-50, parseCents("-0.50"))
        // 只有负号 → 0
        assertEquals(0, parseCents("-"))
    }

    // ── sanitizeAmountInput ──

    @Test
    fun sanitizeAmountInput_keepsDigitsAndSingleDot() {
        assertEquals("12.5", sanitizeAmountInput("12.5"))
        // 多个小数点只留第一个，后面的数字并入小数部分
        assertEquals("12.56", sanitizeAmountInput("12.5.6"))
        // 非法字符直接丢掉，不清空整段
        assertEquals("125", sanitizeAmountInput("12a5"))
    }

    @Test
    fun sanitizeAmountInput_capsDecimalAtTwoDigits() {
        // 金额以「分」为单位，第三位小数存不进去：边输边截断，而不是提交后静默丢弃
        assertEquals("12.99", sanitizeAmountInput("12.999"))
        assertEquals("0.5", sanitizeAmountInput("0.5"))
    }

    @Test
    fun sanitizeAmountInput_allowsLeadingMinus() {
        assertEquals("-12.50", sanitizeAmountInput("-12.50"))
        // 负号只在首位有效
        assertEquals("125", sanitizeAmountInput("12-5"))
    }

    // ── formatSignedMoney ──

    @Test
    fun formatSignedMoney_expenseHasMinus() {
        assertEquals("-¥12.50", formatSignedMoney(tx(TransactionType.EXPENSE, 1250)))
    }

    @Test
    fun formatSignedMoney_incomeHasPlus() {
        assertEquals("+¥12.50", formatSignedMoney(tx(TransactionType.INCOME, 1250)))
    }

    @Test
    fun formatSignedMoney_transferNoSign() {
        assertEquals("¥12.50", formatSignedMoney(tx(TransactionType.TRANSFER, 1250)))
    }
}
