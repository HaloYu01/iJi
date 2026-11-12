package com.iji.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「检查更新」的纯逻辑单测。
 *
 * 更新源是 Gitee Releases 接口（tag_name / assets）。版本比较、x.x.x 格式与字段映射
 * 一旦写错，用户会被误导成「已经最新」或者下载到错的文件（比如把 Release 里附带的
 * 校验文件当安装包）——这几件事必须有测试锁住。
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

    // ── 内置更新源 ──

    @Test
    fun defaultFeed_isBuiltInGiteeRepo() {
        assertEquals(
            "https://gitee.com/api/v5/repos/HaloYu01/iJi-release/releases/latest",
            UpdateChecker.DEFAULT_FEED,
        )
    }

    // ── Gitee Releases 协议解析 ──

    @Test
    fun parseFeed_giteePayloadMapsAllFields() {
        val json = """
            {
              "tag_name": "v2.0.0",
              "body": "新版日志",
              "assets": [
                {"name": "sha256.txt", "size": 64,
                 "browser_download_url": "https://gitee.com/a/sha256.txt"},
                {"name": "iji-2.0.0.apk", "size": 12345678,
                 "browser_download_url": "https://gitee.com/a/iji-2.0.0.apk"}
              ]
            }
        """.trimIndent()
        val info = (UpdateChecker.parseFeed(json, "1.1.0") as UpdateChecker.CheckResult.Available).info
        assertEquals("2.0.0", info.version)
        assertEquals("新版日志", info.notes)
        // 附件里选 .apk，跳过校验文件
        assertEquals("https://gitee.com/a/iji-2.0.0.apk", info.downloadUrl)
        assertEquals("iji-2.0.0.apk", info.fileName)
        assertEquals(12345678L, info.sizeBytes)
        // Gitee 接口不带哈希
        assertEquals(null, info.sha256)
        assertTrue(info.hasApk)
    }

    @Test
    fun parseFeed_giteeWithoutApkFallsBackToFirstAsset() {
        val json = """
            {"tag_name":"2.0.0","body":"",
             "assets":[{"name":"iji.zip","size":1,"browser_download_url":"https://gitee.com/a/iji.zip"}]}
        """.trimIndent()
        val info = (UpdateChecker.parseFeed(json, "1.1.0") as UpdateChecker.CheckResult.Available).info
        assertEquals("https://gitee.com/a/iji.zip", info.downloadUrl)
        assertEquals("iji.zip", info.fileName)
    }

    @Test
    fun parseFeed_giteeWithoutAssetsFails() {
        val result = UpdateChecker.parseFeed("""{"tag_name":"v2.0.0","body":""}""", "1.1.0")
        assertTrue((result as UpdateChecker.CheckResult.Failed).reason.contains("下载地址"))
    }

    @Test
    fun parseFeed_giteeMissingVersionFails() {
        val result = UpdateChecker.parseFeed(
            """{"assets":[{"name":"a.apk","browser_download_url":"https://gitee.com/a.apk"}]}""",
            "1.1.0",
        )
        assertTrue((result as UpdateChecker.CheckResult.Failed).reason.contains("版本号"))
    }

    @Test
    fun parseFeed_giteeSameOrOlderIsUpToDate() {
        val body = """{"tag_name":"v1.2.0","assets":[{"name":"a.apk","browser_download_url":"https://gitee.com/a.apk"}]}"""
        assertEquals(UpdateChecker.CheckResult.UpToDate, UpdateChecker.parseFeed(body, "1.2.0"))
        assertEquals(UpdateChecker.CheckResult.UpToDate, UpdateChecker.parseFeed(body, "1.3.0"))
    }

    @Test
    fun parseFeed_giteeRejectsNonSemverTag() {
        // 约定只有 x.x.x；带后缀的 tag 一律拒绝，不能截断后误判
        for (tag in listOf("v1.0.0-beta", "1.0", "beta")) {
            val json = """{"tag_name":"$tag","assets":[{"name":"a.apk","browser_download_url":"https://gitee.com/a.apk"}]}"""
            assertTrue("$tag 应被拒绝", UpdateChecker.parseFeed(json, "1.0.0") is UpdateChecker.CheckResult.Failed)
        }
    }

    @Test
    fun parseFeed_notJsonFails() {
        assertTrue(UpdateChecker.parseFeed("<html>404</html>", "1.1.0") is UpdateChecker.CheckResult.Failed)
    }

    // ── 失败提示 ──

    @Test
    fun httpFailureReason_isActionable() {
        assertTrue(UpdateChecker.httpFailureReason(404).contains("404"))
        assertTrue(UpdateChecker.httpFailureReason(503).contains("503"))
        assertEquals("检查更新失败，请稍后重试", UpdateChecker.FAILURE_HINT)
    }
}
