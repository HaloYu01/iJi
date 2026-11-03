package com.iji.app

import android.app.Application
import com.iji.app.budget.BudgetAlertScheduler
import com.iji.app.budget.BudgetComponents
import com.iji.app.budget.NotificationHelper
import com.iji.app.data.db.DbProvider
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.reminder.ReminderScheduler
import com.iji.app.ui.PerfState
import com.iji.app.ui.mine.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IjiApplication : Application() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
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
        // 预热偏好设置缓存：各设置页在组合期同步读取这些键，未预热会退化成主线程 runBlocking 读盘
        ioScope.launch { SettingsRepository.warmUp(this@IjiApplication) }

        // 通知渠道（预算 + 记账提醒，幂等）
        NotificationHelper.createChannels(this)
        // 预算提醒：每日巡检排期（已存在则保留，不重置周期）
        runCatching { BudgetAlertScheduler.scheduleDaily(this) }
            .onFailure { android.util.Log.w("IjiApplication", "预算巡检排期失败", it) }
        // 记账提醒：按用户开关重新排期（默认关闭）
        runCatching { ReminderScheduler.reschedule(this) }
            .onFailure { android.util.Log.w("IjiApplication", "记账提醒排期失败", it) }

        // 设置崩溃日志捕获
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLogger.saveCrashLog(this, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}