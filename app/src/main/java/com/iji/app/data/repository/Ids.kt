package com.iji.app.data.repository

import java.util.concurrent.atomic.AtomicLong

/**
 * 主键生成器：时间戳 + 同毫秒自增，保证**严格单调递增且永不重复**。
 *
 * 背景：账单 / 账户 / 周期记账三张表的主键原先都直接取 `System.currentTimeMillis()`。
 * 同一毫秒内的多次写入会拿到相同主键，而：
 * - TransactionDao / AccountDao 是 `OnConflictStrategy.IGNORE` → 冲突行被**静默丢弃**；
 * - RecurringRuleDao 是默认 ABORT → 直接抛 SQLiteConstraintException **崩溃**。
 *
 * 触发场景都不罕见：批量导入一次上百行、周期记账一次性补齐多条到期规则、
 * 快速连续新增账户。现在统一走这里，用 AtomicLong 的 CAS 保证并发下也唯一。
 */
internal object Ids {

    private val last = AtomicLong(0L)

    fun next(): Long {
        val now = System.currentTimeMillis()
        // 时间戳相同或发生回退（用户改系统时间）时自增，保证唯一且大致有序
        return last.updateAndGet { prev -> if (now > prev) now else prev + 1 }
    }
}