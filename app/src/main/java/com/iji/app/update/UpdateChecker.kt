package com.iji.app.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 检查更新。
 *
 * 本应用**没有内置更新服务器**：默认不联网，也绝不再弹一句没有依据的「已是最新版本」。
 * 用户在「关于应用 → 更新版本」里填入更新源地址后，这里会真实发起请求并比对版本号；
 * 没填地址时如实说明「未配置」，把「查不了」讲清楚，而不是假装查过了。
 *
 * 更新源支持两种 JSON 返回（都能直接填）：
 * 1. 简洁格式：`{"version":"1.1.0","url":"https://…/iji-1.1.0.apk","notes":"修复…"}`
 * 2. GitHub 仓库 `releases/latest` 的原始响应：`{"tag_name":"v1.1.0","html_url":"…","body":"…"}`
 */
object UpdateChecker {

    data class UpdateInfo(val version: String, val url: String, val notes: String)

    sealed interface CheckResult {
        /** 未配置更新源地址：无从检查（不是「已是最新」） */
        data object NotConfigured : CheckResult

        /** 已是最新版本 */
        data object UpToDate : CheckResult

        /** 有新版本 */
        data class Available(val info: UpdateInfo) : CheckResult

        /** 检查失败：网络不通或返回内容无法解析 */
        data class Failed(val reason: String) : CheckResult
    }

    /** 真实联网检查；[feedUrl] 为空直接返回 [CheckResult.NotConfigured]（不发请求） */
    suspend fun check(feedUrl: String, currentVersion: String): CheckResult {
        val url = feedUrl.trim()
        if (url.isEmpty()) return CheckResult.NotConfigured
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return CheckResult.Failed("更新源地址需以 http:// 或 https:// 开头")
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .build(),
                ).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        CheckResult.Failed("更新源返回 HTTP ${resp.code}")
                    } else {
                        parseFeed(resp.body?.string().orEmpty(), currentVersion)
                    }
                }
            }.getOrElse { t ->
                Log.w(TAG, "检查更新失败", t)
                CheckResult.Failed(t.message ?: "网络不可用")
            }
        }
    }

    /** 解析更新源返回（纯函数，可脱离 Android 单测） */
    fun parseFeed(json: String, currentVersion: String): CheckResult {
        val obj = runCatching { JSONObject(json.trim()) }.getOrElse {
            return CheckResult.Failed("更新源返回的不是 JSON")
        }
        val version = obj.optString("tag_name").ifBlank { obj.optString("version") }.trim()
        if (version.isEmpty()) return CheckResult.Failed("更新源返回里没有版本号字段")
        val pageUrl = obj.optString("html_url").ifBlank { obj.optString("url") }.trim()
        val notes = obj.optString("body").ifBlank { obj.optString("notes") }.trim()
        return if (isNewer(version, currentVersion)) {
            CheckResult.Available(UpdateInfo(version.trimStart('v', 'V'), pageUrl, notes))
        } else {
            CheckResult.UpToDate
        }
    }

    /** 远端版本是否比当前版本新：逐段比较数字，`1.10.0 > 1.9.9` 这种按字符串比会错的场景也正确 */
    fun isNewer(remote: String, current: String): Boolean {
        val r = versionParts(remote)
        val c = versionParts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun versionParts(v: String): List<Int> = v.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-') // 1.1.0-beta → 1.1.0（预发布版不当作更新）
        .substringBefore('+') // 1.1.0+build → 1.1.0
        .split('.')
        .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private const val TAG = "UpdateChecker"
}
