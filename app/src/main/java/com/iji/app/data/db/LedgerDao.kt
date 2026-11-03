package com.iji.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    // 按**创建顺序**升序：先建的在前。name 仅作 createdAt 相同（或存量未回填）时的稳定兜底。
    // 不能用 sortOrder：新建账本统一写 Int.MAX_VALUE，那样只能退化成按名称排。
    @Query("SELECT * FROM ledgers ORDER BY createdAt, name")
    fun observeAll(): Flow<List<LedgerEntity>>

    @Query("SELECT COUNT(*) FROM ledgers")
    suspend fun count(): Int

    // 账本名是否已存在（重命名时防重名）
    @Query("SELECT COUNT(*) FROM ledgers WHERE name = :name")
    suspend fun exists(name: String): Int

    /** 账本币种（ISO 代码）；账本不存在时返回 null，由调用方回落 CNY */
    @Query("SELECT currency FROM ledgers WHERE name = :name")
    suspend fun currencyOf(name: String): String?

    // 只改币种：不动名称（名称是主键，改名必须走 renameWithCascade 级联），
    // 也不动任何账单金额 —— 币种只影响展示符号，不做汇率换算。
    @Query("UPDATE ledgers SET currency = :code WHERE name = :name")
    suspend fun updateCurrency(name: String, code: String)

    // 种子播种可能被并发触发（多个 MainViewModel 实例同时 init），
    // 同名账本（ledgers.name 是主键）已在表中时直接忽略，避免
    // "UNIQUE constraint failed: ledgers.name" 抛异常导致启动闪退。
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: LedgerEntity)

    @androidx.room.Update
    suspend fun update(entity: LedgerEntity)

    @androidx.room.Delete
    suspend fun delete(entity: LedgerEntity)

    // ── 重命名 / 删除的级联支持 ──
    //
    // 账本以「名称」为主键，且被 transactions / budgets / recurring_rules 以字符串引用，
    // 因此改名单独执行会让该账本的所有数据**瞬间查不到**（界面表现为数据凭空消失）。
    // 下面把改名做成单事务的级联操作，避免出现「改了一半」的中间态。

    @Query("UPDATE ledgers SET name = :newName WHERE name = :oldName")
    suspend fun renameRow(oldName: String, newName: String)

    @Query("UPDATE transactions SET ledgerId = :newName WHERE ledgerId = :oldName")
    suspend fun migrateTransactions(oldName: String, newName: String)

    @Query("UPDATE budgets SET ledgerId = :newName WHERE ledgerId = :oldName")
    suspend fun migrateBudgets(oldName: String, newName: String)

    @Query("UPDATE recurring_rules SET ledgerId = :newName WHERE ledgerId = :oldName")
    suspend fun migrateRecurringRules(oldName: String, newName: String)

    /** 账本下的账单数：删除前必须先确认，避免误删导致数据丢失 */
    @Query("SELECT COUNT(*) FROM transactions WHERE ledgerId = :name")
    suspend fun transactionCount(name: String): Int

    /** 重命名并级联所有引用（单事务：要么全成，要么全不成） */
    @androidx.room.Transaction
    suspend fun renameWithCascade(oldName: String, newName: String) {
        renameRow(oldName, newName)
        migrateTransactions(oldName, newName)
        migrateBudgets(oldName, newName)
        migrateRecurringRules(oldName, newName)
    }

    /**
     * 删除空账本（连带其预算与周期规则）。
     *
     * **只允许删空账本**：账本下的账单不随之删除，否则一次误操作就会抹掉整本账。
     * 有账单时应先改用「合并到其他账本」或逐笔处理。
     */
    @Query("DELETE FROM budgets WHERE ledgerId = :name")
    suspend fun deleteBudgets(name: String)

    @Query("DELETE FROM recurring_rules WHERE ledgerId = :name")
    suspend fun deleteRecurringRules(name: String)

    @androidx.room.Transaction
    suspend fun deleteIfEmpty(name: String): Boolean {
        if (transactionCount(name) > 0) return false
        deleteBudgets(name)
        deleteRecurringRules(name)
        delete(LedgerEntity(name))
        return true
    }

    // ── 合并账本 ──
    //
    // 有账单的账本不能删（删了账单就成了无主数据），于是「合并到其他账本」是这类账本
    // 唯一的正规出路：把账单 / 预算 / 周期规则整体改挂到目标账本，再删掉空壳账本。

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE ledgerId = :name")
    suspend fun recurringRuleCount(name: String): Int

    @Query("SELECT COUNT(*) FROM budgets WHERE ledgerId = :name")
    suspend fun budgetCount(name: String): Int

    /**
     * 只搬**目标账本里没有同类配置**的预算。
     *
     * 预算的「唯一性」是 (categoryId, periodType)（表上没有唯一索引，只靠应用层约束），
     * 若不做过滤，合并后目标账本会出现两条「全分类 + 月」预算，提醒会重复触发。
     * 冲突时**以目标账本为准**（用户正在用的那本），源账本的这条配置被丢弃并在结果里计数。
     */
    @Query(
        """
        UPDATE budgets SET ledgerId = :to WHERE ledgerId = :from
          AND NOT EXISTS (
            SELECT 1 FROM budgets b2
            WHERE b2.ledgerId = :to
              AND b2.categoryId = budgets.categoryId
              AND b2.periodType = budgets.periodType
          )
        """,
    )
    suspend fun migrateBudgetsSkippingConflicts(from: String, to: String)

    @androidx.room.Transaction
    suspend fun mergeInto(from: String, to: String): LedgerMergeResult? {
        if (from == to) return null
        if (exists(from) == 0 || exists(to) == 0) return null
        val result = LedgerMergeResult(
            transactionCount = transactionCount(from),
            recurringRuleCount = recurringRuleCount(from),
        )
        migrateTransactions(from, to)
        migrateRecurringRules(from, to)
        migrateBudgetsSkippingConflicts(from, to)
        // 剩下的就是与目标账本重复的那几条（目标为准），连同源账本一起清掉
        val droppedBudgets = budgetCount(from)
        deleteBudgets(from)
        delete(LedgerEntity(from))
        return result.copy(droppedBudgetCount = droppedBudgets)
    }
}

/** 合并账本的结果：搬走了多少、以及有多少条预算因目标已有同类配置被丢弃 */
data class LedgerMergeResult(
    val transactionCount: Int = 0,
    val recurringRuleCount: Int = 0,
    val droppedBudgetCount: Int = 0,
)
