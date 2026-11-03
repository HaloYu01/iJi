package com.iji.app.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「AI 摘要」：把**当期的汇总数据**（不是原始流水）交给模型，换一段中文点评。
 *
 * 为什么不照搬 AutoAccounting 的 32 字段报告：那边有配套的 WebView 报告页（雷达图、
 * 词云、健康分），iji 没有渲染面；抄一套字段只会得到"生成了没人看"的结果。
 * 这里只取能直接展示成一段话的数据：收支、笔数、分类前三。
 *
 * 隐私边界：只发**聚合结果**（分类名 + 金额），不发单笔流水、不发备注里的商户名。
 */
object AiSummaryService {

    data class CategoryLine(val name: String, val cents: Long)

    data class Input(
        /** 周 / 月 / 年 */
        val dimension: String,
        /** 如「2026年10月」 */
        val period: String,
        val expenseCents: Long,
        val incomeCents: Long,
        val billCount: Int,
        /** 上一周期支出：仅在 > 0 时写进 payload（否则模型会拿 0 算环比） */
        val prevExpenseCents: Long = 0L,
        val topCategories: List<CategoryLine> = emptyList(),
    )

    /** 送模型的 JSON（纯函数，可单测）：金额单位元，分类最多 5 条 */
    internal fun payloadJson(input: Input): String = JSONObject().apply {
        put("dimension", input.dimension)
        put("period", input.period)
        put("expense", input.expenseCents / 100.0)
        put("income", input.incomeCents / 100.0)
        put("balance", (input.incomeCents - input.expenseCents) / 100.0)
        put("billCount", input.billCount)
        if (input.prevExpenseCents > 0) put("prevExpense", input.prevExpenseCents / 100.0)
        put("topCategories", JSONArray().apply {
            input.topCategories.take(5).forEach { line ->
                put(JSONObject().apply {
                    put("name", line.name)
                    put("amount", line.cents / 100.0)
                })
            }
        })
    }.toString()

    /**
     * 生成点评；未配置 Key / 请求失败 / 返回空 → null（界面据此给「重试」而不是空卡片）。
     */
    suspend fun summarize(context: Context, input: Input): String? = withContext(Dispatchers.IO) {
        if (!AiConfig.aiReady(context)) return@withContext null
        val raw = AiBookkeepingService.chat(
            context = context,
            systemPrompt = AiConfig.resolvedPrompt(context, AiPromptKind.SUMMARY),
            userText = payloadJson(input),
            // 点评类文案要一点"人味"，0.1 会写成模板腔
            temperature = 0.6,
        ) ?: return@withContext null
        raw
            .replace("```markdown", "")
            .replace("```", "")
            .trim()
            .ifBlank { null }
    }
}
