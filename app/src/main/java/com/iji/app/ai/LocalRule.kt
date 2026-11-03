package com.iji.app.ai

import java.util.Locale

/**
 * 一条**本地识别规则**：命中关键词 → 落指定分类 / 账户，全程离线、零成本。
 *
 * 为什么把它变成数据而不是继续写在代码里：
 * - 原来 `AiBookkeepingService.ruleBasedParse` 的分类是一串硬编码的 `when(regex)`，
 *   用户没法告诉它「我这里的奶茶要算饮品」「加油要算交通」；现在规则可增删改、可停用；
 * - 「本地能用」是这套识别的前提（没有 Key 的用户也要能用），所以规则必须**可导出备份**，
 *   否则换机就丢。
 *
 * 命中优先级（见 [LocalRuleEngine.match]）：命中关键词数多者优先 → 最长关键词更长者优先 →
 * **用户规则优先于内置规则** → 列表顺序靠前者优先。
 */
data class LocalRule(
    val id: String,
    /** 规则名（列表左上角），如「餐饮」「工资」 */
    val name: String,
    /** [TYPE_EXPENSE] / [TYPE_INCOME] / [TYPE_ANY]（不限） */
    val type: String = TYPE_EXPENSE,
    /** 命中后落的分类名：必须能在分类表里找到，找不到时记账面板会提示手动选 */
    val category: String = "其他",
    /** 命中后预填的账户名；留空 = 不预填 */
    val account: String = "",
    /** 关键词：命中**任意一个**即算匹配（不区分大小写） */
    val keywords: List<String> = emptyList(),
    val enabled: Boolean = true,
    /** 内置规则：可删可改，但「恢复内置规则」能再补回来 */
    val builtin: Boolean = false,
) {
    /** 列表里的一句话状态：`8 词 → 餐饮`；收入/不限类型才标注，配了账户才追加「预填」 */
    fun summary(): String {
        val count = if (keywords.isEmpty()) "未设关键词" else "${keywords.size} 词"
        val typeMark = when (type) {
            TYPE_INCOME -> " · 收入"
            TYPE_ANY -> " · 不限"
            else -> ""
        }
        val accountText = if (account.isBlank()) "" else " · 预填$account"
        return "$count$typeMark → $category$accountText"
    }

    companion object {
        const val TYPE_EXPENSE = "expense"
        const val TYPE_INCOME = "income"
        const val TYPE_ANY = "any"

        fun typeLabel(type: String): String = when (type) {
            TYPE_INCOME -> "收入"
            TYPE_ANY -> "不限"
            else -> "支出"
        }
    }
}

/** 引擎命中结果 */
data class LocalRuleMatch(
    val rule: LocalRule,
    /** 命中的关键词个数（用于排序，也用于界面回显「命中 N 个词」） */
    val hits: Int,
)

/**
 * 本地规则引擎（纯函数，可单测）。
 *
 * 输入是**一条待识别的文本**（用户输入 / 通知原文 / 小票 OCR 出的整段文字），
 * 输出命中的规则；金额不在这里解析——金额有独立的「元 / 块 / ¥」解析器，
 * 二者互不干扰：规则决定"记到哪一类"，金额决定"记多少钱"。
 */
object LocalRuleEngine {

    /**
     * 收入语义词：命中即按收入识别。
     *
     * 与旧版 `ruleBasedParse` 的 `工资|收入|红包|报销|理财` 保持同一口径，
     * 只补了「到账 / 入账」两个明确的资金流入词。**不要**把「退款」加进来：
     * iji 的退款是"负数支出"（见 AddTransactionSheet），算成收入会把净支出统计弄反。
     */
    private val INCOME_WORDS = listOf("工资", "收入", "红包", "报销", "理财", "到账", "入账")

    /** 文本看起来是收入还是支出（规则过滤 + 界面默认值都用它） */
    fun detectType(text: String): String =
        if (INCOME_WORDS.any { text.contains(it) }) LocalRule.TYPE_INCOME else LocalRule.TYPE_EXPENSE

    /**
     * 在 [rules] 里找最匹配的一条；没有命中返回 null。
     *
     * 先按收支过滤（[LocalRule.TYPE_ANY] 永远参与），再按命中强度排序：
     * 命中词数 > 最长命中词长度 > 用户规则优先 > 配置顺序。
     * 「最长命中词」是区分「药」（医疗）与「药店会员卡充值」这类长词的精度的关键。
     */
    fun match(text: String, rules: List<LocalRule>): LocalRuleMatch? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase(Locale.ROOT)
        val wanted = detectType(trimmed)

        return rules.asSequence()
            .filter { it.enabled && it.keywords.isNotEmpty() }
            .filter { it.type == LocalRule.TYPE_ANY || it.type == wanted }
            .mapNotNull { rule ->
                val hitKeywords = rule.keywords
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && lower.contains(it.lowercase(Locale.ROOT)) }
                if (hitKeywords.isEmpty()) null
                else Triple(rule, hitKeywords.size, hitKeywords.maxOf { it.length })
            }
            .sortedWith(
                compareByDescending<Triple<LocalRule, Int, Int>> { it.second }
                    .thenByDescending { it.third }
                    .thenBy { if (it.first.builtin) 1 else 0 }
            )
            .map { LocalRuleMatch(it.first, it.second) }
            .firstOrNull()
    }

    /**
     * 编辑框文本 → 关键词列表：按换行 / 逗号 / 顿号 / 分号切分，去空去重（保持输入顺序）。
     *
     * 允许用户混着写：`地铁, 公交\n打车` 与一行一个等价。
     */
    fun keywordsOf(raw: String): List<String> =
        raw.split('\n', ',', '，', '、', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** 关键词列表 → 编辑框文本（一行一个） */
    fun keywordsText(keywords: List<String>): String = keywords.joinToString("\n")
}

/**
 * 内置规则：与旧版硬编码分类**逐条对齐**（餐饮 / 交通 / 购物 / 居住 / 娱乐 / 医疗 / 收入四类），
 * 另外补了教育、人情、旅行三组——旧版这些词会落到「其他」，用户抱怨最多的就是这里。
 *
 * 分类名用的是出厂分类名（`CategoryDefaults`）；用户改过分类名后，规则里的旧名会命中不到，
 * 记账面板会明确提示「未识别分类」，而不是静默记到别的分类上。
 */
object LocalRuleDefaults {

    val all: List<LocalRule> = listOf(
        LocalRule(
            id = "builtin-food",
            name = "餐饮",
            category = "餐饮",
            keywords = listOf(
                "饭", "吃饭", "餐", "外卖", "奶茶", "咖啡", "早餐", "午餐", "晚餐",
                "夜宵", "零食", "火锅", "烧烤", "食堂", "美团", "饿了么",
            ),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-transport",
            name = "交通",
            category = "交通",
            keywords = listOf(
                "地铁", "公交", "打车", "滴滴", "出租车", "加油", "高铁", "火车",
                "飞机", "停车", "高速", "充电桩", "共享单车",
            ),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-shopping",
            name = "购物",
            category = "购物",
            keywords = listOf("购物", "淘宝", "天猫", "京东", "拼多多", "超市", "日用品", "衣服", "鞋"),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-home",
            name = "居住",
            category = "居住",
            keywords = listOf("房租", "水电", "电费", "水费", "燃气", "宽带", "物业"),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-fun",
            name = "娱乐",
            category = "娱乐",
            keywords = listOf("电影", "游戏", "娱乐", "健身", "KTV", "演唱会"),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-medical",
            name = "医疗",
            category = "医疗",
            keywords = listOf("药", "医院", "看病", "挂号", "体检", "门诊"),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-education",
            name = "教育",
            category = "教育",
            keywords = listOf("学费", "培训", "课程", "书本", "教材", "补习"),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-social",
            name = "人情",
            category = "人情",
            keywords = listOf("请客", "礼物", "份子钱", "随礼", "礼金"),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-travel",
            name = "旅行",
            category = "旅行",
            keywords = listOf("酒店", "民宿", "机票", "门票", "景点", "旅游", "旅行"),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-salary",
            name = "工资",
            type = LocalRule.TYPE_INCOME,
            category = "工资",
            keywords = listOf("工资", "薪资", "发薪", "奖金", "津贴", "补助"),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-redpacket",
            name = "红包",
            type = LocalRule.TYPE_INCOME,
            category = "红包",
            keywords = listOf("红包", "压岁钱", "收钱"),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-reimburse",
            name = "报销",
            type = LocalRule.TYPE_INCOME,
            category = "报销",
            keywords = listOf("报销", "补贴", "赔付", "理赔"),
            builtin = true,
        ),
        LocalRule(
            id = "builtin-invest",
            name = "理财",
            type = LocalRule.TYPE_INCOME,
            category = "理财",
            keywords = listOf("理财", "利息", "分红", "收益", "基金"),
            builtin = true,
        ),
    )
}
