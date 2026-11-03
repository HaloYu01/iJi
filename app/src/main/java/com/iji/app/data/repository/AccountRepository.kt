package com.iji.app.data.repository

import com.iji.app.data.db.AccountDao
import com.iji.app.data.db.AccountEntity
import com.iji.app.data.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 资产账户仓库：Room 本地持久化（生产环境）。
 * dao 为 null 时回退到内存实现，仅用于 Compose 预览等无数据库场景。
 */
class AccountRepository(private val dao: AccountDao?) {

    private val memory = MutableStateFlow(defaultAccounts())

    /** 全部账户（Flow 自动响应数据库变化） */
    val accounts: Flow<List<Account>> =
        dao?.observeAll()?.map { list -> list.map { it.toModel() } } ?: memory

    /** 首次启动（空表）写入默认账户：微信 / 支付宝 */
    suspend fun seedIfEmpty() {
        val d = dao ?: return
        if (d.count() == 0) {
            defaultAccounts().forEach { d.insert(it.toEntity()) }
        }
    }

    /**
     * 新增账户，返回新账户 id。
     *
     * 返回 id 是为了让调用方能「创建完立刻定位到它」（资产页高亮新账户），
     * 不用再靠名称去猜是哪一条。
     */
    suspend fun add(
        name: String,
        subName: String,
        iconKey: String,
        balanceCents: Long,
        includeInNetWorth: Boolean = true,
        category: String = "",
        hidden: Boolean = false,
    ): Long {
        val d = dao
        val id = Ids.next()
        val entity = AccountEntity(
// 走 Ids：同一毫秒连续新增两个账户会拿到相同主键，
            // 而 AccountDao.insert 是 IGNORE 策略 → 后一个账户被静默丢弃
            id = id,
            name = name,
            subName = subName,
            iconKey = iconKey,
            balanceCents = balanceCents,
            sortOrder = Int.MAX_VALUE,
            includeInNetWorth = includeInNetWorth,
            category = category,
            hidden = hidden,
        )
        if (d != null) {
            d.insert(entity)
        } else {
            memory.value = memory.value + entity.toModel()
        }
        return id
    }

    /**
     * 是否已存在同名账户（重名检查）。
     *
     * [excludeId] 用于编辑态：允许与自己同名，但不允许与**其它**账户同名。
     * 名称按去空格后的精确匹配判定（资产页/记一笔都用账户名做键，名字必须唯一）。
     */
    suspend fun existsByName(name: String, excludeId: Long? = null): Boolean {
        val target = name.trim()
        if (target.isEmpty()) return false
        val d = dao
        return if (d != null) {
            val hit = d.getByName(target)
            hit != null && hit.id != excludeId
        } else {
            memory.value.any { it.name.trim() == target && it.id != excludeId }
        }
    }

    /** 更新账户 */
    suspend fun update(account: Account) {
        val d = dao
        if (d != null) {
            d.update(account.toEntity())
        } else {
            memory.value = memory.value.map { if (it.id == account.id) account else it }
        }
    }

    /** 删除账户 */
    suspend fun delete(account: Account) {
        val d = dao
        if (d != null) {
            d.delete(account.toEntity())
        } else {
            memory.value = memory.value.filterNot { it.id == account.id }
        }
    }

    /** 资产页拖动排序：按传入顺序重写 sortOrder（传全量展示顺序，写回 0..n-1） */
    suspend fun reorder(orderedIds: List<Long>) {
        val d = dao
        if (d != null) {
            orderedIds.forEachIndexed { index, id -> d.updateSortOrder(id, index) }
        } else {
            val byId = memory.value.associateBy { it.id }
            memory.value = orderedIds.mapIndexedNotNull { index, id ->
                byId[id]?.copy(sortOrder = index)
            } + memory.value.filter { it.id !in orderedIds }
        }
    }

    /** 调整账户余额（delta 为正负增量） */
    suspend fun adjustBalance(name: String, deltaCents: Long) {
        val d = dao
        if (d != null) {
            d.adjustBalance(name, deltaCents)
        } else {
            memory.value = memory.value.map {
                if (it.name == name) it.copy(balanceCents = it.balanceCents + deltaCents) else it
            }
        }
    }

    /** 默认账户：与资产页设计稿一致（微信钱包 / 支付宝，余额 0） */
    private fun defaultAccounts(): List<Account> = listOf(
        Account(1, "微信", "微信钱包", "wechat", 0L, sortOrder = 0),
        Account(2, "支付宝", "支付宝", "alipay", 0L, sortOrder = 1),
    )
}

private fun Account.toEntity() = AccountEntity(
    id = id,
    name = name,
    subName = subName,
    iconKey = iconKey,
    balanceCents = balanceCents,
    sortOrder = sortOrder,
    includeInNetWorth = includeInNetWorth,
    category = category,
    hidden = hidden,
)

private fun AccountEntity.toModel() = Account(
    id = id,
    name = name,
    subName = subName,
    iconKey = iconKey,
    balanceCents = balanceCents,
    includeInNetWorth = includeInNetWorth,
    category = category,
    hidden = hidden,
    sortOrder = sortOrder,
)
