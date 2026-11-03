package com.iji.app.data.repository

import android.util.Log
import com.iji.app.data.db.ImportHistoryDao
import com.iji.app.data.db.ImportHistoryEntity
import com.iji.app.data.model.ImportRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 导入历史仓库：Room 持久化；dao 为 null 时退化为空实现（预览等无数据库场景）。
 *
 * 写入点是各导入入口（CSV / 备份恢复 / 截屏批量），读取点是导入页的「导入记录」弹窗。
 */
class ImportHistoryRepository(
    private val dao: ImportHistoryDao?,
) {

    /** 全部导入记录（按导入时间倒序） */
    val records: Flow<List<ImportRecord>> =
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
        atMillis: Long = System.currentTimeMillis(),
    ) {
        val d = dao ?: return
        runCatching {
            d.insert(
                ImportHistoryEntity(
                    source = source,
                    fileName = fileName.orEmpty(),
                    ledgerId = ledgerId.orEmpty(),
                    okCount = okCount,
                    failedCount = failedCount,
                    importedAtMillis = atMillis,
                ),
            )
            // 顺带裁剪：保留一年，避免表随年月无限增长
            d.deleteOlderThan(atMillis - KEEP_MILLIS)
        }.onFailure { Log.w(TAG, "导入历史写入失败（不影响本次导入结果）", it) }
    }

    /** 清空导入记录 */
    suspend fun clear() {
        dao?.deleteAll()
    }

    private fun ImportHistoryEntity.toModel() = ImportRecord(
        id = id,
        source = source,
        fileName = fileName,
        ledgerId = ledgerId,
        okCount = okCount,
        failedCount = failedCount,
        importedAtMillis = importedAtMillis,
    )

    private companion object {
        const val TAG = "ImportHistory"
        const val KEEP_MILLIS = 365L * 24 * 60 * 60 * 1000
    }
}
