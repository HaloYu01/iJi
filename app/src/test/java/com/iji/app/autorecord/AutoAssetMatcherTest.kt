package com.iji.app.autorecord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 账户匹配层的用例：只喂文本与资产清单，不依赖 Android / 数据库。
 *
 * 这些用例对应用户实际遇到的形态——页面上写的是「零钱」「余额宝」「招商银行储蓄卡(1234)」，
 * 本机账户叫「微信」「支付宝」「招商银行」。
 */
class AutoAssetMatcherTest {

    private val assets = listOf(
        AutoAsset(name = "微信", subName = "微信钱包", note = "wechat"),
        AutoAsset(name = "支付宝", subName = "支付宝", note = "alipay"),
        AutoAsset(name = "招商银行", subName = "储蓄卡", note = "尾号1234"),
        AutoAsset(name = "现金", subName = "钱包"),
    )

    @Test
    fun `精确同名优先`() {
        val match = AutoAssetMatcher.match("微信", "微信", assets)
        assertEquals("微信", match.assetName)
        assertEquals(100, match.score)
    }

    @Test
    fun `零钱映射到微信`() {
        val match = AutoAssetMatcher.match("零钱", "微信", assets)
        assertEquals("微信", match.assetName)
        assertEquals(65, match.score)
    }

    @Test
    fun `余额宝映射到支付宝`() {
        val match = AutoAssetMatcher.match("余额宝", "支付宝", assets)
        assertEquals("支付宝", match.assetName)
    }

    @Test
    fun `花呗映射到支付宝`() {
        val match = AutoAssetMatcher.match("花呗", "支付宝", assets)
        assertEquals("支付宝", match.assetName)
    }

    @Test
    fun `微信支付归一后等同微信`() {
        val match = AutoAssetMatcher.match("微信支付", "微信", assets)
        assertEquals("微信", match.assetName)
    }

    @Test
    fun `银行卡尾号命中`() {
        val match = AutoAssetMatcher.match("招商银行储蓄卡(1234)", "微信", assets)
        assertEquals("招商银行", match.assetName)
        assertEquals(80, match.score)
        assertEquals("1234", match.tail)
    }

    @Test
    fun `相似度命中银行名`() {
        val match = AutoAssetMatcher.match("招商银行储蓄卡", "微信", assets)
        assertEquals("招商银行", match.assetName)
    }

    @Test
    fun `没有对应资产时不猜`() {
        val match = AutoAssetMatcher.match("中国银行储蓄卡(8888)", "微信", assets)
        assertNull(match.assetName)
        assertEquals("未匹配", match.matcher)
    }

    @Test
    fun `资产为空时不猜`() {
        val match = AutoAssetMatcher.match("零钱", "微信", emptyList())
        assertNull(match.assetName)
    }

    @Test
    fun `用户映射优先级最高`() {
        val mapping = listOf(AutoAssetMapping(match = "零钱", assetName = "招商银行", platform = "微信"))
        val match = AutoAssetMatcher.match("零钱", "微信", assets, mapping)
        assertEquals("招商银行", match.assetName)
        assertEquals(110, match.score)
    }

    @Test
    fun `用户映射可限定平台`() {
        val mapping = listOf(AutoAssetMapping(match = "零钱", assetName = "招商银行", platform = "支付宝"))
        val match = AutoAssetMatcher.match("零钱", "微信", assets, mapping)
        // 平台不匹配时退回场景词典
        assertEquals("微信", match.assetName)
    }

    @Test
    fun `平台默认用于纯渠道词`() {
        val match = AutoAssetMatcher.match("", "支付宝", assets)
        assertEquals("支付宝", match.assetName)
        assertEquals(60, match.score)
    }

    @Test
    fun `尾号只出现在原文时不误配`() {
        val match = AutoAssetMatcher.match("招商银行储蓄卡(5678)", "微信", assets)
        // 尾号 5678 与资产备注 1234 不同，银行一致仍可匹配到招商银行
        assertEquals("招商银行", match.assetName)
        assertTrue(match.score >= 70)
    }

    @Test
    fun `归一化剥掉渠道噪声`() {
        assertEquals("招商", AutoAssetMatcher.normalize("招商银行储蓄卡"))
        assertEquals("微信", AutoAssetMatcher.normalize("微信支付"))
        assertEquals("", AutoAssetMatcher.normalize("默认优先使用"))
    }
}
