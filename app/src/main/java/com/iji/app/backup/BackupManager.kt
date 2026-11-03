package com.iji.app.backup

import android.content.Context
import android.util.Log
import com.iji.app.data.db.DbProvider
import com.iji.app.data.repository.BackupRepository
import com.iji.app.data.repository.ConfigBackupRepository
import com.iji.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 加密备份的落盘与读取。
 *
 * 备份内容 = **完整快照**（配置 + 交易流水），一次搞定换机/误删恢复：
 * - 配置部分复用 [ConfigBackupRepository]，覆盖分类/标签/账户/账本/预算/模板/周期规则/偏好
 * - 流水部分复用 [BackupRepository.toBackupJson] 的既有格式（还原时直接走已有的 JSON 导入）
 *
 * 文件落在 `getExternalFilesDir("backups")`：应用专属外部目录，无需任何存储权限，
 * 但可被文件管理器 / adb 取出（用户需要能带走自己的数据）。
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val DIR = "backups"
    private const val PREFIX = "iji_backup_"
    private const val EXT = ".ijbak"

    /** 保留最近 N 份，超出自动删最旧的（备份文件会随流水增长，不限量会吃满存储） */
    const val KEEP_COUNT = 7

    fun backupDir(context: Context): File? =
        context.getExternalFilesDir(DIR)?.also { if (!it.exists()) it.mkdirs() }

    /** 构建完整快照（明文 JSON） */
    suspend fun buildSnapshot(context: Context): String {
        val configJson = ConfigBackupRepository.exportJson(context)
        val repository = TransactionRepository(DbProvider.database.transactionDao())
        val transactions = repository.transactions.first()
        val txJson = BackupRepository().toBackupJson(transactions)

        return JSONObject().apply {
            put("kind", "iji-full")
            put("version", 1)
            put("app", "i记")
            put("exportTime", System.currentTimeMillis())
            put("transactionCount", transactions.size)
            put("config", JSONObject(configJson))
            put("transactions", JSONArray(txJson))
        }.toString()
    }

    /** 备份文件名（本地与网盘共用同一命名约定，便于人工核对「云上那份是哪次」） */
    fun newBackupName(nowMillis: Long = System.currentTimeMillis()): String = "$PREFIX$nowMillis$EXT"

    /** 执行一次加密备份；成功返回 true */
    suspend fun writeAutoBackup(context: Context): Boolean {
        val dir = backupDir(context) ?: run {
            Log.w(TAG, "外部目录不可用，跳过备份")
            return false
        }
        val encrypted = BackupCipher.encrypt(buildSnapshot(context).toByteArray(Charsets.UTF_8))
        if (encrypted == null) {
            Log.w(TAG, "加密失败，未写入备份")
            return false
        }
        val file = File(dir, newBackupName())
        return runCatching { file.writeBytes(encrypted) }
            .onFailure { Log.w(TAG, "写入备份失败", it) }
            .onSuccess { prune(dir) }
            .isSuccess
    }

    /**
     * 还原一份完整快照：配置按「合并」方式还原（同名跳过），流水**追加**导入。
     *
     * 本地备份恢复与网盘备份恢复共用这一份逻辑 —— 两处各写一遍迟早会走偏
     * （比如一边还原了配置、另一边忘了）。
     *
     * @param importTransactions 把流水 JSON 数组交给 ViewModel 导入，返回 (成功, 失败)
     * @return 可直接展示给用户的摘要
     */
    suspend fun restoreSnapshot(
        context: Context,
        snapshotJson: String,
        importTransactions: (String) -> Pair<Int, Int>,
    ): String {
        val root = JSONObject(snapshotJson)
        val configText = root.optJSONObject("config")?.toString()
        val configResult = configText?.let { ConfigBackupRepository.importJson(context, it) }
        val txArray = root.optJSONArray("transactions")
        var txOk = 0
        var txFailed = 0
        if (txArray != null && txArray.length() > 0) {
            val result = importTransactions(txArray.toString())
            txOk = result.first
            txFailed = result.second
        }
        return buildString {
            append("配置还原 ${configResult?.total ?: 0} 项")
            if (txOk > 0) append("；流水导入 $txOk 条")
            if (txFailed > 0) append("（$txFailed 条失败）")
        }
    }

    /** 全部备份，最新在前 */
    fun listBackups(context: Context): List<File> =
        backupDir(context)
            ?.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(EXT) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun latestBackup(context: Context): File? = listBackups(context).firstOrNull()

    /** 最近一次备份时间（毫秒）；无备份返回 0 */
    fun latestBackupTime(context: Context): Long = latestBackup(context)?.lastModified() ?: 0L

    /** 读取并解密指定备份；失败返回 null */
    fun readSnapshot(context: Context, file: File): String? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val plain = BackupCipher.decrypt(bytes) ?: return null
        return String(plain, Charsets.UTF_8)
    }

    /** 备份总占用（字节） */
    fun totalSize(context: Context): Long = listBackups(context).sumOf { it.length() }

    fun deleteBackup(context: Context, file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    /** 超出 KEEP_COUNT 的旧备份从最旧开始删 */
    private fun prune(dir: File) {
        val files = dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(EXT) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        files.drop(KEEP_COUNT).forEach { old ->
            runCatching { old.delete() }
                .onFailure { Log.w(TAG, "清理旧备份失败：${old.name}", it) }
        }
    }
}