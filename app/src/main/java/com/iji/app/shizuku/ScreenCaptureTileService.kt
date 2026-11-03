package com.iji.app.shizuku

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.iji.app.MainActivity
import com.iji.app.R
import com.iji.app.accessibility.ScreenCaptureFlow
import com.iji.app.budget.NotificationHelper
import com.iji.app.ui.Routes

/**
 * 下拉快捷开关「识屏记账」。
 *
 * 为什么是磁贴而不是悬浮球：磁贴只在用户主动下拉时出现，不常驻遮挡别的应用，
 * 也不需要悬浮窗权限。用户在微信/支付宝账单页下拉通知栏点一下，截到的正好是那一屏。
 *
 * 点下去先收起通知栏再截（`cmd statusbar collapse`，见 [ScreenCaptureFlow.startWithShizuku]），
 * 否则截到的是下拉面板本身。没授权时不假装能截，直接把用户带回应用里的授权入口。
 */
class ScreenCaptureTileService : TileService() {

    override fun onStartListening() {
        val ready = ShizukuBridge.state(this) == ShizukuState.READY
        qsTile?.apply {
            state = if (ready) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_scan_label)
            subtitle = getString(if (ready) R.string.tile_scan_ready else R.string.tile_scan_need_auth)
            updateTile()
        }
    }

    override fun onClick() {
        if (ShizukuBridge.state(this) != ShizukuState.READY) {
            openCaptureSettings()
            return
        }
        ScreenCaptureFlow.startWithShizuku(this, fromShade = true)
    }

    /** 回到应用内的「自动记账 → Shizuku」入口，那里能申请授权 / 打开 Shizuku */
    private fun openCaptureSettings() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.AUTO_BOOKKEEPING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
