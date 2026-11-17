package com.iji.app.autorecord

import android.content.Context
import com.iji.app.ai.AiBookkeepingService
import com.iji.app.ai.AiConfig
import com.iji.app.data.db.DbProvider
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.CategoryDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 分类建议：历史（同商户上次分类）→ 用户规则 → 内置关键词 → AI（可选）→ 默认。
 * 候选分类必须存在于用户当前分类表且类型匹配，避免悬空 ID。
 */
object AutoCategorySuggester {

    suspend fun suggest(context: Context, draft: AutoBillDraft): String {
        if (draft.type == TransactionType.TRANSFER) {
            return CategoryCatalog.ofType(TransactionType.TRANSFER).firstOrNull()?.id ?: "transfer"
        }

        // 1. 历史：商户优先，商户为空时用备注（早期数据商户写在 note 里）
        val key = draft.merchant.ifBlank { draft.remark }.trim()
        if (key.length >= 2) {
            val history = runCatching {
                withContext(Dispatchers.IO) {
                    DbProvider.database.transactionDao().latestCategoryByKey(key, draft.type.name)
                }
            }.getOrNull()
            if (history != null && exists(history, draft.type)) return history
        }

        val text = "${draft.merchant} ${draft.remark}".lowercase()

        // 2. 用户规则：优先级高于内置表，用来纠正内置表判错的场景
        AutoCategoryRuleStore.list(context).firstOrNull { rule ->
            rule.keyword.isNotBlank() &&
                text.contains(rule.keyword.lowercase()) &&
                exists(rule.categoryId, draft.type)
        }?.let { return it.categoryId }

        // 3. 内置关键词
        builtinCategory(text, draft.type)?.let { return it }

        // 4. AI：只有用户主动开了 AI 才会发请求，未配置/超时/失败都直接落默认
        aiSuggest(context, draft)?.let { return it }

        // 5. 默认
        return CategoryDefaults.fallbackId(draft.type)
    }

    /**
     * 仅按内置关键词表归类（不查历史、不调 AI）。
     * 抽出来给单元测试用：分类表在用户未初始化时回落到出厂目录，JVM 上也能跑。
     */
    fun builtinCategory(text: String, type: TransactionType): String? {
        val lower = text.lowercase()
        return BUILTIN.firstOrNull { (keyword, id) ->
            lower.contains(keyword) && exists(id, type)
        }?.second
    }

    /** 候选分类必须真实存在于用户当前分类表、且收支类型一致 */
    private fun exists(categoryId: String, type: TransactionType): Boolean =
        CategoryCatalog.ofType(type).any { it.id == categoryId }

    private suspend fun aiSuggest(context: Context, draft: AutoBillDraft): String? {
        if (!AiConfig.isEnabled(context)) return null
        val categories = CategoryCatalog.ofType(draft.type)
        if (categories.isEmpty()) return null
        val answer = withTimeoutOrNull(4000) {
            withContext(Dispatchers.IO) {
                runCatching {
                    AiBookkeepingService.chat(
                        context,
                        "你是账单分类助手。只从给定分类列表中选择一个最贴合的分类名返回，" +
                            "不要解释、不要标点、不要新增分类。",
                        buildString {
                            append("类型：").append(if (draft.type == TransactionType.INCOME) "收入" else "支出")
                            append("\n商户：").append(draft.merchant)
                            append("\n备注：").append(draft.remark)
                            append("\n可选分类：").append(categories.joinToString("、") { it.name })
                        },
                    )
                }.getOrNull()
            }
        }?.trim().orEmpty()
        if (answer.isBlank()) return null
        return categories.firstOrNull { answer.contains(it.name) || it.name.contains(answer) }?.id
    }

    /** 内置关键词（小写匹配）：命中商户名或备注即归类 */
    private val BUILTIN: List<Pair<String, String>> = listOf(
        "美团外卖" to "food-takeout",
        "饿了么" to "food-takeout",
        "外卖" to "food-takeout",
        "麦当劳" to "food",
        "肯德基" to "food",
        "星巴克" to "food-drink",
        "瑞幸" to "food-drink",
        "蜜雪冰城" to "food-drink",
        "海底捞" to "food",
        "食堂" to "food",
        "餐厅" to "food",
        "奶茶" to "food-drink",
        "咖啡" to "food-drink",
        "快餐" to "food",
        "滴滴" to "transport-taxi",
        "打车" to "transport-taxi",
        "地铁" to "transport-subway",
        "公交" to "transport-bus",
        "加油" to "transport-fuel",
        "停车" to "transport-park",
        "12306" to "transport-train",
        "高铁" to "transport-hsr",
        "火车" to "transport-train",
        "机票" to "transport-plane",
        "京东" to "shopping",
        "淘宝" to "shopping",
        "天猫" to "shopping",
        "拼多多" to "shopping",
        "唯品会" to "shopping",
        "苏宁易购" to "shopping",
        "水费" to "home-util",
        "电费" to "home-util",
        "燃气" to "home-util",
        "物业" to "home-property",
        "房租" to "home-rent",
        "宽带" to "home-network",
        "猫眼" to "fun-movie",
        "淘票票" to "fun-movie",
        "电影票" to "fun-movie",
        "医院" to "medical",
        "药店" to "medical-drug",
        "门诊" to "medical-outpatient",
        "挂号" to "medical-outpatient",
        "学费" to "education-tuition",
        "培训" to "education-training",
        "课程" to "education-training",
        "教材" to "education-book",
        "酒店" to "travel-hotel",
        "携程" to "travel",
        "飞猪" to "travel",
        "工资" to "salary",
        "薪水" to "salary",
        "奖金" to "salary-bonus",
        "年终奖" to "salary-bonus",
        "余额宝收益" to "invest-interest",
        "利息" to "invest-interest",
        "分红" to "invest-dividend",
        "微信红包" to "redpacket",
        "支付宝红包" to "redpacket",
    )
}