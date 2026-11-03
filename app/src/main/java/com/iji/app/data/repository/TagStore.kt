package com.iji.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tagDataStore by preferencesDataStore(name = "iji_tags")

private val KEY_TAGS = stringPreferencesKey("tags")

/**
 * 标签仓库（Preferences DataStore）。
 *
 * 为什么现在才有：标签管理页此前把标签硬编码成 `listOf("工作","生活",…)`，
 * 用户的增删改**重启即丢**，也因此「配置备份」无从备份标签（还原到一个硬编码列表没有意义）。
 * 这里补上持久化，配置备份才能真正覆盖标签这一项。
 */
object TagStore {

    /** 首次使用时的默认标签（与原硬编码列表保持一致，避免老用户看到标签突然变少） */
    val DEFAULT: List<String> = listOf("工作", "生活", "娱乐", "美食", "旅行", "学习")

    fun observe(context: Context): Flow<List<String>> =
        context.tagDataStore.data.map { prefs ->
            val raw = prefs[KEY_TAGS]
            if (raw == null) DEFAULT
            else raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        }

    /** 整体覆盖（配置还原用） */
    suspend fun replace(context: Context, tags: List<String>) {
        val cleaned = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        context.tagDataStore.edit { it[KEY_TAGS] = cleaned.joinToString("\n") }
    }

    /** 新增标签；已存在返回 false */
    suspend fun add(context: Context, tag: String): Boolean {
        val t = tag.trim()
        if (t.isBlank()) return false
        val current = currentTags(context)
        if (current.any { it == t }) return false
        replace(context, current + t)
        return true
    }

    suspend fun remove(context: Context, tag: String): Boolean {
        val current = currentTags(context)
        val next = current.filterNot { it == tag }
        if (next.size == current.size) return false
        replace(context, next)
        return true
    }

    private suspend fun currentTags(context: Context): List<String> {
        val raw = context.tagDataStore.data.first()[KEY_TAGS] ?: return DEFAULT
        return raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
    }
}