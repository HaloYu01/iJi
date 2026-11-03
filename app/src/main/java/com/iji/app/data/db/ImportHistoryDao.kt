package com.iji.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportHistoryDao {

    /** 全部导入记录（按导入时间倒序，最新的在最上面） */
    @Query("SELECT * FROM import_history ORDER BY importedAtMillis DESC")
    fun observeAll(): Flow<List<ImportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ImportHistoryEntity): Long

    /** 清空导入记录（只删历史，不动任何账单） */
    @Query("DELETE FROM import_history")
    suspend fun deleteAll()

    /**
     * 清理过期记录。
     *
     * 导入是低频操作，但表不该无限增长；按时间裁剪即可，
     * 不做「只留最近 N 条」——同一天导入几十次也是用户自己的历史。
     */
    @Query("DELETE FROM import_history WHERE importedAtMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}
