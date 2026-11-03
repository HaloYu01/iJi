package com.iji.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    /** 一次性读取全部账户（AI 账户识别需要「全部账户名」做候选，Flow 版本不方便在 IO 里等） */
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    suspend fun getAllOnce(): List<AccountEntity>

    // 同 LedgerDao：并发播种时按主键忽略重复，避免启动闪退。
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AccountEntity)

    @androidx.room.Update
    suspend fun update(entity: AccountEntity)

    @androidx.room.Delete
    suspend fun delete(entity: AccountEntity)

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): AccountEntity?

    @Query("UPDATE accounts SET balanceCents = balanceCents + :delta WHERE name = :name")
    suspend fun adjustBalance(name: String, delta: Long)

    /** 资产页拖动排序后写入新顺序（同类别内的下标） */
    @Query("UPDATE accounts SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)
}
