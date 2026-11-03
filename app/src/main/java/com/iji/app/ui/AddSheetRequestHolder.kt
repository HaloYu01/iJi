package com.iji.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「打开记账面板」的全局请求桥接（与 [NavIntentHolder] / [PendingBillHolder] 同构）。
 *
 * 记账面板由 `AppRoot` 持有，而引导弹窗可能显示在别的页面（如「我的 → 新手教程」），
 * 拿不到那边的 `showAddSheet`。这里用一个一次性信号中转：页面侧 [post]，
 * AppRoot 收集到就打开面板并 [consume]（避免重组 / 旋转屏幕时重复弹出）。
 */
object AddSheetRequestHolder {

    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    fun post() {
        _requested.value = true
    }

    fun consume() {
        _requested.value = false
    }
}
