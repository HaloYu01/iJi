package com.iji.app.data.repository

import android.content.Context
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
 * （AI 配置页一次读 3 个键）。这里加一层并发缓存，并由 [warmUp] 在 Application 启动时
 * 于 IO 线程预热组合期会用到的键，使这些读取消除磁盘 IO 且不引入加载闪烁。
 *
 * 注意：应用锁 PIN 走 P0 的 EncryptedSharedPreferences（安全敏感，不降级为明文 DataStore），
 * 故 AppLockManager 暂不纳入本仓库。
 */
object SettingsRepository {

    /**
     * 检查更新的更新源地址。
     *
     * 留空 = 未接入在线更新（App 内不联网、不假装「已是最新」）；
     * 填入后由 [com.iji.app.update.UpdateChecker] 真实请求并比对版本号。
     */
    const val KEY_UPDATE_FEED_URL = "update_feed_url"

    /**
     * 首次引导是否已看过（true = 不再自动弹「欢迎使用」）。
     *
     * 默认 false：只在"没看过"时弹一次。老用户升级上来会看到一次，
     * 点「开始使用」即消失并写标记——比"偷偷按有没有账单猜你是不是新用户"更可预期。
     */
    const val KEY_ONBOARDING_DONE = "onboarding_done"

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 已读取的键值缓存（键为 DataStore 键名） */
    private val cache = ConcurrentHashMap<String, Any>()

    /** 由 IjiApplication.onCreate 调用（IO 协程），不阻塞冷启动 */
    suspend fun warmUp(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val p = context.dataStore.data.first()
            // 默认值必须与各 getter 的 def 一致，否则预热后会改变行为
            cache["theme_mode"] = p[stringPreferencesKey("theme_mode")] ?: "system"
            cache["font_size"] = p[floatPreferencesKey("font_size")] ?: 16f
            cache["is_24_hour"] = p[booleanPreferencesKey("is_24_hour")] ?: true
            cache["week_start_day"] = p[intPreferencesKey("week_start_day")] ?: 1
            cache["month_start_day"] = p[intPreferencesKey("month_start_day")] ?: 1
            // AI 配置：默认值必须留空，交给 AiConfig 按当前服务的默认地址 / 模型兜底。
            // 这里曾写死 "https://open.bigmodel.cn/api/paas/v4/"（缺 /chat/completions），
            // 预热后会让「没配置过」的地址看起来已经有值，且请求必然 404。
            cache["api_url"] = p[stringPreferencesKey("api_url")] ?: ""
            cache["api_key"] = p[stringPreferencesKey("api_key")] ?: ""
            // 键名是 "model"（AiConfig.KEY_MODEL）；原来预热的是历史键 "model_name"，从未命中
            cache["model"] = p[stringPreferencesKey("model")] ?: ""
            cache["perf_reduced_effects"] = p[booleanPreferencesKey("perf_reduced_effects")] ?: false
            cache["reminder_enabled"] = p[booleanPreferencesKey("reminder_enabled")] ?: false
            cache["reminder_hour"] = p[intPreferencesKey("reminder_hour")] ?: 21
            // 更新源地址：关于页打开「检查更新」时要读，预热掉以免弹窗在组合期做磁盘 IO
            cache[KEY_UPDATE_FEED_URL] = p[stringPreferencesKey(KEY_UPDATE_FEED_URL)] ?: ""
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
        val v = runBlocking { context.dataStore.data.first()[intPreferencesKey(key)] ?: def }
        cache[key] = v
        return v
    }

    fun setInt(context: Context, key: String, value: Int) {
        cache[key] = value
        ioScope.launch { context.dataStore.edit { it[intPreferencesKey(key)] = value } }
    }

    fun getFloat(context: Context, key: String, def: Float = 0f): Float {
        cache[key]?.let { return it as? Float ?: def }
        val v = runBlocking { context.dataStore.data.first()[floatPreferencesKey(key)] ?: def }
        cache[key] = v
        return v
    }

    fun setFloat(context: Context, key: String, value: Float) {
        cache[key] = value
        ioScope.launch { context.dataStore.edit { it[floatPreferencesKey(key)] = value } }
    }
}