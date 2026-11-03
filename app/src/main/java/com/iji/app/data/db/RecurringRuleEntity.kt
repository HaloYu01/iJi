package com.iji.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 周期记账规则表：nextRunDate 以 epochDay 存储 */
@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey val id: Long,
    val type: String,
    val categoryId: String,
    val amountCents: Long,
    val note: String,
    val account: String,
    val freq: String,
    val nextRunDate: Long,
    val enabled: Boolean,
    /** 所属账本；v13 存量数据默认落在「默认账本」 */
    val ledgerId: String = "默认账本",
)
