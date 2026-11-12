package com.iji.app.accessibility

import android.content.ComponentName
import android.content.Context
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.shizuku.ShizukuBridge
import com.iji.app.shizuku.ShizukuState
import kotlinx.coroutines.delay

/**
 * 借 Shizuku 的 shell 身份，把「音量键快捷键绑定」「开启无障碍服务」这两件原本只能
 * 跳系统设置手动完成的事变成应用内一键操作，且**可撤销**。
 *
 * 设计约束（用户 2026-11-10 确认 F）：
 * - 只由用户主动点击触发，界面必须写明会修改系统设置；
 * - 写入前先备份原值到 [SettingsRepository]，关闭时按备份恢复（不覆盖别人的服务）；
 * - `enabled_accessibility_services` 一律**追加**本服务组件，绝不整串覆盖；
 * - Shizuku 不可用 / 命令失败时原样回落「跳系统设置」，不假装成功；
 * - 命令与备份编解码都是纯函数，可直跑 JVM 单测。
 *
 * 参考 Hyper-pick-up-code 的 `AccessibilityShortcutHelper` 与 AutoAccounting 的
 * `OcrTools.requestPermission` 思路；两仓库分别为 AGPL-3.0 / GPL-3.0，本项目闭源，
 * 只借鉴系统 API 用法，未复制源码。
 */
object AccessibilityShortcutHelper {

    private const val KEY_TARGET = "accessibility_shortcut_target_service"
    /** HyperOS 部分版本读这个复数键，两个都写才能稳定生效 */
    private const val KEY_TARGETS = "accessibility_shortcut_targets"
    private const val KEY_ENABLED = "accessibility_shortcut_enabled"
    private const val KEY_DIALOG_SHOWN = "accessibility_shortcut_dialog_shown"
    private const val KEY_ON_LOCK_SCREEN = "accessibility_shortcut_on_lock_screen"

    private const val KEY_ENABLED_SERVICES = "enabled_accessibility_services"
    private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"

    const val KEY_SHORTCUT_BACKUP = "auto_shortcut_secure_backup"
    const val KEY_A11Y_BACKUP = "auto_a11y_services_backup"

    /** 备份里的 null 用不可打印字符表示，避免与真实空串混淆 */
    private const val NULL_MARK = "\u0000"

    val SHORTCUT_KEYS = listOf(
        KEY_TARGET,
        KEY_TARGETS,
        KEY_ENABLED,
        KEY_DIALOG_SHOWN,
        KEY_ON_LOCK_SCREEN,
    )

    fun shortcutComponent(context: Context): String = ComponentName(
        context,
        VolumeKeyCaptureService::class.java,
    ).flattenToString()

    fun isShizukuReady(context: Context): Boolean =
        ShizukuBridge.state(context) == ShizukuState.READY

    /** 绑定音量键快捷键：一次写 5 个 secure 键，与系统在「无障碍 → 快捷键」里做的等价 */
    fun buildBindCommands(component: String): List<String> = listOf(
        put(KEY_TARGET, component),
        put(KEY_TARGETS, component),
        put(KEY_ENABLED, "1"),
        put(KEY_DIALOG_SHOWN, "1"),
        put(KEY_ON_LOCK_SCREEN, "1"),
    )

    /** 恢复备份：原本不存在的键用 delete，存在的用 put 写回原值 */
    fun buildRestoreCommands(values: Map<String, String?>): List<String> =
        SHORTCUT_KEYS.mapNotNull { key ->
            if (!values.containsKey(key)) return@mapNotNull null
            val value = values[key]
            if (value == null) "settings delete secure $key" else put(key, value)
        }

    /** 在系统已启用的无障碍服务列表尾部追加本服务，不改动其它服务 */
    fun appendService(current: String?, component: String): String {
        val list = current.orEmpty()
            .split(':')
            .filter { it.isNotBlank() }
        if (list.any { it.equals(component, ignoreCase = true) }) return list.joinToString(":")
        return (list + component).joinToString(":")
    }

    /** 撤销时把本服务从列表里摘掉，保留其它服务 */
    fun removeService(current: String?, component: String): String = current.orEmpty()
        .split(':')
        .filter { it.isNotBlank() && !it.equals(component, ignoreCase = true) }
        .joinToString(":")

    fun encodeBackup(values: Map<String, String?>): String =
        SHORTCUT_KEYS.joinToString("\n") { "${it}=${values[it] ?: NULL_MARK}" }

    fun decodeBackup(raw: String): Map<String, String?> = raw
        .split('\n')
        .mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i) to
                line.substring(i + 1).takeIf { it != NULL_MARK }
        }
        .toMap()

    private fun put(key: String, value: String): String =
        "settings put secure $key \"$value\""

    private suspend fun readSecure(context: Context, key: String): String? =
        ShizukuBridge.exec(context, "settings get secure $key")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }

    private suspend fun runAll(context: Context, commands: List<String>): Boolean =
        commands.all { ShizukuBridge.exec(context, it) != null }

    /** 一键绑定音量键快捷键；成功以「系统 Secure 值确实指向本应用」为准，不看命令是否没报错 */
    suspend fun bindShortcut(context: Context): Boolean {
        if (!isShizukuReady(context)) return false
        val before = SHORTCUT_KEYS.associateWith { readSecure(context, it) }
        SettingsRepository.setString(context, KEY_SHORTCUT_BACKUP, encodeBackup(before))
        if (!runAll(context, buildBindCommands(shortcutComponent(context)))) return false
        delay(500)
        return PaymentAccessibilityStatus.isVolumeShortcutBound(context)
    }

    /** 一键解绑：优先按备份还原，没备份时清空本应用写入的那几个键 */
    suspend fun unbindShortcut(context: Context): Boolean {
        if (!isShizukuReady(context)) return false
        val raw = SettingsRepository.getString(context, KEY_SHORTCUT_BACKUP, "")
        val values = if (raw.isBlank()) {
            // 没有备份（例如老版本写过一次）时只摘掉指向本应用的两个键并关掉总开关，
            // 不去动「锁屏可用 / 提示已显示」这类与其它服务共用的系统状态。
            mapOf(
                KEY_TARGET to null,
                KEY_TARGETS to null,
                KEY_ENABLED to "0",
            )
        } else {
            decodeBackup(raw)
        }
        val ok = runAll(context, buildRestoreCommands(values))
        if (ok) SettingsRepository.setString(context, KEY_SHORTCUT_BACKUP, "")
        delay(300)
        return ok
    }

    /** 一键开启支付通知无障碍服务；失败或没 Shizuku 时由调用方回落系统设置 */
    suspend fun enableAccessibility(context: Context): Boolean {
        if (!isShizukuReady(context)) return false
        val component = PaymentAccessibilityStatus.serviceComponent(context).flattenToString()
        val current = readSecure(context, KEY_ENABLED_SERVICES)
        if (current?.split(':')?.any { it.equals(component, ignoreCase = true) } == true) return true
        SettingsRepository.setString(context, KEY_A11Y_BACKUP, current.orEmpty())
        val ok = runAll(
            context,
            listOf(
                put(KEY_ENABLED_SERVICES, appendService(current, component)),
                put(KEY_ACCESSIBILITY_ENABLED, "1"),
            ),
        )
        if (!ok) return false
        delay(700)
        return PaymentAccessibilityStatus.isEnabled(context)
    }

    /** 撤销一键开启：只摘掉本服务，其它无障碍服务原样保留 */
    suspend fun disableAccessibility(context: Context): Boolean {
        if (!isShizukuReady(context)) return false
        val component = PaymentAccessibilityStatus.serviceComponent(context).flattenToString()
        val original = SettingsRepository.getString(context, KEY_A11Y_BACKUP, "")
        val current = readSecure(context, KEY_ENABLED_SERVICES)
        val target = if (original.isNotBlank()) original else removeService(current, component)
        val ok = runAll(context, listOf(put(KEY_ENABLED_SERVICES, target)))
        if (ok) SettingsRepository.setString(context, KEY_A11Y_BACKUP, "")
        return ok
    }
}
