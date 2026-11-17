package com.iji.app.lock

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 应用锁管理：PIN + 指纹。
 *
 * **安全约束（P0 修复）**：PIN 不再以明文存入 SharedPreferences，改用
 * `EncryptedSharedPreferences`（值经 Android Keystore 的 AES256 密钥加密落盘）。
 * 旧版本的明文文件（"iji_lock"）在首次启动时一次性迁移到加密文件（"iji_lock_v2"）并清空，
 * 避免 `adb backup` 直接提取明文 PIN 与整个 Room 数据库。
 */
object AppLockManager {
    /** 加密后的偏好文件名 */
    private const val PREFS = "iji_lock_v2"
    /** 旧版明文偏好文件名（迁移后会被清空） */
    private const val PREFS_LEGACY = "iji_lock"
    private const val KEY_PIN = "pin"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_FINGERPRINT = "fingerprint_enabled"
    /** 连续输错的次数（加密存储，杀进程也赖不掉） */
    private const val KEY_FAILS = "fail_count"
    /** 冷却截止时刻（毫秒）；0 = 未冷却 */
    private const val KEY_LOCK_UNTIL = "lock_until_millis"

    /** PIN 位数。设置端已限制输入，这里再兜一层，避免非 4 位数字写进加密库 */
    const val PIN_LENGTH = 4

    /**
     * 连续输错上限，达到即冷却 [LOCK_MS]。
     *
     * 4 位数字只有 1 万种组合，不设防暴破等于把账本拱手让人——
     * PIN 存在 Keystore 加密的库里只能防"直接读文件"，防不住"反复试"。
     */
    const val MAX_ATTEMPTS = 5

    /** 冷却时长：够挡住手点穷举，又不至于把自己锁死 */
    const val LOCK_MS = 30_000L

    @Volatile
    private var encPrefs: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences {
        encPrefs?.let { return it }
        synchronized(this) {
            encPrefs?.let { return it }
            val appCtx = ctx.applicationContext
            val created = try {
                val masterKey = MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appCtx,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                // 极端情况（Keystore 不可用）回退明文，避免崩溃；但会失去静态加密，仅兜底
                android.util.Log.e("AppLockManager", "EncryptedSharedPreferences 创建失败，回退明文存储", e)
                appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            }
            migrateLegacyIfNeeded(appCtx, created)
            encPrefs = created
            return created
        }
    }

    /** 把旧版明文偏好一次性迁移到加密文件并清空原文，杜绝明文 PIN 残留在磁盘 */
    private fun migrateLegacyIfNeeded(ctx: Context, target: SharedPreferences) {
        val legacy = ctx.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        target.edit().apply {
            legacy.getString(KEY_PIN, null)?.let { putString(KEY_PIN, it) }
            putBoolean(KEY_ENABLED, legacy.getBoolean(KEY_ENABLED, false))
            putBoolean(KEY_FINGERPRINT, legacy.getBoolean(KEY_FINGERPRINT, false))
        }.apply()
        legacy.edit().clear().apply()
    }

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)
    fun getPin(ctx: Context): String? = prefs(ctx).getString(KEY_PIN, null)

    /** PIN 是否合法：4 位纯数字 */
    fun isValidPin(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    /**
     * 设置 PIN；不合法**不写库**并返回 false。
     *
     * 返回 Boolean 是为了让调用方能如实告知用户（原来无论写没写成都提示「PIN已设置」）。
     */
    fun setPin(ctx: Context, pin: String): Boolean {
        if (!isValidPin(pin)) return false
        prefs(ctx).edit().putString(KEY_PIN, pin).putBoolean(KEY_ENABLED, true).apply()
        return true
    }

    fun disable(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, false).putString(KEY_PIN, null).apply()
        resetFailures(ctx)
    }

    // ── 防暴破：连续输错计数 + 冷却 ──

    /** 冷却剩余毫秒（0 = 可以试） */
    fun lockRemainingMillis(ctx: Context): Long =
        (prefs(ctx).getLong(KEY_LOCK_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    /** 冷却中的整秒数，用于界面倒计时 */
    fun lockRemainingSeconds(ctx: Context): Int =
        ((lockRemainingMillis(ctx) + 999L) / 1000L).toInt()

    /** 还剩几次机会（已冷却时为 0） */
    fun remainingAttempts(ctx: Context): Int =
        (MAX_ATTEMPTS - prefs(ctx).getInt(KEY_FAILS, 0)).coerceAtLeast(0)

    /**
     * 记一次输错，返回**剩余可尝试次数**；归零则进入冷却。
     *
     * 计数与截止时刻都写进加密偏好：只放内存的话，杀进程重进就能继续穷举。
     */
    fun recordFailure(ctx: Context): Int {
        val p = prefs(ctx)
        val fails = p.getInt(KEY_FAILS, 0) + 1
        if (fails >= MAX_ATTEMPTS) {
            p.edit()
                .putInt(KEY_FAILS, 0)
                .putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + LOCK_MS)
                .apply()
            return 0
        }
        p.edit().putInt(KEY_FAILS, fails).apply()
        return MAX_ATTEMPTS - fails
    }

    /** 解锁成功 / 关闭密码时清零 */
    fun resetFailures(ctx: Context) {
        prefs(ctx).edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply()
    }

    fun isFingerprintEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_FINGERPRINT, false)

    fun setFingerprintEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_FINGERPRINT, enabled).apply()
    }

    /** 检查设备是否支持指纹（平台 API；minSdk 33 起 `canAuthenticate` 与 BIOMETRIC_STRONG 均可用） */
    fun isFingerprintAvailable(ctx: Context): Boolean {
        val manager = ctx.getSystemService(BiometricManager::class.java) ?: return false
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * 弹出系统指纹认证。
     *
     * 用**平台** `android.hardware.biometrics.BiometricPrompt`（只要 Activity 作 Context），
     * 而不是 `androidx.biometric`：
     * `androidx.biometric:biometric:1.1.0` 会带入 `androidx.fragment:fragment:1.2.5`，
     * 其 `FragmentActivity.startActivityForResult` 会对 activity 1.9.3 生成的 requestCode
     * 抛 `IllegalArgumentException: Can only use lower 16 bits for requestCode` —— 一旦宿主改成
     * FragmentActivity，选图片/选文件等一切 ActivityResult 启动都会崩（2026-10-02 实测）。
     * 平台 API 不涉及 fragment，宿主可保持 ComponentActivity。
     *
     * [title] / [subtitle] 让锁屏入口和「修改 / 关闭密码」的验证入口共用同一套实现。
     */
    fun showFingerprintPrompt(
        activity: Activity,
        title: String = "指纹解锁",
        subtitle: String = "请验证指纹以进入 i记",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton("取消", executor) { _, _ -> onError("已取消") }
                .build()
            prompt.authenticate(CancellationSignal(), executor, callback)
        } catch (e: Throwable) {
            onError(e.message ?: "指纹认证不可用")
        }
    }
}
