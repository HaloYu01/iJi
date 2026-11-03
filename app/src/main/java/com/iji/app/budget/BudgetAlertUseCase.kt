package com.iji.app.budget

import android.util.Log
import com.iji.app.data.model.BudgetAlertTrigger
import com.iji.app.data.repository.BudgetRepository
import java.time.LocalDate

/**
 * 预算告警的统一入口：检查 + 去重 + 发通知。
 *
 * ViewModel（记账后实时检查）与 [BudgetCheckWorker]（每日巡检）共用这一条路径，
 * 保证「防重复提醒」只有一份实现。
 */
object BudgetAlertUseCase {

    /** 记账后实时检查：命中该分类的预算 + 全分类总预算 */
    suspend fun checkAfterTransaction(
        categoryId: String,
        ledgerId: String,
        at: LocalDate,
    ): List<BudgetAlertTrigger> = runChecks { it.checkForCategory(categoryId, ledgerId, at) }

    /** 每日巡检：全部启用预算 */
    suspend fun checkAll(): List<BudgetAlertTrigger> = runChecks { it.checkAll() }

    private suspend fun runChecks(
        block: suspend (BudgetRepository) -> List<BudgetAlertTrigger>,
    ): List<BudgetAlertTrigger> {
        val triggers = runCatching { block(BudgetComponents.repository()) }.getOrElse { error ->
            Log.w(TAG, "预算检查失败", error)
            emptyList()
        }
        if (triggers.isEmpty()) return triggers

        val context = BudgetComponents.contextOrNull()
        if (context == null) {
            // 告警已入账（下次不会再触发），仅缺通知；不抛异常打断记账流程
            Log.w(TAG, "应用上下文未初始化，跳过发通知（${triggers.size} 条告警已记录）")
            return triggers
        }
        triggers.forEach { NotificationHelper.sendBudgetAlert(context, it) }
        return triggers
    }

    private const val TAG = "BudgetAlertUseCase"
}
