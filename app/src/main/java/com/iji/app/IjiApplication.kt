package com.iji.app

import android.app.Application
import com.iji.app.budget.BudgetAlertScheduler
import com.iji.app.budget.BudgetComponents
import com.iji.app.budget.NotificationHelper
import com.iji.app.data.db.DbProvider
import com.iji.app.data.repository.LedgerSelection
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.reminder.ReminderScheduler
import com.iji.app.ui.PerfState
import com.iji.app.ui.mine.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class IjiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 偏好设置必须先整体预热：下面的 PerfState / HapticsState / FontScaleState
        // 会在 onCreate 里同步读取，未预热时每个键都要 runBlocking 读一次 DataStore。
        // 这里一次读完（含 SharedPreferences 迁移），之后所有 getter 只查内存。
        runBlocking { SettingsRepository.warmUp(this@IjiApplication) }
        // 当前账本从设置恢复：否则冷启动先按占位账本查一次空数据，账本列表到达后
        // 再切账本，让全量 / 按月两条账单 Flow 各重查一遍，首屏就先空后跳账单。
        LedgerSelection.init(this)
        DbProvider.init(this)
        // 预算组件需要应用上下文（发通知用）；必须在任何预算检查之前初始化
        BudgetComponents.init(this)
        // 性能开关（降低动态效果）：必须在任何界面读取 PerfState 之前载入
        PerfState.init(this)
        // 预测性返回动画 / 触感反馈开关：GlassDialog、IjiTheme 首帧就会读取，先载入
        com.iji.app.ui.util.PredictiveBackSettings.init(this)
        com.iji.app.ui.HapticsState.init(this)
        // 全局字体大小：主题根会读它做 fontScale（此前 font_size 只写不读）
        com.iji.app.ui.FontScaleState.init(this)
        // 用户自定义分类：必须在任何界面读取 CategoryCatalog 之前载入内存
        com.iji.app.data.repository.CategoryStore.init(this)
        // 本地识别规则：本机 OCR / 文本兜底在拿不到 Context 的地方同步读它，先载入内存
        com.iji.app.ai.LocalRuleStore.init(this)
        // 通知渠道与每日排期和首帧无关：放到 IO，别让 WorkManager 排期的多次
        // binder / DB 往返占用冷启动主线程
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            NotificationHelper.createChannels(this@IjiApplication)
            runCatching { BudgetAlertScheduler.scheduleDaily(this@IjiApplication) }
                .onFailure { android.util.Log.w("IjiApplication", "预算巡检排期失败", it) }
            runCatching { ReminderScheduler.reschedule(this@IjiApplication) }
                .onFailure { android.util.Log.w("IjiApplication", "记账提醒排期失败", it) }
        }

        // 设置崩溃日志捕获
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLogger.saveCrashLog(this, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
