package com.iji.app.budget

import android.content.Context
import android.util.Log
import com.iji.app.data.db.DbProvider
import com.iji.app.data.repository.BudgetRepository

/**
 * 预算相关组件的装配入口。
 *
 * 本项目**没有引入 Hilt/Koin**（依赖由 [DbProvider] 在 Application 中手工初始化），
 * 因此这里用极简的 Service Locator 暴露仓库与应用上下文；若日后接入 Hilt，
 * 只需把它替换为一个 `@Module` + `@Provides`（配合 `@Singleton`），调用方无需改动。
 *
 * 数据库不可用时回退到「无预算」空实现，保证预览/异常场景不崩溃。
 */
object BudgetComponents {

    @Volatile
    private var appContext: Context? = null

    /** 由 IjiApplication.onCreate 调用；存 applicationContext，避免持有 Activity */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 应用上下文；未初始化时返回 null（预览等场景），调用方据此跳过发通知 */
    fun contextOrNull(): Context? = appContext

    fun repository(): BudgetRepository = runCatching {
        val db = DbProvider.database
        BudgetRepository(db.budgetDao(), db.budgetAlertDao())
    }.getOrElse {
        Log.w(TAG, "BudgetRepository 初始化失败，回退空实现（预算提醒不可用）", it)
        BudgetRepository(null, null)
    }

    private const val TAG = "BudgetComponents"
}
