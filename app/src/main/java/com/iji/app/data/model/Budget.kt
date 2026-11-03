package com.iji.app.data.model

import java.time.LocalDate

/** 预算周期类型 */
enum class BudgetPeriodType {
    /** 自然月 */
    MONTH,

    /** 自然周（周一为首日，ISO 周） */
    WEEK,

    /** 自然年 */
    YEAR,

    /** 自定义区间（使用 Budget.startEpochDay / endEpochDay） */
    CUSTOM,
}

/** 预算告警级别 */
enum class BudgetAlertLevel {
    /** 接近超支：进度 ≥ 阈值（默认 80%）且 < 100% */
    NEAR_LIMIT,

    /** 已超支：进度 ≥ 100% */
    OVER_LIMIT,
}

/**
 * 预算（领域模型）。
 *
 * @param categoryId 分类 id；**空串表示「全部分类」的总预算**。
 *   分类不是 Room 实体（见 CategoryCatalog / CategoryStore），这里只存 id。
 * @param ledgerId 所属账本，与 Transaction 一致（默认「默认账本」）
 * @param amountCents 预算金额（分）
 * @param alertThresholdPercent 「接近超支」阈值百分比，默认 80
 * @param startEpochDay / endEpochDay 仅 CUSTOM 周期使用
 */
data class Budget(
    val id: Long = 0L,
    val categoryId: String = "",
    val ledgerId: String = "默认账本",
    val periodType: BudgetPeriodType = BudgetPeriodType.MONTH,
    val amountCents: Long = 0L,
    val alertThresholdPercent: Int = 80,
    val enabled: Boolean = true,
    /** 是否发送提醒通知（与 enabled 分离：可只算进度不打扰） */
    val reminderEnabled: Boolean = true,
    /** 是否启用结转：上期结余（未用完的额度）滚入本期预算 */
    val carryOver: Boolean = false,
    /**
     * 上期结转进来的额度（分）。
     *
     * ⚠️ **不参与额度计算**：结余额度改为按上期真实结余**实时计算**
     * （见 `BudgetRepository.rolloverCentsFor`），本字段仅为备份 / 兼容保留。
     * 原因：结算是"周期切换时跑一次"的定时任务，App 没被打开就不会执行，
     * 存下来的值必然过期；实时算则永远等于"上期到底剩了多少"。
     */
    val rolloverCents: Long = 0L,
    val startEpochDay: Long = 0L,
    val endEpochDay: Long = 0L,
    val createdAtMillis: Long = 0L,
)

/** 预算在某个周期上的区间；[key] 同时用作「同一周期」的判重标识 */
data class BudgetPeriod(
    val key: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val end: LocalDate get() = LocalDate.ofEpochDay(endEpochDay)
}

/** 某一预算在某一周期的进度快照 */
data class BudgetProgress(
    val budget: Budget,
    val period: BudgetPeriod,
    val spentCents: Long,
    /**
     * 本期从上一周期结转进来的额度（分）；未启用结转时为 0。
     * 由 `BudgetRepository.rolloverCentsFor` 按上期真实结余实时算出，不是读库里存的旧值。
     */
    val rolloverCents: Long = 0L,
) {
    /** 本期**基础**额度（分，不含结转） */
    val baseAmountCents: Long get() = budget.amountCents

    /**
     * 本期**有效**预算额度（分）= 基础额度 + 本期结转。
     *
     * 进度百分比、剩余、是否超支都以它为准，这样开了结转的用户看到的
     * 进度和"我真的还有多少钱可花"是一致的，而不是被上期结余打乱。
     */
    val amountCents: Long get() = baseAmountCents + rolloverCents

    /**
     * 进度百分比（整数，可能 >100；预算额 ≤0 时记 0）。
     *
     * 下限夹到 0：退款/红字会让 spentCents 变负，百分比随之变负，
     * 而所有展示层（分类预算卡、首页预算卡、预算进度条文案）都直接渲染这个值。
     */
    val percent: Int
        get() = if (amountCents <= 0L) 0
        else (spentCents * 100 / amountCents).toInt().coerceAtLeast(0)

    /** 剩余（分为正）；超支时为负 */
    val remainingCents: Long get() = amountCents - spentCents

    /** 当前级别：未达阈值返回 null */
    val level: BudgetAlertLevel?
        get() = when {
            amountCents <= 0L -> null
            percent >= 100 -> BudgetAlertLevel.OVER_LIMIT
            percent >= budget.alertThresholdPercent -> BudgetAlertLevel.NEAR_LIMIT
            else -> null
        }

    /** 是否已达「接近超支」阈值（含超支） */
    val isNearLimit: Boolean
        get() = amountCents > 0L && percent >= budget.alertThresholdPercent
}

/**
 * 一条**已触发过**的预算提醒记录（对应 `budget_alerts` 表的一行）。
 *
 * 用途是"提醒历史"：同一预算 / 同一周期 / 同一级别只会记一条（靠唯一索引去重），
 * 所以它回答的是「哪一期、在多少钱的时候、被提醒过一次」，而不是每次越阈值的流水。
 */
data class BudgetAlertRecord(
    val id: Long = 0L,
    val budgetId: Long = 0L,
    /** 周期标识，如 2026-10 / 2026-W42 / 2026 / custom:20000-20030 */
    val periodKey: String = "",
    val level: BudgetAlertLevel = BudgetAlertLevel.NEAR_LIMIT,
    /** 触发当时该预算已花的金额（分） */
    val spentCents: Long = 0L,
    /** 触发当时的进度百分比 */
    val progressPercent: Int = 0,
    val triggeredAtMillis: Long = 0L,
)

/** 一次「新触发」的告警（已排除重复提醒） */
data class BudgetAlertTrigger(
    val progress: BudgetProgress,
    val level: BudgetAlertLevel,
)

/** 消费节奏判定 */
enum class PaceStatus {
    /** 按当前速度会超支 */
    OVER_PACED,

    /** 按当前速度刚好用满附近 */
    ON_PACE,

    /** 花得比预算慢，周期末会有结余 */
    UNDER_PACED,
}

/**
 * 超支预测：把提醒从「事后通知」变成「事前可干预」。
 *
 * 按已过去天数的日均消费外推到周期末，回答两个用户真正关心的问题：
 * 1. 照这个花法，周期末会花多少？
 * 2. 会超支多少？/ 能剩多少？
 */
data class BudgetForecast(
    /** 周期总天数 */
    val daysTotal: Int,
    /** 已过去天数（含今天） */
    val daysElapsed: Int,
    /** 剩余天数（不含今天已计部分） */
    val daysRemaining: Int,
    /** 日均已花（分） */
    val dailyPaceCents: Long,
    /** 按此速度外推到周期末的总支出（分） */
    val projectedCents: Long,
    /** 有效预算额度（分，含结转） */
    val budgetCents: Long,
    /** 预计超支金额（分）；≤0 表示不会超支 */
    val projectedOverspendCents: Long,
    /** 预计结余（分）；负数表示预计超支 */
    val projectedRemainingCents: Long,
    val pace: PaceStatus,
) {
    /** 是否有足够数据做预测（周期刚开始时日均不可信） */
    val isReliable: Boolean get() = daysElapsed >= 1 && dailyPaceCents > 0L
}
