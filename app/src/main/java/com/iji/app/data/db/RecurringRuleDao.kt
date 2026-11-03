package com.iji.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules ORDER BY nextRunDate")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT COUNT(*) FROM recurring_rules")
    suspend fun count(): Int

    /** 一次性读取全部周期规则（删除分类时统计/迁移引用用） */
    @Query("SELECT * FROM recurring_rules ORDER BY nextRunDate")
    suspend fun all(): List<RecurringRuleEntity>

    /** 把一组分类下的周期规则改挂到新分类（删除分类时的引用迁移） */
    @Query("UPDATE recurring_rules SET categoryId = :toId WHERE categoryId IN (:fromIds)")
    suspend fun reassignCategory(fromIds: List<String>, toId: String): Int

    @Insert
    suspend fun insert(entity: RecurringRuleEntity)

    @androidx.room.Update
    suspend fun update(entity: RecurringRuleEntity)

    @androidx.room.Delete
    suspend fun delete(entity: RecurringRuleEntity)
}
