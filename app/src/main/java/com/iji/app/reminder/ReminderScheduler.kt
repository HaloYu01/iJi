package com.iji.app.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.iji.app.data.repository.SettingsRepository
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 记账提醒的开关与排期。
 *
 * 开关状态存 DataStore（与预算/性能设置同一套仓储），默认 **关闭** ——
 * 通知是强打扰，应由用户主动开启，而不是装完就每晚推送。
 */
object ReminderScheduler {

    const val UNIQUE_WORK_NAME = "daily_record_reminder"

    const val KEY_ENABLED = "reminder_enabled"
    const val KEY_HOUR = "reminder_hour"

    /** 默认提醒时刻（21:00） */
    const val DEFAULT_HOUR = 21

    // ── 提醒通知样式 ──
    // 原生实时通知：普通通知；Android 15+ 附加 requestPromotedOngoing，支持实况的机型渲染成实时卡片
    const val STYLE_NATIVE = "native"
    // 小米超级岛：焦点通知（miui.focus.param），Shizuku 可用时借它即时上岛（见 SuperIslandNotifier）
    const val STYLE_ISLAND = "xiaomi_island"
    const val KEY_STYLE = "reminder_notify_style"

    /** 当前提醒通知样式（[STYLE_NATIVE] / [STYLE_ISLAND]），默认原生 */
    fun currentStyle(context: Context): String =
        SettingsRepository.getString(context, KEY_STYLE, STYLE_NATIVE)

    fun setStyle(context: Context, style: String) {
        SettingsRepository.setString(context, KEY_STYLE, style)
    }

    fun isEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean, hour: Int = currentHour(context)) {
        SettingsRepository.setBoolean(context, KEY_ENABLED, enabled)
        SettingsRepository.setInt(context, KEY_HOUR, hour)
        if (enabled) {
            // 用户主动开启 → 必须按新时刻**重建**任务（KEEP 会保留上一次的排期）
            scheduleDaily(context, hour, ExistingPeriodicWorkPolicy.REPLACE)
        } else {
            cancel(context)
        }
    }

    /**
     * 修改提醒时刻。开启状态下一并重建任务，否则只落库（下次开启时生效）。
     *
     * 这里必须用 REPLACE：`KEEP` 会保留已存在的周期任务，改完时间看起来"没生效"，
     * 用户只能靠关掉再打开才能生效——这是个很容易被当成 bug 的坑。
     */
    fun setHour(context: Context, hour: Int) {
        val h = hour.coerceIn(0, 23)
        SettingsRepository.setInt(context, KEY_HOUR, h)
        if (isEnabled(context)) scheduleDaily(context, h, ExistingPeriodicWorkPolicy.REPLACE)
    }

    fun currentHour(context: Context): Int =
        SettingsRepository.getInt(context, KEY_HOUR, DEFAULT_HOUR).coerceIn(0, 23)

    /** 按当前开关状态重新排期（冷启动时调用） */
    fun reschedule(context: Context) {
        if (isEnabled(context)) {
            // 冷启动用 KEEP：任务已经排好，重建只会把周期往后推一天
            scheduleDaily(context, currentHour(context), ExistingPeriodicWorkPolicy.KEEP)
        } else {
            cancel(context)
        }
    }

    /** 排入每日提醒。[policy] 决定已存在同名任务时是保留（冷启动）还是按新时刻重建（用户改设置） */
    fun scheduleDaily(
        context: Context,
        hour: Int,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
    ) {
        val delayMinutes = minutesUntil(hour)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /** 距离下一个指定小时的分钟数（已过则算到明天同一时刻） */
    private fun minutesUntil(hour: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMinutes().coerceAtLeast(1L)
    }
}