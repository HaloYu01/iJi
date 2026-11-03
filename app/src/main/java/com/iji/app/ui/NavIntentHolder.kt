package com.iji.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知点击 → 页面导航的桥接。
 *
 * 通知是系统进程发起的，只能经 Intent 把目标路由带进 Activity；
 * 而导航控制器在 Compose 里。这里用一个全局 StateFlow 中转：
 * Activity（onCreate / onNewIntent）写入，Compose 侧收集后导航并消费。
 *
 * 必须同时处理 onNewIntent：App 已在前台时（launchMode=singleTask）
 * Activity 不会重建，只走 onNewIntent。
 */
object NavIntentHolder {

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    /** 写入待跳转路由（null 表示无） */
    fun post(route: String?) {
        _pendingRoute.value = route
    }

    /** 导航完成后清空，避免重组/旋转屏幕时重复跳转 */
    fun consume() {
        _pendingRoute.value = null
    }
}
