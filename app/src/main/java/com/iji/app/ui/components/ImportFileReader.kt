package com.iji.app.ui.components

import android.content.Context
import android.net.Uri
import java.nio.charset.Charset

/**
 * 账单文件读取结果。
 *
 * 之所以要区分「Excel 文件」和「读失败」，是因为过去一律 `bufferedReader().readText()`：
 * 用户当真选了 `.xlsx`，读出来是一堆控制字符，解析结果「成功 0 条、失败 0 条」——
 * 既没说清为什么不支持，也没提示改用 CSV，只能靠用户自己猜。
 */
sealed interface ImportFileResult {
    /** 读到了文本内容（已去 BOM、已尝试修复乱码） */
    data class Text(val content: String, val encoding: String) : ImportFileResult

    /** 是 Excel 二进制文件（.xlsx / .xls），本项目不解析二进制表格 */
    data object ExcelBinary : ImportFileResult

    /** 空文件或读取失败 */
    data class Failure(val reason: String) : ImportFileResult
}

/**
 * 读取账单导入文件。
 *
 * 支持：CSV / TXT 等纯文本账单（微信、支付宝导出的都是 CSV）。
 * 不支持：`.xlsx`（zip 容器）/ `.xls`（OLE2 复合文档）—— 识别出来直接给出可操作的提示，
 * 而不是把二进制当文本硬塞给解析器。
 *
 * 编码：优先 UTF-8；若出现替换字符（U+FFFD）再按 GBK 试一次 —— 国内银行导出
 * 仍有大量 GBK 文件，不兜这一下会整列乱码。
 */
fun readImportFile(context: Context, uri: Uri): ImportFileResult {
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return ImportFileResult.Failure("无法读取文件")

    if (bytes.isEmpty()) return ImportFileResult.Failure("文件是空的")

    if (isExcelBinary(bytes)) return ImportFileResult.ExcelBinary

    val utf8 = String(bytes, Charsets.UTF_8)
    if ('\uFFFD' in utf8) {
        val gbk = runCatching { String(bytes, Charset.forName("GBK")) }.getOrNull()
        if (gbk != null && '\uFFFD' !in gbk) {
            return ImportFileResult.Text(gbk.removePrefix("\uFEFF"), "GBK")
        }
    }
    return ImportFileResult.Text(utf8.removePrefix("\uFEFF"), "UTF-8")
}

/** `.xlsx` 是 zip（`PK`），`.xls` 是 OLE2 复合文档（`D0 CF 11 E0`） */
private fun isExcelBinary(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    val zip = bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
    val ole2 = bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
        bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
    return zip || ole2
}

/**
 * 文件选择器的 MIME 过滤。
 *
 * 只筛文本类：很多 CSV 导出会把自己标成 `application/vnd.ms-excel`，
 * 所以不能只写 `text/csv`；而写「全部文件」会把 .xlsx 也放进来，选完才发现不支持。
 */
val IMPORT_MIME_TYPES = arrayOf(
    "text/*",
    "text/csv",
    "application/csv",
    "application/vnd.ms-excel",
)

/** Excel 不支持时给用户看的统一提示语 */
const val EXCEL_NOT_SUPPORTED_HINT =
    "这是 Excel 文件（.xlsx/.xls），暂不支持。请在导出账单时选择「CSV / 文本」格式后再导入。"

/**
 * SAF 选中文件的显示名。
 *
 * `uri.lastPathSegment` 拿到的是 documentId（形如 `msf:358`），导入历史里存这个
 * 用户根本认不出导的是哪份；必须查 DISPLAY_NAME。
 */
fun safeDisplayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver
            .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
        ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
