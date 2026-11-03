package com.iji.app.ai

import android.content.Context
import com.iji.app.data.repository.SettingsRepository

/**
 * AI 配置存储（统一走 SettingsRepository / DataStore）。
 * 支持多家模型提供商：智谱GLM、OpenAI、DeepSeek、通义千问等。
 * 历史 "ai_config" SharedPreferences 会在首次访问 DataStore 时自动迁移。
 */
object AiConfig {
    private const val KEY_API_KEY = "api_key"
    private const val KEY_API_URL = "api_url"
    private const val KEY_MODEL = "model"
    private const val KEY_PROVIDER = "provider"

    /** 总开关：关闭后所有解析只走本机规则 / 本地 OCR，不再联网 */
    private const val KEY_ENABLED = "ai_enabled"

    /** 文本解析方式：local（默认，纯本机规则）/ ai（规则没命中时调大模型） */
    private const val KEY_PARSE_MODE = "ai_parse_mode"

    /** AI 功能细分开关（对应 AutoAccounting「AI 功能」里的 aiVisionRecognition / aiCategoryRecognition） */
    private const val KEY_VISION = "ai_vision_enabled"
    private const val KEY_CATEGORY = "ai_category_enabled"

    const val MODE_LOCAL = "local"
    const val MODE_AI = "ai"

    data class Provider(
        val id: String,
        val name: String,
        val defaultUrl: String,
        val defaultModel: String,
    )

    val providers = listOf(
        Provider("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash"),
        Provider("openai", "OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-chat"),
        Provider("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus"),
        Provider("moonshot", "Kimi", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k"),
        Provider("custom", "自定义", "", ""),
    )

    fun getProvider(context: Context): String =
        SettingsRepository.getString(context, KEY_PROVIDER, "zhipu")

    fun setProvider(context: Context, id: String) {
        SettingsRepository.setString(context, KEY_PROVIDER, id)
    }

    fun getApiKey(context: Context): String =
        SettingsRepository.getString(context, KEY_API_KEY, "")

    fun setApiKey(context: Context, key: String) {
        SettingsRepository.setString(context, KEY_API_KEY, key)
    }

    /**
     * 早期版本预热缓存时留下的错误默认地址：只到 `/api/paas/v4/`，缺 `/chat/completions`，
     * 拿它发请求必然 404。这里视同「未设置」，让 [resolvedUrl] 回落到服务默认值。
     */
    private const val LEGACY_BASE_URL = "https://open.bigmodel.cn/api/paas/v4/"

    fun getApiUrl(context: Context): String =
        SettingsRepository.getString(context, KEY_API_URL, "")
            .takeIf { it != LEGACY_BASE_URL }
            .orEmpty()

    fun setApiUrl(context: Context, url: String) {
        SettingsRepository.setString(context, KEY_API_URL, url)
    }

    fun getModel(context: Context): String =
        SettingsRepository.getString(context, KEY_MODEL, "")

    fun setModel(context: Context, model: String) {
        SettingsRepository.setString(context, KEY_MODEL, model)
    }

    fun isEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        SettingsRepository.setBoolean(context, KEY_ENABLED, enabled)
    }

    /** 图片是否允许直发视觉模型（关闭 = 只用本地 OCR，图不出设备） */
    fun isVisionEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_VISION, true)

    fun setVisionEnabled(context: Context, enabled: Boolean) {
        SettingsRepository.setBoolean(context, KEY_VISION, enabled)
    }

    /** AI 是否参与分类（关闭 = 分类落「其他」，由用户在记账面板里自己选） */
    fun isCategoryEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_CATEGORY, true)

    fun setCategoryEnabled(context: Context, enabled: Boolean) {
        SettingsRepository.setBoolean(context, KEY_CATEGORY, enabled)
    }

    /** AI 是否真的可用：总开关打开 **且** 有 Key（URL / 模型有内置默认值兜底） */
    fun aiReady(context: Context): Boolean =
        isEnabled(context) && getApiKey(context).isNotBlank()

    /**
     * 文本解析方式。
     *
     * 存的是用户的选择，返回的是**实际生效**的方式：选过 AI 但没配 Key（或关了总开关）时
     * 一律回落 [MODE_LOCAL] —— 否则界面上会留一个必然失败的选项。
     */
    fun parseMode(context: Context): String =
        resolveMode(SettingsRepository.getString(context, KEY_PARSE_MODE, MODE_LOCAL), aiReady(context))

    /**
     * 选择 + 可用性 → 实际生效的解析方式（纯函数，可单测）。
     *
     * 三条规则：
     * 1. **默认本地**：没选过（[chosen] 为 null/空/未知值）一律本地；
     * 2. **未配 AI 强制本地**：没有 Key 或关了总开关，即使选过 AI 也只在本机跑；
     * 3. 只有「选了 AI 且 AI 真的可用」才联网。
     */
    fun resolveMode(chosen: String?, aiReady: Boolean): String =
        if (chosen == MODE_AI && aiReady) MODE_AI else MODE_LOCAL

    fun setParseMode(context: Context, mode: String) {
        SettingsRepository.setString(context, KEY_PARSE_MODE, mode)
    }

    /**
     * 实际生效的接口地址 / 模型名。
     *
     * 存储里允许为空（用户只选了服务、没手填参数），此时回落到该服务的默认值；
     * 否则请求会带着空 URL 去发，只能靠异常兜底成本地解析——看起来像「配了也不生效」。
     */
    fun resolvedUrl(context: Context): String =
        getApiUrl(context).ifBlank { currentProvider(context)?.defaultUrl.orEmpty() }

    fun resolvedModel(context: Context): String =
        getModel(context).ifBlank { currentProvider(context)?.defaultModel.orEmpty() }

    private fun currentProvider(context: Context): Provider? {
        val id = getProvider(context)
        return providers.firstOrNull { it.id == id } ?: providers.firstOrNull()
    }

    // ---- 提示词：种类见 [AiPromptKind]，在「提示词与规则」页编辑，留空 = 用默认 ----

    /** 账户识别开关：关闭后不再为「账户线索」补一次映射请求（分类识别是另一个开关） */
    private const val KEY_ACCOUNT = "ai_account_enabled"

    fun isAccountEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_ACCOUNT, true)

    fun setAccountEnabled(context: Context, enabled: Boolean) {
        SettingsRepository.setBoolean(context, KEY_ACCOUNT, enabled)
    }

    private const val KEY_PROMPT_PREFIX = "ai_prompt_"

    /**
     * 旧版本只有文本 / 图片两段提示词，键名没有前缀。升级后照旧读得到，
     * 用户第一次保存时会把值迁到新键并清掉旧键（避免「新键清空后又冒出旧值」）。
     */
    private const val LEGACY_KEY_PROMPT_TEXT = "ai_prompt_text"
    private const val LEGACY_KEY_PROMPT_IMAGE = "ai_prompt_image"

    /** 兼容旧调用点 / 旧单测：默认提示词已下沉到 [AiPromptKind] */
    val DEFAULT_PROMPT_TEXT: String get() = AiPromptKind.BILL_TEXT.defaultText
    val DEFAULT_PROMPT_IMAGE: String get() = AiPromptKind.BILL_IMAGE.defaultText

    /** 用户自定义的提示词；留空表示「用默认」 */
    fun getPromptCustom(context: Context, kind: AiPromptKind): String {
        val stored = SettingsRepository.getString(context, KEY_PROMPT_PREFIX + kind.key, "")
        if (stored.isNotBlank()) return stored
        return when (kind) {
            AiPromptKind.BILL_TEXT -> SettingsRepository.getString(context, LEGACY_KEY_PROMPT_TEXT, "")
            AiPromptKind.BILL_IMAGE -> SettingsRepository.getString(context, LEGACY_KEY_PROMPT_IMAGE, "")
            else -> ""
        }
    }

    fun setPromptCustom(context: Context, kind: AiPromptKind, value: String) {
        SettingsRepository.setString(context, KEY_PROMPT_PREFIX + kind.key, value.trim())
        when (kind) {
            AiPromptKind.BILL_TEXT -> SettingsRepository.setString(context, LEGACY_KEY_PROMPT_TEXT, "")
            AiPromptKind.BILL_IMAGE -> SettingsRepository.setString(context, LEGACY_KEY_PROMPT_IMAGE, "")
            else -> Unit
        }
    }

    /** 是否在用自定义提示词（界面显示「默认 / 自定义」） */
    fun isPromptCustom(context: Context, kind: AiPromptKind): Boolean =
        getPromptCustom(context, kind).isNotBlank()

    /** 自定义为空 → 默认（纯函数，可单测） */
    fun resolvePrompt(custom: String, default: String): String = custom.ifBlank { default }

    /** 实际发给模型的提示词 */
    fun resolvedPrompt(context: Context, kind: AiPromptKind): String =
        resolvePrompt(getPromptCustom(context, kind), kind.defaultText)

    fun resolvedPromptText(context: Context): String =
        resolvedPrompt(context, AiPromptKind.BILL_TEXT)

    fun resolvedPromptImage(context: Context): String =
        resolvedPrompt(context, AiPromptKind.BILL_IMAGE)
}
