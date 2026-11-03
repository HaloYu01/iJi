package com.iji.app.data.repository

import android.content.Context

/**
 * 分类图标覆盖存储：记录「分类 → 素材包图标名」的用户自定义映射。
 *
 * ### 为什么 key 从"分类名"改成"分类 id"
 * 旧版按分类名存（`override_餐饮`），一旦改名，覆盖记录就跟着旧名字留在原地 → 图标丢失。
 * 编辑页此前靠"改名前手工搬 key"打补丁，只要有一处改名没走那条路径就会漏。
 * 分类现在全面开放改名，必须换成稳定标识：**id 改名不变**，图标自然跟着走。
 *
 * 兼容：读取时先查 id，再回落旧的名字 key，命中即顺手迁到 id 下（懒迁移，无需一次性洗数据）。
 */
object CategoryIconStore {

    private const val PREFS = "category_icon_override"

    /** 旧版：按分类名 */
    private const val KEY_NAME_PREFIX = "override_"

    /** 新版：按分类 id */
    private const val KEY_ID_PREFIX = "override_id_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 按 id 读取（**推荐**）：id 命中即返回；否则回落旧的名字 key 并顺手迁移 */
    fun get(context: Context, categoryId: String, categoryName: String): String? {
        val p = prefs(context)
        p.getString(KEY_ID_PREFIX + categoryId, null)?.let { return it }
        val legacy = p.getString(KEY_NAME_PREFIX + categoryName, null) ?: return null
        // 懒迁移：把旧 key 的值搬到 id 名下，旧 key 保留（回滚旧版本 APK 时图标不丢）
        p.edit().putString(KEY_ID_PREFIX + categoryId, legacy).apply()
        return legacy
    }

    /** 按名字读取（仅用于拿不到 id 的场景，如编辑页的素材预览格） */
    fun get(context: Context, categoryName: String): String? =
        prefs(context).getString(KEY_NAME_PREFIX + categoryName, null)

    /** 按 id 设置图标 */
    fun setById(context: Context, categoryId: String, iconName: String) {
        prefs(context).edit().putString(KEY_ID_PREFIX + categoryId, iconName).apply()
    }

    /** 按名字设置（旧 API，保留给无 id 的调用点） */
    fun set(context: Context, categoryName: String, iconName: String) {
        prefs(context).edit().putString(KEY_NAME_PREFIX + categoryName, iconName).apply()
    }

    /** 删除分类时清理其图标记录（id + 旧名字 key 一起清） */
    fun resetById(context: Context, categoryId: String, categoryName: String? = null) {
        val edit = prefs(context).edit().remove(KEY_ID_PREFIX + categoryId)
        categoryName?.let { edit.remove(KEY_NAME_PREFIX + it) }
        edit.apply()
    }

    /** 恢复默认：清除该分类的自定义图标（旧 API） */
    fun reset(context: Context, categoryName: String) {
        prefs(context).edit().remove(KEY_NAME_PREFIX + categoryName).apply()
    }
}
