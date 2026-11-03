package com.iji.app.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iji.app.data.repository.SettingsRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 自动备份定时任务（每天一次）。
 *
 * 与预算巡检、记账提醒一致，统一用 WorkManager 的**唯一周期任务**：
 * 重复调用 [schedule] 不会排入第二个任务，也不会重置已有任务的周期。
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val ok = BackupManager.writeAutoBackup(applicationContext)
        if (!ok) {
            Result.retry()
        } else {
            // 配了网盘就一并上传：「自动备份」对用户是一件事，不该只在本地留一份。
            // 上传失败只记日志 —— 本地备份已经写好，为一次网络抖动重跑整个任务是浪费。
            if (CloudBackupManager.isConfigured(applicationContext)) {
                CloudBackupManager.uploadNow(applicationContext)
                    .onFailure { Log.w(TAG, "自动上传网盘失败（本地备份已生成）", it) }
            }
            Result.success()
        }
    } catch (e: Exception) {
        Log.w(TAG, "自动备份异常", e)
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "auto_backup_daily"
        private const val TAG = "AutoBackupWorker"
    }
}

/** 自动备份的开关与排期 */
object AutoBackupScheduler {

    private const val TAG = "AutoBackupScheduler"
    const val KEY_ENABLED = "auto_backup_enabled"
    const val KEY_HOUR = "auto_backup_hour"

    fun isEnabled(context: Context): Boolean = SettingsRepository.getBoolean(context, KEY_ENABLED, false)

    fun hour(context: Context): Int = SettingsRepository.getInt(context, KEY_HOUR, 3).coerceIn(0, 23)

    /** 开关变更后调用：开→排期，关→取消 */
    fun sync(context: Context) {
        if (isEnabled(context)) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(minutesUntilNextRun(context).toLong(), TimeUnit.MINUTES)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AutoBackupWorker.UNIQUE_WORK_NAME,
                // KEEP：已有任务时保留原周期，避免每次进设置页都重置触发时间
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }.onFailure { Log.w(TAG, "排期自动备份失败", it) }
    }

    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(AutoBackupWorker.UNIQUE_WORK_NAME)
        }.onFailure { Log.w(TAG, "取消自动备份失败", it) }
    }

    /** 距今天目标时刻还有多少分钟（已过则顺延到明天） */
    private fun minutesUntilNextRun(context: Context): Int {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour(context))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val diff = target.timeInMillis - now.timeInMillis
        return (diff / 60_000L).toInt().coerceAtLeast(1)
    }
}