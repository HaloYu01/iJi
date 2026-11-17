package com.iji.app.autorecord

import android.content.Context
import com.iji.app.data.repository.SettingsRepository

/**
 * 全自动记账**自己的**监听平台偏好。
 *
 * 刻意不复用 `CapturePreferences`：那边的键同时被半自动（截屏来源、倒计时）使用，
 * 全自动的 chip 一旦与它共用一个存储，用户在半自动里改设置就会悄悄改掉全自动的监听范围。
 * 旧键 `auto_capture_trigger_apps` 只做一次性迁移，之后两套设置互不影响。
 */
object AutoListenPrefs {

    /**
     * 监听清单的内存缓存。
     *
     * 读取发生在无障碍事件回调里（主线程，窗口内容变化每秒几十条），
     * 原先每条事件都要 split + map + 判迁移；缓存后稳态只有一次 Set 查找。
     */
    @Volatile
    private var cachedPackages: Set<String>? = null

    private const val KEY = "auto_listen_packages"
    private const val LEGACY_KEY = "auto_capture_trigger_apps"
    private const val KEY_SEEDED = "auto_listen_seeded"

    /**
     * 早期默认只监听这三家，京东/美团/拼多多/抖音因此**永远不解析**——
     * 规则写得再准也不会触发，表现就是「这几个平台识别不了」。
     */
    private const val OLD_SEED = "com.tencent.mm,com.eg.android.AlipayGphone,com.unionpay"

    /**
     * 平台表变更：旧包名 → 新包名。
     *
     * 用户的选择是按包名存的，平台被替换后（美团外卖 → 淘宝）旧包名会留在设置里，
     * 新平台就**永远不在监听范围内**——表现为「换了平台却完全不识别」。
     * 这里把历史选择平滑迁移过去，不覆盖用户的其它选择。
     */
    private val RENAMED = mapOf(
        "com.sankuai.meituan.takeoutnew" to "com.taobao.taobao",
    )

    /** 默认监听全部平台：事件按包名过滤，未打开对应 App 时不会有任何开销 */
    private val DEFAULT: List<String> get() = AutoPlatforms.packages().toList()

    fun packages(context: Context): Set<String> {
        cachedPackages?.let { return it }
        val loaded = loadPackages(context)
        cachedPackages = loaded
        return loaded
    }

    private fun loadPackages(context: Context): Set<String> {
        val raw = SettingsRepository.getString(context, KEY, "")
        // 迁移：仍是最初那三家默认值说明用户没动过，直接扩到全部平台。
        // 用户自己选过的组合不会被覆盖（只有精确等于旧种子才迁移）。
        if (raw == OLD_SEED) {
            val upgraded = AutoPlatforms.packages()
            SettingsRepository.setString(context, KEY, upgraded.joinToString(","))
            return upgraded
        }
        if (raw.isNotBlank()) {
            val stored = parse(raw)
            val migrated = stored.map { RENAMED[it] ?: it }.toSet()
            // 迁移结果与存量不同才回写，避免每次读取都写一次磁盘
            if (migrated != stored) {
                SettingsRepository.setString(context, KEY, migrated.joinToString(","))
            }
            return migrated
        }
        val legacy = SettingsRepository.getString(context, LEGACY_KEY, "")
        val initial = if (legacy.isNotBlank()) parse(legacy) else DEFAULT.toSet()
        // 只在第一次种下默认值：之后用户清空到 0 家也算他的明确选择，不能又被默认值填回来
        if (!SettingsRepository.getBoolean(context, KEY_SEEDED, false)) {
            SettingsRepository.setString(context, KEY, initial.joinToString(","))
            SettingsRepository.setBoolean(context, KEY_SEEDED, true)
        }
        return initial
    }

    fun setPackages(context: Context, packages: Set<String>) {
        cachedPackages = packages
        SettingsRepository.setString(context, KEY, packages.joinToString(","))
        SettingsRepository.setBoolean(context, KEY_SEEDED, true)
    }

    /** 8 家里最常用的三家：chip 上的「常用」快捷 */
    fun common(): Set<String> = DEFAULT.toSet()

    fun all(): Set<String> = AutoPlatforms.packages()

    private fun parse(raw: String): Set<String> = raw
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}
