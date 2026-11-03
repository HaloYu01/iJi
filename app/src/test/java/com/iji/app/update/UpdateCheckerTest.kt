package com.iji.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「检查更新」的纯逻辑单测。
 *
 * 背景：这个入口原来只弹一句写死的「已是最新版本」，没有任何校验；
 * 现在改为真实比对版本号，比对与解析一旦写错，用户就会被误导成「已经最新」——
 * 所以这两件事必须有测试锁住。
 */
class UpdateCheckerTest {

    // ── 版本比较 ──

    @Test
    fun isNewer_ignoresLeadingV() {
        assertTrue(UpdateChecker.isNewer("v1.1.0", "1.0.0"))
    }

    @Test
    fun isNewer_comparesNumericallyNotLexically() {
        // 字符串比较会得出 "1.9.9" > "1.10.0"，这正是要避免的
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("1.9.9", "1.10.0"))
    }

    @Test
    fun isNewer_equalOrOlderIsFalse() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun isNewer_missingSegmentsTreatedAsZero() {
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"))
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"))
    }

    @Test
    fun isNewer_prereleaseOfSameVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.1.0-beta", "1.1.0"))
    }

    // ── 更新源解析 ──

    @Test
    fun parseFeed_simpleFormat_reportsNewVersion() {
        val result = UpdateChecker.parseFeed(
            """{"version":"1.2.0","url":"https://example.com/dl","notes":"修了几个 bug"}""",
            "1.0.0",
        )
        val info = (result as UpdateChecker.CheckResult.Available).info
        assertEquals("1.2.0", info.version)
        assertEquals("https://example.com/dl", info.url)
        assertEquals("修了几个 bug", info.notes)
    }

    @Test
    fun parseFeed_githubReleaseFormat_isSupported() {
        val result = UpdateChecker.parseFeed(
            """{"tag_name":"v1.3.0","html_url":"https://github.com/x/y/releases/tag/v1.3.0","body":"### 新增\n- 预算结转"}""",
            "1.0.0",
        )
        val info = (result as UpdateChecker.CheckResult.Available).info
        assertEquals("1.3.0", info.version)
        assertEquals("https://github.com/x/y/releases/tag/v1.3.0", info.url)
    }

    @Test
    fun parseFeed_sameVersionIsUpToDate() {
        assertEquals(
            UpdateChecker.CheckResult.UpToDate,
            UpdateChecker.parseFeed("""{"version":"1.0.0"}""", "1.0.0"),
        )
    }

    @Test
    fun parseFeed_notJsonFails() {
        assertTrue(UpdateChecker.parseFeed("<html>404</html>", "1.0.0") is UpdateChecker.CheckResult.Failed)
    }

    @Test
    fun parseFeed_withoutVersionFieldFails() {
        assertTrue(UpdateChecker.parseFeed("""{"notes":"什么也没说"}""", "1.0.0") is UpdateChecker.CheckResult.Failed)
    }

    @Test
    fun check_blankUrlIsNotConfiguredRatherThanUpToDate() {
        // 关键语义：没配置 ≠ 已是最新。以前这里弹的正是「已是最新版本」。
        val result = runCatching {
            kotlinx.coroutines.runBlocking { UpdateChecker.check("  ", "1.0.0") }
        }.getOrNull()
        assertEquals(UpdateChecker.CheckResult.NotConfigured, result)
    }
}
