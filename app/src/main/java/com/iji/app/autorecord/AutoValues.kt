package com.iji.app.autorecord

import java.util.Calendar

/**
 * 字段清洗：金额 / 时间 / 备注。
 *
 * 全部是纯文本处理，与无障碍、截屏都无关，便于单独调整——
 * 各平台的金额写法差异（`¥12.00`、`-12.00`、`12.00元`、`已支付12.00`）都在这里收口。
 */
object AutoValues {

    /** 宽松金额：允许 1~2 位小数、千分位、货币符号、正负号、`元` 后缀 */
    private val AMOUNT_LOOSE = Regex(
        """^(?:已支付|支出|收入)?\s*(?:[-+][¥￥]|[¥￥][-+]|[-+]|[¥￥])?""" +
            """(?:\d{1,3}(?:,\d{3}){0,2}|\d{1,9})(?:\.\d{1,2})?(?:元)?$""",
    )

    /** 严格金额：必须两位小数（账单详情的金额几乎总是两位，能挡掉时间与编号） */
    private val AMOUNT_STRICT = Regex(
        """^(?:已支付|支出|收入)?\s*(?:[-+][¥￥]|[¥￥][-+]|[-+]|[¥￥])?""" +
            """(?:\d{1,3}(?:,\d{3}){0,2}|\d{1,9})\.\d{2}(?:元)?$""",
    )

    /** 时间：`2026-11-14 12:30`、`2026年11月14日`、`2026/11/14 12:30:00` */
    private val TIME = Regex(
        """^\d{4}[-./年]\d{1,2}[-./月]\d{1,2}(?:日)?(?:\s*\d{2}:\d{2}(?::\d{2})?)?$""",
    )

    /**
     * 界面文本里的不可见字符与异体符号。
     *
     * 真机支付宝/抖音的金额节点常带零宽空格（U+200B~200F）或用全角负号（U+FF0D）/数学负号（U+2212）。
     * 原样拿去匹配 `^-?\d+\.\d{2}$` 会整条规则不命中——表现就是「同一个账单页，有的识别有的不识别」。
     */
    private val INVISIBLE = Regex("""[\u200B-\u200F\uFEFF\u2060]""")

    /** 识别前的统一清洗：去零宽字符、负号归一、全角空格归一 */
    fun normalize(text: String): String = INVISIBLE.replace(text, "")
        .replace('\uFF0D', '-')
        .replace('\u2212', '-')
        .replace('\u00A0', ' ')
        .trim()

    /** 无年份时间：`11月14日 12:30`（按当前年补全） */
    private val TIME_NO_YEAR = Regex(
        """^(\d{1,2})[-./月](\d{1,2})(?:日)?(?:\s*(\d{2}):(\d{2})(?::(\d{2}))?)?$""",
    )

    fun isAmount(text: String, strict: Boolean = false): Boolean {
        val trimmed = normalize(text)
        if (trimmed.isEmpty() || trimmed.length > 24) return false
        return (if (strict) AMOUNT_STRICT else AMOUNT_LOOSE).matches(trimmed)
    }

    /**
     * 金额 → 分（绝对值）。
     *
     * 与钱迹的清洗顺序一致：先去 `+`，再删掉 `已支付 / 支出 / 收入 / 元`
     * 与货币符号、千分位、空白，最后转数并取绝对值——收支方向由页面文案判定，不由符号决定。
     */
    fun amountCents(text: String): Long? {
        var s = normalize(text).replace("+", "")
        s = s.replace("已支付", "").replace("支出", "").replace("收入", "")
        s = s.replace("元", "").replace("¥", "").replace("￥", "")
            .replace(",", "").replace(" ", "")
        val value = s.toDoubleOrNull() ?: return null
        val cents = Math.round(Math.abs(value) * 100.0)
        return if (cents in 1..100_000_000L) cents else null
    }

    fun isTime(text: String): Boolean {
        val trimmed = normalize(text)
        if (TIME.matches(trimmed) || TIME_NO_YEAR.matches(trimmed)) return true
        // 「支付时间 2026-11-14 12:30」这类标签与值同节点：只要短文本里含完整日期段就认
        return trimmed.length <= 32 && TIME.containsMatchIn(trimmed)
    }

    /**
     * 时间文本 → 毫秒；解析不了返回 0（与钱迹一致：宁可没有时间，也不猜一个错的时间）。
     *
     * 支持整句纯时间，也支持「支付时间 2026-11-14 12:30」这种标签与值粘连的文本——
     * 真机上这两个字段经常渲染在同一个 TextView 里，只认整句会丢掉一半页面的时间。
     */
    fun parseTime(text: String): Long {
        val trimmed = normalize(text)
        if (trimmed.isEmpty()) return 0L
        TIME.matchEntire(trimmed)?.let { return buildFullTime(trimmed) }
        TIME.find(trimmed)?.let { return buildFullTime(it.value) }
        TIME_NO_YEAR.matchEntire(trimmed)?.let { return buildNoYear(it.value) }
        TIME_NO_YEAR.find(trimmed)?.let { return buildNoYear(it.value) }
        return 0L
    }

    private fun buildFullTime(value: String): Long {
        val normalized = value
            .replace("年", "-").replace("月", "-").replace("日", "")
            .replace("/", "-").replace(".", "-")
        val parts = normalized.split(' ').filter { it.isNotEmpty() }
        val datePart = parts[0].split('-').filter { it.isNotEmpty() }
        if (datePart.size < 3) return 0L
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, datePart[0].toIntOrNull() ?: return 0L)
            set(Calendar.MONTH, (datePart[1].toIntOrNull() ?: return 0L) - 1)
            set(Calendar.DAY_OF_MONTH, datePart[2].toIntOrNull() ?: return 0L)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        parts.getOrNull(1)?.split(':')?.let { timePart ->
            timePart.getOrNull(0)?.toIntOrNull()?.let { calendar.set(Calendar.HOUR_OF_DAY, it) }
            timePart.getOrNull(1)?.toIntOrNull()?.let { calendar.set(Calendar.MINUTE, it) }
            timePart.getOrNull(2)?.toIntOrNull()?.let { calendar.set(Calendar.SECOND, it) }
        }
        return calendar.timeInMillis
    }

    private fun buildNoYear(value: String): Long {
        val noYear = TIME_NO_YEAR.matchEntire(value) ?: return 0L
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
            set(Calendar.MONTH, (noYear.groupValues[1].toIntOrNull() ?: return 0L) - 1)
            set(Calendar.DAY_OF_MONTH, noYear.groupValues[2].toIntOrNull() ?: return 0L)
            set(Calendar.HOUR_OF_DAY, noYear.groupValues[3].toIntOrNull() ?: 0)
            set(Calendar.MINUTE, noYear.groupValues[4].toIntOrNull() ?: 0)
            set(Calendar.SECOND, noYear.groupValues[5].toIntOrNull() ?: 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /** 备注清洗：纯字母数字的长串（订单号 / 交易号）不是备注 */
    fun cleanRemark(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.length > 20 && text.all { it.isLetterOrDigit() } && text.none { it in '\u4e00'..'\u9fa5' }) {
            return ""
        }
        return text
    }

    /** 从「美团订单」「京东购物」这类文案里剥掉前缀，只留内容 */
    fun stripPrefix(text: String?, prefix: String): String {
        val value = text?.trim().orEmpty()
        return if (value.startsWith(prefix)) value.removePrefix(prefix).trim() else value
    }
}
