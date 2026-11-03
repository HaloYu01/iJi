package com.iji.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    /** 全部预算（启用的排在前面） */
    @Query("SELECT * FROM budgets ORDER BY enabled DESC, id ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    /** 全部**启用**的预算：每日巡检与记账后实时检查的入口 */
    @Query("SELECT * FROM budgets WHERE enabled = 1 ORDER BY id ASC")
    suspend fun getEnabled(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    /** 一次性读取全部预算（删除分类时统计/迁移引用用） */
    @Query("SELECT * FROM budgets ORDER BY id ASC")
    suspend fun getAll(): List<BudgetEntity>

    /** 把一组分类下的预算改挂到新分类（删除分类时的引用迁移） */
    @Query("UPDATE budgets SET categoryId = :toId WHERE categoryId IN (:fromIds)")
    suspend fun reassignCategory(fromIds: List<String>, toId: String): Int

    /**
     * 查找「全分类 + 自然月」的总预算（**含已停用的**，避免重复新建行）。
     * 取 id 最小的一条，保证多次设置预算始终是同一行。
     */
    @Query("SELECT * FROM budgets WHERE categoryId = '' AND periodType = 'MONTH' ORDER BY id ASC LIMIT 1")
    suspend fun findTotalMonthly(): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE id = :id")
    fun observeById(id: Long): Flow<BudgetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BudgetEntity): Long

    @Update
    suspend fun update(entity: BudgetEntity)

    @Delete
    suspend fun delete(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 某分类在某周期内的**实际支出**（分）。
     *
     * 口径与既有月度预算保持一致（MainViewModel.budgetExpenseCents）：
     * 只统计支出、剔除 `excludeFromBudget`，按 `amountCents` 求和（不减 discountCents）。
     *
     * @param categoryId 空串表示不限制分类（总预算）
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE type = 'EXPENSE'
          AND excludeFromBudget = 0
          AND ledgerId = :ledgerId
          AND (:categoryId = '' OR categoryId = :categoryId)
          AND dateMillis BETWEEN :startEpochDay AND :endEpochDay
        """,
    )
    suspend fun sumExpenseCents(
        ledgerId: String,
        categoryId: String,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Long
}

@Dao
interface BudgetAlertDao {

    /**
     * 记录一次已触发的告警。
     *
     * @return 新插入行 id；**-1 表示 (budgetId, periodKey, level) 已存在**，
     *         即该预算该周期该级别已提醒过，调用方应跳过发通知。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BudgetAlertEntity): Long

    @Query(
        """
        SELECT COUNT(*) FROM budget_alerts
        WHERE budgetId = :budgetId AND periodKey = :periodKey AND level = :level
        """,
    )
    suspend fun count(budgetId: Long, periodKey: String, level: String): Int

    @Query("SELECT * FROM budget_alerts WHERE budgetId = :budgetId ORDER BY triggeredAtMillis DESC")
    fun observeByBudget(budgetId: Long): Flow<List<BudgetAlertEntity>>

    @Query("SELECT * FROM budget_alerts ORDER BY triggeredAtMillis DESC")
    fun observeAll(): Flow<List<BudgetAlertEntity>>

    /** 删除预算时一并清理其告警记录 */
    @Query("DELETE FROM budget_alerts WHERE budgetId = :budgetId")
    suspend fun deleteByBudget(budgetId: Long)

    /** 清空全部提醒记录（提醒历史页「清空」） */
    @Query("DELETE FROM budget_alerts")
    suspend fun deleteAll()

    /** 清理过期告警（避免表无限增长；日巡检里顺带调用） */
    @Query("DELETE FROM budget_alerts WHERE triggeredAtMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}
