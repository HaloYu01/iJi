package com.iji.app.ui.mine

import android.content.Context

/**
 * 关于应用 / 更新日志 / 许可与授权 / 隐私政策 的静态数据。
 *
 * 三条约定，改内容时请一起遵守：
 * 1. **版本号只有两处**：`app/build.gradle.kts` 的 versionName / versionCode 是安装包的事实，
 *    本文件的更新日志负责「这个版本什么时候发、改了什么」。页面上的「当前版本」由
 *    [currentVersionInfo] 把两边拼起来，不额外维护第三份常量。
 * 2. **日期统一 `yyyy-MM-dd`**，更新日志按由新到旧排列，第一条即当前版本。
 * 3. **变更条目一律带类型标签**（新增 / 优化 / 修复 / 调整 / 文档），见 [ChangeKind]。
 */

// ── 版本与更新日志 ──────────────────────────────────────────────────────

/** 变更类型：更新日志每条前面带彩色标签，扫一眼就知道这次是加了功能还是修了 bug */
enum class ChangeKind(val label: String) {
    FEATURE("新增"),
    IMPROVE("优化"),
    FIX("修复"),
    CHANGE("调整"),
    DOCS("文档"),
}

/** 一条变更记录 */
data class ChangelogItem(
    val kind: ChangeKind,
    val text: String,
)

/** 一个版本的变更记录 */
data class ChangelogEntry(
    val version: String,
    /** 发布（或归档）日期，`yyyy-MM-dd` */
    val date: String,
    /** 一句话概览，展开前就能看懂这个版本干了什么 */
    val summary: String,
    val items: List<ChangelogItem>,
)

/** 更新日志：由新到旧。第一条必须对应当前 versionName（见 [changelogOf]）。 */
val changelogData: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        version = "v1.1.0",
        date = "2026-11-03",
        summary = "识屏记账、多账本、AI 记账与四大页面重构；补全授权条款与版本信息",
        items = listOf(
            ChangelogItem(ChangeKind.FEATURE, "识屏记账：Shizuku 截屏 + 本地 OCR，悬浮窗逐笔确认后才入账"),
            ChangelogItem(ChangeKind.FEATURE, "识屏悬浮窗「详细版」：可改金额、账户、分类、时分、标签与备注"),
            ChangelogItem(ChangeKind.FEATURE, "多账本：账本独立管理，账本级币种只换符号不做汇率换算"),
            ChangelogItem(ChangeKind.FEATURE, "AI 记账：解析管线与提示词规则中心，支持文本、语音与通知记账"),
            ChangelogItem(ChangeKind.FEATURE, "分类全面可编辑：两级分类、图标、排序与删除迁移都可改，出厂分类也是数据"),
            ChangelogItem(ChangeKind.FEATURE, "数据：CSV 导出、四种形态的账单导入、WebDAV 网盘备份"),
            ChangelogItem(ChangeKind.FEATURE, "超级岛：识屏过程态与识别结果上岛"),
            ChangelogItem(ChangeKind.FEATURE, "账单支持时分（精确到分钟），老账单保持「只记到天」"),
            ChangelogItem(ChangeKind.IMPROVE, "统计页重构：口径统一为当期、概览层级重排、分类卡合并、趋势图补刻度"),
            ChangelogItem(ChangeKind.IMPROVE, "日历页重构：卡片化、横滑切月、固定 6 行、相邻月淡显可点"),
            ChangelogItem(ChangeKind.IMPROVE, "账本页重构：按月查询、按天分页、点行进详情、四种空态"),
            ChangelogItem(ChangeKind.IMPROVE, "记一笔：金额表达式实时计算、组合付款、退款溯源、模板与草稿自动保存"),
            ChangelogItem(ChangeKind.FIX, "编辑已有账单时未回填原数据、且会把日期改成今天"),
            ChangelogItem(ChangeKind.FIX, "识屏悬浮窗点击备注后，中部字段区整块变空白"),
            ChangelogItem(ChangeKind.FIX, "统计页切换「周 / 年」不驱动数据"),
            ChangelogItem(ChangeKind.DOCS, "补充本应用授权条款与完整的第三方组件许可清单"),
            ChangelogItem(ChangeKind.DOCS, "更新日志改为「版本 + 发布日期 + 类型标签」的统一格式"),
        ),
    ),
    ChangelogEntry(
        version = "v1.0.0",
        date = "2026-10-04",
        summary = "首个正式版本",
        items = listOf(
            ChangelogItem(ChangeKind.FEATURE, "本地记账核心能力收口：记一笔、资产账户、分类两级菜单、自动记账、偏好与数据管理"),
            ChangelogItem(ChangeKind.IMPROVE, "界面统一折叠顶栏 + 液态玻璃底栏"),
            ChangelogItem(ChangeKind.IMPROVE, "分类 / 资产图标与头像改为 Coil 异步加载"),
        ),
    ),
    ChangelogEntry(
        version = "v0.1.0",
        date = "2026-10-03",
        summary = "记一笔成型，致谢页落地",
        items = listOf(
            ChangelogItem(ChangeKind.FEATURE, "记一笔：金额区实时展示计算过程，计算态完成键变「=」，退位遵循计算器逻辑"),
            ChangelogItem(ChangeKind.FEATURE, "记一笔：支出与预算的计入状态相互独立，通过标记弹窗分别设置"),
            ChangelogItem(ChangeKind.FEATURE, "记一笔：新增报销标记、优惠录入、附件（图片）"),
            ChangelogItem(ChangeKind.FEATURE, "关于应用：致谢列出全部参考项目（NexioSchedule、AutoAccounting 等）并说明各自贡献"),
            ChangelogItem(ChangeKind.IMPROVE, "账本页：单笔账单金额下方账户名称前显示对应账户图标"),
            ChangelogItem(ChangeKind.FIX, "编辑已有账单未回填原数据、且会把日期改成今天"),
        ),
    ),
    ChangelogEntry(
        version = "v0.0.9",
        date = "2026-10-03",
        summary = "分类两级与自动记账",
        items = listOf(
            ChangelogItem(ChangeKind.IMPROVE, "资产页：账户列表、账户详情与分类两级菜单重构"),
            ChangelogItem(ChangeKind.FEATURE, "记账分类支持两级（一级胶囊 + 二级宫格）"),
            ChangelogItem(ChangeKind.FEATURE, "复刻 AutoAccounting 的自动记账能力"),
        ),
    ),
    ChangelogEntry(
        version = "v0.0.8",
        date = "2026-10-02",
        summary = "顶栏统一与沉浸式适配",
        items = listOf(
            ChangelogItem(ChangeKind.IMPROVE, "四页顶栏统一为折叠式 IjiTopBar（AGSL 渐进模糊 + 大标题收起）"),
            ChangelogItem(ChangeKind.IMPROVE, "沉浸式适配：状态栏与挖孔区避让"),
            ChangelogItem(ChangeKind.IMPROVE, "时间选择弹窗改为 Miuix 风格"),
        ),
    ),
    ChangelogEntry(
        version = "v0.0.7",
        date = "2026-10-01",
        summary = "液态玻璃底栏与组件拆分",
        items = listOf(
            ChangelogItem(ChangeKind.IMPROVE, "底部导航改为液态玻璃 GlassBottomNavBar"),
            ChangelogItem(ChangeKind.CHANGE, "完成 Phase 3 组件拆分与复用"),
        ),
    ),
)

/** 当前安装包的版本信息：版本号 / 构建号 / 发布日期，全部从真实来源拼出来 */
data class VersionInfo(
    val versionName: String,
    val versionCode: Long,
    /** 更新日志里同版本的发布日期；开发构建（未登记）为 null */
    val releaseDate: String?,
) {
    /** `v1.1.0（构建 3）` */
    val display: String
        get() = "v$versionName（构建 $versionCode）"

    /** `v1.1.0 · 2026-11-03`；未登记日期时只显示版本号 */
    val displayWithDate: String
        get() = releaseDate?.let { "v$versionName · $it" } ?: "v$versionName"
}

/** 读当前安装包的 versionName / versionCode（取不到时留空，不做假数据兜底） */
fun currentVersionInfo(context: Context): VersionInfo {
    val info = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val name = info?.versionName.orEmpty()
    // minSdk 33 > 28，longVersionCode 一定可用
    val code = info?.longVersionCode ?: 0L
    return VersionInfo(
        versionName = name,
        versionCode = code,
        releaseDate = changelogOf(name)?.date,
    )
}

/** 把安装包的 versionName 映射到更新日志条目：`1.1.0` 与 `v1.1.0` 都认 */
fun changelogOf(versionName: String): ChangelogEntry? {
    val target = versionName.trim().removePrefix("v").removePrefix("V")
    if (target.isEmpty()) return null
    return changelogData.firstOrNull { it.version.removePrefix("v") == target }
}

// ── 第三方组件与致谢 ────────────────────────────────────────────────────

/** 开源组件 / 致谢条目的公共结构 */
data class OpenSourceItem(
    val name: String,
    /** 作者或组织 */
    val author: String,
    /** 许可协议名称（为空表示仅致谢） */
    val license: String = "",
    /** 该项目对本应用的具体贡献（灵感 / 代码 / 组件 / 图标 / 框架） */
    val contribution: String = "",
    val url: String = "",
    /** true = 源码直接内置在本仓库（已修改过，不再是原样的依赖库） */
    val bundled: Boolean = false,
)

/** 一组许可条目：按用途分组，比一长串平铺更好查 */
data class LicenseGroup(
    val title: String,
    val items: List<OpenSourceItem>,
)

/**
 * 特别致谢（关于页展示）。
 *
 * 只列「真的借鉴过」的项目，并按贡献类型写清楚借鉴了什么；
 * 纯依赖库不在这里重复（它们在 [licenseGroups] 里，许可证需要单独声明）。
 */
val thanksData: List<OpenSourceItem> = listOf(
    OpenSourceItem(
        name = "NexioSchedule",
        author = "Haooz (HaoZai000)",
        contribution = "灵感 + 代码：折叠式顶栏（AGSL 渐进模糊 + 大标题收起）、液态玻璃底栏与悬浮按钮、边缘光动效、" +
            "时间选择弹窗、图标锚定下拉菜单、背景动态光效；关于 / 更新日志 / 开源许可 / 隐私政策页的" +
            "页面结构与交互亦参照复刻",
        url = "https://github.com/HaoZai000/NexioSchedule",
    ),
    OpenSourceItem(
        name = "AutoAccounting",
        author = "开源记账项目（参考实现）",
        contribution = "灵感 + 交互参考：分类管理页（5 列网格 + 子面板展开 + 长按菜单）、分类编辑页的字段顺序" +
            "与校验规则（名称为空 / 未选图标）、以及自动记账能力的实现方式",
    ),
    OpenSourceItem(
        name = "Tally",
        author = "cypressincloud",
        contribution = "灵感：页面切换的横向推入 / 回退节奏（时长与缓动曲线），本项目以 Compose 自行实现",
        url = "https://github.com/cypressincloud/Tally",
    ),
    OpenSourceItem(
        name = "Miuix",
        author = "Yukonga",
        license = "Apache-2.0",
        contribution = "代码 + 图标（依赖库）：Card / Button / Switch / TextField / Dialog / Icon 等组件与 Miuix 图标，" +
            "构成全站设计系统",
        url = "https://github.com/compose-miuix-ui/miuix",
    ),
    OpenSourceItem(
        name = "Backdrop",
        author = "Kyant0",
        license = "Apache-2.0",
        contribution = "代码（源码内置）：AGSL 背景采样与模糊效果，支撑顶栏渐进模糊与液态玻璃底栏",
        url = "https://github.com/Kyant0/AndroidLiquidGlass",
        bundled = true,
    ),
    OpenSourceItem(
        name = "Android Jetpack",
        author = "Google",
        license = "Apache-2.0",
        contribution = "框架（依赖库）：Compose、Room、Navigation、DataStore、WorkManager 等",
        url = "https://developer.android.com/jetpack",
    ),
    OpenSourceItem(
        name = "Material Icons",
        author = "Google",
        license = "Apache-2.0",
        contribution = "图标：部分操作与状态图标",
        url = "https://fonts.google.com/icons",
    ),
)

/**
 * 第三方组件许可清单。
 *
 * 口径：只列**真实出现在依赖或源码里**的组件（2026-11-03 按 build.gradle.kts 与源码核对过）；
 * 每条都写清「怎么用进来的」—— 依赖库、源码内置、还是仅图标 / 字体素材。
 */
val licenseGroups: List<LicenseGroup> = listOf(
    LicenseGroup(
        title = "界面与交互组件",
        items = listOf(
            OpenSourceItem(
                "Miuix", "Yukonga", "Apache-2.0",
                "组件库（依赖）：Card / Button / TextField / Dialog / NavigationBar / NumberPicker 等",
                "https://github.com/compose-miuix-ui/miuix",
            ),
            OpenSourceItem(
                "Backdrop", "Kyant0", "Apache-2.0",
                "源码内置（已裁剪改编）：AGSL 背景采样、模糊与高光效果",
                "https://github.com/Kyant0/AndroidLiquidGlass",
                bundled = true,
            ),
        ),
    ),
    LicenseGroup(
        title = "基础框架",
        items = listOf(
            OpenSourceItem(
                "AndroidX / Jetpack", "Google", "Apache-2.0",
                "依赖：Compose、Room、Navigation、DataStore、WorkManager、Lifecycle、Activity Result",
                "https://developer.android.com/jetpack",
            ),
            OpenSourceItem(
                "Kotlin 与 kotlinx.coroutines", "JetBrains", "Apache-2.0",
                "依赖：语言运行时、协程与 Flow",
                "https://github.com/JetBrains/kotlin",
            ),
        ),
    ),
    LicenseGroup(
        title = "数据与网络",
        items = listOf(
            OpenSourceItem(
                "OkHttp", "Square", "Apache-2.0",
                "依赖：WebDAV 网盘备份的网络层",
                "https://square.github.io/okhttp/",
            ),
            OpenSourceItem(
                "Coil", "Coil Contributors", "Apache-2.0",
                "依赖：分类图标、账户图标与头像的异步加载与缓存",
                "https://github.com/coil-kt/coil",
            ),
            OpenSourceItem(
                "org.json", "JSON.org", "JSON License",
                "依赖：备份与导入文件的 JSON 读写",
                "https://github.com/douglascrockford/JSON-java",
            ),
        ),
    ),
    LicenseGroup(
        title = "系统能力",
        items = listOf(
            OpenSourceItem(
                "Shizuku API", "RikkaApps", "Apache-2.0",
                "依赖：以 shell 身份执行截屏（bindUserService + AIDL）",
                "https://github.com/RikkaApps/Shizuku-API",
            ),
            OpenSourceItem(
                "HiddenApiBypass", "LSPosed", "Apache-2.0",
                "依赖：反射访问隐藏 API 时的兜底",
                "https://github.com/LSPosed/AndroidHiddenApiBypass",
            ),
        ),
    ),
    LicenseGroup(
        title = "文字识别",
        items = listOf(
            OpenSourceItem(
                "ML Kit 文字识别（中文）", "Google", "Google ML Kit 服务条款",
                "依赖（非开源）：识屏记账的本地 OCR，模型在设备端离线运行",
                "https://developers.google.com/ml-kit/terms",
            ),
        ),
    ),
    LicenseGroup(
        title = "图标与字体",
        items = listOf(
            OpenSourceItem(
                "Miuix Icons", "Yukonga", "Apache-2.0",
                "依赖：全站图标",
                "https://github.com/compose-miuix-ui/miuix",
            ),
            OpenSourceItem(
                "Material Icons", "Google", "Apache-2.0",
                "依赖（material-icons-extended）：部分操作与状态图标",
                "https://fonts.google.com/icons",
            ),
            OpenSourceItem(
                "Inter", "Rasmus Andersson", "SIL OFL 1.1",
                "字体素材：数字、金额与西文",
                "https://github.com/rsms/inter",
            ),
            OpenSourceItem(
                "Actor", "Google Fonts", "SIL OFL 1.1",
                "字体素材：应用名与标题",
                "https://fonts.google.com/specimen/Actor",
            ),
        ),
    ),
)

// ── 本应用授权条款 / 隐私政策 ────────────────────────────────────────────

/** 说明性章节：标题 + 段落（隐私政策、授权条款共用） */
data class InfoSection(
    val title: String,
    val paragraphs: List<String>,
)

/**
 * 本应用的授权条款。
 *
 * 本应用**目前不开放源代码**，所以这里必须自己写清「允许做什么、不许做什么」，
 * 而不是只挂一份第三方许可清单让人误以为整个项目也是开源的。
 */
val appLicenseSections: List<InfoSection> = listOf(
    InfoSection(
        title = "著作权归属",
        paragraphs = listOf(
            "「i记」（以下简称「本应用」）的源代码、界面设计、图标、文案与文档的著作权归开发者 DHY 所有，" +
                "受著作权法与国际公约保护。",
            "本应用目前不开放源代码：源码仓库不对外提供，也未采用任何开源许可协议发布。" +
                "本页下方列出的开源组件仅指第三方依赖与素材，不包括本应用自身的代码。",
        ),
    ),
    InfoSection(
        title = "你可以做什么",
        paragraphs = listOf(
            "在遵守本条款的前提下，你可以：",
            "· 在个人设备上免费安装、使用本应用，用于个人或家庭记账；",
            "· 为备份目的保留安装包副本；",
            "· 在不修改、不收费、并注明来源的前提下，把官方发布的安装包原样分享给他人。",
        ),
    ),
    InfoSection(
        title = "你不可以做什么",
        paragraphs = listOf(
            "· 反向工程、反编译、反汇编，或以其他方式尝试获取本应用的源代码（法律强制许可的情形除外）；",
            "· 修改、翻译、二次发行本应用，或基于本应用制作衍生作品；",
            "· 销售、出租、以付费墙或广告方式分发本应用；",
            "· 移除、遮蔽或以任何方式更改应用内的著作权、商标与许可声明；",
            "· 将本应用用于任何违法用途，或用于侵犯他人权益的场景。",
            "如需商业授权、定制或分发许可，请通过下方「问题反馈」邮箱联系作者。",
        ),
    ),
    InfoSection(
        title = "第三方组件",
        paragraphs = listOf(
            "本应用包含若干第三方开源组件与素材，它们分别遵循各自的许可协议，" +
                "不受本条款限制；两者冲突时，以对应组件的许可证为准。",
            "完整的组件清单、作者与许可证见下方「第三方开源组件」各分组。" +
                "按 Apache-2.0 与 SIL OFL 1.1 的要求，相关组件的许可声明在此一并保留。",
        ),
    ),
    InfoSection(
        title = "无担保与责任限制",
        paragraphs = listOf(
            "本应用按「现状」提供，不附带任何明示或默示的担保，包括但不限于适销性、" +
                "特定用途适用性与不侵权的担保。",
            "在法律允许的最大范围内，因使用或无法使用本应用造成的任何数据丢失、" +
                "收益损失或其他间接损害，开发者不承担责任。记账数据关系重大，请自行定期备份。",
        ),
    ),
    InfoSection(
        title = "条款更新",
        paragraphs = listOf(
            "本条款随版本更新，最新版本始终展示在本页，并在「更新日志」中记录相应变更。",
            "继续使用本应用即表示你接受更新后的条款；如不接受，请停止使用并卸载。",
        ),
    ),
)

/** 隐私政策章节 */
val privacySections: List<InfoSection> = listOf(
    InfoSection(
        title = "我们收集什么",
        paragraphs = listOf(
            "i记 是一款纯本地记账工具。你的账单、资产账户、分类与标签等数据全部保存在本机数据库中，不会上传到任何服务器。",
        ),
    ),
    InfoSection(
        title = "权限使用",
        paragraphs = listOf(
            "读取媒体图片：仅在「记一笔」中添加附件图片时使用，用于读取你选择的图片。",
            "通知权限：仅在周期记账提醒时使用。",
            "截屏与无障碍（可选）：仅在你主动开启「识屏记账」后用于读取当前屏幕并识别账单，识别在本机完成，图片不上传。",
            "以上权限均为可选，拒绝后不影响记账核心功能。",
        ),
    ),
    InfoSection(
        title = "数据存储与备份",
        paragraphs = listOf(
            "所有数据默认仅存储在本机。使用导出 / 备份功能时，文件由你自行选择保存位置或分享方式，我们无法访问这些文件。",
            "启用网盘备份（WebDAV）时，备份文件会上传到你自行填写的服务器，凭据仅保存在本机。",
            "卸载应用会删除本机数据，请在卸载前先完成备份。",
        ),
    ),
    InfoSection(
        title = "第三方组件",
        paragraphs = listOf(
            "应用使用了开源组件与 Google ML Kit 文字识别用于界面构建、数据持久化与本地识别，" +
                "详见「许可与授权」页面。这些组件均不会收集你的个人数据。",
        ),
    ),
    InfoSection(
        title = "政策更新",
        paragraphs = listOf(
            "若本政策发生变更，我们会在应用内「关于应用 - 隐私政策」中更新，并同步到更新日志。",
        ),
    ),
)
