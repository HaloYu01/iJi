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
        effect = "AI 记账页「文本识别用 AI」打开、且已配置 Key 时生效；本地规则不受影响",
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
        effect = "本机 OCR 认不出金额、且「图片识别用 AI」打开时才生效（关闭则图片永不出设备）",
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
        effect = "已配置 Key 时点「AI 小结」立即生成；数据只发当期汇总，不发原始流水",
        defaultText = """
            你是一名记账助教。根据用户某个周期的收支数据，写一段 80~150 字的中文点评。
            输入是一个 JSON：dimension（周/月/年）、period（周期名）、expense、income、balance、
            billCount、prevExpense（上一周期支出，可能为 0）、
            topCategories（数组，每项含 name 与 amount，按金额降序）。
            要求：
            1. 直接输出正文，不要标题、不要 markdown、不要列表符号；
            2. 至少引用两个具体数字（金额或笔数），不要空泛；
            3. 先一句总览，再点出占比最高的分类，最后给一条可执行的建议；
            4. billCount 为 0 时只回一句「本期还没有记账，先记几笔再来看看」；
            5. 不要臆测数据里没有的信息，也不要给投资建议。
        """.trimIndent(),
    ),
    ;

    companion object {
        /** 按存储键查（路由参数用）；未知键返回 null，不抛异常 */
        fun ofKey(key: String?): AiPromptKind? = entries.firstOrNull { it.key == key }
    }
}
