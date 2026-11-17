package com.iji.app.autorecord

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.iji.app.MainActivity
import com.iji.app.R
import com.iji.app.accessibility.AutoBookkeepingPrefs
import com.iji.app.accessibility.CapturePreferences
import com.iji.app.budget.NotificationHelper
import com.iji.app.ui.Routes

/**
 * 全自动常驻状态服务：前台服务防 ROM 清理后台，通知作为运行状态灯。
 * 后台 `startForeground` 会抛异常，必须吞掉并 stopSelf，否则进程（含无障碍）崩溃导致开关闪断。
 */
class AutoRecordStatusService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val ok = runCatching { startAsForeground(PaymentStatus.isConnected()) }.isSuccess
        if (!ok) {
            // 后台不允许前台服务：安静退出，等下次回前台 / 无障碍重连再启动
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun startAsForeground(connected: Boolean) {
        val notification = buildNotification(this, connected)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {

        private const val CHANNEL_ID = "auto_record_status"
        private const val CHANNEL_NAME = "自动记账运行状态"
        private const val NOTIFICATION_ID = 9201

        /** 服务当前是否活着：只有活着才允许直接更新通知 */
        @Volatile
        private var running = false

        /** 总闸开 + 全自动：这是它唯一的运行条件 */
        fun shouldRun(context: Context): Boolean =
            AutoBookkeepingPrefs.masterEnabled(context) &&
                CapturePreferences.workflow(context) == CapturePreferences.WORKFLOW_AUTO

        /** 无障碍连接后调用：保证服务在跑，并把通知刷成「运行中」 */
        fun startIfEnabled(context: Context) {
            if (!shouldRun(context)) return
            safeStart(context, allowForeground = true)
        }

        /** 用户关总闸 / 切到半自动：停止，不占通知栏 */
        fun stop(context: Context) {
            runCatching { context.stopService(intent(context)) }
        }

        /**
         * 无障碍被系统关掉：**不停止服务**，只把通知改成「已关闭，点此恢复」。
         * 这是用户唯一能看出「它被系统杀了」而不是「应用坏了」的地方。
         */
        fun markDisconnected(context: Context) {
            if (!shouldRun(context)) {
                stop(context)
                return
            }
            // 此时应用大概率在后台：服务还活着就直接刷新通知，
            // 不再 startService（后台启动前台服务会被系统拒绝并崩溃）
            if (running) {
                notify(context, connected = false)
                return
            }
            safeStart(context, allowForeground = false)
        }

        /** 平台数量 / 总闸变化后刷新通知文案 */
        fun refresh(context: Context) {
            if (!shouldRun(context)) {
                stop(context)
                return
            }
            if (running) {
                notify(context, connected = PaymentStatus.isConnected())
                return
            }
            safeStart(context, allowForeground = false)
        }

        private fun notify(context: Context, connected: Boolean) {
            runCatching {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                manager.notify(NOTIFICATION_ID, buildNotification(context, connected))
            }
        }

        private fun safeStart(context: Context, allowForeground: Boolean) {
            val ok = runCatching {
                if (allowForeground) {
                    ContextCompat.startForegroundService(context, intent(context))
                } else {
                    context.startService(intent(context))
                }
            }.isSuccess
            if (!ok) {
                // 兜底：前台启动被系统拒绝时再试普通启动；两条都失败就交给下次回前台
                runCatching { context.startService(intent(context)) }
            }
        }

        private fun intent(context: Context): Intent =
            Intent(context, AutoRecordStatusService::class.java)

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                    description = "显示自动记账是否在运行；被系统关闭时在这里恢复"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
        }

        fun buildNotification(context: Context, connected: Boolean): Notification {
            val count = AutoListenPrefs.packages(context).size
            val title = if (connected) "自动记账 · 运行中" else "自动记账 · 已关闭"
            val text = if (connected) {
                if (count == 0) "未选择监听平台，点开选择" else "正在监听 $count 个平台，切到账单页会自动识别"
            } else {
                "无障碍服务被系统关闭，点此恢复"
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.AUTO_BOOKKEEPING)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // 只有断开时才给动作按钮：运行中多一个「恢复」只会让人困惑
            if (!connected) {
                val recover = PendingIntent.getBroadcast(
                    context,
                    NOTIFICATION_ID + 1,
                    Intent(context, AutoRecoverReceiver::class.java)
                        .setAction(AutoRecoverReceiver.ACTION_RECOVER),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "恢复", recover)
            }
            return builder.build()
        }
    }
}

/** 服务与页面共用的一处「无障碍是否连着」判断，避免各自读一份状态 */
object PaymentStatus {
    fun isConnected(): Boolean =
        runCatching {
            com.iji.app.accessibility.PaymentNotifyAccessibilityService.isConnected()
        }.getOrDefault(false)
}