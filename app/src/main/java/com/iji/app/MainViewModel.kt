package com.iji.app

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iji.app.budget.BudgetAlertUseCase
import com.iji.app.budget.BudgetCalculator
import com.iji.app.budget.BudgetComponents
import com.iji.app.budget.NotificationHelper
import com.iji.app.data.db.DbProvider
import com.iji.app.data.db.LedgerEntity
import com.iji.app.data.db.LedgerMergeResult
import com.iji.app.data.model.Account
import com.iji.app.data.model.Budget
import com.iji.app.data.model.BudgetPeriodType
import com.iji.app.data.model.BudgetProgress
import com.iji.app.data.model.Currency
import com.iji.app.data.model.CurrencyDisplay
import com.iji.app.data.model.ImportBatch
import com.iji.app.data.model.ImportPayload
import com.iji.app.data.model.ReimburseState
import com.iji.app.data.model.RecurrenceFreq
import com.iji.app.data.model.RecurringRule
import com.iji.app.data.model.Transaction
import com.iji.app.ai.BillDraft
import com.iji.app.data.model.PaymentSplit
import com.iji.app.data.model.TransactionType
import com.iji.app.data.model.Category
import com.iji.app.data.repository.AccountRepository
import com.iji.app.data.repository.BackupRepository
import com.iji.app.data.repository.BudgetRepository
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.CategoryDefaults
import com.iji.app.data.repository.CategoryIconStore
import com.iji.app.data.repository.CategoryStore
import com.iji.app.data.repository.ImportHistoryRepository
import com.iji.app.data.repository.LedgerRepository
import com.iji.app.data.repository.LedgerSelection
import com.iji.app.data.repository.RecurringRuleRepository
import com.iji.app.data.repository.TemplateRepository
import com.iji.app.data.repository.TransactionRepository
import com.iji.app.data.repository.balanceDeltas
import com.iji.app.data.repository.writeTransaction
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.util.Log
import java.time.LocalDate
import java.time.YearMonth

/** 某一天的分组汇总 */
data class DaySummary(
    val date: LocalDate,
    val transactions: List<Transaction>,
    val expenseCents: Long,
    val incomeCents: Long,
)

/** 分类占比统计（当月支出按分类聚合） */
data class CategoryStat(
    val categoryId: String,
    val totalCents: Long,
)

/** 月度收支趋势 */
data class MonthStat(
    val month: YearMonth,
    val expenseCents: Long,
    val incomeCents: Long,
)

/** 当前月汇总（单次计算，供首页/统计/日历共享，避免对交易表重复全量扫描） */
data class MonthUiState(
    val summaries: List<DaySummary>,
    val expenseCents: Long,
    val incomeCents: Long,
    val expenseByCategory: List<CategoryStat>,
    val stats: Pair<Int, Long>,
    /** 计入预算的支出（剔除 excludeFromBudget），与收支统计独立 */
    val budgetExpenseCents: Long = 0L,
    /**
     * 是否已发出第一帧真实数据。与 [summaries] 在**同一次 StateFlow 发射**里到达：
     * 页面用它区分「加载中」与「确实没有账单」，不用另设一个由上游查询置位的
     * Boolean（那会先于下游聚合结果到达，冷启动就闪一下空态）。
     */
    val loaded: Boolean = false,
)

/**
 * 退款溯源 / 退款候选的共享索引（由当前账本全量流水派生）。
 *
 * 首页与每个记一笔面板都要按 id 反查原账单、算已退金额、列最近支出候选。
 * 这些计算原先散落在各 Composable 的 `remember(allTx)` 里，启动瞬间同一份数据
 * 被遍历多遍且都在主线程；现在由 ViewModel 在 Default 线程算一次，多处只读。
 */
data class RefundView(
    val byId: Map<Long, Transaction> = emptyMap(),
    /** 原账单 id → 已退金额（分，正数，可能由多笔退款累加） */
    val refundedBySource: Map<Long, Long> = emptyMap(),
    /** 可退的正向支出，按写入顺序倒排（最近消费最可能被退） */
    val candidates: List<Transaction> = emptyList(),
)

/**
 * 「按时间条件删除 / 删除全部账单」的执行结果。
 *
 * 刻意区分 [NoMatch] 与 [Failure]：前者是**业务上的空结果**（该时间段本来就没账单，
 * 不算出错，提示语应是「没有找到…」而不是「删除失败」），后者才是真的出错。
 */
sealed interface DeleteBillsOutcome {
    /** 删除成功；[count] 为实际删除的笔数 */
    data class Success(val count: Int) : DeleteBillsOutcome

    /** 条件合法但没有任何账单命中（未做任何写入） */
    data object NoMatch : DeleteBillsOutcome

    /** 条件非法或执行/回滚失败；[message] 是可以直接展示给用户的一句话 */
    data class Failure(val message: String) : DeleteBillsOutcome
}

/**
 * 删除分类前的「影响面」统计（含其子分类）。
 *
 * 全部为 0 表示这个分类只是躺在目录里没被用过，删了不会有任何副作用。
 */
data class CategoryUsage(
    val bills: Int = 0,
    val budgets: Int = 0,
    val recurring: Int = 0,
    val templates: Int = 0,
) {
    val total: Int get() = bills + budgets + recurring + templates
}

class MainViewModel : ViewModel() {

    private companion object {
        /**
         * 早期 MainViewModel 在 NavHost 里是**每个目的地各一份实例**（HomeScreen/AssetsScreen/CalendarScreen/StatsScreen…），
         * 每份 init 都要跑 seedIfEmpty + 加载 + 周期记账，切进一个新 tab 就有 6+ 次串行 DB 往返。
         * 现在页面统一走 [com.iji.app.ui.util.sharedMainViewModel]（Activity 级一份），
         * 这里仍保留进程级幂等标记：预览/测试或将来再次拆实例时不会重复播种。
         */
        private val seededOnce = java.util.concurrent.atomic.AtomicBoolean(false)
        private val recurringProcessedOnce = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    /** 生产走 Room；预览等无数据库场景回退到内存实现 */
    private val repository: TransactionRepository = runCatching {
        TransactionRepository(DbProvider.database.transactionDao())
    }.getOrElse {
        // P0 修复：不再静默回退——记录错误，否则数据库故障会被掩盖成"空数据"且无法持久化
        Log.e("MainViewModel", "TransactionRepository 初始化失败，回退内存实现（数据不会持久化）", it)
        TransactionRepository(null)
    }

    /** 资产账户仓库（Room / 内存回退） */
    private val accountRepository: AccountRepository = runCatching {
        AccountRepository(DbProvider.database.accountDao())
    }.getOrElse {
        Log.e("MainViewModel", "AccountRepository 初始化失败，回退内存实现", it)
        AccountRepository(null)
    }

    /** 账本仓库（Room / 内存回退） */
    private val ledgerRepository: LedgerRepository = runCatching {
        LedgerRepository(DbProvider.database.ledgerDao())
    }.getOrElse {
        Log.e("MainViewModel", "LedgerRepository 初始化失败，回退内存实现", it)
        LedgerRepository(null)
    }

    /** 周期记账规则仓库（Room / 内存回退） */
    private val recurringRuleRepository: RecurringRuleRepository = runCatching {
        RecurringRuleRepository(DbProvider.database.recurringRuleDao())
    }.getOrElse {
        Log.e("MainViewModel", "RecurringRuleRepository 初始化失败，回退内存实现", it)
        RecurringRuleRepository(null)
    }

    /** 预算仓库（Room / 内存回退）：记账后实时检查预算告警 */
    private val budgetRepository: BudgetRepository = BudgetComponents.repository()

    /** 导入历史仓库（Room / 内存回退）：每次批量导入后写入一条，供导入页「导入记录」查询 */
    private val importHistoryRepository: ImportHistoryRepository = runCatching {
        ImportHistoryRepository(DbProvider.database.importHistoryDao())
    }.getOrElse {
        Log.e("MainViewModel", "ImportHistoryRepository 初始化失败，回退空实现（导入历史不可用）", it)
        ImportHistoryRepository(null)
    }

    /**
     * 周期记账规则。
     *
     * ⚠️ **必须声明在 `init` 之前**：init 里会对它 `clear()` / `addAll()`。
     * Kotlin 按声明顺序初始化，若它写在类体下方（init 之后），构造时 init 先执行、
     * 该字段尚未赋值 → `clear()` 直接 NPE；而 init 的 catch 又吞掉了异常，
     * 表现为「规则永远加载不出来、周期记账不自动生效」且毫无日志。
     */
    val recurringRules = mutableStateListOf<RecurringRule>()

    init {
        // 首次启动播种（默认账户 + 默认账本）。
        // 注意：**不播种模拟账单** —— 假流水进了真实库就和真账分不开，还会跟着备份/导出一起被当真。
        viewModelScope.launch {
            try {
                // 播种只需在进程内做一次（幂等，但每次都是 3 次串行 DB 往返）
                if (seededOnce.compareAndSet(false, true)) {
                    try {
                        accountRepository.seedIfEmpty()
                        ledgerRepository.seedIfEmpty()
                        recurringRuleRepository.seedIfEmpty()
                    } catch (t: Throwable) {
                        seededOnce.set(false) // 失败则允许后续实例重试
                        throw t
                    }
                }
                // 周期记账规则（小表，开屏读一次即可）
                // 账本列表改为**持续订阅**数据库，见下方 init —— 一次性快照会导致新建账本在其他页面不可见
                recurringRules.clear()
                recurringRules.addAll(recurringRuleRepository.rules.first())
                // 启动即执行已到期的周期记账（修复"周期记账不自动生效"）；同样只做一次
                if (recurringProcessedOnce.compareAndSet(false, true)) {
                    try {
                        processRecurringRules()
                    } catch (t: Throwable) {
                        recurringProcessedOnce.set(false)
                        throw t
                    }
                }
            } catch (t: Throwable) {
                // 播种/加载失败不应导致启动闪退（并发播种撞主键曾造成 UNIQUE 冲突崩溃）。
                // 必须留日志：这里吞异常曾让「周期规则加载不出来」完全无迹可寻。
                android.util.Log.e("MainViewModel", "启动播种 / 周期规则加载失败", t)
                if (t is kotlinx.coroutines.CancellationException) throw t
            }
        }
    }

    /**
     * 首帧是否已就绪。
     *
     * `accounts` / `transactions` 都是 `stateIn(..., emptyList())`：初值也是空列表，
     * 页面无法区分「还没读出来」和「确实没有数据」，冷启动会先闪一下「还没有账户 / 暂无数据」。
     * 这两个标记在底层 Flow 第一次 emit 时置 true 并保持，供页面区分加载态与空态。
     */
    private val _accountsReady = MutableStateFlow(false)
    private val _transactionsReady = MutableStateFlow(false)
    val accountsReady: StateFlow<Boolean> = _accountsReady
    val transactionsReady: StateFlow<Boolean> = _transactionsReady

    /** 全部资产账户（按 sortOrder 排序） */
    val accounts: StateFlow<List<Account>> = accountRepository.accounts
        .onEach { _accountsReady.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 导入历史（按导入时间倒序）——导入页「导入记录」弹窗的数据源。
     *
     * 记录的是**跑过的每一次批量导入**：来源、文件名、目标账本、成功/失败条数。
     */
    val importHistory: StateFlow<List<ImportBatch>> = importHistoryRepository.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 新增资产账户（初始余额，分） */
    fun addAccount(
        name: String,
        subName: String,
        iconKey: String,
        balanceCents: Long = 0L,
        includeInNetWorth: Boolean = true,
        category: String = "",
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            accountRepository.add(trimmed, subName.trim(), iconKey, balanceCents, includeInNetWorth, category)
        }
    }

    /**
     * 新增资产账户（**带重名校验**）：成功返回新账户 id，失败返回 null。
     *
     * 为什么不复用 [addAccount]：那个方法对空名/重名一律静默丢弃，页面无从判断
     * 「到底存没存上」。这里返回结果，页面才能给出「已存在同名账户」这类明确反馈，
     * 也才能在创建成功后拿到 id 去资产页高亮新账户。
     *
     * @param excludeId 编辑态传入被编辑账户的 id —— 允许与自己同名，但不许与别的账户同名。
     */
    suspend fun addAccountUnique(
        name: String,
        subName: String,
        iconKey: String,
        balanceCents: Long = 0L,
        includeInNetWorth: Boolean = true,
        category: String = "",
    ): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (accountRepository.existsByName(trimmed)) return null
        return accountRepository.add(
            trimmed, subName.trim(), iconKey, balanceCents, includeInNetWorth, category,
        )
    }

    /**
     * 重名检查（编辑态用）：与除 [excludeId] 之外的账户是否同名。
     */
    suspend fun isAccountNameTaken(name: String, excludeId: Long? = null): Boolean =
        accountRepository.existsByName(name, excludeId)

    /** 删除账户 */
    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            accountRepository.delete(account)
        }
    }

    /** 更新账户 */
    fun updateAccount(account: Account) {
        viewModelScope.launch {
            accountRepository.update(account)
        }
    }

    /**
     * 更新账户（**等待写库完成**）。
     *
     * 与 [updateAccount] 的区别：后者是 fire-and-forget，调用方无法知道是否已落库；
     * 页面在「保存后立刻返回」的场景需要确定写成功再离开（否则返回后列表可能还是旧值）。
     */
    suspend fun updateAccountNow(account: Account) {
        accountRepository.update(account)
    }

    /** 资产页拖动排序：按新的展示顺序重写账户 sortOrder */
    fun reorderAccounts(orderedIds: List<Long>) {
        viewModelScope.launch {
            accountRepository.reorder(orderedIds)
        }
    }

    /** 隐藏账户（从资产列表隐去，仍计入净资产） */
    fun hideAccount(account: Account) {
        viewModelScope.launch {
            accountRepository.update(account.copy(hidden = true))
        }
    }

    /** 恢复显示账户（取消隐藏，重新出现在资产列表） */
    fun showAccount(account: Account) {
        viewModelScope.launch {
            accountRepository.update(account.copy(hidden = false))
        }
    }

    /** 账户间转账：转出账户扣减，转入账户增加 */
    fun transfer(fromAccount: String, toAccount: String, amountCents: Long, note: String = "") {
        if (amountCents <= 0L || fromAccount == toAccount) return
        viewModelScope.launch {
            repository.add(
                Transaction(
                    id = 0L,
                    type = TransactionType.TRANSFER,
                    categoryId = "transfer",
                    amountCents = amountCents,
                    note = note.ifBlank { "$fromAccount -> $toAccount" },
                    account = fromAccount,
                    tags = emptyList(),
                    date = java.time.LocalDate.now(),
                )
            )
            accountRepository.adjustBalance(fromAccount, -amountCents)
            accountRepository.adjustBalance(toAccount, amountCents)
        }
    }

    /** 账本列表与当前账本（从数据库加载，见 init） */
    val ledgers = mutableStateListOf<String>()

    /**
     * 账本实体快照（含币种），与 [ledgers] 同步刷新。
     *
     * 管理页要显示 / 选择每本账的币种，只拿名字不够；用 Compose 快照列表是为了
     * 「改完币种当场就能看到新值」。
     */
    val ledgerInfos = mutableStateListOf<LedgerEntity>()

    /**
     * 当前账本。**必须**走共享状态 [LedgerSelection]：MainViewModel 虽然已是全 App 一份，
     * 但账本选择还要被通知接收器、悬浮窗等进程级组件读取，不能只存在 ViewModel 里。
     */
    val currentLedger: StateFlow<String> = LedgerSelection.current

    fun switchLedger(name: String) {
        LedgerSelection.select(name)
        syncDisplayCurrency()
    }

    /** 新建账本；[currencyCode] 是这本账的币种（只影响展示符号，不做换算） */
    fun addLedger(name: String, currencyCode: String = Currency.CNY.code) {
        val n = name.trim()
        if (n.isNotBlank() && !ledgers.contains(n)) {
            viewModelScope.launch { ledgerRepository.add(n, currencyCode) }
            ledgers.add(n)
            LedgerSelection.select(n)
            CurrencyDisplay.set(currencyCode)
        }
    }

    /**
     * 改账本币种：**只换展示符号**，账单金额一律不动（不做汇率换算）。
     *
     * 改的是当前账本时同时刷新全局展示币种，切页后所有金额立刻用新符号。
     */
    fun setLedgerCurrency(name: String, currencyCode: String) {
        viewModelScope.launch {
            ledgerRepository.setCurrency(name, currencyCode)
            if (currentLedger.value == name) CurrencyDisplay.set(currencyCode)
        }
    }

    /** 把「当前账本的币种」写进全局展示币种（账本列表变化 / 切换账本时调用） */
    private fun syncDisplayCurrency() {
        val code = ledgerInfos.firstOrNull { it.name == currentLedger.value }?.currency
        CurrencyDisplay.set(code)
    }

    // 重命名账本（级联更新 transactions / budgets / recurring_rules 的引用）
    fun renameLedger(oldName: String, newName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = ledgerRepository.rename(oldName, newName)
            if (ok) {
                val i = ledgers.indexOf(oldName)
                if (i >= 0) ledgers[i] = newName.trim()
                if (currentLedger.value == oldName) LedgerSelection.select(newName.trim())
            }
            onResult(ok)
        }
    }

    // 账本下的账单数（删除前确认用）
    fun ledgerTransactionCount(name: String, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(ledgerRepository.transactionCount(name)) }
    }

    /**
     * 合并账本：把 [from] 的账单 / 预算 / 周期规则整体并入 [to]，再删掉 [from]。
     *
     * 有账单的账本删不掉（见 [deleteLedger]），合并是它唯一的正规出路。
     * 成功后若当前账本正是被合并的那本，自动切到目标账本，避免停留在已不存在的账本上。
     */
    fun mergeLedger(from: String, to: String, onResult: (LedgerMergeResult?) -> Unit) {
        viewModelScope.launch {
            val result = ledgerRepository.merge(from, to)
            if (result != null) {
                ledgers.remove(from)
                if (currentLedger.value == from) LedgerSelection.select(to)
            }
            onResult(result)
        }
    }

    // 删除账本；仅允许删空账本，有账单时仓库会拒绝
    fun deleteLedger(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = ledgerRepository.deleteIfEmpty(name)
            if (ok) {
                ledgers.remove(name)
                if (currentLedger.value == name) {
                    LedgerSelection.select(ledgers.firstOrNull() ?: "默认账本")
                }
            }
            onResult(ok)
        }
    }

    // ==================== 分类（增删改排序） ====================

    /**
     * 删除分类前的引用统计：账单 / 预算 / 周期账单 / 模板，**都按整棵分类树算**。
     *
     * 读库失败不抛给 UI：返回全 0 会让用户以为"没人用它"，所以这里显式标注失败——
     * 调用方拿不到数据时会禁用确认按钮（见 CategoryManageScreen 的 confirmEnabled）。
     */
    suspend fun categoryUsage(context: android.content.Context, category: Category): CategoryUsage? =
        withContext(Dispatchers.IO) {
            runCatching {
                val ids = CategoryCatalog.subtreeIds(category.id)
                val db = DbProvider.database
                CategoryUsage(
                    bills = db.transactionDao().countByCategoryIds(ids),
                    budgets = db.budgetDao().getAll().count { it.categoryId in ids },
                    recurring = db.recurringRuleDao().all().count { it.categoryId in ids },
                    templates = TemplateRepository.once(context).count { it.categoryId in ids },
                )
            }.getOrNull()
        }

    /**
     * 删除分类（含子分类）并安全处置引用。
     *
     * 顺序刻意是「先把引用迁走 → 再删目录行」：中途失败时分类还在，
     * 用户的账单不会凭空变成悬空 categoryId，重试一次即可。
     *
     * 迁移目标固定为同类型的「其他」（不存在则自动补一个）；
     * 预算与目标分类冲突（同账本同周期）时丢弃被删分类那条，避免出现两个同样的预算。
     */
    suspend fun deleteCategory(context: android.content.Context, category: Category): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val ids = CategoryCatalog.subtreeIds(category.id)
                val target = ensureMigrationTarget(context, category.type, ids.toSet())
                val db = DbProvider.database

                db.transactionDao().reassignCategory(ids, target.id)

                val allBudgets = db.budgetDao().getAll()
                allBudgets.filter { it.categoryId in ids }.forEach { budget ->
                    val conflict = allBudgets.any {
                        it.id != budget.id && it.categoryId == target.id &&
                            it.ledgerId == budget.ledgerId && it.periodType == budget.periodType
                    }
                    if (conflict) {
                        db.budgetDao().delete(budget)
                    } else {
                        db.budgetDao().update(budget.copy(categoryId = target.id))
                    }
                }

                db.recurringRuleDao().reassignCategory(ids, target.id)
                TemplateRepository.reassignCategory(context, ids.toSet(), target.id)

                val removed = CategoryCatalog.remove(context, category.id)
                if (removed.isEmpty()) return@withContext false
                removed.forEach { CategoryIconStore.resetById(context, it.id) }
                true
            }.getOrDefault(false)
        }

    /**
     * 迁移落点：同类型的「其他」。
     *
     * 如果被删的**正是**「其他」，不能把账单迁到一个马上要消失的 id 上，
     * 这里改为新建一个「其他」（id 已占用时派生新 id）。
     */
    private fun ensureMigrationTarget(
        context: android.content.Context,
        type: TransactionType,
        deletingIds: Set<String>,
    ): Category {
        val wanted = CategoryCatalog.ensureFallback(context, type)
        if (wanted.id !in deletingIds) return wanted
        val base = CategoryDefaults.all.first { it.id == CategoryDefaults.fallbackId(type) }
        var id = "${base.id}-new"
        var seq = 2
        while (CategoryStore.existsById(id)) {
            id = "${base.id}-new${seq++}"
        }
        val created = base.copy(
            id = id,
            builtin = false,
            sortOrder = CategoryStore.nextSortOrder(type, null),
        )
        return if (CategoryStore.add(context, created)) created else wanted
    }


    // 账本列表实时同步：持续订阅 Room，而不是开屏取一次快照。
    // 这样在管理页新建 / 改名 / 删除账本后，主页下拉里的内容立刻跟着变。
    init {
        viewModelScope.launch {
            ledgerRepository.all.collect { infos ->
                ledgers.clear()
                ledgers.addAll(infos.map { it.name })
                ledgerInfos.clear()
                ledgerInfos.addAll(infos)
                // 当前账本若已被改名或删除，自动回退到列表第一项，避免指向不存在的账本
                if (infos.isNotEmpty() && currentLedger.value !in ledgers) {
                    LedgerSelection.select(ledgers.first())
                }
                // 币种跟着当前账本走：改名 / 换账本 / 改币种都会走到这里
                syncDisplayCurrency()
            }
        }
    }

    /** 账单读取失败原因；null = 正常。用它把「本来就没有账单」和「读不出来」区分开 */
    private val _transactionsError = MutableStateFlow<String?>(null)
    val transactionsError: StateFlow<String?> = _transactionsError

    /** 重试计数：自增即重新订阅 Room */
    private val retryTick = MutableStateFlow(0)

    /** 当前账本账单（Room Flow，自动响应增删改） */
    val transactions: StateFlow<List<Transaction>> =
        combine(currentLedger, retryTick) { ledger, _ -> ledger }
            .flatMapLatest { ledger ->
                repository.transactionsByLedger(ledger)
                    // 读库失败不能渲染成「没有账单」：标记原因后给空列表，由界面出错误条。
                    // 成功时**不自动清空**错误：两条流（全量 / 按月）共用一个状态，
                    // 另一条成功会把这条的错误擦掉，界面就变成「本月没有账单」了
                    .catch { e ->
                        _transactionsError.value = e.message ?: "账单读取失败"
                        emit(emptyList())
                    }
            }
            // 首帧标记：切账本后新账本的第一条也要等它 emit，之前都算加载中
            .onEach { _transactionsReady.value = true }
            // 账本过滤已下推到 SQL（observeByLedger + Index(ledgerId)）；
            // 这里的 flowOn 把「实体→模型」的映射挪出主线程（viewModelScope 默认 Main.immediate）
            .flowOn(Dispatchers.Default)
            // 首屏数据：ViewModel 构造即开始订阅，不再等 Compose 首帧来 collect
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 退款索引：由 [transactions] 派生，Default 线程只算一次，首页与各记一笔面板共享 */
    val refundView: StateFlow<RefundView> = transactions.map { list ->
        RefundView(
            byId = list.associateBy { it.id },
            refundedBySource = list.filter { it.refundOfId != 0L }
                .groupBy { it.refundOfId }
                .mapValues { (_, refunds) -> refunds.sumOf { kotlin.math.abs(it.amountCents) } },
            candidates = list.filter { it.type == TransactionType.EXPENSE && it.amountCents > 0L }
                .sortedByDescending { it.id },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RefundView())

    /**
     * 导出用：直接从库读当前账本全量流水。
     *
     * 不能读 `transactions.value` —— 那是 `stateIn(WhileSubscribed)`，导出页若是首次进入、
     * 还没人订阅，`.value` 就是初始空列表，会导出一个只有表头的 CSV（真机上已踩）。
     */
    suspend fun transactionsForExport(): List<Transaction> =
        repository.transactionsByLedger(currentLedger.value).first()

    /** 各标签在全部账单（跨账本）中的使用笔数：标签管理页展示「N 笔在用」，删前心里有数 */
    suspend fun tagUsageCounts(): Map<String, Int> =
        repository.allTagStrings()
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()

    /**
     * 标签管理下钻：带某个标签的全部账单（跨账本），Flow 自动响应增删改。
     *
     * 标签名即主键、账单的 `tags` 是逗号分隔串转出的 List，所以用成员判断精确匹配：
     * `LIKE '%美食%'` 会把「美食券」也算进来。口径与 [tagUsageCounts] 一致（全库），
     * 不能用 [transactions]（只含当前账本），否则列表笔数与标签页上的「N 笔在用」对不上。
     */
    fun transactionsByTag(tag: String): Flow<List<Transaction>> =
        repository.transactions.map { list -> list.filter { tag in it.tags } }

    /** 账单读取失败后重试：自增 tick 重建 Room 订阅 */
    fun retryLoadTransactions() {
        _transactionsError.value = null
        retryTick.value++
    }

    /** 当前选中月份（左右箭头切换） */
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth

    fun prevMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }
    fun nextMonth() { _selectedMonth.value = _selectedMonth.value.plusMonths(1) }
    fun backToThisMonth() { _selectedMonth.value = YearMonth.now() }

    /** 直接跳到某个月：日历横滑翻页、点相邻月日期用（等同 prev/next 的连续调用，但没有中间态） */
    fun setMonth(month: YearMonth) {
        if (month != _selectedMonth.value) _selectedMonth.value = month
    }

    /** 当前选中周 */
    private val _selectedWeek = MutableStateFlow(java.time.LocalDate.now())
    val selectedWeek: StateFlow<LocalDate> = _selectedWeek

    fun prevWeek() { _selectedWeek.value = _selectedWeek.value.minusWeeks(1) }
    fun nextWeek() { _selectedWeek.value = _selectedWeek.value.plusWeeks(1) }
    fun backToThisWeek() { _selectedWeek.value = java.time.LocalDate.now() }

    /** 当前选中年 */
    private val _selectedYear = MutableStateFlow(java.time.Year.now())
    val selectedYear: StateFlow<java.time.Year> = _selectedYear

    fun prevYear() { _selectedYear.value = _selectedYear.value.minusYears(1) }
    fun nextYear() { _selectedYear.value = _selectedYear.value.plusYears(1) }
    fun backToThisYear() { _selectedYear.value = java.time.Year.now() }

    /**
     * 当前账本 + 当前月的流水：**SQL 层按月过滤**（`dateMillis` 存 epochDay）。
     * 上一版是把整本账本取回内存再按月 filter，切月/改一笔都要全表重算。
     */
    private val monthTransactions: Flow<Pair<YearMonth, List<Transaction>>> =
        // retryTick 参与 combine：「重试」要能重建按月查询，否则错误会一直挂在那
        combine(currentLedger, selectedMonth, retryTick) { ledger, month, _ -> ledger to month }
            .flatMapLatest { (ledger, month) ->
                repository.transactionsByLedgerInMonth(ledger, month)
                    // 按月查询同样是读库，失败要落到同一条错误条上，别静默成「本月没账单」
                    .catch { e ->
                        _transactionsError.value = e.message ?: "账单读取失败"
                        emit(emptyList())
                    }
                    .map { month to it }
            }

    // 不在这里置任何「就绪」标志：查询返回 ≠ 聚合结果已发布。
    // 就绪由 monthUiState.loaded 承载，与列表数据同一次发射到达。

    /** 当前月汇总（单次计算，首页/统计/日历共享，避免对交易表重复全量扫描） */
    val monthUiState: StateFlow<MonthUiState> =
        monthTransactions.map { (month, inMonth) ->
            val expenseTxs = inMonth.filter { it.type == TransactionType.EXPENSE }
            val incomeTxs = inMonth.filter { it.type == TransactionType.INCOME }
            // 收支统计：剔除「不计入收支」
            val statsExpense = expenseTxs.filter { !it.excludeFromStats }.sumOf { it.amountCents }
            // 预算进度：剔除「不计入预算」
            val budgetExpense = expenseTxs.filter { !it.excludeFromBudget }.sumOf { it.amountCents }
            val summaries = inMonth
                .groupBy { it.date }
                .toSortedMap(compareByDescending { it })
                .map { (date, txs) ->
                    DaySummary(
                        date = date,
                        transactions = txs.sortedByDescending { it.id },
                        expenseCents = txs.filter { it.type == TransactionType.EXPENSE && !it.excludeFromStats }.sumOf { it.amountCents },
                        incomeCents = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
                    )
                }
            val expenseByCategory = inMonth
                .filter { it.type == TransactionType.EXPENSE && !it.excludeFromStats }
                .groupBy { it.categoryId }
                .map { (id, txs) -> CategoryStat(id, txs.sumOf { it.amountCents }) }
                .sortedByDescending { it.totalCents }
            MonthUiState(
                summaries = summaries,
                expenseCents = statsExpense,
                incomeCents = incomeTxs.sumOf { it.amountCents },
                expenseByCategory = expenseByCategory,
                stats = expenseTxs.filter { !it.excludeFromStats }.count() to (if (statsExpense > 0L) statsExpense / month.lengthOfMonth() else 0L),
                budgetExpenseCents = budgetExpense,
                loaded = true,
            )
        }
            // 当月全量扫描 + 分组 + 聚合，不要在 Main 上做
            .flowOn(Dispatchers.Default)
            // 首屏数据：构造即查询并缓存结果，Compose 首帧订阅时直接拿到已就绪数据。
            // 初值 loaded=false：真实值与 loaded=true 同一次发射，加载态与空态不会混淆。
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                MonthUiState(emptyList(), 0L, 0L, emptyList(), 0 to 0L, loaded = false),
            )

    /** 当前月账单，按日期倒序分组成天（派生自 monthUiState） */
    val monthSummaries: StateFlow<List<DaySummary>> =
        monthUiState.map { it.summaries }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前月总支出（分）（派生自 monthUiState，已剔除「不计入收支」） */
    val monthExpenseCents: StateFlow<Long> =
        monthUiState.map { it.expenseCents }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 当前月计入预算的支出（分）（派生自 monthUiState，已剔除「不计入预算」） */
    val monthBudgetExpenseCents: StateFlow<Long> =
        monthUiState.map { it.budgetExpenseCents }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 当前月总收入（分）（派生自 monthUiState） */
    val monthIncomeCents: StateFlow<Long> =
        monthUiState.map { it.incomeCents }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 本月支出按分类聚合（环形图数据），按金额降序（派生自 monthUiState） */
    val monthExpenseByCategory: StateFlow<List<CategoryStat>> =
        monthUiState.map { it.expenseByCategory }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 近 6 个月收支趋势（柱状图数据），从旧到新 */
    val monthlyTrend: StateFlow<List<MonthStat>> = transactions
        .map { list ->
            val now = YearMonth.now()
            (5L downTo 0L).map { offset ->
                val month = now.minusMonths(offset)
                val inMonth = list.filter { YearMonth.from(it.date) == month }
                MonthStat(
                    month = month,
                    expenseCents = inMonth.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents },
                    incomeCents = inMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
                )
            }
        }
            // 6 个月 × 全表扫描，不要在 Main 上做
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 本月统计：(支出笔数, 日均支出分)（派生自 monthUiState，基于 selectedMonth） */
    val monthStats: StateFlow<Pair<Int, Long>> =
        monthUiState.map { it.stats }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0L)

    /** 新增一笔账单（含账户 / 标签 / 自定义日期） */
    fun addTransaction(
        type: TransactionType,
        categoryId: String,
        amountCents: Long,
        note: String,
        account: String = "",
        tags: List<String> = emptyList(),
        date: LocalDate = LocalDate.now(),
        excludeFromStats: Boolean = false,
        excludeFromBudget: Boolean = false,
        reimburse: Boolean = false,
        discountCents: Long = 0L,
        attachments: List<String> = emptyList(),
        merchant: String = "",
        counterparty: String = "",
        /** 对方联系方式 / 单位（借入借出的「对方资料」） */
        counterpartyContact: String = "",
        counterpartyOrg: String = "",
        reimburseState: ReimburseState = ReimburseState.NONE,
        reconciled: Boolean = false,
        /** 显式指定所属账本；null = 当前账本。批量导入 / 周期记账用它避免记错账本 */
        ledgerId: String? = null,
        /** 转账的转入账户（仅 TRANSFER 用；account 为转出账户） */
        transferToAccount: String = "",
        /** 组合付款拆分明细；非空时余额按它逐账户联动，account 仅作首选账户展示 */
        splits: List<PaymentSplit> = emptyList(),
        /** 退款溯源：本笔退款所关联的原消费账单 id；0 = 无关联。不参与任何金额聚合 */
        refundOfId: Long = 0L,
        /** 当天的第几分钟；-1 = 只记到天（导入 / 周期记账这类没有时分来源的入口用默认值） */
        minuteOfDay: Int = -1,
        /** 写入成功回调（带新账单 id），用于「撤销」这类需要定位的场景 */
        onSaved: ((Long) -> Unit)? = null,
    ) {
        // 允许负数金额（红字冲销 / 退款），只拦「恰好为 0」
        if (amountCents == 0L) return
        viewModelScope.launch {
            val id = persistNewTransaction(
                Transaction(
                    id = 0L,
                    type = type,
                    categoryId = categoryId,
                    amountCents = amountCents,
                    note = note.trim(),
                    account = account,
                    transferToAccount = transferToAccount.trim(),
                    tags = tags,
                    date = date,
                    ledgerId = ledgerId ?: currentLedger.value,
                    excludeFromStats = excludeFromStats,
                    excludeFromBudget = excludeFromBudget,
                    reimburse = reimburse,
                    discountCents = discountCents,
                    attachments = attachments,
                    merchant = merchant.trim(),
                    counterparty = counterparty.trim(),
                    counterpartyContact = counterpartyContact.trim(),
                    counterpartyOrg = counterpartyOrg.trim(),
                    reimburseState = reimburseState,
                    reconciled = reconciled,
                    splits = splits,
                    refundOfId = refundOfId,
                    minuteOfDay = minuteOfDay,
                )
            )
            onSaved?.invoke(id)
        }
    }

    /**
     * 单笔落库：写账 → 账户余额联动 → 预算检查。
     *
     * 抽出来是为了让[importDrafts] 能**逐笔 await**，从而回报确定的成功/失败数；
     * 放在 [addTransaction] 里时是 fire-and-forget，调用方拿不到写入结果。
     *
     * 预算检查的账本取 [Transaction.ledgerId]（**实际写入的账本**）而非当前账本 ——
     * 批量导入到别的账本时同样要触发对应账本的预算。
     */
    private suspend fun persistNewTransaction(tx: Transaction): Long =
        writeTransaction(repository, accountRepository, tx)

    /**
     * 截屏批量导入：逐笔 await 写入，回报**真实的**成功 / 失败数。
     *
     * 与 CSV 那套（`importCsv` 等，fire-and-forget + 同步计数）刻意不同 ——
     * 预览导入是用户在核对之后按下的，必须给一个可信结果，单笔失败也不该被静默吞掉。
     *
     * @param account 该批账单的统一账户（截屏来自哪个 App 就记哪个账户）；草稿自带账户时优先用草稿的
     */
    fun importDrafts(
        drafts: List<BillDraft>,
        targetLedger: String? = null,
        account: String = "",
        onResult: (ok: Int, failed: Int) -> Unit,
    ) {
        if (drafts.isEmpty()) {
            onResult(0, 0)
            return
        }
        viewModelScope.launch {
            var failed = 0
            val ledger = targetLedger ?: currentLedger.value
            val ids = mutableListOf<Long>()
            val written = mutableListOf<Transaction>()
            drafts.forEach { draft ->
                if (draft.amountCents == 0L) {
                    failed++
                    return@forEach
                }
                val tx = Transaction(
                    id = 0L,
                    type = draft.type,
                    categoryId = draft.categoryId,
                    amountCents = draft.amountCents,
                    note = draft.note.trim(),
                    // 用户在预览页选定的账户优先于草稿自带的（草稿那个是 OCR 猜的）
                    account = account.ifBlank { draft.account },
                    date = draft.date,
                    ledgerId = ledger,
                )
                runCatching { persistNewTransaction(tx) }
                    .onSuccess { id -> ids += id; written += tx.copy(id = id) }
                    .onFailure { failed++ }
            }
            importHistoryRepository.record(
                source = "截屏批量导入",
                fileName = "",
                ledgerId = ledger,
                okCount = ids.size,
                failedCount = failed,
                recordIds = ids,
                payload = ImportPayload.encode(backupRepository.toBackupJson(written)),
            )
            onResult(ids.size, failed)
        }
    }

    /** 编辑一笔账单（先回滚旧余额，再应用新余额） */
    @Suppress("LongParameterList")
    fun updateTransaction(
        old: Transaction,
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
        /** 对方联系方式 / 单位（借入借出的「对方资料」） */
        counterpartyContact: String = "",
        counterpartyOrg: String = "",
        reimburseState: ReimburseState = ReimburseState.NONE,
        reconciled: Boolean = false,
        /** 改所属账本；null = 沿用原账本 */
        ledgerId: String? = null,
        transferToAccount: String = "",
        /** 组合付款拆分明细；空列表 = 单一账户 */
        splits: List<PaymentSplit> = emptyList(),
        refundOfId: Long = 0L,
        /** 当天的第几分钟；null = 沿用原账单的值（改日期不该顺手把时分抹成"未指定"） */
        minuteOfDay: Int? = null,
    ) {
        if (amountCents == 0L) return
        viewModelScope.launch {
            // 回滚旧账单对账户的影响（旧账单可能也是组合付款，必须按它自己的拆分回滚）
            adjustAccountBalance(
                old.account, old.type, old.amountCents,
                reverse = true, transferToAccount = old.transferToAccount,
                splits = old.splits,
            )
            // 更新账单
            repository.update(
                old.copy(
                    type = type,
                    categoryId = categoryId,
                    amountCents = amountCents,
                    note = note.trim(),
                    account = account,
                    transferToAccount = transferToAccount.trim(),
                    tags = tags,
                    date = date,
                    ledgerId = ledgerId ?: old.ledgerId,
                    excludeFromStats = excludeFromStats,
                    excludeFromBudget = excludeFromBudget,
                    reimburse = reimburse,
                    discountCents = discountCents,
                    attachments = attachments,
                    merchant = merchant.trim(),
                    counterparty = counterparty.trim(),
                    counterpartyContact = counterpartyContact.trim(),
                    counterpartyOrg = counterpartyOrg.trim(),
                    reimburseState = reimburseState,
                    reconciled = reconciled,
                    splits = splits,
                    refundOfId = refundOfId,
                    minuteOfDay = minuteOfDay ?: old.minuteOfDay,
                )
            )
            // 应用新账单对账户的影响
            adjustAccountBalance(
                account, type, amountCents,
                transferToAccount = transferToAccount, splits = splits,
            )
            // 改金额/改分类都可能越过阈值，同样实时检查一次
            // 账本取**保存后的账本**（可能被改成别的账本）
            if (type == TransactionType.EXPENSE && !excludeFromBudget) {
                BudgetAlertUseCase.checkAfterTransaction(
                    categoryId, ledgerId ?: old.ledgerId, date,
                )
            }
        }
    }

    /** 删除一笔账单（回滚余额） */
    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch {
            repository.delete(tx)
            adjustAccountBalance(
                tx.account, tx.type, tx.amountCents,
                reverse = true, transferToAccount = tx.transferToAccount,
                splits = tx.splits,
            )
        }
    }

    /**
     * 核销应收/垫付：切换某笔账单的 [ReimburseState]。
     *
     * **不动账户余额** —— 钱在当初记账时就已经真实进出过，核销只是把状态标记结清，
     * 再调一次 [adjustAccountBalance] 会导致余额重复变动。
     */
    fun setReimburseState(tx: Transaction, state: ReimburseState) {
        viewModelScope.launch { repository.update(tx.copy(reimburseState = state)) }
    }

    // ── 对账 ──

    /**
     * 标记 / 取消「已对账」。
     *
     * 与 [setReimburseState] 同理**不动余额**：对账只是「和银行流水核对过」的勾，
     * 钱早就进出过了。直接复用 `repository.update` 写整行，Room Flow 会自动把新列表推给 UI。
     */
    fun setReconciled(tx: Transaction, reconciled: Boolean) {
        viewModelScope.launch {
            if (reconciled) repository.markReconciled(tx) else repository.unmarkReconciled(tx)
        }
    }

    /** 批量对账（对账页「全部标记为已对账」） */
    fun markAllReconciled(txs: List<Transaction>) {
        viewModelScope.launch { txs.forEach { repository.markReconciled(it) } }
    }

    /**
     * 清空预算提醒记录（提醒历史页「清空」）。
     *
     * 只删历史，不动预算本身与提醒开关 —— 清完之后再越阈值仍会照常提醒。
     */
    fun clearBudgetAlerts(onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching { budgetRepository.clearAlerts() }.isSuccess
            onDone(ok)
        }
    }

    /** 调整账户余额（单笔）：按 [balanceDeltas] 的结果逐账户写入 */
    private suspend fun adjustAccountBalance(
        account: String,
        type: TransactionType,
        amountCents: Long,
        reverse: Boolean = false,
        transferToAccount: String = "",
        splits: List<PaymentSplit> = emptyList(),
    ) {
        balanceDeltas(
            account = account,
            type = type,
            amountCents = amountCents,
            reverse = reverse,
            transferToAccount = transferToAccount,
            splits = splits,
        ).forEach { (name, delta) -> accountRepository.adjustBalance(name, delta) }
    }

    // ── 按时间条件 / 全部删除账单 ──

    /** 异常 → 可直接展示给用户的一句话（不要把完整堆栈甩给用户） */
    private fun readableError(e: Throwable): String =
        e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

    /** 按条件取目标账单（当前账本）：[start] / [end] 任一为 null = 不限区间，即全部账单 */
    private suspend fun resolveTargets(start: LocalDate?, end: LocalDate?): List<Transaction> {
        val ledger = currentLedger.value
        return if (start == null || end == null) {
            repository.allByLedger(ledger)
        } else {
            repository.inRange(ledger, start, end)
        }
    }

    /**
     * 查询命中笔数：**只查询、无副作用**，供删除前的数量预览。
     *
     * 有它才能在二次确认里写清「将删除 N 笔」，而不是让用户对着一个日期范围盲确认 ——
     * 盲确认的风险是用户以为只删几笔，实际删掉几百笔。
     *
     * @param start 起始日期（含），null = 不限
     * @param end 结束日期（含），null = 不限
     */
    fun countBills(start: LocalDate?, end: LocalDate?, onResult: (Int) -> Unit) {
        if (start != null && end != null && start > end) {
            onResult(0)
            return
        }
        viewModelScope.launch {
            onResult(runCatching { resolveTargets(start, end).size }.getOrDefault(0))
        }
    }

    /**
     * 删除账单并回滚这些账单对账户余额的影响；[start] / [end] 任一为 null = 当前账本全部账单。
     *
     * 只作用于当前账本：账本是数据隔离边界，跨账本删除会让别的账本凭空少账单。
     *
     * **执行顺序**：先删账单、再回滚余额。
     * - 第 ① 步失败 → 此时一笔余额都没动过，直接报失败即可，无需回滚；
     * - 第 ② 步中途失败 → 账单已删，必须把已生效的余额变动反向撤销、并把账单插回去，
     *   否则会留下「账单没了、余额也没退回」的丢钱状态。
     *
     * 余额增量先按账户**聚合**再写入：几千笔账单逐笔 UPDATE 会很慢，
     * 而同一账户的多笔增量只是加法，合并成一次 UPDATE 结果完全等价。
     */
    fun deleteBills(start: LocalDate?, end: LocalDate?, onResult: (DeleteBillsOutcome) -> Unit) {
        if (start != null && end != null && start > end) {
            onResult(DeleteBillsOutcome.Failure("开始日期不能晚于结束日期"))
            return
        }
        viewModelScope.launch {
            val targets = runCatching { resolveTargets(start, end) }
                .getOrElse {
                    onResult(DeleteBillsOutcome.Failure("查询账单失败：${readableError(it)}"))
                    return@launch
                }
            deleteAndRollback(targets, onResult)
        }
    }

    /**
     * 多选删除：命中集合按 id 指定（不连续），与 [deleteBills] 共用同一套
     * 「先删账单、再回滚余额」流程；命中集合直接从当前账本的 [transactions] 里取，
     * 不额外查库（多选来源本来就是这份列表）。
     */
    fun deleteBillsByIds(ids: Collection<Long>, onResult: (DeleteBillsOutcome) -> Unit) {
        // 空集合由 deleteAndRollback 兜底返回 NoMatch，这里无需重复判空
        viewModelScope.launch {
            deleteAndRollback(transactions.value.filter { it.id in ids }, onResult)
        }
    }

    /**
     * 批量改分类：只改 categoryId，不动金额与账户，**无需调整余额**。
     * 一次事务写入；返回受影响的笔数（-1 = 失败）。
     */
    fun updateBillsCategory(ids: Collection<Long>, categoryId: String, onResult: (Int) -> Unit) {
        val targets = transactions.value.filter { it.id in ids }
        if (targets.isEmpty()) {
            onResult(0)
            return
        }
        viewModelScope.launch {
            runCatching { repository.updateAll(targets.map { it.copy(categoryId = categoryId) }) }
                .onSuccess { onResult(targets.size) }
                .onFailure { onResult(-1) }
        }
    }

    /** 删除 + 余额回滚的共用内核；见 [deleteBills] 的注释（顺序与失败补偿）。 */
    private suspend fun deleteAndRollback(
        targets: List<Transaction>,
        onResult: (DeleteBillsOutcome) -> Unit,
    ) {
        if (targets.isEmpty()) {
            onResult(DeleteBillsOutcome.NoMatch)
            return
        }
        // 每笔账单对账户的影响聚合成「账户 → 净增量」
        val deltas = LinkedHashMap<String, Long>()
        targets.forEach { tx ->
            balanceDeltas(
                account = tx.account,
                type = tx.type,
                amountCents = tx.amountCents,
                reverse = true,
                transferToAccount = tx.transferToAccount,
                splits = tx.splits,
            ).forEach { (name, delta) -> deltas[name] = (deltas[name] ?: 0L) + delta }
        }

        // ① 删账单
        runCatching { repository.deleteAll(targets) }
            .onFailure {
                onResult(DeleteBillsOutcome.Failure("删除账单失败：${readableError(it)}"))
                return
            }

        // ② 回滚余额：记录已生效的部分，中途失败时反向撤销
        val applied = LinkedHashMap<String, Long>()
        runCatching {
            deltas.forEach { (name, delta) ->
                accountRepository.adjustBalance(name, delta)
                applied[name] = delta
            }
        }.onFailure {
            // 尽力回滚：这两步仍可能失败，但不能让异常冒泡（否则用户只看到闪退）
            runCatching {
                applied.forEach { (name, delta) -> accountRepository.adjustBalance(name, -delta) }
            }
            runCatching { repository.insertAll(targets) }
            onResult(
                DeleteBillsOutcome.Failure(
                    "回滚账户余额失败，已撤销本次删除：${readableError(it)}",
                ),
            )
            return
        }

        onResult(DeleteBillsOutcome.Success(targets.size))
    }

    // ── 搜索与筛选 ──
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    fun onSearchQueryChange(q: String) { _searchQuery.value = q }

    private val _filterType = MutableStateFlow<TransactionType?>(null)
    val filterType: StateFlow<TransactionType?> = _filterType
    fun setFilterType(type: TransactionType?) { _filterType.value = type }

    /** 过滤后的当月账单（搜索 + 类型筛选） */
    val filteredMonthSummaries: StateFlow<List<DaySummary>> =
        combine(monthSummaries, searchQuery, filterType) { summaries, query, type ->
            val q = query.trim().lowercase()
            summaries.map { day ->
                val filtered = day.transactions.filter { tx ->
                    val matchType = type == null || tx.type == type
                    val matchQuery = q.isEmpty() ||
                        tx.note.lowercase().contains(q) ||
                        tx.account.lowercase().contains(q) ||
                        tx.tags.any { it.lowercase().contains(q) } ||
                        runCatching { com.iji.app.data.repository.CategoryCatalog.of(tx.categoryId).name.lowercase().contains(q) }.getOrDefault(false) ||
                        (tx.amountCents / 100.0).toString().contains(q)
                    matchType && matchQuery
                }
                day.copy(
                    transactions = filtered,
                    expenseCents = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents },
                    incomeCents = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
                )
            }.filter { it.transactions.isNotEmpty() }
        }
            // 搜索过滤是 O(账单数 × 字段数)，移出主线程
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 预算管理 ──
    private val _monthlyBudgetCents = MutableStateFlow(0L)
    val monthlyBudgetCents: StateFlow<Long> = _monthlyBudgetCents

    fun setMonthlyBudget(cents: Long) {
        _monthlyBudgetCents.value = cents
        viewModelScope.launch {
            runCatching {
                // 用 findTotalMonthlyBudget() 而非 allEnabled()：预算曾被设为 0（enabled=false）
                // 时也应复用同一行，否则每设一次就多一行废数据。
                val existing = budgetRepository.findTotalMonthlyBudget()
                val target = (existing ?: Budget(categoryId = "", periodType = BudgetPeriodType.MONTH)).copy(
                    amountCents = cents,
                    ledgerId = currentLedger.value,
                    enabled = cents > 0L,
                )
                budgetRepository.save(target)
            }.onFailure { Log.w("MainViewModel", "保存月度预算失败", it) }
        }
    }

    init {
        viewModelScope.launch {
            runCatching {
                budgetRepository.findTotalMonthlyBudget()?.takeIf { it.enabled }?.amountCents
            }.getOrNull()?.let { saved ->
                if (saved > 0L) _monthlyBudgetCents.value = saved
            }
        }
    }

    /** 本月预算使用率（0f~1f+），计入预算的支出 / 预算 */
    val budgetProgress: StateFlow<Float> =
        combine(monthBudgetExpenseCents, monthlyBudgetCents) { spent, budget ->
            if (budget <= 0L) 0f else spent.toFloat() / budget.toFloat()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /**
     * 首页与预算页**共用**的「本月总预算进度」——两处读同一个 Flow，保证数值永不打架。
     *
     * 优先取持久化的「全分类 + 自然月」Budget，与预算详情页走同一份数据、同一查询口径
     * （[BudgetRepository.progressOf]，按预算自身周期统计，浏览历史月份也不会算错）；
     * 尚未持久化时回退到内存月度预算合成一个，卡片照常显示。
     *
     * 重算时机：预算表变化（Room Flow）+ 账单变化（monthBudgetExpenseCents 作触发信号）。
     */
    val monthBudgetProgress: StateFlow<BudgetProgress?> = combine(
        budgetRepository.budgets,
        monthBudgetExpenseCents,
        monthlyBudgetCents,
    ) { budgets, fallbackSpent, fallbackAmount ->
        Triple(budgets, fallbackSpent, fallbackAmount)
    }.mapLatest { (budgets, fallbackSpent, fallbackAmount) ->
        val persisted = budgets.firstOrNull {
            it.categoryId.isEmpty() && it.periodType == BudgetPeriodType.MONTH && it.enabled
        }
        if (persisted != null) {
            budgetRepository.progressOf(persisted)
        } else if (fallbackAmount > 0L) {
            BudgetCalculator.of(
                Budget(categoryId = "", periodType = BudgetPeriodType.MONTH, amountCents = fallbackAmount),
                fallbackSpent,
            )
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── 周期记账 ──
    // recurringRules 列表声明在类顶部（init 之前），见那里的说明，勿再挪回此处。

    /** 添加周期记账规则（ledgerId 缺省为当前账本） */
    fun addRecurringRule(
        type: TransactionType,
        categoryId: String,
        amountCents: Long,
        note: String,
        account: String,
        freq: RecurrenceFreq,
        startDate: LocalDate,
        ledgerId: String = currentLedger.value,
    ) {
        val rule = RecurringRule(
            type = type,
            categoryId = categoryId,
            amountCents = amountCents,
            note = note,
            account = account,
            freq = freq,
            nextRunDate = startDate,
            ledgerId = ledgerId,
        )
        // 先入内存（UI 立刻可见），等落库拿到真实 id 再回填 ——
        // 新建规则的 id 为 0，不回填的话后续「停用 / 编辑 / 删除」按 id 定位不到这一行
        recurringRules.add(rule)
        viewModelScope.launch {
            val id = recurringRuleRepository.add(rule)
            val idx = recurringRules.indexOfFirst { it === rule }
            if (idx >= 0) recurringRules[idx] = rule.copy(id = id)
            processRecurringRules()
        }
    }

    /**
     * 更新周期记账规则（编辑字段 / 停用启用都走这里）。
     * 按 id 定位内存项；id 为 0（刚新建、尚未回填）时退化为按对象引用定位。
     */
    fun updateRecurringRule(rule: RecurringRule) {
        val idx = indexOfRule(rule)
        if (idx >= 0) recurringRules[idx] = rule
        viewModelScope.launch { recurringRuleRepository.update(rule) }
    }

    /** 删除周期记账规则（不可恢复） */
    fun deleteRecurringRule(rule: RecurringRule) {
        val idx = indexOfRule(rule)
        if (idx >= 0) recurringRules.removeAt(idx)
        viewModelScope.launch { recurringRuleRepository.delete(rule) }
    }

    /** 停用 / 启用：停用的规则不再自动生成账单，但保留配置 */
    fun toggleRecurringRule(rule: RecurringRule, enabled: Boolean) =
        updateRecurringRule(rule.copy(enabled = enabled))

    /** 立即按规则生成一笔，并把下次执行日期推进一个周期 */
    fun runRecurringRuleNow(rule: RecurringRule) {
        val idx = indexOfRule(rule)
        if (idx >= 0) recurringRules[idx] = rule.copy(nextRunDate = LocalDate.now())
        processRecurringRules()
    }

    private fun indexOfRule(rule: RecurringRule): Int =
        if (rule.id != 0L) recurringRules.indexOfFirst { it.id == rule.id }
        else recurringRules.indexOfFirst { it === rule }

    /** 检查并执行到期的周期记账规则 */
    fun processRecurringRules() {
        val today = LocalDate.now()
        var generated = 0
        recurringRules.forEach { rule ->
            if (rule.enabled && rule.nextRunDate <= today) {
                // 生成交易
                addTransaction(
                    type = rule.type,
                    categoryId = rule.categoryId,
                    amountCents = rule.amountCents,
                    note = rule.note,
                    account = rule.account,
                    date = rule.nextRunDate,
                    // 用规则自己的账本，而不是「生成时打开的账本」——
                    // 否则用户切到别的账本时，房租会被记到工作账本里
                    ledgerId = rule.ledgerId,
                )
                // 更新下次执行日期（内存 + 数据库持久化，避免重启后重复生成）
                val nextDate = when (rule.freq) {
                    RecurrenceFreq.DAILY -> rule.nextRunDate.plusDays(1)
                    RecurrenceFreq.WEEKLY -> rule.nextRunDate.plusWeeks(1)
                    RecurrenceFreq.MONTHLY -> rule.nextRunDate.plusMonths(1)
                    RecurrenceFreq.YEARLY -> rule.nextRunDate.plusYears(1)
                }
                val updated = rule.copy(nextRunDate = nextDate)
                val idx = recurringRules.indexOf(rule)
                if (idx >= 0) recurringRules[idx] = updated
                viewModelScope.launch { recurringRuleRepository.update(updated) }
                generated++
            }
        }
        // 自动记账不再静默：有规则执行就汇总通知一条（样式见偏好设置的「超级岛通知」开关）
        if (generated > 0) {
            // 焦点通知/岛发送含 binder 调用与 bypass 等待，放 IO 线程避免卡主线程
            viewModelScope.launch(Dispatchers.IO) {
                BudgetComponents.contextOrNull()?.let { appContext ->
                    NotificationHelper.sendRecurringNotice(
                        appContext,
                        title = "周期账单已自动记账",
                        body = "按规则自动记了 $generated 笔账，点击查看周期账单",
                    )
                }
            }
        }
    }

    // ── 备份（CSV / JSON） ──
    // 序列化与解析已下沉至 BackupRepository。导入需经 ViewModel 写入并联动账户余额，保留门面方法；
    // 导出为纯只读转换，由 UI 直接经 backupRepository 调用（见 BackupSettingsScreen）。
    val backupRepository: BackupRepository = BackupRepository()

    // ── 导入（CSV / JSON）──
    // targetLedger：导入到哪本账。null = 当前账本。

    /**
     * 账单文件导入：逐笔写入，记下真实 id 与账单快照。
     * 历史页的「撤销」靠 id 删除，「重新导入」靠快照重放，两者都只认真实写入结果。
     */
    fun importCsvTransactions(
        transactions: List<Transaction>,
        source: String,
        fileName: String = "",
        targetLedger: String? = null,
        account: String = "",
        failedCount: Int = 0,
        onResult: (ok: Int, failed: Int) -> Unit,
    ) {
        if (transactions.isEmpty()) {
            onResult(0, failedCount)
            return
        }
        viewModelScope.launch {
            val ledger = targetLedger?.takeIf { it.isNotBlank() } ?: currentLedger.value
            var failed = failedCount
            val ids = mutableListOf<Long>()
            val written = mutableListOf<Transaction>()
            transactions.forEach { tx ->
                if (tx.amountCents == 0L) {
                    failed++
                    return@forEach
                }
                val target = tx.copy(
                    id = 0L,
                    ledgerId = ledger,
                    account = account.takeIf { it.isNotBlank() } ?: tx.account,
                )
                runCatching { persistNewTransaction(target) }
                    .onSuccess { id -> ids += id; written += target.copy(id = id) }
                    .onFailure { failed++ }
            }
            importHistoryRepository.record(
                source = source,
                fileName = fileName,
                ledgerId = ledger,
                okCount = ids.size,
                failedCount = failed,
                recordIds = ids,
                payload = ImportPayload.encode(backupRepository.toBackupJson(written)),
            )
            onResult(ids.size, failed)
        }
    }

    /** 备份 JSON 恢复：同样逐笔 await，才有真实成功数与可撤销的 id 列表。 */
    fun importBackupJson(jsonStr: String, targetLedger: String? = null, fileName: String = "", onResult: (Int, Int) -> Unit) {
        val r = backupRepository.parseBackupJson(jsonStr)
        val fallback = targetLedger ?: currentLedger.value
        viewModelScope.launch {
            var failed = r.failedCount
            val ids = mutableListOf<Long>()
            val written = mutableListOf<Transaction>()
            r.transactions.forEach { tx ->
                // 备份里带 ledgerId 就按原账本还原（跨账本恢复的关键）；老备份无该字段则回退到所选账本
                val target = tx.copy(id = 0L, ledgerId = tx.ledgerId.ifBlank { fallback })
                runCatching { persistNewTransaction(target) }
                    .onSuccess { id -> ids += id; written += target.copy(id = id) }
                    .onFailure { failed++ }
            }
            importHistoryRepository.record(
                source = "备份恢复",
                fileName = fileName,
                ledgerId = fallback,
                okCount = ids.size,
                failedCount = failed,
                recordIds = ids,
                payload = ImportPayload.encode(backupRepository.toBackupJson(written)),
            )
            onResult(ids.size, failed)
        }
    }

    /** 清空导入记录（只删历史，不动账单） */
    fun clearImportHistory(onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching { importHistoryRepository.clear() }.isSuccess
            onDone(ok)
        }
    }

    /**
     * 删除单条导入记录：只删 import_history 里的这一行。
     *
     * 不碰 transactions、不回滚余额、不改撤销状态；删掉后这批账单仍在账本里，
     * 只是不再能通过历史页撤销或重放。
     */
    fun deleteImportBatch(batch: ImportBatch, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { importHistoryRepository.deleteById(batch.id) }.isSuccess
            onResult(ok, if (ok) "已删除这条导入记录，已导入的账单不受影响" else "删除失败，请重试")
        }
    }

    /** 撤销一批导入：跨账本按 id 取回 → 删除并回滚余额 → 标记批次（快照保留，仍可重新导入）。 */
    fun undoImportBatch(batch: ImportBatch, onResult: (Boolean, String) -> Unit) {
        if (!batch.canUndo) {
            onResult(false, "这批账单已撤销，或旧记录没有可撤销的账单 id")
            return
        }
        viewModelScope.launch {
            val targets = runCatching { repository.byIds(batch.recordIds) }.getOrDefault(emptyList())
            var result: DeleteBillsOutcome? = null
            deleteAndRollback(targets) { result = it }
            when (val r = result) {
                is DeleteBillsOutcome.Success -> {
                    importHistoryRepository.updateState(batch.id, emptyList(), true)
                    onResult(true, "已撤销 ${r.count} 笔账单")
                }
                is DeleteBillsOutcome.Failure -> onResult(false, r.message)
                else -> {
                    importHistoryRepository.updateState(batch.id, emptyList(), true)
                    onResult(true, "账单已不存在，已标记撤销")
                }
            }
        }
    }

    /** 重新导入：用批次快照重建账单，并更新同一条历史的 id（不新增一批）。 */
    fun reimportBatch(batch: ImportBatch, onResult: (Boolean, String) -> Unit) {
        if (!batch.canReimport) {
            onResult(false, "这条记录没有可重放的账单快照")
            return
        }
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) { ImportPayload.decode(batch.payload) }
            val txs = runCatching { backupRepository.parseBackupJson(json).transactions }
                .getOrElse {
                    onResult(false, "快照已损坏，无法重新导入")
                    return@launch
                }
            if (txs.isEmpty()) {
                onResult(false, "快照里没有账单")
                return@launch
            }
            val ids = mutableListOf<Long>()
            txs.forEach { tx ->
                runCatching { persistNewTransaction(tx.copy(id = 0L)) }
                    .onSuccess { ids += it }
            }
            importHistoryRepository.updateState(batch.id, ids, false)
            onResult(ids.isNotEmpty(), "已重新导入 ${ids.size} 笔账单")
        }
    }
}