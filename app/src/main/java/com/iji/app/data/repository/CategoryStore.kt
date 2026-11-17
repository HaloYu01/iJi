package com.iji.app.data.repository

import android.content.Context
import com.iji.app.data.model.Category
import com.iji.app.data.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject

/**
 * 分类目录存储：**全量目录**（出厂分类 + 用户自建）持久化在 SharedPreferences，内存缓存 + 版本号。
 *
 * 为什么还留在 SharedPreferences 而不是 Room：
 * - `CategoryCatalog.of(id)` 被 70+ 处**拿不到 Context** 的调用点直接使用，只能靠进程内缓存；
 * - 目录本身是几百字节的扁平列表，没有联表查询需求（引用计数用一条 DAO 聚合即可）。
 *
 * ### v2（本次改造）
 * 旧版只存「用户自定义分类」，出厂分类是硬编码常量，因此内置分类天然不可编辑。
 * v2 把**出厂分类也当成数据写进存储**，此后两者同权：可改名 / 删除 / 排序。
 * - `seeded_v2` 标记保证种子只写一次：用户把分类删光后不会被"自动补回"。
 * - 旧版的 `custom_categories` 会在种子时合并进来（保留用户已有分类）。
 * - 所有写操作走 [write] 并返回 Boolean（用 `commit` 拿到真实结果），UI 才能给出"保存失败"而不是假装成功。
 */
object CategoryStore {

    private const val PREF = "iji_categories"

    private const val KEY_LEGACY_CUSTOM = "custom_categories"
    private const val KEY_ALL = "categories_v2"
    private const val KEY_SEEDED = "seeded_v2"
    private const val KEY_SAVED_COLORS = "saved_colors"

    @Volatile
    private var version: Int = 0

    /** [init] 是否已跑过：未跑过时对外提供出厂目录，保证纯 JVM 单测与预览也能拿到分类 */
    @Volatile
    private var initialized: Boolean = false

    private val cache = ArrayList<Category>()

    /**
     * 当前全量分类（含出厂分类），内存快照；UI 侧靠 [cacheVersion] 感知变化。
     *
     * 未 [init] 时返回出厂目录 —— 单测（纯 JVM、无 Context）与 Compose 预览都依赖这一点；
     * 真机上 [init] 一定会先于任何读写发生（见 IjiApplication）。
     */
    val all: List<Category>
        get() = synchronized(this) { if (initialized) cache.toList() else CategoryDefaults.all }

    /** 缓存版本号：目录变化时自增，[CategoryCatalog] 据此重建索引 */
    val cacheVersion: Int get() = version

    /** 由 IjiApplication 调用：首次运行写入种子，之后读回持久化目录 */
    fun init(context: Context) {
        synchronized(this) {
            cache.clear()
            val prefs = prefs(context)
            cache.addAll(
                if (prefs.getBoolean(KEY_SEEDED, false)) {
                    read(context, KEY_ALL)
                } else {
                    seed(context)
                },
            )
            initialized = true
            version++
        }
    }

    /**
     * 首次运行 / 从 v1 升级：写入出厂分类，并把旧版自定义分类接到各自层级的末尾。
     *
     * 注意**不能**用「目录为空就重新播种」的判定：那样用户删光自定义分类后，
     * 下次启动会把出厂分类又塞回来，等于删不掉。只认 [KEY_SEEDED] 标记。
     */
    private fun seed(context: Context): List<Category> {
        val merged = ArrayList(CategoryDefaults.all)
        read(context, KEY_LEGACY_CUSTOM).forEach { legacy ->
            if (merged.none { it.id == legacy.id }) {
                merged.add(
                    legacy.copy(
                        builtin = false,
                        sortOrder = merged.count { it.type == legacy.type && it.parentId == legacy.parentId },
                    ),
                )
            }
        }
        // 种子写入与标记放在同一次 commit 里：中途被杀不会留下"有数据但没标记"的半成品
        write(context, merged, markSeeded = true)
        return merged
    }

    // ── 查询 ──

    fun find(id: String): Category? = all.firstOrNull { it.id == id }

    fun isBuiltin(id: String): Boolean = find(id)?.builtin == true

    fun existsById(id: String): Boolean = find(id) != null

    /** 指定层级的末尾序号（新增分类时用） */
    fun nextSortOrder(type: TransactionType, parentId: String?): Int =
        (all.filter { it.type == type && it.parentId == parentId }.maxOfOrNull { it.sortOrder } ?: -1) + 1

    fun savedColors(context: Context): List<Long> =
        prefs(context).getString(KEY_SAVED_COLORS, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    fun saveColor(context: Context, color: Long) {
        val next = (savedColors(context).filter { it != color } + color).takeLast(8)
        prefs(context).edit().putString(KEY_SAVED_COLORS, next.joinToString(",")).apply()
    }

    // ── 写入 ──

    /**
     * 新增分类。同名（同类型、同父级）已存在时返回 false，不写盘。
     *
     * [category] 的 id 由调用方保证唯一（[CategoryCatalog.add] 负责派生），`sortOrder` 为空时补末尾。
     */
    fun add(context: Context, category: Category): Boolean = synchronized(this) {
        if (cache.any { it.id == category.id }) return false
        if (cache.any { it.name == category.name && it.type == category.type && it.parentId == category.parentId }) {
            return false
        }
        val next = cache + category.copy(
            sortOrder = if (category.sortOrder >= 0) {
                category.sortOrder
            } else {
                (cache.filter { it.type == category.type && it.parentId == category.parentId }
                    .maxOfOrNull { it.sortOrder } ?: -1) + 1
            },
        )
        commit(context, next)
    }

    /** 覆盖一条分类（改名 / 改色 / 换父级 / 改语义）；id 必须已存在 */
    fun update(context: Context, category: Category): Boolean = synchronized(this) {
        val i = cache.indexOfFirst { it.id == category.id }
        if (i < 0) return false
        // 改名时也要防重名（同类型同父级），否则两个同名分类无法在列表里区分
        if (cache.any {
                it.id != category.id && it.name == category.name &&
                    it.type == category.type && it.parentId == category.parentId
            }
        ) {
            return false
        }
        val next = cache.toMutableList().also { it[i] = category }
        commit(context, next)
    }

    /**
     * 删除分类**及其全部子分类**（单次写盘）。
     *
     * 为什么要级联：子分类靠 parentId 挂在父分类下，父分类被删而子分类留下时，
     * 这些子分类在按父分组的界面上永远渲染不出来（等于变成不可见的孤儿数据）。
     *
     * @return 被删除的分类（含子项）；未命中返回空列表
     */
    fun removeCascade(context: Context, id: String): List<Category> = synchronized(this) {
        val removed = cache.filter { it.id == id || it.parentId == id }
        if (removed.isEmpty()) return emptyList()
        val removedIds = removed.map { it.id }.toSet()
        commit(context, cache.filterNot { it.id in removedIds })
        removed
    }

    /**
     * 同层级重排：把 [orderedIds] 按给定顺序重新编号 `sortOrder`（0..n-1）。
     *
     * 只动传入的这些 id，其余分类的序号不变 —— 因此一级/二级可以各自独立排序。
     */
    fun reorder(context: Context, orderedIds: List<String>): Boolean = synchronized(this) {
        if (orderedIds.isEmpty()) return true
        val order = orderedIds.withIndex().associate { (index, id) -> id to index }
        var changed = false
        val next = cache.map { c ->
            val newOrder = order[c.id]
            if (newOrder != null && newOrder != c.sortOrder) {
                changed = true
                c.copy(sortOrder = newOrder)
            } else {
                c
            }
        }
        if (!changed) return true
        commit(context, next)
    }

    /** 恢复出厂目录：清空全部自定义分类与用户改动（含排序），回到 [CategoryDefaults] */
    fun restoreDefaults(context: Context): Boolean = synchronized(this) {
        commit(context, CategoryDefaults.all)
    }

    /** 备份恢复：整表替换（用于新机还原"我改过的分类目录"） */
    fun replaceAll(context: Context, categories: List<Category>): Boolean = synchronized(this) {
        if (categories.isEmpty()) return false
        commit(context, categories)
    }

    // ── 持久化 ──

    /** 一次写盘 + 更新缓存；写失败时缓存保持不变，调用方据此提示"保存失败" */
    private fun commit(context: Context, next: List<Category>): Boolean {
        if (!write(context, next, markSeeded = true)) return false
        cache.clear()
        cache.addAll(next)
        initialized = true
        version++
        return true
    }

    private fun read(context: Context, key: String): List<Category> {
        val raw = prefs(context).getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val type = runCatching { TransactionType.valueOf(o.optString("type", "EXPENSE")) }
                    .getOrDefault(TransactionType.EXPENSE)
                Category(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    color = o.optLong("color", 0xFF9CA3AF),
                    type = type,
                    parentId = o.optString("parentId").takeIf { it.isNotBlank() },
                    sortOrder = o.optInt("sortOrder", i),
                    builtin = o.optBoolean("builtin", false),
                    semantic = o.optString("semantic").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())
    }

    /** @return 是否写盘成功（`commit` 同步返回结果，才能对用户如实反馈） */
    private fun write(context: Context, list: List<Category>, markSeeded: Boolean): Boolean {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("color", c.color)
                    put("type", c.type.name)
                    put("parentId", c.parentId ?: "")
                    put("sortOrder", c.sortOrder)
                    put("builtin", c.builtin)
                    put("semantic", c.semantic ?: "")
                },
            )
        }
        return runCatching {
            val edit = prefs(context).edit().putString(KEY_ALL, arr.toString())
            if (markSeeded) edit.putBoolean(KEY_SEEDED, true)
            edit.commit()
        }.getOrDefault(false)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
