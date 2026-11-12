package com.iji.app.data.repository

import android.content.Context
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
 *
 * **冷启动必须恢复真实账本**：此前进程初值写死「默认账本」，而账本列表由 Room 异步到达，
 * 于是开屏先按占位账本查一次（多半是空 → 首屏误报「无账单」），列表到达后再
 * `select(真实账本)` 让全量 / 按月两条账单 Flow 各重查一遍，账单才「跳」出来。
 * 现在由 [init] 在 Application 启动时从设置缓存恢复，首帧就按真实账本查询。
 */
object LedgerSelection {

    private const val KEY_CURRENT_LEDGER = "current_ledger"
    private const val DEFAULT_LEDGER = "默认账本"

    private val _current = MutableStateFlow(DEFAULT_LEDGER)

    val current: StateFlow<String> = _current.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    /** Application.onCreate 里、SettingsRepository.warmUp 之后调用：读内存缓存，零磁盘 IO */
    fun init(context: Context) {
        appContext = context.applicationContext
        val saved = SettingsRepository.getString(context, KEY_CURRENT_LEDGER, "")
        if (saved.isNotBlank()) _current.value = saved
    }

    fun select(name: String) {
        // 同值不写：账本列表到达、点当前账本等场景不必重复落盘，也不会无谓触发订阅
        if (_current.value == name) return
        _current.value = name
        // 预览 / 进程级组件在 Application 之前拿不到 context 时只改内存
        appContext?.let { SettingsRepository.setString(it, KEY_CURRENT_LEDGER, name) }
    }
}
