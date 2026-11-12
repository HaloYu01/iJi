package com.iji.app.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 解析历史的持久化（SharedPreferences + JSON，与 [LocalRuleStore] 同款）。
 *
 * 为什么不用 Room：历史只是「最近 20 条解析结果」的环形缓存，供误触清空后找回、
 * 一键重新载入，没有联表与聚合查询；SharedPreferences 足够，也不动数据库版本。
 *
 * 存的是**解析结果**而非已入账账单：目的是找回解析现场，重新载入后用户可继续编辑。
 */
object AiParseHistoryStore {

    /** 单条历史：解析结果 + 产生时间（毫秒），时间即列表 key */
    data class AiParseRecord(
        val bill: AiBookkeepingService.BillResult,
        val time: Long,
    )

    private const val PREF = "iji_ai_parse_history"
    private const val KEY = "history_v1"
    /** 公开给界面展示，避免「仅保留最近 20 条」在页面里再硬编码一份 */
    const val MAX = 20

    @Volatile
    private var cache: List<AiParseRecord> = emptyList()

    @Volatile
    private var initialized: Boolean = false

    /** 全部历史（新的在前）。传 Context 会顺带做一次懒初始化 */
    fun all(context: Context? = null): List<AiParseRecord> {
        if (context != null && !initialized) init(context)
        return cache
    }

    /** 由页面首次进入时调用：读回持久化历史 */
    fun init(context: Context) = synchronized(this) {
        if (initialized) return@synchronized
        val raw = prefs(context).getString(KEY, null).orEmpty()
        cache = historyFromJson(raw)
        initialized = true
    }

    /** 记一条解析结果（新的在前，超出 [MAX] 淘汰最旧的） */
    fun record(context: Context, bill: AiBookkeepingService.BillResult): Boolean =
        synchronized(this) {
            val next = (listOf(AiParseRecord(bill, System.currentTimeMillis())) + all(context))
                .take(MAX)
            write(context, next)
        }

    /** 清空全部历史 */
    fun clear(context: Context): Boolean = synchronized(this) {
        write(context, emptyList())
    }

    // ── 持久化 ──

    private fun write(context: Context, records: List<AiParseRecord>): Boolean = runCatching {
        val ok = prefs(context).edit()
            .putString(KEY, historyToJson(records))
            .commit()
        if (ok) cache = records
        ok
    }.getOrDefault(false)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}

/** 历史列表 → JSON */
internal fun historyToJson(records: List<AiParseHistoryStore.AiParseRecord>): String {
    val arr = JSONArray()
    records.forEach { rec ->
        arr.put(
            JSONObject().apply {
                put("time", rec.time)
                put("type", rec.bill.type)
                put("category", rec.bill.categoryHint)
                put("amountCents", rec.bill.amountCents)
                put("note", rec.bill.note)
                put("account", rec.bill.accountHint)
                put("tags", JSONArray(rec.bill.tags))
                put("source", rec.bill.source)
            },
        )
    }
    return arr.toString()
}

/**
 * JSON → 历史列表。容错对齐 [LocalRuleStore]：字段缺失走默认值、
 * 整段不是 JSON 就当空列表 —— 历史只是临时缓存，坏了直接丢，不影响任何主流程。
 */
internal fun historyFromJson(raw: String): List<AiParseHistoryStore.AiParseRecord> =
    runCatching {
        val arr = JSONArray(raw.trim())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            AiParseHistoryStore.AiParseRecord(
                bill = AiBookkeepingService.BillResult(
                    type = o.optString("type", "expense"),
                    categoryHint = o.optString("category", "其他"),
                    amountCents = o.optLong("amountCents"),
                    note = o.optString("note"),
                    accountHint = o.optString("account"),
                    tags = o.optJSONArray("tags")?.let { tags ->
                        (0 until tags.length()).mapNotNull { j ->
                            tags.optString(j).takeIf { it.isNotBlank() }
                        }
                    }.orEmpty(),
                    source = o.optString("source"),
                ),
                time = o.optLong("time"),
            )
        }
    }.getOrDefault(emptyList())
