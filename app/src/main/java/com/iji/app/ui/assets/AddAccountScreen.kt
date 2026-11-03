package com.iji.app.ui.assets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.Account
import com.iji.app.data.model.AccountType
import com.iji.app.ui.components.AccountIconImage
import com.iji.app.ui.components.ContentState
import com.iji.app.ui.components.IjiCategoryChips
import com.iji.app.ui.components.IjiDialog
import com.iji.app.ui.components.IjiFormCard
import com.iji.app.ui.components.IjiFormDivider
import com.iji.app.ui.components.IjiFormSectionTitle
import com.iji.app.ui.components.IjiFormTextField
import com.iji.app.ui.components.IjiPrimaryButton
import com.iji.app.ui.components.IjiSwitchRow
import com.iji.app.ui.components.IjiTextAction
import com.iji.app.ui.components.LocalSnackbarController
import com.iji.app.ui.components.StateView
import com.iji.app.ui.components.SubPageTopBar
import com.iji.app.ui.components.listAccountIconNames
import com.iji.app.ui.components.parseCents
import com.iji.app.ui.components.sanitizeAmountInput
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 宽屏（横屏 / 平板）断点：超过它改为「左表单 + 右图标」两栏 */
private val WIDE_BREAKPOINT = 720.dp
/** 宽屏下内容最大宽度，避免在平板上被拉成超长输入框 */
private val FORM_MAX_WIDTH = 720.dp
/** 宽屏下左侧表单列固定宽度 */
private val WIDE_FORM_COLUMN = 360.dp
/** 图标网格单元的最小宽度（决定列数） */
private val ICON_CELL_MIN_WIDTH = 76.dp
private const val ICON_GRID_MIN_COLUMNS = 3
private const val ICON_GRID_MAX_COLUMNS = 6

private const val NAME_MAX = 12
private const val SUB_NAME_MAX = 20

/** 金额（分）→ 输入框文本 */
private fun formatBalanceInput(cents: Long): String = "%.2f".format(cents / 100.0)

/** 三个输入框的焦点句柄，用于「名称 → 备注 → 余额」的 IME Next 串联 */
private class AccountFocus {
    val name = FocusRequester()
    val subName = FocusRequester()
    val balance = FocusRequester()
}

/**
 * 添加 / 编辑账户页。
 *
 * 三张卡片分组：账户信息（名称 + 备注）/ 余额（金额 + 计入净资产）/ 账户图标（分类 + 搜索 + 网格）。
 * 宽度 ≥ [WIDE_BREAKPOINT] 改为「左表单 + 右图标网格」两栏，内容最大 [FORM_MAX_WIDTH] 居中；
 * 网格列数按可用宽度在 3~6 之间自适应。
 *
 * 校验：名称为空在提交时报错；重名在输入停顿 350ms 后查库，实时提示并禁用提交；
 * 编辑态改余额需二次确认（直接改余额不会生成流水）。
 *
 * 编辑态由 [accountId] 区分，数据自取，`accountsReady` 区分「加载中 / 已删除」，
 * 绝不退化成新建；分类随所选图标自动归类，不可手改。
 */
@Composable
fun AddAccountScreen(
    onBack: () -> Unit,
    /** 非空 = 编辑模式（该账户 id）；null = 新建 */
    accountId: Long? = null,
    /**
     * 新建成功回调（带新账户 id），用于让资产页滚到并高亮它。
     *
     * 不走 MainViewModel：它在 NavHost 里是每个目的地各一份实例，页面之间不共享状态；
     * 由 AppRoot 通过 savedStateHandle 回传给资产页。
     */
    onCreated: (Long) -> Unit = { onBack() },
    app: MainViewModel = viewModel(),
    viewModel: AssetsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AssetsViewModel(app) as T
        },
    ),
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val accountIcons = remember(context) { listAccountIconNames(context) }
    val defaultIcon = remember(accountIcons) {
        accountIcons.firstOrNull { it == "微信" } ?: accountIcons.firstOrNull() ?: "wechat"
    }

    val accounts by app.accounts.collectAsStateWithLifecycle()
    val accountsReady by app.accountsReady.collectAsStateWithLifecycle()
    val editingAccount: Account? = remember(accounts, accountId) {
        accountId?.let { id -> accounts.firstOrNull { it.id == id } }
    }
    val isEditing = accountId != null

    // 表单状态：rememberSaveable，旋转 / 进程重建不丢输入
    var name by rememberSaveable { mutableStateOf("") }
    var subName by rememberSaveable { mutableStateOf("") }
    var iconKey by rememberSaveable { mutableStateOf(defaultIcon) }
    var balanceInput by rememberSaveable { mutableStateOf("") }
    var includeInNetWorth by rememberSaveable { mutableStateOf(true) }
    var selectedType by rememberSaveable { mutableStateOf(AccountType.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var submitting by remember { mutableStateOf(false) }
    // 只在「点过保存且名称为空」后显示空名错误，避免一进页面就报红
    var showEmptyNameError by remember { mutableStateOf(false) }
    var nameTaken by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showBalanceConfirm by remember { mutableStateOf(false) }

    val focus = remember { AccountFocus() }
    val scope = rememberCoroutineScope()

    // 编辑态回填：仅一次，不覆盖用户后续修改
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editingAccount) {
        val acc = editingAccount ?: return@LaunchedEffect
        if (seeded) return@LaunchedEffect
        name = acc.name
        subName = acc.subName
        balanceInput = formatBalanceInput(acc.balanceCents)
        includeInNetWorth = acc.includeInNetWorth
        iconKey = acc.iconKey
        selectedType = AccountType.ofAccount(acc.category, acc.iconKey, acc.name)
        seeded = true
    }

    val trimmedName = name.trim()

    // 重名实时校验：输入停顿 350ms 再查库，边打字边查会打满数据库
    LaunchedEffect(trimmedName, editingAccount) {
        nameTaken = false
        if (trimmedName.isEmpty()) return@LaunchedEffect
        delay(350)
        nameTaken = viewModel.isAccountNameTaken(trimmedName, editingAccount?.id)
    }

    // 未保存判断：与回填基线逐字段比较，返回时确认，避免手滑丢输入
    val dirty = name != (editingAccount?.name ?: "") ||
        subName != (editingAccount?.subName ?: "") ||
        balanceInput != editingAccount?.let { formatBalanceInput(it.balanceCents) }.orEmpty() ||
        iconKey != (editingAccount?.iconKey ?: defaultIcon) ||
        includeInNetWorth != (editingAccount?.includeInNetWorth ?: true)

    // 分类：新建看用户选的；编辑跟随图标自动归类，图标未改则沿用原分类，
    // 避免把老账户的英文 iconKey 归到「其他」
    val resolvedCategory = when {
        editingAccount == null ->
            if (selectedType != AccountType.ALL) selectedType else AccountType.typeOf(iconKey)
        iconKey == editingAccount.iconKey ->
            AccountType.ofAccount(editingAccount.category, editingAccount.iconKey, editingAccount.name)
        else -> AccountType.ofAccount("", iconKey, trimmedName)
    }

    val nameError = when {
        showEmptyNameError && trimmedName.isEmpty() -> "请输入账户名称"
        nameTaken -> "已存在同名账户"
        else -> null
    }
    val canSubmit = trimmedName.isNotEmpty() && !nameTaken && !submitting

    /** 写库（调用前已通过校验） */
    fun persist() {
        submitting = true
        scope.launch {
            val editing = editingAccount
            if (editing != null) {
                viewModel.updateAccountNow(
                    editing.copy(
                        name = trimmedName,
                        subName = subName.trim(),
                        iconKey = iconKey,
                        balanceCents = parseCents(balanceInput),
                        includeInNetWorth = includeInNetWorth,
                        category = resolvedCategory.name,
                    ),
                )
                snackbar.show("已保存")
                submitting = false
                onBack()
            } else {
                val newId = viewModel.addAccountUnique(
                    trimmedName,
                    subName.trim(),
                    iconKey,
                    parseCents(balanceInput),
                    includeInNetWorth,
                    resolvedCategory.name,
                )
                submitting = false
                if (newId == null) {
                    snackbar.show("已存在同名账户")
                } else {
                    snackbar.show("已创建账户")
                    // 把新账户 id 交出去：资产页据此滚到它并高亮
                    onCreated(newId)
                }
            }
        }
    }

    fun submit() {
        if (trimmedName.isEmpty()) {
            showEmptyNameError = true
            runCatching { focus.name.requestFocus() }
            return
        }
        if (nameTaken || submitting) return
        // 编辑态直接改余额会绕过流水 → 二次确认
        if (editingAccount != null && parseCents(balanceInput) != editingAccount.balanceCents) {
            showBalanceConfirm = true
            return
        }
        persist()
    }

    fun requestBack() {
        if (dirty && !submitting) showDiscardDialog = true else onBack()
    }

    // 系统返回键与顶栏返回同一套逻辑
    BackHandler(enabled = dirty && !submitting) { showDiscardDialog = true }

    // 表单两块内容：单列与双栏布局共用，避免复制参数
    fun LazyListScope.formContent() {
        accountFormItems(
            isEditing = isEditing,
            name = name,
            onNameChange = { name = it.take(NAME_MAX) },
            nameError = nameError,
            subName = subName,
            onSubNameChange = { subName = it.take(SUB_NAME_MAX) },
            balanceInput = balanceInput,
            onBalanceChange = { balanceInput = sanitizeAmountInput(it) },
            includeInNetWorth = includeInNetWorth,
            onIncludeChange = { includeInNetWorth = it },
            focus = focus,
            onSubmit = { submit() },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 键盘弹出时整列让出 IME 高度：底部按钮浮在键盘上，而不是被完全遮住
            .imePadding(),
    ) {
        SubPageTopBar(
            title = if (isEditing) "编辑账户" else "添加账户",
            onBack = { requestBack() },
        )

        when {
            // 编辑态：账户列表还没读出来 → 加载态（否则会误报「账户不存在」）
            isEditing && !accountsReady -> StateView(
                state = ContentState.Loading("加载中"),
                modifier = Modifier.weight(1f),
                areaVerticalPadding = 48.dp,
            )

            // 编辑态：已就绪但找不到 → 空态，绝不退化成新建
            isEditing && editingAccount == null -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StateView(state = ContentState.Empty("账户不存在或已被删除"), areaVerticalPadding = 8.dp)
                IjiTextAction(text = "返回资产", onClick = onBack)
            }

            else -> {
                // 编辑态不过滤图标：分类随所选图标自动归类，必须能选到任意分类的图标
                val filteredIcons = rememberIcons(
                    all = accountIcons,
                    type = if (isEditing) AccountType.ALL else selectedType,
                    query = searchQuery,
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val screenPadding = LocalDimens.current.screenPadding
                    val wide = maxWidth >= WIDE_BREAKPOINT
                    // 列数按可用宽度自适应；大字模式（fontScale 大）自动少一列，避免名称被截成省略号
                    val fontScale = LocalConfiguration.current.fontScale
                    val usable = maxWidth - screenPadding * 2 - 24.dp
                    val minCell = ICON_CELL_MIN_WIDTH * fontScale.coerceIn(1f, 1.35f)
                    val columns = (usable / minCell).toInt()
                        .coerceIn(ICON_GRID_MIN_COLUMNS, ICON_GRID_MAX_COLUMNS)

                    fun LazyListScope.iconContent() {
                        iconPicker(
                            icons = filteredIcons,
                            selected = iconKey,
                            columns = columns,
                            isEditing = isEditing,
                            selectedType = selectedType,
                            onSelectType = { selectedType = it },
                            resolvedLabel = resolvedCategory.label,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it.take(20) },
                            onPick = { iconKey = it },
                        )
                    }

                    if (wide) {
                        // 宽屏两栏：左表单固定宽、右图标网格填满剩余
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = FORM_MAX_WIDTH)
                                .align(Alignment.TopCenter)
                                .padding(horizontal = screenPadding, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            LazyColumn(modifier = Modifier.width(WIDE_FORM_COLUMN)) { formContent() }
                            LazyColumn(modifier = Modifier.weight(1f)) { iconContent() }
                        }
                    } else {
                        // 单列：一个 LazyColumn 承载全部内容，不嵌套滚动。
                        // 间距用块内 bottom padding 而非 spacedBy：图标头部与网格行必须无缝拼成一张卡
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = screenPadding,
                                end = screenPadding,
                                top = 16.dp,
                                bottom = 24.dp,
                            ),
                        ) {
                            formContent()
                            iconContent()
                        }
                    }
                }

                // 底部主操作（唯一入口，键盘弹出时随之上浮）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    IjiFormDivider(startPadding = 0.dp)
                    IjiPrimaryButton(
                        text = when {
                            submitting -> "保存中…"
                            isEditing -> "保存修改"
                            else -> "创建账户"
                        },
                        onClick = { submit() },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LocalDimens.current.screenPadding, vertical = 12.dp),
                    )
                }
            }
        }
    }

    // 未保存确认
    IjiDialog(
        show = showDiscardDialog,
        onDismissRequest = { showDiscardDialog = false },
        title = "放弃修改？",
        summary = "已填写的内容不会保存",
        confirmText = "放弃",
        onConfirm = {
            showDiscardDialog = false
            onBack()
        },
    )

    // 编辑态改余额二次确认
    IjiDialog(
        show = showBalanceConfirm,
        onDismissRequest = { showBalanceConfirm = false },
        title = "直接修改余额？",
        summary = "这样改不会生成流水，与记账记录可能对不上。账目核对建议走账户详情里的「对账」。",
        confirmText = "仍然修改",
        onConfirm = {
            showBalanceConfirm = false
            persist()
        },
    )
}

/** 表单区（账户信息 + 余额）：两栏与单列布局共用 */
private fun LazyListScope.accountFormItems(
    isEditing: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    nameError: String?,
    subName: String,
    onSubNameChange: (String) -> Unit,
    balanceInput: String,
    onBalanceChange: (String) -> Unit,
    includeInNetWorth: Boolean,
    onIncludeChange: (Boolean) -> Unit,
    focus: AccountFocus,
    onSubmit: () -> Unit,
) {
    item(key = "info") {
        Box(Modifier.padding(bottom = 16.dp)) {
            AccountInfoBlock(
                name = name,
                onNameChange = onNameChange,
                nameError = nameError,
                subName = subName,
                onSubNameChange = onSubNameChange,
                focus = focus,
            )
        }
    }
    item(key = "balance") {
        Box(Modifier.padding(bottom = 16.dp)) {
            BalanceBlock(
                isEditing = isEditing,
                balanceInput = balanceInput,
                onBalanceChange = onBalanceChange,
                focus = focus,
                includeInNetWorth = includeInNetWorth,
                onIncludeChange = onIncludeChange,
                onSubmit = onSubmit,
            )
        }
    }
}

/**
 * 图标区：头部 + 网格行拼成一张卡。
 *
 * 网格逐行渲染（145 个图标不能一次性组合），空结果时用同样式的一行占位。
 */
private fun LazyListScope.iconPicker(
    icons: List<String>,
    selected: String,
    columns: Int,
    isEditing: Boolean,
    selectedType: AccountType,
    onSelectType: (AccountType) -> Unit,
    resolvedLabel: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPick: (String) -> Unit,
) {
    item(key = "icon-head") {
        IconPickerHeader(
            isEditing = isEditing,
            selectedType = selectedType,
            onSelectType = onSelectType,
            resolvedLabel = resolvedLabel,
            iconKey = selected,
            iconCount = icons.size,
            searchQuery = searchQuery,
            onSearchChange = onSearchChange,
        )
    }

    if (icons.isEmpty()) {
        item(key = "icon-empty") { EmptyIconRow() }
        return
    }

    val rows = icons.chunked(columns)
    rows.forEachIndexed { index, row ->
        item(key = "icon-row-$index") {
            IconRow(
                icons = row,
                selected = selected,
                columns = columns,
                last = index == rows.lastIndex,
                onPick = onPick,
            )
        }
    }
}

/** 图标过滤：分类 → 搜索（含银行简称别名）→ 常用项前置 */
@Composable
private fun rememberIcons(
    all: List<String>,
    type: AccountType,
    query: String,
): List<String> = remember(all, type, query) {
    val byType = if (type == AccountType.ALL) all else all.filter { AccountType.typeOf(it) == type }
    val q = query.trim()
    val bySearch = if (q.isEmpty()) byType else byType.filter { matchesIconQuery(it, q) }
    AccountType.sortByPriority(bySearch, type.priorityKeywords)
}

/**
 * 图标搜索匹配：名称包含关键词，或命中简称别名（「招行」「zsyh」→ 招商银行）。
 * 名称都是中文全名，用户很难记得住，这里只为常见银行 / 支付补一张小别名表。
 */
private val ICON_ALIASES = mapOf(
    "招商银行" to listOf("招行", "zsyh", "cmb"),
    "工商银行" to listOf("工行", "gsyh", "icbc"),
    "建设银行" to listOf("建行", "jsyh", "ccb"),
    "农业银行" to listOf("农行", "nyyh", "abc"),
    "中国银行" to listOf("中行", "zgyh", "boc"),
    "交通银行" to listOf("交行", "jtyh", "bocom"),
    "邮政储蓄银行" to listOf("邮储", "邮局", "yzyh", "psbc"),
    "中信银行" to listOf("中信", "zxyh", "citic"),
    "浦发银行" to listOf("浦发", "pfyh", "spdb"),
    "民生银行" to listOf("民生", "msyh", "cmbc"),
    "兴业银行" to listOf("兴业", "xyyh"),
    "光大银行" to listOf("光大", "gdyh"),
    "平安银行" to listOf("平安", "payh"),
    "广发银行" to listOf("广发", "gfyh"),
    "华夏银行" to listOf("华夏", "hxyh"),
    "支付宝" to listOf("zfb", "alipay"),
    "微信" to listOf("wx", "wechat", "微信支付"),
    "云闪付" to listOf("ysf", "银联"),
)

private fun matchesIconQuery(iconName: String, query: String): Boolean {
    if (iconName.contains(query, ignoreCase = true)) return true
    return ICON_ALIASES[iconName]?.any { it.contains(query, ignoreCase = true) } == true
}

/** 账户信息块：名称（必填）+ 备注 */
@Composable
private fun AccountInfoBlock(
    name: String,
    onNameChange: (String) -> Unit,
    nameError: String?,
    subName: String,
    onSubNameChange: (String) -> Unit,
    focus: AccountFocus,
) {
    Column {
        IjiFormSectionTitle("账户信息")
        IjiFormCard {
            Column(Modifier.padding(16.dp)) {
                // 字段名与字数分列两端，字数不单独占一行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("账户名称 *", Modifier.weight(1f))
                    Text(
                        "${name.length}/$NAME_MAX",
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (name.length >= NAME_MAX) MiuixPrimary else TextSecondary,
                    )
                }
                IjiFormTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.focusRequester(focus.name),
                    placeholder = "微信、招商银行",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.subName.requestFocus() }),
                )
                if (nameError != null) {
                    Text(
                        nameError,
                        modifier = Modifier
                            .padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                            // 错误出现时由读屏播报
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleRed,
                    )
                }
            }
            IjiFormDivider()
            Column(Modifier.padding(16.dp)) {
                FieldLabel("备注（可选）")
                IjiFormTextField(
                    value = subName,
                    onValueChange = onSubNameChange,
                    modifier = Modifier.focusRequester(focus.subName),
                    placeholder = "零钱、工资卡",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.balance.requestFocus() }),
                )
            }
        }
    }
}

/** 余额块：金额 + 计入净资产开关 */
@Composable
private fun BalanceBlock(
    isEditing: Boolean,
    balanceInput: String,
    onBalanceChange: (String) -> Unit,
    focus: AccountFocus,
    includeInNetWorth: Boolean,
    onIncludeChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    Column {
        IjiFormSectionTitle("余额")
        IjiFormCard {
            Column(Modifier.padding(16.dp)) {
                FieldLabel(if (isEditing) "当前余额（元）" else "初始余额（元）")
                IjiFormTextField(
                    value = balanceInput,
                    onValueChange = onBalanceChange,
                    modifier = Modifier.focusRequester(focus.balance),
                    placeholder = "0.00",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                )
                HintText("不填按 0 处理；负债账户可填负数，如信用卡 -1200")
                if (isEditing) {
                    Text(
                        "直接修改余额不会生成流水，保存前会再确认一次",
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleRed.copy(alpha = 0.85f),
                    )
                }
            }
            IjiFormDivider()
            Column(Modifier.padding(16.dp)) {
                IjiSwitchRow(
                    title = "计入净资产",
                    desc = "关闭后该账户不计入净资产（如信用卡等负债账户）",
                    checked = includeInNetWorth,
                    onCheckedChange = onIncludeChange,
                )
            }
        }
    }
}

/** 图标区头部：标题 + 分类（新建可切 / 编辑只读）+ 搜索，顶边圆角与网格行拼成一张卡 */
@Composable
private fun IconPickerHeader(
    isEditing: Boolean,
    selectedType: AccountType,
    onSelectType: (AccountType) -> Unit,
    resolvedLabel: String,
    iconKey: String,
    iconCount: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "账户图标",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$iconCount 个",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "当前：$iconKey",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isEditing) {
            // 编辑态不给切分类：分类随图标自动归类，避免「银行账户挂支付宝图标」的矛盾
            Text(
                "分类随图标自动归类：$resolvedLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MiuixPrimary,
            )
        } else {
            IjiCategoryChips(
                items = AccountType.entries,
                selected = selectedType,
                onSelect = onSelectType,
                labelOf = { it.label },
            )
        }

        Column {
            IjiFormTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "搜索图标或简称",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            HintText("支持「招行」「zsyh」这类简称")
        }
    }
}

/** 一行图标：等宽单元格，末行收底部圆角并补空位 */
@Composable
private fun IconRow(
    icons: List<String>,
    selected: String,
    columns: Int,
    last: Boolean,
    onPick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                if (last) RoundedCornerShape(bottomStart = Radius.sheet, bottomEnd = Radius.sheet)
                else RoundedCornerShape(0.dp),
            )
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icons.forEach { icon ->
            IconCell(
                iconName = icon,
                selected = icon == selected,
                modifier = Modifier.weight(1f),
                onClick = { onPick(icon) },
            )
        }
        // 末行不足时补空位，保证每格等宽
        repeat(columns - icons.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** 无匹配图标：与网格同色同圆角，避免卡片断层 */
@Composable
private fun EmptyIconRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = Radius.sheet, bottomEnd = Radius.sheet))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "没有匹配的图标，换个关键词或分类试试",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 图标单元：选中态 = 主色描边 + 右上角勾 */
@Composable
private fun IconCell(
    iconName: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.lg)
    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(shape)
            .background(
                if (selected) MiuixPrimary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surface,
            )
            // 可访问性：告诉读屏软件这是单选组里的一项，并播报图标名
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "图标：$iconName" }
            .then(
                if (selected) Modifier.border(1.5.dp, MiuixPrimary, shape) else Modifier,
            )
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AccountIconImage(iconKey = iconName, size = 34.dp)
            Spacer(Modifier.height(3.dp))
            Text(
                iconName,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MiuixPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
                    .size(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MiuixPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✓",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                )
            }
        }
    }
}

/** 字段下方的帮助 / 示例文字，与输入框之间留 8dp 空隙，避免两行灰字糊在一起 */
@Composable
private fun HintText(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )
}

/** 字段名：固定在输入框外，输入框内只留一行占位文字 */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        fontWeight = FontWeight.Medium,
    )
}
