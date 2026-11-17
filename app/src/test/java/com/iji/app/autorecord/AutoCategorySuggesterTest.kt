package com.iji.app.autorecord

import com.iji.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 内置分类关键词表的回归用例。
 *
 * 只测「关键词 → 分类」这一级（不查历史、不调 AI），
 * 保证新增关键词时不会把已有场景归错，也不会跨收支类型乱归。
 */
class AutoCategorySuggesterTest {

    @Test
    fun `食堂归餐饮`() {
        assertEquals(
            "food",
            AutoCategorySuggester.builtinCategory("江苏师范大学1_食堂_民族餐厅", TransactionType.EXPENSE),
        )
    }

    @Test
    fun `外卖归外卖子分类`() {
        assertEquals(
            "food-takeout",
            AutoCategorySuggester.builtinCategory("美团外卖", TransactionType.EXPENSE),
        )
    }

    @Test
    fun `瑞幸归饮品`() {
        assertEquals(
            "food-drink",
            AutoCategorySuggester.builtinCategory("瑞幸咖啡", TransactionType.EXPENSE),
        )
    }

    @Test
    fun `打车归交通打车`() {
        assertEquals(
            "transport-taxi",
            AutoCategorySuggester.builtinCategory("滴滴出行", TransactionType.EXPENSE),
        )
    }

    @Test
    fun `工资归收入工资`() {
        assertEquals(
            "salary",
            AutoCategorySuggester.builtinCategory("11月工资", TransactionType.INCOME),
        )
    }

    @Test
    fun `收入场景不能套支出分类`() {
        // 「食堂」只存在于支出分类，收入类型下必须判不出来，交给后续 AI / 默认
        assertNull(AutoCategorySuggester.builtinCategory("食堂", TransactionType.INCOME))
    }

    @Test
    fun `没有关键词时不硬猜`() {
        assertNull(AutoCategorySuggester.builtinCategory("中国邮政储蓄银行", TransactionType.EXPENSE))
    }
}