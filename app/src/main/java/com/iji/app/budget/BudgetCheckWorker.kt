package com.iji.app.budget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 预算后台巡检：每天检查一次**全部启用**预算，对**新触发**的告警发通知。
 *
 * 防重复由 budget_alerts 的唯一索引 (budgetId, periodKey, level) 保证
 * —— 仓库只返回首次触发的告警，因此同一周期同一级别不会重复打扰用户。
 */
class BudgetCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val triggers = BudgetAlertUseCase.checkAll()
            // 顺带清理半年以前的告警记录，避免表无限增长
            BudgetComponents.repository().pruneAlerts()
            Log.i(TAG, "预算巡检完成，新触发 ${triggers.size} 条告警")
            Result.success()
        }.getOrElse { error ->
            // 返回 failure 而非 retry：日巡检明天还会再跑，避免逻辑性错误进入重试风暴
            Log.e(TAG, "预算巡检失败", error)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "BudgetCheckWorker"
    }
}
