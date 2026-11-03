package com.iji.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「当前账本」的**进程内共享**状态。
 *
 * 为什么必须共享：Navigation Compose 里 `viewModel()` 按**目的地**作用域创建，
 * 主页 / 账本管理 / 分类管理 / 账单导入 / 记账面板拿到的是**不同的 MainViewModel 实例**
 * （DAO 注释里"多个 MainViewModel 实例同时 init"也印证了这点）。
 * 若每个实例各自持有当前账本，在一个页面切换、改名或删除账本，另一个页面完全看不到
 * —— 这正是"两个页面数据不一致"的成因。
 *
 * 提升到共享状态后，所有实例读写同一份，天然一致。
 */
object LedgerSelection {

    private val _current = MutableStateFlow("默认账本")

    val current: StateFlow<String> = _current.asStateFlow()

    fun select(name: String) {
        _current.value = name
    }
}
