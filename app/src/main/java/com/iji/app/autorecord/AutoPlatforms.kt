package com.iji.app.autorecord

import com.iji.app.data.model.TransactionType

/**
 * 一次节点识别得到的账单。
 *
 * 字段对齐钱迹 `BillInfo` 的关键项，但只保留我们自己用得上的：
 * 金额（分、绝对值）、收支类型、备注、商户、出入账资产、时间、优惠/手续费。
 * 收支方向由**页面文案**判定，不由金额符号判定——`-12.00` 与 `12.00` 都可能是支出。
 */
data class AutoBillDraft(
    val packageName: String,
    val platform: String,
    val pageType: String,
    val amountCents: Long,
    val type: TransactionType,
    val remark: String,
    val merchant: String,
    val outAsset: String,
    val inAsset: String,
    val timeText: String,
    val timeMillis: Long,
    val feeCents: Long = 0L,
) {
    /**
     * 去重键：平台 + 页面 + 收支 + 时间 + 金额。
     * 同一页面反复触发（内容变化事件会连发多次）必须只算一笔；
     * 时间缺失时退化为「平台+页面+收支+金额」，与钱迹的二级拦截同思路。
     */
    val dedupKey: String
        get() = listOf(
            packageName,
            pageType,
            type.name,
            timeText.ifEmpty { "-" },
            amountCents.toString(),
        ).joinToString("|")
}

/** 一条识别规则：全部 [required] 命中、且 [forbidden] 都没命中，才交给 [extract] 取值 */
class AutoBillRule(
    val pageType: String,
    val required: List<NodeMatcher>,
    val forbidden: List<NodeMatcher> = emptyList(),
    val extract: (NodeList) -> AutoBillDraft?,
)

/** 一个平台的规则集与触发参数 */
data class AutoPlatform(
    val packageName: String,
    val name: String,
    val rules: List<AutoBillRule>,
    /** 页面判定：返回 false 表示这个窗口不处理（如微信首页/聊天列表） */
    val isCandidatePage: (className: String?) -> Boolean = { true },
    /**
     * 页面级黑名单：命中任一条件直接放弃解析。
     * 目前只用于「支付密码键盘」——那上面有金额数字，误识别会记出一笔假账。
     */
    val blocked: List<NodeMatcher> = emptyList(),
    /** 页面事件到抓节点之间的等待：给页面渲染留时间 */
    val delayMillis: Long = 300L,
    /** 早抓通道（只跑指定页面）：支付宝用它覆盖「支付成功页先出、详情页后渲染」两种时序 */
    val fastDelayMillis: Long? = null,
    val fastPageTypes: Set<String> = emptySet(),
    /**
     * 是否允许用 Shizuku 的 shell 身份兜底读节点。
     *
     * 只对「无障碍稳定返回空树」的平台开启（当前只有微信的 MMWebViewUI）。
     * 其余平台一律不兜底：`uiautomator dump` 单次 3–11 秒，
     * 无差别套用会把解析队列堵死（真机日志里京东一次 dump 花了 11.3 秒、返回 0）。
     */
    val shizukuFallback: Boolean = false,
)

/**
 * 识别引擎：平台 → 规则，**顺序命中第一个**（与钱迹同序，长规则在前、兜底在后）。
 *
 * 纯函数式：输入节点快照，输出账单草稿或 null。不碰数据库、不发通知，
 * 便于单独调规则，也便于在没有真机支付 App 的环境里用文本用例验证。
 */
object AutoBillEngine {

    fun parse(packageName: String, root: android.view.accessibility.AccessibilityNodeInfo?): AutoBillDraft? {
        val platform = AutoPlatforms.byPackage(packageName) ?: return null
        return parse(platform, AutoNodeCollector.collect(root))
    }

    fun parse(platform: AutoPlatform, nodes: List<AutoNode>): AutoBillDraft? = parseWithPage(platform, nodes)?.first

    /** 返回「草稿 + 命中的页面类型」，早抓通道需要按页面类型过滤 */
    fun parseWithPage(platform: AutoPlatform, nodes: List<AutoNode>): Pair<AutoBillDraft, String>? {
        if (nodes.isEmpty()) return null
        // 黑名单页面（支付密码键盘等）：宁可不记，也不能把键盘上的数字当金额
        if (platform.blocked.any { it.find(nodes) != null }) return null
        val list = NodeList(nodes)
        for (rule in platform.rules) {
            if (rule.required.any { it.find(nodes) == null }) continue
            if (rule.forbidden.any { it.find(nodes) != null }) continue
            val raw = runCatching { rule.extract(list) }.getOrNull() ?: continue
            if (raw.amountCents <= 0) continue
            // 出口统一清洗：备注/商户去界面噪声，账户原文去前缀，优惠归集到 fee。
            // 规则自己取到的优惠（立减/服务费）优先，避免被全局扫描覆盖掉。
            val draft = raw.copy(
                remark = AutoText.cleanNote(raw.remark),
                merchant = AutoText.cleanMerchant(raw.merchant),
                outAsset = AutoText.cleanAsset(raw.outAsset),
                inAsset = AutoText.cleanAsset(raw.inAsset),
                feeCents = when {
                    raw.feeCents > 0L -> raw.feeCents.coerceAtMost(raw.amountCents)
                    raw.type == TransactionType.EXPENSE ->
                        AutoText.couponCents(nodes).coerceAtMost(raw.amountCents)
                    else -> 0L
                },
            )
            return draft to rule.pageType
        }
        return null
    }
}

/**
 * 8 平台规则表。页面条件只用固定标识，金额锚定到标签（不用 firstAmount 裸兜底），
 * pageType 全局唯一；特异性高的规则在前，顺序命中。
 */
object AutoPlatforms {

    private const val WECHAT = "com.tencent.mm"
    private const val ALIPAY = "com.eg.android.AlipayGphone"
    private const val UNIONPAY = "com.unionpay"
    private const val JD = "com.jingdong.app.mall"
    private const val MEITUAN = "com.sankuai.meituan"
    private const val TAOBAO = "com.taobao.taobao"
    private const val PDD = "com.xunmeng.pinduoduo"
    private const val DOUYIN = "com.ss.android.ugc.aweme"

    private const val TEXT = "android.widget.TextView"
    private const val VIEW = "android.view.View"
    private const val WEBVIEW = "android.webkit.WebView"
    private const val FLATTEN = "com.lynx.tasm.behavior.ui.text.FlattenUIText"

    /** 只排除聊天/社交页（事件高频易 ANR）；账单页容器类名不统一，不能用白名单。 */
    private val WECHAT_BLOCKED_CLASSES = listOf(
        "LauncherUI", "ChattingUI", "SnsTimeLineUI", "ContactList",
        "Conversation", "Finder", "ContactInfoUI", "ChatroomContactUI",
    )

    private const val UNIONPAY_HOME = "com.unionpay.activity.UPActivityMain"

    private val TIME_PATTERN = Regex(
        """^\d{4}[-./年]\d{1,2}[-./月]\d{1,2}(?:日)?(?:\s*\d{2}:\d{2}(?::\d{2})?)?$""",
    )
    private val AMOUNT_STRICT = Regex(
        """^(?:已支付|支出|收入)?\s*(?:[-+][¥￥]|[¥￥][-+]|[-+]|[¥￥])?(?:\d{1,3}(?:,\d{3}){0,2}|\d{1,9})\.\d{2}(?:元)?$""",
    )

    /** 淘宝店铺/商品名之外的按钮与状态词，取备注时要跳过 */
    private val TAOBAO_NOISE = setOf(
        "交易成功", "已发货", "待发货", "实付款", "更多", "申请开票", "催发货",
        "确认收货", "再买一单", "闲鱼转卖", "加入购物车", "查看物流", "延长收货",
        "退货宝", "7天价保", "7天无理由退换", "不支持7天无理由", "大促价保",
        "假一赔十", "假一赔四",
    )

    /** 淘宝的纯金额节点：`¥88.00` / `￥1,234.00`，不含「实付款」这类混排文本 */
    private val TAOBAO_AMOUNT = Regex("""^[¥￥]\d+(?:,\d{3})*(?:\.\d{1,2})?$""")

    val all: List<AutoPlatform> by lazy {
        listOf(wechat(), alipay(), unionpay(), jingdong(), meituan(), taobao(), pinduoduo(), douyin())
    }

    fun byPackage(packageName: String): AutoPlatform? = all.firstOrNull { it.packageName == packageName }

    fun packages(): Set<String> = all.map { it.packageName }.toSet()

    fun nameOf(packageName: String): String =
        all.firstOrNull { it.packageName == packageName }?.name ?: packageName

    /**
     * 统一的草稿出口。
     *
     * @param feeCents 页面上明确写出的优惠/服务费（正数）。为 0 时由引擎扫描优惠标签兜底。
     */
    private fun emit(
        packageName: String,
        platform: String,
        pageType: String,
        cents: Long?,
        type: TransactionType,
        remark: String = "",
        merchant: String = "",
        outAsset: String = "",
        inAsset: String = "",
        timeText: String = "",
        feeCents: Long = 0L,
    ): AutoBillDraft? {
        val value = cents ?: return null
        if (value <= 0L) return null
        return AutoBillDraft(
            packageName = packageName,
            platform = platform,
            pageType = pageType,
            amountCents = value,
            type = type,
            remark = remark,
            merchant = merchant,
            outAsset = outAsset,
            inAsset = inAsset,
            timeText = timeText,
            timeMillis = AutoValues.parseTime(timeText),
            feeCents = feeCents,
        )
    }


    // ── 微信 ────────────────────────────────────────────────────────────────
    // 页面判定：只排除 LauncherUI，其余页面全部交给规则集（钱迹 ca/i 的行为）

    private fun wechat() = AutoPlatform(
        packageName = WECHAT,
        name = "微信",
        // 微信账单是 MMWebViewUI，页面文本比原生页晚得多：150ms 抓到的常常是
        // 「当前状态」还没渲染完的半成品，解析一次就进冷却，表现为完全不识别。
        delayMillis = 350L,
        // 只排除聊天/社交页，其余页面（含各种容器类名的账单页）都允许识别
        isCandidatePage = { className ->
            className == null || WECHAT_BLOCKED_CLASSES.none { className.contains(it) }
        },
        blocked = listOf(
            contains("请输入支付密码"),
            contains("请验证支付密码"),
            idMatch("com.tencent.mm:id/tenpay_keyboard_1"),
        ),
        // 微信账单详情是 MMWebViewUI：对第三方无障碍稳定返回空树（flags 全开仍 child=0），
        // 只有 shell 身份能读到。这是本平台独有的系统限制，其余平台不做兜底。
        shizukuFallback = true,
        rules = listOf(
            // 转账待收款：付款方视角（钱迹 WeChatTransferDetailWaiting）
            AutoBillRule(
                pageType = "WeChatTransferDetailWaiting",
                required = listOf(
                    anyOf(
                        regex("""^待\s*(.+)收款$"""),
                        regex("""^(.+)已收款$"""),
                        startsWith("你已收款，"),
                    ),
                    anyOf(exact("转账时间"), exact("收款时间"), exact("时间"), regex(TIME_PATTERN.pattern)),
                    amount(),
                ),
                // 四个底部 tab 同时出现才是微信主界面，单个「微信」不能排除
                forbidden = listOf(forbiddenCoexist(exact("微信"), exact("通讯录"), exact("发现"), exact("我"))),
                extract = { n ->
                    val income = n.findStarts("你已收款，") != null || n.exists("已收款")
                    // 标题就是「待 张三 收款」/「张三已收款」，名字要剥出来当商户
                    val title = n.firstRegex(Regex("""^待\s*(.+)收款$"""))
                        ?.let { Regex("""^待\s*(.+)收款$""").find(it)?.groupValues?.get(1)?.trim() }
                        ?: n.firstRegex(Regex("""^(.+)已收款$"""))
                            ?.let { Regex("""^(.+)已收款$""").find(it)?.groupValues?.get(1)?.trim() }
                    emit(
                        WECHAT, "微信", "WeChatTransferDetailWaiting",
                        n.amountAfter("转账金额") ?: n.firstAmount(strict = true),
                        if (income) TransactionType.INCOME else TransactionType.EXPENSE,
                        remark = n.afterAny("转账说明", "转账附言", "备注").orEmpty(),
                        merchant = title.orEmpty(),
                        outAsset = n.after("支付方式").orEmpty(),
                        timeText = n.timeAfterAny("转账时间", "收款时间", "时间").orEmpty(),
                    )
                },
            ),
            // 商家转账到零钱：收款方视角
            AutoBillRule(
                pageType = "WeChatTransferInDetail",
                required = listOf(
                    startsWith("商家转账-来自"),
                    exact("付款商家"),
                    exact("收款方式"),
                    exact("转账时间"),
                ),
                extract = { n ->
                    emit(
                        WECHAT, "微信", "WeChatTransferInDetail",
                        n.amountAfter("转账金额") ?: n.firstAmount(strict = true),
                        TransactionType.INCOME,
                        remark = n.afterAny("转账说明", "转账附言", "备注").orEmpty(),
                        merchant = n.after("付款商家").orEmpty(),
                        inAsset = n.after("收款方式").orEmpty(),
                        timeText = n.timeAfter("转账时间").orEmpty(),
                    )
                },
            ),
            // 零钱提现详情：申请中/到账中都能记，金额与手续费分开取
            AutoBillRule(
                pageType = "WechatWithdrawDetail",
                required = listOf(
                    startsWith("零钱提现-"),
                    exact("当前状态"),
                    exact("发起提现"),
                    exact("提现金额"),
                    exact("申请时间"),
                    exact("到账时间"),
                ),
                extract = { n ->
                    emit(
                        WECHAT, "微信", "WechatWithdrawDetail",
                        n.amountAfter("提现金额"),
                        TransactionType.TRANSFER,
                        remark = "微信提现",
                        outAsset = "微信零钱",
                        inAsset = n.nextByType("提现银行", TEXT).orEmpty(),
                        timeText = n.timeAfterAny("到账时间", "申请时间").orEmpty(),
                        feeCents = n.amountAfter("服务费") ?: 0L,
                    )
                },
            ),
            // 零钱提现成功页：比账单详情更早出现
            AutoBillRule(
                pageType = "WechatWithdrawSuccess",
                required = listOf(
                    exact("零钱提现"),
                    exact("发起提现申请"),
                    exact("提现金额"),
                    exact("到账银行卡"),
                ),
                extract = { n ->
                    emit(
                        WECHAT, "微信", "WechatWithdrawSuccess",
                        n.nextExclude("提现金额", "￥", TEXT)?.let { AutoValues.amountCents(it) }
                            ?: n.amountAfter("提现金额"),
                        TransactionType.TRANSFER,
                        remark = "微信提现",
                        outAsset = "微信零钱",
                        inAsset = n.nextByType("到账银行卡", TEXT).orEmpty(),
                        feeCents = n.nextByType("服务费", VIEW)?.let { AutoValues.amountCents(it) }
                            ?: n.amountAfter("服务费") ?: 0L,
                    )
                },
            ),
            // 零钱充值：firstAsset 在「零钱充值-来自XX」这一整句里，金额是它后面第一个 TextView
            AutoBillRule(
                pageType = "WechatChargeDetail",
                required = listOf(
                    startsWith("零钱充值-"),
                    exact("当前状态"),
                    exact("充值完成"),
                    exact("充值时间"),
                    exact("支付方式"),
                ),
                extract = { n ->
                    val source = n.findStarts("零钱充值-来自")
                    val from = source?.text?.removePrefix("零钱充值-来自").orEmpty()
                    emit(
                        WECHAT, "微信", "WechatChargeDetail",
                        source?.let { n.nextByTypeOf(it, TEXT) }?.let { AutoValues.amountCents(it) }
                            ?: n.firstAmount(strict = true),
                        TransactionType.TRANSFER,
                        remark = "零钱充值",
                        outAsset = from,
                        inAsset = "微信零钱",
                        timeText = n.timeAfter("充值时间").orEmpty(),
                    )
                },
            ),
            // 转账详情（转给别人）
            AutoBillRule(
                pageType = "WeChatTransferDetail",
                required = listOf(
                    startsWith("转账-转给"),
                    exact("当前状态"),
                    exact("转账时间"),
                    exact("支付方式"),
                ),
                extract = { n ->
                    emit(
                        WECHAT, "微信", "WeChatTransferDetail",
                        n.amountAfter("转账金额") ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = n.afterAny("转账说明", "付款方留言", "备注").orEmpty(),
                        merchant = n.findStarts("转账-转给")?.text?.removePrefix("转账-转给").orEmpty(),
                        outAsset = n.after("支付方式").orEmpty(),
                        timeText = n.timeAfter("转账时间").orEmpty(),
                    )
                },
            ),
            // 账单详情：覆盖付款、转账、收款、提现、退款，最全的一条
            AutoBillRule(
                pageType = "WeChatBillDetail",
                required = listOf(
                    exact("当前状态"),
                    anyOf(
                        exact("支付时间"), exact("转账时间"), exact("收款时间"),
                        exact("到账时间"), exact("时间"), regex(TIME_PATTERN.pattern),
                    ),
                    anyOf(
                        exact("支付成功"), startsWith("已存入"), exact("收款成功"),
                        contains("交易成功"), exact("已收钱"),
                        regex("""已退款\(￥\d+\.?\d*\)"""),
                    ),
                ),
                extract = { n ->
                    val income = n.exists(
                        "转账-来自", "经营收款-来自", "微信红包-来自", "二维码收款-",
                    ) || n.exists("收款成功", "已收钱") || n.firstRegex(
                        Regex("""^(?:收入\s*[¥￥]?|[+][¥￥]?)\s*\d[\d,]*(?:\.\d{1,2})?(?:元)?$"""),
                    ) != null
                    val transfer = n.find("提现金额") != null
                    val cents = n.firstRegex(Regex("""^金额\s*[-+]?[¥￥]?[-+]?\d[\d,]*(?:\.\d{1,2})?元?$"""))
                        ?.let { AutoValues.amountCents(it) }
                        ?: n.amountAfterAny("支付金额", "转账金额", "收款金额", "提现金额")
                        ?: n.amountAfter("金额")
                        ?: n.firstAmount(strict = true)
                    emit(
                        WECHAT, "微信", "WeChatBillDetail",
                        cents,
                        when {
                            transfer -> TransactionType.TRANSFER
                            income -> TransactionType.INCOME
                            else -> TransactionType.EXPENSE
                        },
                        remark = n.afterAny("付款方留言", "转账说明", "备注", "商品", "收款方备注").orEmpty(),
                        merchant = n.after("商户全称").orEmpty(),
                        outAsset = n.after("支付方式").orEmpty(),
                        inAsset = n.afterAny("到账银行卡", "收款方").orEmpty(),
                        timeText = n.timeAfterAny("支付时间", "转账时间", "收款时间", "到账时间", "时间").orEmpty(),
                    )
                },
            ),
            // 「支付账单」页（真机截图）：标题直接是「支付成功」，没有「当前状态」标签。
            // 旧版账单详情规则要求「当前状态」，这类页面永远匹配不到。
            AutoBillRule(
                pageType = "WeChatPayBillDetail",
                required = listOf(
                    contains("支付账单"),
                    contains("支付成功"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        WECHAT, "微信", "WeChatPayBillDetail",
                        n.amountAfter("支付成功") ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = n.afterAny("商品", "商品说明").orEmpty(),
                        merchant = n.after("商户全称").orEmpty(),
                        outAsset = n.after("支付方式").orEmpty(),
                        timeText = n.timeAfterAny("支付时间", "时间").orEmpty(),
                    )
                },
            ),
            // 支付成功页：只有标题 / 金额，没有时间
            AutoBillRule(
                pageType = "WeChatPaySuccess",
                required = listOf(
                    anyOf(exact("支付成功"), exact("充值成功")),
                    amount(),
                ),
                // 钱迹这里是 **同时存在** 才排除：首页+点单 是点单页，支付成功+去下单 是引导页
                forbidden = listOf(
                    forbiddenCoexist(exact("首页"), exact("点单")),
                    forbiddenCoexist(exact("支付成功"), exact("去下单")),
                ),
                extract = { n ->
                    val cents = n.firstRegex(
                        Regex("""^(?:实付金额|实际支付|支出|收入)[：:，,]?\s*[¥￥]?\d[\d,]*(?:\.\d{1,2})?(?:元)?$"""),
                    )?.let { AutoValues.amountCents(it) }
                        ?: n.amountAfterAny("支付金额", "实付金额", "实际支付")
                        ?: n.firstAmount(strict = true)
                    val payee = n.firstRegex(Regex("""^待(.*)确认收款$"""))?.let {
                        Regex("""^待(.*)确认收款$""").find(it)?.groupValues?.get(1)
                    }
                    emit(
                        WECHAT, "微信", "WeChatPaySuccess",
                        cents,
                        TransactionType.EXPENSE,
                        remark = if (payee.isNullOrBlank()) "微信支付" else "转账给$payee",
                        merchant = payee.orEmpty(),
                        outAsset = n.after("支付方式").orEmpty(),
                    )
                },
            ),
            // 发红包：整句里同时有金额与领取状态
            AutoBillRule(
                pageType = "WechatPersonalRedPacketSend",
                required = listOf(
                    endsWith("的红包"),
                    anyOf(
                        regex("""红包金额(\d+\.\d{2})元，等待对方领取"""),
                        regex("""^(\d+)个红包共([0-9]+(\.[0-9]{2}))元$"""),
                    ),
                ),
                extract = { n ->
                    val inline = n.firstRegex(Regex("""红包金额(\d+\.\d{2})元，等待对方领取"""))
                        ?.let { Regex("""(\d+\.\d{2})""").find(it)?.value }
                    val group = n.firstRegex(Regex("""^(\d+)个红包共([0-9]+(\.[0-9]{2}))元$"""))
                        ?.let { Regex("""共([0-9]+(?:\.[0-9]{2}))元""").find(it)?.groupValues?.get(1) }
                    emit(
                        WECHAT, "微信", "WechatPersonalRedPacketSend",
                        (inline ?: group)?.let { AutoValues.amountCents(it) },
                        TransactionType.EXPENSE,
                        remark = n.afterAny("红包留言", "祝福语").orEmpty(),
                        outAsset = n.after("支付方式").orEmpty(),
                        timeText = n.timeAfterAny("支付时间", "时间").orEmpty(),
                    )
                },
            ),
            // 群红包详情：金额是「共 X/Y 元」里的总额
            AutoBillRule(
                pageType = "WechatGroupRedPacketDetail",
                required = listOf(
                    endsWith("的红包"),
                    exact("元"),
                    anyOf(
                        regex("""^已?领取(\d+)/(\d+)个(，共(\d+\.\d{2})/(\d+\.\d{2})元)?$"""),
                        regex("""^.*\d+.*被抢光$"""),
                    ),
                ),
                extract = { n ->
                    val total = n.firstRegex(
                        Regex("""^已?领取(\d+)/(\d+)个(，共(\d+\.\d{2})/(\d+\.\d{2})元)?$"""),
                    )?.let {
                        Regex("""/(\d+\.\d{2})元""").find(it)?.groupValues?.get(1)
                    }
                    emit(
                        WECHAT, "微信", "WechatGroupRedPacketDetail",
                        total?.let { AutoValues.amountCents(it) } ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = "群红包",
                        outAsset = n.after("支付方式").orEmpty(),
                        timeText = n.timeAfterAny("支付时间", "时间").orEmpty(),
                    )
                },
            ),
            // 领到红包（详情页版）
            AutoBillRule(
                pageType = "WechatPersonalRedPacketReceive",
                required = listOf(
                    endsWith("的红包"),
                    regex("""^(\d+\.\d{2})$"""),
                    regex("""^(\d+\.\d{2})元$"""),
                ),
                extract = { n ->
                    emit(
                        WECHAT, "微信", "WechatPersonalRedPacketReceive",
                        n.firstRegex(Regex("""^(\d+\.\d{2})$"""))?.let { AutoValues.amountCents(it) }
                            ?: n.firstRegex(Regex("""^(\d+\.\d{2})元$"""))?.let { AutoValues.amountCents(it) },
                        TransactionType.INCOME,
                        remark = n.findContains("的红包")?.text.orEmpty(),
                        inAsset = "微信零钱",
                    )
                },
            ),
            // 领到红包（开红包浮层版）
            AutoBillRule(
                pageType = "WechatPersonalRedPacketReceive2",
                required = listOf(
                    endsWith("的红包"),
                    regex("""^\d+\.\d{2}$"""),
                    exact("元"),
                    anyOf(exact("返回"), exact("更多")),
                ),
                extract = { n ->
                    emit(
                        WECHAT, "微信", "WechatPersonalRedPacketReceive2",
                        n.firstRegex(Regex("""^\d+\.\d{2}$"""))?.let { AutoValues.amountCents(it) },
                        TransactionType.INCOME,
                        remark = "微信红包",
                        inAsset = "微信零钱",
                    )
                },
            ),
        ),
    )

    // ── 支付宝 ──────────────────────────────────────────────────────────────
    // 两条时间通道：160ms 只认支付成功页，450ms 跑全量（钱迹同款双抓）

    private fun alipay() = AutoPlatform(
        packageName = ALIPAY,
        name = "支付宝",
        delayMillis = 450L,
        fastDelayMillis = 160L,
        fastPageTypes = setOf("AlipayPaySuccess"),
        rules = listOf(
            // 余额明细详情（充值）
            AutoBillRule(
                pageType = "AlipayChargeDetail",
                required = listOf(
                    exact("余额明细详情"),
                    startsWith("余额充值"),
                    startsWith("支付时间"),
                    exact("对方账户"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayChargeDetail",
                        n.amountAfter("订单金额") ?: n.firstAmount(strict = true),
                        TransactionType.TRANSFER,
                        remark = n.afterAny("备注", "商品说明").orEmpty(),
                        outAsset = n.nextByType("对方账户", TEXT).orEmpty(),
                        inAsset = "支付宝余额",
                        timeText = n.timeAfter("支付时间").orEmpty(),
                    )
                },
            ),
            // 余额明细详情（提现）
            AutoBillRule(
                pageType = "AlipayWithdrawDetail",
                required = listOf(
                    exact("余额明细详情"),
                    startsWith("余额提现"),
                    startsWith("支付时间"),
                    exact("付款方式"),
                    exact("对方账户"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayWithdrawDetail",
                        n.amountAfter("提现金额") ?: n.firstAmount(strict = true),
                        TransactionType.TRANSFER,
                        remark = n.afterAny("备注", "提现说明").orEmpty(),
                        outAsset = n.nextByType("付款方式", TEXT).orEmpty(),
                        inAsset = n.nextByType("对方账户", TEXT).orEmpty(),
                        timeText = n.timeAfter("支付时间").orEmpty(),
                    )
                },
            ),
            // 余利宝：转入详情
            AutoBillRule(
                pageType = "AlipayYuLiBaoTransferInDetail",
                required = listOf(
                    exact("转入详情"),
                    startsWith("转入"),
                    startsWith("成功转入"),
                    startsWith("转入到："),
                    startsWith("付款账号："),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayYuLiBaoTransferInDetail",
                        n.firstAmount(strict = true),
                        TransactionType.TRANSFER,
                        remark = "余利宝转入",
                        outAsset = n.nextByType("付款账号：", TEXT).orEmpty(),
                        inAsset = n.nextByType("转入到：", TEXT).orEmpty(),
                    )
                },
            ),
            // 余利宝：转出详情
            AutoBillRule(
                pageType = "AlipayYuLiBaoTransferOutDetail",
                required = listOf(
                    exact("转出详情"),
                    startsWith("转出到"),
                    exact("成功转出"),
                    startsWith("从哪转出："),
                    startsWith("收款账号："),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayYuLiBaoTransferOutDetail",
                        n.firstAmount(strict = true),
                        TransactionType.TRANSFER,
                        remark = "余利宝转出",
                        outAsset = n.nextByType("从哪转出：", TEXT).orEmpty(),
                        inAsset = n.nextByType("收款账号：", TEXT).orEmpty(),
                    )
                },
            ),
            // 余利宝收益
            AutoBillRule(
                pageType = "AlipayYuLiBaoIncomeDetail",
                required = listOf(
                    exact("收益"),
                    exact("收益到账"),
                    startsWith("收益发放："),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayYuLiBaoIncomeDetail",
                        n.amountAfter("收益") ?: n.amountAfter("收益金额") ?: n.firstAmount(strict = true),
                        TransactionType.INCOME,
                        remark = "余利宝收益",
                        inAsset = "余利宝",
                    )
                },
            ),
            // 余利宝转入成功
            AutoBillRule(
                pageType = "AlipayYuLiBaoTransferInSuccess",
                required = listOf(
                    startsWith("转入成功"),
                    exact("付款方式"),
                    exact("开始计算收益"),
                    exact("收益到账"),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayYuLiBaoTransferInSuccess",
                        n.firstAmount(strict = true),
                        TransactionType.TRANSFER,
                        remark = "余利宝转入",
                        outAsset = n.nextByType("付款方式", TEXT).orEmpty(),
                        inAsset = "余利宝",
                    )
                },
            ),
            // 余利宝快赎转出成功
            AutoBillRule(
                pageType = "AlipayYuLiBaoTransferOutSuccess",
                required = listOf(
                    exact("快赎结果页状态"),
                    exact("转出成功"),
                    exact("收款账号"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayYuLiBaoTransferOutSuccess",
                        n.firstAmount(strict = true),
                        TransactionType.TRANSFER,
                        remark = "余利宝转出",
                        outAsset = "余利宝",
                        inAsset = n.nextByType("收款账号", TEXT).orEmpty(),
                    )
                },
            ),
            // 转出成功：金额在「¥」后面
            AutoBillRule(
                pageType = "AlipayTransferOut",
                required = listOf(
                    exact("转出成功"),
                    exact("¥"),
                    exact("到账账户"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayTransferOut",
                        n.nextExclude("转出成功", "¥", TEXT)?.let { AutoValues.amountCents(it) }
                            ?: n.amountAfter("转出成功"),
                        TransactionType.TRANSFER,
                        remark = "支付宝转出",
                        inAsset = n.nextByType("到账账户", TEXT).orEmpty(),
                    )
                },
            ),
            // 转入成功
            AutoBillRule(
                pageType = "AlipayTransferIn",
                required = listOf(
                    exact("转入成功"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayTransferIn",
                        n.nextMatch("转入成功", Regex(AMOUNT_STRICT.pattern), TEXT, 4)
                            ?.let { AutoValues.amountCents(it) }
                            ?: n.firstAmount(strict = true),
                        TransactionType.TRANSFER,
                        remark = "支付宝转入",
                    )
                },
            ),
            // 转账成功（对方）
            AutoBillRule(
                pageType = "AlipayTransfer",
                required = listOf(
                    exact("转账成功"),
                    anyOf(exact("交易方式"), exact("付款方式")),
                    exact("收款方"),
                    amount(),
                ),
                extract = { n ->
                    val payee = n.nextByType("收款方", TEXT).orEmpty()
                    emit(
                        ALIPAY, "支付宝", "AlipayTransfer",
                        n.nextMatch("转账成功", Regex(AMOUNT_STRICT.pattern), TEXT, 4)
                            ?.let { AutoValues.amountCents(it) }
                            ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = if (payee.isBlank()) "支付宝转账" else "转账给$payee",
                        merchant = payee,
                        outAsset = n.afterAny("交易方式", "付款方式").orEmpty(),
                    )
                },
            ),
            // 充值成功
            AutoBillRule(
                pageType = "AlipayCharge",
                required = listOf(
                    exact("充值成功"),
                    exact("付款方式"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayCharge",
                        n.nextMatch("充值成功", Regex(AMOUNT_STRICT.pattern), TEXT, 4)
                            ?.let { AutoValues.amountCents(it) }
                            ?: n.amountAfter("订单金额"),
                        TransactionType.TRANSFER,
                        remark = "支付宝充值",
                        outAsset = n.after("付款方式").orEmpty(),
                        inAsset = "支付宝余额",
                    )
                },
            ),
            // 提现成功
            AutoBillRule(
                pageType = "AlipayWithdraw",
                required = listOf(
                    exact("提现成功"),
                    startsWith("提现金额"),
                    exact("到账银行卡"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayWithdraw",
                        n.nextByType("提现金额", TEXT)?.let { AutoValues.amountCents(it) }
                            ?: n.amountAfter("提现金额"),
                        TransactionType.TRANSFER,
                        remark = "支付宝提现",
                        outAsset = "支付宝余额",
                        inAsset = n.nextByType("到账银行卡", TEXT).orEmpty(),
                        feeCents = n.amountAfter("服务费") ?: 0L,
                    )
                },
            ),
            // 发红包
            AutoBillRule(
                pageType = "AlipaySendRedPacket",
                required = listOf(
                    exact("发的红包"),
                    regex("""已领取(\d+)/(\d+)个，共(\d+\.\d{2})/(\d+\.\d{2})元"""),
                    exact("24小时内未领取，红包金额将被退回"),
                    exact("查看红包记录"),
                ),
                extract = { n ->
                    val text = n.firstRegex(
                        Regex("""已领取(\d+)/(\d+)个，共(\d+\.\d{2})/(\d+\.\d{2})元"""),
                    )
                    val total = text?.let {
                        Regex("""/(\d+\.\d{2})元""").find(it)?.groupValues?.get(1)
                    }
                    emit(
                        ALIPAY, "支付宝", "AlipaySendRedPacket",
                        total?.let { AutoValues.amountCents(it) },
                        TransactionType.EXPENSE,
                        remark = "支付宝红包",
                        outAsset = n.after("付款方式").orEmpty(),
                    )
                },
            ),
            // 二维码收款
            AutoBillRule(
                pageType = "AlipayQRCodeReceiveDetail",
                required = listOf(
                    exact("账单详情"),
                    exact("当前状态"),
                    exact("已收款"),
                    exact("收款时间"),
                ),
                extract = { n ->
                    emit(
                        ALIPAY, "支付宝", "AlipayQRCodeReceiveDetail",
                        n.amountAfter("订单金额") ?: n.firstAmount(strict = true),
                        TransactionType.INCOME,
                        remark = n.afterAny("交易说明", "付款方备注").orEmpty().ifBlank { "二维码收款" },
                        inAsset = "支付宝余额",
                        timeText = n.timeAfter("收款时间").orEmpty(),
                    )
                },
            ),
            // 支付成功页（早抓通道只认这一页）
            AutoBillRule(
                pageType = "AlipayPaySuccess",
                required = listOf(
                    exact("支付成功"),
                    amount(),
                    anyOf(regex("""^支付成功￥\d+\.\d+$"""), exact("完成")),
                ),
                extract = { n ->
                    val inline = n.firstRegex(Regex("""^支付成功￥\d+\.\d+$"""))
                    val cents = inline?.let { Regex("""￥(\d+\.\d+)""").find(it)?.groupValues?.get(1) }
                        ?.let { AutoValues.amountCents(it) }
                        ?: n.nextMatch("支付成功", Regex(AMOUNT_STRICT.pattern), TEXT, 6)
                            ?.let { AutoValues.amountCents(it) }
                        ?: n.nextMatch("支付成功", Regex("""^￥\d+(?:\.\d{1,2})?$"""), TEXT, 6)
                            ?.let { AutoValues.amountCents(it) }
                    emit(
                        ALIPAY, "支付宝", "AlipayPaySuccess",
                        cents,
                        TransactionType.EXPENSE,
                        remark = "支付宝支付",
                        outAsset = n.afterAny("交易方式", "付款方式").orEmpty(),
                    )
                },
            ),
            // 账单详情：最全的一条，方向按页面显式标签判定
            AutoBillRule(
                pageType = "AlipayBillDetail",
                required = listOf(
                    anyOf(
                        exact("交易成功"), exact("支付成功"), exact("充值成功"), exact("代付成功"),
                        exact("免密支付成功"), exact("自动扣款成功"), exact("等待确认收货"),
                        exact("等待发货"), exact("等待对方确认收货"), exact("还款成功"),
                        exact("收款成功"), exact("已全额退款"), exact("领取中"),
                        // 退款/完成类的独立状态词：只认「已全额退款」会漏掉大部分退款账单
                        exact("已退款"), exact("退款成功"), exact("交易完成"),
                        exact("付款成功"), exact("支付完成"),
                        regex("""已退款\s*[（(]\s*[¥￥]\s*\d+(\.\d+)?\s*[）)]"""),
                    ),
                    anyOf(
                        exact("支付时间"), exact("创建时间"), exact("收款时间"),
                        exact("到账时间"), exact("时间"), regex(TIME_PATTERN.pattern),
                    ),
                    amount(),
                ),
                extract = { n ->
                    val income = n.exists("收款成功", "已收款", "已退款", "退款成功", "已全额退款")
                    val transfer = n.exists("提现到", "还款到", "余额充值") || n.find("提现金额") != null
                    emit(
                        ALIPAY, "支付宝", "AlipayBillDetail",
                        // 新版账单详情页没有「订单金额」标签：金额大字号就压在「交易成功」上方一行
                        // （真机截图：江苏师范大学 / -10.00 / 交易成功）。先按这个版式取。
                        n.amountBefore("交易成功")
                            ?: n.amountBefore("支付成功")
                            ?: n.amountAfter("订单金额")
                            ?: n.amountAfterAny("支付金额", "收款金额", "提现金额")
                            ?: n.firstAmount(strict = true),
                        when {
                            transfer -> TransactionType.TRANSFER
                            income -> TransactionType.INCOME
                            else -> TransactionType.EXPENSE
                        },
                        remark = n.afterAny(
                            "商品说明", "转账说明", "转账备注", "服务详情",
                            "提现说明", "红包说明", "充值说明",
                        ).orEmpty(),
                        merchant = n.after("收款方全称").orEmpty(),
                        outAsset = n.afterAny("付款方式", "收款方式")
                            ?.replace("查看扣款顺序", "")?.trim().orEmpty(),
                        inAsset = n.afterAny("提现到", "还款到").orEmpty(),
                        timeText = n.timeAfterAny(
                            "支付时间", "创建时间", "收款时间", "到账时间", "时间",
                        ).orEmpty(),
                    )
                },
            ),
        ),
    )

    // ── 云闪付 ──────────────────────────────────────────────────────────────

    private fun unionpay() = AutoPlatform(
        packageName = UNIONPAY,
        name = "云闪付",
        isCandidatePage = { className -> className != UNIONPAY_HOME },
        rules = listOf(
            // 转账详情（转出）
            AutoBillRule(
                pageType = "UnionpayTransferDetail",
                required = listOf(
                    anyOf(exact("账单详情"), exact("云闪付交易详情")),
                    exact("当前状态"),
                    exact("交易成功"),
                    exact("转账金额"),
                    anyOf(exact("转账到"), exact("收款方式")),
                    exact("订单时间"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        UNIONPAY, "云闪付", "UnionpayTransferDetail",
                        n.amountAfter("转账金额"),
                        TransactionType.TRANSFER,
                        remark = n.after("附言").orEmpty().ifBlank { "云闪付转账" },
                        outAsset = n.after("付款方式").orEmpty(),
                        inAsset = n.afterAny("转账到", "收款方式").orEmpty(),
                        timeText = n.timeAfter("订单时间").orEmpty(),
                    )
                },
            ),
            // 交易详情（短信/入账通知打开）：消费 / 入账 / 取款 三态
            AutoBillRule(
                pageType = "UnionpayMessageOrderDetail",
                required = listOf(
                    anyOf(exact("交易详情"), exact("银联交易详情")),
                    exact("卡号"),
                    exact("交易时间"),
                    exact("交易类别"),
                    amount(),
                ),
                extract = { n ->
                    val category = n.nextByType("交易类别", TEXT).orEmpty()
                    val type = when {
                        category.contains("入账") -> TransactionType.INCOME
                        category.contains("取款") -> TransactionType.TRANSFER
                        else -> TransactionType.EXPENSE
                    }
                    emit(
                        UNIONPAY, "云闪付", "UnionpayMessageOrderDetail",
                        n.firstAmount(strict = true),
                        type,
                        remark = n.after("附言").orEmpty()
                            .ifBlank { n.nextByType("收款方", TEXT).orEmpty() },
                        merchant = n.nextByType("收款方", TEXT).orEmpty(),
                        outAsset = n.nextByType("卡号", TEXT).orEmpty(),
                        timeText = n.timeAfter("交易时间").orEmpty(),
                    )
                },
            ),
            // 账单详情（消费）
            AutoBillRule(
                pageType = "UnionpayBillDetail",
                required = listOf(
                    anyOf(exact("账单详情"), exact("云闪付交易详情")),
                    anyOf(exact("当前状态"), exact("交易状态")),
                    anyOf(exact("交易成功"), exact("支付成功"), exact("已完成")),
                    exact("订单金额"),
                    anyOf(exact("订单时间"), exact("交易时间")),
                    amount(),
                ),
                extract = { n ->
                    // 云闪付金额常是 `-¥12.00` 整句，先按标签取，再退回整句正则
                    val cents = n.amountAfter("订单金额")
                        ?: n.firstRegex(Regex("""^-?¥\s*(\d{1,3}(?:,\d{3})*|\d+)(?:\.\d{1,2})?"""))
                            ?.let { AutoValues.amountCents(it) }
                    emit(
                        UNIONPAY, "云闪付", "UnionpayBillDetail",
                        cents,
                        TransactionType.EXPENSE,
                        remark = n.after("订单描述").orEmpty(),
                        outAsset = n.after("付款方式").orEmpty(),
                        timeText = n.timeAfterAny("订单时间", "交易时间").orEmpty(),
                        feeCents = n.amountAfter("优惠信息") ?: 0L,
                    )
                },
            ),
            // 转账成功页
            AutoBillRule(
                pageType = "UnionpayTransferSuccess",
                required = listOf(
                    exact("转账成功"),
                    exact("转账详情"),
                    exact("继续转账"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        UNIONPAY, "云闪付", "UnionpayTransferSuccess",
                        n.nextMatch("转账成功", Regex(AMOUNT_STRICT.pattern), TEXT, 6)
                            ?.let { AutoValues.amountCents(it) }
                            ?: n.firstAmount(strict = true),
                        TransactionType.TRANSFER,
                        remark = n.after("附言").orEmpty().ifBlank { "云闪付转账" },
                        outAsset = n.after("付款方式").orEmpty(),
                    )
                },
            ),
            // 支付成功页
            AutoBillRule(
                pageType = "UnionpayPaySuccess",
                required = listOf(
                    exact("支付成功"),
                    exact("付款方式"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        UNIONPAY, "云闪付", "UnionpayPaySuccess",
                        n.nextMatch("支付成功", Regex(AMOUNT_STRICT.pattern), TEXT, 6)
                            ?.let { AutoValues.amountCents(it) }
                            ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = "云闪付支付",
                        outAsset = n.after("付款方式").orEmpty(),
                    )
                },
            ),
        ),
    )

    // ── 京东 ────────────────────────────────────────────────────────────────

    private fun jingdong() = AutoPlatform(
        packageName = JD,
        name = "京东",
        rules = listOf(
            // 钱包账单 V2：列表/详情共用一页，金额在「京东平台商户 -xx.xx 交易成功」整句里
            AutoBillRule(
                pageType = "JingDongWalletBillDetailV2",
                required = listOf(
                    exact("账单详情"),
                    // 「支付立减」只是优惠来源，真机很多版本没有这个节点，
                    // 放成页面成立条件会让整条规则永不命中
                    anyOf(exact("交易成功"), contains("已退款")),
                    startsWith("创建时间"),
                    startsWith("总订单编号"),
                    amount(),
                ),
                extract = { n ->
                    val cents = n.firstRegex(
                        Regex("""(?:京东平台商户\s*)?(-?\d+(?:,\d{3})*\.\d{2})\s*(?:交易成功|已退款)"""),
                    )?.let { AutoValues.amountCents(it) }
                        ?: n.amountAfter("交易金额")
                        ?: n.firstAmount(strict = true)
                    emit(
                        JD, "京东", "JingDongWalletBillDetailV2",
                        cents,
                        TransactionType.EXPENSE,
                        remark = n.afterAny("服务详情", "交易说明").orEmpty().ifBlank { "京东购物" },
                        merchant = n.afterAny("商户名称", "交易对方").orEmpty(),
                        outAsset = n.nextByType("支付方式", TEXT)
                            .orEmpty().ifBlank { n.nextByType("付款方式", TEXT).orEmpty() },
                        timeText = n.firstRegex(
                            Regex("""\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}"""),
                        ).orEmpty(),
                        feeCents = n.firstRegex(Regex("""支付立减\s*(-?\d+(?:,\d{3})*\.\d{2})"""))
                            ?.let { AutoValues.amountCents(it) } ?: 0L,
                    )
                },
            ),
            // 钱包账单（第一版）：必须带「共 N 笔订单/共 N 件」，否则会和 V2 抢同一页
            AutoBillRule(
                pageType = "JingDongWalletBillDetail",
                required = listOf(
                    anyOf(exact("账单详情"), contains("账单详情")),
                    anyOf(exact("支付方式"), exact("付款方式")),
                    exact("创建时间"),
                    exact("总订单编号"),
                    anyOf(exact("交易成功"), startsWith("已退款(￥")),
                    anyOf(
                        regex("""共(\d+)笔订单"""),
                        regex("""同笔支付关联订单[(（]共\d+笔[)）]"""),
                        regex("""共\d+件"""),
                    ),
                    amount(),
                ),
                extract = { n ->
                    val cents = n.firstRegex(
                        Regex("""(?:京东平台商户\s*)?(-\d+(?:,\d{3})*\.\d{2})\s*(?:交易成功|已退款\(￥)"""),
                    )?.let { AutoValues.amountCents(it) }
                        ?: n.amountAfter("订单金额")
                        ?: n.firstAmount(strict = true)
                    emit(
                        JD, "京东", "JingDongWalletBillDetail",
                        cents,
                        TransactionType.EXPENSE,
                        remark = n.afterAny("服务详情", "商品说明").orEmpty().ifBlank { "京东购物" },
                        outAsset = n.afterAny("支付方式", "付款方式").orEmpty(),
                        timeText = n.firstRegex(
                            Regex("""\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}"""),
                        ).orEmpty(),
                        feeCents = n.amountAfter("支付立减") ?: 0L,
                    )
                },
            ),
            // 订单详情（App 内）：对齐钱迹 w9/c 的状态词，并补上真机常见的变体。
            // 钱迹还要求「数量 ×」，但很多版本订单页没有这个文案，不能当页面成立条件。
            AutoBillRule(
                pageType = "JingDongOrderDetail",
                required = listOf(
                    contains("订单详情"),
                    anyOf(
                        exact("正在出库"), exact("商品出库"), exact("完成"), exact("订单状态 完成"),
                        exact("付款成功"), exact("等待收货"),
                        contains("待收货"), contains("已完成"), contains("等待处理"), contains("待发货"),
                    ),
                    anyOf(contains("实付款"), contains("合计")),
                    anyOf(exact("订单编号"), contains("订单编号")),
                ),
                extract = { n ->
                    emit(
                        JD, "京东", "JingDongOrderDetail",
                        n.amountAfterAny("实付款", "实付款：", "合计"),
                        TransactionType.EXPENSE,
                        remark = n.afterAny("商品名称", "商品详情").orEmpty().ifBlank { "京东购物" },
                        outAsset = n.nextByType("支付方式", TEXT).orEmpty(),
                        timeText = n.timeAfterAny("支付时间", "支付时间：", "下单时间", "下单时间：").orEmpty(),
                        feeCents = n.amountAfter("共减") ?: 0L,
                    )
                },
            ),
            // 订单详情（Web 版，字段带冒号，不能用 exact）
            AutoBillRule(
                pageType = "JingDongOrderWeb",
                required = listOf(
                    exact("订单详情"),
                    anyOf(exact("已完成"), exact("等待处理")),
                    exact("订单编号："),
                    exact("下单时间："),
                    exact("支付时间："),
                    exact("实付款："),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        JD, "京东", "JingDongOrderWeb",
                        n.amountAfter("实付款：") ?: n.amountAfter("实付款")
                            ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = n.afterAny("商品名称", "店铺").orEmpty().ifBlank { "京东购物" },
                        merchant = n.afterAny("店铺", "商家").orEmpty(),
                        outAsset = n.nextByType("支付方式：", TEXT).orEmpty(),
                        timeText = n.timeAfterAny("支付时间：", "下单时间：").orEmpty(),
                    )
                },
            ),
            // 爱回收：回收成功是收入
            AutoBillRule(
                pageType = "JingDongAihuishouOrderDetail",
                required = listOf(
                    contains("订单详情"),
                    exact("回收成功啦"),
                    exact("合计收款"),
                    exact("订单编号"),
                    exact("收款方式"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        JD, "京东", "JingDongAihuishouOrderDetail",
                        n.amountAfter("合计收款") ?: n.firstAmount(strict = true),
                        TransactionType.INCOME,
                        remark = n.afterAny("旧机信息", "回收商品").orEmpty().ifBlank { "爱回收" },
                        merchant = "爱回收",
                        inAsset = n.nextByType("收款银行", TEXT)
                            .orEmpty().ifBlank { n.nextByType("收款方式", TEXT).orEmpty() },
                        timeText = n.timeAfterAny("回收时间", "时间").orEmpty(),
                    )
                },
            ),
            // 支付成功页：整句「商户 ¥xx.xx」；排除微信支付页
            AutoBillRule(
                pageType = "JingDongPaySuccess",
                required = listOf(
                    exact("支付成功"),
                    regex("""^(?!微信支付)(.+?)¥(\d+(?:,\d{3})*(?:\.\d{1,2})?).*$"""),
                ),
                extract = { n ->
                    val text = n.firstRegex(Regex("""^(?!微信支付)(.+?)¥(\d+(?:,\d{3})*(?:\.\d{1,2})?).*$"""))
                    val merchant = text?.let { Regex("""^(?!微信支付)(.+?)¥""").find(it)?.groupValues?.get(1) }.orEmpty()
                    val cents = text?.let {
                        Regex("""¥(\d+(?:,\d{3})*(?:\.\d{1,2})?)""").find(it)?.groupValues?.get(1)
                    }?.let { AutoValues.amountCents(it) }
                        ?: n.amountAfterAny("实付款", "支付金额")
                        ?: n.firstAmount(strict = true)
                    emit(
                        JD, "京东", "JingDongPaySuccess",
                        cents,
                        TransactionType.EXPENSE,
                        remark = "京东购物",
                        merchant = merchant,
                        feeCents = n.amountAfter("共优惠") ?: 0L,
                    )
                },
            ),
        ),
    )

    // ── 美团 ────────────────────────────────────────────────────────────────

    private fun meituan() = AutoPlatform(
        packageName = MEITUAN,
        name = "美团",
        rules = listOf(
            // 外卖/跑腿：必须同时有「支付方式 + 实付」，列表卡片不会同时出现这两个
            AutoBillRule(
                pageType = "MeiTuanDeliveryDetail",
                required = listOf(
                    anyOf(
                        contains("订单已完成"),
                        regex(""".*已提前\d+分钟送达"""),
                        regex(""".*预计送达"""),
                        contains("订单已取消"),
                        contains("跑腿"),
                    ),
                    exact("支付方式"),
                    amount(),
                ),
                extract = { n ->
                    val inline = n.firstRegex(
                        Regex("""[(（]实付[¥￥](\d{1,9}(?:\.\d{1,2})?)[)）]"""),
                    )?.let { Regex("""(\d{1,9}(?:\.\d{1,2})?)""").find(it)?.groupValues?.get(1) }
                    emit(
                        MEITUAN, "美团", "MeiTuanDeliveryDetail",
                        inline?.let { AutoValues.amountCents(it) }
                            ?: n.amountAfterAny("实付", "实付款", "合计"),
                        TransactionType.EXPENSE,
                        remark = n.after("商品费用").orEmpty().ifBlank { "美团外卖" },
                        outAsset = n.nextByType("支付方式", TEXT).orEmpty(),
                        timeText = n.timeAfterAny("下单时间", "订单时间").orEmpty(),
                        feeCents = n.firstRegex(Regex("""已优惠[¥]?(\d+(?:\.\d{1,2})?)元?"""))
                            ?.let { Regex("""(\d+(?:\.\d{1,2})?)""").find(it)?.groupValues?.get(1) }
                            ?.let { AutoValues.amountCents(it) } ?: 0L,
                    )
                },
            ),
            // 电影票
            AutoBillRule(
                pageType = "MeiTuanMovieDetail",
                required = listOf(
                    anyOf(exact("放映结束"), regex(""".*\d{2}:\d{2}开场.*""")),
                    anyOf(exact("展开取票码"), exact("取电影票")),
                    exact("订单详情"),
                    exact("实付金额："),
                    regex("""^[¥￥]?\d{1,9}(?:\.\d{1,2})?\s*\(\d+张电影票\)\s*元?$"""),
                ),
                extract = { n ->
                    val ticket = n.firstRegex(Regex("""^[¥￥]?\d{1,9}(?:\.\d{1,2})?\s*\(\d+张电影票\)\s*元?$"""))
                        ?.let { Regex("""\d{1,9}(?:\.\d{1,2})?""").find(it)?.value }
                    emit(
                        MEITUAN, "美团", "MeiTuanMovieDetail",
                        n.amountAfter("实付金额：")
                            ?: ticket?.let { AutoValues.amountCents(it) },
                        TransactionType.EXPENSE,
                        remark = n.afterAny("影片", "电影名称").orEmpty().ifBlank { "美团电影" },
                        merchant = n.afterAny("影院", "影城").orEmpty(),
                        outAsset = n.afterAny("支付方式", "付款方式").orEmpty(),
                        timeText = n.timeAfter("订单时间：").orEmpty(),
                    )
                },
            ),
            // 团购（到店）
            AutoBillRule(
                pageType = "MeiTuanGrouponDetail",
                required = listOf(
                    exact("已使用"),
                    exact("实付"),
                    exact("团购详情"),
                    exact("使用须知"),
                    exact("有效日期"),
                    exact("使用时间"),
                    exact("订单信息"),
                ),
                extract = { n ->
                    emit(
                        MEITUAN, "美团", "MeiTuanGrouponDetail",
                        n.amountAfter("实付"),
                        TransactionType.EXPENSE,
                        remark = n.afterAny("套餐", "商品名称").orEmpty().ifBlank { "美团团购" },
                        merchant = n.afterAny("商家", "店铺名称").orEmpty(),
                        timeText = n.timeAfterAny("下单时间：", "订单时间：").orEmpty(),
                    )
                },
            ),
            // 代金券详情
            AutoBillRule(
                pageType = "MeiTuanVoucherDetail",
                required = listOf(
                    anyOf(exact("已使用"), exact("待到店使用")),
                    exact("实付"),
                    exact("使用须知"),
                    exact("有效日期"),
                    exact("使用时间"),
                ),
                extract = { n ->
                    emit(
                        MEITUAN, "美团", "MeiTuanVoucherDetail",
                        n.amountAfter("实付"),
                        TransactionType.EXPENSE,
                        remark = "美团代金券",
                        outAsset = n.afterAny("支付方式", "付款方式").orEmpty(),
                        timeText = n.timeAfterAny("下单时间：", "订单时间：").orEmpty(),
                    )
                },
            ),
            // 买单成功（到店付款）
            AutoBillRule(
                pageType = "MeiTuanVoucherSuccessDetail",
                required = listOf(
                    exact("买单成功！"),
                    anyOf(exact("实付金额："), contains("实付金额")),
                    exact("复制"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        MEITUAN, "美团", "MeiTuanVoucherSuccessDetail",
                        n.amountAfter("实付金额：")
                            ?: n.amountAfter("实付金额")
                            ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = n.afterAny("商户名称", "商家").orEmpty().ifBlank { "美团买单" },
                        merchant = n.afterAny("商户名称", "商家").orEmpty(),
                        timeText = n.timeAfterAny("消费时间：", "消费时间").orEmpty(),
                        feeCents = n.amountAfter("共省：") ?: n.amountAfter("共省") ?: 0L,
                    )
                },
            ),
            // 充电宝
            AutoBillRule(
                pageType = "MeiTuanChargeOrderDetail",
                required = listOf(
                    anyOf(exact("订单完成"), contains("订单完成")),
                    contains("充电宝已归还"),
                    exact("订单金额"),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        MEITUAN, "美团", "MeiTuanChargeOrderDetail",
                        n.amountBefore("元") ?: n.amountAfter("订单金额"),
                        TransactionType.EXPENSE,
                        remark = "美团充电宝",
                    )
                },
            ),
            // 单车
            AutoBillRule(
                pageType = "MeiTuanBikeOrderDetail",
                required = listOf(
                    regex("""已支付\d+(?:\.\d{1,2})?元"""),
                    exact("费用申诉"),
                    regex("""骑行\d+分\d+秒"""),
                ),
                extract = { n ->
                    val paid = n.firstRegex(Regex("""已支付(\d+(?:\.\d{1,2})?)元"""))
                        ?.let { Regex("""(\d+(?:\.\d{1,2})?)""").find(it)?.groupValues?.get(1) }
                    emit(
                        MEITUAN, "美团", "MeiTuanBikeOrderDetail",
                        paid?.let { AutoValues.amountCents(it) },
                        TransactionType.EXPENSE,
                        remark = "美团单车",
                        outAsset = n.afterAny("支付方式", "付款方式").orEmpty(),
                        timeText = n.firstRegex(
                            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}"""),
                        ).orEmpty(),
                    )
                },
            ),
            // 支付成功页
            AutoBillRule(
                pageType = "MeiTuanPaySuccess",
                required = listOf(
                    exact("支付成功"),
                    regex("""支付成功\s*¥\d+(?:\.\d{1,2})?"""),
                ),
                extract = { n ->
                    val inline = n.firstRegex(Regex("""支付成功\s*¥(\d+(?:\.\d{1,2})?)"""))
                        ?.let { Regex("""¥(\d+(?:\.\d{1,2})?)""").find(it)?.groupValues?.get(1) }
                    emit(
                        MEITUAN, "美团", "MeiTuanPaySuccess",
                        inline?.let { AutoValues.amountCents(it) }
                            ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = n.afterAny("商户名称", "商家").orEmpty().ifBlank { "美团支付" },
                        merchant = n.afterAny("商户名称", "商家").orEmpty(),
                        outAsset = n.nextByType("支付方式", TEXT).orEmpty(),
                    )
                },
            ),
            // 钱包账单详情
            AutoBillRule(
                pageType = "MeiTuanWalletBillDetail",
                required = listOf(
                    exact("账单详情"),
                    anyOf(
                        exact("支付成功"), exact("先用后付自动扣款成功"), exact("极速支付成功"),
                        regex("""已退款\d+(?:\.\d{1,2})?元"""),
                    ),
                    exact("支付方式"),
                ),
                extract = { n ->
                    emit(
                        MEITUAN, "美团", "MeiTuanWalletBillDetail",
                        n.amountBefore("支付成功")
                            ?: n.amountBefore("先用后付自动扣款成功")
                            ?: n.amountAfter("订单金额")
                            ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = n.after("账单详情").orEmpty().ifBlank { "美团" },
                        outAsset = n.nextByType("支付方式", TEXT).orEmpty(),
                        timeText = n.timeAfterAny("下单时间", "下单时间：").orEmpty(),
                    )
                },
            ),
            // 团购订单详情（列表/详情通用，放在最后）
            AutoBillRule(
                pageType = "MeiTuanOrderDetail",
                required = listOf(
                    anyOf(exact("已消费"), exact("已完成"), exact("待评价"), contains("已消费")),
                    exact("实付"),
                    exact("订单信息"),
                ),
                extract = { n ->
                    emit(
                        MEITUAN, "美团", "MeiTuanOrderDetail",
                        n.amountAfter("实付"),
                        TransactionType.EXPENSE,
                        remark = n.after("已消费").orEmpty().ifBlank { "美团团购" },
                        timeText = n.timeAfterAny("下单时间：", "订单时间：").orEmpty(),
                    )
                },
            ),
        ),
    )

    // ── 淘宝 ────────────────────────────────────────────────────────────────

    /**
     * 淘宝：金额必须靠「实付款」后面 2 个 TextView 内的 ¥xx 取值。
     *
     * 页面上同时有「商品价格 / 优惠 / 实付款」多个数字，宽松取值会把原价记成实付；
     * 店铺名在状态词**上方**（钱迹 PrevTextNotEmpty），不能用 after 往后取。
     */
    private fun taobao() = AutoPlatform(
        packageName = TAOBAO,
        name = "淘宝",
        rules = listOf(
            // 话费充值单
            AutoBillRule(
                pageType = "TaoBaoOrderDetailHuafei",
                required = listOf(
                    startsWith("话费充值-"),
                    exact("实付款"),
                ),
                extract = { n ->
                    emit(
                        TAOBAO, "淘宝", "TaoBaoOrderDetailHuafei",
                        n.nextMatch("实付款", Regex(AMOUNT_STRICT.pattern), TEXT, 3)
                            ?.let { AutoValues.amountCents(it) }
                            ?: n.amountAfter("实付款"),
                        TransactionType.EXPENSE,
                        remark = n.findStarts("话费充值-")?.text.orEmpty(),
                        timeText = n.timeAfterAny("付款时间", "创建时间", "下单时间").orEmpty(),
                    )
                },
            ),
            // 普通订单：状态词、实付款、金额节点三者同时存在才算订单详情
            AutoBillRule(
                pageType = "TaoBaoOrderDetailSuccess",
                required = listOf(
                    exact("实付款"),
                    anyOf(exact("交易成功"), exact("已发货"), exact("待发货")),
                    regex(TAOBAO_AMOUNT.pattern),
                ),
                extract = { n ->
                    val amountNode = n.all.firstOrNull { TAOBAO_AMOUNT.matches(it.text) }
                    val statusNode = n.find("交易成功") ?: n.find("已发货") ?: n.find("待发货")
                    // 钱迹的 PrevTextNotEmpty：状态词上方是店铺名、金额上方是商品名，
                    // 都要跳过「实付款」等 20 个按钮/状态词，也要跳过金额自身
                    fun previousMeaningful(anchor: AutoNode?): String {
                        if (anchor == null) return ""
                        return n.all.asSequence()
                            .filter { it.index < anchor.index }
                            .map { it.text }
                            .filter {
                                it !in TAOBAO_NOISE && !TAOBAO_AMOUNT.matches(it) && !AutoValues.isAmount(it)
                            }
                            .lastOrNull().orEmpty()
                    }
                    emit(
                        TAOBAO, "淘宝", "TaoBaoOrderDetailSuccess",
                        n.nextMatch("实付款", TAOBAO_AMOUNT, TEXT, 2)
                            ?.let { AutoValues.amountCents(it) }
                            ?: amountNode?.let { AutoValues.amountCents(it.text) },
                        TransactionType.EXPENSE,
                        remark = previousMeaningful(amountNode).ifBlank { "淘宝购物" },
                        merchant = previousMeaningful(statusNode),
                        timeText = n.timeAfterAny("创建时间", "付款时间", "下单时间").orEmpty()
                            .ifBlank {
                                n.all.map { it.text }.firstOrNull { AutoValues.isTime(it) }.orEmpty()
                            },
                    )
                },
            ),
        ),
    )

    // ── 拼多多 ──────────────────────────────────────────────────────────────

    private fun pinduoduo() = AutoPlatform(
        packageName = PDD,
        name = "拼多多",
        rules = listOf(
            // 先用后付下单弹窗：文案有两种（「先用后付下单成功」/ 结算页「先用后付」）
            AutoBillRule(
                pageType = "PddPayFirstDialog",
                required = listOf(
                    anyOf(contains("先用后付下单成功"), contains("先用后付")),
                    contains("自动付款"),
                ),
                extract = { n ->
                    val text = n.findContains("自动付款")?.text.orEmpty()
                    val cents = Regex("""自动付款¥(\d+(?:\.\d{1,2})?)""")
                        .find(text)?.groupValues?.get(1)
                        ?.let { AutoValues.amountCents(it) }
                        ?: n.amountAfter("先用后付下单，实付")
                    emit(
                        PDD, "拼多多", "PddPayFirstDialog",
                        cents,
                        TransactionType.EXPENSE,
                        remark = "先用后付",
                        outAsset = n.afterAny("支付方式", "付款方式").orEmpty(),
                    )
                },
            ),
            // 钱包账单详情
            AutoBillRule(
                pageType = "PddWalletBillDetail",
                required = listOf(
                    exact("账单详情"),
                    exact("当前状态"),
                    exact("支付成功"),

                ),
                extract = { n ->
                    emit(
                        PDD, "拼多多", "PddWalletBillDetail",
                        n.amountAfter("订单金额") ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = n.after("商品详情").orEmpty(),
                        // 钱包账单页没有稳定的「商户」标签，商品详情前一位常是订单号，
                        // 不能拿它当商户名，否则账户匹配会被带偏
                        merchant = "",
                        outAsset = n.nextByType("支付方式", TEXT).orEmpty(),
                        timeText = n.timeAfter("支付时间").orEmpty(),
                        feeCents = n.amountAfter("优惠") ?: 0L,
                    )
                },
            ),
            // 订单详情：三种「实付」文案都覆盖；金额只从实付整句取，不用 firstAmount
            AutoBillRule(
                pageType = "PddOrderDetail",
                required = listOf(
                    anyOf(
                        startsWith("下单时间："), startsWith("拼单时间："), startsWith("成交时间："),
                    ),
                    anyOf(
                        exact("交易成功"), exact("商家已发货"), startsWith("已发货"), exact("拼团中"),
                        startsWith("待收货"), exact("待发货"), startsWith("运输中"), startsWith("已签收"),
                        startsWith("充值到账"), startsWith("打包"),
                    ),
                    // 钱迹还要求「申请退款/取消订单/申请售后」之一，但订单处于不同状态时
                    // 这组按钮并不总在首屏（真机订单详情页就常常读不到），不能当硬条件
                    startsWith("商品名称："),
                    amount(),
                ),
                extract = { n ->
                    val paid = n.firstRegex(Regex("""实付[：:]?[^,]*?,?(\d+(?:\.\d+)?)元"""))
                        ?: n.firstRegex(Regex("""实付[：:，,\s]*(\d+(?:\.\d+)?)元"""))
                        ?: n.firstRegex(Regex("""实付\s+(\d+(?:\.\d+)?)元"""))
                    val cents = paid?.let {
                        Regex("""(\d+(?:\.\d+)?)""").findAll(it).lastOrNull()?.groupValues?.get(1)
                    }?.let { AutoValues.amountCents(it) }
                    emit(
                        PDD, "拼多多", "PddOrderDetail",
                        cents,
                        TransactionType.EXPENSE,
                        remark = n.findStarts("商品名称：")?.text
                            ?.removePrefix("商品名称：")?.trim().orEmpty().ifBlank { "拼多多购物" },
                        outAsset = n.nextByType("支付方式：", TEXT).orEmpty(),
                        timeText = n.timeAfterAny("下单时间：", "拼单时间：", "成交时间：").orEmpty(),
                        feeCents = n.amountAfter("平台优惠") ?: n.amountAfter("店铺优惠") ?: 0L,
                    )
                },
            ),
        ),
    )

    // ── 抖音 ────────────────────────────────────────────────────────────────

    private fun douyin() = AutoPlatform(
        packageName = DOUYIN,
        name = "抖音",
        rules = listOf(
            // 订单详情页：钱迹 g9/a 只有 4 个抖音页面（支付成功 / 钱包账单 / 转账两态），
            // **没有订单详情规则**。抖音的订单详情要在支付完成之后才能看到，
            // 这里按它的「订单详情 + 状态 + 金额」版式单独补一条。
            AutoBillRule(
                pageType = "DouyingOrderDetail",
                required = listOf(
                    anyOf(
                        contains("订单详情"), contains("商品订单"),
                        contains("交易凭证"), contains("订单信息"),
                    ),
                    anyOf(
                        contains("支付成功"), contains("已完成"), contains("待发货"),
                        contains("已发货"), contains("交易成功"), contains("待收货"),
                    ),
                    amount(),
                ),
                extract = { n ->
                    emit(
                        DOUYIN, "抖音", "DouyingOrderDetail",
                        n.amountBefore("支付成功")
                            ?: n.amountAfterAny("实付款", "实付金额", "支付金额", "订单金额")
                            ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = n.afterAny("商品订单", "商品订单：", "商品名称")
                            .orEmpty().ifBlank { "抖音购物" },
                        merchant = n.afterAny("商家", "店铺").orEmpty(),
                        outAsset = n.afterAny("支付方式", "付款方式")
                            .orEmpty().removePrefix("抖音支付").trim('（', '）', '(', ')'),
                        timeText = n.timeAfterAny("支付时间", "下单时间").orEmpty(),
                        feeCents = n.amountAfter("抖音支付优惠") ?: 0L,
                    )
                },
            ),
            // 支付成功页（FlattenUIText：抖音用 Lynx 渲染）
            AutoBillRule(
                pageType = "DouyingPaySuccess",
                required = listOf(
                    anyOf(exact("支付成功"), startsWith("支付成功")),
                    anyOf(
                        exact("支付方式"), contains("支付方式"),
                        exact("支付时间"), contains("支付时间"),
                    ),
                    regex(AMOUNT_STRICT.pattern),
                ),
                extract = { n ->
                    emit(
                        DOUYIN, "抖音", "DouyingPaySuccess",
                        n.nextMatch("支付成功", Regex(AMOUNT_STRICT.pattern), FLATTEN, 6)
                            ?.let { AutoValues.amountCents(it) }
                            ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = "抖音支付",
                        outAsset = n.nextByType("支付方式", FLATTEN)
                            .orEmpty().ifBlank { n.nextByType("支付方式", TEXT).orEmpty() },
                        timeText = n.timeAfter("支付时间").orEmpty(),
                    )
                },
            ),
            // 钱包账单详情
            AutoBillRule(
                pageType = "DouyingWalletBillDetail",
                required = listOf(
                    anyOf(contains("详情页"), contains("申请电子交易凭证"), contains("点击展开"), contains("账单详情")),
                    // 抖音把标题与金额画在同一行很常见（「支付成功 ¥30.00」），exact 会漏
                    contains("支付成功"),
                    anyOf(startsWith("支付时间"), contains("支付时间")),
                    regex(AMOUNT_STRICT.pattern),
                ),
                extract = { n ->
                    val payment = n.firstRegex(Regex("""^抖音支付[（(](.+)[）)]$"""))
                        ?.let { Regex("""[（(](.+)[）)]""").find(it)?.groupValues?.get(1) }
                        ?: n.after("支付方式")?.removePrefix("抖音支付").orEmpty()
                    emit(
                        DOUYIN, "抖音", "DouyingWalletBillDetail",
                        // 金额大字在状态上方；首页级的「抖音支付优惠」不能当成交金额
                        n.amountBefore("支付成功")
                            ?: n.amountAfter("商品订单")
                            ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = n.afterAny("商品订单", "商品订单：").orEmpty().ifBlank { "抖音购物" },
                        merchant = n.before("支付成功").orEmpty(),
                        outAsset = payment.trim('（', '）', '(', ')'),
                        timeText = (
                            n.timeAfter("支付时间")
                                ?: n.firstRegex(Regex("""^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}$"""))
                        ).orEmpty(),
                        feeCents = n.amountAfter("抖音支付优惠") ?: 0L,
                    )
                },
            ),
            // 转账已收款（收款方）
            AutoBillRule(
                pageType = "DouyingTransferReceived",
                required = listOf(
                    exact("转账详情"),
                    anyOf(regex("""^(.+)已收款$"""), exact("已收款")),
                    exact("转账时间"),
                    amount(),
                ),
                extract = { n ->
                    val title = n.firstRegex(Regex("""^(.+)已收款$"""))
                        ?.let { Regex("""^(.+)已收款$""").find(it)?.groupValues?.get(1) }
                    emit(
                        DOUYIN, "抖音", "DouyingTransferReceived",
                        n.amountAfter("转账金额") ?: n.firstAmount(strict = true),
                        TransactionType.INCOME,
                        remark = n.after("转账附言").orEmpty().ifBlank { "抖音转账" },
                        merchant = title.orEmpty(),
                        inAsset = n.afterAny("收款方式", "到账方式").orEmpty(),
                        timeText = n.timeAfterAny("收款时间", "转账时间").orEmpty(),
                    )
                },
            ),
            // 转账待收款（付款方）：页面有「提醒对方收款」，不是收入
            AutoBillRule(
                pageType = "DouyingTransferWaiting",
                required = listOf(
                    exact("转账详情"),
                    anyOf(regex("""^待\s*(.+)收款$"""), regex("""^待\s+(.+)$""")),
                    exact("转账时间"),
                    amount(),
                ),
                extract = { n ->
                    val title = n.firstRegex(Regex("""^待\s*(.+)收款$"""))
                        ?.let { Regex("""^待\s*(.+)收款$""").find(it)?.groupValues?.get(1) }
                    emit(
                        DOUYIN, "抖音", "DouyingTransferWaiting",
                        n.amountAfter("转账金额") ?: n.firstAmount(strict = true),
                        TransactionType.EXPENSE,
                        remark = n.after("转账附言").orEmpty().ifBlank { "抖音转账" },
                        merchant = title.orEmpty(),
                        outAsset = n.afterAny("支付方式", "付款方式").orEmpty(),
                        timeText = n.timeAfter("转账时间").orEmpty(),
                    )
                },
            ),
        ),
    )
}