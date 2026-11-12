package com.iji.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /** 全部账单，按日期倒序、同日内按 id 倒序 */
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** 按账本查询账单 */
    @Query("SELECT * FROM transactions WHERE ledgerId = :ledgerId ORDER BY dateMillis DESC, id DESC")
    fun observeByLedger(ledgerId: String): Flow<List<TransactionEntity>>

    /** 指定日期区间（含端点，dateMillis 存的是 epochDay）：月视图只查一个月，避免切月全表扫描 */
    @Query(
        "SELECT * FROM transactions WHERE ledgerId = :ledgerId " +
            "AND dateMillis BETWEEN :fromDay AND :toDay ORDER BY dateMillis DESC, id DESC"
    )
    fun observeByLedgerInRange(
        ledgerId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /** 全库标签原始字符串（逗号分隔，统计各标签使用笔数用；只取一列，避免整行搬运） */
    @Query("SELECT tags FROM transactions")
    suspend fun allTags(): List<String>

    @Query("SELECT COUNT(*) FROM transactions WHERE ledgerId = :ledgerId")
    suspend fun countByLedger(ledgerId: String): Int

    /**
     * 一组分类（含子分类）下的账单笔数：删除分类前的"影响面"提示用。
     *
     * 传空列表时 Room 会生成 `IN (NULL)`，恒返回 0，不会误删/误报。
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId IN (:categoryIds)")
    suspend fun countByCategoryIds(categoryIds: List<String>): Int

    /**
     * 把一组分类下的账单改挂到新分类（删除分类时的引用迁移）。
     *
     * @return 受影响行数
     */
    @Query("UPDATE transactions SET categoryId = :toId WHERE categoryId IN (:fromIds)")
    suspend fun reassignCategory(fromIds: List<String>, toId: String): Int

    // 同 LedgerDao：并发播种时按主键忽略重复，避免启动闪退。
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionEntity)

    @androidx.room.Update
    suspend fun update(entity: TransactionEntity)

    /** 批量更新（多选改分类等）：一次事务写完，避免逐笔往返 */
    @androidx.room.Update
    suspend fun updateAll(entities: List<TransactionEntity>)

    @androidx.room.Delete
    suspend fun delete(entity: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /** 跨账本按 id 批量取（撤销导入批次用，不受当前账本过滤影响） */
    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TransactionEntity>

    // ── 应收 / 垫付 / 借款 ──

    /** 待收回（垫付、借出且未核销）的账单，按日期倒序 */
    @Query(
        """
        SELECT * FROM transactions
        WHERE reimburseState = 'PENDING' AND ledgerId = :ledgerId
        ORDER BY dateMillis DESC
        """,
    )
    suspend fun pendingReceivables(ledgerId: String): List<TransactionEntity>

    /** 待收回总额（分） */
    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE reimburseState = 'PENDING' AND ledgerId = :ledgerId
        """,
    )
    suspend fun pendingReceivableCents(ledgerId: String): Long

    // ── 对账 ──

    /** 某账户尚未对账的笔数 */
    @Query("SELECT COUNT(*) FROM transactions WHERE account = :account AND reconciled = 0")
    suspend fun unreconciledCount(account: String): Int

    // ── 商家维度（报表）──

    /** 指定周期内支出最多的商家（按金额倒序） */
    @Query(
        """
        SELECT merchant AS merchant, COALESCE(SUM(amountCents), 0) AS totalCents
        FROM transactions
        WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND ledgerId = :ledgerId
          AND merchant <> '' AND dateMillis BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY merchant
        ORDER BY totalCents DESC
        LIMIT :limit
        """,
    )
    suspend fun topMerchants(
        ledgerId: String,
        startEpochDay: Long,
        endEpochDay: Long,
        limit: Int,
    ): List<MerchantSum>

    /** 某天是否已记过账（记账提醒用：已记就不打扰） */
    @Query("SELECT COUNT(*) FROM transactions WHERE dateMillis = :epochDay AND ledgerId = :ledgerId")
    suspend fun countOnDate(epochDay: Long, ledgerId: String): Int

    /** 指定周期内的支出合计（用于同比环比） */
    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE type = 'EXPENSE' AND excludeFromStats = 0 AND ledgerId = :ledgerId
          AND dateMillis BETWEEN :startEpochDay AND :endEpochDay
        """,
    )
    suspend fun sumExpenseInRange(
        ledgerId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Long

    // ── 按时间条件批量删除 ──

    /**
     * 指定账本、日期区间内的全部账单（**闭区间**）。
     *
     * 注意列名是 `dateMillis` 但存的是 `date.toEpochDay()`（天），所以参数语义是 epochDay 而非毫秒。
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE ledgerId = :ledgerId AND dateMillis BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY dateMillis DESC, id DESC
        """,
    )
    suspend fun inRange(
        ledgerId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    ): List<TransactionEntity>

    /** 指定账本的全部账单（「删除全部」用） */
    @Query("SELECT * FROM transactions WHERE ledgerId = :ledgerId ORDER BY dateMillis DESC, id DESC")
    suspend fun allByLedger(ledgerId: String): List<TransactionEntity>

    /** 批量删除：区间/全部删除一次到位，避免逐笔往返数据库 */
    @androidx.room.Delete
    suspend fun deleteAll(entities: List<TransactionEntity>)

    /**
     * 批量写回：**回滚专用** —— 删除中途出错时把已删掉的账单原样插回去。
     *
     * 用 IGNORE 而非默认 ABORT：回滚发生在异常路径上，此时不应再因主键冲突抛出新异常，
     * 否则会把「删除失败」升级成崩溃，用户连失败提示都看不到。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<TransactionEntity>)
}

/** 商家（收款方）支出聚合结果 */
data class MerchantSum(
    val merchant: String,
    val totalCents: Long,
)
