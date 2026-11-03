package com.iji.app.data.repository

import com.iji.app.data.model.Currency
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * 账单 CSV / JSON 备份：纯序列化与解析，不持有数据库写入能力。
 * 导入结果以 [ImportResult] 返回，由调用方（ViewModel）负责写入并联动账户余额。
 * 全部为纯函数，可脱离 Android 框架独立单测。
 */
class BackupRepository {

    data class ImportResult(val transactions: List<Transaction>, val failedCount: Int)

    companion object {
        /** 导出表头：前 7 列是历史约定（导入端按位置读），新增列只往后加 */
        private val CSV_HEADER = listOf(
            "日期", "类型", "分类", "金额", "账户", "标签", "备注",
            "转入账户", "商家", "对方", "计入收支", "计入预算", "组合付款",
            // 金额列只有裸数字，币种单独成一列：否则导出美元账本后，谁也看不出那些数字是美元
            "币种",
        )
    }

    // ── CSV 导出（通用格式）──
    //
    // 前 7 列的顺序与 [parseCsv] 的历史约定一致（老文件仍能导回），新增列一律追加在后面。
    // 两个必须处理的坑：
    //   1. 不带 BOM 时 Windows Excel 会把 UTF-8 中文读成乱码；
    //   2. 备注里有逗号 / 引号时不做转义，列会被冲散（导入时再回灌就是错的）。
    // 换行统一压成空格：多行备注在表格里本来也读不了，还会让「按行解析」的导入端错位。
    fun toCsv(transactions: List<Transaction>, currencyCode: String = Currency.CNY.code): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append(CSV_HEADER.joinToString(",")).append('\n')
        transactions.forEach { tx ->
            val categoryName = runCatching { CategoryCatalog.of(tx.categoryId).name }.getOrDefault(tx.categoryId)
            val typeName = when (tx.type) {
                TransactionType.EXPENSE -> "支出"
                TransactionType.INCOME -> "收入"
                TransactionType.TRANSFER -> "转账"
            }
            val cells = listOf(
                tx.date.toString(),
                typeName,
                categoryName,
                money(tx.amountCents),
                tx.account,
                tx.tags.joinToString("|"),
                tx.note,
                tx.transferToAccount,
                tx.merchant,
                tx.counterparty,
                if (tx.excludeFromStats) "否" else "是",
                if (tx.excludeFromBudget) "否" else "是",
                tx.splits.joinToString("|") { "${it.account}:${money(it.amountCents)}" },
                Currency.of(currencyCode).code,
            )
            sb.append(cells.joinToString(",") { escapeCsv(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun money(cents: Long): String =
        String.format(Locale.US, "%.2f", cents / 100.0)

    /** 含分隔符 / 引号（/ 换行）时整体加引号，内部引号翻倍 —— RFC 4180 的最小实现 */
    private fun escapeCsv(raw: String): String {
        val s = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        return if (s.any { it == ',' || it == '"' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    // ── CSV 导入（通用格式）──
    fun parseCsv(csv: String): ImportResult {
        // 去掉导出时写的 BOM，否则第一行表头会带着 \uFEFF 被当成数据行
        val lines = csv.removePrefix("\uFEFF").lines().drop(1).filter { it.isNotBlank() }
        val ok = mutableListOf<Transaction>()
        var failed = 0
        lines.forEach { line ->
            try {
                // 用能识别引号的解析，避免「备注里有逗号」的导出文件回灌时错列
                val parts = parseCsvLine(line)
                if (parts.size >= 4) {
                    val date = LocalDate.parse(parts[0].trim())
                    val type = when (parts[1].trim()) {
                        "收入" -> TransactionType.INCOME
                        "转账" -> TransactionType.TRANSFER
                        else -> TransactionType.EXPENSE
                    }
                    val categoryId = resolveCategoryId(parts[2].trim())
                    val amount = (parts[3].trim().toDoubleOrNull() ?: 0.0)
                    val account = if (parts.size > 4) parts[4].trim() else ""
                    val tags = if (parts.size > 5) parts[5].split("|").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
                    val note = if (parts.size > 6) parts[6].trim() else ""
                    ok += Transaction(id = 0L, type = type, categoryId = categoryId, amountCents = (amount * 100).toLong(), note = note, account = account, tags = tags, date = date)
                } else failed++
            } catch (e: Exception) {
                failed++
            }
        }
        return ImportResult(ok, failed)
    }

    /**
     * 分类列兼容两种写法：优先按**分类 id**匹配，再按**分类名**匹配，都没有则原样保留。
     *
     * 手填 CSV 时用户写的是「餐饮」而不是内部 id「food」；若只认 id，
     * 手写导入会全部落到「其他」，模板也就失去意义。
     */
    private fun resolveCategoryId(raw: String): String {
        val all = CategoryCatalog.expenses + CategoryCatalog.incomes + CategoryCatalog.transfers
        return all.firstOrNull { it.id == raw }?.id
            ?: all.firstOrNull { it.name == raw }?.id
            ?: raw
    }

    // ── 微信账单 CSV 导入 ──
    fun parseWeChatCsv(csv: String): ImportResult {
        val lines = csv.lines().filter { it.isNotBlank() }
        val headerIndex = lines.indexOfFirst { it.contains("交易时间") }
        if (headerIndex == -1) return ImportResult(emptyList(), lines.size)

        // 列位置按表头找，不写死下标：微信不同版本的导出里「支付方式 / 当前状态」顺序会互换，
        // 写死会把「当前状态」当成账户名（账单上出现「支付成功」这种账户）
        val header = parseCsvLine(lines[headerIndex])
        fun columnOf(vararg keys: String): Int {
            keys.forEach { key ->
                val index = header.indexOfFirst { it.contains(key) }
                if (index >= 0) return index
            }
            return -1
        }
        val timeCol = columnOf("交易时间").coerceAtLeast(0)
        val noteCol = columnOf("商品", "交易对方").let { if (it >= 0) it else 3 }
        val typeCol = columnOf("收/支", "收支").let { if (it >= 0) it else 4 }
        val amountCol = columnOf("金额").let { if (it >= 0) it else 5 }
        val accountCol = columnOf("支付方式").let { if (it >= 0) it else 6 }
        val requiredMax = maxOf(timeCol, noteCol, typeCol, amountCol)

        val ok = mutableListOf<Transaction>()
        var failed = 0
        for (i in (headerIndex + 1) until lines.size) {
            try {
                val parts = parseCsvLine(lines[i])
                if (parts.size <= requiredMax) {
                    failed++
                    continue
                }
                val date = LocalDate.parse(parts[timeCol].trim().substring(0, 10))
                val type = when (parts[typeCol].trim()) {
                    "收入" -> TransactionType.INCOME
                    "支出" -> TransactionType.EXPENSE
                    else -> TransactionType.TRANSFER
                }
                val amount = parts[amountCol].trim().removePrefix("¥").toDoubleOrNull() ?: 0.0
                // 分类写的是中文名，必须转成分类 id 再落库
                val categoryId = when (type) {
                    TransactionType.INCOME -> resolveCategoryId("工资")
                    TransactionType.EXPENSE -> resolveCategoryId("餐饮")
                    else -> resolveCategoryId("转账")
                }
                val note = parts[noteCol].trim()
                val account = parts.getOrNull(accountCol)?.trim().orEmpty().ifBlank { "微信" }
                ok += Transaction(id = 0L, type = type, categoryId = categoryId, amountCents = (amount * 100).toLong(), note = note, account = account, date = date)
            } catch (e: Exception) {
                failed++
            }
        }
        return ImportResult(ok, failed)
    }

    // ── 支付宝账单 CSV 导入 ──
    fun parseAlipayCsv(csv: String): ImportResult {
        val lines = csv.lines().filter { it.isNotBlank() }
        val headerIndex = lines.indexOfFirst { it.contains("交易号") }
        if (headerIndex == -1) return ImportResult(emptyList(), lines.size)
        val ok = mutableListOf<Transaction>()
        var failed = 0
        for (i in (headerIndex + 1) until lines.size) {
            try {
                val parts = parseCsvLine(lines[i])
                if (parts.size >= 10) {
                    val date = LocalDate.parse(parts[2].trim().substring(0, 10))
                    val type = when (parts[10].trim()) {
                        "收入" -> TransactionType.INCOME
                        "支出" -> TransactionType.EXPENSE
                        else -> TransactionType.TRANSFER
                    }
                    val amount = parts[9].trim().toDoubleOrNull() ?: 0.0
                    // 同上：中文分类名要转成 id
                    val categoryId = when (type) {
                        TransactionType.INCOME -> resolveCategoryId("工资")
                        TransactionType.EXPENSE -> resolveCategoryId("餐饮")
                        else -> resolveCategoryId("转账")
                    }
                    val note = parts[8].trim()
                    val account = "支付宝"
                    ok += Transaction(id = 0L, type = type, categoryId = categoryId, amountCents = (amount * 100).toLong(), note = note, account = account, date = date)
                } else failed++
            } catch (e: Exception) {
                failed++
            }
        }
        return ImportResult(ok, failed)
    }

    // ── 本地备份（JSON，仅交易）──
    fun toBackupJson(transactions: List<Transaction>): String {
        val json = JSONArray()
        transactions.forEach { tx ->
            val obj = JSONObject()
            obj.put("date", tx.date.toString())
            obj.put("type", tx.type.name)
            obj.put("categoryId", tx.categoryId)
            obj.put("amountCents", tx.amountCents)
            obj.put("note", tx.note)
            obj.put("account", tx.account)
            obj.put("transferToAccount", tx.transferToAccount)
            // 借贷的对方资料与核销状态：不写的话恢复后「欠谁 / 怎么联系 / 哪个单位 / 还了没」全丢
            obj.put("counterparty", tx.counterparty)
            obj.put("counterpartyContact", tx.counterpartyContact)
            obj.put("counterpartyOrg", tx.counterpartyOrg)
            obj.put("reimburseState", tx.reimburseState.name)
            // 刻意不导出 refundOfId：它指向的是**本库内**的账单 id，导入时每条都会拿到新 id，
            // 旧引用必然失配、只会变成悬空链接；展示层对「找不到原账单」本就静默降级，
            // 与其写进去误导，不如不写。
            // 组合付款拆分明细：不写的话恢复后组合付款会退化成单账户，余额口径随之错位
            if (tx.splits.isNotEmpty()) {
                obj.put(
                    "splits",
                    org.json.JSONArray().apply {
                        tx.splits.forEach { s ->
                            put(
                                org.json.JSONObject().apply {
                                    put("account", s.account)
                                    put("amountCents", s.amountCents)
                                },
                            )
                        }
                    },
                )
            }
            // 时分：-1 表示只记到天，不写进备份（老版本读不到也多一个无意义字段）
            if (tx.minuteOfDay >= 0) obj.put("minuteOfDay", tx.minuteOfDay)
            obj.put("ledgerId", tx.ledgerId)
            val tagsArr = JSONArray()
            tx.tags.forEach { tagsArr.put(it) }
            obj.put("tags", tagsArr)
            json.put(obj)
        }
        return json.toString()
    }

    // ── 全量备份 ──
    //
    // 「完整备份」统一由 `backup/BackupManager.buildSnapshot()` 产出（配置 + 流水一份快照）。
    // 这里**刻意不再提供**只装流水 + 硬编码 settings（currency="CNY"/theme="light"）的
    // `toFullBackupJson`：那条导出路径换机后会丢掉分类、账户、账本、预算、模板、周期规则与偏好，
    // 而它恰恰是用户最常点的「导出备份」。宁可没有第二个实现，也别留一个看着全、实际缺一半的。

    // ── 备份 JSON 导入 ──
    fun parseBackupJson(jsonStr: String): ImportResult {
        val ok = mutableListOf<Transaction>()
        var failed = 0
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    val date = LocalDate.parse(obj.getString("date"))
                    val type = TransactionType.valueOf(obj.getString("type"))
                    val categoryId = obj.getString("categoryId")
                    val amountCents = obj.getLong("amountCents")
                    val note = obj.optString("note", "")
                    val account = obj.optString("account", "")
                    val tagsArr = obj.getJSONArray("tags")
                    val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }
                    // 读回 ledgerId：导出时写了、这里不读的话，恢复后所有流水都会落到当前账本。
                    // 老备份没有该字段时留空串，由调用方回退到用户选择的账本。
                    val ledgerId = obj.optString("ledgerId", "")
                    // 转账的转入账户：老备份没有该字段时留空串
                    val transferToAccount = obj.optString("transferToAccount", "")
                    // 借贷的对方资料 / 核销状态：老备份缺字段时给空串 / NONE
                    val counterparty = obj.optString("counterparty", "")
                    val counterpartyContact = obj.optString("counterpartyContact", "")
                    val counterpartyOrg = obj.optString("counterpartyOrg", "")
                    val reimburseState = runCatching {
                        com.iji.app.data.model.ReimburseState.valueOf(
                            obj.optString("reimburseState", "NONE"),
                        )
                    }.getOrDefault(com.iji.app.data.model.ReimburseState.NONE)
                    // 组合付款拆分：老备份没有该字段时为「无拆分」
                    val splitsArr = obj.optJSONArray("splits")
                    val splits = if (splitsArr == null) {
                        emptyList()
                    } else {
                        List(splitsArr.length()) { k ->
                            val s = splitsArr.getJSONObject(k)
                            com.iji.app.data.model.PaymentSplit(
                                account = s.optString("account"),
                                amountCents = s.optLong("amountCents"),
                            )
                        }.filter { it.account.isNotBlank() }
                    }
                    // 时分：老备份没有该字段 → -1（只记到天），不做"补 0 点"的猜测
                    val minuteOfDay = obj.optInt("minuteOfDay", -1)
                    ok += Transaction(id = 0L, type = type, categoryId = categoryId, amountCents = amountCents, note = note, account = account, tags = tags, date = date, ledgerId = ledgerId, transferToAccount = transferToAccount, splits = splits, counterparty = counterparty, counterpartyContact = counterpartyContact, counterpartyOrg = counterpartyOrg, reimburseState = reimburseState, minuteOfDay = minuteOfDay)
                } catch (e: Exception) {
                    failed++
                }
            }
        } catch (e: Exception) {
            failed++ // 整体 JSON 损坏：已解析部分保留，标记一次失败
        }
        return ImportResult(ok, failed)
    }

    /** 解析 CSV 行：支持引号包裹、`""` 转义引号、字段内逗号 */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                // 引号内的 "" 是一个字面引号
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result.map { it.trim() }
    }
}
