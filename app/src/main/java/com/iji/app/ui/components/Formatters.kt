package com.iji.app.ui.components

import com.iji.app.data.model.Currency
import com.iji.app.data.model.CurrencyDisplay
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 金额格式：正数 ¥1,234.56，负数 -¥1,234.56（负号置于货币符号前）。
 *
 * 符号取自 [currency]，默认是**当前账本的展示币种**（见 [CurrencyDisplay]）——
 * 币种是账本级的，同一个账本内的所有金额必然同币种，所以这里不需要每个调用点透传。
 * 只有「明确知道不是当前账本」的场景（如导出另一本账）才显式传 [currency]。
 */
fun formatMoney(cents: Long, currency: Currency = CurrencyDisplay.current): String {
    val abs = kotlin.math.abs(cents / 100.0)
    val sign = if (cents < 0) "-" else ""
    return sign + String.format(Locale.US, "${currency.symbol}%,.2f", abs)
}

/**
 * 带正负号的金额：支出 `-¥12.00`，收入 `+¥12.00`，转账无符号。
 *
 * 金额本身可能为负（退款 / 红字冲销），此时符号要反过来，不能再叠一层负号 ——
 * 这也是**唯一的**符号来源，别在别处再写一遍 `"-" + formatMoney(...)`：
 * 对负数会得到 `--¥12.00`。
 */
fun formatSignedMoney(type: TransactionType, cents: Long): String {
    val abs = formatMoney(kotlin.math.abs(cents))
    return when (type) {
        TransactionType.EXPENSE -> if (cents < 0) "+$abs" else "-$abs"
        TransactionType.INCOME -> if (cents < 0) "-$abs" else "+$abs"
        TransactionType.TRANSFER -> abs
    }
}

fun formatSignedMoney(tx: Transaction): String = formatSignedMoney(tx.type, tx.amountCents)

/** 日期格式：9月18日 星期五（今天/昨天做友好化） */
fun formatDay(date: LocalDate): String {
    val today = LocalDate.now()
    val prefix = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> ""
    }
    val text = date.format(DateTimeFormatter.ofPattern("M月d日"))
    val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINESE)
    return if (prefix.isEmpty()) "$text $weekday" else "$prefix $weekday"
}

/**
 * 把用户输入的金额字符串解析为「分」。
 *
 * 解析策略：整数部分必须合法，否则返回 0；小数部分非法（如 `12.` / `12.abc`）按 0 分处理、
 * **保留整数部分** —— 金额输入框只会送进数字与小数点，半成品输入（`12.`）不能被当成 0 元。
 * 超过两位小数只取前两位（`12.999` → 1299 分）。
 *
 * **负数**：允许（负债账户的初始余额就是负数，如信用卡欠款 `-1200.50` → `-120050` 分）。
 * 负号只认最前面一个；`-0.50` 同样按负数处理（先算绝对值再取负），
 * 否则会被算成 +50 分。
 */
fun parseCents(text: String): Long {
    val clean = text.replace(",", "").trim()
    if (clean.isEmpty()) return 0L
    val negative = clean.startsWith("-")
    val body = if (negative) clean.drop(1) else clean
    val parts = body.split(".")
    val yuan = parts[0].toLongOrNull() ?: return 0L
    val cents = if (parts.size > 1) {
        (parts[1] + "00").take(2).toLongOrNull() ?: 0L
    } else {
        0L
    }
    val total = yuan * 100 + cents
    return if (negative) -total else total
}

/**
 * 清洗金额输入框的文本：只保留数字与**一个**小数点，小数最多两位，最前面允许一个负号。
 *
 * 用途是「边输入边过滤」——不能等到提交才校验，否则用户会看到自己打的字凭空消失。
 * 负号是负债账户的必需项（信用卡初始余额为负），因此这里放开首位负号。
 *
 * 长度上限 12（含负号与小数点），小数位上限 2（金额以「分」为最小单位，
 * 第三位小数存不进去，边输边截断比提交时静默丢弃更可预期）。
 */
fun sanitizeAmountInput(input: String): String {
    val raw = input.trim()
    val negative = raw.startsWith("-")
    val body = if (negative) raw.drop(1) else raw
    val digits = body.filter { it.isDigit() || it == '.' }
    // 多个小数点只保留第一个，小数位最多两位
    val dot = digits.indexOf('.')
    val normalized = if (dot >= 0) {
        val intPart = digits.substring(0, dot + 1)
        val decimal = digits.substring(dot + 1).replace(".", "").take(2)
        intPart + decimal
    } else {
        digits
    }
    // 负号也占一位
    val limit = if (negative) 11 else 12
    val limited = normalized.take(limit)
    return (if (negative) "-" else "") + limited
}
