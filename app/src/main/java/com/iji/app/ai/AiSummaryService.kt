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
        /** 支出笔数（笔均的分母）；0 = 不写笔均 */
        val expenseCount: Int = 0,
        /** 周期天数（日均的分母）；0 = 不写日均 */
        val daysInPeriod: Int = 0,
        /** 上一周期支出：仅在 > 0 时写进 payload（否则模型会拿 0 算环比） */
        val prevExpenseCents: Long = 0L,
        /** 上一周期收入：仅在 > 0 时写环比 */
        val prevIncomeCents: Long = 0L,
        /** 预算有效额度与已用（分）：成对给才写进 payload；仅「月维度 + 浏览当月」时有意义 */
        val budgetCents: Long? = null,
        val budgetSpentCents: Long? = null,
        val topCategories: List<CategoryLine> = emptyList(),
    )

    /** 送模型的 JSON：金额单位元，分类最多 5 条。
     *
     * 环比 / 占比 / 日均 / 笔均这类衍生指标一律**本地预计算**后发出去：
     * 模型自己做除法和百分比很容易算错，而点评里一个错数就是一次「假参考」；
     * 预算算好再给，模型只剩引用和表达。字段集合仍锁在隐私边界内（无单笔流水、无备注）。
     */
    internal fun payloadJson(input: Input): String = JSONObject().apply {
        put("dimension", input.dimension)
        put("period", input.period)
        put("expense", input.expenseCents / 100.0)
        put("income", input.incomeCents / 100.0)
        put("balance", (input.incomeCents - input.expenseCents) / 100.0)
        put("billCount", input.billCount)
        if (input.expenseCount > 0) put("expensePerBill", round1(input.expenseCents / 100.0 / input.expenseCount))
        if (input.daysInPeriod > 0) put("dailyAvgExpense", round1(input.expenseCents / 100.0 / input.daysInPeriod))
        if (input.prevExpenseCents > 0) {
            put("prevExpense", input.prevExpenseCents / 100.0)
            put("expenseChangePct", round1((input.expenseCents - input.prevExpenseCents) * 100.0 / input.prevExpenseCents))
        }
        if (input.prevIncomeCents > 0) {
            put("incomeChangePct", round1((input.incomeCents - input.prevIncomeCents) * 100.0 / input.prevIncomeCents))
        }
        val budget = input.budgetCents?.takeIf { it > 0 }
        val spent = input.budgetSpentCents
        if (budget != null && spent != null) {
            put("budget", budget / 100.0)
            put("budgetUsedPct", round1(spent * 100.0 / budget))
        }
        put("topCategories", JSONArray().apply {
            input.topCategories.take(5).forEach { line ->
                put(JSONObject().apply {
                    put("name", line.name)
                    put("amount", line.cents / 100.0)
                    // 占比以「当期总支出」为分母（与 expense 同口径）；分母为 0（极端退款月）不写，免得出现负 / 无穷占比
                    if (input.expenseCents > 0) put("share", round1(line.cents * 100.0 / input.expenseCents))
                })
            }
        })
    }.toString()

    /** 衍生指标统一保留 1 位小数：给模型引用的事实，比 0.1 更细的精度没有意义 */
    private fun round1(v: Double): Double = kotlin.math.round(v * 10.0) / 10.0

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
