package com.iji.app.ui.mine

/**
 * 捐赠名单与项目支出：手工维护的公示数据，不联网、不读账单、不自动同步支付平台。
 *
 * 新增记录直接往下面的列表里加一行，金额单位是「分」（5000 = ¥50.00），页面用 formatMoney 显示。
 * 这里刻意不放示例数据：名单涉及真实人名与金额，由维护者确认后手工填入，避免展示虚构信息。
 *
 * 格式：
 *   DonationRecord("捐赠者", 5000, "2026-11-08", "备注（可省略）")
 *   DonationExpense("服务器", 3000, "2026-11-08")
 */
data class DonationRecord(
    val name: String,
    val amountCents: Long,
    val date: String? = null,
    val note: String? = null,
)

data class DonationExpense(
    val label: String,
    val amountCents: Long,
    val date: String? = null,
    val note: String? = null,
)

/** 收到的赞赏，按填入顺序显示 */
val donationRecords: List<DonationRecord> = emptyList()

/** 项目支出，按填入顺序显示 */
val donationExpenses: List<DonationExpense> = emptyList()
