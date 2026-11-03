package com.iji.app.ui.assets

import com.iji.app.data.model.ReimburseState
import com.iji.app.data.model.Transaction

/**
 * 借贷订单的状态机（纯逻辑，便于单测）。
 *
 * ## 为什么要单独一层
 *
 * 借贷状态最终落在通用的 `Transaction.reimburseState` 上，但那个枚举是给「应收 / 垫付」用的，
 * 语义按**钱的方向**而不是按**我欠 / 人欠**区分。借贷页面需要的是「待归还 / 待收回 → 待确认 → 已归还」，
 * 而且同一状态在借入 / 借出两个方向下文案不同。把「状态收窄 + 可执行操作 + 目标状态」抽在这里，
 * 列表页与详情页就不会各自 if/else 出一套对不上的规则。
 *
 * ## 状态流转
 *
 * ```
 *            归还 / 收到还款 (REPAY)
 *  PENDING ─────────────────────────► PENDING_CONFIRM
 *     │                                     │
 *     │ 直接确认结清 (CONFIRM)               │ 确认结清 (CONFIRM)
 *     └──────────────┬──────────────────────┘
 *                    ▼
 *                 SETTLED
 *                    │ 撤销 (REVOKE)
 *                    └────────────► PENDING
 * ```
 *
 * 撤销（REVOKE）只向 [PENDING] 回退，不会跳过中间态：
 * 「待确认」点撤销 = 我刚才点错了，回到还没还；已结清点撤销 = 恢复为待归还 / 待收回。
 */
enum class BorrowOrderStatus {
    /** 还没结清：借入 = 待归还，借出 = 待收回 */
    PENDING,

    /** 一方已还 / 已收，等另一方确认 */
    PENDING_CONFIRM,

    /** 已结清：借入 = 已归还，借出 = 已收回 */
    SETTLED,
}

/** 借贷订单上可以执行的操作；目标状态见 [targetState]。 */
enum class BorrowOrderAction {
    /** 借入：我已归还；借出：对方已还款。→ [BorrowOrderStatus.PENDING_CONFIRM] */
    REPAY,

    /** 确认结清：借入 = 确认已还清，借出 = 确认已收款。→ [BorrowOrderStatus.SETTLED] */
    CONFIRM,

    /** 撤销：待确认 / 已结清 都回到待归还 / 待收回。→ [BorrowOrderStatus.PENDING] */
    REVOKE,
}

/**
 * 借贷语境下的可见状态。
 *
 * [ReimburseState.NONE] 归到 [BorrowOrderStatus.PENDING]：借贷是「记一笔时选了借入 / 借出分类」
 * 产生的，用户不一定会再去「更多」里补填状态；显示成「无状态」等于把待办藏起来。
 */
val Transaction.borrowOrderStatus: BorrowOrderStatus
    get() = when (reimburseState) {
        ReimburseState.SETTLED -> BorrowOrderStatus.SETTLED
        ReimburseState.PENDING_CONFIRM -> BorrowOrderStatus.PENDING_CONFIRM
        ReimburseState.PENDING, ReimburseState.NONE -> BorrowOrderStatus.PENDING
    }

/** 当前状态下可执行的操作，顺序即界面展示顺序（主动作在前）。 */
val Transaction.borrowOrderActions: List<BorrowOrderAction>
    get() = when (borrowOrderStatus) {
        // 还没还：可以直接确认结清（双方当面结清），也可以先标「已归还」等对方确认
        BorrowOrderStatus.PENDING -> listOf(BorrowOrderAction.REPAY, BorrowOrderAction.CONFIRM)
        BorrowOrderStatus.PENDING_CONFIRM -> listOf(BorrowOrderAction.CONFIRM, BorrowOrderAction.REVOKE)
        BorrowOrderStatus.SETTLED -> listOf(BorrowOrderAction.REVOKE)
    }

/** 操作执行后要写入的 [ReimburseState]。 */
val BorrowOrderAction.targetState: ReimburseState
    get() = when (this) {
        BorrowOrderAction.REPAY -> ReimburseState.PENDING_CONFIRM
        BorrowOrderAction.CONFIRM -> ReimburseState.SETTLED
        BorrowOrderAction.REVOKE -> ReimburseState.PENDING
    }

/** 状态文案：借入说「还」，借出说「收」。 */
fun BorrowOrderStatus.label(direction: BorrowDirection): String = when (this) {
    BorrowOrderStatus.PENDING ->
        if (direction == BorrowDirection.BORROW) "待归还" else "待收回"

    BorrowOrderStatus.PENDING_CONFIRM -> "待确认"

    BorrowOrderStatus.SETTLED ->
        if (direction == BorrowDirection.BORROW) "已归还" else "已收回"
}

/** 操作按钮文案：主语随方向变（我欠 / 人欠），避免用户点错方向。 */
fun BorrowOrderAction.label(direction: BorrowDirection): String {
    val borrow = direction == BorrowDirection.BORROW
    return when (this) {
        BorrowOrderAction.REPAY -> if (borrow) "我已归还" else "对方已还款"
        BorrowOrderAction.CONFIRM -> if (borrow) "确认已还清" else "确认已收款"
        BorrowOrderAction.REVOKE -> "撤销"
    }
}

/** 操作完成后的提示语（走 snackbar）。 */
fun BorrowOrderAction.doneMessage(direction: BorrowDirection): String {
    val borrow = direction == BorrowDirection.BORROW
    return when (this) {
        BorrowOrderAction.REPAY -> if (borrow) "已标记为待确认" else "已标记对方已还，待确认"
        BorrowOrderAction.CONFIRM -> if (borrow) "已标记为已归还" else "已标记为已收回"
        BorrowOrderAction.REVOKE -> if (borrow) "已撤销，恢复为待归还" else "已撤销，恢复为待收回"
    }
}
