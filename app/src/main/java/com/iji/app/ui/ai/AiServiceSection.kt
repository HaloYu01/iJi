package com.iji.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ai.AiConfig
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.ijiButtonFocusRing
import com.iji.app.ui.components.ijiPrimaryButtonColors
import com.iji.app.ui.components.ijiIconFocusRing
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import java.util.concurrent.TimeUnit

/**
 * AI 服务配置区（原「AI 记账设置」整页内容，现在内嵌进 AI 记账页）。
 *
 * 为什么内嵌：配置只有 3 个字段，改完 Key 还要退回上一页再进记账，是纯多余跳转。
 * 这一段是可折叠区块——配置完收起，记账时只占顶部一行状态。
 *
 * 保存策略：Key / 模型 / 地址需要点「保存配置」（避免半截地址就生效）；
 * 总开关在页面顶部即时生效，不在这里。连接测试与保存同卡：测试用的是
 * 当前填写的内容，与是否已保存无关。
 */
@Composable
internal fun AiServiceSection(
    context: android.content.Context,
) {
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    var providerId by rememberSaveable { mutableStateOf(AiConfig.getProvider(context)) }
    var apiKey by rememberSaveable { mutableStateOf(AiConfig.getApiKey(context)) }
    var apiUrl by rememberSaveable { mutableStateOf(AiConfig.getApiUrl(context)) }
    var model by rememberSaveable { mutableStateOf(AiConfig.getModel(context)) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TestOutcome?>(null) }

    val provider = AiConfig.providers.firstOrNull { it.id == providerId } ?: AiConfig.providers.first()
    // 输入框允许留空，实际请求用该服务的默认值（与 resolvedUrl / resolvedModel 同一套兜底）
    val effectiveUrl = apiUrl.trim().ifBlank { provider.defaultUrl }
    val effectiveModel = model.trim().ifBlank { provider.defaultModel }

    fun selectProvider(next: AiConfig.Provider) {
        if (next.id == providerId) return
        providerId = next.id
        // 非自定义服务自动回填默认参数；自定义清空，避免把上一家的地址当自己的
        apiUrl = if (next.id == "custom") "" else next.defaultUrl
        model = if (next.id == "custom") "" else next.defaultModel
        keyVisible = false
        testResult = null
        dirty = true
    }

    fun save() {
        if (provider.id == "custom" && apiUrl.isBlank()) {
            snackbar.show("自定义服务需要填写 API 地址")
            return
        }
        if (provider.id == "custom" && model.isBlank()) {
            snackbar.show("自定义服务需要填写模型名称")
            return
        }
        val finalUrl = apiUrl.trim().ifBlank { provider.defaultUrl }
        val finalModel = model.trim().ifBlank { provider.defaultModel }
        AiConfig.setProvider(context, providerId)
        AiConfig.setApiKey(context, apiKey.trim())
        AiConfig.setApiUrl(context, finalUrl)
        AiConfig.setModel(context, finalModel)
        apiUrl = finalUrl
        model = finalModel
        keyVisible = false
        testResult = null
        dirty = false
    }

    fun startTest() {
        if (testing) return
        testing = true
        testResult = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                testConnection(effectiveUrl, apiKey.trim(), effectiveModel)
            }
            testing = false
            testResult = outcome
        }
    }

    // 与设置页同款节奏：标题→内容 10dp（SmallTitle 自带），组与组之间 12dp（下方 Spacer）。
    // 原来标题与内容块是 spacedBy 的平级子项，标题自带 6dp + 组间距 10dp = 16dp，松了一倍。
    Column {
        SmallTitle(text = "模型服务", insideMargin = PaddingValues(bottom = 10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 平板 / 横屏三列，手机两列：不靠拉宽单个卡片填满空间
            val columns = if (maxWidth >= 520.dp) 3 else 2
            ProviderGrid(
                providers = AiConfig.providers,
                selectedId = providerId,
                columns = columns,
                onSelect = { selectProvider(it) },
            )
        }

        Spacer(Modifier.height(12.dp))
        SmallTitle(text = "接口配置", insideMargin = PaddingValues(bottom = 10.dp))
        IjiFormCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                TextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        dirty = true
                        testResult = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "API Key",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    cornerRadius = Radius.md,
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .ijiIconFocusRing()
                                .clickable(
                                    role = Role.Button,
                                    onClick = { keyVisible = !keyVisible },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (keyVisible) MiuixIcons.Hide else MiuixIcons.Show,
                                contentDescription = if (keyVisible) "隐藏密钥" else "显示密钥",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Text("密钥仅保存在本机", fontSize = 11.sp, color = TextSecondary)

                Spacer(Modifier.height(10.dp))
                TextField(
                    value = model,
                    onValueChange = {
                        model = it
                        dirty = true
                        testResult = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = if (provider.defaultModel.isBlank()) "模型名称（必填）" else "模型名称（留空用默认）",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    cornerRadius = Radius.md,
                )

                Spacer(Modifier.height(10.dp))
                TextField(
                    value = apiUrl,
                    onValueChange = {
                        apiUrl = it
                        dirty = true
                        testResult = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = if (provider.defaultUrl.isBlank()) "API 地址（必填）" else "API 地址",
                    useLabelAsPlaceholder = true,
                    // 地址五六十个字符：单行会截成「…/v4/」，看着像填错
                    singleLine = false,
                    maxLines = 2,
                    minLines = 1,
                    cornerRadius = Radius.md,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                )

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { save() },
                        enabled = dirty,
                        colors = ijiPrimaryButtonColors(),
                        modifier = Modifier
                            .weight(1f)
                            .ijiButtonFocusRing(shape = ContinuousRoundedRectangle(ButtonDefaults.CornerRadius)),
                    ) {
                        Text("保存配置", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { startTest() },
                        enabled = !testing && apiKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier
                            .weight(1f)
                            .ijiButtonFocusRing(shape = ContinuousRoundedRectangle(ButtonDefaults.CornerRadius)),
                    ) {
                        Text(if (testing) "测试中…" else "测试连接")
                    }
                }
                if (apiKey.isBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("填写 API Key 后可测试", fontSize = 11.sp, color = TextSecondary)
                }
                testResult?.let {
                    Spacer(Modifier.height(10.dp))
                    TestResultRow(it)
                }
            }
        }
    }
}

/**
 * 服务选择网格：选中态用主色描边 + 勾，比只有小圆点的单选项更容易一眼看出「现在用的是哪家」。
 * 只放服务名——域名/默认参数在选择后自动填进下方输入框，写在这里只是噪音。
 */
@Composable
private fun ProviderGrid(
    providers: List<AiConfig.Provider>,
    selectedId: String,
    columns: Int,
    onSelect: (AiConfig.Provider) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        providers.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { provider ->
                    ProviderTile(
                        provider = provider,
                        selected = provider.id == selectedId,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(provider) },
                    )
                }
                // 最后一行不足列数时补空位，避免单个卡片被拉满整行
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProviderTile(
    provider: AiConfig.Provider,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.xl)
    val titleColor = if (selected) MiuixPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) MiuixPrimary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = 0.5.dp,
                color = if (selected) MiuixPrimary.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = shape,
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MiuixPrimary)
            }
        }
    }
}

/** 测试结果：成功 / 失败只区分语义，文案由请求结果给出。开局引导页复用同一份连接测试。 */
internal sealed interface TestOutcome {
    val message: String

    data class Success(override val message: String) : TestOutcome

    data class Failure(override val message: String) : TestOutcome
}

@Composable
private fun TestResultRow(result: TestOutcome) {
    val color = if (result is TestOutcome.Success) MiuixPrimary else AppleRed
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        Text(result.message, fontSize = 13.sp, lineHeight = 19.sp, color = color)
    }
}

/**
 * 测试连接：发一条 max_tokens=5 的最小请求。
 *
 * 刻意不复用业务请求：连接测试要**快**（10s/15s 超时），配错了立刻能看到；
 * 业务请求是 30s/60s，拿它测试会让「填错了」的反馈慢得离谱。
 */
internal fun testConnection(url: String, apiKey: String, model: String): TestOutcome {
    if (url.isBlank()) return TestOutcome.Failure("请先填写 API 地址")
    if (model.isBlank()) return TestOutcome.Failure("请先填写模型名称")
    if (apiKey.isBlank()) return TestOutcome.Failure("请先填写 API Key")
    return try {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 5)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            })
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val start = System.currentTimeMillis()
        val response = client.newCall(request).execute()
        val elapsed = System.currentTimeMillis() - start
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful && text.contains("\"choices\"")) {
            TestOutcome.Success("连接成功 · $model · ${elapsed}ms")
        } else {
            TestOutcome.Failure(
                "连接失败：HTTP ${response.code} " + text.take(120).replace("\n", " "),
            )
        }
    } catch (e: Exception) {
        TestOutcome.Failure("连接失败：" + (e.message ?: e.javaClass.simpleName))
    }
}
