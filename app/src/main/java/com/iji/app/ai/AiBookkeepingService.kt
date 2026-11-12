package com.iji.app.ai

import android.content.Context
import com.iji.app.accessibility.PaymentNotificationParser
import com.iji.app.data.db.DbProvider
import com.iji.app.data.repository.CategoryCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * AI 记账服务：调用大模型 API 解析自然语言账单。
 * 默认使用智谱 GLM-4-Flash（免费），用户可在设置中配置自己的 API Key。
 *
 * 提示词全部来自 [AiPromptKind]（可在「提示词与规则」页改，留空 = 默认）；
 * 本地识别规则来自 [LocalRuleStore]（可在同一页增删改）。
 */
object AiBookkeepingService {

    data class BillResult(
        val type: String, // "expense" / "income" / "transfer"
        val categoryHint: String, // 分类提示，如 "餐饮"
        val amountCents: Long, // 金额（分）
        val note: String, // 备注
        val accountHint: String, // 账户提示
        val tags: List<String>,
        /** 识别来源：通知规则 / AI / 本地规则 / 本地识别 / 云端识别。展示给用户判断可信度 */
        val source: String = "",
    )

    /** 识别来源标记：随 [BillResult] 一起返回，界面标题直接展示，用户有权知道这条结果从哪来 */
    const val SOURCE_NOTIFICATION = "通知规则"
    const val SOURCE_AI = "AI"
    const val SOURCE_LOCAL_RULE = "本地规则"
    const val SOURCE_LOCAL_OCR = "本地识别"
    const val SOURCE_CLOUD_VISION = "云端识别"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 文本里的金额：`35块` / `35元` / `¥35` / `35.50` */
    private val AMOUNT_WITH_UNIT = Regex("""(\d+(?:\.\d{1,2})?)\s*[块元]""")
    private val AMOUNT_WITH_SYMBOL = Regex("""[¥￥]\s*(\d+(?:\.\d{1,2})?)""")

    /**
     * 统一请求助手：组装 chat/completions 请求 → 同步执行 → 返回 content 原文；失败返回 null
     */
    private fun postChat(url: String, apiKey: String, body: JSONObject): String? {
        try {
            val requestBody = body.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody)
            return json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 一次「系统提示词 + 用户内容」的纯文本补全；未配置 Key 或请求失败返回 null。
     *
     * 抽出来给 [AiSummaryService]（AI 小结）与分类 / 账户二次映射共用：
     * 三处请求体只差 messages 内容，没必要各写一份 OkHttp。
     */
    fun chat(context: Context, systemPrompt: String, userText: String, temperature: Double = 0.1): String? {
        // 总开关统一闸门：这里收口所有共用 chat 的 AI 调用（小结 / 分类映射 / 账户映射），
        // 关掉「AI 识别」后即使 Key 还在也不发任何请求。管线上层已有 useAiFor 挡一道，这是兜底。
        if (!AiConfig.isEnabled(context)) return null
        val apiKey = AiConfig.getApiKey(context)
        if (apiKey.isBlank()) return null
        val jsonBody = JSONObject().apply {
            put("model", AiConfig.resolvedModel(context))
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userText)
                })
            })
            put("temperature", temperature)
        }
        return postChat(AiConfig.resolvedUrl(context), apiKey, jsonBody)
    }

    /**
     * 从 JSON 解析结果构建 BillResult；提取原文作为备注
     */
    private fun billFromJson(
        content: String,
        fallbackNote: String,
        keepCategory: Boolean = true,
        source: String = SOURCE_AI,
    ): BillResult? {
        return try {
            // 去掉可能的 markdown 代码块标记
            val cleanJson = content
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val result = JSONObject(cleanJson)
            val amountYuan = result.optDouble("amount", 0.0)
            BillResult(
                type = result.optString("type", "expense"),
                // 关了「AI 识别分类」就落「其他」，交给用户在记账面板里选，不偷偷用模型猜的分类
                categoryHint = categoryHintFor(keepCategory, result.optString("category", "其他")),
                // 用 round 而不是直接截断：19.90 在 double 里是 1989.9999…，截断会少 1 分
                amountCents = (amountYuan * 100).roundToLong(),
                note = result.optString("note", fallbackNote),
                accountHint = result.optString("account", ""),
                tags = result.optJSONArray("tags")?.let { arr ->
                    List(arr.length()) { arr.getString(it) }
                } ?: emptyList(),
                source = source,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 解析自然语言为账单。
     *
     * 文本解析由「文本识别用 AI」开关决定（[useAi] 传 null 时按配置，配置默认本地）：
     * 1. **开 = 调用 AI**：直接把文本交给大模型，不再先跑本地通知规则；
     * 2. **AI 失效 = 降级本地**：没配 Key / 关了总开关 / 请求失败 / 返回空或识别不出金额，
     *    都回落到「通知 / 短信规则（[PaymentNotificationParser]）→ 本地识别规则
     *    （[LocalRuleEngine]，离线、零成本、用户可编辑）」；
     * 3. **关 = 纯本地**：不联网，直接走第 2 步的本地链路。
     *
     * 出结果后再走一次 [enrich]：分类 / 账户对不上用户真实数据时，才补一次
     * 「分类识别」「账户识别」请求（开关在 AI 记账页的「AI 功能」里）。
     *
     * @param text 用户输入，如 "今天午饭花了35块"，也可以直接粘贴支付通知原文
     * @param useAi 显式指定是否联网；传 null（默认）按用户配置 [AiConfig.parseMode]。
     *   选过 AI 但没配 Key（或关了总开关）时强制本地；开 AI 后调用失败同样降级本地。
     *   语音 / 文本 / 图片三个入口都不传，行为只由配置决定，不靠各页面记得传对布尔值
     */
    suspend fun parseBill(text: String, context: Context, useAi: Boolean? = null): BillResult? =
        withContext(Dispatchers.IO) {
            val raw = parseBillByPipeline(text, context, useAi) ?: return@withContext null
            // 金额都没有就别再花钱补两次映射请求了
            if (raw.amountCents <= 0) return@withContext raw
            enrich(raw, context)
        }

    /** 文本解析管线本身（不含二次映射）：AI 优先，任何形式的失效都降级本地 */
    private fun parseBillByPipeline(
        text: String,
        context: Context,
        useAi: Boolean?,
    ): BillResult? {
        val allowAi = useAiFor(useAi, AiConfig.parseMode(context), AiConfig.aiReady(context))
        val byAi = if (allowAi) requestAi(text, context) else null
        // AI 结果有效就不再跑本地；失效（没开 AI / 没 Key / 请求失败 / 空结果 / 没金额）才降级
        val byLocal = if (byAi != null && byAi.amountCents > 0) null else localTextParse(text, context)
        return preferTextResult(byAi, byLocal)
    }

    /** 文本解析的最终取舍：AI 有可用金额就用 AI，否则一律用本地结果 */
    fun preferTextResult(ai: BillResult?, local: BillResult?): BillResult? =
        ai?.takeIf { it.amountCents > 0 } ?: local

    /** 本地兜底链路：通知 / 短信模板 → 本地识别规则。全程离线、零成本 */
    private fun localTextParse(text: String, context: Context): BillResult? {
        val byRule = PaymentNotificationParser.parse(null, text)
        if (byRule != null && byRule.amountCents > 0) {
            // 备注取商户名：整条通知原文塞进账单备注又长又没信息量；没有商户才回退原文
            return billResultOf(byRule, note = byRule.merchant.ifBlank { text.take(40) })
        }
        return localRuleParse(text, LocalRuleStore.rules(context))
    }

    /**
     * 这一次解析是否允许联网。
     *
     * @param requested 调用方的意愿；null（默认）= 完全听配置，false = 明确要求本地，
     *   true = 想用 AI 但**仍受配置约束**（不能绕过下面两条）
     * @param mode [AiConfig.parseMode] 的实际生效值（已含「未配 AI 回落本地」）
     * @param aiReady [AiConfig.aiReady]
     *
     * 两条硬约束，顺序即优先级：
     * 1. AI 不可用（无 Key / 关了总开关）→ 只能本地。任何页面写死 `useAi = true`
     *    也发不出去，这是「未配 AI 强制本地」的落点；
     * 2. 用户没选 AI（默认 [AiConfig.MODE_LOCAL]）→ 只能本地。
     */
    fun useAiFor(requested: Boolean?, mode: String, aiReady: Boolean): Boolean {
        if (!aiReady) return false
        if (requested == false) return false
        return mode == AiConfig.MODE_AI
    }

    /** 规则解析结果 → 界面用的 [BillResult]；抽成纯函数便于单测 */
    fun billResultOf(parsed: PaymentNotificationParser.Parsed, note: String): BillResult =
        BillResult(
            type = parsed.type,
            // 通知/短信里没有分类信息，不猜；留给用户在确认时改
            categoryHint = "其他",
            amountCents = parsed.amountCents,
            note = note,
            accountHint = parsed.accountHint,
            tags = emptyList(),
            source = SOURCE_NOTIFICATION,
        )

    /** 走一次大模型；失败返回 null，由调用方回落到本地兜底 */
    private fun requestAi(text: String, context: Context): BillResult? {
        // 提示词可在「提示词与规则」里改；留空则用默认
        val content = chat(context, AiConfig.resolvedPrompt(context, AiPromptKind.BILL_TEXT), text)
            ?: return null
        return billFromJson(content, text, keepCategory = AiConfig.isCategoryEnabled(context))
    }

    /**
     * 从图片识别账单（视觉模型）。图片链路不看 [AiConfig.parseMode]（那是文本开关）；
     * AI 不可用或关了「图片识别用 AI」时返回 null，由调用方回落本地 OCR。
     */
    suspend fun parseBillFromImage(
        imageBase64: String,
        context: Context,
    ): BillResult? = withContext(Dispatchers.IO) {
        if (!AiConfig.visionUsesAi(context)) return@withContext null
        val apiKey = AiConfig.getApiKey(context)
        val apiUrl = AiConfig.resolvedUrl(context)

        // 提示词可在「提示词与规则」里改；留空则用默认
        val systemPrompt = AiConfig.resolvedPrompt(context, AiPromptKind.BILL_IMAGE)

        val jsonBody = JSONObject().apply {
            val model = AiConfig.resolvedModel(context) // 使用配置的模型，而不是硬编码的 glm-4v-flash
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$imageBase64")
                            })
                        })
                    })
                })
            })
            put("temperature", 0.1)
        }

        val content = postChat(apiUrl, apiKey, jsonBody) ?: return@withContext null
        val bill = billFromJson(
            content,
            "小票识别",
            keepCategory = AiConfig.isCategoryEnabled(context),
            source = SOURCE_CLOUD_VISION,
        ) ?: return@withContext null
        enrich(bill, context)
    }

    // ── 二次映射：分类 / 账户对齐用户真实数据 ──

    /**
     * 把模型给的分类 / 账户对齐到用户**真实存在**的分类与账户名。
     *
     * 为什么需要：模型只知道通用词（「餐饮」「微信」），而 iji 的分类和账户是用户可改的数据
     * （改名、删除、自建）。对不上时旧行为是把原始字符串塞进 `categoryHint`，记账面板再提示
     * 「未识别分类」——全靠用户手动选。这里多花一次（只在**对不上时**才发）请求，
     * 让模型从真实列表里挑，直接命中。
     *
     * 只在「AI 识别」生效时执行；两个开关分别控制，任一关闭就跳过对应阶段。
     */
    private suspend fun enrich(bill: BillResult, context: Context): BillResult {
        if (!useAiFor(null, AiConfig.parseMode(context), AiConfig.aiReady(context))) return bill
        var out = bill

        if (AiConfig.isCategoryEnabled(context)) {
            val names = categoryNamesOf(bill.type)
            if (names.isNotEmpty() && out.categoryHint.trim() !in names) {
                mapCategory(out, names, context)?.let { out = out.copy(categoryHint = it) }
            }
        }

        if (AiConfig.isAccountEnabled(context)) {
            val accounts = accountNames(context)
            if (accounts.isNotEmpty() && out.accountHint.trim() !in accounts) {
                mapAccount(out, accounts, context)?.let { out = out.copy(accountHint = it) }
            }
        }
        return out
    }

    /** 该账单类型对应的全部分类名（收入 / 支出分开，转账用支出侧） */
    private fun categoryNamesOf(type: String): List<String> {
        val list = if (type == "income") CategoryCatalog.incomes else CategoryCatalog.expenses
        return list.map { it.name }.distinct()
    }

    private suspend fun accountNames(context: Context): List<String> = runCatching {
        DbProvider.database.accountDao().getAllOnce().map { it.name }.distinct()
    }.getOrDefault(emptyList())

    private fun mapCategory(bill: BillResult, names: List<String>, context: Context): String? {
        val payload = JSONObject().apply {
            put("type", bill.type)
            put("amount", yuanOf(bill.amountCents))
            put("note", bill.note)
            put("account", bill.accountHint)
            put("categories", names.joinToString("，"))
        }
        val content = chat(context, AiConfig.resolvedPrompt(context, AiPromptKind.CATEGORY), payload.toString())
            ?: return null
        return pickFromList(content, names)
    }

    private fun mapAccount(bill: BillResult, accounts: List<String>, context: Context): String? {
        val payload = JSONObject().apply {
            put("type", bill.type)
            put("hint", bill.accountHint)
            put("note", bill.note)
            put("accounts", accounts.joinToString("，"))
        }
        val content = chat(context, AiConfig.resolvedPrompt(context, AiPromptKind.ACCOUNT), payload.toString())
            ?: return null
        return pickFromList(content, accounts)
    }

    // ── 本地识别（离线兜底，规则可编辑） ──

    /**
     * 本地规则解析：金额靠正则，分类 / 类型 / 账户靠 [LocalRuleEngine]。
     *
     * 与旧版硬编码 `when(regex)` 的区别：分类词表从 [LocalRuleStore] 来（用户可增删改），
     * 命中口径统一在引擎里（命中词数 → 最长词 → 用户规则优先），这里只负责拼 [BillResult]。
     *
     * 规则**以参数传入**而不是内部去读存储：这样「一句话 → 一笔账单」这条链路是纯函数，
     * 能在 JVM 单测里逐条锁住（分类、收支方向、金额精度），不用起模拟器。
     */
    internal fun localRuleParse(text: String, rules: List<LocalRule>): BillResult {
        // 金额喂给规则引擎做「金额范围」条件（设了条件的规则在没金额时不命中）；没解析出按 0 记
        val amountYuan = extractAmount(text)
        val matched = LocalRuleEngine.match(text, rules, amountYuan)
        val detectedType = LocalRuleEngine.detectType(text)
        val type = matched?.rule?.type?.takeIf { it != LocalRule.TYPE_ANY } ?: detectedType

        return BillResult(
            type = type,
            categoryHint = matched?.rule?.category ?: "其他",
            // 用 round 而不是直接截断：19.90 在 double 里是 1989.9999…，截断会少 1 分
            amountCents = ((amountYuan ?: 0.0) * 100).roundToLong(),
            note = text,
            accountHint = matched?.rule?.account.orEmpty(),
            tags = emptyList(),
            source = SOURCE_LOCAL_RULE,
        )
    }

    /** 文本 → 金额（元）；认不出返回 null（与「金额为 0」区分开：0 元也是合法金额） */
    internal fun extractAmount(text: String): Double? {
        val raw = AMOUNT_WITH_UNIT.find(text)?.groupValues?.get(1)
            ?: AMOUNT_WITH_SYMBOL.find(text)?.groupValues?.get(1)
            ?: return null
        return raw.toDoubleOrNull()
    }

    /** 金额分 → 元（JSON 用，固定两位小数） */
    internal fun yuanOf(cents: Long): String = String.format(Locale.US, "%.2f", cents / 100.0)

    /**
     * 从模型的自由文本里挑出列表中的一项（分类 / 账户映射共用）。
     *
     * 模型偶尔会带引号、句号或前缀（「分类：餐饮」），所以先取第一行、去掉包裹的引号与标点，
     * 再依次尝试完全相等 → 包含候选名。**只接受列表里已有的名字**，绝不返回模型自造的词。
     */
    internal fun pickFromList(content: String, candidates: List<String>): String? {
        val line = content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val cleaned = line
            .removeSurrounding("\"")
            .removeSurrounding("`")
            .removeSuffix("。")
            .removeSuffix(".")
            .trim()
        candidates.firstOrNull { it == cleaned }?.let { return it }
        // 「分类：餐饮」这种带前缀的写法
        val bare = cleaned.substringAfter("：").substringAfter(":").trim()
        candidates.firstOrNull { it == bare }?.let { return it }
        if (bare.isEmpty()) return null
        // 只接受「候选名出现在回答里」或「回答是候选名的一部分」，不接受模型自己造的词
        return candidates.firstOrNull { bare.contains(it) }
            ?: candidates.firstOrNull { it.contains(bare) }
    }
}

/**
 * 「AI 识别分类」关闭时统一落「其他」，开启时才用模型给的分类。
 *
 * 抽成顶层纯函数是为了让这个开关的语义可测：用户关掉后，账单里**不该出现**
 * 模型猜的分类（比如把「瑞幸」猜成「购物」）。
 */
internal fun categoryHintFor(keepCategory: Boolean, raw: String): String =
    if (keepCategory) raw.ifBlank { "其他" } else "其他"
