package com.iji.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iji.app.data.model.PaymentSplit
import com.iji.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * 记账模板：把「每天都要记的同一笔」固化成一键录入。
 *
 * 模板是**完整预设**：除了基本的类型 / 分类 / 金额，还带记一笔面板的「计入标记」
 * （收支 / 预算）、组合付款拆分与优惠——应用模板时这些字段整体套用（见
 * AddTransactionSheet.applyTemplate），而不是只填空格。
 */
data class TransactionTemplate(
    val id: Long = 0L,
    /** 模板名，如「早餐」「地铁通勤」 */
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: String = "",
    val amountCents: Long = 0L,
    val note: String = "",
    val account: String = "",
    val tags: List<String> = emptyList(),
    /** 不计入收支统计（面板「计入标记」的 excludeFromStats） */
    val excludeFromStats: Boolean = false,
    /** 不计入预算进度（面板「计入标记」的 excludeFromBudget） */
    val excludeFromBudget: Boolean = false,
    /** 组合付款拆分；≥2 条有效项时金额由多个账户共同承担，仅支出 / 收入有意义 */
    val splits: List<PaymentSplit> = emptyList(),
    /** 优惠金额（分）；> 0 时应用模板会自动展开面板的优惠录入 */
    val discountCents: Long = 0L,
)

private val Context.templateDataStore by preferencesDataStore(name = "iji_templates")

private val KEY_TEMPLATES = stringPreferencesKey("templates")

/**
 * 记账模板仓库（Preferences DataStore）。
 *
 * 独立 DataStore 文件，避免与 `iji_settings`（SettingsRepository）互相干扰；
 * 以 Flow 暴露，模板页改动后界面即时刷新，无需手动回读。
 */
object TemplateRepository {

    fun observe(context: Context): Flow<List<TransactionTemplate>> =
        context.templateDataStore.data.map { decode(it[KEY_TEMPLATES].orEmpty()) }

    suspend fun upsert(context: Context, template: TransactionTemplate) {
        mutate(context) { list ->
            val next = if (template.id == 0L) {
                val newId = (list.maxOfOrNull { it.id } ?: 0L) + 1L
                list + template.copy(id = newId)
            } else {
                list.map { if (it.id == template.id) template else it }
            }
            next
        }
    }

    suspend fun remove(context: Context, id: Long) {
        mutate(context) { list -> list.filterNot { it.id == id } }
    }

    /**
     * 删除分类时把模板的引用改挂到新分类（分类删除的引用迁移之一）。
     *
     * 模板的 categoryId 是自由字符串，不迁的话模板会指向一个已不存在的分类，
     * 用它记出来的账同样落在「未分类」。
     */
    suspend fun reassignCategory(context: Context, fromIds: Set<String>, toId: String) {
        if (fromIds.isEmpty()) return
        mutate(context) { list ->
            list.map { if (it.categoryId in fromIds) it.copy(categoryId = toId) else it }
        }
    }

    private suspend fun mutate(
        context: Context,
        transform: (List<TransactionTemplate>) -> List<TransactionTemplate>,
    ) {
        context.templateDataStore.edit { prefs ->
            val current = decode(prefs[KEY_TEMPLATES].orEmpty())
            prefs[KEY_TEMPLATES] = encode(transform(current))
        }
    }

    /** 一次性读取（非组合场景，如记账时取默认模板） */
    suspend fun once(context: Context): List<TransactionTemplate> = observe(context).first()
}

// ── JSON 编解码（org.json，随系统提供，无需额外依赖）──

private fun encode(list: List<TransactionTemplate>): String =
    JSONArray().apply {
        list.forEach { t ->
            put(
                JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("type", t.type.name)
                    put("categoryId", t.categoryId)
                    put("amountCents", t.amountCents)
                    put("note", t.note)
                    put("account", t.account)
                    put("tags", JSONArray().apply { t.tags.forEach { put(it) } })
                    // 新增字段键名与记一笔草稿（AddBillDraft）对齐：exStats / exBudget / discount
                    put("exStats", t.excludeFromStats)
                    put("exBudget", t.excludeFromBudget)
                    put(
                        "splits",
                        JSONArray().apply {
                            t.splits.forEach { s ->
                                put(
                                    JSONObject().apply {
                                        put("account", s.account)
                                        put("amountCents", s.amountCents)
                                    },
                                )
                            }
                        },
                    )
                    put("discount", t.discountCents)
                },
            )
        }
    }.toString()

private fun decode(raw: String): List<TransactionTemplate> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            TransactionTemplate(
                id = o.optLong("id"),
                name = o.optString("name"),
                type = runCatching { TransactionType.valueOf(o.optString("type", "EXPENSE")) }
                    .getOrDefault(TransactionType.EXPENSE),
                categoryId = o.optString("categoryId"),
                amountCents = o.optLong("amountCents"),
                note = o.optString("note"),
                account = o.optString("account"),
                tags = o.optJSONArray("tags")?.let { tags ->
                    List(tags.length()) { tags.optString(it) }.filter { it.isNotBlank() }
                }.orEmpty(),
                // 旧 JSON 没有这些键 → 全部走默认值，向后兼容
                excludeFromStats = o.optBoolean("exStats"),
                excludeFromBudget = o.optBoolean("exBudget"),
                splits = o.optJSONArray("splits")?.let { arr ->
                    List(arr.length()) { j ->
                        arr.optJSONObject(j)?.let {
                            PaymentSplit(
                                account = it.optString("account"),
                                amountCents = it.optLong("amountCents"),
                            )
                        }
                    }.filterNotNull().filter { it.account.isNotBlank() }
                }.orEmpty(),
                discountCents = o.optLong("discount"),
            )
        }
    }.getOrDefault(emptyList())
}
