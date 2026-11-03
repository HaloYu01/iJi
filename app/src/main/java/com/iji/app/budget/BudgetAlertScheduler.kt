package com.iji.app.budget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 预算巡检调度：每天巡查一次全部启用预算，避免只靠记账触发而遗漏
 * （例如跨周期、周期自动翻页等场景）。
 */
object BudgetAlertScheduler {

    /** 唯一任务名：重复调用不会排入第二个周期任务 */
    const val UNIQUE_WORK_NAME = "budget_daily_check"

    /** 每天的检查时刻（本地时间 09:00） */
    private const val CHECK_HOUR = 9

    /**
     * 排入每日巡检（已存在则保持原任务，不重置周期）。
     *
     * 用 [ExistingPeriodicWorkPolicy.KEEP]，避免每次冷启动都把下一次执行时间往后推，
     * 导致「永远不执行」。
     */
    fun scheduleDaily(context: Context) {
        val delayMinutes = minutesUntilNextCheck()
        val request = PeriodicWorkRequestBuilder<BudgetCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    // 低电量也照常检查：预算提醒与省电策略无关，且执行极轻量
                    .setRequiresBatteryNotLow(false)
                    .build(),
            )
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 立即跑一次（用于调试，或用户在预算页改动预算后立即校准） */
    fun runOnce(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<BudgetCheckWorker>().build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /** 距离下一个 CHECK_HOUR 的分钟数（已过则算到明天同一时刻） */
    private fun minutesUntilNextCheck(now: LocalDateTime = LocalDateTime.now()): Long {
        var next = now.withHour(CHECK_HOUR).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return java.time.Duration.between(now, next).toMinutes().coerceAtLeast(1L)
    }
}
