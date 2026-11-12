package com.iji.app.ui.mine

import android.content.Context

/**
 * 关于应用 / 更新日志 / 许可与授权 / 隐私政策 的静态数据。
 *
 * 三条约定，改内容时请一起遵守：
 * 1. **版本号只有两处**：`app/build.gradle.kts` 的 versionName / versionCode 是安装包的事实，
 *    本文件的更新日志负责「这个版本什么时候发、改了什么」。页面上的「当前版本」由
 *    [currentVersionInfo] 把两边拼起来，不额外维护第三份常量。
 * 2. **日期统一 `yyyy-MM-dd`**；归档条目用「开始 ~ 截止」区间。日志按由新到旧排列，
 *    第一条即当前版本。
 * 3. **每条只写一句人话**：不分类别前缀（新增/修复/优化）、不留分支或内部术语，
 *    写用户读得懂的「改成了什么」。
 */

// ── 版本与更新日志 ──────────────────────────────────────────────────────

/** 一个版本的变更记录；归档条目的 `version` / `date` 记区间 */
data class ChangelogEntry(
    val version: String,
    /** 发布（或归档）日期，`yyyy-MM-dd`；归档条目为 `开始 ~ 截止` */
    val date: String,
    /** 逐条变更，一行一条 */
    val items: List<String>,
)

/** 更新日志：由新到旧。第一条必须对应当前 versionName（见 [changelogOf]）。 */
val changelogData: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        version = "v1.0.0",
        date = "2026-11-12",
        items = listOf(
            "首个正式版",
        ),
    ),
    ChangelogEntry(
        // Beta 阶段 16 个版本合并归档为一条开发记录：版本、日期都记区间，只留重要节点
        version = "v0.0.1 Beta → v0.9.8 Beta",
        date = "2026-07-16 ~ 2026-11-03",
        items = listOf(
            "从零搭建本地记账：记一笔、账单列表、账户与分类管理",
            "统计、日历、账本三页重构，统一折叠顶栏与液态玻璃底栏",
            "账单导入导出与 WebDAV 网盘备份",
            "AI 解析、自动记账、识屏记账悬浮窗确认与超级岛通知",
            "补齐关于应用、授权条款与隐私政策",
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
    /** `v1.0.0（构建 4）` */
    val display: String
        get() = "v$versionName（构建 $versionCode）"

    /** `v1.0.0 · 2026-11-12`；未登记日期时只显示版本号 */
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

/** 把安装包的 versionName 映射到更新日志条目：`1.0.0` 与 `v1.0.0` 都认 */
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
 * 只留项目名称、作者与仓库地址，点击整行跳转；贡献说明与许可证标注不再展示，
 * 依赖库的许可细节统一在 [licenseGroups]。
 */
val thanksData: List<OpenSourceItem> = listOf(
    OpenSourceItem(
        name = "NexioSchedule",
        author = "Haooz (HaoZai000)",
        url = "https://github.com/HaoZai000/NexioSchedule",
    ),
    OpenSourceItem(
        name = "Hyper-pick-up-code",
        author = "badnng",
        url = "https://github.com/badnng/Hyper-pick-up-code",
    ),
    OpenSourceItem(
        name = "Maling-Island（码灵属）",
        author = "SiberiaApp",
        url = "https://github.com/SiberiaApp/Maling-Island",
    ),
    OpenSourceItem(
        name = "AutoAccounting",
        author = "AutoAccountingOrg",
        url = "https://github.com/AutoAccountingOrg/AutoAccounting",
    ),
    OpenSourceItem(
        name = "Miuix",
        author = "Yukonga",
        url = "https://github.com/compose-miuix-ui/miuix",
    ),
    OpenSourceItem(
        name = "Backdrop",
        author = "Kyant0",
        url = "https://github.com/Kyant0/AndroidLiquidGlass",
    ),
    OpenSourceItem(
        name = "Android Jetpack",
        author = "Google",
        url = "https://developer.android.com/jetpack",
    ),
    OpenSourceItem(
        name = "Material Icons",
        author = "Google",
        url = "https://fonts.google.com/icons",
    ),
)

/**
 * 第三方组件许可清单。
 *
 * 口径：只列**真实出现在依赖或源码里**的组件（2026-11-10 按 build.gradle.kts 与源码核对过）；
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
                "依赖：Compose、Room、Navigation、NavigationEvent、DataStore、WorkManager、" +
                    "Lifecycle、Activity Result、Security Crypto",
                "https://developer.android.com/jetpack",
            ),
            OpenSourceItem(
                "Kotlin 与 kotlinx.coroutines", "JetBrains", "Apache-2.0",
                "依赖：语言运行时、协程与 Flow",
                "https://github.com/JetBrains/kotlin",
            ),
            OpenSourceItem(
                "JetBrains Annotations", "JetBrains", "Apache-2.0",
                "依赖（org.jetbrains:annotations）：液态玻璃底栏 AGSL RuntimeShader 编译所需",
                "https://github.com/JetBrains/java-annotations",
            ),
        ),
    ),
    LicenseGroup(
        title = "数据与网络",
        items = listOf(
            OpenSourceItem(
                "OkHttp", "Square", "Apache-2.0",
                "依赖：AI 记账、WebDAV 网盘备份与应用内更新的网络层",
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

// ── 隐私政策 ────────────────────────────────────────────────────────────

/** 隐私政策生效日期；修订正文时请一并更新。 */
const val privacyEffectiveDate = "2026-11-08"

/**
 * 隐私政策版本：正文、生效日期或数据处理方式变更时 +1。
 * 已同意的版本号存在 SettingsRepository.KEY_PRIVACY_AGREED_VERSION，
 * 小于此值的老用户下次启动会重新征求同意。
 */
const val privacyPolicyVersion = 1

/** 对外联系邮箱：隐私政策、许可条款与「问题反馈」邮件共用。 */
const val contactEmail = "yuhaod0106@163.com"

/**
 * 隐私政策章节。
 *
 * 写作口径：只陈述代码中确实存在的行为（权限、联网点、存储位置、删除能力），
 * 不承诺尚未实现的服务端留存、账号注销或在线同意撤回机制。
 */
val privacySections: List<InfoSection> = listOf(
    InfoSection(
        title = "一、适用范围与基本原则",
        paragraphs = listOf(
            "「i记」（以下简称「本应用」）由个人开发者独立开发与运营。本政策依据《中华人民共和国个人信息保护法》等适用法规，" +
                "说明本应用在安装与使用过程中如何处理你的信息。",
            "本应用为本地记账工具：不建立账号体系、不运营服务端、不接入广告、统计分析或崩溃上报 SDK。" +
                "你的记账数据默认仅保存在设备本机，我们无法访问，也不会主动收集。",
            "上述信息仅用于完成你在本应用内主动发起的记账、统计、备份、识别与更新等功能，" +
                "不用于用户画像、精准营销或自动化决策。本政策适用于 i记 Android 客户端及其内置功能。",
        ),
    ),
    InfoSection(
        title = "二、我们处理的信息",
        paragraphs = listOf(
            "(1) 你主动录入的数据：账单金额、时间、分类、备注、交易对手、附件图片、账户、账本、预算、模板与标签等，" +
                "全部写入本机数据库，不上传。",
            "(2) 设置与凭据：昵称、头像、应用锁 PIN、AI 接口地址与密钥、WebDAV 服务器地址与账号密码等，" +
                "保存在本机；其中应用锁 PIN 使用 Android Keystore 加密存储，备份文件使用 Keystore 管理的密钥加密。",
            "(3) 诊断信息：应用异常时在本机记录崩溃时间与堆栈（最多保留 10 条），仅在你主动导出时才会离开设备，不会自动上报。",
            "(4) 我们不收集：姓名、手机号、身份证号等身份信息（你主动发邮件联系我们时提供的除外）；" +
                "IMEI、Android ID、广告 ID 等设备标识符；通讯录、短信、通话记录与精确位置。",
        ),
    ),
    InfoSection(
        title = "三、系统权限与设备能力",
        paragraphs = listOf(
            "· 互联网访问：仅用于第四节列出的四类连接。",
            "· 通知：用于周期记账提醒与识屏结果确认，由你授权，拒绝不影响记账。",
            "· 悬浮窗、无障碍服务与 Shizuku（可选）：仅在你主动开启「识屏记账」后使用。开启后可读取到达的通知文本、" +
                "前台应用包名，或在你主动触发时截取一帧屏幕用于本机文字识别；通知文本与包名在本机内存解析，" +
                "不落库、不上传，识屏草稿不写入数据库。",
            "· 麦克风：用于「语音记一笔」调用系统语音识别，识别结果以文字返回本应用。" +
                "本应用不保存也不上传音频文件；系统识别服务的数据处理适用该服务自身的政策。",
            "· 相机与图片：通过系统相机和系统文件选择器获取图片，不申请相机或媒体读取权限；" +
                "图片仅用于账单附件、头像与本机 OCR 识别。",
            "· 生物识别：调用系统生物识别弹窗解锁应用，本应用不读取、不保存你的生物特征信息。",
            "· 安装未知应用：仅用于安装你确认下载的更新包；忽略电池优化用于保障提醒按时执行。",
            "以上权限均可在系统设置中随时关闭；除记账核心功能外，关闭任一可选权限不影响其他功能使用。",
        ),
    ),
    InfoSection(
        title = "四、信息何时会离开你的设备",
        paragraphs = listOf(
            "默认状态下，本应用不会向任何服务器发送你的记账数据。仅在下列由你主动开启或触发的情形下才发生网络传输：",
            "(1) 检查更新：打开「检查更新」时，会向内置的更新源（Gitee Releases 接口）请求版本信息，" +
                "并在你确认后下载安装包至应用私有目录。该请求不携带账单、账户或设备标识符，" +
                "但接收方可能按其政策记录网络层信息（如 IP 地址）。",
            "(2) 云端 AI 解析：默认使用本机规则与离线模型，不联网。仅当你自行填写并保存大模型 API 地址与密钥、" +
                "且选择云端解析时，你输入的账单文本、语音转写文字、可选的小票图片（降采样）" +
                "以及统计小结所需的聚合数据，才会发送至你所选择的服务商。" +
                "该服务商为独立的数据处理方，其处理适用其自身隐私政策；部分服务商位于境外，选择即表示你知晓相应跨境传输。",
            "(3) 网盘备份：仅在你填写 WebDAV 服务器信息并开启备份（自动备份默认关闭）后，" +
                "才会上传使用 AES-256-GCM 加密的备份文件，密钥由本机 Android Keystore 管理。" +
                "我们无法访问你的服务器，也无法解密该文件。",
            "(4) 外部链接与邮件：点击发布页、开源项目链接或「问题反馈」会交由浏览器或邮件应用处理，受其各自政策约束。",
            "除上述情形外，我们不接收、不留存你的任何记账数据。",
        ),
    ),
    InfoSection(
        title = "五、数据存储、安全与保留期限",
        paragraphs = listOf(
            "数据默认保存在应用私有目录，应用未申请外部存储读写权限，并已关闭系统自动备份（allowBackup=false）。",
            "首次使用时的隐私政策同意状态仅记录在本机偏好设置中，不会上传；" +
                "清除数据或卸载本应用后该记录会一并删除。",
            "应用锁 PIN 及本地备份文件采用 Android Keystore 与 AES-256-GCM 加密；" +
                "但由你导出到「下载」目录或自选位置的文件（JSON、CSV、崩溃日志）为明文，请自行妥善保管。",
            "数据保留至你删除或卸载本应用为止。卸载或使用系统「清除数据」会删除应用私有数据；" +
                "公共目录中的导出文件不会随之删除，需你手动清理。自动备份在应用私有目录最多保留 7 份，可手动删除。",
            "我们不保存可用于恢复的数据副本，恢复只能使用你自行导出的备份文件；" +
                "请定期通过「数据与备份」导出，避免因设备丢失或卸载造成损失。",
        ),
    ),
    InfoSection(
        title = "六、第三方组件与信息共享",
        paragraphs = listOf(
            "我们不出售、出租或向第三方共享你的个人信息，也不接入广告、统计、推送或用户画像 SDK。",
            "应用集成的第三方开源组件与 Google ML Kit 中文文字识别模型均在设备端运行，" +
                "用于界面构建、数据持久化与离线识别，不向开发者回传你的内容；完整清单见「许可与授权」。",
            "第四节所述的更新源、AI 服务商与 WebDAV 服务器，由应用发布方指定或由你自行配置，" +
                "相关传输在你触发时直接发生，不属于我们向第三方共享数据的行为。",
        ),
    ),
    InfoSection(
        title = "七、你的权利与选择",
        paragraphs = listOf(
            "· 查阅与导出：可在应用内查看全部账单，并通过「数据与备份」导出 JSON 或 CSV。",
            "· 更正与删除：可编辑、删除单笔或指定区间的账单，删除账本与账户，清空导入历史、崩溃日志与网盘配置。",
            "· 撤回授权：可在系统设置关闭通知、悬浮窗、无障碍、麦克风等权限；" +
                "也可在应用内关闭云端 AI 解析与自动备份，或清除已保存的密钥与凭据。",
            "· 停止使用：卸载应用即停止一切数据处理并删除本机私有数据。" +
                "因本应用无账号体系，不存在账号注销，我们一侧亦无数据副本可供查询或删除。",
            "如你认为本应用在本政策范围外处理了你的信息，请通过第十节所列邮箱联系，我们将在合理期限内核实并回复。",
        ),
    ),
    InfoSection(
        title = "八、儿童个人信息",
        paragraphs = listOf(
            "本应用不面向不满十四周岁的儿童，也不会有意收集其个人信息。",
            "若监护人发现儿童在未经同意的情况下提供了信息，请通过第十节邮箱联系我们。" +
                "由于数据仅存在于设备本机且我们不留存副本，可通过应用内删除、清除数据或卸载完成处理。",
        ),
    ),
    InfoSection(
        title = "九、政策更新",
        paragraphs = listOf(
            "本政策可能随功能调整或法律要求变化而修订。更新后将在本页发布并同步生效日期，重大变更会在「更新日志」中记录。",
            "政策更新后会在你下次打开应用时重新征求同意；不同意可退出应用，并在卸载前自行导出所需数据。",
        ),
    ),
    InfoSection(
        title = "十、联系我们",
        paragraphs = listOf(
            "运营主体：i记（个人开发者独立开发与运营）。",
            "联系邮箱：$contactEmail。关于本政策的疑问、意见或数据相关请求，请通过该邮箱联系，我们将在合理期限内回复。",
        ),
    ),
)
