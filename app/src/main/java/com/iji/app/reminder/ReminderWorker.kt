package com.iji.app.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iji.app.budget.NotificationHelper
import com.iji.app.data.db.DbProvider
import com.iji.app.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 每日记账提醒。
 *
 * 设计取舍：**只在当天一笔都还没记时才提醒**。
 * 无脑每天定点推送会让已经记过账的用户觉得被打扰，进而关掉通知；
 * 只在"确实漏记"时提醒，才真正提升数据完整度（这才是这个功能的价值）。
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val repository = runCatching {
                TransactionRepository(DbProvider.database.transactionDao())
            }.getOrNull()
            if (repository == null) {
                Log.w(TAG, "账单仓库不可用，跳过记账提醒")
                return@withContext Result.success()
            }

            val today = LocalDate.now()
            val recorded = repository.countOnDate(today.toEpochDay())
            if (recorded > 0) {
                Log.i(TAG, "今天已有 $recorded 笔记录，不打扰")
                return@withContext Result.success()
            }

            NotificationHelper.sendRecordReminder(applicationContext)
            Log.i(TAG, "已发送记账提醒")
            Result.success()
        }.getOrElse { error ->
            // 与预算巡检一致：返回 failure 而非 retry，避免逻辑性错误进入重试风暴
            Log.e(TAG, "记账提醒失败", error)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "ReminderWorker"
    }
}