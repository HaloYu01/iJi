package com.iji.app.data.repository

import android.util.Log
import com.iji.app.data.db.ImportHistoryDao
import com.iji.app.data.db.ImportHistoryEntity
import com.iji.app.data.model.ImportBatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 导入批次仓库：Room 持久化；dao 为 null 时退化为空实现（预览等无数据库场景）。
 *
 * 一条记录 = 一次批量导入。撤销只改状态，不删历史行，这样「重新导入」仍可用。
 */
class ImportHistoryRepository(
    private val dao: ImportHistoryDao?,
) {

    /** 全部批次（按导入时间倒序） */
    val records: Flow<List<ImportBatch>> =
        dao?.observeAll()?.map { list -> list.map { it.toModel() } } ?: flowOf(emptyList())

    /**
     * 记一次导入。
     *
     * 失败的写入只记日志：历史记录本身丢一条，也不该影响账单已经入账这件事。
     */
    suspend fun record(
        source: String,
        fileName: String,
        ledgerId: String,
        okCount: Int,
        failedCount: Int,
        recordIds: List<Long> = emptyList(),
        payload: String = "",
        atMillis: Long = System.currentTimeMillis(),
    ) {
        val d = dao ?: return
        runCatching {
            d.insert(
                ImportHistoryEntity(
                    source = source,
                    fileName = fileName,
                    ledgerId = ledgerId,
                    okCount = okCount,
                    failedCount = failedCount,
                    importedAtMillis = atMillis,
                    recordIds = recordIds.joinToString(","),
                    payload = payload,
                ),
            )
            // 顺带裁剪：保留一年，避免表随年月无限增长
            d.deleteOlderThan(atMillis - KEEP_MILLIS)
        }.onFailure { Log.w(TAG, "导入历史写入失败（不影响本次导入结果）", it) }
    }

    /** 撤销后清空账单 id 并标记；重新导入后写回新 id 并取消标记 */
    suspend fun updateState(id: Long, recordIds: List<Long>, undone: Boolean) {
        dao?.updateState(id, recordIds.joinToString(","), undone)
    }

    suspend fun deleteById(id: Long) {
        dao?.deleteById(id)
    }

    /** 清空导入记录 */
    suspend fun clear() {
        dao?.deleteAll()
    }

    private fun ImportHistoryEntity.toModel() = ImportBatch(
        id = id,
        importedAtMillis = importedAtMillis,
        source = source,
        count = okCount,
        recordIds = recordIds.splitToSequence(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toList(),
        fileName = fileName,
        ledgerId = ledgerId,
        payload = payload,
        undone = undone,
    )

    private companion object {
        const val TAG = "ImportHistory"
        const val KEEP_MILLIS = 365L * 24 * 60 * 60 * 1000
    }
}
