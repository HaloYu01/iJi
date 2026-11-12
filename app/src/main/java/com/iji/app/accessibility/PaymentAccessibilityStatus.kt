package com.iji.app.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * 无障碍服务的启用状态查询、系统设置跳转，以及「音量键快捷键」绑定状态查询。
 *
 * 无障碍服务**没有**「是否已授权」的公开 API，只能读系统 `Settings.Secure` 里的
 * `ENABLED_ACCESSIBILITY_SERVICES`（已启用的服务组件列表）和
 * `accessibility_shortcut_target_service`（音量键快捷键当前指向谁）来判断，
 * 这是社区通用做法。**应用自身 uid 无权写入**这些字段；但用户主动点击、且 Shizuku 已授权时，
 * 可借 shell 身份写入（见 [AccessibilityShortcutHelper]，带备份与一键恢复）。
 */
object PaymentAccessibilityStatus {

    /** 目标服务组件名，形如 `com.iji.app/.accessibility.PaymentNotifyAccessibilityService` */
    fun serviceComponent(context: Context): ComponentName = ComponentName(
        context.packageName,
        PaymentNotifyAccessibilityService::class.java.name,
    )

    fun isEnabled(context: Context): Boolean {
        val expected = serviceComponent(context).flattenToString()
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull().orEmpty()
        if (enabled.isBlank()) return false
        // 系统返回值是以 ':' 分隔的扁平组件名列表，大小写与简写可能有差异
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        return splitter.any { it.equals(expected, ignoreCase = true) }
    }

    /** 跳转系统「无障碍」设置页，让用户手动开启（应用无法代替用户授权） */
    fun openSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    /**
     * 系统「音量键快捷键」当前绑定的目标服务。这个快捷键是全局唯一的，
     * 常被别的应用占用；占用时按住音量键唤起的是那个应用，本服务一次都不会被启动。
     */
    fun shortcutTarget(context: Context): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, KEY_SHORTCUT_TARGET)
    }.getOrNull()

    /** 音量键快捷键是否指向本 App 的音量键识屏服务 */
    fun isVolumeShortcutBound(context: Context): Boolean {
        val expected = ComponentName(
            context.packageName,
            VolumeKeyCaptureService::class.java.name,
        ).flattenToString()
        val targets = shortcutTarget(context).orEmpty()
        if (targets.isBlank()) return false
        // 与 ENABLED_ACCESSIBILITY_SERVICES 一致：部分 ROM 会以 ':' 分隔多个目标
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(targets)
        return splitter.any { it.equals(expected, ignoreCase = true) }
    }

    /**
     * 打开系统无障碍设置，让用户为本服务开启「音量键快捷方式」。
     *
     * 刻意不跳 `android.settings.ACCESSIBILITY_SHORTCUT_SETTINGS`：真机（HyperOS）上这个
     * action 虽能 resolve 到 `AccessibilityEditShortcutsActivity`，但缺必要 extra 时隐式启动
     * 不显示任何页面、显式启动会落到「无障碍功能菜单」，比通用页更难找到目标。
     * 通用无障碍页各 ROM 都有，配合页面里的操作文案即可定位到本服务的快捷方式开关。
     */
    fun openShortcutSettings(context: Context): Boolean = openSettings(context)

    private const val KEY_SHORTCUT_TARGET = "accessibility_shortcut_target_service"
}