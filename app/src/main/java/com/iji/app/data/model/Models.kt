package com.iji.app.data.model

import java.time.LocalDate

/** 收支类型 */
enum class TransactionType {
    EXPENSE, INCOME, TRANSFER
}

/**
 * 记账分类。
 * 颜色以 Long 形式存 0xAARRGGBB，UI 层负责渲染；图标按 categoryId 在 UI 层映射。
 * parentId 为空表示一级分类，有值表示该分类是某个一级分类的子分类。
 */
data class Category(
    val id: String,
    val name: String,
    val color: Long,
    val type: TransactionType,
    val parentId: String? = null,
    /**
     * 同层级内的展示顺序（一级分类、每个父下的二级分类各自从 0 连续编号）。
     * 排序只改这个字段，**id 永远不变**，因此账单 / 预算 / 周期规则的引用不会因排序失效。
     */
    val sortOrder: Int = 0,
    /**
     * 是否为「出厂分类」。**只用于「恢复默认分类」时决定初始集合**，
     * 不再作为编辑/删除的权限门槛——出厂分类同样可改名、可删除。
     */
    val builtin: Boolean = false,
    /**
     * 系统语义标记（如 `borrow` / `lend` / `transfer`）。
     *
     * 借贷等业务过去靠 id 前缀（`lend-` / `borrow-`）判断，用户改名不影响、但重建分类就会断。
     * 现在优先看该标记（子分类向上继承父级语义）。
     */
    val semantic: String? = null,
)

/**
 * 一笔账单。金额以「分」存储，避免浮点误差；
 * 账户为内置账户之一，标签为可自定义的字符串列表。
 */
/**
 * 垫付 / 借款状态：用于应收应付追踪（最容易丢钱的一段）。
 *
 * - [NONE]：普通交易，不涉及应收应付（**在借入 / 借出分类下按 [PENDING] 对待**，
 *   见 `ui/assets/BorrowOrderFlow.kt`：用户不一定会在「更多」里补填状态）
 * - [PENDING]：钱已付出但尚未收回（垫付、借出），或借入的钱尚未归还，计入「待收回 / 待归还」
 * - [PENDING_CONFIRM]：一方已经还款 / 收款，等待另一方确认。借出时 = 对方已还、等我确认到账；
 *   借入时 = 我已归还、等对方确认。确认后落 [SETTLED]，撤销则回到 [PENDING]
 * - [SETTLED]：款项已收回 / 已归还 / 已报销到账（核销）
 */
enum class ReimburseState {
    NONE,
    PENDING,
    PENDING_CONFIRM,
    SETTLED,
    ;

    /** 钱还没真正结清：待收回 / 待归还 / 待确认都算未结清（汇总与分组用这个口径） */
    val isOpen: Boolean get() = this == PENDING || this == PENDING_CONFIRM
}

data class Transaction(
    val id: Long,
    val type: TransactionType,
    val categoryId: String,
    val amountCents: Long,
    val note: String = "",
    val account: String = "",
    val tags: List<String> = emptyList(),
    val date: LocalDate = LocalDate.now(),
    val ledgerId: String = "默认账本",
    /**
     * 商家 / 收款方。**与 note 分开存**：note 是自由文本，无法稳定聚合；
     * 有了这个字段才能回答"今年在星巴克花了多少"这类消费归因问题。
     */
    val merchant: String = "",
    /** 是否已与银行/支付账单核对过（对账用） */
    val reconciled: Boolean = false,
    /** 垫付 / 借款状态（[ReimburseState]） */
    val reimburseState: ReimburseState = ReimburseState.NONE,
    /** 对方：借款人 / 被垫付人 / 报销归属项目，用于按人归集与核销 */
    val counterparty: String = "",
    /** 不计入收支统计（如报销款、转账、内部调拨等） */
    val excludeFromStats: Boolean = false,
    /** 不计入预算（仅影响预算进度，仍计入收支统计） */
    val excludeFromBudget: Boolean = false,
    /** 报销标记：归入报销列便于后续核对 */
    val reimburse: Boolean = false,
    /** 优惠金额（分）；实付 = amountCents，原价 = amountCents + discountCents */
    val discountCents: Long = 0L,
    /** 附件图片 URI 列表（持久化权限后保存） */
    val attachments: List<String> = emptyList(),
    /**
     * 转账的**转入账户**；仅 [TransactionType.TRANSFER] 有值，[account] 即转出账户。
     *
     * 放在末尾是为了不打断既有位置参数调用方（mock 数据、历史构造）。
     */
    val transferToAccount: String = "",
    /**
     * 组合付款的拆分明细；非空即表示这笔账由**多个账户**共同支付（如信用卡 + 储蓄卡分摊）。
     *
     * 此时 [account] 退化为「首选账户」（约定等于 `splits.first().account`），仅用于列表展示；
     * 余额联动以本列表为准，不再重复计入 [account]。
     * 各分账金额之和应当等于 [amountCents]（由写入方保证）。
     */
    val splits: List<PaymentSplit> = emptyList(),
    /**
     * 本笔退款所关联的**原消费账单 id**；0 表示不是退款或未关联。
     *
     * 退款在数据上就是一笔负数支出（账户收钱、统计冲减、报销应收自动抵扣），
     * 这个字段**不参与任何金额聚合**，纯粹用于溯源自哪个账单，
     * 以便在列表上显示「这笔消费的净成本」。
     * 账单 id 由时间戳生成，永远不会是 0，故 0 可安全表示「无关联」。
     */
    val refundOfId: Long = 0L,
    /**
     * 对方联系方式（电话 / 微信…）。与 [counterparty] 一起构成借入借出的「对方资料」。
     *
     * 放在末尾是为了不打断既有位置参数调用方（mock 数据、历史构造）。
     */
    val counterpartyContact: String = "",
    /** 对方单位名称（借入借出的「对方资料」） */
    val counterpartyOrg: String = "",
    /**
     * 当天的第几分钟（0..1439）；**-1 = 未指定时间**，表示只记到「天」。
     *
     * 记账面板多数场景只需要日期（识屏、语音、手动记一笔），硬塞一个时间反而是噪声；
     * 所以默认 -1，只有明确选过时分的入口（识屏详细卡片）才写入。
     */
    val minuteOfDay: Int = -1,
)

/**
 * 组合付款的单笔分账：从 [account] 支出（或收入）[amountCents] 分。
 *
 * 账户仍以**名称**为键，与项目现有做法一致（内置账户是常量、资产账户用名称关联）。
 */
data class PaymentSplit(
    val account: String = "",
    val amountCents: Long = 0L,
)

/** 内置账户（记账面板可选账户，简表） */
object Accounts {
    val all = listOf("现金", "银行卡", "支付宝", "微信")
}

/**
 * 资产账户（资产页）：微信 / 支付宝 / 主流银行等。
 * iconKey 对应 AccountCatalog 中的图标类型；金额以「分」存储。
 */
/**
 * 资产账户（资产页）。
 * [category] 为 [AccountType] 的枚举名（BANK / ONLINE …），**存量账户为空串**，
 * 判分类一律走 `AccountType.ofAccount(category, iconKey, name)`，别直接当分类用。
 */
data class Account(
    val id: Long,
    val name: String,
    val subName: String = "",
    val iconKey: String,
    val balanceCents: Long = 0L,
    val includeInNetWorth: Boolean = true,
    val category: String = "",
    val hidden: Boolean = false,
    /** 同类别内的展示顺序（资产页拖动排序的结果）；新建账户统一排最后 */
    val sortOrder: Int = Int.MAX_VALUE,
)

/** 内置常用标签（记账面板可多选，也支持自定义输入） */
object PresetTags {
    val all = listOf("餐饮", "交通", "购物", "工作", "学习", "生活", "旅行", "人情", "医疗", "娱乐")

    /** 标签默认颜色映射 */
    val colors = mapOf(
        "餐饮" to 0xFFFF6B35,
        "交通" to 0xFF3B82F6,
        "购物" to 0xFF8B5CF6,
        "工作" to 0xFF6366F1,
        "学习" to 0xFF06B6D4,
        "生活" to 0xFF10B981,
        "旅行" to 0xFF0EA5E9,
        "人情" to 0xFFF43F5E,
        "医疗" to 0xFFEF4444,
        "娱乐" to 0xFFEC4899,
    )

    /** 获取标签颜色，未设置返回灰色 */
    fun colorOf(tag: String): Long = colors[tag] ?: 0xFF9CA3AF
}

/** 周期记账频率 */
enum class RecurrenceFreq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** 周期记账规则：自动按频率生成交易 */
data class RecurringRule(
    val id: Long = 0L,
    val type: TransactionType,
    val categoryId: String,
    val amountCents: Long,
    val note: String = "",
    val account: String = "",
    val freq: RecurrenceFreq,
    val nextRunDate: LocalDate, // 下次自动生成日期
    val enabled: Boolean = true,
    /**
     * 规则所属账本。**必须有**：否则"每月房租"会在生成时落到当时打开的账本，
     * 房租被记到工作账本这类错位就是这么来的。
     */
    val ledgerId: String = "默认账本",
)
