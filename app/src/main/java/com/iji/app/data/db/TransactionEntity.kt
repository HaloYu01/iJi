package com.iji.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 账单表：日期存 epochDay，标签存逗号分隔，枚举存名称（由 Converters 完成转换） */
@Entity(tableName = "transactions", indices = [Index("dateMillis"), Index("ledgerId")])
data class TransactionEntity(
    @PrimaryKey val id: Long,
    val type: String,
    val categoryId: String,
    val amountCents: Long,
    val note: String,
    val account: String,
    /** 转账的转入账户（仅转账有值，account 为转出账户） */
    val transferToAccount: String = "",
    val tags: String,
    val dateMillis: Long,
    val ledgerId: String = "默认账本",
    val excludeFromStats: Boolean = false,
    val excludeFromBudget: Boolean = false,
    val reimburse: Boolean = false,
    val discountCents: Long = 0L,
    val attachments: String = "",
    /** 商家 / 收款方（与 note 分开存，便于聚合统计） */
    val merchant: String = "",
    /** 是否已对账 */
    val reconciled: Boolean = false,
    /** 垫付 / 借款状态（ReimburseState.name） */
    val reimburseState: String = "NONE",
    /** 对方（借款人 / 被垫付人 / 报销项目） */
    val counterparty: String = "",
    /** 对方联系方式（电话 / 微信…），借入借出的「对方资料」 */
    val counterpartyContact: String = "",
    /** 对方单位名称，借入借出的「对方资料」 */
    val counterpartyOrg: String = "",
    /**
     * 组合付款的拆分明细（JSON 数组 `[{account, amountCents}]`，空串 = 单一账户）。
     *
     * 用 JSON 而非逗号分隔：账户名是用户可编辑的自由文本，可能含逗号。
     */
    val splits: String = "",
    /** 退款所关联的原消费账单 id（0 = 无关联）；仅用于溯源展示，不参与金额聚合 */
    val refundOfId: Long = 0L,
    /**
     * 当天的第几分钟（0..1439）；**-1 = 未指定时间**，只精确到「天」。
     *
     * 单独一列而不是复用 [dateMillis]：那一列全库按 `epochDay`（天）存，
     * 月份区间查询直接拿它当边界，混入时分会让所有区间查询口径错位。
     * 老数据与「只记日期」的入口一律 -1，行为与升级前完全一致。
     */
    @ColumnInfo(defaultValue = "-1")
    val minuteOfDay: Int = -1,
)
