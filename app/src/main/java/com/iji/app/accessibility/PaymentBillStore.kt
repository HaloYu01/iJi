package com.iji.app.accessibility

import android.content.Context
import com.iji.app.ai.AiBookkeepingService
import org.json.JSONObject

/**
 * 待确认账单的暂存。
 *
 * 通知的「记一笔」动作可能在**应用进程已被杀死**时触发，因此不能只靠内存传递，
 * 必须落盘；点击后由 Activity 读取并消费（一次性）。
 */
object PaymentBillStore {

    private const val PREF = "iji_pending_bill"
    private const val KEY = "bill"

    fun put(
        context: Context,
        type: String,
        amountCents: Long,
        merchant: String,
        accountHint: String,
        sourceLabel: String = "",
    ) {
        val json = JSONObject().apply {
            put("type", type)
            put("amountCents", amountCents)
            put("merchant", merchant)
            put("accountHint", accountHint)
            put("source", sourceLabel)
        }.toString()
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, json).apply()
    }

    /** 读取并清除（一次性消费，避免下次冷启动又弹出同一笔） */
    fun consume(context: Context): AiBookkeepingService.BillResult? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return null
        prefs.edit().remove(KEY).apply()
        return runCatching {
            val o = JSONObject(raw)
            val merchant = o.optString("merchant")
            // 备注按用户在「记账偏好」里配的模板生成（默认 {商户}，可带 {来源}）
            val note = AutoBookkeepingPrefs.buildNote(
                template = AutoBookkeepingPrefs.noteTemplate(context),
                merchant = merchant,
                source = o.optString("source"),
            ).ifBlank { "自动识别" }
            AiBookkeepingService.BillResult(
                type = o.optString("type", "expense"),
                // 无障碍解析不出分类，交由记账面板按「其他」预填，用户可在面板里改
                categoryHint = "其他",
                amountCents = o.optLong("amountCents"),
                note = note,
                accountHint = o.optString("accountHint"),
                tags = emptyList(),
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}