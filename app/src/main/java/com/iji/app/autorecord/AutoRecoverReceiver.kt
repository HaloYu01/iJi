package com.iji.app.autorecord

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.iji.app.accessibility.A11ySelfHeal
import com.iji.app.accessibility.AccessibilityShortcutHelper
import com.iji.app.accessibility.PaymentAccessibilityStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知上的「恢复」动作，对齐钱迹的 `AutoRecordToggleReceiver`。
 *
 * 为什么把它放在通知而不是页面里：被系统清理后，用户多半正在别的 App 里，
 * 下拉通知栏点一下就修好，比「打开应用 → 找到页面 → 点按钮」少两步。
 * Receiver 不依赖无障碍服务进程，只要进程能被拉起就能执行。
 *
 * 恢复顺序（每一步都真能落地，失败才降级）：
 * 1. Shizuku 可用 → 借 shell 身份写回（无需额外授权）；
 * 2. 有 `WRITE_SECURE_SETTINGS` → 直接写系统设置（adb 授权过才有）；
 * 3. 都没有 → 打开系统无障碍页，让用户手动开。
 */
class AutoRecoverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RECOVER) return
        // 广播有 10s 上限，写设置 + 等系统绑定尽量在后台线程做完
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (PaymentAccessibilityStatus.isRunning(app)) {
                    AutoRecordStatusService.refresh(app)
                    return@launch
                }
                val ok = when {
                    AccessibilityShortcutHelper.isShizukuReady(app) ->
                        AccessibilityShortcutHelper.enableAccessibility(app)

                    A11ySelfHeal.canWrite(app) ->
                        A11ySelfHeal.enable(app) && A11ySelfHeal.waitUntilRunning(app)

                    else -> false
                }
                if (ok) {
                    // 已经连上：通知刷回「运行中」，服务也在跑就顺带刷新
                    AutoRecordStatusService.refresh(app)
                } else {
                    // 没能力写回：把用户送到能开的地方，不留下「点了没反应」
                    PaymentAccessibilityStatus.openSettings(app)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "通知恢复失败", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AutoRecover"
        const val ACTION_RECOVER = "com.iji.app.action.RECOVER_A11Y"
    }
}
