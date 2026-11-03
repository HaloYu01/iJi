package com.iji.app.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

/**
 * 统计区间计算：周/月/年的区间与「上一期」偏移。
 *
 * 这是统计页最容易出错、又最难靠肉眼发现的部分（差一天、跨年、跨月都会让环比算错），
 * 所以单独锁住；不依赖 Android / Room。
 */
class StatsPeriodTest {

    private val october = YearMonth.of(2026, 10)
    private val year2026 = Year.of(2026)
    /** 2026-10-21 是周三 */
    private val wednesday = LocalDate.of(2026, 10, 21)

    @Test
    fun `week range starts on Monday`() {
        val range = periodRange(StatsDimension.WEEK, wednesday, october, year2026)
        assertEquals(LocalDate.of(2026, 10, 19), range.start)
        assertEquals(LocalDate.of(2026, 10, 25), range.end)
        assertEquals(DayOfWeek.MONDAY, range.start.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, range.end.dayOfWeek)
    }

    @Test
    fun `week range on Sunday belongs to the same week`() {
        val sunday = LocalDate.of(2026, 10, 25)
        val range = periodRange(StatsDimension.WEEK, sunday, october, year2026)
        assertEquals(LocalDate.of(2026, 10, 19), range.start)
        assertEquals(sunday, range.end)
    }

    @Test
    fun `week range crossing year boundary`() {
        // 2027-01-01 是周五，所属周从 2026-12-28 开始
        val range = periodRange(
            StatsDimension.WEEK,
            LocalDate.of(2027, 1, 1),
            YearMonth.of(2027, 1),
            Year.of(2027),
        )
        assertEquals(LocalDate.of(2026, 12, 28), range.start)
        assertEquals(LocalDate.of(2027, 1, 3), range.end)
    }

    @Test
    fun `month range covers whole month`() {
        val range = periodRange(StatsDimension.MONTH, wednesday, october, year2026)
        assertEquals(LocalDate.of(2026, 10, 1), range.start)
        assertEquals(LocalDate.of(2026, 10, 31), range.end)
    }

    @Test
    fun `month range handles february leap year`() {
        val range = periodRange(
            StatsDimension.MONTH,
            LocalDate.of(2028, 2, 10),
            YearMonth.of(2028, 2),
            Year.of(2028),
        )
        assertEquals(LocalDate.of(2028, 2, 29), range.end)
    }

    @Test
    fun `year range covers whole year`() {
        val range = periodRange(StatsDimension.YEAR, wednesday, october, year2026)
        assertEquals(LocalDate.of(2026, 1, 1), range.start)
        assertEquals(LocalDate.of(2026, 12, 31), range.end)
    }

    @Test
    fun `previous range of week is last week`() {
        val current = periodRange(StatsDimension.WEEK, wednesday, october, year2026)
        val previous = previousRange(current, StatsDimension.WEEK)
        assertEquals(LocalDate.of(2026, 10, 12), previous.start)
        assertEquals(LocalDate.of(2026, 10, 18), previous.end)
    }

    @Test
    fun `previous range of month crosses year boundary`() {
        val january = periodRange(
            StatsDimension.MONTH,
            LocalDate.of(2026, 1, 15),
            YearMonth.of(2026, 1),
            Year.of(2026),
        )
        val previous = previousRange(january, StatsDimension.MONTH)
        assertEquals(LocalDate.of(2025, 12, 1), previous.start)
        assertEquals(LocalDate.of(2025, 12, 31), previous.end)
    }

    @Test
    fun `previous range of year is last year`() {
        val current = periodRange(StatsDimension.YEAR, wednesday, october, year2026)
        val previous = previousRange(current, StatsDimension.YEAR)
        assertEquals(LocalDate.of(2025, 1, 1), previous.start)
        assertEquals(LocalDate.of(2025, 12, 31), previous.end)
    }

    @Test
    fun `date range contains boundaries only`() {
        val range = DateRange(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25))
        assertTrue(LocalDate.of(2026, 10, 19) in range)
        assertTrue(LocalDate.of(2026, 10, 25) in range)
        assertTrue(LocalDate.of(2026, 10, 22) in range)
        assertFalse(LocalDate.of(2026, 10, 18) in range)
        assertFalse(LocalDate.of(2026, 10, 26) in range)
    }
}
