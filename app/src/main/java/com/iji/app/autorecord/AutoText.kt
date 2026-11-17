package com.iji.app.autorecord

/**
 * 全自动记账的文本清洗与优惠归集（**独立于半自动**）。
 *
 * 无障碍读到的原文常混着界面噪声：`收起（共 2 笔）`、`立即领取 10 积分`、
 * `券后¥12.00`、`查看详情`。直接写进备注/商户会让账单看起来像爬虫抓的，
 * 也会干扰账户匹配，所以在**引擎出口统一清洗**，规则里不必逐条处理。
 */
object AutoText {

    /** 界面上的动作词 / 提示词：出现在字段值里一定是噪声 */
    private val NOISE = listOf(
        "收起（共", "展开全部", "查看更多", "查看详情", "查看全部", "查看账单",
        "立即领取", "立即查看", "立即使用", "去查看", "去使用", "去支付", "去评价",
        "复制", "分享", "更多", "已完成", "已关闭", "关闭", "返回", "取消",
        "我知道了", "知道了", "确认", "确定", "完成", "下一步", "上一步",
        "客服", "帮助", "投诉", "申请售后", "联系商家", "联系卖家",
        "广告", "推广", "推荐", "热门", "榜单", "精选",
        "展开", "收起", "加载中", "下拉刷新", "没有更多了",
    )

    /** 带变量的噪声：积分 / 券后价 / 展开收起 / 点击查看 */
    private val NOISE_PATTERNS = listOf(
        Regex("""^(?:展开|收起).{0,8}$"""),
        Regex("""立即领取\s*\d+\s*积分"""),
        Regex("""^点击查看.*"""),
        Regex("""^\d+\s*积分$"""),
        Regex("""^券后[¥￥]?[\d.,]+$"""),
        Regex("""^已(?:省|优惠)\s*[¥￥]?[\d.,]+$"""),
        Regex("""^(?:共|合计)\s*\d+\s*笔.*"""),
    )

    private val WHITESPACE = Regex("""\s+""")
    private val LEADING_PUNCT = Regex("""^[：:，,。;；、\-—_]+""")
    private val TRAILING_PUNCT = Regex("""[：:，,。;；、\-—_]+$""")

    /** 优惠 / 立减 / 抵扣的标签（照钱迹 4.5.5 的 16 个键，按常见度排序） */
    private val COUPON_KEYS = listOf(
        "碰一下立减", "银行立减金", "花呗立减", "立减金", "随机立减",
        "支付优惠", "优惠金额", "满减", "红包抵扣", "积分抵扣",
        "余额宝抵扣", "省钱卡", "已优惠", "优惠", "折扣", "券后",
    )

    fun isNoise(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return true
        if (value.length > 60) return true
        if (NOISE.any { value == it }) return true
        if (NOISE_PATTERNS.any { it.matches(value) }) return true
        // 「查看详情」这类按钮常带箭头/右尖括号，做包含判断时只认短文本
        return value.length <= 8 && NOISE.any { value.contains(it) }
    }

    /** 备注：洗噪声、去首尾标点、折叠空白 */
    fun cleanNote(raw: String?): String = clean(raw, maxLength = 50)

    /** 商户：比备注更严格一点，纯数字/编号一律丢掉 */
    fun cleanMerchant(raw: String?): String {
        val value = clean(raw, maxLength = 40)
        if (value.isEmpty()) return ""
        if (value.all { it.isDigit() || it == '.' || it == '-' }) return ""
        if (value.length > 24 && value.none { it in '\u4e00'..'\u9fa5' }) return ""
        return value
    }

    /** 账户原文：只去首尾空白与「优先使用」前缀，保留渠道名本身 */
    fun cleanAsset(raw: String?): String {
        var value = raw?.trim().orEmpty()
        value = value.removePrefix("优先使用").removePrefix("默认").removePrefix("使用")
        return value.trim().trim('：', ':', '，', ',')
    }

    /**
     * 优惠归集：把「立减 / 抵扣 / 券后」等标签后的金额累加成负数费用。
     *
     * 只做保守匹配：标签后 3 个节点内必须是可解析金额，否则跳过——
     * 宁可少算优惠，也不要把「优惠券」的券面额当成实际优惠。
     */
    fun couponCents(nodes: List<AutoNode>): Long {
        if (nodes.isEmpty()) return 0L
        var total = 0L
        nodes.forEachIndexed { index, node ->
            if (COUPON_KEYS.none { node.text.contains(it) }) return@forEachIndexed
            // 标签自身就带金额：`立减¥2.00`
            val inline = amountIn(node.text)
            val amount = inline ?: run {
                var hit: Long? = null
                for (i in index + 1..minOf(index + 3, nodes.lastIndex)) {
                    val candidate = nodes[i].text
                    if (isNoise(candidate)) continue
                    hit = amountIn(candidate)
                    if (hit != null) break
                }
                hit
            }
            // 一次只算一次：`花呗立减` 同时含「立减」，靠 forEachIndexed 天然去重
            if (amount != null && amount in 1..10_000_000L) total += amount
        }
        return total
    }

    private fun amountIn(text: String): Long? = if (AutoValues.isAmount(text)) {
        AutoValues.amountCents(text)
    } else {
        // `立减¥2.00` / `优惠 2 元`：从混合文本里抠最后一个金额
        Regex("""[¥￥]?\s*(\d{1,6}(?:\.\d{1,2})?)\s*元?""")
            .findAll(text)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.let { Math.round(it * 100) }
            ?.takeIf { it > 0 }
    }

    private fun clean(raw: String?, maxLength: Int): String {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        if (isNoise(value)) return ""
        NOISE_PATTERNS.forEach { value = it.replace(value, "") }
        value = WHITESPACE.replace(value, " ")
        value = LEADING_PUNCT.replace(value, "")
        value = TRAILING_PUNCT.replace(value, "")
        value = value.trim()
        return if (value.length > maxLength) value.take(maxLength) else value
    }
}
