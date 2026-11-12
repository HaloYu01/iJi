package com.iji.app.ui.assets

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iji.app.data.model.AccountType
import com.iji.app.ui.components.AccountIconImage
import com.iji.app.ui.components.IjiFormTextField
import com.iji.app.ui.theme.IjiIconSize
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import com.iji.app.ui.theme.TextSecondary
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/** 图标网格单元的最小宽度（决定列数） */
private val ICON_CELL_MIN_WIDTH = 76.dp
private const val ICON_GRID_MIN_COLUMNS = 3
private const val ICON_GRID_MAX_COLUMNS = 6
/** 弹层内容最大高度占屏高比例：网格区尽量大，又不至于盖满全屏失去层级感 */
private const val SHEET_HEIGHT_FRACTION = 0.75f

/**
 * 账户图标选择弹层（底部抽屉，[OverlayBottomSheet]）。
 *
 * 添加/编辑账户页的头像块点开：分类 chips + 搜索（含银行简称别名）+ 图标网格，点选即确认并关闭。
 * 把 145 个图标从页面主体抽到这里，主表单只留一个「当前图标 + 更换入口」。
 *
 * 浏览状态（分类筛选）跨开合保留——用户连续添加几个同分类账户时不必每次重新筛选；
 * 搜索词每次打开清空，避免上次的关键词残留下次结果变少。
 *
 * @param allIcons              全量图标名（assets/icons/account/）
 * @param selectedIconKey       当前选中的图标（网格里打勾）
 * @param resolvedCategoryLabel 当前图标自动归类后的分类名（如「银行」），头部展示
 */
@Composable
internal fun AccountIconPickerSheet(
    show: Boolean,
    allIcons: List<String>,
    selectedIconKey: String,
    resolvedCategoryLabel: String,
    onDismissRequest: () -> Unit,
    onPick: (String) -> Unit,
) {
    var selectedType by rememberSaveable { mutableStateOf(AccountType.ALL) }
    var searchQuery by remember(show) { mutableStateOf("") }

    val filteredIcons = rememberIcons(allIcons, selectedType, searchQuery)

    OverlayBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = null,
        defaultWindowInsetsPadding = false,
    ) {
        val maxHeight = LocalConfiguration.current.screenHeightDp.dp * SHEET_HEIGHT_FRACTION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                // 抽屉不做系统 insets（defaultWindowInsetsPadding = false），自行避让手势条与键盘
                .navigationBarsPadding()
                .imePadding(),
        ) {
            IconSheetHeader(
                resolvedCategoryLabel = resolvedCategoryLabel,
                onClose = onDismissRequest,
            )

            IjiFormTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(20) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                placeholder = "搜索图标或简称，如「招行」",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            // 弹层高度宝贵：分类用单行横滚，不换行铺开
            IconTypeChipsRow(
                selected = selectedType,
                onSelect = { selectedType = it },
            )

            // 网格：不超高时按内容收缩，超高时滚动（fill = false 的 weight 才能做到两者兼得）
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                // 列数按弹层宽度自适应；大字模式（fontScale 大）自动少一列，避免名称被截成省略号
                val fontScale = LocalConfiguration.current.fontScale
                val minCell = ICON_CELL_MIN_WIDTH * fontScale.coerceIn(1f, 1.35f)
                val columns = ((maxWidth - 24.dp) / minCell).toInt()
                    .coerceIn(ICON_GRID_MIN_COLUMNS, ICON_GRID_MAX_COLUMNS)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    iconGridRows(
                        icons = filteredIcons,
                        selected = selectedIconKey,
                        columns = columns,
                        onPick = onPick,
                    )
                }
            }
        }
    }
}

/** 弹层头部：标题 + 自动归类说明 + 关闭按钮，单行排布 */
@Composable
private fun IconSheetHeader(
    resolvedCategoryLabel: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(start = 20.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "选择图标",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "随图标自动归入「$resolvedCategoryLabel」",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            MiuixIcons.Close,
            contentDescription = "关闭",
            tint = TextSecondary,
            modifier = Modifier
                // 点击区保持 48dp（项目触摸目标下限），只把图标画小；
                // 右移压掉点击区的对称留白，字形贴弹窗右上角（记账面板顶栏同款手法）
                .size(48.dp)
                .offset(x = 6.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .padding(14.dp),
        )
    }
}

/**
 * 弹层专用的单行横滚分类胶囊。
 *
 * 页面里的 [IjiCategoryChips] 用 FlowRow 换行铺开（保证全部可见），但弹层高度宝贵，
 * 8 个分类换行要占两行 —— 这里收紧为单行 [LazyRow]，靠横滚看全，节省一格网格的空间。
 */
@Composable
private fun IconTypeChipsRow(
    selected: AccountType,
    onSelect: (AccountType) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(
            items = AccountType.entries,
            key = { it.name },
        ) { type ->
            val isSelected = type == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(Radius.xxl))
                    .background(
                        if (isSelected) MiuixPrimary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    )
                    .clickable { onSelect(type) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    type.label,
                    color = if (isSelected) MiuixPrimary else TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
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

/** 弹层网格：逐行渲染（145 个图标不能一次性组合），空结果时用同样式的一行占位 */
private fun LazyListScope.iconGridRows(
    icons: List<String>,
    selected: String,
    columns: Int,
    onPick: (String) -> Unit,
) {
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

/** 一行图标：等宽单元格，末行补空位 */
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

/** 无匹配图标：与网格同色，避免断层 */
@Composable
private fun EmptyIconRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sheet))
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
                else Color.Transparent,
            )
            // 可访问性：告诉读屏软件这是单选组里的一项，并播报图标名
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "图标：$iconName" }
            .then(
                if (selected) Modifier.border(1.5.dp, MiuixPrimary, shape) else Modifier,
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AccountIconImage(iconKey = iconName, size = IjiIconSize.Grid)
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
                    .clip(CircleShape)
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
