package com.iji.app.autorecord

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 全自动记账的诊断日志（对齐钱迹的「按 tag 分类 + 文件裁剪」）。
 *
 * 真机上「为什么没识别」「为什么记到了这个账户」只看 logcat 不够——
 * logcat 会被系统刷掉，用户也没法把日志发回来。这里落一个滚动文件：
 *
 * - 位置：`filesDir/diag/auto-bill.log`（卸载即清，不上传）；
 * - 上限 80KB，超了砍掉前一半（保留最近的）；
 * - 未命中时把整页节点 dump 出来，最多每平台 30 秒一次，避免刷爆磁盘。
 *
 * **线程安全**：这里必须假定调用方来自任意线程（主线程解析调度、
 * `auto-bill-parse` 串行线程、`AutoRecordStore` 的协程）。
 * 因此格式化器用线程安全的 [DateTimeFormatter]——早期版本用的是共享
 * `SimpleDateFormat`，多线程并发 `format()` 会让内部 Calendar 状态错乱并抛异常；
 * 那个异常从 `log()` 抛给调用方，直接把 `parseNodes` 的第一行打断，
 * 表现为「调度日志有、解析日志全无、永远识别不了」。
 *
 * 同理，[log] 整体包在 runCatching 里：**日志失败永远不能影响记账本身**。
 */
object AutoDiag {

    private const val TAG = "AutoDiag"
    private const val FILE_NAME = "auto-bill.log"
    private const val MAX_BYTES = 80 * 1024
    private const val DUMP_INTERVAL_MS = 30_000L
    private const val MAX_DUMP_LINES = 600

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val lastDump = HashMap<String, Long>()

    fun log(context: Context, category: String, message: String) {
        // 时间格式化 + 落盘都不能把调用方拖下水（尤其不能打断解析线程）
        runCatching {
            val time = timeFormat.format(Instant.now().atZone(ZoneId.systemDefault()))
            val line = "$time [$category] $message\n"
            Log.d(TAG, "$category: $message")
            scope.launch { runCatching { append(context, line) } }
        }
    }

    /** 未识别时 dump 整页节点：排障时能直接看到规则差在哪一段文本 */
    fun dumpPage(context: Context, platform: String, pageType: String?, nodes: List<AutoNode>) {
        val now = System.currentTimeMillis()
        synchronized(lastDump) {
            val last = lastDump[platform] ?: 0L
            if (now - last < DUMP_INTERVAL_MS) return
            lastDump[platform] = now
        }
        val header = "===== 未识别 ${platform}${pageType?.let { "/$it" } ?: ""} " +
            "nodes=${nodes.size} ====="
        scope.launch {
            runCatching {
                append(context, "\n$header\n")
                nodes.take(MAX_DUMP_LINES).forEach { node ->
                    append(context, "  ${node.index}. ${node.text}\n")
                }
                append(context, "===== end =====\n")
            }
        }
    }

    fun file(context: Context): File = File(context.filesDir, "diag/$FILE_NAME")

    /**
     * 读取最近一段日志，给「自动记账」页的「复制诊断日志」用。
     *
     * 真机上用户不需要 adb 也能把 miss / hit / deliver / overlay 的失败原因发回来——
     * 之前只能 `adb shell run-as`，release 包还拉不到，排障基本靠猜。
     */
    fun readTail(context: Context, maxChars: Int = 8000): String = runCatching {
        val f = file(context)
        if (!f.exists() || f.length() == 0L) return "（暂无诊断日志）"
        f.readText().takeLast(maxChars)
    }.getOrElse { "读取诊断日志失败：${it.message}" }

    fun clear(context: Context) {
        scope.launch { runCatching { file(context).takeIf { it.exists() }?.delete() } }
    }

    private fun append(context: Context, line: String) {
        val file = file(context)
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        if (file.exists() && file.length() + line.length > MAX_BYTES) {
            val kept = file.readText().takeLast(MAX_BYTES / 2)
            file.writeText(kept)
        }
        file.appendText(line)
    }
}
