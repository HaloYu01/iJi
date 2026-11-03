package com.iji.app.accessibility

/**
 * 通知关键词闸门：决定一条通知**要不要进解析**（纯 JVM，可单测）。
 *
 * 为什么需要它：解析器本身靠「金额 + 收支语义」把关，但群消息、营销推送里同样可能出现
 * 「转账」「余额」「元」这些词，白白跑一遍 26 条规则。先按关键词粗筛一遍，
 * 既省 CPU，也顺手挡掉了「群里有人说『我转你 50 元』」这类误触。
 *
 * 规则很简单，因为「配置项越复杂越没人配」：
 * - 命中**黑名单**任何一词 → 拦掉（黑名单优先，哪怕同时命中白名单）；
 * - 白名单**为空** → 全放行（用户清空即视为不设闸门，本仓库语义）；
 * - 否则必须命中白名单里任一词。
 */
object PaymentNotificationFilter {

    /** 白名单 / 黑名单在 SettingsRepository（DataStore）里的键，界面与解析服务共用 */
    const val KEY_WHITELIST = "payment_filter_whitelist"
    const val KEY_BLACKLIST = "payment_filter_blacklist"

    /**
     * 默认白名单：钱相关词汇。
     *
     * 刻意保留「元」与货币符号——银行短信的核心特征就是金额，漏掉它们会把大量真实账单挡在门外；
     * 代价是「这次活动满 100 元」这类营销文案也会进解析，再由解析器「必须同时有收支语义」挡掉。
     */
    const val DEFAULT_WHITELIST =
        "银行,信用卡,借记卡,储蓄卡,公积金,元,￥,¥,人民币,消费,支付,支出,转出,取出,取款," +
            "收入,转入,存入,存款,退款,还款,贷款,借款,逾期,转账,账户,余额,交易,动账,账单"

    fun allowed(text: String, whitelist: String, blacklist: String): Boolean {
        if (text.isBlank()) return false
        if (matches(text, blacklist)) return false
        val words = split(whitelist)
        if (words.isEmpty()) return true
        return words.any { text.contains(it, ignoreCase = true) }
    }

    /** 词表里的有效词条数，界面显示「白 N · 黑 M」用（与 [allowed] 共用同一套分隔规则） */
    fun wordCount(raw: String): Int = split(raw).size

    /** 逗号（中英文）/ 换行 / 竖线 / 空格分隔，方便用户直接粘贴一段词表 */
    private fun split(raw: String): List<String> =
        raw.split(',', '，', '\n', '|', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun matches(text: String, raw: String): Boolean =
        split(raw).any { text.contains(it, ignoreCase = true) }
}
