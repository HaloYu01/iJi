package com.iji.app.ui

import android.content.Context
import com.iji.app.ai.AiConfig
import com.iji.app.data.db.DbProvider
import com.iji.app.data.model.Currency
import com.iji.app.data.model.CurrencyDisplay
import com.iji.app.data.repository.LedgerRepository
import com.iji.app.data.repository.LedgerSelection
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.reminder.ReminderScheduler
import com.iji.app.ui.mine.ProfileState
import com.iji.app.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 开局引导的一次性草稿。
 *
 * 所有选择只改这份内存对象，点「完成」才由 [OnboardingApplier.apply] 统一提交；
 * 「跳过 / 取消」和途中被杀进程都不会留下半截配置。主题 / 字号因此在向导内
 * 只做页内预览，不实时改全局状态——也保证纯白引导页不会被选中的深色主题染黑。
 */
data class OnboardingDraft(
    val theme: String = "system",
    val fontSizeSp: Float = FontScaleState.BASE_SP,
    val nickname: String = "",
    /** 已是 file://（私有目录）或刚选到的 content://，提交前统一落盘为私有文件 */
    val avatarUri: String = "",
    val currencyCode: String = Currency.CNY.code,
    val monthStartDay: Int = 1,
    val weekStartDay: Int = 1,
    val is24Hour: Boolean = true,
    val aiProviderId: String = "zhipu",
    val aiKey: String = "",
    val aiUrl: String = "",
    val aiModel: String = "",
    val aiEnabled: Boolean = true,
    val aiText: Boolean = false,
    val aiVision: Boolean = true,
    val aiCategory: Boolean = true,
    val aiAccount: Boolean = true,
    val reminderOn: Boolean = false,
    val reminderHour: Int = ReminderScheduler.DEFAULT_HOUR,
) {
    companion object {
        /** 重配模式下每一步的初值 = 当前落盘配置，完成即「覆盖现有」。 */
        fun current(context: Context): OnboardingDraft = OnboardingDraft(
            theme = SettingsRepository.getString(context, "theme_mode", "system"),
            fontSizeSp = FontScaleState.fontSizeSp,
            nickname = SettingsRepository.getNickname(context),
            avatarUri = SettingsRepository.getAvatarUri(context),
            // CurrencyDisplay 由 MainViewModel 按当前账本同步，进程内可读，不必在组合期查 Room
            currencyCode = CurrencyDisplay.current.code,
            monthStartDay = SettingsRepository.getInt(context, "month_start_day", 1).coerceIn(1, 28),
            weekStartDay = SettingsRepository.getInt(context, "week_start_day", 1).let { if (it == 7) 7 else 1 },
            is24Hour = SettingsRepository.getBoolean(context, "is_24_hour", true),
            aiProviderId = AiConfig.getProvider(context),
            aiKey = AiConfig.getApiKey(context),
            aiUrl = AiConfig.getApiUrl(context),
            aiModel = AiConfig.getModel(context),
            aiEnabled = AiConfig.isEnabled(context),
            // 读存储原值而非 parseMode() 的解析结果：没配 Key 时后者会回落 local，
            // 否则重配只要不动这个开关，就会把用户原先选的「AI 文本识别」悄悄改回本地
            aiText = SettingsRepository.getString(
                context,
                "ai_parse_mode",
                AiConfig.MODE_LOCAL,
            ) == AiConfig.MODE_AI,
            aiVision = AiConfig.isVisionEnabled(context),
            aiCategory = AiConfig.isCategoryEnabled(context),
            aiAccount = AiConfig.isAccountEnabled(context),
            reminderOn = ReminderScheduler.isEnabled(context),
            reminderHour = ReminderScheduler.currentHour(context),
        )
    }
}

/**
 * 引导配置的提交器：一处集中「落盘 → 同步全局状态 → 重排依赖」。
 *
 * 分开写 `setXxx` 会漏掉几类必要的同步：ThemeState 没有持久化 setter、
 * 字号必须走 FontScaleState.setFontSize、提醒必须重排 WorkManager、
 * 币种要写 Room 后同步 CurrencyDisplay。首启与重配共用本函数。
 */
object OnboardingApplier {

    suspend fun apply(context: Context, draft: OnboardingDraft) {
        // 外观
        SettingsRepository.setString(context, "theme_mode", draft.theme)
        ThemeState.themeMode = draft.theme
        FontScaleState.setFontSize(context, draft.fontSizeSp)

        // 个人资料
        ProfileState.ensureInit(context)
        ProfileState.setNickname(context, draft.nickname.trim())
        ProfileState.setAvatarUri(context, draft.avatarUri)

        // 记账口径：使用侧按需直读缓存，无需额外同步
        SettingsRepository.setInt(context, "month_start_day", draft.monthStartDay.coerceIn(1, 28))
        SettingsRepository.setInt(context, "week_start_day", draft.weekStartDay)
        SettingsRepository.setBoolean(context, "is_24_hour", draft.is24Hour)

        // 币种：Room 写入必须在完成前做完，否则放行后首页首帧读到的还是旧符号
        withContext(Dispatchers.IO) {
            // runCatching 的 lambda 不是 suspend，包不住 Room 的挂起写库；用 try/catch
            try {
                LedgerRepository(DbProvider.database.ledgerDao())
                    .setCurrency(
                        LedgerSelection.current.value,
                        Currency.of(draft.currencyCode).code,
                    )
            } catch (t: Throwable) {
                // 账本表异常不该让整个引导完成不了：展示符号仍按草稿同步
                android.util.Log.w("OnboardingApplier", "写入账本币种失败", t)
            }
        }
        CurrencyDisplay.set(draft.currencyCode)

        // AI 配置：AiConfig 无状态，getter 直读缓存，写完即全局生效
        AiConfig.setProvider(context, draft.aiProviderId)
        AiConfig.setApiKey(context, draft.aiKey.trim())
        AiConfig.setApiUrl(context, draft.aiUrl.trim())
        AiConfig.setModel(context, draft.aiModel.trim())
        AiConfig.setEnabled(context, draft.aiEnabled)
        AiConfig.setParseMode(context, if (draft.aiText) AiConfig.MODE_AI else AiConfig.MODE_LOCAL)
        AiConfig.setVisionEnabled(context, draft.aiVision)
        AiConfig.setCategoryEnabled(context, draft.aiCategory)
        AiConfig.setAccountEnabled(context, draft.aiAccount)

        // 提醒：setEnabled 同时写开关 / 时刻并 REPLACE 或取消周期任务
        ReminderScheduler.setEnabled(context, draft.reminderOn, draft.reminderHour.coerceIn(0, 23))
    }
}
