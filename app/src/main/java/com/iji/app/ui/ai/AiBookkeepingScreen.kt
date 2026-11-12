package com.iji.app.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.iji.app.ai.AiConfig
import com.iji.app.ai.AiPromptKind
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiFormDivider
import com.iji.app.ui.components.IjiSwitchRow
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.mine.MineSubPage
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * AI 记账：AI 的**配置与开关**收口页。
 *
 * 记账输入区（文本 / 拍照 / 相册 / 语音 → 解析 → 确认卡）已迁入「自动记账」页——
 * 那边是「采集 + 记账」的动作现场，本页只管 AI 怎么参与：模型服务、识别开关、提示词。
 *
 * 三段：
 * 1. **AI 状态卡**：总开关 + 内嵌的模型服务配置（折叠，需要时才展开）；
 * 2. **AI 功能**：文本用 AI、图片用 AI、AI 识别分类、账户识别用 AI；
 * 3. **提示词与识别规则**入口。
 *
 * 原来的「AI 记账设置」整页已删除——配置只有 5 个字段，单独一页只会让「改 Key → 回去记账」
 * 多两次跳转。提示词仍是独立页（内容长，且要整页编辑）。
 *
 * 原则不变：未配 Key / 关掉开关时**不发起任何网络请求**，图片不出设备。
 */
@Composable
fun AiBookkeepingScreen(
    onBack: () -> Unit,
    onNavigatePrompts: () -> Unit,
) {
    val ctx = LocalContext.current

    var aiReady by remember { mutableStateOf(AiConfig.aiReady(ctx)) }
    var aiEnabled by remember { mutableStateOf(AiConfig.isEnabled(ctx)) }
    var useAi by remember { mutableStateOf(AiConfig.parseMode(ctx) == AiConfig.MODE_AI) }
    var visionEnabled by remember { mutableStateOf(AiConfig.isVisionEnabled(ctx)) }
    var categoryEnabled by remember { mutableStateOf(AiConfig.isCategoryEnabled(ctx)) }
    var accountEnabled by remember { mutableStateOf(AiConfig.isAccountEnabled(ctx)) }
    var configExpanded by rememberSaveable { mutableStateOf(false) }

    // 从提示词页 / 系统设置回来时刷新：刚配上 Key 就能立刻生效，清空 Key 立刻退回本地
    LifecycleResumeEffect(Unit) {
        aiReady = AiConfig.aiReady(ctx)
        aiEnabled = AiConfig.isEnabled(ctx)
        useAi = AiConfig.parseMode(ctx) == AiConfig.MODE_AI
        visionEnabled = AiConfig.isVisionEnabled(ctx)
        categoryEnabled = AiConfig.isCategoryEnabled(ctx)
        accountEnabled = AiConfig.isAccountEnabled(ctx)
        onPauseOrDispose { }
    }

    /**
     * 开关「文本识别用 AI」：开 = 文本解析直接调用 AI，关 = 纯本地规则。
     * AI 不可用或调用失败时由 [AiBookkeepingService] 统一降级本地，界面不需要额外状态。
     *
     * 不判 Key：子开关在 [aiReady] 为 false 时整行禁用（拨不到）；选了 AI 之后 Key 被清掉，
     * 由 [AiConfig.parseMode] 回落本地，界面显示的永远是实际生效的方式。
     */
    fun toggleUseAi(on: Boolean) {
        useAi = on
        AiConfig.setParseMode(ctx, if (on) AiConfig.MODE_AI else AiConfig.MODE_LOCAL)
    }

    val statusText = when {
        !aiEnabled -> "已关闭 · 仅本地识别"
        !aiReady -> "未填 Key · 暂走本地"
        useAi -> AiConfig.resolvedModel(ctx)
        else -> "文本用本地规则"
    }

    MineSubPage(title = "AI 记账", onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalDimens.current.screenPadding)
                .padding(top = ijiTopBarContentPadding(), bottom = 24.dp),
            // 组间距与设置页一致；标题→卡片由 SmallTitle 自己给 10dp，不再叠加在这里
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 1. AI 状态：总开关 + 配置展开 ──
            IjiFormCard {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("AI 识别", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(2.dp))
                            Text(statusText, fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = aiEnabled,
                            onCheckedChange = { on ->
                                aiEnabled = on
                                AiConfig.setEnabled(ctx, on)
                                // 关掉总开关后 parseMode 会自动回落本地，界面同步过来；
                                // aiReady 同步刷新，下面四个子开关的禁用态立刻跟着变
                                aiReady = AiConfig.aiReady(ctx)
                                useAi = AiConfig.parseMode(ctx) == AiConfig.MODE_AI
                            },
                        )
                    }
                    IjiFormDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("服务配置", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IjiTextAction(
                            text = if (configExpanded) "收起" else "展开",
                            compact = true,
                            onClick = { configExpanded = !configExpanded },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = configExpanded) {
                AiServiceSection(context = ctx)
            }

            // ── 2. AI 功能 ──
            // 子开关只在 AI 真正可用（总开关开 + 有 Key）时可拨；不可用时整行禁用并在
            // 说明里写明原因——摆一个拨了也不生效的开关，比禁用更误导。
            // checked 显示的是**实际生效值**：AI 不可用时显示关，恢复可用后按
            // 存储的偏好自动亮回（偏好本身不清除）。
            Column {
                SmallTitle(text = "AI 功能", insideMargin = PaddingValues(bottom = 10.dp))
                IjiFormCard {
                    IjiSwitchRow(
                        title = "文本识别用 AI",
                        desc = if (aiReady) {
                            "开启调用 AI 识别，关闭只用本地规则；AI 失效自动降级本地"
                        } else {
                            "AI 未启用 · 暂走本地识别"
                        },
                        checked = useAi,
                        onCheckedChange = { toggleUseAi(it) },
                        enabled = aiReady,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    IjiFormDivider()
                    IjiSwitchRow(
                        title = "图片识别用 AI",
                        desc = when {
                            !aiReady -> "AI 未启用 · 暂走本地识别"
                            visionEnabled -> "跳过本机 OCR 直接交给模型；失败自动回落本地识别"
                            else -> "只跑本机 OCR，图片不出设备"
                        },
                        checked = aiReady && visionEnabled,
                        onCheckedChange = { on ->
                            visionEnabled = on
                            AiConfig.setVisionEnabled(ctx, on)
                        },
                        enabled = aiReady,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    IjiFormDivider()
                    IjiSwitchRow(
                        title = "AI 识别分类",
                        desc = if (aiReady) "关闭时分类记为「其他」" else "AI 未启用 · 暂不生效",
                        checked = aiReady && categoryEnabled,
                        onCheckedChange = { on ->
                            categoryEnabled = on
                            AiConfig.setCategoryEnabled(ctx, on)
                        },
                        enabled = aiReady,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    IjiFormDivider()
                    IjiSwitchRow(
                        title = "账户识别用 AI",
                        desc = if (aiReady) "从已有账户中匹配付款方式" else "AI 未启用 · 暂不生效",
                        checked = aiReady && accountEnabled,
                        onCheckedChange = { on ->
                            accountEnabled = on
                            AiConfig.setAccountEnabled(ctx, on)
                        },
                        enabled = aiReady,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    IjiFormDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("提示词与识别规则", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "AI 提示词 ${AiPromptKind.entries.size} 类 · 本地识别规则",
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }
                        IjiTextAction(text = "编辑", compact = true, onClick = onNavigatePrompts)
                    }
                }
            }
        }
    }
}
