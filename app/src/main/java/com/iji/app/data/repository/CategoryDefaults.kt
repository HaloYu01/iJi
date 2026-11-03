package com.iji.app.data.repository

import com.iji.app.data.model.Category
import com.iji.app.data.model.TransactionType

/**
 * 出厂分类目录（旧版里硬编码在 `CategoryCatalog` 里的那批常量）。
 *
 * **现在只是"种子数据"**：首次启动时被写进 [CategoryStore]，之后与用户自建分类完全同权，
 * 可改名、可删除、可排序；[Category.builtin] 仅用于「恢复默认分类」时重建这批初始内容。
 *
 * 两个与旧版不同的地方（都是修 bug，不是行为变化）：
 * 1. 收入的「其他」id 由 `other` 改为 `other-income` —— 旧版支出/收入共用 `other`，
 *    索引去重时后者被丢弃，导致改收入「其他」实际改到支出那条（DB v22 已迁移引用）。
 * 2. `lend` / `borrow` / `transfer` 带上 [Category.semantic]，业务判定不再依赖 id 前缀。
 */
object CategoryDefaults {

    private const val C_EXPENSE_FOOD = 0xFFFF6B35
    private const val C_EXPENSE_TRANSPORT = 0xFF3B82F6
    private const val C_EXPENSE_SHOPPING = 0xFF8B5CF6
    private const val C_EXPENSE_HOME = 0xFF10B981
    private const val C_EXPENSE_FUN = 0xFFEC4899
    private const val C_EXPENSE_MEDICAL = 0xFFEF4444
    private const val C_EXPENSE_SOCIAL = 0xFFF43F5E
    private const val C_EXPENSE_EDUCATION = 0xFF6366F1
    private const val C_EXPENSE_TRAVEL = 0xFF06B6D4
    private const val C_BORROW_LEND = 0xFFF59E0B
    private const val C_NEUTRAL = 0xFF9CA3AF

    private const val C_INCOME_SALARY = 0xFF10B981
    private const val C_INCOME_INVEST = 0xFF3B82F6
    private const val C_INCOME_REDPACKET = 0xFFEF4444
    private const val C_INCOME_REIMBURSE = 0xFF8B5CF6

    /** 借出 / 借入的语义标记：资产页借此判断方向，用户改名/搬父级都不会失效 */
    const val SEMANTIC_LEND = "lend"
    const val SEMANTIC_BORROW = "borrow"
    const val SEMANTIC_TRANSFER = "transfer"

    private val expenses = listOf(
        Category("food", "餐饮", C_EXPENSE_FOOD, TransactionType.EXPENSE),
        Category("food-meal", "正餐", C_EXPENSE_FOOD, TransactionType.EXPENSE, parentId = "food"),
        Category("food-snack", "零食", C_EXPENSE_FOOD, TransactionType.EXPENSE, parentId = "food"),
        Category("food-drink", "饮品", C_EXPENSE_FOOD, TransactionType.EXPENSE, parentId = "food"),
        Category("food-grocery", "食材", C_EXPENSE_FOOD, TransactionType.EXPENSE, parentId = "food"),
        Category("food-breakfast", "早餐", C_EXPENSE_FOOD, TransactionType.EXPENSE, parentId = "food"),
        Category("food-takeout", "外卖", C_EXPENSE_FOOD, TransactionType.EXPENSE, parentId = "food"),
        Category("food-fruit", "水果", C_EXPENSE_FOOD, TransactionType.EXPENSE, parentId = "food"),
        Category("food-midnight", "夜宵", C_EXPENSE_FOOD, TransactionType.EXPENSE, parentId = "food"),
        Category("transport", "交通", C_EXPENSE_TRANSPORT, TransactionType.EXPENSE),
        Category("transport-subway", "地铁", C_EXPENSE_TRANSPORT, TransactionType.EXPENSE, parentId = "transport"),
        Category("transport-bus", "公交", C_EXPENSE_TRANSPORT, TransactionType.EXPENSE, parentId = "transport"),
        Category("transport-taxi", "打车", C_EXPENSE_TRANSPORT, TransactionType.EXPENSE, parentId = "transport"),
        Category("transport-fuel", "加油", C_EXPENSE_TRANSPORT, TransactionType.EXPENSE, parentId = "transport"),
        Category("transport-train", "火车", C_EXPENSE_TRANSPORT, TransactionType.EXPENSE, parentId = "transport"),
        Category("transport-hsr", "高铁", C_EXPENSE_TRANSPORT, TransactionType.EXPENSE, parentId = "transport"),
        Category("transport-plane", "飞机", C_EXPENSE_TRANSPORT, TransactionType.EXPENSE, parentId = "transport"),
        Category("transport-park", "停车", C_EXPENSE_TRANSPORT, TransactionType.EXPENSE, parentId = "transport"),
        Category("shopping", "购物", C_EXPENSE_SHOPPING, TransactionType.EXPENSE),
        Category("shopping-clothes", "服饰", C_EXPENSE_SHOPPING, TransactionType.EXPENSE, parentId = "shopping"),
        Category("shopping-daily", "日用品", C_EXPENSE_SHOPPING, TransactionType.EXPENSE, parentId = "shopping"),
        Category("shopping-digital", "数码", C_EXPENSE_SHOPPING, TransactionType.EXPENSE, parentId = "shopping"),
        Category("shopping-beauty", "美妆", C_EXPENSE_SHOPPING, TransactionType.EXPENSE, parentId = "shopping"),
        Category("shopping-bag", "鞋包", C_EXPENSE_SHOPPING, TransactionType.EXPENSE, parentId = "shopping"),
        Category("shopping-baby", "母婴", C_EXPENSE_SHOPPING, TransactionType.EXPENSE, parentId = "shopping"),
        Category("shopping-book", "书籍", C_EXPENSE_SHOPPING, TransactionType.EXPENSE, parentId = "shopping"),
        Category("home", "居住", C_EXPENSE_HOME, TransactionType.EXPENSE),
        Category("home-rent", "房租", C_EXPENSE_HOME, TransactionType.EXPENSE, parentId = "home"),
        Category("home-util", "水电煤", C_EXPENSE_HOME, TransactionType.EXPENSE, parentId = "home"),
        Category("home-network", "宽带", C_EXPENSE_HOME, TransactionType.EXPENSE, parentId = "home"),
        Category("home-property", "物业", C_EXPENSE_HOME, TransactionType.EXPENSE, parentId = "home"),
        Category("home-repair", "维修", C_EXPENSE_HOME, TransactionType.EXPENSE, parentId = "home"),
        Category("home-housekeeping", "家政", C_EXPENSE_HOME, TransactionType.EXPENSE, parentId = "home"),
        Category("fun", "娱乐", C_EXPENSE_FUN, TransactionType.EXPENSE),
        Category("fun-movie", "电影", C_EXPENSE_FUN, TransactionType.EXPENSE, parentId = "fun"),
        Category("fun-game", "游戏", C_EXPENSE_FUN, TransactionType.EXPENSE, parentId = "fun"),
        Category("fun-travel", "游玩", C_EXPENSE_FUN, TransactionType.EXPENSE, parentId = "fun"),
        Category("fun-ktv", "KTV", C_EXPENSE_FUN, TransactionType.EXPENSE, parentId = "fun"),
        Category("fun-show", "演出", C_EXPENSE_FUN, TransactionType.EXPENSE, parentId = "fun"),
        Category("fun-sport", "运动", C_EXPENSE_FUN, TransactionType.EXPENSE, parentId = "fun"),
        Category("medical", "医疗", C_EXPENSE_MEDICAL, TransactionType.EXPENSE),
        Category("medical-outpatient", "门诊", C_EXPENSE_MEDICAL, TransactionType.EXPENSE, parentId = "medical"),
        Category("medical-drug", "药品", C_EXPENSE_MEDICAL, TransactionType.EXPENSE, parentId = "medical"),
        Category("medical-hospital", "住院", C_EXPENSE_MEDICAL, TransactionType.EXPENSE, parentId = "medical"),
        Category("medical-health", "保健", C_EXPENSE_MEDICAL, TransactionType.EXPENSE, parentId = "medical"),
        Category("social", "人情", C_EXPENSE_SOCIAL, TransactionType.EXPENSE),
        Category("social-treat", "请客", C_EXPENSE_SOCIAL, TransactionType.EXPENSE, parentId = "social"),
        Category("social-gift", "礼物", C_EXPENSE_SOCIAL, TransactionType.EXPENSE, parentId = "social"),
        Category("social-wedding", "喜事", C_EXPENSE_SOCIAL, TransactionType.EXPENSE, parentId = "social"),
        Category("education", "教育", C_EXPENSE_EDUCATION, TransactionType.EXPENSE),
        Category("education-tuition", "学费", C_EXPENSE_EDUCATION, TransactionType.EXPENSE, parentId = "education"),
        Category("education-book", "书本", C_EXPENSE_EDUCATION, TransactionType.EXPENSE, parentId = "education"),
        Category("education-training", "培训", C_EXPENSE_EDUCATION, TransactionType.EXPENSE, parentId = "education"),
        Category("travel", "旅行", C_EXPENSE_TRAVEL, TransactionType.EXPENSE),
        Category("travel-flight", "机票", C_EXPENSE_TRAVEL, TransactionType.EXPENSE, parentId = "travel"),
        Category("travel-hotel", "酒店", C_EXPENSE_TRAVEL, TransactionType.EXPENSE, parentId = "travel"),
        Category("travel-ticket", "门票", C_EXPENSE_TRAVEL, TransactionType.EXPENSE, parentId = "travel"),
        Category("lend", "借出", C_BORROW_LEND, TransactionType.EXPENSE, semantic = SEMANTIC_LEND),
        Category("lend-loan", "借款", C_BORROW_LEND, TransactionType.EXPENSE, parentId = "lend"),
        Category("other", "其他", C_NEUTRAL, TransactionType.EXPENSE),
    )

    private val incomes = listOf(
        Category("salary", "工资", C_INCOME_SALARY, TransactionType.INCOME),
        Category("salary-bonus", "奖金", C_INCOME_SALARY, TransactionType.INCOME, parentId = "salary"),
        Category("salary-allowance", "津贴", C_INCOME_SALARY, TransactionType.INCOME, parentId = "salary"),
        Category("invest", "理财", C_INCOME_INVEST, TransactionType.INCOME),
        Category("invest-interest", "利息", C_INCOME_INVEST, TransactionType.INCOME, parentId = "invest"),
        Category("invest-dividend", "分红", C_INCOME_INVEST, TransactionType.INCOME, parentId = "invest"),
        Category("redpacket", "红包", C_INCOME_REDPACKET, TransactionType.INCOME),
        Category("redpacket-lottery", "中奖", C_INCOME_REDPACKET, TransactionType.INCOME, parentId = "redpacket"),
        Category("reimburse", "报销", C_INCOME_REIMBURSE, TransactionType.INCOME),
        Category("borrow", "借入", C_BORROW_LEND, TransactionType.INCOME, semantic = SEMANTIC_BORROW),
        Category("other-income", "其他", C_NEUTRAL, TransactionType.INCOME),
    )

    private val transfers = listOf(
        Category("transfer", "转账", 0xFF6B7280, TransactionType.TRANSFER, semantic = SEMANTIC_TRANSFER),
    )

    /**
     * 出厂目录：三个类型各自按数组顺序编号 `sortOrder`，并统一打上 `builtin = true`。
     * 一级/二级共用一条 sortOrder 序列不影响展示——渲染时是同层级内比较。
     */
    val all: List<Category> = (expenses + incomes + transfers)
        .mapIndexed { index, category -> category.copy(sortOrder = index, builtin = true) }

    /** 「其他」的 id（删除分类时的迁移目标）；收入侧单独一个 id，见类注释 */
    fun fallbackId(type: TransactionType): String = when (type) {
        TransactionType.INCOME -> "other-income"
        else -> "other"
    }
}
