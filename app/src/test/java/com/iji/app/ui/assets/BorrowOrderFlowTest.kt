package com.iji.app.ui.assets

import com.iji.app.data.model.ReimburseState
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 借贷状态机：状态收窄、可执行操作、操作后的目标状态。
 *
 * 锁住的规则：普通账单默认的 NONE 在借贷分类下按「待归还 / 待收回」处理；
 * 待确认只能通过归还动作产生，撤销一律回到待归还 / 待收回。
 */
class BorrowOrderFlowTest {

    private fun tx(
        state: ReimburseState = ReimburseState.NONE,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: String = "lend",
    ) = Transaction(
        id = 1L,
        type = type,
        categoryId = categoryId,
        amountCents = 100_00,
        reimburseState = state,
    )

    @Test
    fun `none state is treated as pending for borrow orders`() {
        assertEquals(BorrowOrderStatus.PENDING, tx(ReimburseState.NONE).borrowOrderStatus)
        assertEquals(BorrowOrderStatus.PENDING, tx(ReimburseState.PENDING).borrowOrderStatus)
        assertEquals(
            BorrowOrderStatus.PENDING_CONFIRM,
            tx(ReimburseState.PENDING_CONFIRM).borrowOrderStatus,
        )
        assertEquals(BorrowOrderStatus.SETTLED, tx(ReimburseState.SETTLED).borrowOrderStatus)
    }

    @Test
    fun `pending offers repay and direct confirm`() {
        assertEquals(
            listOf(BorrowOrderAction.REPAY, BorrowOrderAction.CONFIRM),
            tx(ReimburseState.PENDING).borrowOrderActions,
        )
    }

    @Test
    fun `pending confirm offers confirm and revoke`() {
        assertEquals(
            listOf(BorrowOrderAction.CONFIRM, BorrowOrderAction.REVOKE),
            tx(ReimburseState.PENDING_CONFIRM).borrowOrderActions,
        )
    }

    @Test
    fun `settled only offers revoke`() {
        assertEquals(
            listOf(BorrowOrderAction.REVOKE),
            tx(ReimburseState.SETTLED).borrowOrderActions,
        )
    }

    @Test
    fun `actions map to the expected target states`() {
        assertEquals(ReimburseState.PENDING_CONFIRM, BorrowOrderAction.REPAY.targetState)
        assertEquals(ReimburseState.SETTLED, BorrowOrderAction.CONFIRM.targetState)
        assertEquals(ReimburseState.PENDING, BorrowOrderAction.REVOKE.targetState)
    }

    @Test
    fun `full lifecycle repay confirm revoke`() {
        // 记一笔借出（没填状态）→ 待收回
        var current = tx(ReimburseState.NONE)
        assertEquals("待收回", current.borrowOrderStatus.label(BorrowDirection.LEND))

        // 对方已还 → 待确认
        current = current.copy(reimburseState = BorrowOrderAction.REPAY.targetState)
        assertEquals(BorrowOrderStatus.PENDING_CONFIRM, current.borrowOrderStatus)
        assertEquals("待确认", current.borrowOrderStatus.label(BorrowDirection.LEND))

        // 确认收款 → 已收回
        current = current.copy(reimburseState = BorrowOrderAction.CONFIRM.targetState)
        assertEquals("已收回", current.borrowOrderStatus.label(BorrowDirection.LEND))

        // 撤销 → 回到待收回，且重新可执行「对方已还款」
        current = current.copy(reimburseState = BorrowOrderAction.REVOKE.targetState)
        assertEquals(BorrowOrderStatus.PENDING, current.borrowOrderStatus)
        assertTrue(BorrowOrderAction.REPAY in current.borrowOrderActions)
    }

    @Test
    fun `direct confirm from pending skips the confirm stage`() {
        val settled = tx(ReimburseState.PENDING).copy(
            reimburseState = BorrowOrderAction.CONFIRM.targetState,
        )
        assertEquals(BorrowOrderStatus.SETTLED, settled.borrowOrderStatus)
        assertEquals(listOf(BorrowOrderAction.REVOKE), settled.borrowOrderActions)
    }

    @Test
    fun `labels switch subject with direction`() {
        val borrow = tx(ReimburseState.PENDING, TransactionType.INCOME, "borrow")
        assertEquals("待归还", borrow.borrowOrderStatus.label(BorrowDirection.BORROW))
        assertEquals("我已归还", BorrowOrderAction.REPAY.label(BorrowDirection.BORROW))
        assertEquals("对方已还款", BorrowOrderAction.REPAY.label(BorrowDirection.LEND))

        val settledBorrow = borrow.copy(reimburseState = ReimburseState.SETTLED)
        assertEquals("已归还", settledBorrow.borrowOrderStatus.label(BorrowDirection.BORROW))
    }

    @Test
    fun `open state covers pending and pending confirm only`() {
        assertTrue(ReimburseState.PENDING.isOpen)
        assertTrue(ReimburseState.PENDING_CONFIRM.isOpen)
        assertFalse(ReimburseState.SETTLED.isOpen)
        assertFalse(ReimburseState.NONE.isOpen)
    }
}
