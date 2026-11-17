package com.iji.app.accessibility

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.iji.app.autorecord.AutoDiag

/**
 * 直写系统设置的「无障碍自愈」，对齐钱迹的 `SecureSettingsA11y`（研究包 `da/k.java`）。
 *
 * 普通应用拿不到 `WRITE_SECURE_SETTINGS`，但可以用一条 adb 命令授权：
 * ```
 * adb shell pm grant <包名> android.permission.WRITE_SECURE_SETTINGS
 * ```
 * 授权后无需 Shizuku 也能把本服务写回 `enabled_accessibility_services`——
 * 这是钱迹「被清理后仍能自己修回来」的技术基础。
 *
 * 三条自律：
 * - 只**追加**本服务，不动列表里其它无障碍服务；
 * - 没有权限就直接返回 false，不假装成功；
 * - 写完不立刻断言成功，交给 [waitUntilRunning] 用系统绑定结果说话。
 */
object A11ySelfHeal {

    private const val TAG = "A11ySelfHeal"
    private const val KEY_ENABLED_SERVICES = "enabled_accessibility_services"
    private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"

    /** 当前进程是否已被授权写系统设置（adb 或 root 授权过才有） */
    fun canWrite(context: Context): Boolean = runCatching {
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** 需要用户执行的那一条命令；没有它，自愈只能退化为「跳系统设置」 */
    fun adbCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /**
     * 把本服务写回启用列表并打开无障碍总开关。
     *
     * 返回 true 只表示**写入成功**，不表示系统已经绑定；
     * 是否真的跑起来要用 [waitUntilRunning] 确认。
     */
    fun enable(context: Context): Boolean {
        if (!canWrite(context)) {
            AutoDiag.log(context, "heal", "skip: no WRITE_SECURE_SETTINGS")
            return false
        }
        val component = PaymentAccessibilityStatus.serviceComponent(context).flattenToString()
        val current = runCatching {
            Settings.Secure.getString(context.contentResolver, KEY_ENABLED_SERVICES)
        }.getOrNull()
        // 复用 Shizuku 路径的追加逻辑：已在列表里就原样返回，不重复不覆盖
        val target = AccessibilityShortcutHelper.appendService(current, component)
        return runCatching {
            Settings.Secure.putString(context.contentResolver, KEY_ENABLED_SERVICES, target)
            Settings.Secure.putInt(context.contentResolver, KEY_ACCESSIBILITY_ENABLED, 1)
            AutoDiag.log(context, "heal", "put ok uses=${target.split(':').size}")
            true
        }.getOrElse { error ->
            Log.w(TAG, "写回无障碍失败", error)
            AutoDiag.log(context, "heal", "put failed: ${error.message}")
            false
        }
    }

    /**
     * 写回后系统绑定有延迟（钱迹等 1s + 2s），这里轮询到真的跑起来为止。
     * 超时就返回 false，由调用方回落到「跳系统设置」。
     */
    fun waitUntilRunning(context: Context, timeoutMs: Long = 2500L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (PaymentAccessibilityStatus.isRunning(context)) return true
            runCatching { Thread.sleep(250) }
        }
        return PaymentAccessibilityStatus.isRunning(context)
    }
}
