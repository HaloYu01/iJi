package com.iji.app.ui.assets

import com.iji.app.data.model.Account
import com.iji.app.data.model.AccountType
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资产页汇总口径：资产 / 负债 / 净资产。
 *
 * 锁住三点：信用类欠款只进负债不抵资产、信用类溢缴算资产、隐藏与「不计入净资产」账户的处理。
 * 纯函数，不依赖 Android / Room。
 */
class AssetSummaryTest {

    private fun account(
        id: Long,
        name: String,
        balance: Long,
        category: AccountType = AccountType.BANK,
        includeInNetWorth: Boolean = true,
        hidden: Boolean = false,
    ) = Account(
        id = id,
        name = name,
        iconKey = name,
        balanceCents = balance,
        includeInNetWorth = includeInNetWorth,
        category = category.name,
        hidden = hidden,
    )

    @Test
    fun `non credit balances go to assets`() {
        val totals = computeAssetTotals(
            listOf(
                account(1, "工资卡", 500_00),
                account(2, "微信", 120_00, AccountType.ONLINE),
            )
        )
        assertEquals(620_00L, totals.assetsCents)
        assertEquals(0L, totals.liabilitiesCents)
        assertEquals(620_00L, totals.netWorthCents)
    }

    @Test
    fun `credit account debt goes to liabilities and never offsets assets`() {
        val totals = computeAssetTotals(
            listOf(
                account(1, "工资卡", 500_00),
                account(2, "信用卡", -1_200_00, AccountType.CREDIT),
            )
        )
        assertEquals(500_00L, totals.assetsCents)
        assertEquals(1_200_00L, totals.liabilitiesCents)
        // 净资产仍是代数和：500 - 1200
        assertEquals(-700_00L, totals.netWorthCents)
        assertEquals(totals.assetsCents - totals.liabilitiesCents, totals.netWorthCents)
    }

    @Test
    fun `credit account overpayment counts as asset`() {
        val totals = computeAssetTotals(
            listOf(account(1, "信用卡", 80_00, AccountType.CREDIT))
        )
        assertEquals(80_00L, totals.assetsCents)
        assertEquals(0L, totals.liabilitiesCents)
        assertEquals(80_00L, totals.netWorthCents)
    }

    @Test
    fun `hidden account still counts toward totals`() {
        val totals = computeAssetTotals(
            listOf(
                account(1, "微信", 100_00, AccountType.ONLINE),
                account(2, "旧卡", 50_00, hidden = true),
            )
        )
        assertEquals(150_00L, totals.assetsCents)
        assertEquals(150_00L, totals.netWorthCents)
    }

    @Test
    fun `account excluded from net worth still shows in assets`() {
        val totals = computeAssetTotals(
            listOf(
                account(1, "工资卡", 300_00),
                account(2, "公司卡", 200_00, includeInNetWorth = false),
            )
        )
        assertEquals(500_00L, totals.assetsCents)
        assertEquals(300_00L, totals.netWorthCents)
    }

    @Test
    fun `empty list gives zeros`() {
        val totals = computeAssetTotals(emptyList())
        assertEquals(0L, totals.assetsCents)
        assertEquals(0L, totals.liabilitiesCents)
        assertEquals(0L, totals.netWorthCents)
    }

    @Test
    fun `category is inferred from icon key for legacy accounts`() {
        // 老账户 category 为空，靠 iconKey/名称推断成「信用」，否则欠款会被当成负资产
        val legacy = Account(
            id = 1,
            name = "招行信用卡",
            iconKey = "招商银行",
            balanceCents = -300_00,
            category = "",
        )
        val totals = computeAssetTotals(listOf(legacy))
        assertEquals(0L, totals.assetsCents)
        assertEquals(300_00L, totals.liabilitiesCents)
    }

    private fun tx(
        id: Long,
        type: TransactionType,
        categoryId: String,
        amount: Long,
        excludeFromStats: Boolean = false,
        date: LocalDate = LocalDate.of(2026, 10, 1),
    ) = Transaction(
        id = id,
        type = type,
        categoryId = categoryId,
        amountCents = amount,
        excludeFromStats = excludeFromStats,
        date = date,
    )

    @Test
    fun `borrow and lend are summed by category`() {
        val totals = computeBorrowLend(
            listOf(
                tx(1, TransactionType.INCOME, "borrow", 1_000_00),
                tx(2, TransactionType.INCOME, "borrow", 500_00),
                tx(3, TransactionType.EXPENSE, "lend", 300_00),
            )
        )
        assertEquals(1_500_00L, totals.borrowedCents)
        assertEquals(300_00L, totals.lentCents)
    }

    @Test
    fun `sub categories count toward their parent tree`() {
        val totals = computeBorrowLend(
            listOf(tx(1, TransactionType.EXPENSE, "lend-loan", 800_00))
        )
        assertEquals(800_00L, totals.lentCents)
    }

    @Test
    fun `transactions excluded from stats are ignored`() {
        val totals = computeBorrowLend(
            listOf(
                tx(1, TransactionType.EXPENSE, "lend", 200_00, excludeFromStats = true),
                tx(2, TransactionType.INCOME, "borrow", 100_00, excludeFromStats = true),
            )
        )
        assertEquals(0L, totals.lentCents)
        assertEquals(0L, totals.borrowedCents)
    }

    @Test
    fun `type must match the category direction`() {
        // 分类被用户改成反向类型：借出记成收入不该算作借出，也不该算借入
        val totals = computeBorrowLend(
            listOf(tx(1, TransactionType.INCOME, "lend", 400_00))
        )
        assertEquals(0L, totals.lentCents)
        assertEquals(0L, totals.borrowedCents)
    }

    @Test
    fun `other categories and transfers do not count`() {
        val totals = computeBorrowLend(
            listOf(
                tx(1, TransactionType.TRANSFER, "transfer", 900_00),
                tx(2, TransactionType.INCOME, "salary", 5_000_00),
                tx(3, TransactionType.EXPENSE, "food", 60_00),
            )
        )
        assertEquals(BorrowLendTotals(), totals)
        assertEquals(BorrowLendTotals(), computeBorrowLend(emptyList()))
    }

    @Test
    fun `orders are filtered by direction and sorted newest first`() {
        val orders = borrowLendOrders(
            listOf(
                tx(1, TransactionType.INCOME, "borrow", 100_00, date = LocalDate.of(2026, 9, 1)),
                tx(2, TransactionType.EXPENSE, "lend", 200_00, date = LocalDate.of(2026, 10, 5)),
                tx(3, TransactionType.INCOME, "borrow", 300_00, date = LocalDate.of(2026, 10, 8)),
                // 同日按 id 倒序（后记的在前）
                tx(4, TransactionType.INCOME, "borrow", 400_00, date = LocalDate.of(2026, 10, 8)),
            ),
            BorrowDirection.BORROW,
        )
        assertEquals(listOf(4L, 3L, 1L), orders.map { it.id })
    }

    @Test
    fun `direction matching follows category tree and type`() {
        assertTrue(BorrowDirection.BORROW.matches(tx(1, TransactionType.INCOME, "borrow", 1)))
        assertTrue(BorrowDirection.LEND.matches(tx(2, TransactionType.EXPENSE, "lend-loan", 1)))
        assertFalse(BorrowDirection.LEND.matches(tx(3, TransactionType.INCOME, "lend", 1)))
        assertFalse(BorrowDirection.BORROW.matches(tx(4, TransactionType.INCOME, "salary", 1)))
        assertFalse(
            BorrowDirection.BORROW.matches(
                tx(5, TransactionType.INCOME, "borrow", 1, excludeFromStats = true),
            ),
        )
    }

    @Test
    fun `route name falls back to borrow for unknown values`() {
        assertEquals(BorrowDirection.LEND, BorrowDirection.ofName("LEND"))
        assertEquals(BorrowDirection.BORROW, BorrowDirection.ofName("nope"))
        assertEquals(BorrowDirection.BORROW, BorrowDirection.ofName(null))
    }
}
