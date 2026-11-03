package com.iji.app.budget

import com.iji.app.data.model.Budget
import com.iji.app.data.model.BudgetAlertLevel
import com.iji.app.data.model.BudgetForecast
import com.iji.app.data.model.BudgetPeriod
import com.iji.app.data.model.BudgetPeriodType
import com.iji.app.data.model.BudgetProgress
import com.iji.app.data.model.PaceStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields

/**
 * 预算进度计算工具（纯函数，无 IO，便于单测）。
 */
object BudgetCalculator {

    /**
     * 计算预算在 [at] 所属周期的区间与判重 key。
     *
     * key 同时用于「同一周期」的防重复提醒，因此必须**在同一周期内保持稳定**：
     * - MONTH → `2026-10`
     * - WEEK  → `2026-W42`（ISO 周，周一为首日）
     * - YEAR  → `2026`
     * - CUSTOM→ `custom:<start>-<end>`
     */
    fun periodOf(budget: Budget, at: LocalDate = LocalDate.now()): BudgetPeriod {
        val start: LocalDate
        val end: LocalDate
        val key: String
        when (budget.periodType) {
            BudgetPeriodType.MONTH -> {
                val ym = YearMonth.from(at)
                start = ym.atDay(1)
                end = ym.atEndOfMonth()
                key = "%04d-%02d".format(ym.year, ym.monthValue)
            }
            BudgetPeriodType.WEEK -> {
                val wf = WeekFields.ISO
                val week = at.get(wf.weekOfWeekBasedYear())
                val weekYear = at.get(wf.weekBasedYear())
                start = at.with(wf.dayOfWeek(), 1) // ISO 第 1 天 = 周一
                end = start.plusDays(6)
                key = "%04d-W%02d".format(weekYear, week)
            }
            BudgetPeriodType.YEAR -> {
                start = LocalDate.of(at.year, 1, 1)
                end = LocalDate.of(at.year, 12, 31)
                key = "%04d".format(at.year)
            }
            BudgetPeriodType.CUSTOM -> {
                start = LocalDate.ofEpochDay(budget.startEpochDay)
                end = LocalDate.ofEpochDay(budget.endEpochDay)
                key = "custom:${budget.startEpochDay}-${budget.endEpochDay}"
            }
        }
        return BudgetPeriod(
            key = key,
            startEpochDay = start.toEpochDay(),
            endEpochDay = end.toEpochDay(),
        )
    }

    /** 组装进度快照（spentCents 由 [com.iji.app.data.repository.BudgetRepository] 从库里聚合得到） */
    fun of(budget: Budget, spentCents: Long, at: LocalDate = LocalDate.now()): BudgetProgress =
        BudgetProgress(
            budget = budget,
            period = periodOf(budget, at),
            spentCents = spentCents,
        )

    /**
     * 判定告警级别。
     *
     * @return ≥100% → [BudgetAlertLevel.OVER_LIMIT]；
     *         ≥阈值（默认 80%）→ [BudgetAlertLevel.NEAR_LIMIT]；否则 null。
     */
    fun levelOf(progress: BudgetProgress): BudgetAlertLevel? = progress.level

    /** 本期有效额度（基础额度 + 启用结转时的上期结转） */
    fun effectiveAmountCents(budget: Budget): Long =
        budget.amountCents + if (budget.carryOver) budget.rolloverCents else 0L

    /**
     * 超支预测：用「已过去天数的日均支出」外推到周期末。
     *
     * 价值：原本的提醒只在**已经**接近/超出预算时才通知，是事后补救；
     * 有了预测，用户月中就能知道"照这个花法月底会超 800 块"，从而来得及调整。
     */
    fun forecast(progress: BudgetProgress, now: LocalDate = LocalDate.now()): BudgetForecast {
        val start = progress.period.start
        val end = progress.period.end
        val daysTotal = (end.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(1)
        // 已过去天数（含今天），并夹在 [0, daysTotal]
        val elapsed = (now.toEpochDay() - start.toEpochDay() + 1)
            .coerceIn(0L, daysTotal.toLong()).toInt()
        val remaining = (daysTotal - elapsed).coerceAtLeast(0)

        val dailyPace = if (elapsed > 0) progress.spentCents / elapsed else 0L
        val projected = dailyPace * daysTotal
        val budgetCents = effectiveAmountCents(progress.budget)
        val projectedRemaining = budgetCents - projected
        val overspend = (-projectedRemaining).coerceAtLeast(0L)

        val reliable = elapsed >= 1 && progress.spentCents > 0L
        val pace = when {
            !reliable -> PaceStatus.ON_PACE
            projected > budgetCents -> PaceStatus.OVER_PACED
            // 预计用不到额度的 90% 视为偏慢
            projected < budgetCents * 90 / 100 -> PaceStatus.UNDER_PACED
            else -> PaceStatus.ON_PACE
        }

        return BudgetForecast(
            daysTotal = daysTotal,
            daysElapsed = elapsed,
            daysRemaining = remaining,
            dailyPaceCents = dailyPace,
            projectedCents = projected,
            budgetCents = budgetCents,
            projectedOverspendCents = overspend,
            projectedRemainingCents = projectedRemaining,
            pace = pace,
        )
    }

    /**
     * 结算本期、算出应结转给下一期的额度。
     *
     * 未启用结转、或本期已超支时返回 0 —— **超支不累计到下期**，否则会滚成债务雪球。
     * 上限卡在 [BudgetProgress.amountCents]：退款冲减支出后 remainingCents 可能**大于**本期额度，
     * 差额是「本期没花完」而非「下期預算」，一并结转会永久放大下期额度。
     *
     * 调用方（`BudgetRepository.rolloverCentsFor`）传入的是**上一期**的进度快照，
     * 且该快照不带结转（`rolloverCents` 取默认 0），因此结余只按上期基础额度算、不连锁累加。
     */
    fun rolloverFrom(progress: BudgetProgress): Long {
        if (!progress.budget.carryOver) return 0L
        return progress.remainingCents.coerceIn(0L, progress.amountCents)
    }
}
