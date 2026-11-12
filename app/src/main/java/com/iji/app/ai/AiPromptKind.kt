package com.iji.app.ai

/**
 * AI 提示词注册表：**每一种提示词都必须有真实调用点**，否则就是假开关。
 *
 * 参照 AutoAccounting 的「提示词管理」（`settings_ai_assistant.xml` 的四条提示词），
 * 但按 iji 自己的解析管线收口——那边的资产映射 / 分类识别 / 月度总结是三个独立的后端能力，
 * 我们这边对应的是：
 *
 * | 种类 | 触发时机 | 调用点 |
 * |---|---|---|
 * | [BILL_TEXT] | 自然语言 / 短信 / 通知原文 → 一笔账单 | [AiBookkeepingService.parseBill] |
 * | [BILL_IMAGE] | 小票 / 账单截图 → 一笔账单（视觉模型） | [AiBookkeepingService.parseBillFromImage] |
 * | [CATEGORY] | 模型给的分类不在你的分类表里时，让它从**真实分类**里重选 | [AiBookkeepingService] 的二次映射阶段 |
 * | [ACCOUNT] | 付款方式线索对不上**真实账户**时，做一次账户对齐 | 同上 |
 * | [SUMMARY] | 统计页「AI 小结」按当期数据写点评 | [AiSummaryService] |
 *
 * 存储键为 `ai_prompt_<key>`，留空 = 用 [defaultText]（与旧版「留空保存 = 使用默认」一致）。
 * 旧版本只有 `ai_prompt_text` / `ai_prompt_image` 两个键，升级后仍按 [AiConfig.getPromptCustom] 回退读取。
 */
enum class AiPromptKind(
    /** 存储键后缀，同时是路由参数（不要改已上线的值） */
    val key: String,
    /** 列表与编辑页标题 */
    val title: String,
    /** 一句话说明「这段提示词管什么」 */
    val usage: String,
    /** 生效条件：展示在编辑页顶部，让用户知道改完什么时候会用到 */
    val effect: String,
    /** 默认提示词：每个字都参与行为，改之前先看单测 */
    val defaultText: String,
) {

    BILL_TEXT(
        key = "bill_text",
        title = "账单识别（文本）",
        usage = "自然语言、短信、支付通知原文 → 一笔账单",
        effect = "AI 记账页「文本识别用 AI」打开、且已配置 Key 时优先生效；AI 失效时降级本地规则",
        // 与早先硬编码版本逐字一致：老用户升级后解析行为不变
        defaultText = """
            你是一个记账助手。把用户输入的自然语言解析成一笔账单。
            返回 JSON 格式：
            {
              "type": "expense" 或 "income" 或 "transfer",
              "category": 分类名称（如：餐饮、交通、购物、工资、理财等）,
              "amount": 金额数字（元）,
              "note": 备注说明,
              "account": 账户（如：微信、支付宝、现金、银行卡）,
              "tags": ["标签1", "标签2"]
            }
            只返回 JSON，不要其他内容。
        """.trimIndent(),
    ),

    BILL_IMAGE(
        key = "bill_image",
        title = "小票识别（图片）",
        usage = "小票、账单截图 → 一笔账单（视觉模型）",
        effect = "开启「图片识别用 AI」时直接把图片交给视觉模型（失败回落本地 OCR）；关闭则图片永不出设备",
        defaultText = """
            你是一个小票识别助手。从用户提供的小票图片中识别出一笔消费记录。
            返回 JSON 格式：
            {
              "type": "expense",
              "category": 分类名称,
              "amount": 总金额数字,
              "note": 商家名称或备注,
              "account": "",
              "tags": []
            }
            只返回 JSON，不要其他内容。
        """.trimIndent(),
    ),

    CATEGORY(
        key = "category",
        title = "分类识别",
        usage = "从你的真实分类里挑一个，避免模型自造分类名",
        effect = "「AI 识别分类」打开、且模型给的分类不在你的分类表里时补一次请求",
        defaultText = """
            你是账单分类助手。从「可选分类」里为这笔账单挑一个最合适的分类。
            输入是一个 JSON，包含 type（expense / income / transfer）、amount、note、account 等字段，
            以及 categories（逗号分隔的可选分类名，来自用户自己的分类表）。
            规则：
            1. 只能从 categories 里选一个名字，逐字照抄，不得改写、翻译、拼接或新增；
            2. 依据 note 的语义判断，不要求关键词完全匹配；
            3. type 是 income 时优先收入语义词（工资、奖金、报销、利息、红包）；
            4. 多个分类都说得通时选更具体的那个；
            5. 拿不准就输出「其他」；
            6. 只输出分类名本身，不要解释、不要 JSON、不要标点。
        """.trimIndent(),
    ),

    ACCOUNT(
        key = "account",
        title = "账户识别",
        usage = "把付款方式线索对齐到你已有的账户",
        effect = "「账户识别用 AI」打开、且线索对不上任何已有账户名时补一次请求",
        defaultText = """
            你是账户匹配助手。把账单里的付款方式线索映射到用户已有的账户。
            输入是一个 JSON，包含 hint（原始线索，可能为空）、note、type，
            以及 accounts（逗号分隔的合法账户名）。
            规则：
            1. 只能从 accounts 里选一个，逐字照抄；不得拼接、翻译或臆造；
            2. 依次尝试：完全相同 → 忽略大小写相同 → 包含关系（线索是账户名的一部分，或反之）；
            3. 「零钱」不等于「零钱通」，「余额」不等于「余额宝」，「储蓄卡」不等于「信用卡」，不可互相替代；
            4. 多个候选并列时选名称更长、更具体的那个；
            5. 无法确定时输出空字符串；
            6. 只输出账户名本身（或空），不要解释、不要 JSON。
        """.trimIndent(),
    ),

    SUMMARY(
        key = "summary",
        title = "AI 摘要",
        usage = "统计页「AI 小结」按当期数据写一段点评",
        effect = "已配置 Key 时点「AI 小结」立即生成；数据只发当期汇总与预计算指标，不发原始流水",
        defaultText = """
            你是用户的记账分析师。根据某个周期的汇总数据写一段点评，让用户看清三件事：钱花在哪、和上一期或预算比怎么样、下一步改什么。
            输入 JSON 字段：dimension（周/月/年）、period（周期名）、expense、income、balance、billCount、
            expensePerBill（支出笔均）、dailyAvgExpense（日均支出）、prevExpense 与 expenseChangePct（上期支出与支出环比%）、
            incomeChangePct（收入环比%）、budget 与 budgetUsedPct（预算额度与已用%，可能没有）、
            topCategories（数组：name、amount、share 为占总支出百分比，按金额降序）。
            金额单位均为元。所有衍生指标都已经算好，直接引用即可，禁止自行计算，也禁止写出输入里没有的数字。
            要求：
            1. 直接输出正文，不要标题、不要 markdown 符号，不超过 180 字；
            2. 每个判断必须挂一个输入里的具体数字；没有数字支撑的结论不要写；
            3. 按顺序讲三件事：花在哪（share 最高的分类）、变化（环比或预算消耗，对应字段存在才写）、一条建议；
            4. 建议必须落到具体分类：点出它的金额与占比，给一个本周或本月内能执行的动作；禁止「理性消费」「注意控制开支」这类没有对象的空话；
            5. 不臆测数据外的信息，不给投资建议；billCount 为 0 时只回一句「本期还没有记账，先记几笔再来看看」。
        """.trimIndent(),
    ),
    ;

    companion object {
        /** 按存储键查（路由参数用）；未知键返回 null，不抛异常 */
        fun ofKey(key: String?): AiPromptKind? = entries.firstOrNull { it.key == key }
    }
}
