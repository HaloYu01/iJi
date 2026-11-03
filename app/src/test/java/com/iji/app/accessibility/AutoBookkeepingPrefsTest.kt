package com.iji.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 记账偏好的纯函数：落账账本、备注模板、去重窗口。
 *
 * 这三条直接决定"自动记账记到哪、备注长什么样、会不会重复入账"，出错用户很难察觉，
 * 所以把规则从 UI 里抽出来单测。
 */
class AutoBookkeepingPrefsTest {

    @Test
    fun ledgerFollowsCurrentWhenUnset() {
        assertEquals("日常", AutoBookkeepingPrefs.effectiveLedger("", "日常"))
        assertEquals("日常", AutoBookkeepingPrefs.effectiveLedger("   ", "日常"))
    }

    @Test
    fun ledgerSettingWins() {
        assertEquals("旅行", AutoBookkeepingPrefs.effectiveLedger("旅行", "日常"))
    }

    @Test
    fun noteUsesMerchantByDefault() {
        assertEquals(
            "瑞幸咖啡",
            AutoBookkeepingPrefs.buildNote("{商户}", "瑞幸咖啡", "微信支付"),
        )
    }

    @Test
    fun noteSupportsSourcePlaceholder() {
        assertEquals(
            "微信支付·瑞幸咖啡",
            AutoBookkeepingPrefs.buildNote("{来源}·{商户}", "瑞幸咖啡", "微信支付"),
        )
    }

    @Test
    fun noteKeepsUnknownPlaceholder() {
        // 写错的占位符原样保留，用户能看出模板没生效，而不是被静默吞掉
        assertEquals(
            "{金额}瑞幸咖啡",
            AutoBookkeepingPrefs.buildNote("{金额}{商户}", "瑞幸咖啡", "微信支付"),
        )
    }

    @Test
    fun blankTemplateFallsBackToMerchant() {
        assertEquals("瑞幸咖啡", AutoBookkeepingPrefs.buildNote("", "瑞幸咖啡", "微信支付"))
        assertEquals("瑞幸咖啡", AutoBookkeepingPrefs.buildNote("{来源}", "瑞幸咖啡", ""))
    }

    @Test
    fun dedupWindowConvertsToMillis() {
        assertEquals(60_000L, AutoBookkeepingPrefs.dedupWindowMs(60))
        assertEquals(30_000L, AutoBookkeepingPrefs.dedupWindowMs(30))
    }

    @Test
    fun dedupWindowIsClamped() {
        // 防止脏配置导致「永不去重」或「全去重」
        assertEquals(10_000L, AutoBookkeepingPrefs.dedupWindowMs(5))
        assertEquals(600_000L, AutoBookkeepingPrefs.dedupWindowMs(999))
    }
}
