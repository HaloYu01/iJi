package com.iji.app.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 小票 OCR **本地**引擎（ML Kit 中文文字识别）。
 *
 * 离线、图片不出设备：AI 不可用或「图片识别用 AI」关着时只跑这里，开关开着且模型失败也回落到这里。
 * 识别+解析均为纯函数式流程，不依赖任何 Android 组件（除 Bitmap），便于单测。
 */
object LocalOcrEngine {

    /** 识别器全局复用（内部含原生模型，重复创建会浪费内存） */
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /** 把小票图片识别为文本行；失败返回 null（调用方决定是否回落云端） */
    suspend fun recognizeLines(bmp: Bitmap): List<String>? = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { result ->
                if (cont.isActive) {
                    cont.resume(
                        result.textBlocks
                            .flatMap { block -> block.lines }
                            .map { it.text.trim() }
                            .filter { it.isNotBlank() },
                    )
                }
            }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }

    /**
     * 识别并解析成账单；**解析不出金额时返回 null**，交由调用方回落云端。
     */
    suspend fun recognize(bmp: Bitmap): AiBookkeepingService.BillResult? {
        val lines = recognizeLines(bmp) ?: return null
        return parse(lines)
    }

    // ── 文本解析（纯逻辑，可单测）──

    /** 金额行的关键词：命中这些词的行优先采信（小票上总计/实付才是真正金额） */
    private val TOTAL_KEYWORDS = listOf(
        "合计", "总计", "总额", "实付", "实收", "应付", "应收",
        "付款金额", "订单金额", "消费金额", "交易金额", "小计", "金额",
    )

    private val RE_CURRENCY = Regex("""[¥￥]\s*(\d+(?:\.\d{1,2})?)""")
    private val RE_YUAN = Regex("""(\d+(?:\.\d{1,2})?)\s*元""")
    /** 两位小数：小票金额的典型形态（日期 2026-01-01、长数字订单号都不会命中） */
    private val RE_DECIMAL2 = Regex("""(?<![\d.])(\d{1,6}\.\d{2})(?![\d])""")

    /** 单笔金额上限（100 万元），超出视为误识别（订单号 / 卡号被当成金额） */
    private const val MAX_AMOUNT_CENTS = 100_000_000L

    private data class AmountCandidate(val cents: Long, val weight: Int)

    private fun parse(lines: List<String>): AiBookkeepingService.BillResult? {
        val amount = pickAmount(lines) ?: return null

        val joined = lines.joinToString(" ")
        val incomeCat = INCOME_KEYWORDS.entries.firstOrNull { (_, words) ->
            words.any { joined.contains(it) }
        }?.key
        val expenseCat = EXPENSE_KEYWORDS.entries.firstOrNull { (_, words) ->
            words.any { joined.contains(it) }
        }?.key

        // 先走「提示词与规则」页里可编辑的本地规则：用户改了分类词表，本机 OCR 也立刻跟着变。
        // 没命中再退回下面两张小票专用词表（它们比通用规则更细，如「米线/烘焙/影城」）。
        // OCR 金额（分）折成元喂给引擎的「金额范围」条件；认不出金额时规则仍可命中，
        // 只有设了金额条件的规则会被拦下（语义见 LocalRule.matchesAmount）。
        val matched = LocalRuleEngine.match(joined, LocalRuleStore.rules(), amount / 100.0)
        val ruleType = matched?.rule?.type?.takeIf { it != LocalRule.TYPE_ANY }

        val type = ruleType ?: if (incomeCat != null) "income" else "expense"
        val category = matched?.rule?.category ?: incomeCat ?: expenseCat ?: "其他"

        return AiBookkeepingService.BillResult(
            type = type,
            categoryHint = category,
            amountCents = amount,
            note = pickMerchant(lines),
            accountHint = matched?.rule?.account?.takeIf { it.isNotBlank() } ?: pickAccount(joined),
            tags = emptyList(),
            source = AiBookkeepingService.SOURCE_LOCAL_OCR,
        )
    }

    /**
     * 选金额：先按「权重 + 数值」择优。
     *
     * 权重规则：带货币符号或「元」= 3 分；仅两位小数 = 1 分；所在行命中
     * 总计类关键词再 +2 分。这样「实付 ¥35.00」能压过「数量 2.00」与「单价 18.00」。
     */
    private fun pickAmount(lines: List<String>): Long? {
        var best: AmountCandidate? = null
        lines.forEach { line ->
            val candidates = mutableListOf<AmountCandidate>()
            // toCandidate 可能返回 null（金额非法），用 let 过滤；不能写成 `candidates += toCandidate(...)`，
            // 否则会退化成 plusAssign 的不可用重载而报 val 不能重新赋值
            RE_CURRENCY.findAll(line).forEach { r ->
                toCandidate(r.groupValues[1], 3)?.let { candidates += it }
            }
            RE_YUAN.findAll(line).forEach { r ->
                toCandidate(r.groupValues[1], 3)?.let { candidates += it }
            }
            RE_DECIMAL2.findAll(line).forEach { r ->
                toCandidate(r.groupValues[1], 1)?.let { candidates += it }
            }
            val bonus = if (TOTAL_KEYWORDS.any { line.contains(it) }) 2 else 0
            candidates.forEach { c ->
                val scored = c.copy(weight = c.weight + bonus)
                if (best == null || scored.weight > best!!.weight ||
                    (scored.weight == best!!.weight && scored.cents > best!!.cents)
                ) {
                    best = scored
                }
            }
        }
        return best?.cents
    }

    private fun toCandidate(raw: String, weight: Int): AmountCandidate? {
        val cents = runCatching { (raw.toDouble() * 100).toLong() }.getOrNull() ?: return null
        if (cents <= 0L || cents > MAX_AMOUNT_CENTS) return null
        return AmountCandidate(cents, weight)
    }

    /** 商家名：取第一个「像名称」的短行（纯数字/日期/总计行排除） */
    private fun pickMerchant(lines: List<String>): String {
        lines.forEach { line ->
            val cleaned = line.trim()
            if (cleaned.length in 2..20 &&
                !TOTAL_KEYWORDS.any { cleaned.contains(it) } &&
                !Regex("""^[\d\s.¥￥:：/-]+$""").matches(cleaned) &&
                !cleaned.contains("日期") && !cleaned.contains("时间") &&
                !cleaned.contains("单号") && !cleaned.contains("订单")
            ) {
                return cleaned
            }
        }
        return "小票识别"
    }

    private fun pickAccount(text: String): String = when {
        text.contains("支付宝") -> "支付宝"
        text.contains("微信") -> "微信"
        text.contains("云闪付") -> "云闪付"
        text.contains("现金") -> "现金"
        text.contains("银行卡") || text.contains("储蓄卡") -> "银行卡"
        else -> ""
    }

    /** 支出分类关键词 → 内置分类名（须能在 CategoryCatalog 中按名字解析到 id） */
    private val EXPENSE_KEYWORDS = linkedMapOf(
        "餐饮" to listOf(
            "餐", "饭", "外卖", "食堂", "餐厅", "小吃", "米线", "奶茶", "咖啡",
            "星巴克", "肯德基", "麦当劳", "火锅", "烧烤", "快餐", "面包", "蛋糕",
            "零食", "水果", "烘焙",
        ),
        "交通" to listOf("地铁", "公交", "打车", "滴滴", "出租", "加油", "停车", "高铁", "火车", "机票", "航空", "通行费"),
        "购物" to listOf("超市", "商场", "淘宝", "京东", "拼多多", "服饰", "鞋", "包", "美妆", "数码", "手机", "家电"),
        "居住" to listOf("房租", "租金", "物业", "水费", "电费", "燃气", "宽带", "维修", "家政", "中介"),
        "娱乐" to listOf("电影", "影城", "游戏", "ktv", "演出", "运动", "健身", "游泳"),
        "医疗" to listOf("医院", "门诊", "药品", "药店", "挂号", "体检", "住院"),
        "教育" to listOf("学费", "培训", "书本", "书店", "课程", "网课"),
        "旅行" to listOf("酒店", "民宿", "旅游", "景点", "门票"),
        "人情" to listOf("红包", "礼金", "随礼", "请客", "礼物", "份子钱"),
    )

    /** 收入分类关键词（顺序敏感：更具体的放前面，避免「退款」被当成支出） */
    private val INCOME_KEYWORDS = linkedMapOf(
        "工资" to listOf("工资", "薪资", "薪水", "代发"),
        "奖金" to listOf("奖金", "年终奖", "绩效"),
        "报销" to listOf("报销", "退款", "退货", "退钱"),
        "红包" to listOf("红包", "收款", "已收款", "转账给你"),
        "理财" to listOf("利息", "分红", "收益"),
        "借入" to listOf("借入", "借钱给你", "收到转账"),
    )
}