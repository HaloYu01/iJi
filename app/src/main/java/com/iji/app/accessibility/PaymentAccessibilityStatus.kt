package com.iji.app.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * 支付通知无障碍服务的启用状态查询与跳转。
 *
 * 无障碍服务**没有**「是否已授权」的公开 API，只能读系统
 * `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`（已启用的服务组件列表）来判断，
 * 这是社区通用做法。注意该字段是「用户主动开启」的系统状态，应用无法自行写入。
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
}