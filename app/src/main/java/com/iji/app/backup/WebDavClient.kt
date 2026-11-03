package com.iji.app.backup

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 极简 WebDAV 客户端：只做云备份真正需要的四件事 —— 探活 / 列目录 / 上传 / 下载。
 *
 * 为什么是 WebDAV：坚果云、Nextcloud、群晖、Apache/Nginx 的 dav 模块都提供它，
 * 一个实现就能覆盖绝大多数「网盘」；而 Dropbox / OneDrive 需要各自的 OAuth 应用与审核，
 * 本应用不接入，也不在界面上摆一个点了没反应的选项。
 *
 * 认证用 HTTP Basic（坚果云要求「账号 + 应用密码」，不能用登录密码）。
 * 所有方法都返回 [Result]：失败时 `exceptionOrNull()?.message` 是可以直接给用户看的中文说明。
 */
class WebDavClient(
    private val baseUrl: String,
    private val user: String,
    private val pass: String,
) {

    /** 远端一个文件条目 */
    data class RemoteFile(
        /** 可直接 GET 的绝对地址 */
        val url: String,
        val name: String,
        val sizeBytes: Long,
        /** 服务器返回的 Last-Modified 原文（展示用；解析失败则为空） */
        val lastModified: String,
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** 探活：对目标目录发一次 PROPFIND（Depth: 0），能拿到 207 就说明地址、账号、密码都对 */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(propfindRequest(baseUrl, depth = "0")).execute().use { resp ->
                if (resp.code in 200..299) {
                    "连接成功，目录可访问"
                } else {
                    throw IllegalStateException(httpMessage(resp.code))
                }
            }
        }.tidy()
    }

    /** 上传一个文件（PUT）。成功返回文件名 */
    suspend fun upload(fileName: String, bytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = request(joinUrl(baseUrl, fileName))
                .put(bytes.toRequestBody(OCTET_STREAM))
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code in 200..299) {
                    fileName
                } else {
                    throw IllegalStateException(httpMessage(resp.code))
                }
            }
        }.tidy()
    }

    /** 列出目标目录下的备份文件（PROPFIND Depth: 1），最新在前 */
    suspend fun list(extensions: Set<String>): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(propfindRequest(baseUrl, depth = "1")).execute().use { resp ->
                if (resp.code !in 200..299) throw IllegalStateException(httpMessage(resp.code))
                val xml = resp.body?.string().orEmpty()
                parseList(xml, extensions).sortedByDescending { it.lastModified }
            }
        }.tidy()
    }

    /** 下载（GET） */
    suspend fun download(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(request(url).get().build()).execute().use { resp ->
                if (resp.code !in 200..299) throw IllegalStateException(httpMessage(resp.code))
                resp.body?.bytes() ?: ByteArray(0)
            }
        }.tidy()
    }

    // ── 内部 ──

    private fun request(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(user, pass))

    private fun propfindRequest(url: String, depth: String): Request =
        request(url)
            .method("PROPFIND", null)
            .header("Depth", depth)
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()

    /**
     * 把网络层异常翻译成用户看得懂的话。
     *
     * 尤其是 http 明文：Android 从 9 开始默认禁止明文传输（本应用没有开全局
     * `usesCleartextTraffic`），OkHttp 抛的是 `CLEARTEXT communication to … not permitted`，
     * 直接丢给用户等于没说 —— 必须告诉他换 https。
     */
    private fun <T> Result<T>.tidy(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(IllegalStateException(friendlyMessage(it))) },
    )

    private fun friendlyMessage(t: Throwable): String {
        val raw = t.message.orEmpty()
        return when {
            raw.contains("CLEARTEXT", ignoreCase = true) ->
                "该地址是 http 明文地址，系统禁止明文传输；请改用 https（WebDAV 一般都有 https 地址）"
            raw.contains("Unable to resolve host", ignoreCase = true) -> "找不到服务器，请检查地址是否写对"
            raw.contains("timeout", ignoreCase = true) -> "连接超时，请检查网络或服务器地址"
            t is javax.net.ssl.SSLException ->
                "证书校验失败：服务器证书不被系统信任（自签名证书需先安装到系统信任列表）"
            raw.isBlank() -> "连接失败（${t::class.java.simpleName}）"
            else -> raw
        }
    }

    /** 解析 multistatus XML。命名空间前缀在不同服务器上写法不一（d: / D: / 无前缀），故全部可选匹配 */
    internal fun parseList(xml: String, extensions: Set<String>): List<RemoteFile> =
        RESPONSE_BLOCK.findAll(xml).mapNotNull { match ->
            val block = match.groupValues[1]
            val href = HREF.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            if (href.isBlank()) return@mapNotNull null
            val name = decode(href.trimEnd('/').substringAfterLast('/'))
            if (extensions.none { name.endsWith(it, ignoreCase = true) }) return@mapNotNull null
            RemoteFile(
                url = absolute(href),
                name = name,
                sizeBytes = SIZE.find(block)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: 0L,
                lastModified = MODIFIED.find(block)?.groupValues?.get(1)?.trim().orEmpty(),
            )
        }.toList()

    private fun absolute(href: String): String {
        if (href.startsWith("http://", true) || href.startsWith("https://", true)) return href
        val uri = runCatching { java.net.URI(baseUrl) }.getOrNull() ?: return href
        return "${uri.scheme}://${uri.authority}$href"
    }

    private fun joinUrl(base: String, fileName: String): String =
        base.trimEnd('/') + "/" + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun httpMessage(code: Int): String = when (code) {
        401 -> "账号或应用密码不正确（HTTP 401）"
        403 -> "账号没有该目录的读写权限（HTTP 403）"
        404 -> "目录不存在，请检查服务器地址（HTTP 404）"
        405 -> "服务器不支持该操作，请确认填的是 WebDAV 地址（HTTP 405）"
        409 -> "目标目录不存在：请先在网盘里建好这个目录（HTTP 409）"
        423 -> "文件被锁定，稍后重试（HTTP 423）"
        507 -> "网盘空间不足（HTTP 507）"
        else -> "服务器返回 HTTP $code"
    }.also { Log.w(TAG, "WebDAV 请求失败：$code") }

    private companion object {
        const val TAG = "WebDavClient"
        val OCTET_STREAM = "application/octet-stream".toMediaType()

        val RESPONSE_BLOCK =
            Regex("(?is)<(?:[a-z0-9]+:)?response[^>]*>(.*?)</(?:[a-z0-9]+:)?response>")
        val HREF = Regex("(?is)<(?:[a-z0-9]+:)?href[^>]*>(.*?)</(?:[a-z0-9]+:)?href>")
        val SIZE = Regex("(?is)<(?:[a-z0-9]+:)?getcontentlength[^>]*>(\\d+)<")
        val MODIFIED = Regex("(?is)<(?:[a-z0-9]+:)?getlastmodified[^>]*>([^<]+)<")
    }
}
