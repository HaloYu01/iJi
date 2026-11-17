package com.iji.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 需要一次性迁移到 DataStore 的历史 SharedPreferences 文件。
 * 首次访问 DataStore 时自动把其中的键导入，保留用户既有设置（主题、时间格式、字体、云备份、AI 配置等）。
 */
private val MIGRATE_PREFS = setOf("iji_settings", "cloud_backup", "ai_config")

private val Context.dataStore by preferencesDataStore(
    name = "iji_settings",
    produceMigrations = { context ->
        MIGRATE_PREFS.map { SharedPreferencesMigration(context, it) }
    },
)

/**
 * 全局偏好设置仓库（Preferences DataStore）。
 *
 * 取代散落在各设置页与全局 object（ProfileState / AiConfig）中的 SharedPreferences 直连，
 * 集中管理键名与读写，避免魔法字符串四处硬编码。写入走 IO 协程、不阻塞调用线程。
 *
 * **内存缓存**：各设置页此前用 `remember { getXxx(...) }` 在**组合期**读取，
 * 而 DataStore 的 `data.first()` 是磁盘 IO —— runBlocking 会直接阻塞主线程
 * （AI 配置页一次读 3 个键）。[warmUp] 在 Application 启动时**一次性**把 DataStore
 * 整份读进内存（同时完成 SharedPreferences 迁移），并置 `warmed`；此后所有 getter
 * 只查内存，未落盘的键直接返回默认值，不再触发磁盘 IO。
 *
 * 注意：warmUp 必须在任何 UI/State 初始化之前调用。此前它是异步 launch，而
 * PerfState / HapticsState / FontScaleState 在 Application.onCreate 里已经同步读了
 * 4 次 DataStore（每次都 runBlocking），异步预热反而排在它们后面。
 *
 * 注意：应用锁 PIN 走 P0 的 EncryptedSharedPreferences（安全敏感，不降级为明文 DataStore），
 * 故 AppLockManager 暂不纳入本仓库。
 */
object SettingsRepository {

    private const val TAG = "SettingsRepository"

    /** App 启动即检查更新（默认开，可在偏好设置里关） */
    const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"

    /** 发现新版本时弹出提醒（默认开；关掉后只记录检查结果） */
    const val KEY_UPDATE_REMINDER = "update_reminder"

    /** 启动检查的日期戳 yyyy-MM-dd：每天最多请求一次，避免重复联网 */
    const val KEY_UPDATE_LAST_CHECK_DATE = "update_last_check_date"

    /**
     * 已同意的隐私政策版本号（0 = 从未同意）。
     *
     * 存版本号而不是布尔：政策修订后把 privacyPolicyVersion +1，
     * 老用户下次启动会重新看到同意页，不需要做数据迁移或清除标记。
     */
    const val KEY_PRIVACY_AGREED_VERSION = "privacy_agreed_version"

    /**
     * 开局引导状态（int 三态，不是布尔）：
     * - [ONBOARDING_FRESH] 从未触发：存量用户升级 / 尚未同意隐私，不该被引导拦住；
     * - [ONBOARDING_PENDING] 刚同意隐私、引导待完成：同意后立刻被杀进程，下次冷启动仍会进引导；
     * - [ONBOARDING_DONE] 完成或跳过：以后都不再进。
     *
     * 只在「同意隐私」时从 FRESH 推进到 PENDING，因此已完成的老用户在隐私政策
     * 版本升级后重新同意，也不会重复看到引导。
     */
    const val KEY_ONBOARDING_STATE = "onboarding_state"
    const val ONBOARDING_FRESH = 0
    const val ONBOARDING_PENDING = 1
    const val ONBOARDING_DONE = 2

    /** 权限检验页「首次说明卡」是否已看过；可随时从页内「查看说明」重新展开 */
    const val KEY_PERMISSION_GUIDE_SHOWN = "permission_guide_shown"

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 已读取的键值缓存（键为 DataStore 键名） */
    private val cache = ConcurrentHashMap<String, Any>()

    /** warmUp 是否已完成。完成后未命中缓存的键 = 确实没落盘，直接返回默认值。 */
    @Volatile
    private var warmed = false

    /**
     * 由 IjiApplication.onCreate 在**任何 UI / State 初始化之前**调用。
     *
     * 一次性读取整份 DataStore（含 SharedPreferences 迁移），把所有已落盘的键塞进内存；
     * 此前只预热固定十几个键，通知规则、首次引导、按账本记忆等动态键仍会在组合期
     * runBlocking 读盘。现在预热后所有 getter 都不再碰磁盘。
     */
    suspend fun warmUp(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val p = context.dataStore.data.first()
            p.asMap().forEach { (key, value) ->
                if (value != null) cache[key.name] = value
            }
            warmed = true
        }.onFailure {
            // 预热失败也必须置位：否则每个 getter 都 runBlocking 读盘，事件风暴下主线程会 ANR。
            warmed = true
            Log.w(TAG, "DataStore 预热失败，改用内存默认值", it)
        }
    }

    // ── 个人资料：头像 / 昵称 ──
    fun getNickname(context: Context): String = getString(context, "profile_nickname", "")

    fun getAvatarUri(context: Context): String = getString(context, "profile_avatar_uri", "")

    fun setNickname(context: Context, value: String) = setString(context, "profile_nickname", value)

    fun setAvatarUri(context: Context, value: String) = setString(context, "profile_avatar_uri", value)

    // ── 通用基础类型读写（供各设置页去硬编码迁移，键名需全局唯一）──
    fun getString(context: Context, key: String, def: String = ""): String {
        cache[key]?.let { return it as? String ?: def }
        if (warmed) return def
        val v = runBlocking { context.dataStore.data.first()[stringPreferencesKey(key)] ?: def }
        cache[key] = v
        return v
    }

    fun setString(context: Context, key: String, value: String) {
        cache[key] = value
        ioScope.launch { context.dataStore.edit { it[stringPreferencesKey(key)] = value } }
    }

    fun getBoolean(context: Context, key: String, def: Boolean = false): Boolean {
        cache[key]?.let { return it as? Boolean ?: def }
        if (warmed) return def
        val v = runBlocking { context.dataStore.data.first()[booleanPreferencesKey(key)] ?: def }
        cache[key] = v
        return v
    }

    fun setBoolean(context: Context, key: String, value: Boolean) {
        cache[key] = value
        ioScope.launch { context.dataStore.edit { it[booleanPreferencesKey(key)] = value } }
    }

    fun getInt(context: Context, key: String, def: Int = 0): Int {
        cache[key]?.let { return it as? Int ?: def }
        if (warmed) return def
        val v = runBlocking { context.dataStore.data.first()[intPreferencesKey(key)] ?: def }
        cache[key] = v
        return v
    }

    fun setInt(context: Context, key: String, value: Int) {
        cache[key] = value
        ioScope.launch { context.dataStore.edit { it[intPreferencesKey(key)] = value } }
    }

    /**
     * 与 [setInt] 不同：**返回时 DataStore 已写完**。
     *
     * 用于隐私政策同意这类强一致标记：用户点完同意可能立刻被系统杀进程，
     * 若只走异步落盘，写入没赶上下次启动就会重复弹同意页。
     */
    suspend fun setIntAwait(context: Context, key: String, value: Int) {
        cache[key] = value
        withContext(Dispatchers.IO) {
            context.dataStore.edit { it[intPreferencesKey(key)] = value }
        }
    }

    fun getFloat(context: Context, key: String, def: Float = 0f): Float {
        cache[key]?.let { return it as? Float ?: def }
        if (warmed) return def
        val v = runBlocking { context.dataStore.data.first()[floatPreferencesKey(key)] ?: def }
        cache[key] = v
        return v
    }

    fun setFloat(context: Context, key: String, value: Float) {
        cache[key] = value
        ioScope.launch { context.dataStore.edit { it[floatPreferencesKey(key)] = value } }
    }
}