package com.iji.app.data.repository

import android.util.Log
import com.iji.app.budget.BudgetCalculator
import com.iji.app.data.db.BudgetAlertDao
import com.iji.app.data.db.BudgetAlertEntity
import com.iji.app.data.db.BudgetDao
import com.iji.app.data.db.BudgetEntity
import com.iji.app.data.model.Budget
import com.iji.app.data.model.BudgetAlertLevel
import com.iji.app.data.model.BudgetAlertRecord
import com.iji.app.data.model.BudgetAlertTrigger
import com.iji.app.data.model.BudgetPeriod
import com.iji.app.data.model.BudgetPeriodType
import com.iji.app.data.model.BudgetProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * 预算仓库：Room 持久化；dao 为 null 时退化为「无预算」的空实现（预览等无数据库场景）。
 */
class BudgetRepository(
    private val dao: BudgetDao?,
    private val alertDao: BudgetAlertDao?,
) {

    /** 全部预算（Flow 自动响应数据库变化） */
    val budgets: Flow<List<Budget>> =
        dao?.observeAll()?.map { list -> list.map { it.toModel() } } ?: flowOf(emptyList())

    /**
     * 全部提醒记录（按触发时间倒序）—— 提醒历史页的数据源。
     *
     * 注意它**不是**「每次越阈值的流水」：同一预算 / 同一周期 / 同一级别靠唯一索引只留一条，
     * 所以 N 条记录 = N 次真正意义上的提醒。
     */
    val alerts: Flow<List<BudgetAlertRecord>> =
        alertDao?.observeAll()?.map { list -> list.map { it.toModel() } } ?: flowOf(emptyList())

    /** 全部启用预算：日巡检入口 */
    suspend fun allEnabled(): List<Budget> =
        dao?.getEnabled()?.map { it.toModel() }.orEmpty()

    suspend fun get(id: Long): Budget? = dao?.getById(id)?.toModel()

    /**
     * 「全分类 + 自然月」的总预算（含已停用）。
     * 设置预算时必须用它而不是 [allEnabled]，否则把预算改成 0（enabled=false）后再设回来会新建重复行。
     */
    suspend fun findTotalMonthlyBudget(): Budget? = dao?.findTotalMonthly()?.toModel()

    fun observeById(id: Long): Flow<Budget?> =
        dao?.observeById(id)?.map { it?.toModel() } ?: flowOf(null)

    /** 新增或更新（id=0 新增）；返回预算 id */
    suspend fun save(budget: Budget): Long {
        val d = dao ?: return 0L
        return if (budget.id == 0L) {
            d.insert(
                budget.toEntity().copy(createdAtMillis = budget.createdAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis()),
            )
        } else {
            d.update(budget.toEntity())
            budget.id
        }
    }

    /**
     * 更新提醒设置：开关 + 「接近超支」阈值。
     *
     * @param thresholdPercent 1~100；100 表示只在真正超支时才提醒（没有「接近」阶段）
     */
    suspend fun setReminder(id: Long, enabled: Boolean, thresholdPercent: Int) {
        val d = dao ?: return
        val entity = d.getById(id) ?: return
        d.update(
            entity.copy(
                reminderEnabled = enabled,
                alertThresholdPercent = thresholdPercent.coerceIn(1, 100),
            ),
        )
    }

    /**
     * 设置结转开关。关闭时保留用户设定的固定金额：[rolloverCentsFor] 在未启用时
     * 直接返回 0，再打开时还能沿用上次设定，不必重填。
     */
    suspend fun setCarryOver(id: Long, enabled: Boolean) {
        val d = dao ?: return
        val entity = d.getById(id) ?: return
        d.update(entity.copy(carryOver = enabled))
    }

    /**
     * 设置自定义结转金额（分）。
     *
     * `0` = 回到「按上期结余自动结转」；`> 0` = 每期固定按这个额度结转。
     * 复用 budgets.rolloverCents 列，旧数据默认值 0 行为不变，无需数据库升级。
     */
    suspend fun setCarryOverAmount(id: Long, amountCents: Long) {
        val d = dao ?: return
        val entity = d.getById(id) ?: return
        d.update(entity.copy(rolloverCents = amountCents.coerceAtLeast(0L)))
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val d = dao ?: return
        val entity = d.getById(id) ?: return
        d.update(entity.copy(enabled = enabled))
    }

    /** 删除预算并清理其告警记录 */
    suspend fun remove(id: Long) {
        dao?.deleteById(id)
        alertDao?.deleteByBudget(id)
    }

    /** 某预算在某周期内已发生的支出（分） */
    suspend fun spentCents(budget: Budget, period: BudgetPeriod): Long =
        dao?.sumExpenseCents(
            ledgerId = budget.ledgerId,
            categoryId = budget.categoryId,
            startEpochDay = period.startEpochDay,
            endEpochDay = period.endEpochDay,
        ) ?: 0L

    /** 某预算在 [at] 所属周期的进度快照 */
    suspend fun progressOf(budget: Budget, at: LocalDate = LocalDate.now()): BudgetProgress {
        val period = BudgetCalculator.periodOf(budget, at)
        return BudgetProgress(
            budget = budget,
            period = period,
            spentCents = spentCents(budget, period),
            rolloverCents = rolloverCentsFor(budget, at),
        )
    }

    /**
     * 本期从上一周期结转进来的额度（分）= 上期**没花完**的基础额度；未启用结转时恒为 0。
     *
     * **为什么实时算而不是落库**：结算天然是「周期切换时跑一次」的定时任务，
     * 用户几天不开 App 就没人触发，存下来的 `rolloverCents` 必然过期；
     * 而「上期花了多少」是既成事实，随时重算都得到同一个答案。
     *
     * **不连锁**：只用上期的基础额度算结余（不把上上期的结转再算进来），
     * 否则结余会一期期滚雪球，预算额度被无限放大。
     *
     * CUSTOM 周期没有「上一周期」的概念，直接返回 0。
     *
     * 例外：`budget.rolloverCents > 0` 表示用户设了固定结转额度，直接采用、跳过自动结算。
     */
    suspend fun rolloverCentsFor(budget: Budget, at: LocalDate = LocalDate.now()): Long {
        if (!budget.carryOver) return 0L
        // 固定额度优先；0 = 未设置，继续按上期真实结余实时算（兼容旧数据）。
        if (budget.rolloverCents > 0L) return budget.rolloverCents
        val prevAt = when (budget.periodType) {
            BudgetPeriodType.MONTH -> at.minusMonths(1)
            BudgetPeriodType.WEEK -> at.minusWeeks(1)
            BudgetPeriodType.YEAR -> at.minusYears(1)
            BudgetPeriodType.CUSTOM -> return 0L
        }
        val prevPeriod = BudgetCalculator.periodOf(budget, prevAt)
        val prevSpent = spentCents(budget, prevPeriod)
        // rolloverCents 省略（默认 0）→ 上期额度 = 基础额度，不连锁
        return BudgetCalculator.rolloverFrom(BudgetProgress(budget, prevPeriod, prevSpent))
    }

    /** 该预算全部周期内的进度（按周期倒序取最近若干个） */
    suspend fun progressHistory(budget: Budget, count: Int = 6): List<BudgetProgress> {
        val list = mutableListOf<BudgetProgress>()
        var cursor = LocalDate.now()
        repeat(count) {
            list += progressOf(budget, cursor)
            cursor = when (budget.periodType) {
                BudgetPeriodType.MONTH -> cursor.minusMonths(1)
                BudgetPeriodType.WEEK -> cursor.minusWeeks(1)
                BudgetPeriodType.YEAR -> cursor.minusYears(1)
                BudgetPeriodType.CUSTOM -> return@repeat
            }
        }
        return list
    }

    /**
     * 每日巡检：检查**全部启用**预算，返回本次**新触发**的告警
     * （同一预算 / 同一周期 / 同一级别已提醒过的会被去重、不返回）。
     */
    suspend fun checkAll(at: LocalDate = LocalDate.now()): List<BudgetAlertTrigger> {
        // 关闭提醒的预算只参与进度展示，不检查、不打扰
        val budgets = allEnabled().filter { it.reminderEnabled }
        val triggers = mutableListOf<BudgetAlertTrigger>()
        for (b in budgets) {
            val progress = progressOf(b, at)
            val level = progress.level ?: continue
            if (markTriggered(progress, level)) triggers += BudgetAlertTrigger(progress, level)
        }
        return triggers
    }

    /**
     * 记账后实时检查：只检查受这笔账影响的预算
     * —— 命中该分类的预算 + 全分类总预算（categoryId 为空串）。
     */
    suspend fun checkForCategory(
        categoryId: String,
        ledgerId: String,
        at: LocalDate = LocalDate.now(),
    ): List<BudgetAlertTrigger> {
        val related = allEnabled().filter {
            it.reminderEnabled &&
                it.ledgerId == ledgerId &&
                (it.categoryId.isEmpty() || it.categoryId == categoryId)
        }
        val triggers = mutableListOf<BudgetAlertTrigger>()
        for (b in related) {
            val progress = progressOf(b, at)
            val level = progress.level ?: continue
            if (markTriggered(progress, level)) triggers += BudgetAlertTrigger(progress, level)
        }
        return triggers
    }

    /**
     * 记录告警并返回是否**首次**触发。
     *
     * 依赖 budget_alerts 的唯一索引 (budgetId, periodKey, level)：
     * IGNORE 插入返回 -1 即代表该组合已提醒过。
     */
    private suspend fun markTriggered(
        progress: BudgetProgress,
        level: BudgetAlertLevel,
    ): Boolean {
        val d = alertDao ?: return true // 无数据库（预览）场景不去重
        val rowId = d.insert(
            BudgetAlertEntity(
                budgetId = progress.budget.id,
                periodKey = progress.period.key,
                level = level.name,
                spentCents = progress.spentCents,
                progressPercent = progress.percent,
                triggeredAtMillis = System.currentTimeMillis(),
            ),
        )
        return rowId != -1L
    }

    /** 清空全部提醒记录（用户在提醒历史页手动清理） */
    suspend fun clearAlerts() {
        runCatching { alertDao?.deleteAll() }
            .onFailure { Log.w(TAG, "清空预算提醒记录失败", it) }
    }

    /** 清理过期告警记录（默认保留半年），避免表无限增长 */
    suspend fun pruneAlerts(keepDays: Long = 180L) {
        runCatching {
            alertDao?.deleteOlderThan(System.currentTimeMillis() - keepDays * 24 * 60 * 60 * 1000L)
        }.onFailure { Log.w(TAG, "清理过期预算告警失败", it) }
    }

    private companion object {
        const val TAG = "BudgetRepository"
    }
}

private fun BudgetEntity.toModel() = Budget(
    id = id,
    categoryId = categoryId,
    ledgerId = ledgerId,
    periodType = runCatching { BudgetPeriodType.valueOf(periodType) }.getOrDefault(BudgetPeriodType.MONTH),
    amountCents = amountCents,
    alertThresholdPercent = alertThresholdPercent,
    enabled = enabled,
    reminderEnabled = reminderEnabled,
    carryOver = carryOver,
    rolloverCents = rolloverCents,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    createdAtMillis = createdAtMillis,
)

private fun BudgetAlertEntity.toModel() = BudgetAlertRecord(
    id = id,
    budgetId = budgetId,
    periodKey = periodKey,
    level = runCatching { BudgetAlertLevel.valueOf(level) }.getOrDefault(BudgetAlertLevel.NEAR_LIMIT),
    spentCents = spentCents,
    progressPercent = progressPercent,
    triggeredAtMillis = triggeredAtMillis,
)

private fun Budget.toEntity() = BudgetEntity(
    id = id,
    categoryId = categoryId,
    ledgerId = ledgerId,
    periodType = periodType.name,
    amountCents = amountCents,
    alertThresholdPercent = alertThresholdPercent,
    enabled = enabled,
    reminderEnabled = reminderEnabled,
    carryOver = carryOver,
    rolloverCents = rolloverCents,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    createdAtMillis = createdAtMillis,
)
