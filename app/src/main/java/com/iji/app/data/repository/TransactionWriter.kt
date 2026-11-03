package com.iji.app.data.repository

import com.iji.app.budget.BudgetAlertUseCase
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType

/**
 * 单笔落库：写账 → 账户余额联动 → 预算检查。
 *
 * 抽成独立函数，是因为「确认一笔账单」现在有两条入口：
 * 应用内的 [com.iji.app.MainViewModel.persistNewTransaction]（界面记账 / 批量导入）
 * 和悬浮窗确认（[QuickBillWriter]）。两条路必须算出同一个余额、触发同一套预算提醒，
 * 所以只留这一份实现。
 *
 * 预算检查的账本取 [Transaction.ledgerId]（**实际写入的账本**）而非当前账本 ——
 * 批量导入 / 识屏确认写到别的账本时，同样要触发对应账本的预算。
 */
suspend fun writeTransaction(
    transactions: TransactionRepository,
    accounts: AccountRepository,
    tx: Transaction,
): Long {
    val id = transactions.add(tx)
    // 账户余额联动（组合付款按拆分明细、转账双账户、负数冲销都在 balanceDeltas 里统一处理）
    balanceDeltas(
        account = tx.account,
        type = tx.type,
        amountCents = tx.amountCents,
        reverse = false,
        transferToAccount = tx.transferToAccount,
        splits = tx.splits,
    ).forEach { (name, delta) -> accounts.adjustBalance(name, delta) }
    // 预算实时检查：只有「支出且计入预算」会推高预算进度
    if (tx.type == TransactionType.EXPENSE && !tx.excludeFromBudget) {
        BudgetAlertUseCase.checkAfterTransaction(tx.categoryId, tx.ledgerId, tx.date)
    }
    return id
}
