package com.iji.app.data.repository

import com.iji.app.data.db.LedgerDao
import com.iji.app.data.db.LedgerEntity
import com.iji.app.data.model.Currency
import com.iji.app.data.db.LedgerMergeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 账本仓库：Room 本地持久化（生产环境）。
 * dao 为 null 时回退到内存实现，仅用于 Compose 预览等无数据库场景。
 */
class LedgerRepository(private val dao: LedgerDao?) {

    private val memory = MutableStateFlow(defaultLedgers())

    /**
     * 全部账本实体（含币种），Flow 自动响应数据库变化。
     *
     * 币种是账本级配置，展示层要知道「当前账本用什么符号」就必须拿实体而不是只拿名字，
     * 因此额外暴露这一条；内存兜底（预览）时一律按 CNY。
     */
    val all: Flow<List<LedgerEntity>> =
        dao?.observeAll() ?: memory.map { list -> list.map { LedgerEntity(it) } }

    /** 全部账本名（Flow 自动响应数据库变化） */
    val names: Flow<List<String>> =
        all.map { list -> list.map { it.name } }

    /** 首次启动（空表）写入默认账本 */
    suspend fun seedIfEmpty() {
        val d = dao ?: return
        if (d.count() == 0) {
            defaultLedgers().forEachIndexed { index, name ->
                // Ids.next() 严格单调递增：同一毫秒内插入多个账本也不会撞 createdAt，
                // 种子账本因此保持「默认账本 → 旅行 → 工作」的既定顺序。
                d.insert(LedgerEntity(name, sortOrder = index, createdAt = Ids.next()))
            }
        }
    }

    suspend fun add(name: String, currency: String = Currency.CNY.code) {
        val n = name.trim()
        if (n.isBlank()) return
        val d = dao
        if (d != null) {
            // createdAt 决定账本在滚轮 / 下拉里的位置：新账本永远排在最后。
            // 注意别再写 sortOrder = Int.MAX_VALUE —— 那样新账本之间只能按名称排，
            // 显示顺序会与创建先后脱节（见 MIGRATION_17_18）。
            d.insert(
                LedgerEntity(
                    n,
                    sortOrder = Int.MAX_VALUE,
                    createdAt = Ids.next(),
                    currency = Currency.of(currency).code,
                ),
            )
        } else {
            if (!memory.value.contains(n)) memory.value = memory.value + n
        }
    }

    /** 账本币种（ISO 代码）；账本不存在 / 内存兜底时回落 CNY */
    suspend fun currencyOf(name: String): String =
        dao?.currencyOf(name) ?: Currency.CNY.code

    /**
     * 改账本币种。
     *
     * 只改展示符号，**不动任何账单金额、不做汇率换算** —— 把一本账的币种从 CNY 改成 USD
     * 是「当初这些数字记的就是美元」，而不是「把人民币折算成美元」。
     */
    suspend fun setCurrency(name: String, code: String) {
        dao?.updateCurrency(name, Currency.of(code).code)
    }

    suspend fun remove(name: String) {
        val d = dao
        if (d != null) {
            d.delete(LedgerEntity(name))
        } else {
            memory.value = memory.value.filter { it != name }
        }
    }

    /** 账本下的账单数（删除前确认用） */
    suspend fun transactionCount(name: String): Int = dao?.transactionCount(name) ?: 0

    /**
     * 重命名账本，**级联更新** transactions / budgets / recurring_rules 的引用。
     *
     * 账本以名称为主键，改名若不级联，该账本的数据会立刻「查不到」——
     * 表现为用户的账单凭空消失，这是最容易被误认为数据丢失的坑。
     *
     * @return false 表示新名称为空、与原名相同或已存在
     */
    suspend fun rename(oldName: String, newName: String): Boolean {
        val n = newName.trim()
        if (dao == null) {
            if (n.isBlank() || n == oldName || memory.value.contains(n)) return false
            memory.value = memory.value.map { if (it == oldName) n else it }
            return true
        }
        if (n.isBlank() || n == oldName) return false
        if (dao.exists(n) > 0) return false
        dao.renameWithCascade(oldName, n)
        return true
    }

    /**
     * 删除**空**账本（连带其预算与周期规则）。
     *
     * 有账单的账本一律拒绝删除：账本一旦删除，其账单就成了无主数据。
     * 这种账本应改用「合并到其他账本」处理。
     *
     * @return false 表示账本内仍有账单
     */
    suspend fun deleteIfEmpty(name: String): Boolean {
        if (dao == null) {
            memory.value = memory.value.filter { it != name }
            return true
        }
        return dao.deleteIfEmpty(name)
    }

    /**
     * 把 [from] 整个并入 [to]，然后删掉 [from]。
     *
     * 这是「有账单的账本」唯一的安全出路：直接删除会让账单变成无主数据，
     * 所以删除入口对有账单的账本一律拒绝，引导用户来这里。
     *
     * @return null 表示两端相同 / 有任意一端不存在；否则返回搬走的明细
     */
    suspend fun merge(from: String, to: String): LedgerMergeResult? {
        val d = dao ?: run {
            if (from == to || !memory.value.contains(from) || !memory.value.contains(to)) return null
            memory.value = memory.value.filter { it != from }
            return LedgerMergeResult()
        }
        return d.mergeInto(from, to)
    }

    /** 默认账本：与首页设计稿一致 */
    private fun defaultLedgers(): List<String> = listOf("默认账本", "旅行", "工作")
}
