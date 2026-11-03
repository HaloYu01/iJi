package com.iji.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 资产账户表：名称 / 副名 / 图标类型 / 余额（分） / 账户分类
 *
 * [category] 存 [com.iji.app.data.model.AccountType] 的枚举名（如 BANK / ONLINE）；
 * **存量数据为空串**，需经 `AccountType.ofAccount(category, iconKey, name)` 推断，
 * 不要直接把空串当分类用。
 */
@Entity(tableName = "accounts", indices = [Index("name")])
data class AccountEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val subName: String,
    val iconKey: String,
    val balanceCents: Long,
    val sortOrder: Int,
    val includeInNetWorth: Boolean = true,
    val category: String = "",
    val hidden: Boolean = false,
)
