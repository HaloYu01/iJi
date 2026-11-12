package com.iji.app.ui.assets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.iji.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.iji.app.ui.util.sharedMainViewModel

/** 宽屏（横屏 / 平板）下内容最大宽度，避免被拉成超长输入框 */
private val FORM_MAX_WIDTH = 720.dp

/** 头部图标尺寸：图标充满它，整个图标就是「换图标」的按钮 */
private val ICON_HEADER_SIZE = 64.dp

private const val NAME_MAX = 12
private const val SUB_NAME_MAX = 20

/** 金额（分）→ 输入框文本 */
private fun formatBalanceInput(cents: Long): String = "%.2f".format(cents / 100.0)

/** 输入框的焦点句柄，用于「名称 → 余额 → 备注」的 IME Next 串联 */
private class AccountFocus {
    val name = FocusRequester()
    val balance = FocusRequester()
    val subName = FocusRequester()
}

/**
 * 添加 / 编辑账户页。
 *
 * 紧凑三卡：图标 + 名称（同卡，点图标弹 [AccountIconPickerSheet]）/ 余额 + 计入净资产 / 备注。
 * 图标选择收进底部弹层，选图标时名称自动带出（用户改过名则不覆盖），一屏即可完成填写；
 * 宽屏（横屏 / 平板）下内容限 [FORM_MAX_WIDTH] 居中。
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
     * 不走全局状态：这是一次性导航结果，由 AppRoot 通过 savedStateHandle 回传给资产页，
     * 避免退出页面后仍残留「待高亮」标记。
     */
    onCreated: (Long) -> Unit = { onBack() },
    app: MainViewModel = sharedMainViewModel(),
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
    var showIconPicker by rememberSaveable { mutableStateOf(false) }

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

    // 分类：新建跟随所选图标归类；编辑时图标没改沿用原分类，
    // 改了图标则按新图标重新归类，避免把老账户的英文 iconKey 归到「其他」
    val resolvedCategory = when {
        editingAccount == null -> AccountType.typeOf(iconKey)
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

    /**
     * 选图标：名称自动带出。名称是「空的 / 与上一个图标同名 / 与编辑基线的图标同名」
     * 都视为用户没起过自定义名，跟随新图标，免去选完图标再回顶部打一遍名字的重复输入；
     * 一旦用户自己改过名，换图标不再覆盖。
     */
    fun pickIcon(newIcon: String) {
        val oldIcon = iconKey
        iconKey = newIcon
        val followsIcon = name.isEmpty() ||
            name == oldIcon ||
            (editingAccount != null && name == editingAccount.iconKey)
        if (followsIcon) name = newIcon.take(NAME_MAX)
    }

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
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // 平板 / 横屏下表单限宽居中，不拉成超长输入框
                        .widthIn(max = FORM_MAX_WIDTH)
                        .align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(
                        start = LocalDimens.current.screenPadding,
                        end = LocalDimens.current.screenPadding,
                        top = 16.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    item(key = "icon-name") {
                        Box(Modifier.padding(bottom = 16.dp)) {
                            IconNameCard(
                                iconKey = iconKey,
                                onPickIcon = { showIconPicker = true },
                                name = name,
                                onNameChange = { name = it.take(NAME_MAX) },
                                nameError = nameError,
                                nameLength = name.length,
                                focus = focus,
                            )
                        }
                    }
                    item(key = "balance") {
                        Box(Modifier.padding(bottom = 16.dp)) {
                            BalanceCard(
                                isEditing = isEditing,
                                balanceInput = balanceInput,
                                onBalanceChange = { balanceInput = sanitizeAmountInput(it) },
                                focus = focus,
                                includeInNetWorth = includeInNetWorth,
                                onIncludeChange = { includeInNetWorth = it },
                            )
                        }
                    }
                    item(key = "subname") {
                        SubNameCard(
                            subName = subName,
                            onSubNameChange = { subName = it.take(SUB_NAME_MAX) },
                            focus = focus,
                            onSubmit = { submit() },
                        )
                    }
                }

                // 底部主操作（唯一入口，键盘弹出时随之上浮）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                            .widthIn(max = FORM_MAX_WIDTH)
                            .fillMaxWidth()
                            .padding(horizontal = LocalDimens.current.screenPadding, vertical = 12.dp),
                    )
                }
            }
        }
    }

    // 图标选择弹层：点选即确认并关闭
    AccountIconPickerSheet(
        show = showIconPicker,
        allIcons = accountIcons,
        selectedIconKey = iconKey,
        resolvedCategoryLabel = resolvedCategory.label,
        onDismissRequest = { showIconPicker = false },
        onPick = { newIcon ->
            showIconPicker = false
            pickIcon(newIcon)
        },
    )

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

/** 头部卡：当前图标（点击换）+ 账户名称，同一张卡 */
@Composable
private fun IconNameCard(
    iconKey: String,
    onPickIcon: () -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    nameError: String?,
    nameLength: Int,
    focus: AccountFocus,
) {
    Column {
        IjiFormSectionTitle("账户信息")
        IjiFormCard {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 图标即按钮：png 素材裁圆后图形充满，整个图标可点，点击弹图标选择弹层。
                // clip 放在 clickable 之前，水波纹被裁成与图标同大小的圆形
                AccountIconImage(
                    iconKey = iconKey,
                    size = ICON_HEADER_SIZE,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onPickIcon)
                        .semantics { contentDescription = "更换图标，当前：$iconKey" },
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    // 字段名与字数分列两端，字数不单独占一行
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FieldLabel("账户名称 *", Modifier.weight(1f))
                        Text(
                            "$nameLength/$NAME_MAX",
                            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (nameLength >= NAME_MAX) MiuixPrimary else TextSecondary,
                        )
                    }
                    IjiFormTextField(
                        value = name,
                        onValueChange = onNameChange,
                        modifier = Modifier.focusRequester(focus.name),
                        placeholder = "微信、招商银行",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.balance.requestFocus() }),
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
            }
        }
    }
}

/** 余额卡：金额 + 计入净资产开关 */
@Composable
private fun BalanceCard(
    isEditing: Boolean,
    balanceInput: String,
    onBalanceChange: (String) -> Unit,
    focus: AccountFocus,
    includeInNetWorth: Boolean,
    onIncludeChange: (Boolean) -> Unit,
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
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { focus.subName.requestFocus() }),
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

/** 备注卡：低频字段放最后，不打断「名称 → 余额」主流程 */
@Composable
private fun SubNameCard(
    subName: String,
    onSubNameChange: (String) -> Unit,
    focus: AccountFocus,
    onSubmit: () -> Unit,
) {
    Column {
        IjiFormSectionTitle("备注")
        IjiFormCard {
            Column(Modifier.padding(16.dp)) {
                FieldLabel("备注（可选）")
                IjiFormTextField(
                    value = subName,
                    onValueChange = onSubNameChange,
                    modifier = Modifier.focusRequester(focus.subName),
                    placeholder = "零钱、工资卡",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
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
