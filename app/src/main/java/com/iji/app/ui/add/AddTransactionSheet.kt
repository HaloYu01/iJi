package com.iji.app.ui.add

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiSwitchRow
import com.iji.app.ui.components.RegisterOverlayBackLayer
import com.iji.app.ui.components.WheelOptionDialog
import com.iji.app.ui.components.ijiButtonFocusRing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import top.yukonga.miuix.kmp.basic.Switch
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.Category
import com.iji.app.data.model.CurrencyDisplay
import com.iji.app.data.model.PaymentSplit
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.data.repository.TagStore
import com.iji.app.data.repository.TemplateRepository
import com.iji.app.data.repository.TransactionTemplate
import com.iji.app.ui.components.CategoryIconImage
import com.iji.app.ui.components.formatMoney
import com.iji.app.ui.components.parseCents
import com.iji.app.ui.components.sanitizeAmountInput
import com.iji.app.ui.components.AccountIconImage
import com.iji.app.ui.theme.DividerGray

import com.iji.app.ui.theme.IjiTheme
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.components.DateTimePickerDialog
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Send
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 编辑日期 / 时间的 Saver：旋转或进程重建后不丢用户所选时间 */
private val DateTimeSaver = listSaver<LocalDateTime, Int>(
    save = { listOf(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute) },
    restore = { LocalDateTime.of(it[0], it[1], it[2], it[3], it[4]) },
)

/** 组合付款明细的 Saver（PaymentSplit 不是 Parcelable，编码成「账户 + 分」） */
private val SplitsSaver = listSaver<List<PaymentSplit>, String>(
    save = { splits -> splits.flatMap { listOf(it.account, it.amountCents.toString()) } },
    restore = { flat -> flat.chunked(2).map { PaymentSplit(it[0], it[1].toLongOrNull() ?: 0L) } },
)

/** 上次落库的类型 / 分类 / 账户（按账本记忆，作为下次打开面板的默认值） */
private data class AddBillMemory(
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = "",
    val account: String = "",
) {
    fun save(context: Context, ledger: String) {
        SettingsRepository.setString(context, "addbill.type@$ledger", type.name)
        SettingsRepository.setString(context, "addbill.category@$ledger", category)
        SettingsRepository.setString(context, "addbill.account@$ledger", account)
    }

    companion object {
        fun load(context: Context, ledger: String): AddBillMemory = AddBillMemory(
            type = runCatching {
                TransactionType.valueOf(SettingsRepository.getString(context, "addbill.type@$ledger"))
            }.getOrDefault(TransactionType.EXPENSE),
            category = SettingsRepository.getString(context, "addbill.category@$ledger"),
            account = SettingsRepository.getString(context, "addbill.account@$ledger"),
        )
    }
}

/**
 * 「记一笔」的未完成草稿：退出面板时自动落盘，下次打开同一个账本自动恢复。
 *
 * 设计约束（改之前先读）：
 * - **只存新增态**：编辑既有账单用的是它自己的完整数据，既不写也不读草稿；
 *   落库成功后立即 [clear]，否则下次打开会恢复出一笔已经记过的内容。
 * - **按账本隔离**：草稿里带着 ledger，账本对不上就不恢复（草稿仍留在盘上，切回原账本还能用）。
 * - **读盘永不抛**：字段一律「缺失即默认」；类型名对不上（旧的枚举值 / 手工改坏的 JSON）
 *   直接当没有草稿。分类、账户这类**外部引用**在恢复时还会再校验一次存在性。
 */
private object AddBillDraft {
    const val KEY = "addbill.draft"

    /**
     * 草稿快照。分两组：
     * - **填写内容**：用户敲进去的数据（金额 / 分类 / 备注 / 日期 / 账户 / 标签 / 拆分 …）；
     * - **面板展开 / 开关状态**：决定重新打开时面板长什么样（二级分类是否展开、优惠区是否打开、
     *   退款 / 标签 chip 是否点亮）。
     */
    data class Draft(
        // ── 填写内容 ──
        val ledger: String,
        val type: TransactionType,
        val categoryId: String,
        val selectedParentId: String,
        val account: String,
        val transferToAccount: String,
        val expr: String,
        val note: String,
        val tags: List<String>,
        val dateTime: LocalDateTime,
        val splits: List<PaymentSplit>,
        val discountCents: Long,
        val merchant: String,
        val counterparty: String,
        val counterpartyContact: String,
        val counterpartyOrg: String,
        val reimburseState: String,
        val reconciled: Boolean,
        val excludeFromStats: Boolean,
        val excludeFromBudget: Boolean,
        val attachments: List<String>,
        // ── 面板展开 / 开关状态 ──
        val refund: Boolean,
        val discountOn: Boolean,
        val tagPicked: Boolean,
        /** 展开中的一级分类 id；null = 全部收起 */
        val expandedParentId: String?,
    )

    fun encode(d: Draft): String = JSONObject().apply {
        put("ledger", d.ledger)
        put("type", d.type.name)
        put("category", d.categoryId)
        put("parent", d.selectedParentId)
        put("account", d.account)
        put("transferTo", d.transferToAccount)
        put("expr", d.expr)
        put("note", d.note)
        put("tags", JSONArray(d.tags))
        put("time", d.dateTime.toString())
        put(
            "splits",
            JSONArray().apply { d.splits.forEach { put(JSONArray().put(it.account).put(it.amountCents)) } },
        )
        put("discount", d.discountCents)
        put("merchant", d.merchant)
        put("counterparty", d.counterparty)
        put("contact", d.counterpartyContact)
        put("org", d.counterpartyOrg)
        put("reimburse", d.reimburseState)
        put("reconciled", d.reconciled)
        put("exStats", d.excludeFromStats)
        put("exBudget", d.excludeFromBudget)
        put("attachments", JSONArray(d.attachments))
        put("refund", d.refund)
        put("discountOn", d.discountOn)
        put("tagPicked", d.tagPicked)
        put("expanded", d.expandedParentId.orEmpty())
    }.toString()

    fun load(context: Context): Draft? = runCatching {
        val raw = SettingsRepository.getString(context, KEY)
        if (raw.isBlank()) return@runCatching null
        val o = JSONObject(raw)
        // 类型是唯一不能用默认值兜住的字段：名字对不上就当没有草稿（valueOf 会抛，这里按名字找）
        val type = TransactionType.entries.firstOrNull { it.name == o.optString("type") }
            ?: return@runCatching null
        Draft(
            ledger = o.optString("ledger"),
            type = type,
            categoryId = o.optString("category"),
            selectedParentId = o.optString("parent"),
            account = o.optString("account"),
            transferToAccount = o.optString("transferTo"),
            expr = o.optString("expr"),
            note = o.optString("note"),
            tags = o.optJSONArray("tags")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty(),
            dateTime = runCatching { LocalDateTime.parse(o.optString("time")) }
                .getOrDefault(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)),
            splits = o.optJSONArray("splits")?.let { a ->
                List(a.length()) { i ->
                    val row = a.optJSONArray(i) ?: JSONArray()
                    PaymentSplit(row.optString(0), row.optLong(1))
                }
            }.orEmpty(),
            discountCents = o.optLong("discount"),
            merchant = o.optString("merchant"),
            counterparty = o.optString("counterparty"),
            counterpartyContact = o.optString("contact"),
            counterpartyOrg = o.optString("org"),
            reimburseState = o.optString("reimburse"),
            reconciled = o.optBoolean("reconciled"),
            excludeFromStats = o.optBoolean("exStats"),
            excludeFromBudget = o.optBoolean("exBudget"),
            attachments = o.optJSONArray("attachments")
                ?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty(),
            refund = o.optBoolean("refund"),
            discountOn = o.optBoolean("discountOn"),
            tagPicked = o.optBoolean("tagPicked"),
            expandedParentId = o.optString("expanded").takeIf { it.isNotBlank() },
        )
    }.getOrNull()

    fun save(context: Context, draft: Draft) {
        SettingsRepository.setString(context, KEY, encode(draft))
    }

    fun clear(context: Context) = SettingsRepository.setString(context, KEY, "")
}

/** 上次使用的账户排最前（sortedWith 稳定，其余保持原顺序） */
private fun recentFirst(recent: String): Comparator<String> =
    compareBy<String> { if (it == recent) 0 else 1 }

/**
 * 记一笔：iCost 风格悬浮记账面板（非全屏）
 *
 * 形态：底部悬浮窗，占屏高 85%（短屏 92%，保证分类区至少两行）；顶栏与数字键盘固定，
 * 中间分类宫格可滚动（转账换成转出/转入）；金额 / 日期 / 备注 / 功能项固定在键盘上方。
 *
 * 键盘：右侧一栏为四则运算（÷ ＋ × −），支持混合运算（先乘除后加减）；
 * 「完成」始终是落库键，表达式在落库时求值。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    viewModel: MainViewModel = viewModel(),
    prefillType: TransactionType? = null,
    prefillCategoryId: String? = null,
    /** 预填来源给的分类名（AI / 通知）：解析失败时用于提示用户手动选择 */
    prefillCategoryHint: String? = null,
    prefillAmountCents: Long? = null,
    prefillNote: String? = null,
    prefillAccount: String? = null,
    prefillTags: List<String>? = null,
    /** 预填日期（日历页「当天没有账单」的空态入口用）；null = 今天 */
    prefillDate: java.time.LocalDate? = null,
    /**
     * 由「记退款」入口带入的**原账单 id**；非 0 时面板直接进入退款模式。
     * 该值只做溯源，不参与金额聚合。
     */
    prefillRefundOfId: Long = 0L,
    existing: com.iji.app.data.model.Transaction? = null,
    /** 编辑态保存回调；传 null 表示本面板只用于新增（内部直接落库） */
    onConfirm: ((TransactionDraft) -> Unit)? = null,
) {

    val context = LocalContext.current
    // 统一轻量提示（替代 Toast）：需在组合作用域捕获，供内部普通函数 / 点击回调使用
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    // 资产账户（数据库）：账户选择、组合付款、转账候选的唯一来源
    val assetAccounts by viewModel.accounts.collectAsStateWithLifecycle()
    val accountBalances = remember(assetAccounts) { assetAccounts.associate { it.name to it.balanceCents } }
    // 账户候选只取「已添加的账户」（资产页里真实存在的），不拼内置的现金 / 银行卡等虚构项
    val accountOptions = remember(assetAccounts) {
        assetAccounts.map { it.name }.filter { it.isNotBlank() }.distinct()
    }
    // 当前账本：面板内选账本的默认值
    val currentLedger by viewModel.currentLedger.collectAsStateWithLifecycle()
    // 上次落库的类型 / 分类 / 账户（按账本），避免每次从「支出 · 餐饮 · 现金」重选；
    // 落库后同步更新，账户选择器里的「最近使用」也能立刻生效
    var lastUsed by remember(currentLedger) { mutableStateOf(AddBillMemory.load(context, currentLedger)) }
    // 面板高度：短屏多给 7%，保证分类区至少能放下两行；宽屏封顶，避免抽屉在大屏上过高
    val panelHeight = with(LocalDensity.current) {
        val ratio = if (configuration.screenHeightDp < 700) 0.92f else 0.85f
        val raw = (configuration.screenHeightDp * ratio).dp
        if (configuration.screenWidthDp >= 600) raw.coerceAtMost(640.dp) else raw
    }


    // ── 表单状态 ──
    // 抽屉是「常驻组合 + show 开关」：会话 id 只在打开边沿推进，关闭时不推进。
    // 不能直接用 show 当 key —— 收起动画期间 show 已为 false，key 变化会把表单闪回默认值；
    // 打开期间预填变化（语音 / OCR / 通知在面板已打开后才送值）则通过比对 formKey 跟随。
    val prefillKey = listOf(
        prefillType?.name, prefillCategoryId, prefillAmountCents,
        prefillNote, prefillAccount, prefillTags, prefillRefundOfId, prefillDate,
    )
    val formKeyText = ((existing?.id ?: 0L) to prefillKey).toString()
    // 会话 id 与「是否已打开」都 saveable：旋转后不会因为重建而重新推进会话、重置表单
    var formSession by rememberSaveable { mutableIntStateOf(0) }
    var wasShown by rememberSaveable { mutableStateOf(false) }
    var openedKey by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(show, formKeyText) {
        if (!show) {
            wasShown = false
            return@LaunchedEffect
        }
        if (!wasShown || openedKey != formKeyText) {
            wasShown = true
            openedKey = formKeyText
            formSession++
        }
    }

    val initialType = existing?.type ?: prefillType ?: lastUsed.type
    // 账户始终有值（上次使用 / 现金）：面板显示什么就落库什么，不再出现「显示未选、实际写现金」
    val initialAccount = existing?.account?.takeIf { it.isNotBlank() }
        ?: prefillAccount?.takeIf { it in accountOptions }
        ?: lastUsed.account.takeIf { it.isNotBlank() && it in accountOptions }
        // 没有已添加账户时留空，由用户在弹窗里先建 / 选，不默认到并不存在的账户
        ?: accountOptions.firstOrNull().orEmpty()
    val initialCategoryId = run {
        val candidate = prefillCategoryId ?: existing?.categoryId
            ?: lastUsed.category.takeIf { it.isNotBlank() }
        when {
            initialType == TransactionType.TRANSFER -> "transfer"
            candidate != null && CategoryCatalog.ofType(initialType).any { it.id == candidate } -> candidate
            // 分类可以被用户删空：不能 first()，空目录时留空 → 显示「未分类」，由用户去建分类
            else -> CategoryCatalog.ofType(initialType).firstOrNull()?.id.orEmpty()
        }
    }

    var type by rememberSaveable(formSession) { mutableStateOf(initialType) }
    var selectedCategoryId by rememberSaveable(formSession) { mutableStateOf(initialCategoryId) }
    // 两级菜单：当前展开的一级分类 id（一级本身也可作为分类被选中）
    var selectedParentId by rememberSaveable(formSession) {
        mutableStateOf(CategoryCatalog.of(initialCategoryId).let { it.parentId ?: it.id })
    }
    // 各类型上次所选的分类：切类型再切回来不丢选择
    var lastExpenseCategory by rememberSaveable(formSession) {
        mutableStateOf(
            if (initialType == TransactionType.EXPENSE) initialCategoryId
            else CategoryCatalog.expenses.firstOrNull()?.id.orEmpty(),
        )
    }
    var lastIncomeCategory by rememberSaveable(formSession) {
        mutableStateOf(
            if (initialType == TransactionType.INCOME) initialCategoryId
            else CategoryCatalog.incomes.firstOrNull()?.id.orEmpty(),
        )
    }
    // 下拉展开状态：null = 全部收起。打开面板不自动展开 —— 否则一半高度被二级菜单占掉
    var expandedParentId by rememberSaveable(formSession) { mutableStateOf<String?>(null) }

    // 金额输入：计算表达式（实时计算，支持四则运算）；编辑时回填原金额，
    // 自动记账（语音/OCR/通知）预填金额时直接落成「已填好」的表达式
    var expr by rememberSaveable(formSession) {
        mutableStateOf(
            existing?.let { "%.2f".format(it.amountCents / 100.0) }
                ?: prefillAmountCents?.let { "%.2f".format(it / 100.0) }
                ?: "",
        )
    }

    var note by rememberSaveable(formSession) { mutableStateOf(prefillNote ?: existing?.note ?: "") }
    var dateTime by rememberSaveable(formSession, stateSaver = DateTimeSaver) {
        mutableStateOf(
            // 回到编辑：带时分的账单按原时分回填，只到天（minuteOfDay = -1）的仍显示 00:00
            existing?.date?.atStartOfDay()
                ?.plusMinutes(existing.minuteOfDay.coerceAtLeast(0).toLong())
                ?: prefillDate?.atStartOfDay()
                ?: LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES),
        )
    }
    /**
     * 时分是否「可信」：决定保存时写真实时分还是 -1（只记到天）。
     *
     * 老账单的 minuteOfDay 是 -1（升级前只存日期），若不加这道判断，
     * 只要打开编辑面板保存一次，就会被回填的 00:00 写成"凌晨零点"。
     */
    var timeChosen by rememberSaveable(formSession) {
        mutableStateOf(existing?.minuteOfDay?.let { it >= 0 } ?: true)
    }
    var account by rememberSaveable(formSession) { mutableStateOf(initialAccount) }
    // 组合付款拆分明细；非空时这笔账由多个账户共同承担，account 仅作列表展示的首选账户
    var splits by rememberSaveable(formSession, stateSaver = SplitsSaver) {
        mutableStateOf(existing?.splits.orEmpty())
    }
    var showSplitDialog by remember(formSession) { mutableStateOf(false) }
    // 落库中：阻断连点导致的重复记账
    var submitting by remember(formSession) { mutableStateOf(false) }
    // 草稿开关：落库后关闭，同时让在途的防抖写盘失效（退出时不再有「放弃」这条路径）
    var draftEnabled by rememberSaveable(formSession) { mutableStateOf(true) }
    var showActionsDialog by remember(formSession) { mutableStateOf(false) }
    // 待应用的模板：表单非空时先确认覆盖
    var pendingTemplate by remember(formSession) { mutableStateOf<TransactionTemplate?>(null) }
    // 退款模式：输入正数、按负数支出落库（仅支出有意义）。
    // 编辑既有退款时按原金额符号判定；从「记退款」入口进来时直接置位。
    var refundMode by rememberSaveable(formSession) {
        mutableStateOf(
            prefillRefundOfId != 0L ||
                existing?.let { it.type == TransactionType.EXPENSE && it.amountCents < 0L } == true,
        )
    }
    var tags by rememberSaveable(formSession) {
        mutableStateOf(existing?.tags ?: prefillTags.orEmpty())
    }
    var excludeFromStats by rememberSaveable(formSession) { mutableStateOf(existing?.excludeFromStats ?: false) }
    var excludeFromBudget by rememberSaveable(formSession) { mutableStateOf(existing?.excludeFromBudget ?: false) }

    var discountOn by rememberSaveable(formSession) { mutableStateOf(existing != null && existing.discountCents > 0L) }
    var discountCents by rememberSaveable(formSession) { mutableStateOf(existing?.discountCents ?: 0L) }
    var attachments by rememberSaveable(formSession) { mutableStateOf(existing?.attachments ?: listOf<String>()) }
    var tagPicked by rememberSaveable(formSession) {
        mutableStateOf(existing?.tags?.isNotEmpty() == true || prefillTags?.isNotEmpty() == true)
    }
    var ledger by rememberSaveable(formSession) {
        mutableStateOf(existing?.ledgerId ?: currentLedger)
    }
    /** 转账的转入账户；仅 TRANSFER 有意义 */
    var transferToAccount by rememberSaveable(formSession) {
        mutableStateOf(existing?.transferToAccount ?: "")
    }

    // ── 商家 / 对方 / 垫付状态 / 已对账（「更多」弹窗）──
    var merchant by rememberSaveable(formSession) { mutableStateOf(existing?.merchant ?: "") }
    var counterparty by rememberSaveable(formSession) { mutableStateOf(existing?.counterparty ?: "") }
    // 对方资料：借入借出专用，直接在面板上就地编辑（不再藏进「更多」）
    var counterpartyContact by rememberSaveable(formSession) {
        mutableStateOf(existing?.counterpartyContact ?: "")
    }
    var counterpartyOrg by rememberSaveable(formSession) {
        mutableStateOf(existing?.counterpartyOrg ?: "")
    }
    // 报销 / 垫付：原来「报销开关」与「垫付状态」两处独立，语义打架，合并为单一状态。
    // 旧数据（reimburse = true、状态为空）按「待处理」回填，无需迁移。
    var reimburseState by rememberSaveable(formSession) {
        mutableStateOf(
            existing?.reimburseState?.takeIf { it != com.iji.app.data.model.ReimburseState.NONE }
                ?: if (existing?.reimburse == true) com.iji.app.data.model.ReimburseState.PENDING
                else com.iji.app.data.model.ReimburseState.NONE,
        )
    }
    /** 是否涉及报销 / 垫付：由状态推导，不再单独存一个开关 */
    val reimburse = reimburseState != com.iji.app.data.model.ReimburseState.NONE
    var reconciled by rememberSaveable(formSession) { mutableStateOf(existing?.reconciled ?: false) }
    /** 「更多」是否被填过：用于给入口 chip 上色，提示有隐藏信息 */
    val moreFilled = merchant.isNotBlank() || counterparty.isNotBlank() ||
        counterpartyContact.isNotBlank() || counterpartyOrg.isNotBlank() ||
        reimburseState != com.iji.app.data.model.ReimburseState.NONE || reconciled
    // 当前分类是否借贷类：决定面板上是否出现「对方资料」区（见下方 CounterpartySection）
    val borrowDirection = com.iji.app.ui.assets.borrowDirectionOfCategory(selectedCategoryId)

    var showDatePicker by remember(formSession) { mutableStateOf(false) }
    // 备注是**行内编辑**（不弹二次弹窗）：true = 日期/备注行里的备注位变成输入框
    var noteEditing by remember(formSession) { mutableStateOf(false) }
    // 优惠金额：走面板内的自定义键盘，不唤起系统输入法
    var discountEditing by remember(formSession) { mutableStateOf(false) }
    // 面板内提示：面板开着时全局 snackbar 会被抽屉盖住，必须当场看到的反馈走这里
    var panelNotice by remember(formSession) { mutableStateOf<String?>(null) }
    LaunchedEffect(panelNotice) {
        if (panelNotice != null) {
            delay(2600)
            panelNotice = null
        }
    }
    // 预填解析失败时明确告知：名称对不上就不要静默用默认分类 / 账户
    LaunchedEffect(show, prefillCategoryHint, prefillCategoryId, prefillAccount) {
        if (!show || existing != null) return@LaunchedEffect
        when {
            !prefillCategoryHint.isNullOrBlank() && prefillCategoryId == null ->
                panelNotice = "未识别分类「$prefillCategoryHint」，请手动选择"
            !prefillAccount.isNullOrBlank() && prefillAccount !in accountOptions ->
                panelNotice = "账户「$prefillAccount」不存在，已改用默认账户"
        }
    }
    var showAccountDialog by remember(formSession) { mutableStateOf(false) }
    var showTransferToDialog by remember(formSession) { mutableStateOf(false) }
    var showTagDialog by remember(formSession) { mutableStateOf(false) }
    var showLedgerDialog by remember(formSession) { mutableStateOf(false) }
    /** 编辑态待迁移的目标账本：确认后才真正改 [ledger] */
    var pendingLedger by remember(formSession) { mutableStateOf<String?>(null) }
    var showMarkDialog by remember(formSession) { mutableStateOf(false) }
    var showAttachmentDialog by remember(formSession) { mutableStateOf(false) }
    var showMoreDialog by remember(formSession) { mutableStateOf(false) }
    var showTemplateDialog by remember(formSession) { mutableStateOf(false) }

    // 记账模板：改动后立即刷新（DataStore Flow），空列表时面板上不显示模板入口
    val templates by remember(context) { TemplateRepository.observe(context) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // 旧版转账（本字段引入前写入的 TRANSFER）：只有转出账户、没有转入账户，
    // 因此这笔转账当时既不联动余额、也说不清资金去向，需要在面板上明确引导补全。
    val legacyTransfer = existing?.type == TransactionType.TRANSFER &&
        existing.transferToAccount.isBlank()

    // ── 计算逻辑：表达式实时计算 ──
    /** 解析并计算表达式，返回 BigDecimal（容错：忽略尾部孤立运算符） */
    fun evaluateExpr(s: String): BigDecimal {
        if (s.isEmpty()) return BigDecimal.ZERO
        val nums = mutableListOf<BigDecimal>()
        val ops = mutableListOf<Char>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotEmpty()) {
                nums.add(runCatching { BigDecimal(cur.toString()) }.getOrDefault(BigDecimal.ZERO))
                cur.clear()
            }
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c in "+-×÷") {
                // 表达式开头的负号视为数字一部分
                if (nums.isEmpty() && c == '-' && cur.isEmpty()) {
                    cur.append('-')
                    i++
                    continue
                }
                flush()
                ops.add(c)
            } else {
                cur.append(c)
            }
            i++
        }
        flush()
        return evaluate(nums, ops)
    }

    /** 实时显示值（分）：尾部若停留运算符则取前段运算值 */
    fun liveCents(): Long {
        val s = if (expr.endsWith('+') || expr.endsWith('-') || expr.endsWith('×') || expr.endsWith('÷')) {
            expr.dropLast(1)
        } else {
            expr
        }
        return evaluateExpr(s)
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    /**
     * 实际落库的金额（分）。
     *
     * 「退款」开关打开时取负：退款就是一笔**负数支出** —— 账户把钱收回来、统计里冲减支出，
     * 而报销应收的 `SUM(amountCents) WHERE reimburseState='PENDING'` 也会自动把它抵掉。
     * 用户只需按正常正数输入，不必自己先按「−」。
     *
     * 值已经是负数时不再翻转（编辑既有退款时 expr 带着负号回填）。
     */
    fun signedCents(): Long {
        val v = liveCents()
        return if (refundMode && type == TransactionType.EXPENSE && v > 0L) -v else v
    }

    /** 输入数字 / 小数点 */
    fun appendDigit(d: Char) {
        if (expr.length >= 18) return
        val lastOpIdx = expr.indexOfLast { it in "+-×÷" }
        val curNum = if (lastOpIdx < 0) expr else expr.substring(lastOpIdx + 1)
        if (d == '.') {
            if (curNum.contains('.')) return
            expr = if (expr.isEmpty() ||
                expr.endsWith('+') || expr.endsWith('-') || expr.endsWith('×') || expr.endsWith('÷')
            ) {
                expr + "0."
            } else {
                expr + "."
            }
            return
        }
        // 小数位最多两位
        if (curNum.contains('.')) {
            val dec = curNum.substringAfter('.')
            if (dec.length >= 2) return
        }
        // 前导零替换
        if (curNum == "0") {
            expr = if (lastOpIdx < 0) d.toString() else expr.substring(0, lastOpIdx + 1) + d
            return
        }
        if (curNum == "-0") {
            expr = if (lastOpIdx < 0) "-$d" else expr.substring(0, lastOpIdx + 1) + "-$d"
            return
        }
        expr += d
    }

    /** 输入运算符 */
    fun appendOp(o: Char) {
        if (expr.isEmpty()) {
            if (o == '-') expr = "-"
            return
        }
        if (expr == "-") return
        if (expr.endsWith('+') || expr.endsWith('-') || expr.endsWith('×') || expr.endsWith('÷')) {
            expr = expr.dropLast(1) + o
            return
        }
        expr += o
    }

    /** 退位（计算器逻辑：删除最后一位） */
    fun backspace() {
        if (expr.isNotEmpty()) expr = expr.dropLast(1)
    }

    /** 落库（调用前已通过校验）；closeAfter=false 即「再记」 */
    fun commit(closeAfter: Boolean) {
        if (submitting) return
        // 带符号：退款模式下「输入 25」最终落库为 -2500
        val cents = signedCents()
        // 允许负数（红字冲销 / 退款），只拦恰好为 0
        if (cents == 0L) {
            panelNotice = "请输入金额"
            return
        }
        // 转账的账户是**可选项**：不选也能落库，只是不联动余额（balanceDeltas 遇到空账户直接跳过）。
        // 因此这里只拦「转出与转入填成同一个账户」；支出 / 收入仍必须选账户。
        if (type == TransactionType.TRANSFER) {
            if (account.isNotBlank() && account == transferToAccount) {
                panelNotice = "转出与转入不能是同一个账户"
                showTransferToDialog = true
                return
            }
        } else if (account.isBlank()) {
            // 账户必须来自「已添加的账户」，没有就先让用户去选 / 新建
            panelNotice = "请先选择账户"
            showAccountDialog = true
            return
        }
        // 组合付款：仅支出/收入的有效正额有意义（转账走转出→转入，红字冲销按单账户处理）。
        // 只有一条拆分等于没拆，直接退回单账户逻辑。
        val effectiveSplits = if (type != TransactionType.TRANSFER && cents > 0L) {
            splits.filter { it.account.isNotBlank() && it.amountCents != 0L }
        } else {
            emptyList()
        }
        if (effectiveSplits.size > 1) {
            val sum = effectiveSplits.sumOf { it.amountCents }
            if (sum != cents) {
                panelNotice = "分账合计 ${formatMoney(sum)} 与金额 ${formatMoney(cents)} 不一致"
                showSplitDialog = true
                return
            }
        }
        val finalSplits = if (effectiveSplits.size > 1) effectiveSplits else emptyList()
        // 组合付款时首选账户跟随第一条拆分，保证列表展示与余额口径一致
        val finalAccount = finalSplits.firstOrNull()?.account ?: account
        val date = dateTime.toLocalDate()
        // 时分：没动过选择器时保持 -1（只记到天），动过才落真实值
        val minuteOfDay = if (timeChosen) dateTime.hour * 60 + dateTime.minute else -1
        val toAccount = if (type == TransactionType.TRANSFER) transferToAccount else ""
        // 溯源：编辑时沿用原值，新建时取「记退款」入口带入的原账单 id
        val finalRefundOfId = existing?.refundOfId ?: prefillRefundOfId
        submitting = true
        if (existing != null) {
            // 编辑模式：交给调用方回写（含余额回滚），而非新增一条
            onConfirm?.invoke(
                TransactionDraft(
                    type = type,
                    categoryId = selectedCategoryId,
                    amountCents = cents,
                    note = note,
                    account = finalAccount,
                    tags = tags,
                    date = date,
                    excludeFromStats = excludeFromStats,
                    excludeFromBudget = excludeFromBudget,
                    reimburse = reimburse,
                    discountCents = discountCents,
                    attachments = attachments,
                    merchant = merchant,
                    counterparty = counterparty,
                    reimburseState = reimburseState,
                    reconciled = reconciled,
                    ledgerId = ledger,
                    transferToAccount = toAccount,
                    splits = finalSplits,
                    refundOfId = finalRefundOfId,
                    counterpartyContact = counterpartyContact,
                    counterpartyOrg = counterpartyOrg,
                    minuteOfDay = minuteOfDay,
                )
            )
            snackbar.show("已保存")
        } else {
            val memory = AddBillMemory(type, selectedCategoryId, finalAccount)
            memory.save(context, ledger)
            lastUsed = memory
            viewModel.addTransaction(
                type = type,
                categoryId = selectedCategoryId,
                amountCents = cents,
                note = note,
                // 与 onConfirm 分支同口径：组合付款时首选账户取第一条拆分
                account = finalAccount,
                tags = tags,
                date = date,
                // TRANSFER 天然不进收支/预算统计，无需强制 excludeFromStats
                excludeFromStats = excludeFromStats,
                excludeFromBudget = excludeFromBudget,
                reimburse = reimburse,
                discountCents = discountCents,
                attachments = attachments,
                merchant = merchant,
                counterparty = counterparty,
                counterpartyContact = counterpartyContact,
                counterpartyOrg = counterpartyOrg,
                reimburseState = reimburseState,
                reconciled = reconciled,
                ledgerId = ledger,
                transferToAccount = toAccount,
                splits = finalSplits,
                refundOfId = finalRefundOfId,
                minuteOfDay = minuteOfDay,
                onSaved = { id ->
                    snackbar.show(
                        "已记一笔 ${formatMoney(cents)}",
                        actionLabel = "撤销",
                        onAction = { viewModel.deleteBillsByIds(listOf(id)) {} },
                    )
                },
            )
        }
        // 已落库：停掉草稿写盘并清除（防止在途防抖把刚记的内容又写回去）
        draftEnabled = false
        AddBillDraft.clear(context)
        if (closeAfter) {
            onDismiss()
            return
        }
        // 再记：清空金额与备注，保留类型 / 分类 / 账户 / 标签；短暂锁定按钮防连点
        expr = ""
        note = ""
        dateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        draftEnabled = true
        scope.launch {
            delay(600)
            submitting = false
        }
    }

    fun saveAndClose() = commit(true)

    fun saveAndContinue() = commit(false)

    /** 切换类型：各类型分别记住所选分类；退款态只属于支出 */
    fun selectType(target: TransactionType) {
        if (target == type) return
        when (type) {
            TransactionType.EXPENSE -> lastExpenseCategory = selectedCategoryId
            TransactionType.INCOME -> lastIncomeCategory = selectedCategoryId
            TransactionType.TRANSFER -> Unit
        }
        type = target
        if (target != TransactionType.EXPENSE) refundMode = false
        val next = when (target) {
            TransactionType.EXPENSE -> lastExpenseCategory
            TransactionType.INCOME -> lastIncomeCategory
            TransactionType.TRANSFER -> "transfer"
        }
        selectedCategoryId = next
        if (target != TransactionType.TRANSFER) {
            selectedParentId = CategoryCatalog.of(next).let { it.parentId ?: it.id }
        }
        expandedParentId = null
    }

    // 面板是否「有东西可存」：决定要不要写草稿（也用作「应用模板前先确认覆盖」的判据）
    val hasContent = expr.isNotBlank() || note.isNotBlank() || tags.isNotEmpty() ||
        splits.isNotEmpty() || discountCents != 0L || merchant.isNotBlank() ||
        counterparty.isNotBlank() || counterpartyContact.isNotBlank() ||
        counterpartyOrg.isNotBlank() || transferToAccount.isNotBlank() ||
        attachments.isNotEmpty() ||
        // 只动过「面板状态」也算动过：换了分类、展开了二级菜单，都该在下次打开时复原
        selectedCategoryId != initialCategoryId || expandedParentId != null

    /**
     * 面板当前状态 → 草稿快照；返回 null 表示**不该存**。
     *
     * 三种情况不存：
     * 1. 编辑既有账单（`existing != null`）——它退出后本来就不该在「记一笔」里复活；
     * 2. 面板还是空的（[hasContent] 为 false）——空草稿只会让下次打开莫名提示「已恢复」；
     * 3. 刚落库成功（`draftEnabled == false`）——否则会把已记过的内容又写回去。
     */
    fun snapshotDraft(): AddBillDraft.Draft? {
        if (!draftEnabled || existing != null || !hasContent) return null
        return AddBillDraft.Draft(
            ledger = ledger,
            type = type,
            categoryId = selectedCategoryId,
            selectedParentId = selectedParentId,
            account = account,
            transferToAccount = transferToAccount,
            expr = expr,
            note = note,
            tags = tags,
            dateTime = dateTime,
            splits = splits,
            discountCents = discountCents,
            merchant = merchant,
            counterparty = counterparty,
            counterpartyContact = counterpartyContact,
            counterpartyOrg = counterpartyOrg,
            reimburseState = reimburseState.name,
            reconciled = reconciled,
            excludeFromStats = excludeFromStats,
            excludeFromBudget = excludeFromBudget,
            attachments = attachments,
            refund = refundMode,
            discountOn = discountOn,
            tagPicked = tagPicked,
            expandedParentId = expandedParentId,
        )
    }

    /**
     * 立即把草稿写盘（跳过 800ms 防抖），**含清空**。
     *
     * 退出时必须同步写：面板一收，[draftJson] 立刻变成空串，防抖协程会被取消，
     * 那 800ms 内填的内容就丢了。写盘本身是 SharedPreferences 的同步提交，代价可接受。
     *
     * 面板被清空后退出要顺手把旧草稿删掉，否则下次打开会把「用户已经删掉的内容」又恢复回来。
     */
    fun persistDraftNow() {
        if (!draftEnabled) return
        val snap = snapshotDraft()
        if (snap != null) {
            AddBillDraft.save(context, snap)
        } else if (existing == null) {
            AddBillDraft.clear(context)
        }
    }

    /**
     * 关闭面板：**不再弹「放弃这笔？」确认**，而是先把当前填写内容与面板状态落盘，
     * 下次打开同一个账本自动恢复。只有正在落库时忽略（防连点）。
     */
    fun requestDismiss() {
        if (submitting) return
        persistDraftNow()
        onDismiss()
    }

    // 面板也可能被外部直接收起（宿主置 show=false），那条路不经过 requestDismiss：
    // 在 show 落回 false 的边缘补一次写盘。**只写不清**——本组件在每个宿主里都常驻，
    // 初始 show=false 也会跑一遍，这里清空会把用户上次的草稿白扔掉。
    LaunchedEffect(show) {
        if (show) return@LaunchedEffect
        snapshotDraft()?.let { AddBillDraft.save(context, it) }
    }

    // 登记进全局弹窗返回栈：返回键先关栈顶（选择器），再关本抽屉（关闭即保存草稿）
    RegisterOverlayBackLayer(show = show, onDismiss = { requestDismiss() }, label = "AddTransactionSheet")

    /** 应用模板：空分类 / 空金额 / 空备注不覆盖当前值；计入标记 / 组合付款 / 优惠整体套用 */
    fun applyTemplate(template: TransactionTemplate) {
        if (template.type != type) selectType(template.type)
        if (template.categoryId.isNotBlank()) {
            val c = CategoryCatalog.of(template.categoryId)
            selectedParentId = c.parentId ?: c.id
            selectedCategoryId = template.categoryId
        }
        expr = if (template.amountCents != 0L) "%.2f".format(template.amountCents / 100.0) else ""
        if (template.note.isNotBlank()) note = template.note
        if (template.account.isNotBlank()) account = template.account
        if (template.tags.isNotEmpty()) {
            tags = template.tags
            tagPicked = true
        }
        // 计入标记 / 组合付款 / 优惠：模板是完整预设，非转账时整体套用——
        // 转账天然不进收支 / 预算统计，也没有组合付款与优惠的概念。
        // 退款是红字冲销、按单账户处理，拆分不适用（与功能排的门控一致）。
        if (template.type != TransactionType.TRANSFER && !refundMode) {
            excludeFromStats = template.excludeFromStats
            excludeFromBudget = template.excludeFromBudget
            splits = template.splits
                .filter { it.account.isNotBlank() && it.amountCents > 0L }
                .takeIf { it.size >= 2 }
                .orEmpty()
            discountCents = template.discountCents
            discountOn = template.discountCents > 0L
        }
    }

    // ── 草稿：打开时恢复上次退出时的面板状态，改动节流写盘，退出时立即写盘 ──
    var draftChecked by rememberSaveable(formSession) { mutableStateOf(false) }
    LaunchedEffect(formSession, draftChecked) {
        if (draftChecked) return@LaunchedEffect
        draftChecked = true
        // 显式预填（AI / 通知 / 日历 / 退款）优先于草稿；refundOfId 默认 0 不算预填。
        // 编辑既有账单也不读草稿：那是另一笔账，恢复会把用户正在改的内容冲掉。
        val hasPrefill = prefillType != null || prefillCategoryId != null || prefillAmountCents != null ||
            !prefillNote.isNullOrBlank() || !prefillAccount.isNullOrBlank() ||
            !prefillTags.isNullOrEmpty() || prefillRefundOfId != 0L || prefillDate != null
        if (existing != null || hasPrefill) return@LaunchedEffect
        // 读盘失败（空 / JSON 损坏 / 枚举对不上）一律 null → 静默用默认面板，不打扰用户。
        // 也**不需要**再判「草稿是否为空」：写盘侧只写「用户动过」的状态（见 snapshotDraft），
        // 盘上有草稿就一定有东西可恢复。
        val draft = AddBillDraft.load(context) ?: return@LaunchedEffect
        // 草稿按账本隔离：账本对不上就不恢复（草稿留着，切回原账本仍然可用）
        if (draft.ledger != ledger) return@LaunchedEffect
        // 类型先落地，后续分类 / 展开状态都以它为准做存在性校验
        type = draft.type
        val categories = if (draft.type == TransactionType.TRANSFER) {
            CategoryCatalog.transfers
        } else {
            CategoryCatalog.ofType(draft.type)
        }
        // 分类可能已被删除 / 改名：找不到就退回该类型的第一个分类
        val restoredCategoryId = draft.categoryId.takeIf { id -> categories.any { it.id == id } }
            ?: categories.firstOrNull()?.id
            ?: draft.categoryId
        selectedCategoryId = restoredCategoryId
        selectedParentId = draft.selectedParentId.takeIf { id -> categories.any { it.id == id } }
            ?: CategoryCatalog.of(restoredCategoryId).let { it.parentId ?: it.id }
        // 账户可能已被删除：只恢复仍然存在的账户（转出 / 转入各自校验）
        if (draft.account in accountOptions) account = draft.account
        if (draft.transferToAccount in accountOptions) transferToAccount = draft.transferToAccount
        expr = draft.expr
        note = draft.note
        tags = draft.tags
        tagPicked = draft.tagPicked || draft.tags.isNotEmpty()
        dateTime = draft.dateTime
        refundMode = draft.refund && draft.type == TransactionType.EXPENSE
        discountCents = draft.discountCents
        discountOn = draft.discountOn || draft.discountCents > 0L
        merchant = draft.merchant
        counterparty = draft.counterparty
        counterpartyContact = draft.counterpartyContact
        counterpartyOrg = draft.counterpartyOrg
        reimburseState = runCatching {
            com.iji.app.data.model.ReimburseState.valueOf(draft.reimburseState)
        }.getOrDefault(com.iji.app.data.model.ReimburseState.NONE)
        reconciled = draft.reconciled
        excludeFromStats = draft.excludeFromStats
        excludeFromBudget = draft.excludeFromBudget
        attachments = draft.attachments
        splits = draft.splits
        // 展开状态最后恢复：只有该项目确实是一级分类时才展开，否则保持全收起
        expandedParentId = draft.expandedParentId
            ?.takeIf { id -> categories.any { it.id == id && it.parentId == null } }
        panelNotice = "已恢复上次未完成的记账"
    }
    // 输入过程中仍走 800ms 节流：进程被杀时最多丢最后 0.8 秒的输入；
    // 正常退出由 requestDismiss / show=false 边缘的 persistDraftNow() 立即写盘
    val draftJson = remember(
        show, draftEnabled, expr, note, account, transferToAccount, type, selectedCategoryId,
        selectedParentId, tags, tagPicked, dateTime, refundMode, discountOn, discountCents,
        merchant, counterparty, counterpartyContact, counterpartyOrg, reimburseState, reconciled,
        excludeFromStats, excludeFromBudget, attachments, splits, ledger, expandedParentId, hasContent,
    ) {
        if (!show) "" else snapshotDraft()?.let { AddBillDraft.encode(it) }.orEmpty()
    }
    LaunchedEffect(draftJson, draftEnabled) {
        if (!draftEnabled || draftJson.isEmpty()) return@LaunchedEffect
        delay(800)
        SettingsRepository.setString(context, AddBillDraft.KEY, draftJson)
    }

    // 日期 + 时间选择（Miuix 风格，可改年/月/日/时/分）；补记场景给「今天/昨天/前天」快捷项
    DateTimePickerDialog(
        show = showDatePicker,
        initial = dateTime,
        quickDays = true,
        onDismiss = { showDatePicker = false },
        onConfirm = {
            dateTime = it
            timeChosen = true
        },
    )

    // 账户选择：内置账户 + 资产账户；近期使用的排前，资产账户显示余额
    AccountPickerDialog(
        show = showAccountDialog,
        accounts = accountOptions.sortedWith(recentFirst(lastUsed.account)),
        balances = accountBalances,
        selected = account,
        title = "选择账户",
        onDismissRequest = { showAccountDialog = false },
        onConfirm = {
            account = it
            showAccountDialog = false
        },
    )

    // 组合付款：把一笔账拆到多个账户
    SplitPaymentDialog(
        show = showSplitDialog,
        accounts = accountOptions,
        splits = splits,
        totalCents = liveCents(),
        onDismissRequest = { showSplitDialog = false },
        onConfirm = { result ->
            splits = result
            showSplitDialog = false
            if (result.isEmpty()) panelNotice = "已取消组合付款"
        },
    )

    // 转账的转入账户：候选里剔除转出账户（同账户转账余额总额不变，无意义）
    AccountPickerDialog(
        show = showTransferToDialog,
        accounts = accountOptions.filter { it != account }.sortedWith(recentFirst(lastUsed.account)),
        balances = accountBalances,
        selected = transferToAccount,
        title = "选择转入账户",
        onDismissRequest = { showTransferToDialog = false },
        onConfirm = {
            transferToAccount = it
            showTransferToDialog = false
        },
    )

    // 标记弹窗：支出与预算的计入状态相互独立
    IjiDialog(
        show = showMarkDialog,
        onDismissRequest = { showMarkDialog = false },
        title = "计入标记",
        confirmText = "完成",
        onConfirm = { showMarkDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IjiSwitchRow(
                title = "计入收支",
                desc = "关闭后不计入本月收支统计与分类汇总",
                checked = !excludeFromStats,
                onCheckedChange = { excludeFromStats = !it },
            )
            IjiSwitchRow(
                title = "计入预算",
                desc = "关闭后不计入预算进度（仍计入收支）",
                checked = !excludeFromBudget,
                onCheckedChange = { excludeFromBudget = !it },
            )
        }
    }

    // 更多信息弹窗：商家 / 对方 / 垫付状态 / 已对账
    MoreInfoDialog(
        show = showMoreDialog,
        merchant = merchant,
        counterparty = counterparty,
        reconciled = reconciled,
        onDismissRequest = { showMoreDialog = false },
        showCounterparty = borrowDirection == null,
        onConfirm = { m, c, r ->
            merchant = m
            counterparty = c
            reconciled = r
            showMoreDialog = false
        },
    )

    // 附件弹窗：添加 / 移除图片
    AttachmentDialog(
        show = showAttachmentDialog,
        attachments = attachments,
        onConfirm = { attachments = it; showAttachmentDialog = false },
        onDismissRequest = { showAttachmentDialog = false },
        context = context,
    )

    TagSelectDialog(
        show = showTagDialog,
        initial = tags,
        context = context,
        onConfirm = { picked ->
            tags = picked
            tagPicked = picked.isNotEmpty()
            showTagDialog = false
        },
        onDismiss = { showTagDialog = false },
    )

    // 模板：一键带出类型 / 分类 / 金额 / 备注 / 账户 / 标签
    TemplatePickerDialog(
        show = showTemplateDialog,
        templates = templates,
        onDismissRequest = { showTemplateDialog = false },
        onPick = { template ->
            showTemplateDialog = false
            // 已有内容时先确认覆盖，避免误触把刚填的金额 / 备注冲掉
            if (hasContent) {
                pendingTemplate = template
            } else {
                applyTemplate(template)
                panelNotice = "已应用模板「${template.name.ifBlank { "未命名模板" }}」"
            }
        },
    )

    // 模板覆盖确认
    IjiDialog(
        show = pendingTemplate != null,
        onDismissRequest = { pendingTemplate = null },
        title = "应用模板？",
        summary = "当前已填写的内容会被模板覆盖",
        confirmText = "覆盖",
        onConfirm = {
            pendingTemplate?.let {
                applyTemplate(it)
                panelNotice = "已应用模板「${it.name.ifBlank { "未命名模板" }}」"
            }
            pendingTemplate = null
        },
    )

    // 「更多」二级菜单：低频项收进固定入口，窄屏不必横滑寻找
    IjiDialog(
        show = showActionsDialog,
        onDismissRequest = { showActionsDialog = false },
        title = "更多选项",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 报销 / 垫付状态：与「报销」开关合并为同一控件（reimburse = 状态 ≠ 不涉及）
            Text(
                "报销 / 垫付",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.iji.app.data.model.ReimburseState.entries.forEach { state ->
                    ActionChip(
                        label = when (state) {
                            com.iji.app.data.model.ReimburseState.NONE -> "不涉及"
                            com.iji.app.data.model.ReimburseState.PENDING -> "待处理"
                            com.iji.app.data.model.ReimburseState.PENDING_CONFIRM -> "待确认"
                            com.iji.app.data.model.ReimburseState.SETTLED -> "已结清"
                        },
                        selected = reimburseState == state,
                        onClick = { reimburseState = state },
                    )
                }
            }
            // 组合付款 / 优惠 / 附件已提升为面板上的直接 chip，这里只留低频项
            ActionMenuRow(
                label = "计入标记",
                value = when {
                    excludeFromStats && excludeFromBudget -> "均不计入"
                    excludeFromStats -> "不计收支"
                    excludeFromBudget -> "不计预算"
                    else -> ""
                },
            ) {
                showActionsDialog = false
                showMarkDialog = true
            }
            ActionMenuRow(
                label = "商家 / 对方",
                value = listOf(merchant, counterparty).firstOrNull { it.isNotBlank() } ?: "",
            ) {
                showActionsDialog = false
                showMoreDialog = true
            }
        }
    }

    // 滚轮选项：滚到中间即暂定，点确定提交
    WheelOptionDialog(
        show = showLedgerDialog,
        options = viewModel.ledgers,
        selected = ledger,
        title = "选择账本",
        onDismissRequest = { showLedgerDialog = false },
        onConfirm = {
            showLedgerDialog = false
            // 编辑态换账本 = 把这笔记到别的账本（原账本里不再出现），需要明确确认
            if (existing != null && it != existing.ledgerId) {
                pendingLedger = it
            } else {
                ledger = it
            }
        },
    )

    // 编辑态跨账本迁移确认
    IjiDialog(
        show = pendingLedger != null,
        onDismissRequest = { pendingLedger = null },
        title = "迁移到「${pendingLedger ?: ""}」？",
        summary = "这笔账单会记到该账本，原账本里不再显示",
        confirmText = "迁移",
        onConfirm = {
            pendingLedger?.let { ledger = it }
            pendingLedger = null
        },
    )

    // 备注输入条的收起：同时清焦点，确保输入法跟着收
    val focusManager = LocalFocusManager.current
    fun endNoteEditing() {
        noteEditing = false
        focusManager.clearFocus()
    }

    // ── 悬浮面板：底部抽屉化（Miuix OverlayBottomSheet）──
    OverlayBottomSheet(
        show = show,
        onDismissRequest = { requestDismiss() },
        title = null,
        defaultWindowInsetsPadding = false,
    ) {
        // 备注输入条是浮层（键盘弹出时靠 imePadding 顶到键盘上沿），所以外层用 Box 承载：
        // 面板本体保持固定高度不变，不再靠实测偏移量去挪行
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
                // 系统导航栏：抽屉不再代劳（defaultWindowInsetsPadding = false），需自行避让手势条
                // 左右留白：由 Miuix BottomSheetDefaults.insideMargin(24dp) 提供，不再叠加 screenPadding
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 点面板**非可点区域**（空白处）收起分类下拉 + 结束备注行内编辑；
                    // 按钮/图标自消费点击事件，不会传到这里（Compose 事件自子向父冒泡），所以不会误收起
                    .pointerInput(Unit) {
                        detectTapGestures {
                            expandedParentId = null
                            endNoteEditing()
                        }
                    },
            ) {
                // 顶部留白压到最小：整行向上平移（原来 8.dp）
                Spacer(Modifier.height(2.dp))

                // ── 顶部：× | 支出/收入/转账 | 选择账本（固定）──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        MiuixIcons.Close,
                        contentDescription = "关闭",
                        tint = TextSecondary,
                        modifier = Modifier
                            // 点击区保持 48dp（项目触摸目标下限），只把图标画小（24 → 20dp）；
                            // 再整体左移 6dp，压掉与面板左边缘之间多余的空白
                            .size(48.dp)
                            .offset(x = (-6).dp)
                            .clip(CircleShape)
                            .clickable { requestDismiss() }
                            .padding(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    // 类型切换（三段）：外壳内边距 3 → 2dp，字号交给 [TypeOption] 统一缩小
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp),
                    ) {
                        TypeOption(
                            label = "支出",
                            value = TransactionType.EXPENSE,
                            current = type,
                            onSelect = { selectType(it) },
                            modifier = Modifier.weight(1f),
                        )
                        TypeOption(
                            label = "收入",
                            value = TransactionType.INCOME,
                            current = type,
                            onSelect = { selectType(it) },
                            modifier = Modifier.weight(1f),
                        )
                        TypeOption(
                            label = "转账",
                            value = TransactionType.TRANSFER,
                            current = type,
                            onSelect = { selectType(it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    // 账本选择：超长账本名省略，不挤压三段切换
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.md))
                            // 一层很浅的底纹，把账本区与左侧类型切换分开；不做成实心卡片，避免抢视觉重心
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .clickable { showLedgerDialog = true }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 编辑态换过账本：用主色提示「这笔要迁走」
                        val ledgerChanged = existing != null && ledger != existing.ledgerId
                        Text(
                            ledger,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ledgerChanged) MiuixPrimary else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 84.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "▾",
                            color = if (ledgerChanged) MiuixPrimary else TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // 顶部栏与金额区之间收紧（原来 8.dp）：金额整体上移
                Spacer(Modifier.height(2.dp))

                // ── 金额大显示 + 算式小字（固定）──
                // 表达式每帧只求值一次；退款模式按绝对值展示，语义由「退款」标记承担
                val live = liveCents()
                AmountDisplay(
                    expr = expr,
                    displayCents = if (refundMode) kotlin.math.abs(live) else live,
                    refund = refundMode,
                )
                if (expr.isNotBlank() && live == 0L) {
                    Text(
                        "金额需大于 0",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MiuixPrimary,
                        textAlign = TextAlign.Center,
                    )
                }

                // 金额区与分类区之间收紧（原来 6.dp）：省下来的高度全给分类区
                Spacer(Modifier.height(4.dp))

                // ── 分类宫格（两级菜单）：一级网格 + 二级下拉 ──
                // 转账没有分类语义，把这块固定高度的宫格换成「转出 → 转入」账户选择：
                // 宫格的位置正好在金额与键盘之间，是转账唯一缺失的必填项。
                if (type == TransactionType.TRANSFER) {
                    TransferPanel(
                        modifier = Modifier.weight(1f),
                        from = account,
                        to = transferToAccount,
                        legacy = legacyTransfer,
                        onPickFrom = { showAccountDialog = true },
                        onPickTo = { showTransferToDialog = true },
                        onSwap = {
                            if (account.isBlank() && transferToAccount.isBlank()) {
                                // 两个都没选：这一下正好去选，避免静默无反应
                                showTransferToDialog = true
                            } else {
                                // 账户已改成可选，允许「只有一侧有值」的状态互换，否则点了没反应
                                val from = account
                                account = transferToAccount
                                transferToAccount = from
                            }
                        },
                    )
                } else {
                    CategoryGrid(
                        // weight(1f) 吃掉上方压缩出来的全部剩余高度；
                        // 再加上下内边距，让一级/二级分类网格舒展、不与金额区和日期行贴死
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        type = type,
                        selectedParentId = selectedParentId,
                        selectedCategoryId = selectedCategoryId,
                        expandedParentId = expandedParentId,
                        onParentClick = { id ->
                            selectedParentId = id
                            selectedCategoryId = id
                            // 再点同一个图标 = 收起；点别的图标 = 展开那个
                            expandedParentId = if (expandedParentId == id) null else id
                        },
                        onSelectCategory = { selectedCategoryId = it },
                    )
                }

                // ── 键盘上方功能区：日期/备注行 + 功能 chip 排 ──
                // 三者之间统一使用 cardGap，避免栏目互相挤压、或与键盘重叠
                Spacer(Modifier.height(LocalDimens.current.cardGap))

                // ── 对方资料（仅借入 / 借出）：直接在面板上就地编辑，不再藏进「更多」──
                if (borrowDirection != null) {
                    Spacer(Modifier.height(LocalDimens.current.cardGap))
                    CounterpartySection(
                        direction = borrowDirection,
                        name = counterparty,
                        contact = counterpartyContact,
                        org = counterpartyOrg,
                        onNameChange = { counterparty = it.take(30) },
                        onContactChange = { counterpartyContact = it.take(50) },
                        onOrgChange = { counterpartyOrg = it.take(40) },
                    )
                }

                // ── 日期 + 备注行（固定于键盘上方）；备注就地编辑，不弹二次弹窗 ──
                DateNoteRow(
                    dateTime = dateTime,
                    note = note,
                    onPickDate = {
                        endNoteEditing()
                        showDatePicker = true
                    },
                    onEditNote = { noteEditing = true },
                )

                // 面板内反馈（面板开着时全局 snackbar 会被抽屉挡住）
                PanelNotice(panelNotice)

                Spacer(Modifier.height(LocalDimens.current.cardGap))

                // ── 功能选择排：常用项横滑 + 「更多」常驻收纳低频项 ──
                ActionRow(
                    account = account,
                    tags = tags,
                    tagPicked = tagPicked,
                    moreActive = discountOn || excludeFromStats || excludeFromBudget ||
                        moreFilled || attachments.isNotEmpty() || splits.size > 1,
                    onPickAccount = { showAccountDialog = true },
                    onPickTags = { showTagDialog = true },
                    onOpenActions = { showActionsDialog = true },
                    // 转账的转出 / 转入账户在中部 TransferPanel 里选，功能排不再重复一个账户 chip
                    showAccountChip = type != TransactionType.TRANSFER,
                    templateCount = templates.size.takeIf { it > 0 },
                    onOpenTemplates = { showTemplateDialog = true },
                    refund = refundMode,
                    showRefundEntry = type == TransactionType.EXPENSE,
                    onToggleRefund = { refundMode = !refundMode },
                    splitCount = splits.size,
                    // 转账没有组合概念；退款是负数支出，拆账户语义不成立
                    showSplitEntry = type != TransactionType.TRANSFER && !refundMode,
                    onOpenSplit = { showSplitDialog = true },
                    discountOn = discountOn,
                    onOpenDiscount = { discountOn = !discountOn },
                    attachmentCount = attachments.size,
                    onOpenAttachments = { showAttachmentDialog = true },
                )

                // 优惠录入：开启优惠后展示（点开用面板内的自定义键盘输入）
                if (discountOn) {
                    Spacer(Modifier.height(LocalDimens.current.cardGap))
                    DiscountInputRow(
                        discountCents = discountCents,
                        onClick = {
                            endNoteEditing()
                            discountEditing = true
                        },
                    )
                }

                Spacer(Modifier.height(LocalDimens.current.cardGap))

                // ── 数字键盘（固定底部）：右侧一栏为四则运算 ──
                Column {
                    KeyRow {
                        NumKey("1", Modifier.weight(1f)) { appendDigit('1') }
                        NumKey("2", Modifier.weight(1f)) { appendDigit('2') }
                        NumKey("3", Modifier.weight(1f)) { appendDigit('3') }
                        // 长按退格 = 清空整串
                        FuncKey("⌫", Modifier.weight(1f), onClick = { backspace() }, onLongClick = { expr = "" })
                    }
                    KeyRow {
                        NumKey("4", Modifier.weight(1f)) { appendDigit('4') }
                        NumKey("5", Modifier.weight(1f)) { appendDigit('5') }
                        NumKey("6", Modifier.weight(1f)) { appendDigit('6') }
                        CalcKey("÷", Modifier.weight(0.5f)) { appendOp('÷') }
                        CalcKey("＋", Modifier.weight(0.5f)) { appendOp('+') }
                    }
                    KeyRow {
                        NumKey("7", Modifier.weight(1f)) { appendDigit('7') }
                        NumKey("8", Modifier.weight(1f)) { appendDigit('8') }
                        NumKey("9", Modifier.weight(1f)) { appendDigit('9') }
                        CalcKey("×", Modifier.weight(0.5f)) { appendOp('×') }
                        CalcKey("−", Modifier.weight(0.5f)) { appendOp('-') }
                    }
                    KeyRow {
                        NumKey(".", Modifier.weight(1f)) { appendDigit('.') }
                        NumKey("0", Modifier.weight(1f)) { appendDigit('0') }
                        // 再记：仅新增态提供（编辑态「再记」语义混乱）
                        if (existing == null) {
                            Box(modifier = Modifier.weight(1f).padding(3.dp)) {
                                top.yukonga.miuix.kmp.basic.TextButton(
                                    text = "再记",
                                    onClick = { saveAndContinue() },
                                    enabled = !submitting,
                                    cornerRadius = Radius.xxl,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .ijiButtonFocusRing(
                                            shape = com.kyant.capsule.ContinuousRoundedRectangle(Radius.xxl)
                                        ),
                                )
                            }
                        }
                        // 完成：始终是「落库」（表达式在落库时求值），语义不再随输入变化
                        Box(modifier = Modifier.weight(if (existing == null) 1f else 2f).padding(3.dp)) {
                            top.yukonga.miuix.kmp.basic.Button(
                                onClick = { saveAndClose() },
                                enabled = live != 0L && !submitting,
                                cornerRadius = Radius.xxl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .ijiButtonFocusRing(
                                        shape = com.kyant.capsule.ContinuousRoundedRectangle(Radius.xxl)
                                    ),
                                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(
                                    if (submitting) "保存中…" else "完成",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            // 备注输入浮层：键盘弹出时由 imePadding 顶到键盘上沿（不再手工测量偏移）
            if (noteEditing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    NoteEditor(
                        note = note,
                        onNoteChange = { note = it },
                        onDone = { endNoteEditing() },
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
            }
            // 优惠金额键盘：与主面板同一套按键，不唤起系统输入法
            if (discountEditing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    DiscountKeypad(
                        initialCents = discountCents,
                        onCancel = { discountEditing = false },
                        onConfirm = {
                            discountCents = it
                            discountEditing = false
                        },
                    )
                }
            }
        }
    }
}

// ── section 子 Composable（便于复用与预览）──

@Composable
private fun AmountDisplay(expr: String, displayCents: Long, refund: Boolean = false) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 计算过程（实时算式）：字号 bodyMedium → bodySmall，留白收紧
            Text(
                if (expr.isNotEmpty()) expr else "",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 「退款」标记：金额本身按正数展示（用户敲的就是 25），语义交给这枚标记，
                // 免得用户看到自己敲的 25 变成 -25 以为输错了
                if (refund) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(IncomeGreen.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("退款", style = MaterialTheme.typography.bodySmall, color = IncomeGreen)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    formatMoney(displayCents),
                    // 负数金额（红字冲销）同样高亮，否则用户会以为键盘没生效
                    color = if (displayCents != 0L) MaterialTheme.colorScheme.onSurface else TextSecondary,
                    // 36 → 32sp：金额仍是视觉主角，但不再把面板上半部分撑开
                    fontSize = MaterialTheme.typography.headlineLarge.fontSize,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 组合付款的一行草稿：金额文本自持，避免回写打断输入 */
private data class SplitRow(val account: String = "", val amount: String = "") {
    val cents: Long get() = parseCents(amount)
}

/**
 * 组合付款弹窗：把一笔账拆到多个账户上（信用卡额度不足、家庭共同消费等）。
 *
 * 金额按整数分解析（不经 Double）；合计与面板金额一致、且至少两个有效账户才能确定。
 */
@Composable
private fun SplitPaymentDialog(
    show: Boolean,
    accounts: List<String>,
    splits: List<PaymentSplit>,
    totalCents: Long,
    onConfirm: (List<PaymentSplit>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var rows by remember(show) {
        mutableStateOf(
            splits.map {
                SplitRow(it.account, if (it.amountCents == 0L) "" else "%.2f".format(it.amountCents / 100.0))
            },
        )
    }
    var pickerRowIndex by remember(show) { mutableStateOf<Int?>(null) }
    val sumCents = rows.sumOf { it.cents }
    val diffCents = totalCents - sumCents
    val validCount = rows.count { it.account.isNotBlank() && it.cents != 0L }

    /** 平均分：除不尽的余数给最后一行 */
    fun splitEvenly(): List<SplitRow> {
        if (rows.isEmpty()) return rows
        val base = totalCents / rows.size
        return rows.mapIndexed { i, row ->
            val cents = if (i == rows.lastIndex) totalCents - base * (rows.size - 1) else base
            row.copy(amount = "%.2f".format(cents / 100.0))
        }
    }

    /** 最后一行补剩余金额 */
    fun fillRemainder(): List<SplitRow> {
        if (rows.isEmpty()) return rows
        val rest = totalCents - rows.dropLast(1).sumOf { it.cents }
        return rows.mapIndexed { i, row ->
            if (i == rows.lastIndex) row.copy(amount = "%.2f".format(rest / 100.0)) else row
        }
    }

    IjiDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "组合付款",
        confirmText = "确定",
        confirmEnabled = validCount >= 2 && diffCents == 0L,
        onConfirm = {
            onConfirm(rows.filter { it.account.isNotBlank() && it.cents != 0L }.map { PaymentSplit(it.account, it.cents) })
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "合计 ${formatMoney(sumCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (diffCents == 0L) IncomeGreen else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        totalCents == 0L -> "请先输入金额"
                        diffCents == 0L -> "与金额一致"
                        diffCents > 0L -> "还差 ${formatMoney(diffCents)}"
                        else -> "超出 ${formatMoney(-diffCents)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (diffCents == 0L) TextSecondary else MaterialTheme.colorScheme.onSurface,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 账户：复用统一账户选择器，避免第三套列表
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { pickerRowIndex = index }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (row.account.isNotBlank()) {
                                AccountIconImage(iconKey = row.account, size = 16.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                row.account.ifBlank { "选择账户" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (row.account.isBlank()) TextSecondary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Box(modifier = Modifier.width(104.dp)) {
                            top.yukonga.miuix.kmp.basic.TextField(
                                value = row.amount,
                                onValueChange = { raw ->
                                    val clean = sanitizeAmountInput(raw)
                                    rows = rows.toMutableList().also { it[index] = it[index].copy(amount = clean) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = "金额",
                                useLabelAsPlaceholder = true,
                            )
                        }
                        // 至少保留一行，否则弹窗会空掉
                        if (rows.size > 1) {
                            Text(
                                "×",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { rows = rows.toMutableList().also { it.removeAt(index) } }
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "＋ 添加账户",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiuixPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { rows = rows + SplitRow() }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                if (totalCents != 0L) {
                    Text(
                        "平均分",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MiuixPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable { rows = splitEvenly() }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "补剩余",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MiuixPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable { rows = fillRemainder() }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
                // 已存在组合时给出撤销入口：否则「至少两个账户」的约束会让人删不掉
                if (splits.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "清除",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable { onConfirm(emptyList()) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
            }
            if (validCount < 2) {
                Text(
                    "至少需要两个账户才能组合付款",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }

    // 行内账户选择：复用统一弹窗，选中后只回写这一行
    AccountPickerDialog(
        show = pickerRowIndex != null,
        accounts = accounts,
        selected = rows.getOrNull(pickerRowIndex ?: -1)?.account.orEmpty(),
        title = "选择账户",
        onDismissRequest = { pickerRowIndex = null },
        onConfirm = { picked ->
            val idx = pickerRowIndex
            if (idx != null && idx in rows.indices) {
                rows = rows.toMutableList().also { it[idx] = it[idx].copy(account = picked) }
            }
            pickerRowIndex = null
        },
    )
}

/**
 * 转账面板：转出账户 → 转入账户。
 *
 * 此前转账只有一个「不分类，请直接输入金额」的占位文案，`MainViewModel` 对 TRANSFER
 * 直接 return，于是这笔账既不改变任何账户余额、也没留下资金流向，等于白记。
 * 这里把两个账户显式摆出来，且强制两者不同（同账户即内部调拨，余额总额不变，记了没意义）。
 */
@Composable
private fun TransferPanel(
    modifier: Modifier = Modifier,
    from: String,
    to: String,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    /** 互换转出 / 转入 */
    onSwap: () -> Unit = {},
    /** 是否为「转入账户」字段引入之前写入的旧版转账（需要用户补全） */
    legacy: Boolean = false,
) {
    val same = from.isNotBlank() && from == to
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 左右各内缩 4dp：卡片不贴面板边缘，宽度仍接近满幅，像一条「选择栏」
            .padding(horizontal = 4.dp)
            .padding(top = 2.dp),
        // 关键：内容**顶对齐**（原来是 CenterVertically）。
        // 本区域由 weight(1f) 吃掉金额与键盘之间的剩余高度，居中会让两张卡漂到区域正中，
        // 于是金额下方出现一大段无意义空白、整块重心偏下；顶对齐后转账区紧贴金额显示。
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TransferAccountCard(
            role = "转出账户",
            account = from,
            icon = MiuixIcons.Send,
            onClick = onPickFrom,
        )
        // 连接符：浅底圆钮 + 向下箭头，点一下互换上下两个账户的方向
        TransferConnector(onSwap = onSwap)
        TransferAccountCard(
            role = "转入账户",
            account = to,
            icon = MiuixIcons.Download,
            onClick = onPickTo,
        )
        when {
            legacy && to.isBlank() -> Text(
                "此账单为旧版转账，未记录转入账户（当时未联动余额）；补选后保存才会联动",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
            same -> Text(
                "转出与转入是同一个账户，属于内部调拨",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp),
            )
            from.isBlank() && to.isBlank() -> Text(
                // 账户是可选项：没有账户（或不想联动余额）时照样能记一笔转账
                "账户可选：不选也能保存，只是不联动余额",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

/** 两张账户卡片之间的连接符：浅底圆钮 + 手绘向下箭头，点击互换两个账户 */
@Composable
private fun TransferConnector(onSwap: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable(onClick = onSwap),
        contentAlignment = Alignment.Center,
    ) {
        // 自己画箭头：Miuix 没有 ArrowDown，ExpandMore 的字形不是箭头
        Canvas(Modifier.size(14.dp)) {
            val stroke = 1.8.dp.toPx()
            val color = TextSecondary
            drawLine(
                color,
                Offset(size.width * 0.22f, size.height * 0.34f),
                Offset(size.width * 0.5f, size.height * 0.66f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(size.width * 0.78f, size.height * 0.34f),
                Offset(size.width * 0.5f, size.height * 0.66f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * 记账模板选择弹窗：把「每天都记的同一笔」一键带出。
 *
 * 模板此前只有管理页能建能改，记一笔面板完全读不到，等于做了没用。
 * 这里列出现有模板，点击即回填类型/分类/金额/备注/账户/标签。
 */
@Composable
private fun TemplatePickerDialog(
    show: Boolean,
    templates: List<TransactionTemplate>,
    onPick: (TransactionTemplate) -> Unit,
    onDismissRequest: () -> Unit,
) {
    IjiDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "选择模板",
        confirmText = "完成",
        onConfirm = onDismissRequest,
    ) {
        if (templates.isEmpty()) {
            Text(
                "暂无模板。可在「我的 → 记账模板」中把常记的账单存成模板。",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                templates.forEach { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPick(template) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                template.name.ifBlank { "未命名模板" },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            val categoryName = template.categoryId.takeIf { it.isNotBlank() }
                                ?.let { runCatching { CategoryCatalog.of(it).name }.getOrNull() }
                            // 副行只拼非空项，避免出现孤零零的「 · 」
                            val parts = buildList {
                                categoryName?.let(::add)
                                template.account.takeIf { it.isNotBlank() }?.let(::add)
                                template.tags.takeIf { it.isNotEmpty() }
                                    ?.let { add(it.joinToString(" ") { tag -> "#$tag" }) }
                                // 新增能力的可见标记：应用前就知道模板带了什么
                                if (template.splits.size >= 2) add("组合付款")
                                template.discountCents.takeIf { it > 0L }?.let { add("优惠 ${formatMoney(it)}") }
                                if (template.excludeFromStats || template.excludeFromBudget) {
                                    add(
                                        when {
                                            template.excludeFromStats && template.excludeFromBudget -> "均不计入"
                                            template.excludeFromStats -> "不计收支"
                                            else -> "不计预算"
                                        },
                                    )
                                }
                            }
                            if (parts.isNotEmpty()) {
                                Text(
                                    parts.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (template.amountCents != 0L) {
                            Text(
                                formatMoney(template.amountCents),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (template.type) {
                                    TransactionType.EXPENSE -> MaterialTheme.colorScheme.onSurface
                                    TransactionType.INCOME -> IncomeGreen
                                    TransactionType.TRANSFER -> TextSecondary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 转账面板里的单个账户卡片：**左图标 / 中标题 / 右取值**。
 *
 * 原来是「标题 + 值」两段纯文本，看不出可点，也没有层次。这里做成通栏浅灰圆角卡片，
 * 左侧加圆形图标底（转出=Send，转入=Download），右侧给值 + `>` 指示，明确「点一下就能选」。
 */
@Composable
private fun TransferAccountCard(
    role: String,
    account: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val picked = account.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            // 通栏浅灰圆角底：与白色面板拉开层次，一眼看出是「可点的选择栏」
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧图标：圆形主色浅底，转账方向一眼可辨
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MiuixPrimary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MiuixPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            role,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        // 值独占剩余宽度并右对齐：账户名过长时就地省略，右侧箭头不会被挤掉
        Text(
            if (picked) account else "点击选择",
            style = MaterialTheme.typography.bodyLarge,
            color = if (picked) MaterialTheme.colorScheme.onSurface else TextSecondary,
            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 一级分类每行个数（与二级下拉的箭头列共用同一栅格） */
private const val CATEGORY_GRID_SPAN = 5

/**
 * 分类选择：两级菜单（原地内嵌，无弹窗），对照 AutoAccounting 记账页的交互。
 * 一级菜单：小号圆形图标网格（一行 [CATEGORY_GRID_SPAN] 个），有子分类的图标带「···」角标；
 * 点击图标 → 其下方原位插入带箭头的子分类下拉卡片（展开/收起带动画），
 * 再点同一图标、或点面板任意非可点区域 → 收起（收起不取消已选分类）。
 *
 * 两级选中态都用「分类色」表达：一级是圆形底填充 + 文字染成分类色，
 * 二级是浅色底 + 分类色描边。仅用于支出 / 收入；转账走 [TransferPanel]。
 */
@Composable
private fun CategoryGrid(
    modifier: Modifier = Modifier,
    type: TransactionType,
    selectedParentId: String,
    selectedCategoryId: String,
    expandedParentId: String?,
    onParentClick: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
) {
    val parents = CategoryCatalog.parentsOfType(type)
    // 收起动画期间内容不能跟着变空（否则 shrink 会闪一下），
    // 记一份「最近展开的一级」，expandedParentId 为 null 时用它渲染、visible=false 播退场
    var lastExpandedId by remember { mutableStateOf(selectedParentId) }
    LaunchedEffect(expandedParentId) { if (expandedParentId != null) lastExpandedId = expandedParentId }
    val displayParentId = expandedParentId ?: lastExpandedId

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 一级网格 + 插入的下拉卡片会撑高内容，整体滚动
            .verticalScroll(rememberScrollState()),
    ) {
        parents.chunked(CATEGORY_GRID_SPAN).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { parent ->
                    ParentCategoryItem(
                        category = parent,
                        selected = parent.id == selectedParentId,
                        hasChildren = CategoryCatalog.childrenOf(parent.id).isNotEmpty(),
                        onClick = { onParentClick(parent.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(CATEGORY_GRID_SPAN - row.size) { Spacer(Modifier.weight(1f)) }
            }

            // ── 下拉卡片：插在「当前展开的一级」所在行下方 ──
            val expandedIdx = row.indexOfFirst { it.id == displayParentId }
            val expandedChildren = CategoryCatalog.childrenOf(displayParentId)
            AnimatedVisibility(
                visible = expandedParentId != null && expandedIdx >= 0 && expandedChildren.isNotEmpty(),
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                ) + fadeOut(),
            ) {
                SubCategoryPanel(
                    items = listOf(CategoryCatalog.of(displayParentId)) + expandedChildren,
                    selectedCategoryId = selectedCategoryId,
                    onSelectCategory = onSelectCategory,
                    arrowColumn = expandedIdx.coerceAtLeast(0),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * 子分类下拉卡片：圆角浅底 + 顶部小三角（对准选中的一级图标），内部子分类一行 5 个。
 * 首格放一级本身（与旧版二级网格一致：不选子分类时直接用一级）。
 */
@Composable
private fun SubCategoryPanel(
    items: List<Category>,
    selectedCategoryId: String,
    onSelectCategory: (String) -> Unit,
    arrowColumn: Int,
) {
    val panelColor = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        // 箭头：逐列 weight(1f) 均分，三角**居中放在选中那一列里** ——
        // 与一级图标的居中方式完全一致（图标也是格内居中），屏宽怎么变都不会错位。
        // （旧写法用「两侧 weight 占位」列，三角自身宽度参与分配，导致中心偏移、看着歪）
        Row(Modifier.fillMaxWidth()) {
            repeat(CATEGORY_GRID_SPAN) { column ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (column == arrowColumn.coerceIn(0, CATEGORY_GRID_SPAN - 1)) {
                        Triangle(color = panelColor)
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.xxl))
                .background(panelColor)
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            items.chunked(CATEGORY_GRID_SPAN).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { category ->
                        CategoryCell(
                            category = category,
                            selected = category.id == selectedCategoryId,
                            onClick = { onSelectCategory(category.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(CATEGORY_GRID_SPAN - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * 下拉卡片顶部的小三角：**圆角**（旋转 45° 的圆角方块），颜色与卡片底色一致。
 * 只露出上半截尖端 —— 下半截被紧随其后绘制的卡片盖住，省掉手写圆角贝塞尔路径。
 */
@Composable
private fun Triangle(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .rotate(45f)
            .clip(RoundedCornerShape(Radius.xs))
            .background(color),
    )
}

/**
 * 一级分类：**小号**圆形背景图标 + 文字（对照 AutoAccounting 的密度）。
 *
 * 选中时圆形底填充**分类色**（如餐饮=紫色/橙色）、图标转白、文字染成分类色；
 * 未选中只留 12% 浅色底。有子分类时右下角带「···」角标，提示可点开下拉。
 * 点击区是整个格子（≥48dp），图标本身缩小不影响可用性。
 */
@Composable
private fun ParentCategoryItem(
    category: Category,
    selected: Boolean,
    hasChildren: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(category.color)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.lg))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            CategoryIconImage(
                name = category.name,
                color = accent,
                size = 40.dp,
                selected = selected,
                categoryId = category.id,
            )
            if (hasChildren) {
                ChildrenBadge(
                    accent = accent,
                    modifier = Modifier
                        // 贴在图标圆形底的右下角，作为小徽章（不占额外行高）
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 「该一级下还有二级分类」徽章：胶囊浅底（比分类色更浅）+ 三个 2dp 小圆点，横向均匀、居中。
 *
 * 是否显示由调用方按数据模型判断（[CategoryCatalog.childrenOf] 非空），
 * 没有二级分类的一级**完全不渲染**这颗徽章。
 */
@Composable
private fun ChildrenBadge(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(2.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}

/**
 * 日期 + 备注行。
 *
 * **备注点击后由 [NoteEditor] 浮层就地输入**（**不再弹二次弹窗**）：输入条靠 `imePadding()`
 * 顶到键盘上沿，面板本体不动。
 *
 * 稳定性：编辑态与展示态共用同一字号 / 内边距，备注固定单行（长备注省略号，不撑高行）。
 * 长度上限 50 字（与原来的备注弹窗一致）。
 */
@Composable
private fun DateNoteRow(
    modifier: Modifier = Modifier,
    dateTime: LocalDateTime,
    note: String,
    onPickDate: () -> Unit,
    onEditNote: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 日期：不再配日历图标（纯文字更干净），点击整块即选日期
        Text(
            dateTime.format(DateTimeFormatter.ofPattern("M月d日 EEEE HH:mm", Locale.CHINESE)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.xl))
                .clickable { onPickDate() }
                .padding(horizontal = 2.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(10.dp))

        // 展示态：整块（含右侧空白）可点，一点即打开浮层输入条
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Radius.xl))
                .clickable { onEditNote() }
                .padding(horizontal = 2.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                if (note.isEmpty()) "点击填写备注" else note,
                style = MaterialTheme.typography.bodyMedium,
                color = if (note.isEmpty()) TextSecondary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 面板内提示条：面板打开时全局 snackbar 会被抽屉遮住，校验 / 应用模板这类
 * 「必须当场看到」的反馈统一走这里。空内容时不占高度。
 */
@Composable
private fun PanelNotice(text: String?) {
    // 退场动画期间 text 已置空，先留住最后一条，避免闪成空白
    var lastText by remember { mutableStateOf("") }
    if (text != null) lastText = text
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
    ) {
        Text(
            lastText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(MiuixPrimary.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MiuixPrimary,
        )
    }
}

/** 备注输入条（浮层）：与 [DateNoteRow] 同样式，靠 `imePadding()` 落在键盘上沿 */
@Composable
private fun NoteEditor(
    modifier: Modifier = Modifier,
    note: String,
    onNoteChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val noteStyle = MaterialTheme.typography.bodyMedium
    val focusRequester = remember { FocusRequester() }
    // onFocusChanged 的首次回调就是「未聚焦」，直接当收起信号会让编辑态一出现就退出，
    // 所以先记「曾聚焦」，只有聚焦过再失焦（点空白 / 点日期 / 点键盘等）才收起。
    var everFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = note,
            onValueChange = { onNoteChange(it.take(50)) },
            singleLine = true,
            textStyle = noteStyle.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start),
            cursorBrush = SolidColor(MiuixPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) everFocused = true else if (everFocused) onDone()
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (note.isEmpty()) {
                        Text("备注", style = noteStyle, color = TextSecondary, maxLines = 1)
                    }
                    innerTextField()
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "完成",
            style = MaterialTheme.typography.bodyMedium,
            color = MiuixPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .clickable { onDone() }
                .padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}

@Composable
private fun ActionRow(
    account: String,
    tags: List<String>,
    tagPicked: Boolean,
    /** 收纳起来的低频项是否有内容 / 已开启 */
    moreActive: Boolean,
    onPickAccount: () -> Unit,
    onPickTags: () -> Unit,
    onOpenActions: () -> Unit,
    /**
     * 是否显示账户 chip。转账恒为 false：转出 / 转入在中部 [TransferPanel] 里选，
     * 底部再放一个选账户的胶囊只是重复入口。
     */
    showAccountChip: Boolean = true,
    /** 模板入口：为 null 表示没有模板，不显示该 chip */
    templateCount: Int? = null,
    onOpenTemplates: () -> Unit = {},
    /** 退款开关：仅支出显示 */
    refund: Boolean = false,
    showRefundEntry: Boolean = true,
    onToggleRefund: () -> Unit = {},
    /** 组合付款：转账 / 退款下不适用 */
    splitCount: Int = 0,
    showSplitEntry: Boolean = true,
    onOpenSplit: () -> Unit = {},
    /** 优惠 */
    discountOn: Boolean = false,
    onOpenDiscount: () -> Unit = {},
    /** 附件 */
    attachmentCount: Int = 0,
    onOpenAttachments: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 常用项横滑；「更多」固定在右侧，窄屏也能直接看到
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (templateCount != null) {
                ActionChip(label = "模板 $templateCount", selected = false, onClick = onOpenTemplates)
            }
            if (showAccountChip) {
                ActionChip(
                    // 空账户时不能留一个空胶囊：这是支出 / 收入唯一的选账户入口
                    label = account.ifBlank { "账户" },
                    selected = true,
                    onClick = onPickAccount,
                    leadingIcon = { AccountIconImage(iconKey = account, size = 16.dp) },
                )
            }
            // 退款：把「先按 − 再输金额」这个暗号变成显式开关
            if (showRefundEntry) {
                ActionChip(label = "退款", selected = refund, onClick = onToggleRefund)
            }
            // 常用项直接展开，不再藏进「更多」：少一次点击
            if (showSplitEntry) {
                ActionChip(
                    label = if (splitCount > 1) "组合付款 $splitCount" else "组合付款",
                    selected = splitCount > 1,
                    onClick = onOpenSplit,
                )
            }
            ActionChip(label = "优惠", selected = discountOn, onClick = onOpenDiscount)
            ActionChip(
                label = if (tagPicked) "标签 ${tags.size}" else "标签",
                selected = tagPicked,
                onClick = onPickTags,
            )
            ActionChip(
                label = if (attachmentCount > 0) "附件 $attachmentCount" else "附件",
                selected = attachmentCount > 0,
                onClick = onOpenAttachments,
            )
        }
        Spacer(Modifier.width(6.dp))
        ActionChip(label = "更多", selected = moreActive, onClick = onOpenActions)
    }
}

/**
 * 对方资料区（借入 / 借出专用）：姓名 / 联系方式 / 单位，三个字段并排一行。
 *
 * 为什么放在面板里而不是继续塞进「更多」：借贷是**按人**记账的，对方是谁比商家重要得多；
 * 藏在「更多」里等于没有录入口（实测老数据 counterparty 几乎全空）。
 *
 * 并排而不是竖排三个输入框：面板高度固定 85%，竖排会把分类宫格挤没；
 * 一行三个短字段既常驻可见，又只占一格高度。收起 / 展开那套也一并省掉 —— 少一次点击。
 */
@Composable
private fun CounterpartySection(
    direction: com.iji.app.ui.assets.BorrowDirection,
    name: String,
    contact: String,
    org: String,
    onNameChange: (String) -> Unit,
    onContactChange: (String) -> Unit,
    onOrgChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            // 不透明：键盘弹出时这一块会浮到分类宫格上方，半透明会把下面的图标透出来
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            if (direction == com.iji.app.ui.assets.BorrowDirection.BORROW) "对方资料 · 出借人" else "对方资料 · 借款人",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            top.yukonga.miuix.kmp.basic.TextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.weight(1f),
                label = "姓名",
                useLabelAsPlaceholder = true,
                // 三个字段并排，必须单行：多行会把整块高度撑开、挤掉分类宫格
                singleLine = true,
            )
            top.yukonga.miuix.kmp.basic.TextField(
                value = contact,
                onValueChange = onContactChange,
                modifier = Modifier.weight(1.3f),
                label = "电话 / 微信",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            top.yukonga.miuix.kmp.basic.TextField(
                value = org,
                onValueChange = onOrgChange,
                modifier = Modifier.weight(1f),
                label = "单位",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
        }
    }
}

/**
 * 「更多」弹窗：商家 / 对方 / 垫付状态 / 已对账。
 *
 * 这些字段实体与领域模型里早已存在，但此前没有录入入口，数据永远是空值，
 * 商家归因、应收核销、对账也就无从谈起。此处统一补齐。
 */
@Composable
private fun MoreInfoDialog(
    show: Boolean,
    merchant: String,
    counterparty: String,
    reconciled: Boolean,
    onDismissRequest: () -> Unit,
    /** 借入 / 借出分类下「对方」已由面板上的「对方资料」区承担，这里不再重复一个同名输入框 */
    showCounterparty: Boolean = true,
    onConfirm: (String, String, Boolean) -> Unit,
) {
    var m by remember(show) { mutableStateOf(merchant) }
    var c by remember(show) { mutableStateOf(counterparty) }
    var r by remember(show) { mutableStateOf(reconciled) }

    IjiDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "更多信息",
        confirmText = "完成",
        onConfirm = { onConfirm(m, c, r) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            top.yukonga.miuix.kmp.basic.TextField(
                value = m,
                onValueChange = { m = it.take(30) },
                modifier = Modifier.fillMaxWidth(),
                label = "商家 / 收款方",
                useLabelAsPlaceholder = true,
            )
            if (showCounterparty) {
                top.yukonga.miuix.kmp.basic.TextField(
                    value = c,
                    onValueChange = { c = it.take(30) },
                    modifier = Modifier.fillMaxWidth(),
                    label = "对方（借款人 / 报销项目）",
                    useLabelAsPlaceholder = true,
                )
            }
            IjiSwitchRow(
                title = "已对账",
                desc = "与银行 / 支付账单核对无误后勾选",
                checked = r,
                onCheckedChange = { r = it },
            )
        }
    }
}

// ── 计入标记 / 优惠 / 附件 ──

/** 标记弹窗中的单行开关已统一到 [com.iji.app.ui.components.IjiSwitchRow] */

/** 优惠金额行（开启优惠后展示）：只读展示，点击后由面板内的自定义键盘录入 */
@Composable
private fun DiscountInputRow(
    discountCents: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "优惠金额",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (discountCents == 0L) "点击输入" else formatMoney(discountCents),
            style = MaterialTheme.typography.bodyMedium,
            color = if (discountCents == 0L) TextSecondary else MiuixPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 优惠金额键盘：复用面板底部的自定义键盘（无系统输入法）。
 * 只接受数字与一个小数点，最多两位小数。
 */
@Composable
private fun DiscountKeypad(
    initialCents: Long,
    onCancel: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var text by remember {
        mutableStateOf(if (initialCents == 0L) "" else "%.2f".format(initialCents / 100.0))
    }
    fun type(ch: Char) {
        if (ch == '.') {
            if (text.contains('.')) return
            text = if (text.isEmpty()) "0." else "$text."
            return
        }
        val dot = text.indexOf('.')
        if (dot >= 0 && text.length - dot > 2) return
        if (dot < 0 && text.length >= 9) return
        text = if (text == "0") ch.toString() else text + ch
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "优惠金额",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${CurrencyDisplay.current.symbol} ${text.ifEmpty { "0" }}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "取消",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onCancel() }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        KeyRow {
            NumKey("1", Modifier.weight(1f)) { type('1') }
            NumKey("2", Modifier.weight(1f)) { type('2') }
            NumKey("3", Modifier.weight(1f)) { type('3') }
            FuncKey("⌫", Modifier.weight(1f), onClick = { if (text.isNotEmpty()) text = text.dropLast(1) })
        }
        KeyRow {
            NumKey("4", Modifier.weight(1f)) { type('4') }
            NumKey("5", Modifier.weight(1f)) { type('5') }
            NumKey("6", Modifier.weight(1f)) { type('6') }
            CalcKey("清空", Modifier.weight(1f)) { text = "" }
        }
        KeyRow {
            NumKey("7", Modifier.weight(1f)) { type('7') }
            NumKey("8", Modifier.weight(1f)) { type('8') }
            NumKey("9", Modifier.weight(1f)) { type('9') }
            // 快捷补两个 0（整百金额常用）
            NumKey("00", Modifier.weight(1f)) { type('0'); type('0') }
        }
        KeyRow {
            NumKey(".", Modifier.weight(1f)) { type('.') }
            NumKey("0", Modifier.weight(1f)) { type('0') }
            Box(modifier = Modifier.weight(2f).padding(3.dp)) {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { onConfirm(parseCents(text.trimEnd('.'))) },
                    cornerRadius = Radius.xxl,
                    // 与面板主键盘同一个主色样式，避免看起来像「不可点」
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text("完成", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 附件弹窗：添加 / 移除图片（取持久化读取权限，关闭后列表可长期访问） */
@Composable
private fun AttachmentDialog(
    show: Boolean,
    attachments: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
    context: android.content.Context,
) {
    var list by remember(show) { mutableStateOf(attachments) }
    // 弹窗内提示（snackbar 会被面板/弹窗挡住）
    var error by remember(show) { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // P0 修复：授权失败时不写入 URI（否则重启后该附件无法读取）
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        }.getOrElse { false }
        if (granted) {
            list = list + uri.toString()
            error = null
        } else {
            error = "无法获取该图片的长期访问权限"
        }
    }
    IjiDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "附件（${list.size}）",
        confirmText = "完成",
        onConfirm = { onConfirm(list) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                list.forEach { uri ->
                    Box {
                        ThumbnailImage(uri = uri, size = 72.dp)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .clickable { list = list - uri },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("×", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            top.yukonga.miuix.kmp.basic.Button(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .ijiButtonFocusRing(
                        shape = com.kyant.capsule.ContinuousRoundedRectangle(
                            top.yukonga.miuix.kmp.basic.ButtonDefaults.CornerRadius,
                        ),
                    ),
            ) {
                Text("添加图片", color = Color.White)
            }
        }
    }
}

/** 本地解码 URI 为缩略图（不引入第三方图片库） */
@Composable
private fun ThumbnailImage(uri: String, size: Dp) {
    val ctx = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(Radius.sm)),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(RoundedCornerShape(Radius.sm)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("图片", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

// ── 四则运算工具 ──

/** 单次四则运算 */
private fun applyOp(a: BigDecimal, op: Char, b: BigDecimal): BigDecimal = when (op) {
    '+' -> a.add(b)
    '-' -> a.subtract(b)
    '×' -> a.multiply(b)
    '÷' -> if (b.compareTo(BigDecimal.ZERO) != 0) a.divide(b, 6, RoundingMode.HALF_UP) else a
    else -> b
}

/** 混合四则运算：先乘除后加减；对缺失右操作数的孤立运算符做防御丢弃 */
private fun evaluate(nums: List<BigDecimal>, ops: List<Char>): BigDecimal {
    if (nums.isEmpty()) return BigDecimal.ZERO
    val n = nums.toMutableList()
    val o = ops.toMutableList()
    var i = 0
    while (i < o.size) {
        // 孤立运算符（缺右操作数）：丢弃
        if (i + 1 >= n.size) {
            o.removeAt(i)
            continue
        }
        if (o[i] == '×' || o[i] == '÷') {
            val divisor = n[i + 1]
            if (o[i] == '×') {
                n[i] = n[i].multiply(divisor)
            } else if (divisor.compareTo(BigDecimal.ZERO) != 0) {
                n[i] = n[i].divide(divisor, 6, RoundingMode.HALF_UP)
            } else {
                n.removeAt(i + 1)
                o.removeAt(i)
                continue
            }
            n.removeAt(i + 1)
            o.removeAt(i)
        } else {
            i++
        }
    }
    var acc = n[0]
    for (j in o.indices) {
        if (j + 1 >= n.size) break
        acc = if (o[j] == '+') acc.add(n[j + 1]) else acc.subtract(n[j + 1])
    }
    return acc
}

@Composable
private fun NumKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(Radius.xl))
            // 键帽底必须跟着主题走：写死浅灰会让深色下的白字直接看不见
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FuncKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(Radius.xl))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.titleMedium)
    }
}

/** 运算键（÷ ＋ × −）：浅红底红字 */
@Composable
private fun CalcKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(Radius.xl))
            .background(MiuixPrimary.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MiuixPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth()) { content() }
}

/** 「更多」菜单行：左标题、右当前值 + 箭头 */
@Composable
private fun ActionMenuRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = MiuixPrimary)
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 功能选择胶囊（选择账户/报销/退款/标签/更多） */
@Composable
private fun ActionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            // 圆角 10 → 12dp：chip 只有 34dp 高，再大就成胶囊了，保留方形感
            .clip(RoundedCornerShape(Radius.lg))
            .background(if (selected) MiuixPrimary.copy(alpha = 0.12f) else DividerGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingIcon?.invoke()
            if (leadingIcon != null) Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MiuixPrimary else TextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/**
 * 账户选择弹窗：只列「已添加的账户」（[accounts] 由资产页数据提供，不做任何补充）。
 * 选中态用底纹高亮表示，点一下即确认并关闭（没有二次确认步骤）。
 */
@Composable
private fun AccountPickerDialog(
    show: Boolean,
    accounts: List<String>,
    selected: String,
    balances: Map<String, Long> = emptyMap(),
    title: String? = null,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var query by remember(show) { mutableStateOf("") }
    // 账户多时给出搜索，避免在长列表里翻找
    val filtered = if (query.isBlank()) accounts else accounts.filter { it.contains(query.trim(), true) }
    IjiDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
    ) {
        Column {
            if (accounts.size > 8) {
                top.yukonga.miuix.kmp.basic.TextField(
                    value = query,
                    onValueChange = { query = it.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    label = "搜索账户",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
            }
            // 不虚构账户：一个都没添加时如实提示去资产页新建
            if (accounts.isEmpty()) {
                Text(
                    "还没有账户，请先在「资产」页添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                items(filtered, key = { it }) { name ->
                    val isSel = name == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            // 选中态靠底纹高亮，不再用打勾（列表更干净，也少一层视觉噪音）
                            .background(if (isSel) MiuixPrimary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onConfirm(name) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccountIconImage(iconKey = name, size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSel) MiuixPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        balances[name]?.let {
                            Text(formatMoney(it), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 标签选择弹窗：多选 + 自定义。
 *
 * 标签来源改为读 [TagStore]（Preferences DataStore）而非硬编码的 `PresetTags` ——
 * 此前用户在「标签管理」页增删的标签在这里永远看不到，两套标签互不相通。
 * 新增标签同时写回 [TagStore]，让「在这里建」与「在管理页建」等价。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSelectDialog(
    show: Boolean,
    initial: List<String>,
    context: Context,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(initial) }
    var customTag by remember { mutableStateOf("") }
    // 用户标签库（标签管理页维护的那份）
    val userTags by TagStore.observe(context)
        .collectAsStateWithLifecycle(initialValue = TagStore.DEFAULT)
    // 已选项始终排在前面，避免「打了标签却看不见自己打的标签」
    val options = remember(userTags, picked) {
        (userTags + picked.filter { it !in userTags }).distinct()
    }
    val scope = rememberCoroutineScope()

    IjiDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = "选择标签",
        onConfirm = { onConfirm(picked) },
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(
                                    if (tag in picked) IncomeGreen.copy(alpha = 0.14f) else DividerGray,
                                )
                                .clickable {
                                    picked = if (tag in picked) picked - tag else picked + tag
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (tag in picked) IncomeGreen else TextSecondary,
                                fontWeight = if (tag in picked) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    top.yukonga.miuix.kmp.basic.TextField(
                        customTag,
                        { customTag = it.take(8) },
                        Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    top.yukonga.miuix.kmp.basic.Button(
                        onClick = {
                            val tag = customTag.trim()
                            if (tag.isNotEmpty() && tag !in picked) {
                                picked = picked + tag
                                // 同时落到用户标签库，标签管理页能看到、也能在这里再选到
                                scope.launch { TagStore.add(context, tag) }
                            }
                            customTag = ""
                        },
                        modifier = Modifier.ijiButtonFocusRing(
                            shape = com.kyant.capsule.ContinuousRoundedRectangle(
                                top.yukonga.miuix.kmp.basic.ButtonDefaults.CornerRadius
                            )
                        ),
                    ) {
                        Text("添加", color = Color.White)
                    }
                }
            }
    }
}

/** 支出/收入/转账切换按钮 */
@Composable
private fun TypeOption(
    label: String,
    value: TransactionType,
    current: TransactionType,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = value == current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.xl))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onSelect(value) }
            // 纵向内边距 8 → 5dp：三段控件整体更矮
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            // 字号 16 → 14sp（原本走默认 bodyLarge）
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * 二级分类网格项：圆形图标 + 名称。
 *
 * 选中态 = 分类色 12% 浅底 + 1.5dp 分类色描边（背景色弱、描边强，比单靠图标变色更容易在
 * 一排 5 个里被看见）；未选中完全透明，避免未选中项看起来像一排卡片。
 *
 * 点击即通过 [onClick] 把分类 id 回传，调用方（[AddTransactionSheet]）持有 `selectedCategoryId`，
 * 保存时直接用它，不存在"选了没生效"的中间态。
 */
@Composable
private fun CategoryCell(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(category.color)
    val shape = RoundedCornerShape(Radius.lg)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .then(if (selected) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIconImage(
            name = category.name,
            color = accent,
            size = 36.dp,
            selected = selected,
            categoryId = category.id,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun AddSheetPreview() {
    IjiTheme { AddTransactionSheet(show = true, onDismiss = {}, viewModel = MainViewModel()) }
}
