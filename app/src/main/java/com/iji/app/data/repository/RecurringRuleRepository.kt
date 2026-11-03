package com.iji.app.data.repository

import com.iji.app.data.db.RecurringRuleDao
import com.iji.app.data.db.RecurringRuleEntity
import com.iji.app.data.model.RecurrenceFreq
import com.iji.app.data.model.RecurringRule
import com.iji.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * 周期记账规则仓库：Room 本地持久化（生产环境）。
 * dao 为 null 时回退到内存实现，仅用于 Compose 预览等无数据库场景。
 */
class RecurringRuleRepository(private val dao: RecurringRuleDao?) {

    private val memory = MutableStateFlow(emptyList<RecurringRule>())

    /** 全部周期记账规则（Flow 自动响应数据库变化） */
    val rules: Flow<List<RecurringRule>> =
        dao?.observeAll()?.map { list -> list.map { it.toModel() } } ?: memory

    suspend fun seedIfEmpty() {
        // 周期记账规则默认空，无需种子数据
        dao ?: return
    }

    /**
     * 新增规则，返回**落库后的主键**。
     *
     * 回传 id 是必要的：[RecurringRule] 新建时 `id == 0`，真实主键由 [Ids] 在 `toEntity()`
     * 里生成；若不回传，内存里的对象永远是 id=0，后续「停用 / 编辑 / 删除」按 id 找不到行，
     * 写库会静默失败（`UPDATE ... WHERE id=0` 影响 0 行）。调用方需把 id 回填到内存对象。
     */
    suspend fun add(rule: RecurringRule): Long {
        val entity = rule.toEntity() // id 在此刻唯一确定，之后不再二次生成
        val d = dao ?: run {
            memory.value = memory.value + entity.toModel()
            return entity.id
        }
        d.insert(entity)
        return entity.id
    }

    suspend fun update(rule: RecurringRule) {
        val d = dao ?: run {
            memory.value = memory.value.map { if (it.id == rule.id) rule else it }
            return
        }
        d.update(rule.toEntity())
    }

    suspend fun delete(rule: RecurringRule) {
        val d = dao ?: run {
            memory.value = memory.value.filterNot { it.id == rule.id }
            return
        }
        d.delete(rule.toEntity())
    }

    private fun RecurringRule.toEntity() = RecurringRuleEntity(
// 走 Ids：同一毫秒新增多条规则会拿到相同主键，而 RecurringRuleDao.insert
        // 是默认 ABORT 策略 → 直接抛 SQLiteConstraintException 崩溃
        id = if (id == 0L) Ids.next() else id,
        type = type.name,
        categoryId = categoryId,
        amountCents = amountCents,
        note = note,
        account = account,
        freq = freq.name,
        nextRunDate = nextRunDate.toEpochDay(),
        enabled = enabled,
        ledgerId = ledgerId,
    )

    private fun RecurringRuleEntity.toModel() = RecurringRule(
        id = id,
        type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
        categoryId = categoryId,
        amountCents = amountCents,
        note = note,
        account = account,
        freq = runCatching { RecurrenceFreq.valueOf(freq) }.getOrDefault(RecurrenceFreq.MONTHLY),
        nextRunDate = LocalDate.ofEpochDay(nextRunDate),
        enabled = enabled,
        ledgerId = ledgerId,
    )
}
