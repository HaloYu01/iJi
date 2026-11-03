package com.iji.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.SubcomposeAsyncImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.iji.app.data.repository.CategoryIconStore

/**
 * 图标资源加载（AutoAccounting 素材包 → app/src/main/assets/icons/）：
 * - icons/category/ —— 分类图标（白色线条、透明底，需配彩色圆形底）
 * - icons/account/  —— 资产图标（彩色 logo，直接用）
 *
 * 命名规范：文件名 = 分类名 / 账户名（中文）。加载顺序：原名 → 别名。
 * 都匹配不到时不画通用灰图：分类走「分类名首字」徽标，账户走自绘品牌徽章。
 *
 * P2 改造：图标解码由 Coil 异步完成（[SubcomposeAsyncImage]），不再在
 * remember 里同步 BitmapFactory 解码阻塞组合；Coil 自带内存/磁盘缓存，
 * 列表（账单行、分类管理）中复用同一图标不再重复解码。assets 名称集合只
 * list 一次并缓存，用于无 I/O 的候选解析。
 */

/** 分类名 → 素材包文件名（应用分类与素材包不同名时的别名映射） */
private val CATEGORY_ALIAS = mapOf(
    "正餐" to "午餐",
    "食材" to "餐饮",
    "居住" to "住房居家",
    "宽带" to "网络",
    "游玩" to "门票",
    "教育" to "教育学习",
    "借出" to "应收",
    "报销" to "报销款",
    "借入" to "其他",
    "转账" to "其他",
    // 新增二级分类：复用一级分类素材图标，保证层级视觉一致
    "早餐" to "餐饮", "外卖" to "餐饮", "水果" to "餐饮", "夜宵" to "餐饮",
    "火车" to "交通", "高铁" to "交通", "飞机" to "交通", "停车" to "交通",
    "美妆" to "购物", "鞋包" to "购物", "母婴" to "购物", "书籍" to "购物",
    "物业" to "居住", "维修" to "居住", "家政" to "居住",
    "KTV" to "娱乐", "演出" to "娱乐", "运动" to "娱乐",
    "门诊" to "医疗", "药品" to "医疗", "住院" to "医疗", "保健" to "医疗",
    "请客" to "人情", "礼物" to "人情", "喜事" to "人情",
    "学费" to "教育", "书本" to "教育", "培训" to "教育",
    "机票" to "旅行", "酒店" to "旅行", "门票" to "旅行",
    "借款" to "借出",
    "奖金" to "工资", "津贴" to "工资",
    "利息" to "理财", "分红" to "理财",
    "中奖" to "红包",
)

/** 资产 iconKey → 素材包文件名（旧英文 key / 简称 → 素材包中文名） */
private val ACCOUNT_ALIAS = mapOf(
    "邮储银行" to "邮政储蓄银行",
)

/** assets 中已存在的分类/资产图标名（去重、首列后缓存，避免重复 list 与无谓 I/O） */
private var cachedCategoryNames: Set<String>? = null
private var cachedAccountNames: Set<String>? = null

private fun categoryNameSet(context: Context): Set<String> {
    cachedCategoryNames?.let { return it }
    // AssetManager.list() 返回的是文件名（含 .png 后缀），而候选名是不带后缀的图标名，
    // 必须剥离后缀再比较；否则所有图标匹配失败、统一回落到占位图。
    val set = context.assets.list("icons/category")
        ?.map { it.removeSuffix(".png") }
        ?.toSet()
        .orEmpty()
    cachedCategoryNames = set
    return set
}

private fun accountNameSet(context: Context): Set<String> {
    cachedAccountNames?.let { return it }
    val set = context.assets.list("icons/account")
        ?.map { it.removeSuffix(".png") }
        ?.toSet()
        .orEmpty()
    cachedAccountNames = set
    return set
}

/**
 * 解析分类图标的最终 assets 相对路径：用户覆盖名 → 原名 → 别名。
 *
 * 都匹配不到时返回 null（而不是回落到素材库的默认灰图）：
 * 调用方会改用「分类名首字」徽标，比一张灰蒙蒙的通用图更能表达"这是哪个分类"。
 */
private fun resolveCategoryPath(context: Context, name: String, categoryId: String? = null): String? {
    val set = categoryNameSet(context)
    val candidates = buildList {
        // 优先按稳定 id 命中（改名不丢图标），再回落旧的名字 key
        if (categoryId != null) {
            CategoryIconStore.get(context, categoryId, name)?.let { add(it) }
        } else {
            CategoryIconStore.get(context, name)?.let { add(it) }
        }
        add(name)
        CATEGORY_ALIAS[name]?.let { add(it) }
    }
    val found = candidates.distinct().firstOrNull { it in set }
    return found?.let { "icons/category/$it.png" }
}

/**
 * 解析资产图标的最终 assets 相对路径；无匹配返回 null（调用方回落到自绘品牌徽章）。
 */
private fun resolveAccountPath(context: Context, iconKey: String): String? {
    val set = accountNameSet(context)
    val candidates = buildList {
        add(iconKey)
        ACCOUNT_ALIAS[iconKey]?.let { add(it) }
        runCatching { AccountCatalog.of(iconKey).label }.getOrNull()?.let { add(it) }
    }
    return candidates.distinct().firstOrNull { it in set }?.let { "icons/account/$it.png" }
}

/**
 * 某个分类当前**实际显示**的素材图标名（不含路径）。
 *
 * 分类编辑页进入时要把"当前图标"回填进选择器：没有用户覆盖时应回填名字/别名解析出来的那张图，
 * 而不是图标列表第一项 —— 否则用户只是改个名字，图标却被悄悄改成了"115网盘"。
 */
fun currentCategoryIconName(context: Context, name: String, categoryId: String? = null): String? {
    val set = categoryNameSet(context)
    val candidates = buildList {
        if (categoryId != null) {
            CategoryIconStore.get(context, categoryId, name)?.let { add(it) }
        } else {
            CategoryIconStore.get(context, name)?.let { add(it) }
        }
        add(name)
        CATEGORY_ALIAS[name]?.let { add(it) }
    }
    return candidates.distinct().firstOrNull { it in set }
}

/** assets 里全部资产图标名（去 .png 后缀），供添加账户时的图标选择列表 */
fun listAccountIconNames(context: Context): List<String> =
    context.assets.list("icons/account")
        ?.filter { it.endsWith(".png") }
        ?.map { it.removeSuffix(".png") }
        ?.sorted()
        ?: emptyList()

/** assets 里全部分类图标名（去 .png 后缀），供分类管理时选择图标 */
fun listCategoryIconNames(context: Context): List<String> =
    context.assets.list("icons/category")
        ?.filter { it.endsWith(".png") }
        ?.map { it.removeSuffix(".png") }
        ?.sorted()
        ?: emptyList()

/**
 * 自定义 Coil 模型：携带 assets 相对路径，交由 [AssetIconFetcher] 直接
 * `assets.open(path)` 解码，彻底绕开 `file:///android_asset/...` URI 的中文路径
 * 编码/解析环节（原始素材文件名多为中文，URI 方案易出现加载不到、回落占位的问题）。
 */
private data class AssetIconPath(val path: String)

/** 按原始 assets 相对路径直接打开并解码 PNG；失败抛异常 → 走 Coil error 兜底 */
private class AssetIconFetcher(
    private val context: Context,
    private val path: String,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("解码资产图标失败: $path")
        return ImageFetchResult(bitmap.asImage(), false, DataSource.DISK)
    }
}

private class AssetIconFetcherFactory : Fetcher.Factory<AssetIconPath> {
    override fun create(
        data: AssetIconPath,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = AssetIconFetcher(options.context, data.path)
}

/** 复用单个 ImageLoader（注册了资产图标 Fetcher），避免每次组合都新建 */
private val loaderLock = Any()
@Volatile
private var cachedIconLoader: ImageLoader? = null

private fun iconImageLoader(context: Context): ImageLoader {
    cachedIconLoader?.let { return it }
    synchronized(loaderLock) {
        cachedIconLoader?.let { return it }
        return ImageLoader.Builder(context.applicationContext)
            .components { add(AssetIconFetcherFactory()) }
            .build()
            .also { cachedIconLoader = it }
    }
}

/**
 * 分类图标组件：彩色圆形底（分类色）+ 白色线条 PNG。
 *
 * - 有素材：Coil 异步加载（不阻塞组合）；未选中时把线条染成分类色，
 *   否则白线画在 12% 透明底上几乎看不见。
 * - **没有素材**：显示分类名首字的文字徽标（[IconInitial]），不再画通用灰图。
 * - 加载失败：优先用调用方给的 [fallback]，否则同样退回首字徽标。
 *
 * remember key 包含用户自定义图标名，分类管理修改后全局即时刷新。
 */
@Composable
fun CategoryIconImage(
    name: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    fallback: (@Composable () -> Unit)? = null,
    /** 分类 id：传了就按 id 取自定义图标（改名不丢图标），不传则按名字兜底 */
    categoryId: String? = null,
) {
    val context = LocalContext.current
    val path = remember(context, name, categoryId) {
        resolveCategoryPath(context, name, categoryId)
    }
    val initialTint = if (selected) Color.White else color
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) color else color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (path == null) {
            IconInitial(name = name, tint = initialTint, size = size)
        } else {
            SubcomposeAsyncImage(
                model = remember(path) { AssetIconPath(path) },
                contentDescription = name,
                imageLoader = iconImageLoader(context),
                modifier = Modifier
                    .size(size * 0.56f)
                    // 未选中时底色只有 12% 透明，白色线条画上去几乎看不见；
                    // 把图标本身染成分类色 → 变成"淡底 + 彩色线条"，观感不变但看得清
                    .graphicsLayer(colorFilter = if (selected) null else ColorFilter.tint(color)),
                loading = { fallback?.invoke() },
                error = {
                    if (fallback != null) fallback() else IconInitial(name = name, tint = initialTint, size = size)
                },
            )
        }
    }
}

/**
 * 无图标时的文字徽标：取分类名首字，画在分类色圆底上。
 *
 * 比素材库的 `default_category.png`（一张灰图）更能表达"这是哪个分类"：
 * 颜色继续表达分类色，文字直接可读；也不会出现"灰底 + 灰图"的糊成一团。
 */
@Composable
private fun IconInitial(name: String, tint: Color, size: Dp) {
    val label = name.trim().take(1).ifEmpty { "?" }
    Text(
        text = label,
        color = tint,
        fontSize = (size.value * 0.4f).sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

/**
 * 资产图标组件：加载彩色 logo PNG 直接显示（无需底色）。
 * 由 Coil 异步加载；无匹配图标或加载失败时回退到自绘品牌徽章（AccountIcon），
 * 保证旧账户（wechat/alipay 等）不破版。
 */
@Composable
fun AccountIconImage(
    iconKey: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val path = remember(context, iconKey) { resolveAccountPath(context, iconKey) }
    if (path == null) {
        AccountIcon(iconKey = iconKey, size = size, modifier = modifier)
        return
    }
    SubcomposeAsyncImage(
        model = AssetIconPath(path),
        contentDescription = iconKey,
        imageLoader = iconImageLoader(context),
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        error = { AccountIcon(iconKey = iconKey, size = size, modifier = modifier) },
    )
}
