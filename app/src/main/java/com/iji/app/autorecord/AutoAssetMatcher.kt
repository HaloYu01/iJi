package com.iji.app.autorecord

/**
 * 全自动记账的账户匹配层（**与半自动完全独立**）。
 *
 * 页面上的「支付方式 / 付款方式」写的是渠道名称：`零钱`、`余额宝`、`花呗`、
 * `招商银行储蓄卡(1234)`、`微信支付`。它们**不是**本机账户名，直接当账户写库
 * 会让余额联动静默失效（`WHERE name = ?` 匹配不上）。
 *
 * 这里照钱迹 4.5.5 的匹配链重实现了一遍，优先级由高到低：
 *
 * | 分 | 匹配器 | 处理 |
 * |----|--------|------|
 * |110|用户映射|用户自己配的别名（正则 / 平台 / 固定账户）|
 * |100|精确同名|归一后完全相等|
 * | 80|银行卡尾号|`...(1234)` 取下 4 位，资产名或备注包含它|
 * | 70|文本相似度|Dice + LCS，尾号 +0.30、银行一致 +0.25 / 冲突 −0.15|
 * | 65|场景词典|零钱/零钱通→微信，余额宝/花呗→支付宝，白条→京东…|
 * | 60|平台默认|该平台名下的第一张资产|
 * | — |不猜|null，交给确认卡让用户手选|
 *
 * 关键自律：**匹配不到就返回 null**，绝不自动建账户、绝不硬塞一个近似名字。
 */
data class AutoAsset(
    val name: String,
    val subName: String = "",
    val note: String = "",
    val iconKey: String = "",
    val category: String = "",
)

/**
 * 用户自定义映射（落库前的最高优先级）。
 *
 * [match] 支持正则；[platform] 非空时只在该平台生效；
 * 顺序即优先级，前面的先命中。
 */
data class AutoAssetMapping(
    val match: String,
    val assetName: String,
    val platform: String = "",
    val enabled: Boolean = true,
)

/** 一次匹配的结果；[assetName] 为 null 表示「不猜」 */
data class AssetMatch(
    val raw: String,
    val normalized: String,
    val assetName: String?,
    val score: Int,
    val matcher: String,
    val tail: String = "",
    val note: String = "",
) {
    val matched: Boolean get() = !assetName.isNullOrBlank()

    /** 匹配审计串：真机排查「为什么记到这个账户」时看这一行 */
    val trace: String
        get() = "$matcher[${raw.ifBlank { "-" }}→${normalized.ifBlank { "-" }}][${assetName ?: "未匹配"}]"
}

object AutoAssetMatcher {

    /** 银行全称 → 简称（原文与资产名两侧都对它归一） */
    private val BANKS = listOf(
        "工商银行" to "工商", "工行" to "工商",
        "建设银行" to "建设", "建行" to "建设",
        "农业银行" to "农业", "农行" to "农业",
        "中国银行" to "中行", "中行" to "中行",
        "招商银行" to "招商", "招行" to "招商",
        "交通银行" to "交通", "交行" to "交通",
        "邮政储蓄" to "邮储", "邮储" to "邮储",
        "浦发银行" to "浦发", "中信银行" to "中信",
        "民生银行" to "民生", "光大银行" to "光大",
        "兴业银行" to "兴业", "平安银行" to "平安",
        "华夏银行" to "华夏", "广发银行" to "广发",
        "北京银行" to "北京", "上海银行" to "上海",
        "宁波银行" to "宁波", "江苏银行" to "江苏",
        "浙商银行" to "浙商",
    )

    /** 场景词典：页面渠道词 → 账户平台关键词（按词长降序匹配，先长后短） */
    private val SCENE = listOf(
        "微信" to listOf("微信零钱", "零钱通", "微信支付", "微信", "零钱"),
        "支付宝" to listOf("余额宝", "花呗", "支付宝", "余额"),
        "云闪付" to listOf("云闪付", "银联"),
        "京东" to listOf("京东支付", "白条", "京东"),
        "美团" to listOf("美团支付", "美团月付", "美团"),
        "拼多多" to listOf("多多支付", "拼多多"),
        "抖音" to listOf("抖音支付", "抖音"),
    )

    /** 归一化时要剥掉的渠道噪声词 */
    private val NOISE = listOf(
        "银行卡", "储蓄卡", "信用卡", "借记卡", "银联", "支付",
        "钱包", "零钱", "余额", "默认", "优先使用",
    )

    private val TAIL_PATTERNS = listOf(
        Regex("""[（(](\d{4,})[)）]"""),
        Regex("""(?:尾号|卡号)(\d{4,})"""),
        Regex("""\D(\d{4,6})\D"""),
    )

    private val PUNCT = Regex("""[\s\-_·•/\\|:：,，.。;；'"“”‘’（）()\[\]【】<>《》¥￥$]""")

    /**
     * @param raw 页面上取到的账户/渠道原文
     * @param platform 平台展示名（微信 / 支付宝 / 云闪付 / 京东 / 美团 / 拼多多 / 抖音）
     * @param assets 本机资产（顺序即排序，平台默认取第一张）
     * @param mappings 用户映射（可空，最高优先级）
     */
    fun match(
        raw: String,
        platform: String,
        assets: List<AutoAsset>,
        mappings: List<AutoAssetMapping> = emptyList(),
    ): AssetMatch {
        val text = raw.trim()
        val normalized = normalize(text)
        val tail = extractTail(text)
        val bank = detectBank(text) ?: detectBank(normalized)
        if (assets.isEmpty()) {
            return AssetMatch(text, normalized, null, 0, "无资产", tail)
        }

        // 110 用户映射：正则或纯文本，只在指定平台生效
        mappings.firstOrNull { m ->
            m.enabled &&
                (m.platform.isBlank() || m.platform == platform) &&
                m.match.isNotBlank() &&
                (m.match == text || m.match == normalized ||
                    runCatching { Regex(m.match).containsMatchIn(text) }.getOrDefault(false))
        }?.let { mapping ->
            pick(assets, mapping.assetName)?.let { asset ->
                return AssetMatch(text, normalized, asset.name, 110, "用户映射", tail)
            }
        }

        // 100 精确同名（归一后比较，容忍「微信钱包」vs「微信 钱包」）
        if (normalized.isNotEmpty()) {
            assets.firstOrNull { normalize(it.name) == normalized }?.let { asset ->
                return AssetMatch(text, normalized, asset.name, 100, "精确同名", tail)
            }
        }

        // 80 银行卡尾号：原文带尾号、资产名或备注里也有同一个尾号
        if (tail.isNotEmpty()) {
            assets.firstOrNull { a ->
                listOf(a.name, a.note, a.subName).any { it.contains(tail) }
            }?.let { asset ->
                return AssetMatch(text, normalized, asset.name, 80, "银行卡尾号", tail)
            }
        }

        // 70 文本相似度：取最高分；分差过小视为不确定
        if (normalized.isNotEmpty()) {
            val scored = assets.map { asset ->
                asset to similarity(normalized, asset, tail, bank)
            }.sortedByDescending { it.second }
            val best = scored.firstOrNull()
            if (best != null) {
                val threshold = threshold(normalized, bank, tail)
                val tied = scored.count { it.second >= best.second - 0.02 } > 1
                if (best.second >= threshold && !tied) {
                    return AssetMatch(text, normalized, best.first.name, 70, "相似度${pct(best.second)}", tail)
                }
            }
        }

        // 65 场景词典：零钱/零钱通 → 微信，余额宝/花呗 → 支付宝，白条 → 京东…
        val sceneHit = SCENE.asSequence()
            .flatMap { (token, words) -> words.asSequence().map { word -> token to word } }
            .sortedByDescending { it.second.length }
            .firstOrNull { (_, word) -> text.contains(word) || normalized.contains(word) }
        if (sceneHit != null) {
            val token = sceneHit.first
            assets.firstOrNull { a ->
                normalize(a.name).contains(normalize(token)) ||
                    normalize(a.subName).contains(normalize(token))
            }?.let { asset ->
                return AssetMatch(text, normalized, asset.name, 65, "场景·${sceneHit.second}", tail)
            }
            // 转账/还款类：原文是「余额宝」但库里只有「支付宝」，上面的 contains 已覆盖；
            // 仍未命中就继续往下走默认
        }

        // 60 平台默认：该平台名下的第一张资产
        val platformToken = SCENE.firstOrNull { it.first == platform }?.first
            ?: platform.takeIf { it.isNotBlank() }
        if (platformToken != null) {
            assets.firstOrNull { a ->
                normalize(a.name).contains(normalize(platformToken)) ||
                    normalize(a.subName).contains(normalize(platformToken))
            }?.let { asset ->
                return AssetMatch(text, normalized, asset.name, 60, "平台默认·$platform", tail)
            }
        }

        return AssetMatch(text, normalized, null, 0, "未匹配", tail)
    }

    // ── 文本处理 ───────────────────────────────────────────────────────────

    /** 去括号内容、银行全称归一、剥渠道噪声、去标点空白、小写 */
    fun normalize(input: String): String {
        var s = input.trim()
        s = s.replace(Regex("""[（(][^）)]*[)）]"""), "")
        BANKS.forEach { (full, short) -> s = s.replace(full, short) }
        NOISE.forEach { s = s.replace(it, "") }
        s = PUNCT.replace(s, "")
        return s.lowercase()
    }

    /** 取卡号尾号：`(1234)` / `尾号1234` / 中间 4~6 位 */
    fun extractTail(input: String): String {
        TAIL_PATTERNS.forEach { pattern ->
            val m = pattern.find(input) ?: return@forEach
            val value = m.groupValues.getOrNull(1).orEmpty()
            if (value.length >= 4) return value
        }
        return ""
    }

    private fun detectBank(input: String): String? =
        BANKS.firstOrNull { (full, short) -> input.contains(full) || input.contains(short) }?.second

    private fun pick(assets: List<AutoAsset>, name: String): AutoAsset? {
        val target = normalize(name)
        return assets.firstOrNull { normalize(it.name) == target }
            ?: assets.firstOrNull { normalize(it.name).contains(target) && target.isNotEmpty() }
    }

    /** 相似度：Dice 二元组 0.5 + LCS 0.5，再加尾号 / 银行权重 */
    private fun similarity(normalized: String, asset: AutoAsset, tail: String, bank: String?): Double {
        val name = normalize(asset.name)
        if (name.isEmpty()) return 0.0
        var score = dice(normalized, name) * 0.5 + lcsRatio(normalized, name) * 0.5
        if (tail.isNotEmpty() && (asset.note.contains(tail) || asset.subName.contains(tail) || name.contains(tail))) {
            score += 0.30
        }
        val assetBank = detectBank(asset.name) ?: detectBank(asset.note)
        if (bank != null && assetBank != null) {
            score += if (bank == assetBank) 0.25 else -0.15
        }
        return score
    }

    /** 阈值随输入长度放宽：越短的输入越容易误配，要求越高 */
    private fun threshold(normalized: String, bank: String?, tail: String): Double = when {
        normalized.length < 4 -> 0.90
        normalized.length > 10 -> 0.60
        bank != null && tail.isNotEmpty() -> 0.60
        else -> 0.70
    }

    private fun dice(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length < 2 || b.length < 2) return 0.0
        val aPairs = HashMap<String, Int>()
        for (i in 0 until a.length - 1) {
            val pair = a.substring(i, i + 2)
            aPairs[pair] = (aPairs[pair] ?: 0) + 1
        }
        var hit = 0
        for (i in 0 until b.length - 1) {
            val pair = b.substring(i, i + 2)
            val left = aPairs[pair] ?: continue
            if (left > 0) {
                hit++
                aPairs[pair] = left - 1
            }
        }
        return 2.0 * hit / (a.length - 1 + b.length - 1)
    }

    private fun lcsRatio(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dp = IntArray(b.length + 1)
        for (i in 1..a.length) {
            var prev = 0
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev + 1 else maxOf(dp[j], dp[j - 1])
                prev = temp
            }
        }
        return dp[b.length].toDouble() / maxOf(a.length, b.length)
    }

    private fun pct(v: Double) = (v * 100).toInt().coerceIn(0, 999)
}
