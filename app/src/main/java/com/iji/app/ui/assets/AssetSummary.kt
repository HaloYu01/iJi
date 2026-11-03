package com.iji.app.ui.assets

import com.iji.app.data.model.Account
import com.iji.app.data.model.AccountType
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.CategoryDefaults

/**
 * 资产页汇总口径（单位：分）。
 *
 * 三者满足 `净资产 = 资产 - 负债`（在其账户都计入净资产的前提下），
 * 拆开存只是为了让概览卡能分开显示「资产 / 负债」。
 */
data class AssetTotals(
    val assetsCents: Long,
    val liabilitiesCents: Long,
    val netWorthCents: Long,
)

/**
 * 计算资产页总额（纯函数，便于单测）。
 *
 * 口径（与产品确认）：
 * - 信用类账户（[AccountType.CREDIT]，含信用卡/花呗/白条…）：**余额为负时计入负债**（取绝对值）；
 *   余额为正视为溢缴，仍计资产。
 * - 其它账户：余额直接计入资产（透支成负数时会冲减资产，不做特殊处理）。
 * - 净资产：`includeInNetWorth` 账户的代数和；隐藏账户仍计入（沿用旧行为，避免隐藏后总额突变）。
 */
fun computeAssetTotals(accounts: List<Account>): AssetTotals {
    var assets = 0L
    var liabilities = 0L
    var netWorth = 0L
    for (account in accounts) {
        // 老账户 category 为空时 ofAccount 先按图标判类；「图标选了银行徽章、名字写着信用卡」很常见，
        // 这种欠款会被当成负资产。所以余额为负时再用名称兜一次（只影响本页口径，不改全局分类）。
        val isCredit = AccountType.ofAccount(
            category = account.category,
            iconKey = account.iconKey,
            name = account.name,
        ) == AccountType.CREDIT ||
            (account.balanceCents < 0 && AccountType.typeOf(account.name) == AccountType.CREDIT)
        if (isCredit && account.balanceCents < 0) {
            liabilities += -account.balanceCents
        } else {
            assets += account.balanceCents
        }
        if (account.includeInNetWorth) netWorth += account.balanceCents
    }
    return AssetTotals(assets, liabilities, netWorth)
}

/** 借入 / 借出的累计金额（分），供资产页「总借入 / 总借出」两张卡展示。 */
data class BorrowLendTotals(
    val borrowedCents: Long = 0L,
    val lentCents: Long = 0L,
)

/**
 * 借贷方向，也是借贷记录页的唯一切换维度。
 *
 * 借入 = `borrow` 分类树内的收入（借到钱），借出 = `lend` 分类树内的支出（借出钱）。
 * 匹配同时认两种凭据：分类的 `semantic` 标记（改名/搬父级都不失效）与旧的 id 前缀
 * （`borrow` / `lend` 及其子分类 `lend-`）。标记为「不计入统计」的账单不算，与资产页同口径。
 */
enum class BorrowDirection(
    val label: String,
    private val rootCategoryId: String,
    private val transactionType: TransactionType,
    private val semantic: String,
) {
    BORROW("借入", "borrow", TransactionType.INCOME, CategoryDefaults.SEMANTIC_BORROW),
    LEND("借出", "lend", TransactionType.EXPENSE, CategoryDefaults.SEMANTIC_LEND),
    ;

    fun matches(tx: Transaction): Boolean =
        !tx.excludeFromStats &&
            tx.type == transactionType &&
            matchesCategory(tx.categoryId)

    /**
     * 只看分类归属（不看类型 / 是否计入统计）：记一笔面板用它判断是否展示「对方资料」。
     *
     * 先走 id 前缀（快、无依赖，纯单测里也能过），再问目录里的语义标记
     * （用户把别的分类搬到借出下面、或自建了一个带语义的分类时生效）。
     */
    fun matchesCategory(categoryId: String): Boolean =
        categoryId.isInCategoryTree(rootCategoryId) ||
            CategoryCatalog.semanticOf(categoryId) == semantic

    companion object {
        /** 路由参数 → 方向；非法值一律回落到借入 */
        fun ofName(name: String?): BorrowDirection = entries.firstOrNull { it.name == name } ?: BORROW
    }
}

/** 分类 → 借贷方向；普通分类返回 null */
fun borrowDirectionOfCategory(categoryId: String): BorrowDirection? =
    BorrowDirection.entries.firstOrNull { it.matchesCategory(categoryId) }

/** 某个方向的全部订单：日期倒序，同日按 id 倒序（最近记的在前） */
fun borrowLendOrders(
    transactions: List<Transaction>,
    direction: BorrowDirection,
): List<Transaction> = transactions
    .filter { direction.matches(it) }
    .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })

/**
 * 累计借入 / 借出（纯函数，便于单测）。
 *
 * 是历史累计，不是「待还 / 待收回」余额——后者看每笔的 `reimburseState`。
 */
fun computeBorrowLend(transactions: List<Transaction>): BorrowLendTotals = BorrowLendTotals(
    borrowedCents = transactions.filter { BorrowDirection.BORROW.matches(it) }.sumOf { it.amountCents },
    lentCents = transactions.filter { BorrowDirection.LEND.matches(it) }.sumOf { it.amountCents },
)

/** 子分类 id 用前缀归属判断，避免为了归集再多加载一遍分类表 */
private fun String.isInCategoryTree(root: String): Boolean =
    this == root || startsWith("$root-")
