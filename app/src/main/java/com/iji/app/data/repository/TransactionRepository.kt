package com.iji.app.data.repository

import com.iji.app.data.db.MerchantSum
import com.iji.app.data.db.TransactionDao
import com.iji.app.data.db.TransactionEntity
import com.iji.app.data.model.Category
import com.iji.app.data.model.PaymentSplit
import com.iji.app.data.model.ReimburseState
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * 分类目录：**唯一的分类读取门面**，数据全部来自 [CategoryStore]。
 *
 * 出厂分类与用户自建分类在这里已经**没有区别**：同样可改名、可删除、可排序。
 * 对外仍以 `expenses / incomes / transfers` 属性与 `of(id)` 暴露，
 * 因此既有调用点（账单行 / 统计 / 备份等）无需改动即可看到用户的全部改动。
 *
 * 索引按 [CategoryStore.cacheVersion] 惰性重建，列表按 `sortOrder` 排序；
 * 用户拖动排序后，记一笔选择器 / 统计 / 高级筛选会一起跟着变。
 */
object CategoryCatalog {

    // ==================== 索引（全量目录，按 CategoryStore 版本号重建） ====================

    /** 目录里查不到 id 时的展示占位：**绝不抛异常**（分类可删，历史数据一定会有悬空 id） */
    private const val UNCATEGORIZED = "未分类"
    private const val UNCATEGORIZED_COLOR = 0xFF9CA3AF

    @Volatile private var idxVersion = -1
    private var idxExpenses: List<Category> = emptyList()
    private var idxIncomes: List<Category> = emptyList()
    private var idxTransfers: List<Category> = emptyList()
    private var idxById: Map<String, Category> = emptyMap()
    private var idxByParent: Map<String?, List<Category>> = emptyMap()

    private fun ensureIndex() {
        val v = CategoryStore.cacheVersion
        if (v == idxVersion) return
        synchronized(this) {
            if (v == idxVersion) return
            val all = CategoryStore.all.sortedBy { it.sortOrder }
            idxExpenses = all.filter { it.type == TransactionType.EXPENSE }
            idxIncomes = all.filter { it.type == TransactionType.INCOME }
            idxTransfers = all.filter { it.type == TransactionType.TRANSFER }
            idxById = all.associateBy { it.id }
            idxByParent = all.groupBy { it.parentId }
            idxVersion = v
        }
    }

    /** 支出分类（按 sortOrder） */
    val expenses: List<Category> get() { ensureIndex(); return idxExpenses }

    /** 收入分类（按 sortOrder） */
    val incomes: List<Category> get() { ensureIndex(); return idxIncomes }

    /** 转账分类（按 sortOrder） */
    val transfers: List<Category> get() { ensureIndex(); return idxTransfers }

    /**
     * 按 id 取分类。
     *
     * 查不到时返回「未分类」占位（保留原 id），**不再回落到某个内置分类**：
     * 分类现在可以被删除，任何"一定存在"的假设都会在用户删掉它之后变成崩溃或错账。
     */
    fun of(id: String): Category {
        ensureIndex()
        idxById[id]?.let { return it }
        return Category(
            id = id,
            name = UNCATEGORIZED,
            color = UNCATEGORIZED_COLOR,
            type = TransactionType.EXPENSE,
        )
    }

    fun ofType(type: TransactionType): List<Category> =
        when (type) {
            TransactionType.EXPENSE -> expenses
            TransactionType.INCOME -> incomes
            // 转账不分类（与既有行为一致）
            TransactionType.TRANSFER -> emptyList()
        }

    /** 一级分类（parentId 为空） */
    fun parentsOfType(type: TransactionType): List<Category> =
        ofType(type).filter { it.parentId == null }

    /** 获取某一级分类下的子分类（按 sortOrder） */
    fun childrenOf(parentId: String): List<Category> {
        ensureIndex()
        return idxByParent[parentId] ?: emptyList()
    }

    /** 该分类是否为出厂分类（**仅用于文案提示**，不再限制编辑/删除） */
    fun isBuiltin(id: String): Boolean = CategoryStore.isBuiltin(id)

    /**
     * 分类树上的系统语义（`borrow` / `lend` / `transfer`），子分类向上继承父级。
     *
     * 借贷业务过去靠 id 前缀判断，用户改名不受影响、但重建分类就断；
     * 改成读标记后，只有真正删掉带语义的分类才会失去识别（此时已无对应账单语义可谈）。
     */
    fun semanticOf(id: String): String? {
        ensureIndex()
        var current = idxById[id]
        var guard = 0
        while (current != null && guard++ < 16) {
            current.semantic?.takeIf { it.isNotBlank() }?.let { return it }
            current = current.parentId?.let { idxById[it] }
        }
        return null
    }

    /** 该分类及其全部后代的 id（删除确认、引用迁移都要按整棵树算） */
    fun subtreeIds(id: String): List<String> {
        ensureIndex()
        val result = mutableListOf(id)
        var cursor = 0
        while (cursor < result.size) {
            val parent = result[cursor]
            idxByParent[parent]?.forEach { result.add(it.id) }
            cursor++
        }
        return result
    }

    /** 某类型下的「其他」分类；不存在时按需补一个（删除迁移的落点必须存在） */
    fun ensureFallback(context: android.content.Context, type: TransactionType): Category {
        val wanted = CategoryDefaults.fallbackId(type)
        ensureIndex()
        idxById[wanted]?.let { return it }
        // 用户把「其他」也删了：按出厂定义补回来（id 已被占用时派生新 id）
        val base = CategoryDefaults.all.first { it.id == wanted }
        var id = base.id
        var seq = 2
        while (CategoryStore.existsById(id)) {
            id = "${base.id}~$seq"
            seq++
        }
        val created = base.copy(
            id = id,
            sortOrder = CategoryStore.nextSortOrder(type, null),
            builtin = false,
        )
        CategoryStore.add(context, created)
        ensureIndex()
        return created
    }

    /**
     * 新增分类（一级/二级皆可，出厂分类与自建分类同权）。
     *
     * @param color 省略时：二级分类继承父分类颜色，一级分类用中性灰
     * @return 创建成功返回新分类；同名（同类型同父级）已存在时返回 null
     */
    fun add(
        context: android.content.Context,
        name: String,
        type: TransactionType,
        parentId: String? = null,
        color: Long = parentId?.let { of(it).color } ?: 0xFF9CA3AF,
    ): Category? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        // id 由名称派生，但**改名后 id 保持旧名**（id 是稳定标识，不随改名变化），
        // 因此必须去重，避免用户把「A」改名为「B」后再新建「A」时撞 id。
        val base = "custom-${type.name.lowercase()}-$trimmed"
        var id = base
        var seq = 2
        while (CategoryStore.existsById(id)) {
            id = "$base~$seq"
            seq++
        }
        val created = Category(
            id = id,
            name = trimmed,
            color = color,
            type = type,
            parentId = parentId,
            sortOrder = CategoryStore.nextSortOrder(type, parentId),
        )
        return if (CategoryStore.add(context, created)) created else null
    }

    /**
     * 更新分类（改名 / 改色 / 改父级 / 改语义）。**id 不变**，账单与预算的引用自动跟随新名字。
     * 重名或写盘失败返回 false。
     */
    fun update(context: android.content.Context, category: Category): Boolean =
        CategoryStore.update(context, category)

    /** 恢复出厂目录：出厂分类重新出现、自建分类清空、排序归位（不可撤销） */
    fun restoreDefaults(context: android.content.Context): Boolean =
        CategoryStore.restoreDefaults(context)

    /**
     * 删除分类及其全部子分类（出厂分类同样可删）。
     *
     * **账单引用不在这里处理**：调用方必须先把 `transactions/budgets/recurring_rules`
     * 里指向这些 id 的行迁走或删掉（见 `MainViewModel.deleteCategory`），否则会留下悬空引用。
     */
    fun remove(context: android.content.Context, id: String): List<Category> =
        CategoryStore.removeCascade(context, id)

    /** 同层级拖动排序：只改 `sortOrder`，返回是否写盘成功 */
    fun reorder(context: android.content.Context, orderedIds: List<String>): Boolean =
        CategoryStore.reorder(context, orderedIds)
}

/**
 * 账单仓库：Room 本地持久化（生产环境）。
 * dao 为 null 时回退到内存实现，仅用于 Compose 预览等无数据库场景。
 */
class TransactionRepository(private val dao: TransactionDao?) {

    private val memory = MutableStateFlow(mockTransactions())

    /** 全部账单（Flow 自动响应数据库变化） */
    val transactions: Flow<List<Transaction>> =
        dao?.observeAll()?.map { list -> list.map { it.toModel() } } ?: memory

    /** 按账本查询账单 */
    fun transactionsByLedger(ledgerId: String): Flow<List<Transaction>> =
        dao?.observeByLedger(ledgerId)?.map { list -> list.map { it.toModel() } }
            ?: memory.map { list -> list.filter { it.ledgerId == ledgerId } }

    /** 某账本某个月的账单（SQL 层按月过滤）：切月只查一个月，不再全表扫描后过滤 */
    fun transactionsByLedgerInMonth(ledgerId: String, month: YearMonth): Flow<List<Transaction>> {
        val fromDay = month.atDay(1).toEpochDay()
        val toDay = month.atEndOfMonth().toEpochDay()
        return dao?.observeByLedgerInRange(ledgerId, fromDay, toDay)?.map { list -> list.map { it.toModel() } }
            ?: memory.map { list ->
                list.filter { it.ledgerId == ledgerId && YearMonth.from(it.date) == month }
            }
    }

    // ── 播种 ──
    //
    // 这里**刻意不提供** seedIfEmpty()：空库时往真实数据库里写十几条模拟流水，
    // 会让新用户首启就看到一堆不属于自己的账——它们和真账在界面上无法区分，
    // 会跟着备份、导出、统计与预算一起被当真。空库就该是空的，首页已有
    // 「本月还没有账单，点底部「＋」记一笔」的空态引导。
    // 模拟数据只保留给无数据库的预览场景（见上方 memory 字段）。

    /** @return 实际写入的 id（撤销 / 定位用） */
    suspend fun add(transaction: Transaction): Long {
        val d = dao
        return if (d != null) {
            // 走 Ids（单调唯一）：同一毫秒的多次写入（批量导入、周期记账一次补多条、
            // 快速连记）会撞主键，而本表 insert 是 IGNORE 策略 → 冲突行被静默丢弃
            val tx = if (transaction.id == 0L) transaction.copy(id = Ids.next()) else transaction
            d.insert(tx.toEntity())
            tx.id
        } else {
            memory.value = memory.value + transaction
            transaction.id
        }
    }

    /** 更新一笔账单 */
    suspend fun update(transaction: Transaction) {
        val d = dao
        if (d != null) {
            d.update(transaction.toEntity())
        } else {
            memory.value = memory.value.map { if (it.id == transaction.id) transaction else it }
        }
    }

    /** 批量更新（多选改分类等）：只写一次事务 */
    suspend fun updateAll(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        val d = dao
        if (d != null) {
            d.updateAll(transactions.map { it.toEntity() })
        } else {
            val byId = transactions.associateBy { it.id }
            memory.value = memory.value.map { byId[it.id] ?: it }
        }
    }

    // ── 应收 / 垫付 / 借款（最容易丢钱的一段）──

    /** 待收回（垫付 / 借出且未核销）的账单 */
    suspend fun pendingReceivables(ledgerId: String = "默认账本"): List<Transaction> =
        dao?.pendingReceivables(ledgerId)?.map { it.toModel() }.orEmpty()

    /** 待收回总额（分） */
    suspend fun pendingReceivableCents(ledgerId: String = "默认账本"): Long =
        dao?.pendingReceivableCents(ledgerId) ?: 0L

    /** 核销：把一笔垫付 / 借出标记为已收回 */
    suspend fun settleReceivable(transaction: Transaction) {
        update(transaction.copy(reimburseState = ReimburseState.SETTLED))
    }

    // ── 对账与凭证 ──

    /** 标记为已对账（与银行 / 支付账单核对过） */
    suspend fun markReconciled(transaction: Transaction) {
        update(transaction.copy(reconciled = true))
    }

    /** 取消对账标记 */
    suspend fun unmarkReconciled(transaction: Transaction) {
        update(transaction.copy(reconciled = false))
    }

    /** 某账户未对账笔数 */
    suspend fun unreconciledCount(account: String): Int = dao?.unreconciledCount(account) ?: 0

    /** 某天已记录的笔数（记账提醒用：已记就不打扰） */
    suspend fun countOnDate(epochDay: Long, ledgerId: String = "默认账本"): Int =
        dao?.countOnDate(epochDay, ledgerId) ?: 0

    // ── 报表 ──

    /** 周期内支出最多的商家 */
    suspend fun topMerchants(
        ledgerId: String,
        startEpochDay: Long,
        endEpochDay: Long,
        limit: Int = 10,
    ): List<MerchantSum> =
        dao?.topMerchants(ledgerId, startEpochDay, endEpochDay, limit).orEmpty()

    /** 全库标签原始字符串（标签管理页统计各标签使用笔数用），未入库时为空 */
    suspend fun allTagStrings(): List<String> = dao?.allTags().orEmpty()

    /** 区间支出合计（同比环比用） */
    suspend fun sumExpenseInRange(
        ledgerId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Long = dao?.sumExpenseInRange(ledgerId, startEpochDay, endEpochDay) ?: 0L

    /** 删除一笔账单 */
    suspend fun delete(transaction: Transaction) {
        val d = dao
        if (d != null) {
            d.delete(transaction.toEntity())
        } else {
            memory.value = memory.value.filterNot { it.id == transaction.id }
        }
    }

    // ── 按时间条件批量删除 ──

    /**
     * 指定账本、日期区间内的账单（**闭区间**）。
     *
     * 与「删除」解耦：调用方先用它拿到命中列表做**数量预览与二次确认**，
     * 确认后再交给 [deleteAll]，避免用户还没确认就把数据删了。
     */
    suspend fun inRange(ledgerId: String, start: LocalDate, end: LocalDate): List<Transaction> {
        val d = dao
        return if (d != null) {
            d.inRange(ledgerId, start.toEpochDay(), end.toEpochDay()).map { it.toModel() }
        } else {
            memory.value.filter { it.ledgerId == ledgerId && it.date in start..end }
        }
    }

    /** 指定账本的全部账单（「删除全部」的数量预览与执行用） */
    suspend fun allByLedger(ledgerId: String): List<Transaction> {
        val d = dao
        return if (d != null) {
            d.allByLedger(ledgerId).map { it.toModel() }
        } else {
            memory.value.filter { it.ledgerId == ledgerId }
        }
    }

    /** 批量删除账单（[inRange] / [allByLedger] 的命中结果） */
    suspend fun deleteAll(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        val d = dao
        if (d != null) {
            d.deleteAll(transactions.map { it.toEntity() })
        } else {
            val ids = transactions.mapTo(HashSet()) { it.id }
            memory.value = memory.value.filterNot { it.id in ids }
        }
    }

    /**
     * 批量写回：**回滚专用**。
     *
     * 删除中途（如余额回退）出错时，把已删掉的账单原样插回去，
     * 保证「删了一半」不会变成永久性的数据丢失。
     */
    suspend fun insertAll(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        val d = dao
        if (d != null) {
            d.insertAll(transactions.map { it.toEntity() })
        } else {
            memory.value = memory.value + transactions
        }
    }

    /** 模拟账单：覆盖当月与前 4 个月，供首启展示与图表预览 */
    private fun mockTransactions(): List<Transaction> {
        val today = LocalDate.now()
        return listOf(
            Transaction(1, TransactionType.EXPENSE, "food", 2_850, "午餐 和同事聚餐", "支付宝", listOf("餐饮", "工作"), today),
            Transaction(2, TransactionType.EXPENSE, "transport", 600, "地铁通勤", "微信", listOf("交通"), today),
            Transaction(3, TransactionType.INCOME, "salary", 1_500_000, "9月工资", "银行卡", listOf("工资"), today),
            Transaction(4, TransactionType.EXPENSE, "shopping", 12_900, "天猫 日用品", "支付宝", listOf("购物"), today.minusDays(1)),
            Transaction(5, TransactionType.EXPENSE, "fun", 4_500, "电影票×2", "微信", listOf("娱乐"), today.minusDays(1)),
            Transaction(6, TransactionType.EXPENSE, "food", 3_200, "晚餐 火锅", "现金", listOf("餐饮", "人情"), today.minusDays(1)),
            Transaction(7, TransactionType.EXPENSE, "home", 350_000, "房租", "银行卡", listOf("生活"), today.minusDays(2)),
            Transaction(8, TransactionType.EXPENSE, "medical", 1_560, "感冒药", "微信", listOf("医疗"), today.minusDays(2)),
            Transaction(9, TransactionType.INCOME, "redpacket", 20_000, "红包", "微信", emptyList(), today.minusDays(3)),
            Transaction(10, TransactionType.EXPENSE, "food", 1_850, "早餐+午餐", "支付宝", listOf("餐饮"), today.minusDays(3)),
            Transaction(11, TransactionType.EXPENSE, "shopping", 25_600, "衣服", "支付宝", listOf("购物"), today.minusMonths(1).withDayOfMonth(12)),
            Transaction(12, TransactionType.EXPENSE, "food", 18_700, "本月餐饮", "微信", listOf("餐饮"), today.minusMonths(1).withDayOfMonth(20)),
            Transaction(13, TransactionType.INCOME, "salary", 1_500_000, "8月工资", "银行卡", listOf("工资"), today.minusMonths(1).withDayOfMonth(10)),
            Transaction(14, TransactionType.EXPENSE, "travel", 128_000, "周边游", "支付宝", listOf("旅行"), today.minusMonths(2).withDayOfMonth(15)),
            Transaction(15, TransactionType.INCOME, "invest", 35_000, "理财收益", "银行卡", emptyList(), today.minusMonths(2).withDayOfMonth(5)),
            Transaction(16, TransactionType.EXPENSE, "fun", 6_800, "游戏充值", "微信", listOf("娱乐"), today.minusMonths(3).withDayOfMonth(8)),
            Transaction(17, TransactionType.EXPENSE, "home", 350_000, "房租", "银行卡", listOf("生活"), today.minusMonths(3).withDayOfMonth(1)),
            Transaction(18, TransactionType.EXPENSE, "education", 9_900, "网课", "支付宝", listOf("学习"), today.minusMonths(4).withDayOfMonth(18)),
            Transaction(19, TransactionType.INCOME, "salary", 1_500_000, "5月工资", "银行卡", listOf("工资"), today.minusMonths(4).withDayOfMonth(10)),
            Transaction(20, TransactionType.TRANSFER, "transfer", 50_000, "零钱转入", "微信", emptyList(), today),
        )
    }
}

private fun Transaction.toEntity() = TransactionEntity(
    id = id,
    type = type.name,
    categoryId = categoryId,
    amountCents = amountCents,
    note = note,
    account = account,
    transferToAccount = transferToAccount,
    tags = tags.joinToString(","),
    dateMillis = date.toEpochDay(),
    ledgerId = ledgerId,
    excludeFromStats = excludeFromStats,
    excludeFromBudget = excludeFromBudget,
    reimburse = reimburse,
    discountCents = discountCents,
    attachments = attachments.joinToString(","),
    merchant = merchant,
    reconciled = reconciled,
    reimburseState = reimburseState.name,
    counterparty = counterparty,
    counterpartyContact = counterpartyContact,
    counterpartyOrg = counterpartyOrg,
    splits = encodeSplits(splits),
    refundOfId = refundOfId,
    minuteOfDay = minuteOfDay,
)

/** 拆分明细 → JSON（空列表存空串，省掉无意义的一对空括号） */
private fun encodeSplits(splits: List<PaymentSplit>): String {
    if (splits.isEmpty()) return ""
    return JSONArray().apply {
        splits.forEach { s ->
            put(
                JSONObject().apply {
                    put("account", s.account)
                    put("amountCents", s.amountCents)
                },
            )
        }
    }.toString()
}

/** JSON → 拆分明细；老数据为空串、或内容损坏时一律退化为「无拆分」 */
private fun decodeSplits(raw: String): List<PaymentSplit> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            PaymentSplit(
                account = o.optString("account"),
                amountCents = o.optLong("amountCents"),
            )
        }.filter { it.account.isNotBlank() }
    }.getOrDefault(emptyList())
}

private fun TransactionEntity.toModel() = Transaction(
    id = id,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    categoryId = categoryId,
    amountCents = amountCents,
    note = note,
    account = account,
    transferToAccount = transferToAccount,
    tags = if (tags.isBlank()) emptyList() else tags.split(",").filter { it.isNotBlank() },
    date = LocalDate.ofEpochDay(dateMillis),
    ledgerId = ledgerId,
    excludeFromStats = excludeFromStats,
    excludeFromBudget = excludeFromBudget,
    reimburse = reimburse,
    discountCents = discountCents,
    attachments = if (attachments.isBlank()) emptyList() else attachments.split(",").filter { it.isNotBlank() },
    merchant = merchant,
    reconciled = reconciled,
    reimburseState = runCatching { ReimburseState.valueOf(reimburseState) }.getOrDefault(ReimburseState.NONE),
    counterparty = counterparty,
    counterpartyContact = counterpartyContact,
    counterpartyOrg = counterpartyOrg,
    splits = decodeSplits(splits),
    refundOfId = refundOfId,
    minuteOfDay = minuteOfDay,
)
