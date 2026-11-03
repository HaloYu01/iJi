package com.iji.app.data.model

/**
 * 账户分类：用于「添加账户」页组织图标选择与后续资产分组展示。
 *
 * 扩展方式：新增分类只需在枚举里加一项并补充关键词列表，无需改其它代码；
 * 匹配按声明顺序优先级（信用类最优先，避免「微信分付」被误判为网银）。
 *
 * priorityKeywords：该分类下常用项的前置排序关键词（按先后顺序排最前），
 * 未命中的名称保持原有顺序排在后面。
 */
enum class AccountType(
    val label: String,
    val keywords: List<String>,
    val priorityKeywords: List<String> = emptyList(),
) {
    ALL("全部", emptyList()),
    CASH("现金", listOf("现金")),
    BANK(
        "银行",
        listOf("银行", "銀行", "储蓄", "儲蓄", "信用社", "信合", "农信"),
        listOf(
            "邮政", "农业", "中国银行", "建设", "招商",
            "工商", "交通",
            "中信", "浦发", "民生", "兴业", "光大", "平安", "广发", "华夏", "浙商", "渤海",
        ),
    ),
    ONLINE(
        "网银",
        listOf("微信", "支付宝", "云闪付", "钱包", "数字人民币", "京东"),
        listOf("微信", "支付宝", "云闪付", "QQ钱包", "华为钱包", "京东", "数字人民币"),
    ),
    RECHARGE("充值", listOf("话费", "公交卡", "饭卡", "加油卡", "会员卡", "充值", "水电")),
    INVEST("投资", listOf("股票", "基金", "理财", "黄金", "债券", "外汇", "期货", "公积金", "医保", "零钱通", "固定收益", "余额宝")),
    CREDIT("信用", listOf("信用卡", "花呗", "借呗", "白条", "月付", "分付")),
    OTHER("其他", emptyList()),
    ;

    companion object {

        /** 参与图标归类匹配的分类（ALL / OTHER 不参与，命中失败归 OTHER） */
        private val MATCHABLE = listOf(CREDIT, CASH, BANK, ONLINE, INVEST, RECHARGE)

        /** 按图标名判断所属分类：先特定词（信用），再通用词，无命中归「其他」 */
        fun typeOf(iconName: String): AccountType {
            for (t in MATCHABLE) {
                if (t.keywords.any { iconName.contains(it) }) return t
            }
            return OTHER
        }

        /**
         * 按分类优先级稳定排序：命中 priorityKeywords 的名称按关键词顺序排最前，
         * 未命中的保持传入顺序排后面。
         */
        /**
         * 旧版本账户的 iconKey 是英文（wechat / alipay / icbc …），
         * 判分类前必须先映射成中文名，否则会被一律判成「其他」。
         */
        private val LEGACY_KEY_LABEL = mapOf(
            "wechat" to "微信", "alipay" to "支付宝",
            "icbc" to "工商银行", "ccb" to "建设银行", "abc" to "农业银行",
            "boc" to "中国银行", "bocom" to "交通银行", "cmb" to "招商银行",
            "psbc" to "邮储银行", "citic" to "中信银行",
            "spdb" to "浦发银行", "cmbc" to "民生银行",
        )

        /**
         * 判定账户所属分类（**存量数据兼容**）：
         * 1. 有持久化的 [category] 则直接采用（升级后新存的账户）；
         * 2. 否则按「图标名（英文 iconKey 先映射中文）→ 账户名」逐级推断，
         *    保证旧账户升级后分类不丢、不误判为「其他」。
         */
        fun ofAccount(category: String, iconKey: String, name: String): AccountType {
            if (category.isNotBlank()) {
                runCatching { valueOf(category) }.getOrNull()?.let { return it }
            }
            val cn = LEGACY_KEY_LABEL[iconKey] ?: iconKey
            val byIcon = typeOf(cn)
            if (byIcon != OTHER) return byIcon
            return typeOf(name)
        }

        fun sortByPriority(names: List<String>, priorityKeywords: List<String>): List<String> {
            if (priorityKeywords.isEmpty() || names.size <= 1) return names
            val rank = HashMap<String, Int>()
            priorityKeywords.forEachIndexed { i, k -> rank.putIfAbsent(k, i) }
            return names.withIndex().sortedWith(
                compareBy(
                    { (_, name) ->
                        var best = Int.MAX_VALUE
                        for ((i, k) in priorityKeywords.withIndex()) {
                            if (name.contains(k)) { best = i; break }
                        }
                        best
                    },
                    { it.index },
                ),
            ).map { it.value }
        }
    }
}
