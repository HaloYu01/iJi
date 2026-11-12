package com.iji.app.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * 检查更新（真实联网，源为作者的 Gitee Release 仓库接口 [DEFAULT_FEED]）。
 *
 * 原生协议是 Gitee API v5 的 latest release：
 * ```
 * {"tag_name":"v1.2.0","body":"更新日志","assets":[{"name":"…apk","size":…,"browser_download_url":"…"}]}
 * ```
 * - 版本号取 `tag_name`，统一 **x.x.x**（三段数字），允许可选的 `v` 前缀；比较时按段转数字，
 *   避免 `1.10.0` 被字符串比较判成比 `1.9.9` 旧。不要拿 versionCode 直接比，
 *   因为远端只给 versionName，安装前才用 versionCode 校验待装包。
 * - 下载地址从 `assets` 附件里挑 `.apk`（Release 可能附带校验文件等），是不透明直链：
 *   Gitee Release 附件的 302 由 OkHttp 自动跟随，后续换 R2 / 任意 CDN 都不需要改客户端。
 *
 * 请求整体 4 秒兜底（callTimeout），失败提示统一带 [FAILURE_HINT] 前缀，
 * 同时在第二行给出具体原因（超时 / HTTP 码 / 解析问题），不弹无依据的结论。
 */
object UpdateChecker {

    /** 内置更新源（Gitee Release 仓库的 latest 接口）；换源只需改这一个常量，界面不提供覆盖入口 */
    const val DEFAULT_FEED = "https://gitee.com/api/v5/repos/HaloYu01/iJi-release/releases/latest"

    /** 网络/接口异常时给用户的统一兜底提示 */
    const val FAILURE_HINT = "检查更新失败，请稍后重试"

    data class UpdateInfo(
        /** 远端版本号，x.x.x（已去掉可选的 v 前缀） */
        val version: String,
        /** 更新日志原文（Gitee Release 的 body 字段） */
        val notes: String,
        /** 安装包直链，不透明：任何 CDN / Release 附件地址都能直接用 */
        val downloadUrl: String,
        /** 接口给的文件名，仅用于展示与排障；落盘文件名按 version 生成，避免路径注入 */
        val fileName: String,
        /** 可选：接口声明的字节数 */
        val sizeBytes: Long? = null,
        /** 可选：接口声明的 SHA-256（64 位十六进制） */
        val sha256: String? = null,
    ) {
        val hasApk: Boolean get() = downloadUrl.isNotBlank()
    }

    sealed interface CheckResult {
        /** 已是最新版本 */
        data object UpToDate : CheckResult

        /** 有新版本 */
        data class Available(val info: UpdateInfo) : CheckResult

        /** 检查失败：带上可直接读给用户的原因 */
        data class Failed(val reason: String) : CheckResult
    }

    suspend fun check(currentVersion: String): CheckResult = withContext(Dispatchers.IO) {
        val result = runCatching {
            client.newCall(
                Request.Builder()
                    .url(DEFAULT_FEED)
                    .header("Accept", "application/json")
                    .build(),
            ).execute().use { resp ->
                if (resp.isSuccessful) {
                    parseFeed(resp.body?.string().orEmpty(), currentVersion)
                } else {
                    CheckResult.Failed(httpFailureReason(resp.code))
                }
            }
        }.getOrElse { t ->
            Log.w(TAG, "检查更新失败", t)
            CheckResult.Failed(
                if (t is InterruptedIOException) "请求超时（4 秒）" else (t.message ?: t.javaClass.simpleName),
            )
        }
        // 统一兜底文案 + 具体原因：既满足「失败提示」，也保留排障线索
        when (result) {
            is CheckResult.Failed -> CheckResult.Failed("$FAILURE_HINT\n${result.reason}")
            else -> result
        }
    }

    /** 把 HTTP 状态码翻译成可照着处理的原因 */
    internal fun httpFailureReason(code: Int): String = when (code) {
        404 -> "更新源不存在（HTTP 404）"
        429 -> "更新服务请求过于频繁（HTTP 429）"
        in 500..599 -> "更新服务暂时不可用（HTTP $code）"
        else -> "更新源返回 HTTP $code"
    }

    /**
     * 解析更新源返回（纯函数，可脱离 Android 单测）：Gitee Releases 协议（tag_name / assets）。
     * 非 x.x.x 版本号、缺下载地址都如实报错。
     */
    fun parseFeed(json: String, currentVersion: String): CheckResult {
        val obj = runCatching { JSONObject(json.trim()) }.getOrNull()
            ?: return CheckResult.Failed("更新源返回的不是 JSON")

        val rawVersion = obj.optString("tag_name").trim()
        if (rawVersion.isEmpty()) return CheckResult.Failed("更新源返回里没有版本号字段")
        val version = rawVersion.removePrefix("v").removePrefix("V")
        if (!VERSION_PATTERN.matches(version)) {
            return CheckResult.Failed("版本号应为 x.x.x 格式，实际：$rawVersion")
        }
        if (!isNewer(version, currentVersion)) return CheckResult.UpToDate

        val assets = obj.optJSONArray("assets")
        var downloadUrl = ""
        var fileName = ""
        var sizeBytes = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val url = asset.optString("browser_download_url").trim()
                if (url.isEmpty()) continue
                val name = asset.optString("name").trim()
                val isApk = name.endsWith(".apk", ignoreCase = true)
                // 首选 .apk 附件；Release 只挂了别的文件时记住第一个附件兜底
                if (isApk || downloadUrl.isEmpty()) {
                    downloadUrl = url
                    fileName = name
                    sizeBytes = asset.optLong("size", 0L)
                    if (isApk) break
                }
            }
        }
        if (downloadUrl.isEmpty()) return CheckResult.Failed("更新源没有提供下载地址")

        return CheckResult.Available(
            UpdateInfo(
                version = version,
                notes = obj.optString("body").trim(),
                downloadUrl = downloadUrl,
                fileName = fileName,
                sizeBytes = sizeBytes.takeIf { it > 0L },
                // Gitee 接口不提供哈希，安装前校验按无哈希路径走（versionCode 校验不受影响）
                sha256 = null,
            ),
        )
    }

    /**
     * 远端版本是否比当前版本新：逐段比较数字，
     * `1.10.0 > 1.9.9` 这种按字符串比会错的场景也正确。
     */
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
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /** 严格 x.x.x：三段纯数字，避免 `1.0.0Beta` 被截成 [1,0,0] 后误判 */
    private val VERSION_PATTERN = Regex("""^\d+\.\d+\.\d+$""")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private const val TAG = "UpdateChecker"
}
