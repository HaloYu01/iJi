package com.iji.app.ui.overlay

import com.iji.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class OverlayFormatTest {

    @Test
    fun expenseUsesMinusSign() {
        assertEquals("-10.00", OverlayFormat.amountLabel(TransactionType.EXPENSE, 1000))
    }

    @Test
    fun incomeUsesPlusSign() {
        assertEquals("+10.00", OverlayFormat.amountLabel(TransactionType.INCOME, 1000))
    }

    @Test
    fun transferUsesEqualsSign() {
        // 设计稿转账示例就是 "=10.00"（既不是花出去也不是赚到）
        assertEquals("=10.00", OverlayFormat.amountLabel(TransactionType.TRANSFER, 1000))
    }

    @Test
    fun amountKeepsTwoDecimals() {
        assertEquals("-12.30", OverlayFormat.amountLabel(TransactionType.EXPENSE, 1230))
        assertEquals("-0.05", OverlayFormat.amountLabel(TransactionType.EXPENSE, 5))
    }

    @Test
    fun sanitizeKeepsSingleDotAndDigits() {
        assertEquals("12.34", OverlayFormat.sanitizeAmount("¥12.3.4abc"))
        assertEquals("12.34", OverlayFormat.sanitizeAmount("12.34"))
        assertEquals("", OverlayFormat.sanitizeAmount("abc"))
    }

    @Test
    fun timeLabelPadsToTwoDigits() {
        assertEquals("00:00", OverlayFormat.timeLabel(0))
        assertEquals("09:05", OverlayFormat.timeLabel(9 * 60 + 5))
        assertEquals("23:59", OverlayFormat.timeLabel(23 * 60 + 59))
    }

    @Test
    fun timeLabelIsEmptyWhenUnset() {
        // -1 = 只记到天：卡片里只显示日期，不显示 00:00
        assertEquals("", OverlayFormat.timeLabel(-1))
    }

    @Test
    fun dateTimeLabelAppendsTimeOnlyWhenSet() {
        val date = LocalDate.of(2026, 11, 3)
        assertEquals("2026-11-03", OverlayFormat.dateTimeLabel(date, -1))
        assertEquals("2026-11-03 13:03", OverlayFormat.dateTimeLabel(date, 13 * 60 + 3))
    }

    @Test
    fun centsToPlainDropsTrailingZero() {
        assertEquals("", OverlayFormat.centsToPlain(0))
        assertEquals("10", OverlayFormat.centsToPlain(1000))
        assertEquals("10.5", OverlayFormat.centsToPlain(1050))
        assertEquals("0.05", OverlayFormat.centsToPlain(5))
    }
}
