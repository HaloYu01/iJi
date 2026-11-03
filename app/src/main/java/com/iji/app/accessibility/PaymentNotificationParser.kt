package com.iji.app.accessibility

/**
 * 支付通知文本解析（纯逻辑，无 Android 依赖，可单测）。
 *
 * 规则体系参考 AutoAccounting（AutoAccountingOrg/AutoAccounting）v0.5.6 规则包
 * 的通知识别规则（AutoRule 仓库 notice 目录规则 + common.js），分两层：
 *
 * 1. **App 专属规则**：各支付 App / 银行的通知有固定文案模板，按 AutoAccounting
 *    社区维护的正则直接提取金额 / 商户 / 账户，来源包名可进一步限定；
 * 2. **通用兜底规则**：专属规则未命中时，要求「明确含金额 + 明确含收支语义」
 *    才解析，收支关键词与 AutoAccounting common.js 的 isPaymentType 对齐。
 *
 * 原则不变：宁可漏判也不误判——误判会凭空多出一笔账，比漏判更难让用户接受。
 * 所以另有三道闸：验证码 / 中奖类通知直接拒绝；金额为 0 拒绝；超过 100 万视为
 * 误读（余额、卡号）拒绝。
 */
object PaymentNotificationParser {

    private val RE_WHITESPACE = Regex("""\s+""")

    /** 验证码 / 营销类通知：无论长得多像账单都不解析 */
    private val RE_BLACKLIST = Regex("""验证码|校验码|动态密码|动态码|中奖|兑奖""")

    /** 「人民币」开头的金额，优先级最高（银行短信的正式写法） */
    private val RE_AMOUNT_RMB = Regex("""人民币\s*([\d,]+(?:\.\d{1,2})?)""")

    /** 「元」结尾的金额 */
    private val RE_AMOUNT_YUAN = Regex("""([\d,]+(?:\.\d{1,2})?)\s*元""")

    /** 货币符号开头的金额 */
    private val RE_AMOUNT_SYMBOL = Regex("""[¥￥]\s*([\d,]+(?:\.\d{1,2})?)""")

    /** 单笔金额上限（100 万元），超出视为误判（如余额、卡号） */
    private const val MAX_CENTS = 100_000_000L

    /**
     * 收支语义关键词，与 AutoAccounting common.js 的 isPaymentType 对齐：
     * 支出侧（支付/消费/支出/转出/取出/支取/扣款/购买/付款/出账/取款）基础上，
     * 补充旧版已有的「成功/已」变体以覆盖更随意的通知文案。
     */
    private val EXPENSE_WORDS = listOf(
        "支出", "消费", "支付", "付款", "扣款", "扣费", "购买", "购物",
        "转出", "取出", "支取", "取款", "出账",
    )
    private val INCOME_WORDS = listOf(
        "收入", "收款", "入账", "到账", "转入", "存入", "存款",
        "退款", "退回", "返现", "报销入账",
    )

    /** 账户提示词：长词在前，避免「微信零钱」被截成「微信」 */
    private val RE_ACCOUNT = Regex(
        """微信零钱|微信支付|支付宝|云闪付|数字人民币|美团月付|花呗|招行信用卡|招行一卡通|""" +
            """信用卡|储蓄卡|借记卡|银行卡|零钱|钱包|现金""",
    )

    /** 通用兜底的商户提取（AutoAccounting 同款关键词） */
    private val RE_MERCHANT = Regex(
        """(?:商家|商户|收款方|收款人|付款给|交易对方|对方)\s*[:：]?\s*([^\s,，。;；]{1,20})""",
    )

    /** 短信签名「【招商银行】…」→ 银行名可作账户提示（AutoAccounting splitSms 同款） */
    private val RE_SMS_SIGNATURE = Regex("""【([^】]{2,12})】""")

    data class Parsed(
        /** "expense" / "income" */
        val type: String,
        val amountCents: Long,
        val merchant: String,
        val accountHint: String,
    )

    /** 兼容旧调用：不提供来源包名时只走专属规则中不限包名的部分 + 通用兜底 */
    fun parse(text: String): Parsed? = parse(null, text)

    /**
     * @param sourceApp 通知来源包名（如 com.tencent.mm）；专属规则用它精确限定
     */
    fun parse(sourceApp: String?, text: String): Parsed? {
        val raw = text.replace(RE_WHITESPACE, " ").trim()
        if (raw.isBlank()) return null
        if (RE_BLACKLIST.containsMatchIn(raw)) return null

        // 专属规则按声明顺序取第一个命中的（顺序即优先级，AutoAccounting 同策略）
        for (rule in APP_RULES) {
            if (rule.app != null && rule.app != sourceApp) continue
            val match = rule.regex.find(raw) ?: continue
            val parsed = rule.build(match, raw) ?: continue
            if (parsed.amountCents in 1..MAX_CENTS) return parsed
        }
        return parseGeneric(raw)
    }

    // ── App 专属规则 ──────────────────────────────────────────────────────────
    // 每条都注明对应的 AutoAccounting 规则名，便于将来对照 AutoRule 仓库更新。

    private class Rule(
        val name: String,
        /** 限定通知来源包名；null = 不限（命中文案即认） */
        val app: String?,
        val regex: Regex,
        val build: (match: MatchResult, raw: String) -> Parsed?,
    )

    /** 元字符串 → 分；按字符串精确解析，避免 toDouble*100 的浮点误差（19.90 → 1989） */
    private fun cents(raw: String): Long? {
        val s = raw.replace(",", "").trim()
        if (s.isEmpty()) return null
        val intPart = s.substringBefore('.')
        val fracPart = if (s.contains('.')) s.substringAfter('.') else ""
        if (fracPart.length > 2) return null
        // 整数部分超过一亿元必被上限拒绝，先挡掉防溢出
        val yuan = intPart.toLongOrNull() ?: return null
        if (yuan > 100_000_000L) return null
        val frac = when (fracPart.length) {
            0 -> 0L
            1 -> (fracPart[0] - '0') * 10L
            else -> (fracPart[0] - '0') * 10L + (fracPart[1] - '0')
        }
        return yuan * 100 + frac
    }

    /** AutoAccounting splitShop：商户名带「-」时取「-」前的主体（如 支付宝-店铺全称） */
    private fun shopName(raw: String): String {
        if (!raw.contains('-')) return raw.trim()
        return raw.substringBeforeLast('-').trim().ifEmpty { raw.trim() }
    }

    private fun expense(cents: Long, merchant: String = "", account: String = "") =
        Parsed("expense", cents, merchant, account)

    private fun income(cents: Long, merchant: String = "", account: String = "") =
        Parsed("income", cents, merchant, account)

    /** 取文本中第一个 ¥ 金额（微信支付通知的写法，AutoAccounting rule_157 同款取法） */
    private fun firstSymbolAmount(raw: String): Long? =
        RE_AMOUNT_SYMBOL.find(raw)?.groupValues?.get(1)?.let { cents(it) }

    private val APP_RULES: List<Rule> = listOf(
        // 微信支付消费：标题「微信支付」+ 文案「已支付¥xx」（AutoAccounting 微信支付）
        Rule("微信支付消费", "com.tencent.mm", Regex("""已支付""")) { _, raw ->
            firstSymbolAmount(raw)?.let { expense(it, account = "微信支付") }
        },
        // 无包名时的同款兜底：正文同时含「微信支付」和「已支付」才认
        Rule("微信支付消费(无包名)", null, Regex("""微信支付.*已支付""")) { _, raw ->
            firstSymbolAmount(raw)?.let { expense(it, account = "微信支付") }
        },
        // 支付宝花呗支付（AutoAccounting 支付宝花呗支付）
        Rule(
            "支付宝花呗支付", "com.eg.android.AlipayGphone",
            Regex("""你有一笔([\d,]+(?:\.\d{2}))元的支出.*?使用花呗支付"""),
        ) { m, _ ->
            cents(m.groupValues[1])?.let { expense(it, merchant = "支付宝", account = "支付宝花呗") }
        },
        // 网商银行代扣（滴滴）交易提醒（AutoAccounting 网商银行交易提醒）
        Rule(
            "网商银行滴滴支付", "com.eg.android.AlipayGphone",
            Regex("""滴滴支付\s*([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[1])?.let { expense(it, merchant = "滴滴", account = "网商银行") }
        },
        // 云闪付银行卡消费（AutoAccounting 云闪付消费通知）
        Rule(
            "云闪付消费", "com.unionpay",
            Regex("""您尾号为(\d{4})的银行卡于.*?消费([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[2])?.let { expense(it, account = "银行卡(尾号${m.groupValues[1]})") }
        },
        // 云闪付银行卡退款（AutoAccounting 云闪付退款通知）
        Rule(
            "云闪付退款", "com.unionpay",
            Regex("""您尾号为(\d{4})的银行卡于.*?退款([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[2])?.let { income(it, merchant = "云闪付", account = "银行卡(尾号${m.groupValues[1]})") }
        },
        // 云闪付转账入账（AutoAccounting 云闪付转账入账通知）
        Rule(
            "云闪付转账入账", "com.unionpay",
            Regex("""您的(.+?)收到来自(.+?)的转账([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[3])?.let { income(it, merchant = m.groupValues[2], account = m.groupValues[1]) }
        },
        // 云闪付转账出账（AutoAccounting 云闪付转账出账通知：商户=对方名称，账户=对方账户）
        Rule(
            "云闪付转账出账", "com.unionpay",
            Regex("""您向(.+?)的(.+?)转账([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[3])?.let { expense(it, merchant = m.groupValues[1], account = m.groupValues[2]) }
        },
        // 招行信用卡消费/预授权（AutoAccounting 招商银行信用卡消费通知）
        Rule(
            "招行信用卡消费", "cmb.pb",
            Regex("""信用卡通知：您尾号(\d{4})的招行信用卡(?:消费|预授权)([\d,]+(?:\.\d{2}))"""),
        ) { m, _ ->
            cents(m.groupValues[2])?.let { expense(it, account = "招行信用卡(尾号${m.groupValues[1]})") }
        },
        // 招行快捷支付扣款：文案带【商户-商品】（AutoAccounting 招商银行消费通知）
        Rule(
            "招行快捷支付扣款", "cmb.pb",
            Regex("""您账户(\d{4})于.*?在【(.+?)-(.+?)】发生快捷支付扣款，.*?([\d,]+(?:\.\d{2}))"""),
        ) { m, _ ->
            cents(m.groupValues[4])?.let {
                expense(it, merchant = m.groupValues[2], account = "招商银行(尾号${m.groupValues[1]})")
            }
        },
        // 招行账户扣款（AutoAccounting 招商银行消费通知第二式）
        Rule(
            "招行账户扣款", "cmb.pb",
            Regex("""您尾号(\d{4})的账户扣款.*?([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[2])?.let { expense(it, account = "招商银行(尾号${m.groupValues[1]})") }
        },
        // 招行入账（AutoAccounting 招商银行入账通知）
        Rule(
            "招行入账", "cmb.pb",
            Regex("""您尾号(\d{4})的.*?入账.*?([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[2])?.let { income(it, account = "招商银行(尾号${m.groupValues[1]})") }
        },
        // 招行收款（AutoAccounting 招商银行入账通知第二式）
        Rule(
            "招行收款", "cmb.pb",
            Regex("""您账户(\d{4})于.*?收款人民币([\d,]+(?:\.\d{2}))"""),
        ) { m, _ ->
            cents(m.groupValues[2])?.let { income(it, account = "招商银行(尾号${m.groupValues[1]})") }
        },
        // 招行一卡通转出（AutoAccounting 招商银行转账通知）
        Rule(
            "招行一卡通转出", "cmb.pb",
            Regex("""您尾号(\d{4})的招行一卡通转出.*?([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[2])?.let { expense(it, account = "招行一卡通(尾号${m.groupValues[1]})") }
        },
        // 掌上生活消费通知（AutoAccounting 掌上生活消费通知，含真实样例）
        Rule(
            "掌上生活消费", "com.cmbchina.ccd.pluto.cmbActivity",
            Regex("""您在(.+?)有一笔([\d,]+(?:\.\d{2}))人民币的消费已成功"""),
        ) { m, _ ->
            cents(m.groupValues[2])?.let {
                expense(it, merchant = shopName(m.groupValues[1]), account = "招商银行信用卡")
            }
        },
        // 工商银行收支：「尾号xxxx卡M月D日h:i交易类型(商户)金额元」（AutoAccounting 工商银行收支）
        Rule(
            "工商银行收支", "com.icbc",
            Regex("""尾号(\d{4})卡(\d{1,2}月\d{1,2}日\d{1,2}:\d{1,2})(.*?)\(([^)]+)\)([\d,]+(?:\.\d{1,2})?)元"""),
        ) { m, _ ->
            val value = cents(m.groupValues[5])
            // 交易类型在时间与商户之间；无关键词时不猜，交回通用兜底
            when {
                value == null -> null
                EXPENSE_WORDS.any { m.groupValues[3].contains(it) } ->
                    expense(value, merchant = m.groupValues[4], account = "工商银行(尾号${m.groupValues[1]})")
                INCOME_WORDS.any { m.groupValues[3].contains(it) } ->
                    income(value, merchant = m.groupValues[4], account = "工商银行(尾号${m.groupValues[1]})")
                else -> null
            }
        },
        // 农行借记卡收入（AutoAccounting 中国农业银行收入）
        Rule(
            "农行收入", "com.android.bankabc",
            Regex("""您尾号为(\d{4})的农行借记卡于.*?发生一笔收入([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[2])?.let { income(it, merchant = "中国农业银行", account = "中国农业银行(尾号${m.groupValues[1]})") }
        },
        // 中信信用卡消费（AutoAccounting 中信银行信用卡消费通知）
        Rule(
            "中信信用卡消费", "com.citiccard.mobilebank",
            Regex("""您有一笔([\d,]+(?:\.\d{2}))元的消费\(尾号(\d{4})\)"""),
        ) { m, _ ->
            cents(m.groupValues[1])?.let { expense(it, account = "中信信用卡(尾号${m.groupValues[2]})") }
        },
        // 中信「笔笔返」现金奖励：返现金额是第二组（AutoAccounting 同款）
        Rule(
            "中信返现", "com.citiccard.mobilebank",
            Regex("""[\d,]+(?:\.\d{2})元现金奖励笔笔返([\d,]+(?:\.\d{2}))元已成功返现"""),
        ) { m, _ ->
            cents(m.groupValues[1])?.let { income(it, merchant = "中信银行", account = "中信信用卡") }
        },
        // 中信信用卡退款（AutoAccounting 中信银行信用卡退款通知）
        Rule(
            "中信信用卡退款", "com.citiccard.mobilebank",
            Regex("""您有一笔人民币([\d,]+(?:\.\d{2}))元的退款（尾号(\d{4})）"""),
        ) { m, _ ->
            cents(m.groupValues[1])?.let { income(it, account = "中信信用卡(尾号${m.groupValues[2]})") }
        },
        // 美团月付支出（AutoAccounting 美团月付支出）
        Rule(
            "美团月付支出", "com.sankuai.meituan",
            Regex("""【美团月付】成功(?:支付|下单)([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[1])?.let { expense(it, merchant = "美团", account = "美团月付") }
        },
        // 美团月付退款（AutoAccounting 美团月付退款）
        Rule(
            "美团月付退款", "com.sankuai.meituan",
            Regex("""退款成功，([\d,]+(?:\.\d{2}))元已退回美团月付"""),
        ) { m, _ ->
            cents(m.groupValues[1])?.let { income(it, merchant = "美团", account = "美团月付") }
        },
        // 小米公交/门禁卡刷卡（AutoAccounting 小米智能卡消费通知，0 元不记）
        Rule(
            "小米智能卡消费", "com.miui.tsmclient",
            Regex("""刷卡完成，交易金额：([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[1])?.takeIf { it > 0 }?.let { expense(it, account = "小米智能卡") }
        },
        // 数字人民币钱包付款（AutoAccounting 数字人民币消费通知两式）
        Rule(
            "数字人民币付款", "cn.gov.pbc.dcep",
            Regex("""您的(.+?)数字人民币钱包在(.+?)支付[¥￥]([\d,]+(?:\.\d{2}))"""),
        ) { m, _ ->
            cents(m.groupValues[3])?.let { expense(it, merchant = m.groupValues[2], account = "数字人民币") }
        },
        Rule(
            "数字人民币付款(钱包)", "cn.gov.pbc.dcep",
            Regex("""您的(.+?)钱包在(.+?)支付了[¥￥]([\d,]+(?:\.\d{2}))"""),
        ) { m, _ ->
            cents(m.groupValues[3])?.let { expense(it, merchant = m.groupValues[2], account = "数字人民币") }
        },
        // 广西北部湾银行转入（AutoAccounting 广西北部湾银行转入）
        Rule(
            "北部湾银行转入", "com.yitong.bbw.mbank.android",
            Regex("""您尾号(\d{4})的账户发生转入交易，人民币([\d,]+(?:\.\d{2}))元"""),
        ) { m, _ ->
            cents(m.groupValues[2])?.let { income(it, account = "广西北部湾银行(尾号${m.groupValues[1]})") }
        },
        // OPPO/一加钱包消费（AutoAccounting 钱包支付）
        Rule(
            "钱包支付消费", "com.finshell.wallet",
            Regex("""本次消费：([\d,]+(?:\.\d{2}))\s*元"""),
        ) { m, _ ->
            cents(m.groupValues[1])?.let { expense(it, merchant = "钱包支付", account = "OPPO/一加钱包") }
        },
    )

    /**
     * 已支持的来源 App（包名 → 展示名），供界面如实告诉用户「能认哪些通知」。
     *
     * 只列出在 [APP_RULES] 里做过包名限定的 App；**新增带包名的规则时请同步这里**，
     * 否则界面会少报一个来源——少报比多报安全，但也不能长期不一致。
     */
    val supportedSources: Map<String, String> = linkedMapOf(
        "com.tencent.mm" to "微信支付",
        "com.eg.android.AlipayGphone" to "支付宝（花呗 / 网商银行）",
        "com.unionpay" to "云闪付",
        "cmb.pb" to "招商银行",
        "com.cmbchina.ccd.pluto.cmbActivity" to "掌上生活",
        "com.icbc" to "工商银行",
        "com.android.bankabc" to "农业银行",
        "com.citiccard.mobilebank" to "中信银行",
        "com.sankuai.meituan" to "美团月付",
        "com.miui.tsmclient" to "小米智能卡",
        "cn.gov.pbc.dcep" to "数字人民币",
        "com.yitong.bbw.mbank.android" to "广西北部湾银行",
        "com.finshell.wallet" to "OPPO / 一加钱包",
    )

    // ── 通用兜底规则 ──────────────────────────────────────────────────────────

    private fun parseGeneric(raw: String): Parsed? {
        val isIncome = INCOME_WORDS.any { raw.contains(it) }
        val isExpense = EXPENSE_WORDS.any { raw.contains(it) }
        // 收支语义都没有 → 不猜
        if (!isIncome && !isExpense) return null

        val cents = pickAmount(raw) ?: return null
        val merchant = RE_MERCHANT.find(raw)?.groupValues?.get(1)?.trim().orEmpty()

        return Parsed(
            // 收支语义并存（如「消费的48元已退回」）按收入处理：退款的语义强于
            // 句中残留的消费字样，且与旧版行为一致
            type = if (isIncome) "income" else "expense",
            amountCents = cents,
            merchant = merchant,
            accountHint = pickAccount(raw),
        )
    }

    private fun pickAmount(text: String): Long? {
        // 「人民币」→「元」→货币符号，优先取更正式的写法；同一写法命中多次时
        // 取第一处非「余额/额度」前缀的（银行通知习惯把余额附在账单金额之后）
        for (re in listOf(RE_AMOUNT_RMB, RE_AMOUNT_YUAN, RE_AMOUNT_SYMBOL)) {
            for (match in re.findAll(text)) {
                val start = match.range.first
                val prefix = text.substring(maxOf(0, start - 4), start)
                if ("余额" in prefix || "额度" in prefix) continue
                val value = cents(match.groupValues[1]) ?: continue
                if (value in 1..MAX_CENTS) return value
            }
        }
        return null
    }

    private fun pickAccount(text: String): String {
        // 短信签名「【招商银行】」比正文关键词更可靠
        RE_SMS_SIGNATURE.find(text)?.groupValues?.get(1)?.let { return it }
        return RE_ACCOUNT.find(text)?.value.orEmpty()
    }
}
