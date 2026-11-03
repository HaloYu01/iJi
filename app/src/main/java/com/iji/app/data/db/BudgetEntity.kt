package com.iji.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 预算表。
 *
 * 金额一律存「分」；周期起止存 epochDay（与 transactions.dateMillis 同一口径）。
 */
@Entity(
    tableName = "budgets",
    indices = [Index("categoryId"), Index("ledgerId")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 分类 id；空串 = 全部分类（总预算） */
    val categoryId: String = "",
    val ledgerId: String = "默认账本",
    /** BudgetPeriodType.name */
    val periodType: String = "MONTH",
    val amountCents: Long = 0L,
    val alertThresholdPercent: Int = 80,
    val enabled: Boolean = true,
    /** 是否发送提醒通知。与 enabled 分离：可只参与进度计算而不打扰用户 */
    val reminderEnabled: Boolean = true,
    /** 是否启用结转：上期未用完的额度滚入本期 */
    val carryOver: Boolean = false,
    /** 上期结转进来的额度（分） */
    val rolloverCents: Long = 0L,
    /** 仅 CUSTOM 周期使用 */
    val startEpochDay: Long = 0L,
    /** 仅 CUSTOM 周期使用 */
    val endEpochDay: Long = 0L,
    val createdAtMillis: Long = 0L,
)

/**
 * 已触发的预算告警（用于防重复提醒）。
 *
 * (budgetId, periodKey, level) 上的**唯一索引**是防重的关键：
 * 插入用 OnConflictStrategy.IGNORE，返回 -1 即表示该组合已提醒过。
 */
@Entity(
    tableName = "budget_alerts",
    indices = [Index(value = ["budgetId", "periodKey", "level"], unique = true)],
)
data class BudgetAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val budgetId: Long = 0L,
    /** 周期标识，如 2026-10 / 2026-W42 / 2026 / custom:20000-20030 */
    val periodKey: String = "",
    /** BudgetAlertLevel.name */
    val level: String = "NEAR_LIMIT",
    val spentCents: Long = 0L,
    val progressPercent: Int = 0,
    val triggeredAtMillis: Long = 0L,
)
