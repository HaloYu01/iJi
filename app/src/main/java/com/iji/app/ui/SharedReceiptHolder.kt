package com.iji.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统分享（ACTION_SEND + 图片 MIME）送进来的小票图片路径。
 *
 * MainActivity 把图片复制到自己 cache 后只传路径：进程级持有，冷启动 / 已在前台都能拿到；
 * 「智能识别」页取走即清空，避免旋转屏幕或重复进页时又识别一次。
 */
object SharedReceiptHolder {
    private val _path = MutableStateFlow<String?>(null)
    val path: StateFlow<String?> = _path.asStateFlow()

    fun post(value: String?) {
        _path.value = value
    }

    fun consume() {
        _path.value = null
    }
}
