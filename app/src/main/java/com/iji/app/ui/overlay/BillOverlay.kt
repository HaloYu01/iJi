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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.iji.app.accessibility.CapturePreferences
import com.iji.app.ai.BillDraft
import com.iji.app.data.db.DbProvider
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.QuickBillWriter
import com.iji.app.reminder.SuperIslandNotifier
import com.iji.app.ui.theme.IjiTheme
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 识屏结果悬浮窗：在其他应用之上直接确认账单（参考 AutoAccounting 的 `FloatingTip`，
 * 同样是 `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE`）。
 *
 * 为什么要有它：Shizuku 截屏发生在**别的应用**之上，识别完如果只能发通知，
 * 用户还得点通知、跳回本应用、再确认——中间任何一步走神，这笔账就丢了。
 * 悬浮窗把「看一眼 + 点一下」压缩到支付完成后的那几秒里。
 *
 * 两种卡片样式（`CapturePreferences.overlayStyle`，另有超级岛通知模式不走本类）：
 * - **详细**：**固定贴屏幕顶端**的单笔确认卡，按 Figma「详细版页面」还原（见 [DetailedOverlayCard]）：
 *   类型 / 金额 / 账户 / 分类 / 日期 / 标签 / 备注 + 不计入预算、计入资产，保存后自动进下一笔；
 * - **简洁**：底部只读预览（分类 / 账户 / 日期 / 金额 / 备注），点一行补改备注。
 *
 * 编辑实现的关键约束：悬浮窗默认 `FLAG_NOT_FOCUSABLE`（不抢下方应用的焦点），
 * 但这样 TextField 拿不到输入法。所以行内编辑文本时由 [setWindowFocusable]
 * 临时把窗口切成可聚焦、收起后立刻切回——不开任何对话框 / 新窗口，反应最快。
 *
 * 其余取舍：
 * - 不加常驻悬浮球，只在识别出账单后出现，确认或忽略即消失；
 * - 不加「自动记账」，金额/分类/账户仍要用户看一眼再点（宁可多点一下，不可记错账）；
 * - 视图与 owner 跨次复用（dismiss 只摘窗不销毁），第二次弹出不再冷启动整套 Compose；
 * - 视图直接挂在应用进程上（不经 Service），少一层后台启动限制。
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
     * @param detailed true = 详细模式（全字段可编辑），false = 简洁模式（只补备注）
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

/** 简洁模式的行内编辑只有备注一种 */
private const val EDIT_NOTE = "note"

/** 简洁模式最多展示的行数 */
private const val COMPACT_MAX_ROWS = 4

@Composable
private fun OverlayCard(
    drafts: List<BillDraft>,
    ledgerId: String,
    detailed: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 卡片顶部可现场切换样式：详细 ↔ 简洁只换排版（已做的编辑保留），通知 = 收卡片改发超级岛。
    // 切换写回偏好，下一次识屏直接按新样式弹。
    var style by remember {
        mutableStateOf(if (detailed) CapturePreferences.OVERLAY_DETAILED else CapturePreferences.OVERLAY_COMPACT)
    }
    val isDetailed = style == CapturePreferences.OVERLAY_DETAILED
    var rows by remember(drafts) { mutableStateOf(drafts.take(COMPACT_MAX_ROWS)) }
    val rest = remember(drafts) { drafts.drop(COMPACT_MAX_ROWS) }

    // 详细版：独立卡片，严格按 Figma「详细版页面」还原（单笔逐条确认，保存后自动进下一笔）
    if (isDetailed) {
        DetailedOverlayCard(drafts = drafts, ledgerId = ledgerId, onDismiss = onDismiss)
        return
    }

    var writtenTotal by remember { mutableStateOf(0) }
    var allDone by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    /** 通知模式发送失败时提示留在卡片里，用户还能直接记录 */
    var islandFailed by remember { mutableStateOf(false) }
    /** 当前展开行内编辑的行下标 + 字段；null = 没有行在编辑 */
    var editingRow by remember { mutableStateOf<Int?>(null) }
    var editingField by remember { mutableStateOf<String?>(null) }

    // 组合付款候选账户：每次弹出加载一次（识屏多发生在支付完成后，库里账户基本不变）
    var accounts by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        accounts = withContext(Dispatchers.IO) {
            runCatching {
                DbProvider.database.accountDao().getAllOnce().map { it.name }.filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
        }
    }

    // 写完停一拍再收，让「已记 N 笔」被看见；比旧版的 1.2s 收得更快
    LaunchedEffect(allDone) {
        if (allDone) {
            delay(800)
            onDismiss()
        }
    }

    fun collapseEditing() {
        if (editingField != null) {
            editingField = null
            editingRow = null
            BillOverlay.setWindowFocusable(false)
            BillOverlay.hideIme()
        }
    }

    fun updateRow(index: Int, draft: BillDraft) {
        rows = rows.mapIndexed { i, d -> if (i == index) draft else d }
    }

    fun writeAll() {
        val list = rows + rest
        if (busy || list.isEmpty()) return
        collapseEditing()
        busy = true
        scope.launch {
            val ok = QuickBillWriter.addDrafts(list, ledgerId)
            busy = false
            if (ok > 0) {
                writtenTotal = ok
                allDone = true
            } else {
                onDismiss()
            }
        }
    }

    /** 切到通知模式：收起卡片改发超级岛焦点通知；发不出去就留在卡片里提示直接记录 */
    fun goIsland() {
        if (busy) return
        collapseEditing()
        CapturePreferences.setOverlayStyle(context, CapturePreferences.OVERLAY_ISLAND)
        busy = true
        scope.launch {
            val result = SuperIslandNotifier.sendReminder(
                context,
                "截屏识别完成",
                "识别到 ${drafts.size} 笔账单，点击核对后再导入",
            )
            busy = false
            if (result.sent) onDismiss() else islandFailed = true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        allDone -> "已记 $writtenTotal 笔到「$ledgerId」"
                        else -> "识别到 ${drafts.size} 笔"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (allDone) {
                    Text("✓", color = IncomeGreen, fontWeight = FontWeight.Medium)
                }
            }

            if (!allDone) {
                // ── 样式切换按钮：当场换排版或转超级岛，选择会被记住 ──
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(
                        CapturePreferences.OVERLAY_DETAILED to "详细",
                        CapturePreferences.OVERLAY_COMPACT to "简洁",
                        CapturePreferences.OVERLAY_ISLAND to "通知",
                    ).forEach { (value, label) ->
                        StyleChip(
                            label = label,
                            selected = style == value,
                            onClick = {
                                when (value) {
                                    CapturePreferences.OVERLAY_ISLAND -> goIsland()
                                    else -> {
                                        collapseEditing()
                                        style = value
                                        CapturePreferences.setOverlayStyle(context, value)
                                    }
                                }
                            },
                        )
                    }
                }
                if (islandFailed) {
                    Text(
                        text = "超级岛通知没发出去，可直接在卡片里记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(6.dp))
                rows.forEachIndexed { index, draft ->
                    CompactRow(
                        draft = draft,
                        editing = editingRow == index && editingField == EDIT_NOTE,
                        onUpdate = { updateRow(index, it) },
                        onEdit = { editing ->
                            if (editingRow == index) {
                                collapseEditing()
                            } else {
                                BillOverlay.setWindowFocusable(true)
                                editingRow = index
                                editingField = EDIT_NOTE
                            }
                        },
                    )
                }
                if (rows.size < drafts.size) {
                    Text(
                        text = "另有 ${drafts.size - rows.size} 笔，进应用里核对",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        text = "忽略",
                        onClick = {
                            collapseEditing()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { writeAll() },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) {
                        val remain = rows.size + rest.size
                        Text(if (busy) "记录中…" else "记录全部 $remain 笔")
                    }
                }
            }
        }
    }
}

/** 简洁模式的行：只读预览（分类 / 账户 / 日期 / 金额 / 备注），点击补改备注 */
@Composable
private fun CompactRow(
    draft: BillDraft,
    editing: Boolean,
    onUpdate: (BillDraft) -> Unit,
    onEdit: (Boolean) -> Unit,
) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit(true) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = runCatching { CategoryCatalog.of(draft.categoryId).name }.getOrDefault("其他"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (draft.duplicateOfId != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "疑似重复",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
                val meta = listOf(draft.account, "${draft.date.monthValue}/${draft.date.dayOfMonth}")
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (meta.isNotBlank() || draft.note.isNotBlank()) {
                    Text(
                        text = listOf(meta, draft.note).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val income = draft.type == TransactionType.INCOME
            Text(
                text = (if (income) "+" else "-") +
                    String.format(Locale.CHINA, "¥%.2f", draft.amountCents / 100.0),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (income) IncomeGreen else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (editing) {
            InlineNoteEditor(
                initial = draft.note,
                onCommit = { note ->
                    onUpdate(draft.copy(note = note))
                    onEdit(false)
                },
            )
        }
    }
}

/** 卡片顶部的样式切换小按钮：选中态主色浅底 */
@Composable
private fun StyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 备注行内编辑：同金额编辑的焦点 / 输入法处理，上限 50 字 */
@Composable
private fun InlineNoteEditor(initial: String, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
        BillOverlay.showIme()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = text,
            onValueChange = { text = it.take(50) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            label = "备注",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(text.trim()) }),
        )
        Text(
            text = "完成",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .clickable { onCommit(text.trim()) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

