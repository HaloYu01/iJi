package com.iji.app.ui.overlay

import com.iji.app.ai.BillDraft
import com.iji.app.data.model.TransactionType
import java.time.LocalDate
import java.util.Locale

/**
 * 详细悬浮卡片的纯展示口径（不依赖 Compose，便于单测）。
 *
 * 金额符号完全照设计稿：支出 `-10.00`、收入 `+10.00`、转账 `=10.00`。
 * 日期快捷项只有今天 / 昨天 / 前天（卡片里不放完整日历，避免在他人应用上盖住半屏）。
 */
internal object OverlayFormat {

    fun typeLabel(type: TransactionType): String = when (type) {
        TransactionType.INCOME -> "收入"
        TransactionType.TRANSFER -> "转账"
        else -> "支出"
    }

    fun amountLabel(type: TransactionType, amountCents: Long): String {
        val sign = when (type) {
            TransactionType.INCOME -> "+"
            TransactionType.TRANSFER -> "="
            else -> "-"
        }
        return sign + String.format(Locale.CHINA, "%.2f", amountCents / 100.0)
    }

    fun amountLabel(draft: BillDraft): String = amountLabel(draft.type, draft.amountCents)

    /** 当天的第几分钟 → `HH:mm`；-1（未指定）返回空串 */
    fun timeLabel(minuteOfDay: Int): String {
        if (minuteOfDay < 0) return ""
        val safe = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        return String.format(Locale.CHINA, "%02d:%02d", safe / 60, safe % 60)
    }

    /** 账单时间展示：有时分 → `2026-11-03 13:03`；只有日期 → `2026-11-03` */
    fun dateTimeLabel(date: LocalDate, minuteOfDay: Int): String {
        val time = timeLabel(minuteOfDay)
        return if (time.isEmpty()) date.toString() else "$date $time"
    }

    /** 分 → 输入框文本（整数金额不带 `.0`，避免用户改金额时先删小数点） */
    fun centsToPlain(cents: Long): String {
        if (cents == 0L) return ""
        val value = cents / 100.0
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }

    /** 金额输入过滤：只留数字与第一个小数点 */
    fun sanitizeAmount(raw: String): String = buildString {
        var dotSeen = false
        for (c in raw) {
            when {
                c.isDigit() -> append(c)
                c == '.' && !dotSeen -> {
                    append(c)
                    dotSeen = true
                }
            }
        }
    }
}
