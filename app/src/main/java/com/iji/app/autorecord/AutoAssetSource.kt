package com.iji.app.autorecord

import android.content.Context
import com.iji.app.data.db.DbProvider
import com.iji.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 账户匹配所需的两个数据源：本机资产列表与用户映射表。
 *
 * 全自动链路**不读半自动的解析器**，资产也只取「名字 / 备注 / 子标题」这些匹配必需字段，
 * 不持有 Account 实体，避免两条链路在模型上耦合。
 */
object AutoAssetSource {

    private const val KEY_MAPPINGS = "auto_asset_mappings"

    /** 从数据库读本机资产；读不到时返回空列表（匹配器会退化为「不猜」） */
    suspend fun loadAssets(): List<AutoAsset> = withContext(Dispatchers.IO) {
        runCatching {
            DbProvider.database.accountDao().getAllOnce().map { entity ->
                AutoAsset(
                    name = entity.name,
                    subName = entity.subName,
                    note = entity.category,
                    iconKey = entity.iconKey,
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 用户映射：每行 `匹配规则|平台|\u8d44\u4ea7\u540d`，平台为空表示全平台生效 */
    fun loadMappings(context: Context): List<AutoAssetMapping> =
        SettingsRepository.getString(context, KEY_MAPPINGS, "")
            .split('\n')
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 3) return@mapNotNull null
                val match = parts[0].trim()
                val platform = parts[1].trim()
                val asset = parts[2].trim()
                if (match.isEmpty() || asset.isEmpty()) return@mapNotNull null
                AutoAssetMapping(match = match, platform = platform, assetName = asset)
            }

    fun saveMappings(context: Context, mappings: List<AutoAssetMapping>) {
        SettingsRepository.setString(
            context,
            KEY_MAPPINGS,
            mappings.filter { it.enabled && it.match.isNotBlank() && it.assetName.isNotBlank() }
                .joinToString("\n") { "${it.match}|${it.platform}|${it.assetName}" },
        )
    }

    fun addMapping(context: Context, mapping: AutoAssetMapping) {
        val existing = loadMappings(context)
            .filterNot { it.match == mapping.match && it.platform == mapping.platform }
        saveMappings(context, existing + mapping)
    }
}
