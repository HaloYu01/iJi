package com.iji.app.ai

import android.graphics.Bitmap
import com.iji.app.capture.ForegroundAppResolver
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.CategoryDefaults
import java.time.LocalDate

/**
 * 截屏账单列表页里的一笔**待导入**账单。
 *
 * 这是"草稿"而非最终账单：字段可能识别不准，必须经预览页确认后才写库。
 */
data class BillDraft(
    val type: TransactionType,
    val categoryId: String,
    val amountCents: Long,
    val note: String,
    val account: String,
    val date: LocalDate,
    /** 预览页勾选状态；疑似重复的默认不勾 */
    val selected: Boolean = true,
    /** 疑似重复时指向库内已有账单 id；null = 判定为新账单 */
    val duplicateOfId: Long? = null,
    /** 优惠/立减金额（分）；实付 = amountCents，原价 = amountCents + discountCents */
    val discountCents: Long = 0L,
    /**
     * 商户 / 收款方原文。
     *
     * 全自动识别会把商户单独存下来：同商户的历史分类复用、商家统计都依赖这一列。
     * 之前商户被并进 note，落库后 Transaction.merchant 为空，历史分类无从查起。
     */
    val merchant: String = "",
)

/**
 * 账单列表页「整屏截屏」→ 多笔账单。
 *
 * 与 [LocalOcrEngine] 的分工：那个是给**一张小票只出一笔**做的 —— 它把整图所有行拼成一整串再挑一个金额，
 * 因此完全没有"按笔切分"的能力。账单列表页一屏十几行、每行一笔，必须**按行切分**再逐行抽字段。
 *
 * ## 设计取向：宁可漏认，不要错认
 *
 * 账单列表页的版式随 App 版本、机型、系统字号变化很大，纯规则的整屏识别不可能 100% 准。
 * 所以这里只做"高置信度"的抽取：
 * - 金额必须带货币符号 / 带正负号 / 恰好两位小数，避免把 `10:30`、`10-14` 当金额；
 * - 商户名只认"紧邻金额上方"或"金额同一行金额之前"的那段文本；
 * - 任何一笔都进**预览页**由用户核对后才写库，不做静默导入。
 */
object ScreenshotBillParser {

    /** 金额上限（分）：超过 100 万人民币的多半是识别错误（把账号、订单号当金额） */
    private const val MAX_AMOUNT_CENTS = 100_000_000L

    // ── 金额 ──
    /** 带货币符号：`¥32.00` / `-￥1,234.5` */
    private val RE_CNY = Regex("""([-−+＋])?\s*[¥￥]\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")

    /** 带正负号 + 必须有小数：`-32.00` / `+1,200.00`（要求小数是为了不和 `10-14` 这类日期撞车） */
    private val RE_SIGNED = Regex("""([-−+＋])\s*(\d{1,3}(?:,\d{3})*\.\d{1,2}|\d+\.\d{1,2})""")

    /** 恰好两位小数（账单页金额几乎总是两位），且左右不能紧邻数字/冒号/点，避免命中时间与编号 */
    private val RE_DEC2 = Regex("""(?<![\d:.：])(\d{1,3}(?:,\d{3})*\.\d{2})(?![\d:：.])""")

    // ── 日期 / 时间 ──
    private val RE_FULL_DATE = Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?""")
    private val RE_MD = Regex("""(?<!\d)(\d{1,2})\s*[-/月]\s*(\d{1,2})\s*日?(?!\d)""")
    private val RE_TIME = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")

    /**
     * 纯日期/分组标题行（`10-14`、`2026年10月`）的识别。
     *
     * **字符集里刻意不含 `.`** —— 含 `.` 的话 `-32.00`、`45.50` 这类金额会整个命中，
     * 被当成日期行丢掉，导致列表页只剩"带 ¥ 或带千分位"的那一两笔能认出来。
     */
    private val RE_PURE_DATEISH = Regex("""^[\d\s年月日\-—·/]+$""")

    /**
     * 纯 UI 框架文字，不可能是商户名。
     *
     * 含各 App 名（顶栏标题行）：[detectAccount] 靠它推断账户，但那一行本身不是商户 ——
     * 若不排除，整屏第一笔若"金额行前面没有商户文本"，就会把「支付宝」当成商户名写进备注。
     */
    private val NOISE = listOf(
        "账单", "全部", "筛选", "搜索", "收支", "交易时间", "交易类型", "交易对方",
        "更多", "没有更多", "暂无", "明细", "统计", "本月", "上月", "近三月", "请选择",
        "收支统计", "我的", "首页", "查看", "商品说明", "收付款", "当前", "完成",
        "支付宝", "微信", "云闪付",
    )

    /** 收支方向关键词（用于**金额同一行**的包含判断，如「支出 32.00」） */
    private val INCOME_WORDS = listOf("收入", "收款", "退款", "转入", "收到", "入账")
    private val EXPENSE_WORDS = listOf("支出", "付款", "消费", "转出", "支付", "扣款")
    private val TRANSFER_WORDS = listOf("转账", "提现", "充值", "还款", "零钱通", "余额宝")

    /**
     * **独立成行**的收支标签 → 收支类型。
     *
     * 这里只认精确相等，**不能用 contains** —— 否则「转账-张三」「真实消费」「正常消费」
     * 这类**商户名**会被当成标签行丢掉，笔记里就只剩空商户，甚至把上一行的金额文本当商户名。
     */
    private val LABEL_EXACT: Map<String, TransactionType> = mapOf(
        "支出" to TransactionType.EXPENSE,
        "付款" to TransactionType.EXPENSE,
        "消费" to TransactionType.EXPENSE,
        "转出" to TransactionType.EXPENSE,
        "收入" to TransactionType.INCOME,
        "收款" to TransactionType.INCOME,
        "退款" to TransactionType.INCOME,
        "转入" to TransactionType.INCOME,
        "转账" to TransactionType.TRANSFER,
        "提现" to TransactionType.TRANSFER,
        "充值" to TransactionType.TRANSFER,
        "还款" to TransactionType.TRANSFER,
    )

    /** 一屏最多认这么多笔：远超正常列表页行数时，说明这张图不是账单列表页 */
    private const val MAX_DRAFTS = 60

    /** 商户关键词 → 分类名（最终按名称解析成分类 id，避免硬编码 id 与目录脱节） */
    private val CATEGORY_KEYWORDS: List<Pair<List<String>, String>> = listOf(
        listOf("早餐", "正餐", "外卖", "餐饮", "饭店", "餐厅", "食堂", "烧烤", "火锅", "小吃",
            "咖啡", "奶茶", "星巴克", "瑞幸", "肯德基", "麦当劳", "必胜客", "美团", "饿了么",
            "水果", "面包", "蛋糕", "零食", "夜宵", "food") to "餐饮",
        listOf("地铁", "公交", "打车", "滴滴", "出租", "高铁", "火车", "机票", "航班", "加油",
            "停车", "高速", "单车", "共享", "交通", "机场", "车票", "ETC") to "交通",
        // 注意别放单字关键词：早期放了个「包」，导致「红包」被误判成购物
        listOf("淘宝", "天猫", "京东", "拼多多", "超市", "便利店", "商场", "购物", "服饰",
            "数码", "家电", "美妆", "母婴", "图书", "书籍", "盒马", "山姆") to "购物",
        listOf("房租", "水电", "电费", "水费", "物业", "燃气", "宽带", "居住", "维修", "家政", "取暖") to "居住",
        listOf("电影", "游戏", "娱乐", "KTV", "视频", "会员", "音乐", "演出", "运动", "游乐园", "腾讯视频", "爱奇艺") to "娱乐",
        listOf("医院", "药", "诊所", "挂号", "体检", "医疗", "门诊", "保健", "口腔", "牙") to "医疗",
        listOf("学费", "培训", "课程", "教育", "网课", "书", "考试", "订阅") to "教育",
        listOf("酒店", "民宿", "旅游", "门票", "景区", "旅行", "携程", "飞猪") to "旅行",
        listOf("请客", "礼物", "喜事", "人情", "份子", "送礼") to "人情",
        listOf("工资", "薪资", "薪", "奖金", "津贴") to "工资",
        listOf("利息", "分红", "理财", "基金", "收益") to "理财",
        listOf("红包", "中奖", "彩票") to "红包",
        listOf("报销", "垫付") to "报销",
        listOf("借款", "借出", "借入", "贷款") to "借出",
        // 放最后：转账目录里只有「转账」一个分类，放前面会抢走别的类型的关键词
        listOf("转账", "提现", "充值", "还款") to "转账",
    )

    /**
     * 整屏识别：OCR 行 → 多笔草稿。
     *
     * @param today 用于把 `10-14` 这类无年份日期补全为完整日期，并处理跨年
     * @param sourcePkg 截图时的前台应用包名；能映射到账户时优先于 OCR 文本推断
     */
    suspend fun recognizeDrafts(
        bmp: Bitmap,
        today: LocalDate = LocalDate.now(),
        sourcePkg: String? = null,
    ): List<BillDraft> {
        val lines = LocalOcrEngine.recognizeLines(bmp).orEmpty()
        return parse(lines, today, sourcePkg)
    }

    /**
     * 从整屏文字判断截图来自哪个 App，用作这批账单的默认账户。
     *
     * 返回的是**账户名**（与内置账户 / 资产账户同名），便于直接落到 `Transaction.account`。
     * 判不出来返回空串，由调用方让用户选。
     */
    fun detectAccount(lines: List<String>): String {
        val joined = lines.joinToString(" ")
        return when {
            joined.contains("支付宝") -> "支付宝"
            joined.contains("微信") -> "微信"
            joined.contains("云闪付") -> "云闪付"
            else -> ""
        }
    }

    /** 纯函数版本（便于单测 / 直接喂 OCR 文本） */
    fun parse(
        lines: List<String>,
        today: LocalDate = LocalDate.now(),
        sourcePkg: String? = null,
    ): List<BillDraft> {
        val out = mutableListOf<BillDraft>()
        // 前台包名是硬证据（顶栏文字可能被 OCR 漏掉或误读），认不出再退回整屏文字推断
        val detectedAccount = ForegroundAppResolver.accountFor(sourcePkg)
            ?: detectAccount(lines).takeIf { it.isNotBlank() }
            ?: ""
        var curDate: LocalDate? = null
        // 商户名与收支方向都可能落在金额**上一行**（微信/支付宝列表常见的两行式版式）
        var pendingMerchant = ""
        var pendingType: TransactionType? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // 1) 日期：可能是分组标题行（「2026年10月」「10-14」），也可能是行内的一部分
            extractDate(line, today, curDate)?.let { curDate = it }

            val money = findMoney(line)
            if (money == null) {
                // 2) 无金额的行：独立标签 / 商户名 / 纯 UI 文字
                LABEL_EXACT[line]?.let {
                    pendingType = it
                    continue
                }
                if (isNoise(line)) continue
                val cleaned = cleanMerchant(line)
                if (cleaned.isNotBlank()) pendingMerchant = cleaned
                continue
            }

            // 3) 有金额 → 成一笔
            val before = cleanMerchant(line.substring(0, money.range.first))
            val merchant = before.ifBlank { pendingMerchant }
            // 显式正负号最可靠，其次是金额同一行里的「支出/收入」字样，最后才用上一行的标签
            val type = when {
                money.income == true -> TransactionType.INCOME
                money.income == false -> TransactionType.EXPENSE
                else -> directionIn(line) ?: pendingType ?: TransactionType.EXPENSE
            }
            pendingType = null
            pendingMerchant = ""

            out += BillDraft(
                type = type,
                categoryId = guessCategoryId(merchant, type),
                amountCents = money.cents,
                note = merchant.take(50),
                account = detectedAccount,
                date = curDate ?: today,
            )
            if (out.size >= MAX_DRAFTS) break
        }
        return out
    }

    // ── 内部工具 ──

    private data class Money(val cents: Long, val income: Boolean?, val range: IntRange)

    /**
     * 从一行里找金额。优先级：货币符号 > 正负号 > 恰好两位小数。
     * 返回 null 表示这行没有可信金额。
     */
    private fun findMoney(line: String): Money? {
        // 纯日期/纯数字行直接排除（分组标题「2026年10月」「14」等）
        if (RE_PURE_DATEISH.matches(line)) return null

        RE_CNY.find(line)?.let { m ->
            val sign = m.groupValues[1]
            val cents = toCents(m.groupValues[2]) ?: return@let
            if (cents <= 0L || cents > MAX_AMOUNT_CENTS) return@let
            return Money(cents, signIncome(sign), m.range)
        }
        RE_SIGNED.find(line)?.let { m ->
            val cents = toCents(m.groupValues[2]) ?: return@let
            if (cents <= 0L || cents > MAX_AMOUNT_CENTS) return@let
            return Money(cents, signIncome(m.groupValues[1]), m.range)
        }
        val m = RE_DEC2.find(line) ?: return null
        // 双位数冒号紧邻的多半是时间（如「12.30」被 OCR 误读），已被正则的前后视断言排除一部分，
        // 这里再挡一次"整行只有时间"的情况
        if (RE_TIME.containsMatchIn(line) && line.length < 8) return null
        val cents = toCents(m.groupValues[1]) ?: return null
        if (cents <= 0L || cents > MAX_AMOUNT_CENTS) return null
        return Money(cents, null, m.range)
    }

    /** 符号 → 收支：`-` 支出、`+` 收入、无符号 null */
    private fun signIncome(sign: String): Boolean? = when (sign) {
        "-", "−" -> false
        "+", "＋" -> true
        else -> null
    }

    private fun toCents(raw: String): Long? {
        val v = raw.replace(",", "").toDoubleOrNull() ?: return null
        return Math.round(v * 100)
    }

    /** 从一行里抽日期；抽不到返回 null（调用方沿用上一次的日期） */
    private fun extractDate(line: String, today: LocalDate, fallback: LocalDate?): LocalDate? {
        RE_FULL_DATE.find(line)?.let { m ->
            return runCatching {
                LocalDate.of(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt(),
                )
            }.getOrNull()
        }
        // 相对日期
        val rel = when {
            line.contains("今天") -> today
            line.contains("昨天") -> today.minusDays(1)
            line.contains("前天") -> today.minusDays(2)
            else -> null
        }
        if (rel != null) return rel

        RE_MD.find(line)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return@let
            val day = m.groupValues[2].toIntOrNull() ?: return@let
            if (month !in 1..12 || day !in 1..31) return@let
            // 无年份：默认今年；若这会让日期落到"未来"，说明截图是去年的
            val guess = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return@let
            val year = if (guess.isAfter(today.plusDays(1))) today.year - 1 else today.year
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        return null
    }

    /**
     * 只用于**金额所在那一行**的收支判断（如「支出 32.00」）。
     *
     * 这种行除金额外都是要丢弃的标签，所以用 contains 足够；
     * 但**不能**用它判断独立成行的内容 —— 那会误伤「转账-张三」这类商户名，
     * 独立行请用 [LABEL_EXACT] 精确匹配。
     */
    private fun directionIn(line: String): TransactionType? = when {
        TRANSFER_WORDS.any { line.contains(it) } -> TransactionType.TRANSFER
        // 收入词里「退款」也算收入侧（列表页的退款是钱回到账户）
        INCOME_WORDS.any { line.contains(it) } -> TransactionType.INCOME
        EXPENSE_WORDS.any { line.contains(it) } -> TransactionType.EXPENSE
        else -> null
    }

    private fun isNoise(line: String): Boolean {
        if (line.length < 2) return true
        if (NOISE.any { line == it || line.startsWith(it) && line.length <= it.length + 2 }) return true
        // 纯数字 / 纯符号
        if (line.all { it.isDigit() || it in ".:/-—+%¥￥ " }) return true
        // 过长的一行多半是说明文字，不是商户名
        if (line.length > 30) return true
        return false
    }

    /** 去掉行内的日期、时间、货币符号，得到商户名 */
    private fun cleanMerchant(raw: String): String {
        // 带货币符号的行不可能是商户名。这条挡掉的典型情况：
        // 金额**超过上限**被判定为无效的那一行（如把「¥1,200,000.00」误识别出来的），
        // 它会退化成"商户候选"，把真正的商户名顶掉。
        if (raw.contains('¥') || raw.contains('￥')) return ""

        var s = raw
        s = RE_TIME.replace(s, " ")
        s = RE_FULL_DATE.replace(s, " ")
        s = RE_MD.replace(s, " ")
        s = s.replace(Regex("""[¥￥]"""), " ")
        s = s.replace(Regex("""^[\s\-—·*•+]+|[\s\-—·*•+]+$"""), "")
        s = s.replace(Regex("""\s{2,}"""), " ")
        return s.trim()
    }

    /** 商户名 → 分类 id（按分类**名称**解析，找不到用「其他」） */
    private fun guessCategoryId(merchant: String, type: TransactionType): String {
        val catalog = when (type) {
            TransactionType.EXPENSE -> CategoryCatalog.expenses
            TransactionType.INCOME -> CategoryCatalog.incomes
            TransactionType.TRANSFER -> CategoryCatalog.transfers
        }
        if (merchant.isNotBlank()) {
            // **逐个**关键词组尝试，命中但该分类不在当前类型目录里时**继续往后找** ——
            // 例如「红包」会被购物组的「包」命中，但收入目录里没有「购物」，
            // 若此时直接放弃就会落到「其他」，而正确结果是后面的「红包」组。
            CATEGORY_KEYWORDS.forEach { (words, name) ->
                if (words.any { merchant.contains(it, ignoreCase = true) }) {
                    catalog.firstOrNull { it.name == name }?.let { return it.id }
                }
            }
        }
        // 兜底：先找当前类型「其他」的固定 id（支出 other / 收入 other-income），
        // 用户可能把它改名，所以再按名字找，最后退到目录第一项；目录为空时返回空串（= 未分类），
        // 绝不返回一个可能已被删除的 id —— 分类现在可以被删除，硬编码 id 的假设都不可靠。
        val fallbackId = com.iji.app.data.repository.CategoryDefaults.fallbackId(type)
        return catalog.firstOrNull { it.id == fallbackId }?.id
            ?: catalog.firstOrNull { it.name == "其他" }?.id
            ?: catalog.firstOrNull()?.id
            ?: ""
    }
}

/**
 * 标记疑似重复。
 *
 * **计数式判重**：库里有 N 笔同键账单时，只把 N 笔同键的识别结果标为重复 —— 否则
 * 「同一天买两杯一样的咖啡」里第二杯会被无脑吞掉。
 *
 * 判重键用 `日期 + 收支类型 + 金额`：截屏导入时账户/备注的文本与库里很难逐字一致，
 * 把它们纳入键会让判重失效（同一笔永远匹配不上）。
 */
fun markDuplicates(drafts: List<BillDraft>, existing: List<Transaction>): List<BillDraft> {
    fun key(date: LocalDate, type: TransactionType, cents: Long) = "$date|$type|$cents"

    // 同键的已有账单 id 池，命中即取走一个
    val pool: MutableMap<String, MutableList<Long>> = existing
        .groupBy { key(it.date, it.type, it.amountCents) }
        .mapValues { (_, list) -> list.map { it.id }.toMutableList() }
        .toMutableMap()

    return drafts.map { draft ->
        val bucket = pool[key(draft.date, draft.type, draft.amountCents)]
        if (!bucket.isNullOrEmpty()) {
            val matched = bucket.removeAt(0)
            draft.copy(selected = false, duplicateOfId = matched)
        } else {
            draft
        }
    }
}

/**
 * 视觉模型单笔结果 → 导入预览草稿。
 * 分类名对不上用户真实分类表时落该类型的「其他」（[CategoryDefaults.fallbackId]），改分类在预览页做。
 */
fun AiBookkeepingService.BillResult.toImportDraft(today: LocalDate = LocalDate.now()): BillDraft {
    val type = when (type) {
        "income" -> TransactionType.INCOME
        "transfer" -> TransactionType.TRANSFER
        else -> TransactionType.EXPENSE
    }
    val catalog = if (type == TransactionType.INCOME) CategoryCatalog.incomes else CategoryCatalog.expenses
    return BillDraft(
        type = type,
        categoryId = catalog.firstOrNull { it.name == categoryHint.trim() }?.id
            ?: CategoryDefaults.fallbackId(type),
        amountCents = amountCents,
        note = note,
        account = accountHint,
        date = today,
    )
}
