package com.iji.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * 更新包下载与安装。
 *
 * 两件事都不做「假装成功」：
 * - 下载走 `.part` 临时文件，**校验通过才原子改名**，半成品不会被当成安装包复用；
 * - 安装前逐层校验（大小 → ZIP 完整性 → 包名 → 版本号 → SHA-256），
 *   任一层不过就删包并报出具体原因，而不是拉起安装器让系统弹「解析失败」。
 *
 * 下载地址是更新源给的 [UpdateChecker.UpdateInfo.downloadUrl] 不透明直链，无鉴权；
 * Gitee Release / R2 附件的 302 由 OkHttp 自动跟随。调试期若该地址指向 pdf，
 * 会在这里被 ZIP 头校验如实拦下——换成真 apk 即通，不做「假装能装」的放行。
 */
object UpdateInstaller {

    /** 安装包下载目录（同时登记在 res/xml/file_paths.xml 里供 FileProvider 授权） */
    private const val APK_DIR = "update"

    private const val APK_MIME = "application/vnd.android.package-archive"

    // 进度回调约定：`(已下载字节, 总字节)`，总字节未知时给 -1；在 IO 线程调用，调用方自行切主线程

    fun apkFile(context: Context, version: String): File =
        File(File(context.filesDir, APK_DIR), "iji-${version.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")

    private fun partFile(final: File): File = File(final.parentFile, final.name + ".part")

    /** 本地是否已经存着一份同版本的包（用于「已下载，待安装」的续用） */
    fun existingApk(context: Context, info: UpdateChecker.UpdateInfo): File? {
        val f = apkFile(context, info.version)
        return f.takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * 下载安装包（点「立即下载」才调用）。
     *
     * @return 校验通过、已落到最终路径的 apk 文件
     */
    suspend fun download(
        context: Context,
        info: UpdateChecker.UpdateInfo,
        onProgress: suspend (received: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val finalFile = apkFile(context, info.version)
        val part = partFile(finalFile)
        finalFile.parentFile?.mkdirs()
        if (part.exists()) part.delete()
        val url = info.downloadUrl.ifBlank { throw IOException("更新源没有提供下载地址") }

        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException(
                    when (resp.code) {
                        404 -> "安装包不存在（HTTP 404）：请确认更新源里的附件是否已上传"
                        else -> "下载失败（HTTP ${resp.code}）"
                    },
                )
            }
            val body = resp.body ?: throw IOException("下载响应为空")
            val total = body.contentLength()
            var received = 0L
            body.byteStream().use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        received += n
                        onProgress(received, total)
                    }
                    output.fd.sync()
                }
            }
            if (total > 0 && received != total) {
                part.delete()
                throw IOException("下载不完整：$received / $total 字节，请重试")
            }
        }

        val problem = verify(context, part, info)
        if (problem != null) {
            part.delete()
            throw IOException(problem)
        }
        if (finalFile.exists()) finalFile.delete()
        if (!part.renameTo(finalFile)) {
            part.delete()
            throw IOException("安装包落盘失败，请检查存储空间")
        }
        // 同一版本的旧包可能被这次的新文件替换；其它版本的残留一并清掉
        cleanup(context, keepVersion = info.version)
        finalFile
    }

    /**
     * 安装前校验，返回 null 表示可用；否则返回给用户看的原因。
     *
     * 顺序刻意从便宜到贵：体积 → ZIP 结构 → 包名 → 版本号 → SHA-256。
     */
    fun verify(context: Context, file: File, info: UpdateChecker.UpdateInfo): String? {
        if (!file.isFile || file.length() <= 0L) return "安装包为空或不存在"
        info.sizeBytes?.let { expected ->
            if (expected > 0L && file.length() != expected) {
                return "安装包大小不符（${file.length()} / $expected 字节）"
            }
        }
        if (!hasZipHeader(file)) return "文件不是有效的 APK（缺少 ZIP 头）"
        val manifestOk = runCatching {
            ZipFile(file).use { zip -> zip.getEntry("AndroidManifest.xml") != null }
        }.getOrDefault(false)
        if (!manifestOk) return "安装包结构损坏（读不到 AndroidManifest.xml）"

        val pm = context.packageManager
        val archive = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(file.absolutePath, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES)
            }
        }.getOrNull() ?: return "安装包无法解析（可能下载不完整）"

        if (archive.packageName != context.packageName) {
            return "安装包包名不一致：${archive.packageName}"
        }
        val installedCode = runCatching {
            PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(context.packageName, 0))
        }.getOrDefault(-1L)
        val archiveCode = PackageInfoCompat.getLongVersionCode(archive)
        if (installedCode >= 0L && archiveCode <= installedCode) {
            return "安装包版本号（$archiveCode）不比已装版本（$installedCode）新，系统会拒绝覆盖安装"
        }

        info.sha256?.takeIf { it.isNotBlank() }?.let { want ->
            val actual = sha256(file) ?: return "校验值计算失败"
            if (!actual.equals(want, ignoreCase = true)) return "SHA-256 校验不通过，安装包可能被篡改或损坏"
        }
        return null
    }

    /** 拉起系统安装器；返回 false 表示没拉起来（无安装器 / FileProvider 配置缺失） */
    fun install(context: Context, file: File): Boolean {
        if (!file.isFile) {
            Log.w(TAG, "安装包不存在：${file.absolutePath}")
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        }.getOrElse { e ->
            Log.e(TAG, "拉起安装器失败", e)
            false
        }
    }

    /** 是否已被允许「安装未知应用」（Android 8+ 每次安装都要过一次） */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** 跳到本应用的「安装未知应用」授权页 */
    fun openInstallPermission(context: Context) {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
            add(Intent(Settings.ACTION_SECURITY_SETTINGS))
            add(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        Log.w(TAG, "没有可用的安装权限设置页")
    }

    /** 清掉除 [keepVersion] 以外的历史安装包与半成品 */
    fun cleanup(context: Context, keepVersion: String?) {
        val dir = File(context.filesDir, APK_DIR)
        val keep = keepVersion?.let { runCatching { apkFile(context, it).name }.getOrNull() }
        dir.listFiles()?.forEach { f ->
            val name = f.name
            val isKept = keep != null && name == keep
            if (!isKept && (name.endsWith(".apk") || name.endsWith(".apk.part"))) {
                runCatching { f.delete() }
            }
        }
    }

    /** 流式 SHA-256（64KB 缓冲，不把整个包读进内存） */
    fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun hasZipHeader(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 4L) return@use false
            val header = ByteArray(4)
            raf.readFully(header)
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
        }
    }.getOrDefault(false)

    /** 下载用客户端：自动跟随 302（Gitee Release / R2 附件都会跳 CDN） */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private const val TAG = "UpdateInstaller"
}
