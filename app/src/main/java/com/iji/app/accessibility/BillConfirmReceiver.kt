package com.iji.app.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.iji.app.budget.NotificationHelper
import com.iji.app.data.repository.LedgerSelection
import com.iji.app.data.repository.QuickBillWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 超级岛大岛「确认记账」按钮的接收端：点击直接把识屏识别结果入账，不回应用核对。
 *
 * 为什么用广播而不是拉起 Activity：协议 3 的岛按钮按 Maling-Island 真机验证的结构
 * 挂广播 PendingIntent（`miui.focus.action_complete`），后台起 Activity 在部分
 * ROM 上还会被拦；记账本身不需要界面，toast 就是足够的反馈。
 *
 * 数据来源与语义：草稿只放内存（[ScreenCaptureFlow]，进程活着就能读到），这与其余
 * 入口一致——识别草稿必须经用户确认才入账，进程被杀就当作没截过。这里「确认」
 * 就是用户的那一下：按钮文案叫「确认记账」，点了即入账，事后可在应用里改。
 */
class BillConfirmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONFIRM) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                confirm(context)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun confirm(context: Context) {
        // 无论有没有草稿，先撤岛：挂着一条点完按钮还在的「识别完成」只会让人困惑
        NotificationHelper.cancelScreenshotNotice(context)

        val drafts = ScreenCaptureFlow.drafts.value
        if (drafts.isNullOrEmpty()) {
            toast(context, "没有待确认的识别结果")
            return
        }
        // 记账偏好里指定了账本就用它，否则跟随当前账本（与悬浮窗 / 导入页同一口径）
        val ledgerId = AutoBookkeepingPrefs.effectiveLedger(
            setting = AutoBookkeepingPrefs.ledger(context),
            current = LedgerSelection.current.value,
        )
        val ok = QuickBillWriter.addDrafts(drafts, ledgerId)
        // 写完立即清空：回应用打开导入页时不能再把这些笔当「待核对」重复导入
        ScreenCaptureFlow.consume()
        toast(
            context,
            if (ok > 0) "已记 $ok 笔到「$ledgerId」，可在应用内核对" else "记账失败，请进应用重试",
        )
    }

    private suspend fun toast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_CONFIRM = "com.iji.app.action.CONFIRM_CAPTURE_BILLS"
    }
}
