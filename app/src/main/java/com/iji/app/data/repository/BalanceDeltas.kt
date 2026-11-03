package com.iji.app.data.repository

import com.iji.app.data.model.PaymentSplit
import com.iji.app.data.model.TransactionType

/**
 * 一笔账单对账户余额的影响：**账户名 → 增量（分）**，纯计算、不落库。
 *
 * 抽成顶层纯函数是为了让三条路径共用**同一套方向判定**：
 * 界面内单笔记账（[writeTransaction]）、删除时回滚、以及悬浮窗确认后的快速落库。
 * 组合付款按分账、转账双账户、负数金额（红字冲销）这些分支只在一处维护，
 * 才不会出现「按这批数据算出来的余额」和「按那批数据算出来的余额」对不上。
 *
 * **直接用带符号的 [amountCents]，不取绝对值** —— 金额为负表示红字冲销 / 退款，
 * 方向应随金额一起翻转（支出 -100 会把账户加回 100），若先 abs 再按 type 定符号，
 * 就会出现「统计里支出减少了、余额却又被扣一遍」的错账。
 *
 * 转账（[transferToAccount]）：转出账户 [account] 减、转入账户加；两者相同时视为内部调拨、
 * 余额总额不变，直接不动（否则先把余额扣成负数再加回来，中途失败就丢钱）。
 *
 * @param reverse true = 撤回这笔的影响（删除时用），整体取反
 */
fun balanceDeltas(
    account: String,
    type: TransactionType,
    amountCents: Long,
    reverse: Boolean,
    transferToAccount: String,
    splits: List<PaymentSplit>,
): List<Pair<String, Long>> {
    // reverse：把当初那笔的影响原样撤回，故整体取反
    val dir = if (reverse) -1L else 1L
    // 组合付款：按分账明细逐账户调整。此时 account 只是首选账户（用于列表展示），
    // 若仍走下面的单账户逻辑，就会把一个账户整额扣一遍、其余账户再各扣一遍，重复计账。
    if (type != TransactionType.TRANSFER && splits.isNotEmpty()) {
        return splits.mapNotNull { split ->
            val name = split.account.trim()
            if (name.isBlank() || split.amountCents == 0L) return@mapNotNull null
            val delta = when (type) {
                TransactionType.EXPENSE -> -split.amountCents * dir
                TransactionType.INCOME -> split.amountCents * dir
                // 外层已排除 TRANSFER，这里只为穷尽 when
                TransactionType.TRANSFER -> return@mapNotNull null
            }
            name to delta
        }
    }
    if (type == TransactionType.TRANSFER) {
        val from = account.trim()
        val to = transferToAccount.trim()
        if (from.isBlank() || to.isBlank() || from == to) return emptyList()
        return listOf(from to -amountCents * dir, to to amountCents * dir)
    }
    if (account.isBlank()) return emptyList()
    val delta = when (type) {
        TransactionType.EXPENSE -> -amountCents * dir
        TransactionType.INCOME -> amountCents * dir
        TransactionType.TRANSFER -> return emptyList()
    }
    return listOf(account to delta)
}
