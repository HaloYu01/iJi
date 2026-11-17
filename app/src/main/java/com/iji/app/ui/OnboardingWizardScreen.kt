package com.iji.app.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iji.app.ai.AiConfig
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.ui.ai.testConnection
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.mine.CheckItem
import com.iji.app.ui.mine.CheckStatus
import com.iji.app.ui.mine.KEY_NOTIFICATION
import com.iji.app.ui.mine.detectPermissions
import com.iji.app.ui.mine.runPermissionAction
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import top.yukonga.miuix.kmp.basic.ButtonDefaults

private const val STEP_COUNT = 5
private val ButtonShape = ContinuousRoundedRectangle(ButtonDefaults.CornerRadius)
private val DialogShape = ContinuousRoundedRectangle(22.dp)
private val DialogButtonShape = ContinuousRoundedRectangle(12.dp)

/**
 * 每步的「激活页头图」：放大的图标徽章在左、大标题 + 说明在右（左右结构，高度统一），
 * 可选一个角标（如「可跳过」）。
 * 文案从原 StepCard 上移到这里，步骤函数只负责表单本身。
 */
internal data class OnboardingStepMeta(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val badge: String? = null,
)

private val OnboardingSteps: List<OnboardingStepMeta> = listOf(
    OnboardingStepMeta(
        title = "先把它调成顺手的样子",
        desc = "主题和字号在完成配置后生效，下面的预览可以先看效果。",
        icon = Icons.Rounded.Palette,
    ),
    OnboardingStepMeta(
        title = "记账口径",
        desc = "这些决定统计与预算的区间，之后改动会让历史数据的看法变掉，先定好最省事。",
        icon = Icons.Rounded.Tune,
    ),
    OnboardingStepMeta(
        title = "AI 服务",
        desc = "整步留空就是纯本地识别，不发起任何网络请求；密钥只保存在本机。",
        icon = Icons.Rounded.AutoAwesome,
        badge = "可跳过",
    ),
    OnboardingStepMeta(
        title = "运行前提",
        desc = "不授权也能正常记账。以下只影响提醒、识屏确认与自动记账，按需开启即可。",
        icon = Icons.Rounded.Verified,
    ),
    OnboardingStepMeta(
        title = "记账提醒",
        desc = "默认关闭。通知是强打扰，由你决定要不要、以及几点收到。",
        icon = Icons.Rounded.NotificationsActive,
    ),
)

/**
 * 草稿的存档器：去系统设置授权时系统可能重建 Activity（配置变更 / 低内存），
 * 只用 `remember` 会让返回后掉回第一页、之前填的全丢。全部字段都是可存的基本类型，
 * 因此用 listSaver 平铺即可（顺序必须与 restore 严格一致）。
 */
private val OnboardingDraftSaver = listSaver<OnboardingDraft, Any>(
    save = { d ->
        listOf(
            d.theme, d.fontSizeSp, d.nickname, d.avatarUri, d.currencyCode,
            d.monthStartDay, d.weekStartDay, d.is24Hour,
            d.aiProviderId, d.aiKey, d.aiUrl, d.aiModel, d.aiEnabled,
            d.aiText, d.aiVision, d.aiCategory, d.aiAccount,
            d.reminderOn, d.reminderHour,
        )
    },
    restore = { v ->
        OnboardingDraft(
            theme = v[0] as String,
            fontSizeSp = v[1] as Float,
            nickname = v[2] as String,
            avatarUri = v[3] as String,
            currencyCode = v[4] as String,
            monthStartDay = v[5] as Int,
            weekStartDay = v[6] as Int,
            is24Hour = v[7] as Boolean,
            aiProviderId = v[8] as String,
            aiKey = v[9] as String,
            aiUrl = v[10] as String,
            aiModel = v[11] as String,
            aiEnabled = v[12] as Boolean,
            aiText = v[13] as Boolean,
            aiVision = v[14] as Boolean,
            aiCategory = v[15] as Boolean,
            aiAccount = v[16] as Boolean,
            reminderOn = v[17] as Boolean,
            reminderHour = v[18] as Int,
        )
    },
)

/**
 * 开局引导配置页（5 步），视觉对标手机「激活页」：
 *
 * - 顶部一层极淡的蓝色环境光晕（仍是固定浅色，不跟随主题），给首屏空气感；
 * - 每步开头是「左图标 + 右标题」的头图，像系统激活页那样有仪式感；
 * - 五段进度条带弹簧动画：当前段加宽、已过段收窄成实色、未到段保持淡色；
 * - 步骤切换走 HorizontalPager（仅按钮驱动、禁用手势滑动防误触），页面带轻微淡出；
 * - 底部大号胶囊按钮：主按钮统一蓝 + 白字，首启最后一步是「开始使用」；
 *   提交期间显示「配置中…」（OnboardingApplier 要写 DataStore / Room / WorkManager）；
 * - 首启右上角「跳过」先弹页内轻量确认，再丢弃草稿；重配的「取消」不写任何东西、直接返回。
 *
 * 两种模式共用同一份向导、同一份草稿、同一个提交器：
 * - 首启：完成 = 提交草稿 + 写完成标记；跳过 = 确认后丢弃草稿 + 只写完成标记；两者都进主页。
 * - 重配：初值取当前配置，完成 = 全量覆盖现有（不动完成标记）+ 回主页；取消 = 不写任何东西。
 *
 * 页面固定浅色白底 / 黑字 / 蓝色强调，不跟随主题：主题与字号只在页内预览，
 * 点「开始使用」才写进 ThemeState / FontScaleState，避免选了深色就把引导页染黑。
 */
@Composable
fun OnboardingWizardScreen(
    reconfigure: Boolean,
    /** 「调整头像」子页裁剪完成的一次性回传；非 null 即写进草稿并立即消费 */
    avatarResult: String?,
    /** 草稿已应用回传结果后清空一次性结果，防止下次重组重复应用 */
    onAvatarResultConsumed: () -> Unit,
    /** 相册选图回调：宿主导航进「调整头像」子页缩放裁剪，引导页自己不落盘原图 */
    onPickAvatarImage: (Uri) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var step by rememberSaveable { mutableIntStateOf(0) }
    var draft by rememberSaveable(stateSaver = OnboardingDraftSaver) {
        mutableStateOf(OnboardingDraft.current(context))
    }

    // 「调整头像」子页裁剪完成回传的结果写进草稿后立即消费（一次性结果）。
    // 结果经 savedStateHandle 传递：向导被 AVATAR_EDIT 盖住期间不在组合里，
    // 回来重组后才收到，所以只能在这里收，不能在导航回调里直接写。
    LaunchedEffect(avatarResult) {
        val uri = avatarResult
        if (!uri.isNullOrBlank()) {
            draft = draft.copy(avatarUri = uri)
            onAvatarResultConsumed()
        }
    }
    var submitting by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var permissionItems by remember { mutableStateOf(detectPermissions(context)) }
    // 首启点「跳过」会丢弃已填的全部内容，先弹轻量确认再执行
    var showSkipConfirm by remember { mutableStateOf(false) }

    // 浅色页必须配深色状态栏图标；离开时恢复进入前的值，不影响其它页面
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }

    // 从权限检验页 / 系统设置返回时自动重查就绪度（同 PermissionCheckScreen 的 ON_RESUME 口径）
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionItems = detectPermissions(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 头像与「我的」页同一条链路：Photo Picker 先拿到原图 → 宿主导航进「调整头像」
    // 子页缩放 / 拖动 / 确定 → 裁好的 512 圆形图作为一次性结果带回草稿。
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        onPickAvatarImage(uri)
    }

    val notificationReady = permissionItems
        .firstOrNull { it.key == KEY_NOTIFICATION }?.status == CheckStatus.OK

    // 第 4 步的每一项直接进对应系统开关（不再跳到权限检验页）；通知权限在 13+ 走运行时弹窗，
    // 授权结果回来后整表重查，页内状态立刻更新
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { permissionItems = detectPermissions(context) }

    val onPermissionAction: (CheckItem) -> Unit = { item ->
        runPermissionAction(
            context = context,
            item = item,
            onRequestNotificationPermission = {
                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
            onMessage = { message -> snackbar.show(message) },
        )
    }

    fun finish() {
        if (submitting) return
        submitting = true
        val snapshot = draft
        scope.launch {
            OnboardingApplier.apply(context, snapshot)
            if (!reconfigure) {
                SettingsRepository.setIntAwait(
                    context,
                    SettingsRepository.KEY_ONBOARDING_STATE,
                    SettingsRepository.ONBOARDING_DONE,
                )
            }
            onFinish()
        }
    }

    fun skip() {
        if (submitting) return
        submitting = true
        scope.launch {
            SettingsRepository.setIntAwait(
                context,
                SettingsRepository.KEY_ONBOARDING_STATE,
                SettingsRepository.ONBOARDING_DONE,
            )
            onFinish()
        }
    }

    fun runTest() {
        if (testing) return
        val snapshot = draft
        val provider = AiConfig.providers.firstOrNull { it.id == snapshot.aiProviderId }
            ?: AiConfig.providers.first()
        val url = snapshot.aiUrl.trim().ifBlank { provider.defaultUrl }
        val model = snapshot.aiModel.trim().ifBlank { provider.defaultModel }
        testing = true
        testMessage = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                testConnection(url, snapshot.aiKey.trim(), model)
            }
            testing = false
            testMessage = outcome.message
        }
    }

    val update: ((OnboardingDraft) -> OnboardingDraft) -> Unit = { transform ->
        draft = transform(draft)
    }

    // 系统返回键：跳过确认弹窗打开时先关弹窗；否则只回到「进入引导前的上一个页面」，
    // 不在向导内部倒退、更不退回第一页：
    // - 重配：来源页是引导教程页，直接 onCancel 出栈回去；
    // - 首启：上一页是已经同意过的隐私政策页，不能回退，也不该借返回键跳到主页，
    //   因此这里只消费返回事件；退出请用右上角明确的「跳过」。
    BackHandler(enabled = true) {
        if (showSkipConfirm) {
            showSkipConfirm = false
        } else if (reconfigure) {
            onCancel()
        }
    }

    // 步骤推进只由底部按钮驱动（禁用手势滑动防误触），这里把 step 同步成 pager 的动画目标
    val pagerState = rememberPagerState(initialPage = 0) { STEP_COUNT }
    LaunchedEffect(step) {
        pagerState.animateScrollToPage(step)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingPalette.White),
    ) {
        // 顶部一层极淡的蓝色环境光 + 两团柔光圆，超出屏幕的部分被裁掉，营造「激活页」的空气感
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to OnboardingPalette.Blue.copy(alpha = 0.10f),
                            0.30f to OnboardingPalette.Blue.copy(alpha = 0.04f),
                            0.65f to OnboardingPalette.White,
                            1f to OnboardingPalette.White,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 72.dp, y = (-96).dp)
                .size(240.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(OnboardingPalette.Blue.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-64).dp, y = 36.dp)
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(OnboardingPalette.Blue.copy(alpha = 0.08f), Color.Transparent),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                // ImmersiveRoutes 已登记：顶部安全区由本页自己消化（不加 ijiRoute 的外层 padding）
                .statusBarsPadding()
                .navigationBarsPadding()
                // adjustResize + edge-to-edge 下 IME inset 全靠这里消化：不补的话，
                // 填昵称 / API Key / 接口地址时键盘会盖住输入框和底部按钮
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentedProgress(step)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${step + 1}/$STEP_COUNT",
                    color = OnboardingPalette.Black55,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                // 跳过 / 取消：胶囊文字按钮，激活页右上角的惯用位置
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(OnboardingPalette.Blue.copy(alpha = 0.07f))
                        .clickable {
                            // 重配模式的「取消」不写任何东西，直接返回；首启的「跳过」丢内容，先确认
                            if (reconfigure) onCancel() else showSkipConfirm = true
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = if (reconfigure) "取消" else "跳过",
                        color = OnboardingPalette.Black70,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f),
            ) { page ->
                // 与当前页的偏离量（0 = 正中），用于切页时的轻微淡出
                val pageOffset =
                    ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - pageOffset.coerceIn(0f, 1f) * 0.55f }
                        .verticalScroll(rememberScrollState()),
                ) {
                    StepHero(OnboardingSteps[page])
                    when (page) {
                        0 -> StepPersonalize(
                            draft = draft,
                            update = update,
                            onPickAvatar = {
                                avatarLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )

                        1 -> StepAccounting(draft = draft, update = update)

                        2 -> StepAi(
                            draft = draft,
                            update = update,
                            onClearKey = { draft = draft.copy(aiKey = "") },
                            testing = testing,
                            testMessage = testMessage,
                            onTest = { runTest() },
                        )

                        3 -> StepPermissions(
                            items = permissionItems,
                            onAction = onPermissionAction,
                        )

                        else -> StepReminder(
                            draft = draft,
                            update = update,
                            notificationReady = notificationReady,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 第 2 步起都能退回上一步；第一页没有「上一步」（入口页）
                if (step > 0) {
                    SecondaryButton(
                        text = "上一步",
                        onClick = { if (!submitting) step -= 1 },
                        modifier = Modifier.weight(1f),
                    )
                }
                PrimaryButton(
                    // 提交期间 OnboardingApplier 要写 DataStore / Room / WorkManager，
                    // 文案直接表达「正在生效」，防止这 1~2 秒看起来像没反应
                    text = when {
                        submitting -> "配置中…"
                        step == STEP_COUNT - 1 -> if (reconfigure) "完成" else "开始使用"
                        else -> "下一步"
                    },
                    onClick = {
                        if (!submitting) {
                            if (step == STEP_COUNT - 1) finish() else step += 1
                        }
                    },
                    modifier = Modifier.weight(if (step > 0) 2f else 1f),
                )
            }
        }

        if (showSkipConfirm) {
            SkipConfirmDialog(
                onConfirm = {
                    showSkipConfirm = false
                    skip()
                },
                onDismiss = { showSkipConfirm = false },
            )
        }
    }
}

/** 顶部五段进度条：当前段加宽、已过段收窄成实色、未到段保持淡色，全部带动画；没有任何步骤名。 */
@Composable
private fun SegmentedProgress(step: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(STEP_COUNT) { index ->
            val finished = index < step
            val active = index == step
            val width by animateDpAsState(
                targetValue = when {
                    active -> 36.dp
                    finished -> 28.dp
                    else -> 20.dp
                },
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "onboardingProgress$index",
            )
            val color by animateColorAsState(
                targetValue = if (active || finished) OnboardingPalette.Blue else OnboardingPalette.FaintBlue,
                animationSpec = tween(200),
                label = "onboardingProgressColor$index",
            )
            Box(
                Modifier
                    .width(width)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * 首启「跳过」的轻量确认卡。引导页是固定浅色页，不读主题的 Dialog 组件——
 * 深色主题下会跳出一张系统色的黑弹窗；这里用页内调色板自绘，视觉与向导一致。
 * 点遮罩 / 「继续填写」/ 返回键 = 放弃跳过；「跳过」确认后才走原 skip() 路径。
 */
@Composable
private fun SkipConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = DialogShape,
                    ambientColor = OnboardingPalette.Shadow,
                    spotColor = OnboardingPalette.Shadow,
                )
                .clip(DialogShape)
                .background(OnboardingPalette.White)
                .padding(24.dp),
        ) {
            Text(
                "跳过引导配置？",
                color = OnboardingPalette.Black,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "已填的内容不会保存，应用将使用默认配置；之后可在「我的」页随时重新配置。",
                color = OnboardingPalette.Black55,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 安全动作做主按钮：确认弹窗的强调点永远在「不丢内容」那一侧
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(DialogButtonShape)
                        .background(OnboardingPalette.Blue)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "继续填写",
                        color = OnboardingPalette.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(DialogButtonShape)
                        .border(1.dp, OnboardingPalette.BorderBlue, DialogButtonShape)
                        .background(OnboardingPalette.White)
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "跳过",
                        color = OnboardingPalette.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(ButtonShape)
            .background(OnboardingPalette.Blue)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = OnboardingPalette.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(ButtonShape)
            .border(1.dp, OnboardingPalette.BorderBlue, ButtonShape)
            .background(OnboardingPalette.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = OnboardingPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
