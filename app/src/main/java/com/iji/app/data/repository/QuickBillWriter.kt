package com.iji.app.data.repository

import com.iji.app.ai.BillDraft
import com.iji.app.data.db.DbProvider
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import java.time.LocalDate

/**
 * 悬浮窗里点「记 N 笔」之后的落库通道。
 *
 * 与界面内记账的区别只是**没有 ViewModel**：识屏发生时应用在后台（甚至没有界面），
 * 所以直接从数据库容器取仓库。写入本身仍走 [writeTransaction]，
 * 余额联动与预算提醒和界面记账完全一致。
 */
object QuickBillWriter {

    /**
     * @param drafts 悬浮窗里勾选待记的草稿
     * @param ledgerId 写进哪本账（取当前账本，见 [LedgerSelection]）
     * @return 实际写入成功的笔数；单笔失败不影响其他笔
     */
    suspend fun addDrafts(drafts: List<BillDraft>, ledgerId: String): Int {
        if (drafts.isEmpty()) return 0
        val db = DbProvider.database
        val transactions = TransactionRepository(db.transactionDao())
        val accounts = AccountRepository(db.accountDao())

        var written = 0
        drafts.forEach { draft ->
            if (draft.amountCents == 0L) return@forEach
            runCatching {
                writeTransaction(
                    transactions = transactions,
                    accounts = accounts,
                    tx = Transaction(
                        id = 0,
                        type = draft.type,
                        categoryId = draft.categoryId,
                        amountCents = draft.amountCents,
                        note = draft.note.trim(),
                        account = draft.account,
                        date = draft.date,
                        ledgerId = ledgerId,
                    ),
                )
            }.onSuccess { written++ }
        }
        return written
    }

    /**
     * 写单笔「详细版」悬浮卡片确认过的账单。
     *
     * 与 [addDrafts] 的区别：卡片里能改的东西比 [BillDraft] 多——
     * 标签、不计入预算、是否关联资产账户、转账转入账户。这些都属于「用户当场做的决定」，
     * 不能塞回识别产物里，所以单独走一个入口。
     *
     * @param account 传空串表示「不计入资产」（不联动账户余额）
     * @return 是否真的写进去了（金额为 0 或异常时为 false）
     */
    suspend fun addDetailed(
        ledgerId: String,
        type: TransactionType,
        categoryId: String,
        amountCents: Long,
        note: String,
        account: String,
        transferToAccount: String,
        tags: List<String>,
        date: LocalDate,
        excludeFromBudget: Boolean,
        /** 当天的第几分钟；-1 = 只记到天（默认） */
        minuteOfDay: Int = -1,
    ): Boolean {
        if (amountCents == 0L) return false
        val db = DbProvider.database
        val transactions = TransactionRepository(db.transactionDao())
        val accounts = AccountRepository(db.accountDao())
        return runCatching {
            writeTransaction(
                transactions = transactions,
                accounts = accounts,
                tx = Transaction(
                    id = 0,
                    type = type,
                    categoryId = categoryId,
                    amountCents = amountCents,
                    note = note.trim(),
                    account = account,
                    tags = tags,
                    date = date,
                    ledgerId = ledgerId,
                    excludeFromBudget = excludeFromBudget,
                    transferToAccount = transferToAccount,
                    minuteOfDay = minuteOfDay,
                ),
            )
            true
        }.getOrDefault(false)
    }
}
