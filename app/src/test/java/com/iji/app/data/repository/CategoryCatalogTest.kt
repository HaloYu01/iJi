package com.iji.app.data.repository

import com.iji.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类目录纯逻辑单测。
 *
 * 覆盖本次「分类全面可编辑」改造里**最容易回归**的几条约定：
 * 目录为空的时候不许崩、未知 id 要有占位、出厂分类的 id 不许撞车、排序字段要真的生效。
 *
 * 注意：这里不初始化 [CategoryStore]（没有 Context），目录读的是出厂种子
 * —— 这正是 [CategoryStore.all] 在未 init 时回落到 [CategoryDefaults] 的原因。
 */
class CategoryCatalogTest {

    @Test
    fun of_unknownId_returnsUncategorizedPlaceholderInsteadOfThrowing() {
        // 分类可删：账单里的历史 categoryId 一定会有"查不到"的情况，这里必须安全降级
        val category = CategoryCatalog.of("某个已经被删掉的分类")
        assertEquals("未分类", category.name)
        assertEquals("某个已经被删掉的分类", category.id)
    }

    @Test
    fun expenseAndIncomeOtherDoNotShareId() {
        // 旧版支出/收入「其他」共用 id "other"，改一个会连带另一个（索引按 id 去重时后者被丢）
        val expenseOther = CategoryCatalog.expenses.first { it.name == "其他" }
        val incomeOther = CategoryCatalog.incomes.first { it.name == "其他" }
        assertNotEquals(expenseOther.id, incomeOther.id)
        assertEquals("other", expenseOther.id)
        assertEquals("other-income", incomeOther.id)
    }

    @Test
    fun parentsOfType_isOrderedBySortOrder() {
        val parents = CategoryCatalog.parentsOfType(TransactionType.EXPENSE)
        assertTrue(parents.isNotEmpty())
        assertEquals(parents.sortedBy { it.sortOrder }.map { it.id }, parents.map { it.id })
    }

    @Test
    fun childrenOf_returnsOnlyChildrenOfThatParentInOrder() {
        val children = CategoryCatalog.childrenOf("food")
        assertTrue(children.isNotEmpty())
        assertTrue(children.all { it.parentId == "food" })
        assertEquals(children.sortedBy { it.sortOrder }.map { it.id }, children.map { it.id })
        assertTrue(CategoryCatalog.childrenOf("food").none { it.id == "food" })
    }

    @Test
    fun subtreeIds_containsSelfAndAllDescendants() {
        val ids = CategoryCatalog.subtreeIds("food")
        assertTrue("food" in ids)
        assertTrue("food-breakfast" in ids)
        // 别的父分类不该被带进来
        assertTrue(ids.none { it.startsWith("transport") })
    }

    @Test
    fun semanticOf_inheritsFromParentCategory() {
        assertEquals(CategoryDefaults.SEMANTIC_LEND, CategoryCatalog.semanticOf("lend"))
        // 子分类 `lend-loan` 自身没有标记，靠向上继承
        assertEquals(CategoryDefaults.SEMANTIC_LEND, CategoryCatalog.semanticOf("lend-loan"))
        assertEquals(CategoryDefaults.SEMANTIC_BORROW, CategoryCatalog.semanticOf("borrow"))
        assertEquals(null, CategoryCatalog.semanticOf("food-breakfast"))
    }

    @Test
    fun fallbackIds_areStableAndResolvable() {
        assertEquals("other", CategoryDefaults.fallbackId(TransactionType.EXPENSE))
        assertEquals("other-income", CategoryDefaults.fallbackId(TransactionType.INCOME))
        // 兜底 id 必须真的能在对应类型目录里找到，否则删除分类时没有迁移落点
        assertNotNull(CategoryCatalog.expenses.firstOrNull { it.id == "other" })
        assertNotNull(CategoryCatalog.incomes.firstOrNull { it.id == "other-income" })
    }

    @Test
    fun defaultsAreAllMarkedBuiltinWithValidParents() {
        val all = CategoryDefaults.all
        assertTrue(all.all { it.builtin })
        all.filter { it.parentId != null }.forEach { child ->
            assertNotNull("子分类的父级必须存在: ${child.id}", all.firstOrNull { it.id == child.parentId })
        }
        // id 全局唯一（旧版的 other 冲突就是在这里暴露的）
        assertEquals(all.size, all.map { it.id }.distinct().size)
    }
}
