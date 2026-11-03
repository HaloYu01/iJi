package com.iji.app.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地识别规则的持久化（SharedPreferences + JSON，与 [com.iji.app.data.repository.CategoryStore] 同款）。
 *
 * 为什么不用 Room：规则只有几十条、没有联表查询，而 [rules] 需要在**拿不到 Context** 的地方
 * 被同步读取（[LocalOcrEngine] 在本机 OCR 里直接调引擎）。进程内缓存 + 版本号足够，
 * 也顺带让纯 JVM 单测无需任何 Android 依赖就能跑引擎。
 *
 * 写入一律返回 Boolean（`commit` 同步拿结果），界面才能如实提示「保存失败」。
 */
object LocalRuleStore {

    private const val PREF = "iji_local_rules"
    private const val KEY_RULES = "rules_v1"
    private const val KEY_SEEDED = "seeded_v1"

    @Volatile
    private var version: Int = 0

    /** 是否已经按 Context 初始化过；未初始化时对外提供内置规则 */
    @Volatile
    private var initialized: Boolean = false

    private var cache: List<LocalRule> = LocalRuleDefaults.all

    /** 缓存版本号：规则变化时自增，页面据此重建列表 */
    val cacheVersion: Int get() = version

    /**
     * 当前全部规则（含停用的）。传 Context 会顺带做一次懒初始化 ——
     * 单测 / Compose 预览不传也能拿到内置规则。
     */
    fun rules(context: Context? = null): List<LocalRule> {
        if (context != null && !initialized) init(context)
        return cache
    }

    /** 由 IjiApplication 调用：首次运行写入内置规则，之后读回持久化规则 */
    fun init(context: Context) = synchronized(this) {
        if (initialized) return@synchronized
        val prefs = prefs(context)
        cache = if (prefs.getBoolean(KEY_SEEDED, false)) {
            rulesFromJson(prefs.getString(KEY_RULES, null).orEmpty())
                // 存储被清空 / 解析失败时退回内置规则，避免整个识别链路"没有任何规则"
                .ifEmpty { LocalRuleDefaults.all }
        } else {
            LocalRuleDefaults.all.also { write(context, it) }
        }
        initialized = true
        version++
    }

    /** 整表替换（导入 / 恢复内置都用它） */
    fun save(context: Context, rules: List<LocalRule>): Boolean = synchronized(this) {
        if (!write(context, rules)) return false
        cache = rules
        initialized = true
        version++
        true
    }

    /** 新增或覆盖一条（按 id）；新规则请用 [newRule] 生成 id */
    fun upsert(context: Context, rule: LocalRule): Boolean = synchronized(this) {
        val next = cache.toMutableList()
        val index = next.indexOfFirst { it.id == rule.id }
        if (index >= 0) next[index] = rule else next.add(rule)
        save(context, next)
    }

    fun remove(context: Context, id: String): Boolean = synchronized(this) {
        val next = cache.filterNot { it.id == id }
        if (next.size == cache.size) return false
        save(context, next)
    }

    /** 恢复内置规则：**只补回缺失的内置项**，用户自建规则与对内置项的改动都保留 */
    fun restoreBuiltins(context: Context): Boolean = synchronized(this) {
        val existingIds = cache.map { it.id }.toSet()
        val restored = cache + LocalRuleDefaults.all.filterNot { it.id in existingIds }
        if (restored.size == cache.size) return false
        save(context, restored)
    }

    /** 导出（JSON 文本）；给「导出规则」用 */
    fun exportJson(context: Context): String = rulesToJson(rules(context))

    /**
     * 导入（JSON 文本）：**整表替换**，返回导入的规则条数；格式不合法返回 -1。
     *
     * 替换而不是合并：用户导出的就是"一整套规则"，合并会留下两套同名规则互相抢命中。
     * 界面必须在调用前确认一次。
     */
    fun importJson(context: Context, raw: String): Int {
        val parsed = runCatching { rulesFromJson(raw) }.getOrNull().orEmpty()
        if (parsed.isEmpty()) return -1
        return if (save(context, parsed)) parsed.size else -1
    }

    /** 新规则 id：时间戳足够（同一毫秒内连点两次的概率可忽略，且同 id 只会覆盖自己） */
    fun newRuleId(): String = "user-${System.currentTimeMillis()}"

    // ── 持久化 ──

    private fun write(context: Context, rules: List<LocalRule>): Boolean = runCatching {
        prefs(context).edit()
            .putString(KEY_RULES, rulesToJson(rules))
            .putBoolean(KEY_SEEDED, true)
            .commit()
    }.getOrDefault(false)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}

/** 规则包 JSON 版本号：字段改名时靠它决定是否迁移 */
internal const val LOCAL_RULE_SCHEMA = 1

/** 规则列表 → JSON（纯函数，可单测） */
internal fun rulesToJson(rules: List<LocalRule>): String {
    val arr = JSONArray()
    rules.forEach { rule ->
        arr.put(
            JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("type", rule.type)
                put("category", rule.category)
                put("account", rule.account)
                put("keywords", JSONArray(rule.keywords))
                put("enabled", rule.enabled)
                put("builtin", rule.builtin)
            },
        )
    }
    return JSONObject().apply {
        put("version", LOCAL_RULE_SCHEMA)
        put("rules", arr)
    }.toString()
}

/**
 * JSON → 规则列表（纯函数，可单测）。
 *
 * 容错：字段缺失走默认值、类型不认识按支出处理、关键词为空跳过、整段不是 JSON 就当空列表 ——
 * 手改文件 / 导入了别的 JSON 不该让 App 崩，界面据「空列表」提示格式不对。
 * 兼容两种形态：`{"version":1,"rules":[...]}` 与裸数组 `[...]`。
 */
internal fun rulesFromJson(raw: String): List<LocalRule> = runCatching { parseRules(raw) }
    .getOrDefault(emptyList())

private fun parseRules(raw: String): List<LocalRule> {
    if (raw.isBlank()) return emptyList()
    val trimmed = raw.trim()
    val arr = if (trimmed.startsWith("[")) {
        JSONArray(trimmed)
    } else {
        val root = JSONObject(trimmed)
        root.optJSONArray("rules") ?: JSONArray()
    }
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val keywords = o.optJSONArray("keywords")?.let { kw ->
            (0 until kw.length()).map { kw.optString(it).trim() }.filter { it.isNotEmpty() }
        }.orEmpty()
        LocalRule(
            id = id,
            name = o.optString("name").ifBlank { "未命名规则" },
            type = o.optString("type").takeIf {
                it == LocalRule.TYPE_EXPENSE || it == LocalRule.TYPE_INCOME || it == LocalRule.TYPE_ANY
            } ?: LocalRule.TYPE_EXPENSE,
            category = o.optString("category").ifBlank { "其他" },
            account = o.optString("account"),
            keywords = keywords,
            enabled = o.optBoolean("enabled", true),
            builtin = o.optBoolean("builtin", false),
        )
    }
}
