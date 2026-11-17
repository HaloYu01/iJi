package com.iji.app.accessibility

import android.content.Context
import com.iji.app.data.repository.SettingsRepository

/**
 * 自动记账偏好（对照 AutoAccounting「记账设置」里能真实落地的那几项）。
 *
 * 只放**有落点**的设置：
 * - 账本：识屏悬浮窗与通知确认落到哪本账（空 = 跟随当前账本）；
 * - 备注格式：通知草稿的备注模板，支持 `{商户}` / `{来源}`；
 * - 去重窗口：同一条通知重复推送时，多久内视为同一笔。
 *
 * 手续费、债务、自动创建分类、资产映射等在 iji 没有对应数据模型，不做假开关。
 */
object AutoBookkeepingPrefs {

    const val KEY_LEDGER = "auto_bookkeeping_ledger"
    const val KEY_NOTE_TEMPLATE = "auto_bookkeeping_note_template"
    const val KEY_DEDUP_SECONDS = "auto_bookkeeping_dedup_seconds"
    /** 应用内逻辑总闸：关掉后无障碍服务不再发「待记账」提示，系统授权保持不变 */
    const val KEY_MASTER_ENABLED = "auto_bookkeeping_enabled"

    const val DEFAULT_NOTE_TEMPLATE = "{商户}"
    const val DEFAULT_DEDUP_SECONDS = 60

    /** 默认开：与既有行为一致，升级用户不会突然收不到提示 */
    fun masterEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_MASTER_ENABLED, true)

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        SettingsRepository.setBoolean(context, KEY_MASTER_ENABLED, enabled)
    }

    /** 可选窗口（秒）：太短会重复入账，太长会把两笔真实同额消费合并 */
    val DEDUP_OPTIONS = listOf(30, 60, 120, 300)

    fun ledger(context: Context): String =
        SettingsRepository.getString(context, KEY_LEDGER, "")

    fun setLedger(context: Context, name: String) {
        SettingsRepository.setString(context, KEY_LEDGER, name.trim())
    }

    fun noteTemplate(context: Context): String =
        SettingsRepository.getString(context, KEY_NOTE_TEMPLATE, DEFAULT_NOTE_TEMPLATE)

    fun setNoteTemplate(context: Context, template: String) {
        SettingsRepository.setString(context, KEY_NOTE_TEMPLATE, template.trim())
    }

    fun dedupSeconds(context: Context): Int =
        SettingsRepository.getInt(context, KEY_DEDUP_SECONDS, DEFAULT_DEDUP_SECONDS)

    fun setDedupSeconds(context: Context, seconds: Int) {
        SettingsRepository.setInt(context, KEY_DEDUP_SECONDS, seconds)
    }

    /** 设置里选了账本就用它，否则跟随用户当前账本 */
    fun effectiveLedger(setting: String, current: String): String =
        setting.ifBlank { current }

    /**
     * 按模板生成备注。
     *
     * 未知占位符原样保留（用户写错时能看出是模板没生效，而不是被静默吞掉）；
     * 模板为空或结果为空时回退商户名，避免账单没有备注。
     */
    fun buildNote(template: String, merchant: String, source: String): String =
        template
            .replace("{商户}", merchant)
            .replace("{来源}", source)
            .trim()
            .ifBlank { merchant }

    fun dedupWindowMs(seconds: Int): Long = seconds.coerceIn(10, 600) * 1000L

    // ── 重复识别策略（全自动）────────────────────────────────────────────
    // 三档与钱迹一致：拦掉 / 照常确认但标注 / 不检测。
    // 默认「标注」：宁可让用户多点一下，也不误杀同一天两笔同额消费。

    const val KEY_DUP_POLICY = "auto_bill_dup_policy"
    const val DUP_CONFIRM = "confirm"
    const val DUP_BLOCK = "block"
    const val DUP_OFF = "off"

    val DUP_POLICY_VALUES = listOf(DUP_CONFIRM, DUP_BLOCK, DUP_OFF)

    fun dupPolicy(context: Context): String =
        SettingsRepository.getString(context, KEY_DUP_POLICY, DUP_CONFIRM)

    fun setDupPolicy(context: Context, policy: String) {
        SettingsRepository.setString(context, KEY_DUP_POLICY, policy)
    }

    fun dupPolicyLabel(policy: String): String = when (policy) {
        DUP_BLOCK -> "直接拦掉"
        DUP_OFF -> "不检测"
        else -> "确认时标注"
    }
}
