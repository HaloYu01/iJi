package com.iji.app.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.iji.app.ai.BillDraft
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.QuickBillWriter
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.theme.IjiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 识屏结果悬浮窗：在其他应用之上直接确认账单（`TYPE_APPLICATION_OVERLAY`）。
 * 详细 / 简洁两种排版由 [OverlayCard] 装入 [OverlayShell]，样式在「设置 → 识屏记账」切换。
 *
 * 关键约束：默认 `FLAG_NOT_FOCUSABLE` 时 TextField 拿不到输入法，行内编辑文本时由
 * [setWindowFocusable] 临时切成可聚焦、收起后立刻切回——不开对话框 / 新窗口。
 * 不加自动记账，金额/分类/账户仍要用户看一眼再点（宁可多点一下，不可记错账）。
 */
object BillOverlay {

    private const val TAG = "BillOverlay"

    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var params: WindowManager.LayoutParams? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openPermissionSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    /**
     * 显示确认卡片。
     *
     * @param detailed true = 详细模式（全字段可编辑），false = 简洁模式（只补备注 + 剔除）
     * @return 是否真的挂到了窗口上；false 时调用方应退回通知，不能假装展示过了
     */
    fun show(
        context: Context,
        drafts: List<BillDraft>,
        ledgerId: String,
        detailed: Boolean,
    ): Boolean {
        if (drafts.isEmpty()) return false
        if (!canShow(context)) {
            Log.i(TAG, "没有悬浮窗权限，跳过悬浮窗")
            return false
        }
        dismiss()

        val app = context.applicationContext
        val windowManager = app.getSystemService(WindowManager::class.java) ?: return false
        // owner / view 跨次复用：dismiss 只摘窗口不销毁实例，重复识屏时省一次冷启动
        val overlayOwner = owner ?: OverlayOwner().also { owner = it }
        val composeView = view ?: ComposeView(app).also { created ->
            created.setViewTreeLifecycleOwner(overlayOwner)
            created.setViewTreeViewModelStoreOwner(overlayOwner)
            created.setViewTreeSavedStateRegistryOwner(overlayOwner)
            view = created
        }
        composeView.setContent {
            IjiTheme {
                OverlayCard(
                    drafts = drafts,
                    ledgerId = ledgerId,
                    detailed = detailed,
                    onDismiss = { dismiss() },
                )
            }
        }
        val horizontalMargin = 12.dp.px(app)
        val p = WindowManager.LayoutParams(
            // 宽度 = 屏宽 − 左右各 12dp；卡片内部按这个宽度等比换算设计稿（927px 基准）
            app.resources.displayMetrics.widthPixels - horizontalMargin * 2,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不抢焦点（不挡输入法、不影响下方应用），但仍然收得到点击
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 固定贴屏幕顶端：比起初再往上 8dp（`statusBarHeight - 8dp`），
            // 卡片顶边的圆角刚好压住状态栏下沿一点点，视觉上更"贴顶"
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (statusBarHeightPx(app) - 8.dp.px(app)).coerceAtLeast(0)
        }
        params = p

        return runCatching {
            windowManager.addView(composeView, p)
            true
        }.onFailure {
            // 权限被收回、MIUI 额外开关没开时就是这里失败：调用方会退回通知，
            // 但日志必须留下原因，否则线上只能看到「没弹窗」而查不出为什么
            Log.w(TAG, "悬浮窗添加失败，退回通知", it)
            view = null
            owner = null
        }.getOrDefault(false)
    }

    fun dismiss() {
        val current = view ?: return
        val wm = current.context.getSystemService(WindowManager::class.java)
        runCatching { wm?.removeView(current) }
        // view / owner 留着给下一次 show 复用；窗口参数还原为不抢焦点
        params?.let { it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE }
    }

    /**
     * 行内文本编辑期间把窗口临时切成可聚焦（否则输入法弹不出来），
     * 收起编辑后必须调 [setWindowFocusable]`(false)` 还回去。
     */
    internal fun setWindowFocusable(focusable: Boolean) {
        val v = view ?: return
        val p = params ?: return
        p.flags = if (focusable) {
            p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching {
            v.context.getSystemService(WindowManager::class.java)?.updateViewLayout(v, p)
        }
    }

    /** 请求弹出输入法（窗口已可聚焦、TextField 已拿到焦点后调用） */
    internal fun showIme() {
        val v = view ?: return
        val imm = v.context.getSystemService(InputMethodManager::class.java) ?: return
        fun attempt() {
            runCatching { imm.showSoftInput(v.findFocus() ?: v, 0) }
        }
        // 焦点链与窗口 flag 生效都有几百毫秒级的延迟，分两次补试
        v.postDelayed({ attempt() }, 100)
        v.postDelayed({ attempt() }, 300)
    }

    internal fun hideIme() {
        val v = view ?: return
        val imm = v.context.getSystemService(InputMethodManager::class.java) ?: return
        runCatching { imm.hideSoftInputFromWindow(v.windowToken, 0) }
    }

    private fun Dp.px(context: Context): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** 状态栏高度：悬浮窗贴在状态栏下方，不遮住时间和状态图标 */
    private fun statusBarHeightPx(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
}

/** 悬浮窗里没有 Activity，Compose 需要的三个 owner 就地造一个 */
private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
}

/** 简洁模式最多展示的行数 */
private const val COMPACT_MAX_ROWS = 4

/**
 * 卡片主体：品牌行 + （详细模式）进度条 + 两种排版之一。
 *
 * 样式只在设置里切换，卡片内不提供入口：这里按 [detailed]（调起时读好的偏好）
 * 直接渲染对应排版。编辑状态（draftsState / detailedIndex / skipped）仍提在这一层，
 * 让详细模式的进度、简洁模式的剔除与两边的字段修改都落在同一份数据上。
 */
@Composable
private fun OverlayCard(
    drafts: List<BillDraft>,
    ledgerId: String,
    detailed: Boolean,
    onDismiss: () -> Unit,
) {
    // 当前弹出的所有笔；剔除只影响显示与写库，原列表不动
    var draftsState by remember(drafts) { mutableStateOf(drafts) }
    /** 详细模式的当前笔（保存/跳过推进） */
    var detailedIndex by remember(drafts) { mutableIntStateOf(0) }
    /** 简洁模式里被剔除的笔（识别错误的），写库时跳过 */
    var skipped by remember(drafts) { mutableStateOf(setOf<Int>()) }
    /** 非 null = 已完成写入（值 = 笔数），顶部徽章随之换成完成态 */
    var doneCount by remember { mutableStateOf<Int?>(null) }

    fun changeDraft(index: Int, draft: BillDraft) {
        draftsState = draftsState.mapIndexed { i, old -> if (i == index) draft else old }
    }

    OverlayShell(onDismissed = onDismiss) { close ->
        OverlayBrandRow(
            badge = when {
                doneCount != null -> "✓ 已记 $doneCount"
                detailed && draftsState.size > 1 -> "${detailedIndex + 1}/${draftsState.size}"
                !detailed -> "${draftsState.size - skipped.size} 笔"
                else -> null
            },
        )
        if (detailed && doneCount == null && draftsState.size > 1) {
            Spacer(Modifier.height(6.dp))
            // 跳过也算处理过了：进度反映的是「处理到第几笔」
            OverlayProgress(done = detailedIndex, total = draftsState.size)
        }
        Spacer(Modifier.height(10.dp))
        if (detailed) {
            DetailedOverlayCard(
                drafts = draftsState,
                initialIndex = detailedIndex,
                onDraftChange = { index, draft -> changeDraft(index, draft) },
                onIndexChange = { detailedIndex = it },
                onDone = { doneCount = it },
                ledgerId = ledgerId,
                onClose = close,
            )
        } else {
            CompactOverlayBody(
                drafts = draftsState,
                ledgerId = ledgerId,
                skipped = skipped,
                onSkip = { index -> skipped = skipped + index },
                onDraftChange = { index, draft -> changeDraft(index, draft) },
                onDone = { doneCount = it },
                onClose = close,
            )
        }
    }
}

/** 简洁模式主体：只读预览行 + 行内补备注 + 逐笔剔除，一键全部记录 */
@Composable
private fun CompactOverlayBody(
    drafts: List<BillDraft>,
    ledgerId: String,
    skipped: Set<Int>,
    onSkip: (Int) -> Unit,
    onDraftChange: (Int, BillDraft) -> Unit,
    onDone: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var writtenTotal by remember { mutableStateOf(0) }
    var allDone by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    /** 当前展开行内编辑备注的行（真实下标）；null = 没有行在编辑 */
    var editingRow by remember { mutableStateOf<Int?>(null) }

    // 剔除只影响显示与写库：带真实下标配对，× 与备注编辑都能定位回原列表
    val visible = drafts.mapIndexed { index, d -> index to d }.filter { it.first !in skipped }
    val shown = visible.take(COMPACT_MAX_ROWS)
    val overflow = visible.size - shown.size

    // 写完停一拍再收，让「已记 N 笔」被看见；比旧版的 1.2s 收得更快
    LaunchedEffect(allDone) {
        if (allDone) {
            delay(800)
            onClose()
        }
    }

    fun collapseEditing() {
        if (editingRow != null) {
            editingRow = null
            BillOverlay.setWindowFocusable(false)
            BillOverlay.hideIme()
        }
    }

    if (allDone) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✓",
                color = OverlayPalette.IncomeText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "已记 $writtenTotal 笔到「$ledgerId」",
                fontFamily = InterMedium,
                fontSize = 15.sp,
                color = OverlayPalette.Ink,
            )
        }
        return
    }

    shown.forEach { (index, draft) ->
        CompactRow(
            draft = draft,
            editing = editingRow == index,
            canSkip = visible.size > 1,
            onUpdate = { onDraftChange(index, it) },
            onEdit = {
                if (editingRow == index) {
                    collapseEditing()
                } else {
                    BillOverlay.setWindowFocusable(true)
                    editingRow = index
                }
            },
            onSkip = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                collapseEditing()
                onSkip(index)
            },
        )
        Spacer(Modifier.height(8.dp))
    }
    if (overflow > 0) {
        Text(
            text = "另有 $overflow 笔，进应用里核对",
            fontSize = 11.5.sp,
            color = OverlayPalette.Hint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    if (visible.isEmpty()) {
        Text(
            text = "识别结果已全部忽略",
            fontSize = 11.5.sp,
            color = OverlayPalette.Hint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }

    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth()) {
        FooterButton(
            text = if (visible.isEmpty()) "关闭" else "忽略",
            modifier = Modifier.weight(1f),
            container = Color.Transparent,
            content = OverlayPalette.Secondary,
            outlined = true,
            enabled = true,
            onClick = {
                collapseEditing()
                onClose()
            },
        )
        Spacer(Modifier.width(12.dp))
        FooterButton(
            text = when {
                busy -> "记录中…"
                visible.isEmpty() -> "没有可记的"
                else -> "记录全部 ${visible.size} 笔"
            },
            modifier = Modifier.weight(1.55f),
            container = OverlayPalette.Accent,
            content = OverlayPalette.OnAccent,
            outlined = false,
            enabled = !busy && visible.isNotEmpty(),
            onClick = {
                if (!busy && visible.isNotEmpty()) {
                    collapseEditing()
                    busy = true
                    scope.launch {
                        val ok = QuickBillWriter.addDrafts(visible.map { it.second }, ledgerId)
                        busy = false
                        if (ok > 0) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            writtenTotal = ok
                            allDone = true
                            onDone(ok)
                        } else {
                            onClose()
                        }
                    }
                }
            },
        )
    }
}

/**
 * 简洁模式的行：分类图标 + 分类/重复标记 + 账户·日期·备注预览 + 语义色金额。
 * 点击整行就地展开备注编辑；多笔时行尾 × 可剔除误识别的这笔。
 */
@Composable
private fun CompactRow(
    draft: BillDraft,
    editing: Boolean,
    canSkip: Boolean,
    onUpdate: (BillDraft) -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit,
) {
    val category = runCatching { CategoryCatalog.of(draft.categoryId) }.getOrNull()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (editing) OverlayPalette.Accent.copy(alpha = 0.06f)
                    else OverlayPalette.SurfaceRaised,
                )
                .border(
                    1.dp,
                    if (editing) OverlayPalette.Accent.copy(alpha = 0.45f)
                    else OverlayPalette.CardBorder,
                    RoundedCornerShape(14.dp),
                )
                .pressableScale(onClick = onEdit)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconImage(
                name = category?.name ?: "分类",
                color = category?.let { Color(it.color) } ?: OverlayPalette.PlaceholderDot,
                size = 22.dp,
                categoryId = draft.categoryId,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category?.name ?: "其他",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        color = OverlayPalette.Ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (draft.duplicateOfId != null) {
                        Spacer(Modifier.width(6.dp))
                        DuplicateBadge()
                    }
                }
                val meta = listOf(draft.account, "${draft.date.monthValue}/${draft.date.dayOfMonth}")
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                val sub = listOf(meta, draft.note).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp,
                        color = OverlayPalette.Secondary,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                // 统一走带符号口径：支出 -10.00、收入 +10.00、转账 =10.00，与详细模式一致
                text = OverlayFormat.amountLabel(draft),
                fontFamily = InterMedium,
                fontSize = 16.sp,
                color = amountColor(draft.type),
            )
            if (canSkip) {
                Spacer(Modifier.width(2.dp))
                // 28dp 命中区：悬浮窗空间有限，贴着 32dp 推荐下限的紧凑取舍
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSkip),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "×", fontSize = 16.sp, color = OverlayPalette.Tertiary)
                }
            }
        }
        if (editing) {
            Spacer(Modifier.height(6.dp))
            NoteInlineEditor(
                initial = draft.note,
                onCommit = { note ->
                    onUpdate(draft.copy(note = note))
                    onEdit()
                },
            )
        }
    }
}

/** 「疑似重复」标记：橙色小胶囊，比灰字更难漏看 */
@Composable
private fun DuplicateBadge() {
    Text(
        text = "疑似重复",
        fontSize = 9.5.sp,
        color = OverlayPalette.WarningInk,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayPalette.WarningBg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
