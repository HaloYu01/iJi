package com.iji.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 账本表：以账本名为主键。
 *
 * [createdAt] 是**创建顺序**的唯一依据（升序 = 越早创建越靠前）。
 * 原先只靠 [sortOrder] 排序，而新建账本一律写 `sortOrder = Int.MAX_VALUE`，
 * 结果用户新建的账本之间只能按名称排，滚轮/下拉里显示的顺序是乱的。
 */
@Entity(tableName = "ledgers")
data class LedgerEntity(
    @PrimaryKey val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    /** 账本级币种（ISO 代码，如 CNY / USD / JPY）。金额仍以「分」存储，不做汇率换算。 */
    val currency: String = "CNY",
)
