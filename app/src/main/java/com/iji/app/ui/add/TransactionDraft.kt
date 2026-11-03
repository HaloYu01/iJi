package com.iji.app.ui.add

import com.iji.app.data.model.PaymentSplit
import com.iji.app.data.model.ReimburseState
import com.iji.app.data.model.TransactionType
import java.time.LocalDate

/**
 * 「记一笔 / 编辑一笔」的完整表单快照：面板对外出口。
 *
 * 面板内部字段较多（金额、分类、账户、标签、分账、对方资料…），此前以 22 个位置参数回传，
 * 新增字段要改 6 处调用点且极易串行错位；这里收成一个对象，字段顺序不再影响调用方。
 */
data class TransactionDraft(
    val type: TransactionType,
    val categoryId: String,
    val amountCents: Long,
    val note: String,
    val account: String,
    val tags: List<String>,
    val date: LocalDate,
    val excludeFromStats: Boolean,
    val excludeFromBudget: Boolean,
    /** 是否涉及报销 / 垫付（由 [reimburseState] 推导，见面板内说明） */
    val reimburse: Boolean,
    val discountCents: Long,
    val attachments: List<String>,
    val merchant: String,
    val counterparty: String,
    val reimburseState: ReimburseState,
    val reconciled: Boolean,
    val ledgerId: String,
    val transferToAccount: String,
    val splits: List<PaymentSplit>,
    /** 退款溯源：本笔退款关联的原消费账单 id；0 = 无关联 */
    val refundOfId: Long,
    // 对方资料：联系方式 / 单位（姓名复用 counterparty）
    val counterpartyContact: String,
    val counterpartyOrg: String,
    /** 当天的第几分钟；-1 = 只记到天（用户没动过分选择器） */
    val minuteOfDay: Int = -1,
)
