package com.iji.app.ui.home

import androidx.lifecycle.ViewModel
import com.iji.app.DeleteBillsOutcome
import com.iji.app.MainViewModel
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import java.time.LocalDate

/**
 * 账本页（首页）专属 ViewModel：持有本屏对共享数据的读取与动作转发，
 * 共享数据/导航状态委托给 App 级 MainViewModel。本屏的筛选/多选/弹窗等临时 UI 状态仍由 Composable 的 remember 持有。
 */
class HomeViewModel(private val app: MainViewModel) : ViewModel() {
    /** 首页唯一数据源：列表、汇总金额、「是否已加载」在同一个状态里同帧发布 */
    val monthUiState = app.monthUiState
    /** 退款索引：与记一笔面板共享同一份计算，不在组合期重复遍历全量账单 */
    val refundView = app.refundView
    val selectedMonth = app.selectedMonth
    val currentLedger = app.currentLedger
    val monthlyBudgetCents = app.monthlyBudgetCents
    /** 本月总预算进度：与预算页、预算详情页共用同一份数据 */
    val monthBudgetProgress = app.monthBudgetProgress
    val ledgers = app.ledgers

    /** 账本实体快照（含币种）：切账本时用它标出「这本账用的是别的币种」 */
    val ledgerInfos = app.ledgerInfos
    /** 当前账本全量账单（跨月）：退款溯源需要按 id 反查原账单，不能只用本月汇总 */
    val transactions = app.transactions

    /** 读库失败原因（null = 正常） */
    val transactionsError = app.transactionsError

    fun retryLoadTransactions() = app.retryLoadTransactions()

    fun prevMonth() = app.prevMonth()
    fun nextMonth() = app.nextMonth()
    fun backToThisMonth() = app.backToThisMonth()
    fun switchLedger(name: String) = app.switchLedger(name)
    fun addLedger(name: String) = app.addLedger(name)
    fun setMonthlyBudget(cents: Long) = app.setMonthlyBudget(cents)

    fun addTransaction(
        type: TransactionType,
        categoryId: String,
        amountCents: Long,
        note: String,
        account: String,
        tags: List<String>,
        date: LocalDate,
        excludeFromStats: Boolean = false,
        excludeFromBudget: Boolean = false,
        reimburse: Boolean = false,
        discountCents: Long = 0L,
        attachments: List<String> = emptyList(),
    ) = app.addTransaction(
        type, categoryId, amountCents, note, account, tags, date,
        excludeFromStats, excludeFromBudget, reimburse, discountCents, attachments,
    )

    fun deleteTransaction(tx: Transaction) = app.deleteTransaction(tx)

    /** 多选批量删除（含余额回滚） */
    fun deleteBillsByIds(ids: Collection<Long>, onResult: (DeleteBillsOutcome) -> Unit) =
        app.deleteBillsByIds(ids, onResult)

    /** 多选批量改分类；[onResult] 传受影响笔数，-1 表示失败 */
    fun updateBillsCategory(ids: Collection<Long>, categoryId: String, onResult: (Int) -> Unit) =
        app.updateBillsCategory(ids, categoryId, onResult)

    /** 导出所选账单为 CSV 时复用 BackupRepository 的序列化 */
    fun toCsv(transactions: List<Transaction>) =
        app.backupRepository.toCsv(transactions, com.iji.app.data.model.CurrencyDisplay.current.code)

    fun updateTransaction(
        tx: Transaction,
        type: TransactionType,
        categoryId: String,
        amountCents: Long,
        note: String,
        account: String,
        tags: List<String>,
        date: LocalDate,
        excludeFromStats: Boolean = false,
        excludeFromBudget: Boolean = false,
        reimburse: Boolean = false,
        discountCents: Long = 0L,
        attachments: List<String> = emptyList(),
        merchant: String = "",
        counterparty: String = "",
        counterpartyContact: String = "",
        counterpartyOrg: String = "",
        reimburseState: com.iji.app.data.model.ReimburseState = com.iji.app.data.model.ReimburseState.NONE,
        reconciled: Boolean = false,
        ledgerId: String? = null,
        transferToAccount: String = "",
        splits: List<com.iji.app.data.model.PaymentSplit> = emptyList(),
        refundOfId: Long = 0L,
        /** 当天的第几分钟；null = 沿用原账单的值 */
        minuteOfDay: Int? = null,
    ) = app.updateTransaction(
        tx, type, categoryId, amountCents, note, account, tags, date,
        excludeFromStats, excludeFromBudget, reimburse, discountCents, attachments,
        merchant, counterparty, counterpartyContact, counterpartyOrg, reimburseState, reconciled,
        ledgerId = ledgerId, transferToAccount = transferToAccount, splits = splits,
        refundOfId = refundOfId, minuteOfDay = minuteOfDay,
    )
}
