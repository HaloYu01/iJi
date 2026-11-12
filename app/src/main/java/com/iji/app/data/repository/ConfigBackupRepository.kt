package com.iji.app.data.repository

import android.content.Context
import com.iji.app.data.db.DbProvider
import com.iji.app.data.model.Category
import com.iji.app.data.model.TransactionType
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * **配置备份**仓库：导出 / 还原「账本环境」，不含交易流水。
 *
 * 还原策略一律**合并、不覆盖**：
 * - 同名/同 id 的项目跳过（绝不覆盖用户在本机已有的配置）
 * - 账户余额随账户一并恢复（新设备上无流水可推导余额）
 * - 偏好设置按键覆盖（这些是纯偏好，不涉及数据）
 */
object ConfigBackupRepository {

    const val BACKUP_VERSION = 1

    /**
     * 参与备份的偏好键白名单。
     *
     * **刻意排除 api_key / api_url / model_name**：API Key 是敏感凭证且与设备/账号绑定，
     * 跨设备还原会造成「Key 泄漏到新设备」与「旧 Key 覆盖新设备已配好的 Key」两处隐患。
     */
    private val PREF_STRINGS = listOf("theme_mode", "profile_nickname", "profile_avatar_uri")
    private val PREF_INTS = listOf("week_start_day", "month_start_day", "reminder_hour")
    private val PREF_BOOLS = listOf("is_24_hour", "perf_reduced_effects", "reminder_enabled")
    private val PREF_FLOATS = listOf("font_size")

    /** 还原统计（用于给用户明确反馈，而不是「导入完成」四个字） */
    data class RestoreResult(
        val categories: Int = 0,
        val tags: Int = 0,
        val accounts: Int = 0,
        val ledgers: Int = 0,
        val budgets: Int = 0,
        val templates: Int = 0,
        val recurringRules: Int = 0,
        val prefs: Int = 0,
        val skipped: Int = 0,
    ) {
        val total: Int get() = categories + tags + accounts + ledgers + budgets + templates + recurringRules + prefs
    }

    // ── 导出 ──

    suspend fun exportJson(context: Context): String {
        val db = DbProvider.database

        val root = JSONObject().apply {
            put("kind", "iji-config")
            put("version", BACKUP_VERSION)
            put("exportTime", System.currentTimeMillis())
        }

        // 分类目录：**整份导出**（出厂分类也可以被改名/删除/排序，只导自定义那份等于丢配置）
        // categoriesFull=true 时还原侧整表替换；老备份没有这个标记，走"只补新增"的兼容路径。
        root.put("categoriesFull", true)
        root.put("categories", JSONArray().apply {
            CategoryStore.all.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("color", c.color)
                    put("type", c.type.name)
                    put("parentId", c.parentId ?: "")
                    put("sortOrder", c.sortOrder)
                    put("builtin", c.builtin)
                    put("semantic", c.semantic ?: "")
                })
            }
        })

        root.put("tags", JSONArray().apply { TagStore.observe(context).first().forEach { put(it) } })

        root.put("accounts", JSONArray().apply {
            db.accountDao().observeAll().first().forEach { a ->
                put(JSONObject().apply {
                    put("name", a.name)
                    put("subName", a.subName)
                    put("iconKey", a.iconKey)
                    put("balanceCents", a.balanceCents)
                    put("sortOrder", a.sortOrder)
                    put("includeInNetWorth", a.includeInNetWorth)
                    put("category", a.category)
                    put("hidden", a.hidden)
                })
            }
        })

        root.put("ledgers", JSONArray().apply {
            db.ledgerDao().observeAll().first().forEach { l ->
                put(JSONObject().apply {
                    put("name", l.name)
                    put("sortOrder", l.sortOrder)
                    // 币种是账本配置的一部分：不导出的话，恢复后全变回 CNY
                    put("currency", l.currency)
                })
            }
        })

        root.put("budgets", JSONArray().apply {
            db.budgetDao().observeAll().first().forEach { b ->
                put(JSONObject().apply {
                    put("categoryId", b.categoryId)
                    put("ledgerId", b.ledgerId)
                    put("periodType", b.periodType)
                    put("amountCents", b.amountCents)
                    put("alertThresholdPercent", b.alertThresholdPercent)
                    put("enabled", b.enabled)
                    put("reminderEnabled", b.reminderEnabled)
                    put("carryOver", b.carryOver)
                    put("rolloverCents", b.rolloverCents)
                    put("startEpochDay", b.startEpochDay)
                    put("endEpochDay", b.endEpochDay)
                })
            }
        })

        root.put("templates", JSONArray().apply {
            TemplateRepository.once(context).forEach { t ->
                put(JSONObject().apply {
                    put("name", t.name)
                    put("type", t.type.name)
                    put("categoryId", t.categoryId)
                    put("amountCents", t.amountCents)
                    put("note", t.note)
                    put("account", t.account)
                    put("tags", JSONArray().apply { t.tags.forEach { put(it) } })
                })
            }
        })

        root.put("recurringRules", JSONArray().apply {
            db.recurringRuleDao().observeAll().first().forEach { r ->
                put(JSONObject().apply {
                    put("type", r.type)
                    put("categoryId", r.categoryId)
                    put("amountCents", r.amountCents)
                    put("note", r.note)
                    put("account", r.account)
                    put("freq", r.freq)
                    put("nextRunDate", r.nextRunDate)
                    put("ledgerId", r.ledgerId)
                    put("enabled", r.enabled)
                })
            }
        })

        root.put("prefs", JSONObject().apply {
            PREF_STRINGS.forEach { put(it, SettingsRepository.getString(context, it)) }
            PREF_INTS.forEach { put(it, SettingsRepository.getInt(context, it)) }
            PREF_BOOLS.forEach { put(it, SettingsRepository.getBoolean(context, it)) }
            PREF_FLOATS.forEach { put(it, SettingsRepository.getFloat(context, it).toDouble()) }
        })

        return root.toString(2)
    }

    // ── 还原（合并式） ──

    suspend fun importJson(context: Context, json: String): RestoreResult {
        val root = JSONObject(json)
        val db = DbProvider.database
        var skipped = 0

        // 分类
        var catAdded = 0
        root.optJSONArray("categories")?.let { arr ->
            val incoming = ArrayList<Category>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val name = o.optString("name")
                val type = runCatching { TransactionType.valueOf(o.optString("type", "EXPENSE")) }
                    .getOrDefault(TransactionType.EXPENSE)
                if (id.isBlank() || name.isBlank()) { skipped++; continue }
                incoming.add(
                    Category(
                        id = id,
                        name = name,
                        color = o.optLong("color", 0xFF9CA3AF),
                        type = type,
                        parentId = o.optString("parentId").takeIf { it.isNotBlank() },
                        sortOrder = o.optInt("sortOrder", i),
                        builtin = o.optBoolean("builtin", false),
                        semantic = o.optString("semantic").takeIf { it.isNotBlank() },
                    ),
                )
            }
            if (root.optBoolean("categoriesFull", false)) {
                // 新备份：整份目录就是用户当前的样子（含改名、删除、排序），直接替换
                if (CategoryStore.replaceAll(context, incoming)) {
                    catAdded = incoming.size
                } else {
                    skipped += incoming.size
                }
            } else {
                // 老备份：只有自定义分类，按 id 补缺，不动本机的出厂目录
                incoming.forEach { c ->
                    if (CategoryStore.existsById(c.id)) {
                        skipped++
                    } else if (CategoryStore.add(context, c)) {
                        catAdded++
                    } else {
                        skipped++
                    }
                }
            }
        }

        // 标签（并集）
        var tagAdded = 0
        val mergedTags = (TagStore.observe(context).first() +
            root.optJSONArray("tags")?.let { arr ->
                List(arr.length()) { arr.optString(it) }
            }.orEmpty()).distinct()
        val beforeTags = TagStore.observe(context).first().size
        TagStore.replace(context, mergedTags)
        tagAdded = mergedTags.size - beforeTags

        // 账户（同名跳过，绝不覆盖本机已有账户及其余额）
        var accAdded = 0
        root.optJSONArray("accounts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isBlank()) { skipped++; continue }
                if (db.accountDao().getByName(name) != null) { skipped++; continue }
                db.accountDao().insert(
                    com.iji.app.data.db.AccountEntity(
                        id = 0L,
                        name = name,
                        subName = o.optString("subName"),
                        iconKey = o.optString("iconKey"),
                        balanceCents = o.optLong("balanceCents"),
                        sortOrder = o.optInt("sortOrder"),
                        includeInNetWorth = o.optBoolean("includeInNetWorth", true),
                        category = o.optString("category"),
                        hidden = o.optBoolean("hidden"),
                    ),
                )
                accAdded++
            }
        }

        // 账本
        var ledgerAdded = 0
        // 还原顺序 = 备份文件里的顺序（导出时已按 createdAt 排好）；
        // createdAt 必须显式递增写入，否则账本全都落到 0，列表又会按名称乱排。
        var nextLedgerCreatedAt = Ids.next()
        root.optJSONArray("ledgers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isBlank()) { skipped++; continue }
                if (db.ledgerDao().observeAll().first().any { it.name == name }) { skipped++; continue }
                db.ledgerDao().insert(
                    com.iji.app.data.db.LedgerEntity(
                        name = name,
                        sortOrder = o.optInt("sortOrder"),
                        createdAt = nextLedgerCreatedAt++,
                        // 老备份没有该字段 → CNY；非法代码同样回落 CNY（见 Currency.of）
                        currency = com.iji.app.data.model.Currency.of(o.optString("currency")).code,
                    ),
                )
                ledgerAdded++
            }
        }

        // 预算：按 (categoryId, periodType) 匹配已有行则更新额度，否则新增
        var budgetAdded = 0
        root.optJSONArray("budgets")?.let { arr ->
            val existing = db.budgetDao().observeAll().first()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val categoryId = o.optString("categoryId")
                val periodType = o.optString("periodType", "MONTH")
                val match = existing.firstOrNull { it.categoryId == categoryId && it.periodType == periodType }
                val amount = o.optLong("amountCents")
                if (match != null) {
                    db.budgetDao().update(
                        match.copy(
                            amountCents = amount,
                            alertThresholdPercent = o.optInt("alertThresholdPercent", match.alertThresholdPercent),
                            reminderEnabled = o.optBoolean("reminderEnabled", match.reminderEnabled),
                            carryOver = o.optBoolean("carryOver", match.carryOver),
                            rolloverCents = o.optLong("rolloverCents", match.rolloverCents),
                        ),
                    )
                    budgetAdded++
                } else {
                    db.budgetDao().insert(
                        com.iji.app.data.db.BudgetEntity(
                            categoryId = categoryId,
                            ledgerId = o.optString("ledgerId", "默认账本"),
                            periodType = periodType,
                            amountCents = amount,
                            alertThresholdPercent = o.optInt("alertThresholdPercent", 80),
                            enabled = o.optBoolean("enabled", true),
                            reminderEnabled = o.optBoolean("reminderEnabled", true),
                            carryOver = o.optBoolean("carryOver", false),
                            rolloverCents = o.optLong("rolloverCents", 0L),
                            startEpochDay = o.optLong("startEpochDay"),
                            endEpochDay = o.optLong("endEpochDay"),
                            createdAtMillis = System.currentTimeMillis(),
                        ),
                    )
                    budgetAdded++
                }
            }
        }

        // 记账模板：同名跳过
        var tplAdded = 0
        root.optJSONArray("templates")?.let { arr ->
            val existingNames = TemplateRepository.once(context).map { it.name }.toSet()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isBlank()) { skipped++; continue }
                if (name in existingNames) { skipped++; continue }
                TemplateRepository.upsert(
                    context,
                    TransactionTemplate(
                        id = 0L,
                        name = name,
                        type = runCatching { TransactionType.valueOf(o.optString("type", "EXPENSE")) }
                            .getOrDefault(TransactionType.EXPENSE),
                        categoryId = o.optString("categoryId"),
                        amountCents = o.optLong("amountCents"),
                        note = o.optString("note"),
                        account = o.optString("account"),
                        tags = o.optJSONArray("tags")?.let { t ->
                            List(t.length()) { t.optString(it) }.filter { it.isNotBlank() }
                        }.orEmpty(),
                    ),
                )
                tplAdded++
            }
        }

        // 周期规则：主键非自增，必须重新分配 id，否则与本机已有 id 冲突直接抛异常
        var ruleAdded = 0
        root.optJSONArray("recurringRules")?.let { arr ->
            var nextId = (db.recurringRuleDao().observeAll().first().maxOfOrNull { it.id } ?: 0L) + 1L
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                db.recurringRuleDao().insert(
                    com.iji.app.data.db.RecurringRuleEntity(
                        id = nextId++,
                        type = o.optString("type", "EXPENSE"),
                        categoryId = o.optString("categoryId"),
                        amountCents = o.optLong("amountCents"),
                        note = o.optString("note"),
                        account = o.optString("account"),
                        freq = o.optString("freq", "MONTHLY"),
                        nextRunDate = o.optLong("nextRunDate"),
                        ledgerId = o.optString("ledgerId", "默认账本"),
                        enabled = o.optBoolean("enabled", true),
                    ),
                )
                ruleAdded++
            }
        }

        // 偏好设置（按键覆盖）
        var prefCount = 0
        root.optJSONObject("prefs")?.let { prefs ->
            PREF_STRINGS.forEach { k ->
                if (prefs.has(k)) { SettingsRepository.setString(context, k, prefs.optString(k)); prefCount++ }
            }
            PREF_INTS.forEach { k ->
                if (prefs.has(k)) { SettingsRepository.setInt(context, k, prefs.optInt(k)); prefCount++ }
            }
            PREF_BOOLS.forEach { k ->
                if (prefs.has(k)) { SettingsRepository.setBoolean(context, k, prefs.optBoolean(k)); prefCount++ }
            }
            PREF_FLOATS.forEach { k ->
                if (prefs.has(k)) { SettingsRepository.setFloat(context, k, prefs.optDouble(k).toFloat()); prefCount++ }
            }
        }

        // 分类缓存变化后需让 CategoryCatalog 重建索引（CategoryStore.add 已自增版本号）
        return RestoreResult(
            categories = catAdded,
            tags = tagAdded,
            accounts = accAdded,
            ledgers = ledgerAdded,
            budgets = budgetAdded,
            templates = tplAdded,
            recurringRules = ruleAdded,
            prefs = prefCount,
            skipped = skipped,
        )
    }
}