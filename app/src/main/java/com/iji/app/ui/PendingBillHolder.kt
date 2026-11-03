package com.iji.app.ui

import com.iji.app.ai.AiBookkeepingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「自动记账」待确认账单的内存桥接。
 *
 * 链路：支付通知 → 无障碍服务解析 → 通知动作「记一笔」→ [com.iji.app.accessibility.PaymentBillStore]
 * 落盘 → Receiver 拉起 MainActivity → 本桥接 → AppRoot 打开记账面板预填。
 *
 * 与 [NavIntentHolder] 同构：通知动作发生在应用进程之外，只能靠 Intent + 全局 StateFlow 中转；
 * 导航/消费后立刻清空，避免重组或旋转屏幕时重复弹出面板。
 */
object PendingBillHolder {

    private val _pending = MutableStateFlow<AiBookkeepingService.BillResult?>(null)
    val pending: StateFlow<AiBookkeepingService.BillResult?> = _pending.asStateFlow()

    fun post(bill: AiBookkeepingService.BillResult?) {
        _pending.value = bill
    }

    fun consume() {
        _pending.value = null
    }
}