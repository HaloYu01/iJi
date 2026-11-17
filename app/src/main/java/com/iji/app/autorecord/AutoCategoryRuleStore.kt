package com.iji.app.autorecord

import android.content.Context
import com.iji.app.data.repository.SettingsRepository

/**
 * 用户自定义「关键词 → 分类」规则。
 *
 * 与 [AutoCategorySuggester] 的内置关键词表是叠加关系：用户规则优先级更高，
 * 用来纠正内置表判错的场景（例如某个商户在你这里固定算「人情」而不是「餐饮」）。
 *
 * 持久化在 DataStore，一行一条 `关键词|分类ID`，与账户映射（`AutoAssetSource`）同一套写法。
 */
object AutoCategoryRuleStore {

    private const val KEY = "auto_category_rules"

    data class Rule(
        val keyword: String,
        val categoryId: String,
    )

    fun list(context: Context): List<Rule> =
        SettingsRepository.getString(context, KEY, "")
            .split('\n')
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 2) return@mapNotNull null
                val keyword = parts[0].trim()
                val categoryId = parts[1].trim()
                if (keyword.isEmpty() || categoryId.isEmpty()) return@mapNotNull null
                Rule(keyword, categoryId)
            }

    /** 同关键词只保留一条（后写的覆盖先写的），避免列表里出现互相矛盾的两行 */
    fun add(context: Context, keyword: String, categoryId: String) {
        val word = keyword.trim()
        if (word.isEmpty() || categoryId.isBlank()) return
        val next = list(context).filterNot { it.keyword.equals(word, ignoreCase = true) } +
            Rule(word, categoryId)
        save(context, next)
    }

    fun remove(context: Context, keyword: String) {
        save(context, list(context).filterNot { it.keyword == keyword })
    }

    private fun save(context: Context, rules: List<Rule>) {
        SettingsRepository.setString(
            context,
            KEY,
            rules.joinToString("\n") { "${it.keyword}|${it.categoryId}" },
        )
    }
}