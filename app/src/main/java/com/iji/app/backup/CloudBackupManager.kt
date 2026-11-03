package com.iji.app.backup

import android.content.Context
import com.iji.app.data.repository.ConfigBackupRepository
import com.iji.app.data.repository.SettingsRepository
import org.json.JSONObject

/**
 * 网盘备份（WebDAV）。
 *
 * 改造前这里是**纯空壳**：弹窗只把类型 / 账号 / 密码写进 DataStore，零上传、零下载，
 * 用户以为备份到云上了，其实本机之外什么都没有。
 *
 * 现在的口径：
 * - 配置齐全（地址 + 账号 + 密码）才认为「已配置」，否则一律如实说未配置；
 * - 上传的就是本地那套加密备份（同一密钥、同一 `.ijbak` 格式），**先本机加密再上传**，
 *   网盘只看得到密文；恢复时仍在本地解密；
 * - 只支持 WebDAV 系（坚果云 / Nextcloud / 群晖 / 自建），Dropbox、OneDrive 需要各自的
 *   OAuth 应用，未接入就不在界面上摆选项。
 */
object CloudBackupManager {

    const val TYPE_JIANGUO = "坚果云"
    const val TYPE_GENERIC = "WebDAV（通用）"

    /** 坚果云的 WebDAV 入口地址是固定值，省得用户去翻文档 */
    const val JIANGUO_URL = "https://dav.jianguoyun.com/dav/"

    /** 云备份文件后缀（与本地备份一致） */
    const val BACKUP_EXT = ".ijbak"

    /**
     * 备份内容。
     *
     * 全量 = 配置 + 全部流水（换机/误删都能救）；仅配置 = 体积小、只搬分类/账户/预算等设置。
     * 两者都先在本机 AES 加密再上传，网盘上只有密文。
     */
    enum class Content(val label: String, val prefix: String) {
        FULL("全量（配置 + 流水）", "iji_backup_"),
        CONFIG("仅配置（分类/账户/预算等）", "iji_config_"),
    }

    data class Config(
        val type: String = TYPE_JIANGUO,
        val url: String = "",
        val user: String = "",
        val pass: String = "",
        /** 备份目录（相对 [url]，空 = 直接放在 url 这个目录里）；目录必须已存在，WebDAV 不支持隐式建目录 */
        val dir: String = "",
    ) {
        /** 地址与凭据齐了才算配好——半套配置上传必然失败，不如提前说清楚 */
        val complete: Boolean get() = url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()

        /** 实际读写的目录：把 [dir] 拼在 [url] 后面，WebDavClient 本身只认一个目录地址 */
        val targetUrl: String
            get() {
                val base = url.trim().trimEnd('/')
                val sub = dir.trim().trim('/')
                if (sub.isEmpty()) return base + "/"
                val encoded = sub.split('/')
                    .filter { it.isNotBlank() }
                    .joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                return "$base/$encoded/"
            }

        /** 给用户看的"备份到哪"（不含账号密码） */
        val displayTarget: String get() = targetUrl
    }

    private const val KEY_TYPE = "cloud_type"
    private const val KEY_URL = "cloud_url"
    private const val KEY_USER = "cloud_user"
    private const val KEY_PASS = "cloud_pass"
    private const val KEY_DIR = "cloud_dir"

    // 历史版本写过的键：只有类型/账号/密码，没有地址 —— 所以那时确实传不上去。
    // 首次读取时把它们接过来，用户不用重新填一遍。
    private const val LEGACY_TYPE = "type"
    private const val LEGACY_USER = "user"
    private const val LEGACY_PASS = "pass"

    fun load(context: Context): Config {
        val type = SettingsRepository.getString(context, KEY_TYPE)
            .ifBlank { SettingsRepository.getString(context, LEGACY_TYPE, TYPE_JIANGUO) }
            .let { if (it == TYPE_GENERIC || it == TYPE_JIANGUO) it else TYPE_GENERIC }
        val url = SettingsRepository.getString(context, KEY_URL)
            .ifBlank { if (type == TYPE_JIANGUO) JIANGUO_URL else "" }
        val user = SettingsRepository.getString(context, KEY_USER)
            .ifBlank { SettingsRepository.getString(context, LEGACY_USER) }
        val pass = SettingsRepository.getString(context, KEY_PASS)
            .ifBlank { SettingsRepository.getString(context, LEGACY_PASS) }
        return Config(
            type = type,
            url = url,
            user = user,
            pass = pass,
            dir = SettingsRepository.getString(context, KEY_DIR),
        )
    }

    fun save(context: Context, config: Config) {
        SettingsRepository.setString(context, KEY_TYPE, config.type)
        SettingsRepository.setString(context, KEY_URL, config.url.trim())
        SettingsRepository.setString(context, KEY_USER, config.user.trim())
        SettingsRepository.setString(context, KEY_PASS, config.pass)
        SettingsRepository.setString(context, KEY_DIR, config.dir.trim().trim('/'))
    }

    /**
     * 清除网盘配置（含历史键）。
     *
     * 没有出口的配置等于赖在用户账号里：换了网盘、或想停掉云上传，必须能清干净，
     * 否则自动备份会一直往旧地址传。
     */
    fun clear(context: Context) {
        listOf(KEY_TYPE, KEY_URL, KEY_USER, KEY_PASS, KEY_DIR, LEGACY_TYPE, LEGACY_USER, LEGACY_PASS)
            .forEach { SettingsRepository.setString(context, it, "") }
    }

    /** 是否已配置好（配没配好决定界面要不要显示「未配置」） */
    fun isConfigured(context: Context): Boolean = load(context).complete

    /** 探活：真实发一次 PROPFIND（探的是**实际备份目录**，目录没建好会如实报 404） */
    suspend fun testConnection(context: Context): Result<String> {
        val config = load(context)
        if (!config.complete) return notConfigured()
        return client(config).testConnection()
    }

    /**
     * 立即备份到网盘：构建快照 → 本机加密 → PUT 上传。
     *
     * @param content 全量（配置 + 流水）或仅配置；两者都加密，网盘上只有密文
     * @return 成功时返回「文件名（大小）」供界面展示，用户能据此确认**真的上去了**
     */
    suspend fun uploadNow(
        context: Context,
        content: Content = Content.FULL,
    ): Result<String> {
        val config = load(context)
        if (!config.complete) return notConfigured()

        val plain = when (content) {
            Content.FULL -> BackupManager.buildSnapshot(context)
            // 纯配置快照：结构与全量对齐（都有 config 节点），恢复侧同一套逻辑即可识别
            Content.CONFIG -> JSONObject().apply {
                put("kind", "iji-config")
                put("version", 1)
                put("app", "i记")
                put("exportTime", System.currentTimeMillis())
                put("config", JSONObject(ConfigBackupRepository.exportJson(context)))
            }.toString()
        }
        val encrypted = BackupCipher.encrypt(plain.toByteArray(Charsets.UTF_8))
            ?: return Result.failure(IllegalStateException("加密失败，已取消上传（未上传任何内容）"))
        val name = content.prefix + System.currentTimeMillis() + BACKUP_EXT
        return client(config).upload(name, encrypted)
            .map { "$name（${"%.1f".format(encrypted.size / 1024f)} KB）" }
    }

    /** 列出网盘上的备份文件（只看当前备份目录） */
    suspend fun listRemote(context: Context): Result<List<WebDavClient.RemoteFile>> {
        val config = load(context)
        if (!config.complete) return notConfigured()
        return client(config).list(setOf(BACKUP_EXT))
    }

    /**
     * 从网盘取回一份快照（解密后的明文 JSON），交给调用方走既有恢复流程。
     *
     * 解密失败通常是换了设备：密钥在 Android Keystore 里，不随云盘走。
     */
    suspend fun downloadSnapshot(
        context: Context,
        remote: WebDavClient.RemoteFile,
    ): Result<String> {
        val config = load(context)
        if (!config.complete) return notConfigured()
        return client(config).download(remote.url).mapCatching { bytes ->
            val plain = BackupCipher.decrypt(bytes)
                ?: error("解密失败：这份备份不是本机密钥加密的（换设备后无法解密网盘备份，请改用配置备份）")
            String(plain, Charsets.UTF_8)
        }
    }

    private fun client(config: Config) = WebDavClient(config.targetUrl, config.user, config.pass)

    private fun <T> notConfigured(): Result<T> =
        Result.failure(IllegalStateException("未配置网盘：请先填写服务器地址、账号与应用密码"))
}
