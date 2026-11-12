# i记（iji-android）交接文档

> **单一权威文档 · 最后更新 2026-11-12**
> 本轮（11-12）：加密备份改成「先 gzip 再 AES」（新旧格式都认）、文件导入按字节识别、网盘按 7 份保留、识屏/分享临时图用完即删。真机与模拟器验证结果见 **§8.7**，剩余待办见 **§9.1**。
> 原 8 份 Markdown（本文 + README + 6 份专题/历史文档）已收敛为「本文 + 一页 README」；重复段落删除，历史过程只保留结论与理由。
> 工程根 `Y:\iji-android`｜应用身份 `com.haloyu.iji`（2026-11-13 起；namespace 代码包仍是 `com.iji.app`）｜改动全部在工作区、**未提交 git**。
>
> 读法：**§2 能跑起来，§7 别踩坑，§9 是接着做的活**。

## 1. 项目概览

| 项 | 现状 |
|---|---|
| 版本 | `1.0.0`（versionCode 4） |
| 形态 | 单 Activity + Compose；四 tab（账本 / 资产 / 日历 / 统计）+ 记一笔悬浮面板 + 右上「我的」 |
| 风格 | Miuix（HyperOS）+ Material3；主色 `#2177E8`，收入绿 `#34C759` |
| 数据 | Room **v24**（7 张表）+ SharedPreferences + DataStore |
| 质量 | 可编译 / 可安装 / 可运行；**256 个单测 / 28 个测试类全绿**；`assembleDebug` / `assembleRelease` 通过 |
| 走查 | 模拟器多轮通过；真机（Android 16）备份链路本轮已过，剩余专项见 §9.1 |

已落地：多账本、多币种（账本级，只换符号不换算）、分类自由增删改排序、预算与提醒、周期账单、模板、应收垫付、标签、附件、应用锁、CSV 导入导出、JSON 全量/配置备份、WebDAV 网盘、自动备份、AI 记账、截屏批量导入、Shizuku 与无障碍识屏、支付通知记账、小米超级岛。

## 2. 快速上手

### 2.1 构建

```powershell
cd Y:\iji-android
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'   # 系统 PATH 里没有 java，必须显式设置
.\gradlew :app:compileDebugKotlin --console=plain  # 快速语法检查
.\gradlew :app:assembleDebug --console=plain       # 产物 app\build\outputs\apk\debug\app-debug.apk
.\gradlew :app:assembleRelease --console=plain     # 见 §2.1.1，产出两个按 ABI 分包的已签名 APK
```

- JDK 24 + Gradle 9.6 会打 `native-access` 警告，不影响编译。
- **构建成功 ≠ APK 可用**：曾出现「DEX 里没有 Kotlin 类、CLI 仍 SUCCESSFUL、真机 `ClassNotFoundException`」。换过构建配置后解包确认 dex 里有 `IjiApplication`（§7.1）。
- 国内网络 wrapper 下载超时：手动下载 zip 放进 `%USERPROFILE%\.gradle\wrapper\dists\gradle-9.6.0-bin\<hash>\`，再建同名 `.ok` 文件即可走本地缓存。
- 本机 `D:\SDK` 的 API 37 平台目录名是 `android-37.0`，AGP 9.x 能识别；降回 AGP 8.x 会报找不到 `platforms;android-37`。

#### 2.1.1 发布包（签名 + ABI 分包）

`assembleRelease` 产出两个**已签名**的 ABI 分包包，不再是单个 universal 包：

| 产物 | 用途 | 体积 |
|---|---|---|
| `app\build\outputs\apk\release\app-arm64-v8a-release.apk` | 真机分发 | 24.3 MiB |
| `app\build\outputs\apk\release\app-x86_64-release.apk` | 模拟器验证 | 24.9 MiB |

- **签名材料**：根目录 `release.jks`（PKCS12，RSA 2048，有效期 10000 天，DN `CN=iJi Android, OU=Release, O=iJi, C=CN`）+ `keystore.properties`，两者均已被 `.gitignore` 排除。**必须离线另存一份**——丢失后无法再发覆盖升级包。密码不在仓库里，由持有者保管。
- **缺 `keystore.properties` 时**：release 仍能构建，但产物是未签名包，`adb install` 会失败；只影响分发，不影响日常开发。
- **换签名必须卸载重装**：签名变更后无法覆盖安装（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），卸载会清空应用数据。从 debug 包切到正式包时同理，先备份。
- **ABI 分包只在 `assembleRelease` 时启用**（`app/build.gradle.kts` 里按 taskNames 判断），`assembleDebug` 仍是单一 `app-debug.apk`，既有交付路径不变。
- **只保留 `arm64-v8a` + `x86_64`**：前者覆盖真机，后者供模拟器验证；已去掉 `armeabi-v7a` / `x86`。新增 ABI 需求时改 `splits.abi.include`。
- 体积口径（真机实测，2026-11-12）：原 universal 包 71.7 MiB / 装机约 98 MiB → ABI 分包 42.9 MiB → 再叠加收紧 Compose keep 后 **APK 24.3 MiB + AOT 13.1 MiB ≈ 37.5 MiB**（`speed-profile` 编译后，arm64 真机）。ML Kit OCR 的 `.so` 仍占 10.6 MiB，是剩余最大单项；`.so` 不参与 dexopt，所以只算 APK 那一份。
- **release 包不可 `run-as`**：正式包非 debuggable，`adb shell run-as com.iji.app` 会报 `package not debuggable`。走查 release 包时不能用 run-as 拉数据库/看私有目录；需要看数据就用 debug 包，或用应用内的导入导出。

### 2.2 安装与走查

```powershell
$adb = 'D:\SDK\platform-tools\adb.exe'   # 不在 PATH
& $adb devices
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am force-stop com.haloyu.iji
& $adb shell am start -n com.haloyu.iji/com.iji.app.MainActivity
```

截图必须重定向输出流（PowerShell 的 `>` 会破坏 PNG 二进制）：

```powershell
Start-Process -FilePath $adb -ArgumentList 'exec-out','screencap','-p' `
  -RedirectStandardOutput 'C:\Temp\s.png' -NoNewWindow -Wait
```

- 点击 `adb shell input tap X Y`；滑动 `input swipe X1 Y1 X2 Y2 400`；**长按**用 `input swipe X Y X Y 1000`。
- 屏幕基准 1280×2856；坐标优先用 `uiautomator dump` 拿真实 bounds。
- **验证超级岛必须按 HOME 退到后台**：应用在前台时系统不浮出岛，属正常行为。

### 2.3 单元测试

`.\gradlew testDebugUnitTest` **在本机必失败**（Gradle worker 起不来，用户目录含中文）。改为直跑 JUnitCore：

```powershell
.\gradlew :app:compileDebugUnitTestKotlin --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run_unit_tests.ps1
```

- 脚本自动扫描编译产物里的 `*Test.class` 并拼 classpath（junit / hamcrest / kotlin-stdlib / org.json / coroutines / okhttp / okio）。**加测试类不用改脚本**，只有新引入运行期依赖时才补一条 jar。
- 纯 JVM 单测没有 Android Context：`CategoryStore.all` 未 `init` 时回落到 `CategoryDefaults`，**不要改成抛异常**；`SettingsRepository`/DataStore 不可用，可测逻辑要写成纯函数（名单、配置作为参数传入）。

## 3. 技术栈与架构

### 3.1 版本（以 `gradle/libs.versions.toml` 为准）

| 项 | 版本 |
|---|---|
| AGP / Gradle / Kotlin | 9.3.0 / 9.6.0 / 2.4.20（**Kotlin 由 AGP 内置**，见 §7.1） |
| compileSdk / targetSdk / minSdk | 37 / 36 / 33 |
| UI | Compose BOM 2026.08.00 + Material3 + **Miuix 0.9.4**（`miuix-ui` / `miuix-icons` / `miuix-blur` / `miuix-squircle`） |
| 持久化 | Room 2.8.5（KSP 2.3.12），数据库 **v24**；偏好 DataStore 1.1.4 |
| 图片 / OCR | Coil 3.3.0；ML Kit 中文 OCR 16.0.1（本地兜底） |
| 系统能力 | WorkManager 2.10.0、Shizuku 13.1.5、hiddenapibypass 4.3、无障碍服务 |
| 导航 | navigation-compose 2.8.5 |
| 版本号 | versionName `1.0.0` / versionCode `4`（与更新日志首条对应） |

### 3.2 架构

- 单 `ComponentActivity` + Compose；`IjiApplication` 初始化顺序：`FontScale → CategoryStore.init → Room(DbProvider.init) → WarmUp`。
- `MainViewModel` 是跨页共享大 VM，按「月 / 账本」查询用 `stateIn(WhileSubscribed)`。**导出类功能不要读它的 value，直接查库**，否则会拿到未加载的空列表。
- 各页面另有自己的 ViewModel；跨页状态走 `savedStateHandle`；Repository 层封装 Room / SharedPreferences / DataStore。
- 全局单例（`CurrencyDisplay`、`CategoryCatalog`、`ProfileState` 等）不用 Compose 状态，UI 靠 `cacheVersion` 或 Flow 刷新（§7.3）。

## 4. 工程结构

```
app/src/main/java/com/iji/app/
├── IjiApplication.kt          # 初始化顺序见 §3.2
├── MainActivity.kt            # 纯 Compose 宿主（必须 ComponentActivity）
├── MainViewModel.kt           # 账单 / 汇总 / 增删 / 分类删除迁移 / 周期账单执行
├── data/
│   ├── model/                 # Models.kt、Currency.kt、AccountType.kt、ImportBatch.kt、ImportPayload.kt
│   ├── db/                    # AppDatabase(v24) + Entity/Dao + Converters + Migrations
│   └── repository/            # Transaction/Account/Ledger/Budget/Recurring/Template/
│                              # CategoryCatalog+CategoryStore+CategoryDefaults/CategoryIconStore/
│                              # BackupRepository(CSV)/ConfigBackupRepository(JSON)/QuickBillWriter/
│                              # SettingsRepository(DataStore)
├── ai/                        # AiBookkeepingService、AiConfig、AiPromptKind、LocalRule(Store/Engine)、
│                              # ScreenshotBillParser、LocalOcrEngine、AiSummaryService
├── accessibility/             # 支付通知解析与过滤、无障碍截屏、识屏流程、音量键服务
├── backup/                    # BackupManager / ConfigBackup / WebDavClient / CloudBackupManager / AutoBackupWorker
├── budget/  reminder/  update/  lock/  shizuku/
└── ui/
    ├── AppRoot.kt             # Routes + NavHost + 底部四 tab + 记一笔圆钮 + ImmersiveRoutes
    ├── nav/ theme/ util/
    ├── components/            # IjiTopBar / IjiDialog / IjiTextAction / IjiSettingsRows / Formatters / IconLoader
    ├── effects/               # AGSL 渐进模糊、layerBackdrop、折叠顶栏
    ├── home/ assets/ calendar/ stats/ add/ budget/ ai/ overlay/
    └── mine/                  # 我的 + 设置子页（30+ 文件，统一 MineSubPage）
```

测试在 `app/src/test/java/com/iji/app/`（23 个纯 JVM 测试类）。

## 5. 功能与页面

| 模块 | 状态 |
|---|---|
| 首页账本 | 月度卡片、按天分组、点行进详情、多选（导出/删除）、逐行滑动（右滑编辑）、折叠顶栏 + AGSL 模糊 + 日头吸顶、四种空态 |
| 记一笔 | 支出 / 收入 / 转账（双账户）、组合付款、优惠、报销与未对账、标签、附件、四则运算键盘、保存再记、模板、AI 预填、**退出即存草稿** |
| 资产 | 账户增删改（品牌图标 + 实时校验 + 未保存确认）、净资产/总资产/负债、借贷记录、对账与报销列、按分类分组 |
| 日历 | 卡片化、横滑切月、固定 6 行、选中=主色实心 / 今天=描边 / 相邻月淡显可点、每日账单 |
| 统计 | 维度切换（周/月/年）、分类构成（一级/细分）、趋势图、预算进度、分类下钻、AI 小结 |
| 我的 | 6 组：记账（分类/账本/标签/模板）、预算与账单（预算/周期账单/应收·垫付）、自动化（自动记账/AI 记账）、数据与备份（数据与备份 / 账单导入/导出 两行）、偏好（偏好设置/密码保护）、关于（新手教程/关于应用） |
| 偏好设置 | **5 组**：外观 / 提醒 / 超级岛 / 时间 / 动效与手感；主题用滚轮、通知样式在「提醒」组 |
| 数据能力 | CSV 导出（UTF-8 BOM、14 列含币种，入口在账单导入/导出页）、全量/配置 JSON、自动备份、截图与 CSV 批量导入、多币种（只换符号）、导入批次可撤销/重放/单条删除记录（删除只删历史行，不动账单与余额）、图片识别用 AI 单开关（开=跳过本机 OCR 直发视觉模型、失败自动回落本地 OCR；关=纯本地 OCR） |
| 数据与备份 | 三入口：导出数据备份（全量/配置，下载目录或自选）、将备份的导入（自动识别全量/流水/配置，先预览再确认）、网盘备份（目录 + 内容 + 就地状态）；账单 CSV 导入/导出统一在「我的 → 数据与备份 → 账单导入/导出」 |

**路由与安全区**：新增子页一律用 `MineSubPage`，并在 `AppRoot.kt` 的 `ImmersiveRoutes` 登记；漏登记会让 `ijiRoute` 在外层再垫一次状态栏高度（实测底部多 52dp，整页下沉）。

## 6. 数据与持久化（动表必读）

- **Room**：`iji.db`，`version = 24`，`addMigrations(1→2 … 23→24)` 全链显式迁移，`exportSchema = true`。v24 给 `import_history` 加了 `recordIds` / `payload` / `undone`，导入批次可撤销、可重放。
  7 张表：`transactions / accounts / ledgers / recurring_rules / budgets / budget_alerts / import_history`。
  标签、模板、分类目录、图标覆盖**不在 DB**，走 SharedPreferences。
- **字段口径**：金额一律 `Long`（分）；日期 `epochDay`；`minuteOfDay` 为当天第几分钟，`-1 = 只记到天`（v23 新增）。
- **降级会清库**：`fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` 是刻意设计（旧版 APK 覆盖新版时避免崩溃）。**回退版本 / 装低版本包 = 数据全丢**，别在真机用户数据上试；正常升级不走这条。
- **SharedPreferences**：`CategoryStore`（分类全量目录 + `seeded_v2`）、`CategoryIconStore`（`override_id_<分类id>`，读时懒迁移旧名字 key）、`TemplateRepository`、`AppLockManager`（加密 PIN）、`LocalRuleStore`、`CapturePreferences`、部分偏好。
- **DataStore**：`SettingsRepository`（字体缩放、通知样式、岛开关、备份偏好、更新源等）。
- **备份 JSON**：`kind=iji-full` + `config` + `transactions`；分类整份导出（`categoriesFull = true`），恢复时整表替换；老备份只有自定义分类时按 id 补缺。恢复**先 config 后 transactions**，否则流水引用不到分类/账户。
- **加密备份 `.ijbak`**：`[1B 版本][12B IV][AES-GCM 密文+Tag]`，明文先 gzip；版本 2 = 压缩，版本 1 = 旧包（只读兼容）。密钥在 Keystore、不可导出，备份只能原机还原。格式细节与导入/清理口径见 **§8.7**。
- **CSV**：导出 14 列，第 14 列为币种；导入按表头找列，不写死下标；微信/支付宝/通用三种格式。
- **新增 DB 列**：`ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT …` + 版本号 +1 + 注册迁移（参考 `MIGRATION_22_23`）。

## 7. 硬约束（踩过坑，别改回去）

### 7.1 构建

1. **AGP 9.3 内置 Kotlin（红线）**：`app/build.gradle.kts` **不要** apply `org.jetbrains.kotlin.android`，不要写 `jvmTarget` 块；`gradle.properties` **不要**设 `android.builtInKotlin=false`。设错的后果是 Kotlin 类不进 dex、CLI 仍 SUCCESSFUL、真机启动即 `ClassNotFoundException`。JVM target 由 `compileOptions` 的 `VERSION_11` 决定。
1.1 **别把 `-keep class androidx.compose.** { *; }` 加回来**：它让 R8 无法裁剪 `material-icons-extended`（34 MB AAR、约 1.1 万个图标类）。2026-11-12 实测该规则让 release dex 多留 18.27 MiB、APK 多 18.6 MiB、AOT 再多约 4 MiB。项目只用到 6 个 material 图标，删规则后 R8 正常保留被引用的类（仅重命名），真机全页回归通过。Compose 自身的 keep 由 androidx AAR 的 consumer-rules 提供。
2. **`MainActivity` 必须继承 `ComponentActivity`**：改成 `FragmentActivity`（例如为了 `androidx.biometric`）会让全项目 ActivityResult（选图/选文件）一起崩。
3. Room 注解处理走 KSP；改 `AppDatabase` 后必须同步迁移链，否则老库升级时崩溃。

### 7.2 UI 与交互

4. **所有弹窗统一 `IjiDialog`**（液态玻璃外壳）。调用点必须位于 Miuix `Scaffold` 子树内，且根部要有 `NavigationEventDispatcherOwner`，否则弹层拿不到宿主。
5. **长按菜单不要用 Popup**：`OverlayCascadingListPopup` 之类由长按触发时，松手那一帧会被当成「点外部」直接关掉（一闪而过）。长按一律 `IjiDialog`。
6. **子页面**：一律 `MineSubPage` + `ImmersiveRoutes` 登记（§5）。标题靠 `showSmallTitle` 兜底，别改回页面自己画标题。
7. **顶栏对齐口径**：返回左 16dp / 标题全栏水平居中 / 操作右 16dp，三者垂直中心同一线，由 `CollapsibleTopBar` 统一摆放；纯色顶栏底色取 M3 `background`（与页面同色），不要取 Miuix 白，否则状态栏下留白带。
8. **底栏选中态只单向跟随路由**，不要在 `onTabSelected` 里反向改 index（点「我的」会被弹回首页）。
9. **`layerBackdrop` 必须传 `recordKey`**，否则每帧全屏录制采样层导致玻璃卡顿。底栏传 `currentRoute`；顶栏传 `Unit + mustRecord = isScrollInProgress`。
10. **`awaitEachGesture` 里 down 之前不能 `return`**（会让主线程空转 ANR），手势骨架要完整走完。
11. **返回键优先级**：弹层 > 页面内临时模式（如多选）> 路由出栈 > 退出应用。多选态返回只退出多选。
12. **设置类页面用 `IjiSettingsRows` 拼装**，别再手抄 Card + 行 + 分隔线；偏好设置页不放左图标；一件事只出现在一个分组。
13. **四个主 tab 的 LazyColumn** 必须挂 `collapsibleTopInset(scrollBehavior)` + `nestedScroll(scrollBehavior.nestedScrollConnection)` + 条件 `layerBackdrop(topBarBackdrop)`，漏挂会让文字与顶栏模糊块重叠（资产页曾漏，表现为 120dp 白块压住 52dp 起始的内容）。
14. **性能优先于动画**：动画卡顿且无法优化时直接删（已删底栏 / 记一笔按钮的按压缩放）。
- **浮层选项卡片必须显式定宽**：`OverlayListPopup(minWidth = …)` 只保证下限，条目 `fillMaxWidth` 会把面板撑成通栏白条；`IjiOptionCardPopup` 的根节点必须 `Modifier.width(PanelWidth)`。
- **状态栏/导航栏图标明暗跟随 App 主题**：用 `isAppDarkTheme()`（联动 `ThemeState`），不要只用 `isSystemInDarkTheme()`；否则「系统浅色 + App 手动深色」时图标仍是深色，黑底黑字不可见。

### 7.3 数据与语义

15. **无 Context 的全局单例不要用 `mutableStateOf`**（`CurrencyDisplay`、`CategoryCatalog` 等）：单测是纯 JVM 直跑，引 Compose 运行时会 `NoClassDefFoundError`。
16. **金额格式化统一 `formatMoney(cents, currency)`**（默认取当前账本币种），不要硬编码 `¥`；固定浅底容器要显式给内容色，深色模式下否则文字纯黑。
17. **分类 id 是稳定标识**：改名不改 id，图标覆盖按 id 存。`CategoryCatalog.of(未知 id)` 返回「未分类」占位是**刻意**的（分类可被删除）。
18. **退款用「负数支出」**（`refundOfId` 关联原单），不建独立表、不算收入；「退款」也不能进本地规则的收入语义词。
19. **不要造假入口**（既定原则）：做不了就不放按钮；配置必须能清除；不许写死「已是最新 / 成功」。
20. **存储语义**：转账不计收支统计；组合付款用 `splits`；多币种跨币种金额不相加。
21. **Miuix `TextField` 只有 `label`（String），没有 `placeholder`**；需要占位提示时用 Material3 `OutlinedTextField`。
22. **KDoc 里不要写含 `/*` 的路径**（如 `icons/category/*.png`），会触发嵌套注释导致整个文件语法错误。

## 8. 关键子系统

### 8.1 AI 记账与自动记账

**解析管线**（`AiBookkeepingService`；通知规则移植自 AutoAccounting 的 AutoRule v0.5.6）：

```
文本 / 语音 → parseBill(text)
  ① PaymentNotificationParser.parse(null, text)   26 条 App 专属规则 + 通用兜底，金额 > 0 即命中
  ② AI（AiConfig.aiReady：总开关开 + 有 Key）
  ③ LocalRuleEngine（本地关键词 → 分类 / 账户）
图片 → LocalOcrEngine（本地优先）→ 认不出金额且「图片识别用 AI」开 → 视觉模型 → 规则兜底
```

- `BillResult` 是全链路（AI / 通知 / 悬浮窗 / 确认面板）**唯一**的解析结果类型；`PaymentBillStore` 落盘 JSON 必须保持兼容（老版本 pending bill 仍会被新代码 consume）。
- 金额解析：`cents()` 用字符串精确解析，`parseBill` 两处 `(元 * 100).toLong()` 已改 `Math.round`（修复 19.90 → 1989 的浮点 bug）。
- 识别方式**下沉到管线**：`parseBill` / `parseBillFromImage` 的 `useAi: Boolean?`，`null` 时读 `AiConfig.parseMode`；默认本地，未配 Key 强制本地，界面不给必然失败的选项。
- 规则命中时备注取**商户名**（无商户才截原文 40 字），不再把整条短信塞进备注。

**页面与入口**：

| 页面 | 路由 | 内容 |
|---|---|---|
| 自动记账 | `auto_bookkeeping` | **四组单页，一件事只出现一处**：状态与总闸（逻辑总闸 / 权限就绪度→权限检验）、触发器（支付通知 / 截图授权方式下拉 / 音量键开关＝绑定与解绑 / 立即识屏）、一键授权（一键加磁贴 / 一键开或关无障碍）、识别与落账（**悬浮窗确认开关置于结果样式上方：关＝样式自动切「通知」，选「通知」＝关开关** / 结果样式下拉 / 落账账本 / 去重窗口 / 倒计时 / AI 记账与截屏批量导入两个跳转）。2026-11-11 去重：删检查并开启权限按钮、3 步向导入口、识屏与授权跳转、独立绑定音量键行，并删除 `screen_capture` / `auto_guide` 两个冗余子页。**不摆记录、提示词、备注格式**，备注格式在「提示词与规则」页 |
| 智能识别 | `smart_capture` | 原自动记账页的**手动**识别区（一句话 / 拍照 / 相册 / 语音 → 结果卡快存或详细编辑 → 最近解析，带空态与重试）；快存遵循落账账本与备注模板 |
| 通知识别规则 | `notification_rules` | 无障碍与总闸状态 + 白/黑名单多行编辑（即时写盘，无保存按钮）+ 支持来源清单 |
| 记账偏好 | ~~`bookkeeping_prefs`~~ | **已删除**，内容上提到自动记账首页 |
| AI 记账 | `ai` | AI 状态卡（总开关 + 配置折叠）→ 记账区 → AI 功能开关 → 示例 |
| 提示词与规则 | `prompts` | 提示词 5 类 / 本地识别规则 / 规则数据（导出、导入、恢复内置）/ **备注格式**（`{商户}` `{来源}` 模板、实时预览、未知占位符校验，2026-11-10 从自动记账页迁入）；二级页 `prompt_edit/{kind}`、`local_rule_edit/{ruleId}` |

**四个 AI 开关都真实生效**：「文本识别用 AI」（**开 = 文本直接调 AI，关 = 纯本地；AI 失效（没 Key / 关总开关 / 请求失败 / 空结果）自动降级本地：通知模板 → 本地识别规则**）；「图片识别用 AI」（关 = 图片永不出设备）；「AI 识别分类」（关 = 落「其他」）；「账户识别用 AI」（关 = 只做本地名称匹配）。

**五类提示词**（`AiPromptKind`，每类都有真实调用点，留空 = 默认）：

| 种类 | 生效点 |
|---|---|
| 账单识别（文本）`bill_text` | 文本识别用 AI 打开且已配 Key（AI 优先）；失效时降级本地规则 |
| 小票识别（图片）`bill_image` | 本地 OCR 认不出金额 + 图片识别用 AI 开 |
| 分类识别 `category` | 模型给的分类不在用户分类表里时补一次 |
| 账户识别 `account` | 线索对不上已有账户名时补一次 |
| AI 摘要 `summary` | 统计页「AI 小结」（只发当期汇总，不发原始流水） |

**本地识别规则**：内置 13 条（餐饮/交通/购物/居住/娱乐/医疗/教育/人情/旅行等），可增删改、停用、导出导入、恢复内置（只补缺失项）。命中顺序：命中词数多者胜 → 最长命中词更长者胜 → **用户规则优先于内置** → 列表顺序。存储为 SharedPreferences + JSON，不动 DB。

**支付通知链路**：无障碍服务（只读通知）→ 包名白名单 → **关键词白/黑名单闸门**（黑名单优先；白名单为空 = 全放行）→ `PaymentNotificationParser.parse(pkg, text)` → 去重（窗口可配，默认 60s）→ 发「记一笔 / 忽略」通知 → `PaymentActionReceiver` → `PaymentBillStore` → 打开记一笔面板预填。三自律不可破坏：只读不模拟点击、先确认后落库、去重窗口。

防误判三闸：验证码/中奖等黑名单直接拒绝、金额 0 拒绝、超 100 万元拒绝；「余额/额度」前缀排除。

已支持来源（13 项，由 `PaymentNotificationParser.supportedSources` 生成，UI 只读不重复维护）：微信支付、支付宝（花呗 / 网商银行）、云闪付、招商银行、掌上生活、工商银行、农业银行、中信银行、美团月付、小米智能卡、数字人民币、广西北部湾银行、OPPO/一加钱包；其余含金额与收支语义的通知走通用兜底。

**刻意不做**（无数据模型或触发面，不造空开关）：手续费、债务、自动创建分类、资产映射、AI 月末总结、双击背部触发、延迟同步、第三方记账软件同步、悬浮窗位置/超时配置。

### 8.2 超级岛与实时通知

通知有两条路径：**小米超级岛** / **原生实时通知**（机型不支持时回落普通通知）。样式开关在「偏好设置 → 提醒 → 通知样式」；「超级岛」分组下另有预算、截屏结果、识屏过程、周期账单、展开光效 5 个开关。

**协议代数**（真机踩坑的核心）：

| 设备 | 模板 | 结构 |
|---|---|---|
| `islandLevel >= 3`（HyperOS 3） | `protocol: 3` + `islandParamsJsonV3` | Maling-Island 模板；`ticker` / `islandFirstFloat` / `isShowNotification` / 左图标右文字两栏；无 `templateNo`、无 `hintInfo` |
| 其余 MIUI / HyperOS 2 | `protocol: 1` + `islandParamsJson` | NexioSchedule 结构；模板 2、`baseInfo` + `hintInfo`（底栏 + 「查看」按钮走 `actionInfo.actionIntent`）、`islandTimeout: 3600` |

- `islandLevel()` 先读系统 `notification_focus_protocol`（1/2/3），读不到时只要是 MIUI 就按 3 发；**不要把 `persist.sys.feature.island` 当硬门槛**（小米 14 挖孔屏上可能为 false，但照样支持焦点通知）。
- **协议 1 在 OS3 上会「建出岛事件但不浮出」**（logcat 有 `UpdateDynamicIsland`，屏幕无内容）；必须按代数选模板。
- **bypass 终版结论**：协议 1/2/3 都走 `ShizukuBridge.withXmsfBypass`，**100ms 窗口 + 全局串行 + 恢复失败补恢复**。三组真机对照：100ms + `iconTextInfo` 稳定渲染；1.5s 或去 bypass 都会回落/被吞。协议 3 的模板 16 卡片区**不可**换成 `baseInfo`（协议 1 的模板 2 本来就用 `baseInfo`），否则整条参数不被识别。
- bypass 原理：借 Shizuku 把 XMSF 防火墙链 9 置为拒绝，让焦点通知的云端白名单校验落空。Android 16 起相关方法被移出反射列表，靠 `hiddenapibypass` 直调；方法名有多个变体，逐个尝试并重试 2 次（间隔 500ms）。焦点通知必须挂 HIGH 渠道，否则 HyperOS 不当作焦点载荷。
- 同一 id 重复发会被岛框架视为静默替换、不重新浮出；**测试发送用独立 id `5000`**。

**业务与 id**：

| 业务 | 岛开关 | 渠道 | 通知 id |
|---|---|---|---|
| 记账提醒 | 通知样式 = 岛 | `record_reminder_island` | 岛 9002 / 原生 9001（测试 5000） |
| 预算提醒 | `island_budget`（默认关） | `budget_alert_island` | 按告警实体生成 |
| 截屏结果 | `island_screenshot`（默认关） | `screenshot_import_island` | 9102 |
| 识屏过程态 | `island_screenshot_progress`（默认**开**） | 同截屏 | 9104，结束整条撤下 |
| 周期账单 | `island_recurring`（默认关） | `recurring_auto_island` | 9103 |

**原生实时通知**（Android 15+ / 16）：`POST_PROMOTED_NOTIFICATIONS` 权限 + `targetSdk 36` + platform `Notification.Builder` + `setOngoing(true)` + `autoCancel=false` + HIGH 渠道 `record_reminder_live` + API 36 `setShortCriticalText`。

- **原生路径禁止叠加 `miui.focus.param`**：HyperOS 3 看到焦点参数会按焦点通知接管（`focusType=PARAMS`），而协议 1 在 OS3 不渲染 → 既不上岛也不算实况。要岛请显式选「小米超级岛」。
- 小米机型上原生实况不会渲染成岛，属系统行为。

**验证方法**：`dumpsys notification --noredact | grep id=9001` 看 `focusType`（原生应为 `null`）；岛看 logcat `DynamicIslandEvent`；**前台不浮出属正常，必须退后台**。

**遗留**：协议 1 的 `iconTextInfo` 不带 `animIconInfo` 时文字有轻微回退缩进；协议 3 没有「查看」按钮（点岛走 `contentIntent`）。

### 8.3 分类图标与账户分类

- 素材：`app/src/main/assets/icons/` — 分类 453 个白色线条 PNG、账户 145 个彩色 logo PNG、占位图 2 个。**文件名 = 中文分类名/账户名**，APK 内是正确的 UTF-8；Windows 工具显示乱码属显示问题，**不要改名**。
- 加载器 `ui/components/IconLoader.kt`：`CategoryIconImage`（彩色圆底 + 白色图标）、`AccountIconImage`（彩色 logo，失败回退自绘徽章）。加载顺序：**用户覆盖 → 原名 → 别名 → 占位图**。别名表 `CATEGORY_ALIAS`（正餐→午餐、食材→餐饮、居住→住房居家、宽带→网络、游玩→门票、教育→教育学习、借出→应收、报销→报销款等）。
- 全局同步：`CategoryIconStore` 按**分类 id** 存覆盖（`override_id_<id>`），读取先查 id、再回落旧名字 key 并懒迁移；记一笔面板、账单行、分类管理都走同一个组件，改一处全局生效。
- 账户分类：`data/model/AccountType.kt`（ALL/CASH/BANK/ONLINE/RECHARGE/INVEST/CREDIT/OTHER + 关键词 + 优先级）；`AccountEntity.category` 由 `MIGRATION_6_7` 引入，旧数据按「英文 iconKey → 中文名 → 账户名」逐级推断。
- SVG 图标：`SvgIconStore` 只保留存储能力（复制到 `filesDir/svg_icons/` + 名称映射），**当前没有 UI 入口、渲染也未接入**，属休眠代码；要启用需同时补上传入口与 Coil-svg / AndroidSVG 渲染，不要只挂一个点了没反应的按钮。
- 可点击控件点击区统一 `Modifier.minTouchTarget()`（`sizeIn` 兜到 48dp，视觉尺寸不变）；不要用 `size` 锁死 min/max。

### 8.4 识屏悬浮窗「详细版」

- `ui/overlay/DetailedOverlayCard.kt` 以 Figma 927×607 稿为基准，`scale = 实际宽 / 927` 等比换算尺寸、间距、字号；窗口 `Gravity.TOP`，位于状态栏下 8dp。
- 三态：支出 / 收入 / 转账；金额就地编辑；分类、账户、日期、标签行内选择；「不计入预算」「计入资产」；取消 / 保存。多笔时右上角显示 `1/N`，保存逐笔推进。
- 账户与分类用真实素材图标（`AccountIconImage` / `CategoryIconImage`），展开列表按一级 + 二级展示；展开态用固定 dp（不随 927 稿缩放，否则行高 19dp 点不准）。
- 时间只选到天（详细版未接 `minuteOfDay`），紧凑月历可选任意日期；需要时分时改走 `QuickBillWriter.addDetailed` 的 `minuteOfDay`，不要只改 UI。
- 写入走 `QuickBillWriter.addDetailed`；字体 Actor/Inter、图标 `check/alarm/comment/fast_forward` 已落在 `res/font`、`res/drawable`。

### 8.5 去桩与底层补齐（已完成，保留结论）

原则：**能真做就真做，做不了就不装**；配置必须有出口；不写死「成功 / 已是最新」。

| 项 | 结果 |
|---|---|
| A1 自动记账去桩 | 删掉假弹窗，四个入口各跳真实页面（AI 记账 / 截屏导入 / 账单导入 / 无障碍状态） |
| A2 无障碍截屏 | `canTakeScreenshot` + `takeScreenshot` → OCR；必须「退后台 + 3 秒倒计时 + 通知带回」，否则截到的是 iji 自己；硬件缓冲立即 `close()`，6s 超时 |
| A3 WebDAV | 真实 PROPFIND/PUT/GET，先本机加密再上传；删除接不了的 Dropbox/OneDrive；配置可清除 |
| A4 检查更新 | 2026-11-10 换源：内置 Worker 接口（`code/version/changelog/downloadUrl/fileName`），版本严格 x.x.x 逐段比较；A 启动即检查（每天一次、可关）+ B 关于页手动检查共用 `UpdateFlow` 单例与唯一 `UpdateDialog`；点「立即下载」才下载。旧 GitHub 私有仓库源与构建期令牌已移除；口径演进：留空=不联网 → 打开弹窗即检查 → 启动即检查 |
| A5 导入历史 | DB v19 建表，记录来源/时间/成功失败/目标账本/文件名，可清空 |
| A6 字体缩放 | `LocalDensity(fontScale = sp/16)` 真正生效并落盘（原来只写不读） |
| A7 导入条数 | 补插值，不再写死「成功  条」 |
| A8 文件格式 | xlsx 在选择器置灰，按文件头识别 zip/OLE2 并提示导出 CSV；UTF-8 失败时 GBK 兜底 |
| D1 换机丢数据 | 导出统一走 `BackupManager.buildSnapshot`（config + transactions），删掉只导流水的第二实现 |
| D2 模拟数据 | 删除空库播种，首启只有默认账本 + 2 个默认账户 |
| D3 应用锁 | PIN 4 位校验；连错 5 次冷却 30s，计数与截止时刻写加密偏好；冷启动才锁 |
| D4 触控与无障碍 | 12 处图标点击区兜到 48dp；装饰性图标 `contentDescription = null` 是刻意（同行文字已承载语义） |
| D5 反馈 | ~60 处 Toast 改 Snackbar（指纹解锁 2 处保留，因 LockScreen 上 SnackbarHost 尚未渲染）；状态不只靠颜色 |
| 不做 | PullToRefresh（本地库无远端，转了也没新数据）、统计再分组（待信息架构决策） |

### 8.6 自动记账重做（2026-11-10）

参考 `AutoAccountingOrg/AutoAccounting`（GPL-3.0）与 `badnng/Hyper-pick-up-code`（AGPL-3.0），**只借鉴交互结构与系统 API 用法，未复制源码**（本项目闭源）。

| 项 | 结果 |
|---|---|
| 信息架构 | 旧页 624 行塞「手动记账 + 最近解析 + 三个只读跳转」→ 推翻为分组单页（状态与总闸 / 触发器 / 一键授权 / 识别与落账）；手动区迁到 `smart_capture`；`BookkeepingPrefsScreen` 删除；记录与提示词跳转按用户要求移除；2026-11-11 再按「一件事只出现在一处」删掉重复入口、合并音量键开关、删除 `ScreenCaptureSettingsScreen` 与 `AutoGuideScreen` 两个冗余子页 |
| 选择器口径 | 新增 `IjiSettingsDropdownRow`：≤5 项走 `LiquidGlassDropdownMenu`（须挂 `LiquidGlassDropdownOverlayHost` popup 层），>5 自动降级 `WheelOptionDialog`；布尔量 Switch、自由文本就地编辑。替换旧识屏页 chips，并把所有选择项集中到自动记账首页（一件事只出现在一个分组） |
| 逻辑总闸 | `AutoBookkeepingPrefs.masterEnabled`，`PaymentNotifyAccessibilityService` 在通知分支读取；系统无障碍授权本身不改 |
| 悬浮窗确认 | `CapturePreferences.overlayConfirmEnabled` 与系统权限解耦，**能开就能关**；关掉后识屏结果回落草稿 + 通知，不入账。2026-11-11 起与 `overlayStyle` **双向联动**：开关移到「识别结果样式」上方，关＝样式写为 `OVERLAY_ISLAND`（通知），开回来时若仍是通知则回 `OVERLAY_COMPACT`；下拉选「通知」＝关开关，选卡片样式＝开开关。两处不再出现互相矛盾的状态 |
| 倒计时 | `screen_capture_delay_seconds`（3/5 秒下拉），`captureDelayMs()` 钳在 1..10 秒；磁贴与首页都读它 |
| 一键授权（需 Shizuku） | `AccessibilityShortcutHelper`：一键加磁贴走 `StatusBarManager.requestAddTileService`（处理结果码）；音量键写 5 个 `Settings.Secure` 键并**先备份、可一键恢复**；开启无障碍只**追加**本服务组件、可撤销。Shizuku 不可用一律回落系统设置 |
| 磁贴分流 | `ScreenCaptureTileService.onClick()` 按 `CapturePreferences.resolve()` 走；Shizuku 直接收下拉栏截图，仅无障碍时回应用内「立即识屏」，避免截到下拉面板 |
| 快存口径 | `SmartCaptureScreen.quickSave` 与识屏/通知链路共用 `effectiveLedger` + `buildNote` |
| 触发入口 | 桌面长按快捷方式（`res/xml/shortcuts.xml`）、系统分享图片（`ACTION_SEND image/*` → `SharedReceiptHolder` → 自动 OCR，仍停在确认卡）。自动记账的 3 步向导子页已删；开局引导是独立的 5 步配置页（§9.2.1）；「引导教程」是静态说明文档，页底提供真实的「重新配置引导」入口，不是自动记账教程 |
| 规则页 | 白/黑名单即时写盘，去掉「保存」按钮；如实提示总闸已关 |
| 验证 | `compileDebugKotlin` / `assembleDebug` 通过；249 单测（新增 `AccessibilityShortcutHelperTest` 8 例）全绿；**一键授权的真机验证待设备接入**（用户 2026-11-10 说明真机未连接） |

### 8.7 加密备份格式、字节导入与网盘保留（2026-11-12）

| 项 | 口径 |
|---|---|
| 文件格式 | `[1B 版本][12B IV][AES-GCM 密文 + 16B Tag]`。密钥在 Android Keystore（`iji_backup_key`，不可导出）→ **备份只能原机还原**，换机请走配置备份 |
| 版本 | **2 = 先 gzip 再加密**（`BackupPayload`，当前写入）；**1 = 旧包**（明文 JSON，只读兼容）。改格式只加版本号，不要重解释旧版本；`unpack` 对版本 1 必须原样返回 |
| 唯一入口 | `BackupManager.decodeSnapshot(bytes)`：首字节是已知版本才解密，否则按 UTF-8 当明文读。解密失败返回 `null`，**不要退化成按明文读**——那会把密文解成乱码、报「无法识别」，把真因（换机 / 密钥被清）藏起来 |
| 线程 | 快照构建 + gzip + AES + 落盘全在 `Dispatchers.IO`（全量可达数 MB，别搬回主线程）；`readSnapshot` 是 `suspend` |
| 文件导入 | SAF 选择器必须 `readBytes()`，**不能用 Reader**（二进制会被糟蹋）；同一份 `decodeSnapshot` 也服务本机备份列表，两处不再各写一套 |
| 网盘保留 | 上传成功后按 `Content.prefix`（`iji_backup_` / `iji_config_`）分组各留最新 `KEEP_COUNT = 7` 份；删旧失败只记日志，不影响上传结果。排序按**文件名**倒序（名字含毫秒时间戳），别用 `getlastmodified`（HTTP 日期串字典序先比星期几） |
| 临时图 | `TempImages.delete/sweep`：拍照（`cache/camera`）与系统分享（`cache/shared_receipt_*`）在 decode 进内存后立刻删；启动 / 进页扫掉 1 小时前的遗留（不动当前流程刚写的文件） |

**验证（2026-11-12）**
- 真机 23127PN0CC / Android 16：本机备份首字节 = 2（2196 B）；「本机加密备份 → 恢复」预览出「3 笔流水 + 配置」；网盘连传 8 次后列表只剩 7 份、最旧一份被删。
- 模拟器 Android 17（debug 包，可 `run-as`）：`iji_backup_*.ijbak` 版本字节 = 2、598 笔流水可解出预览；下载目录里的 `.ijbak` 经「从文件导入」——同机包正常预览，异机包提示「解密失败：这份备份不是本机密钥加密的…」。植入 2025 年时间戳的 `cache/shared_receipt_old.jpg` 与 `cache/camera/shot_old.jpg`：重启应用清掉前者，进「智能识别」清掉后者。
- 单测：`BackupPayloadTest`（压缩往返 / gzip 魔数 / 版本 1 透传 / 未知版本拒绝 / 空载荷）、`BackupImportBytesTest`（明文原样、BOM 不误判、版本 1+2 都认、残缺文件不算密文）。

**改动文件**：新增 `backup/BackupPayload.kt`、`ai/TempImages.kt`；改 `backup/BackupCipher.kt`、`BackupManager.kt`、`CloudBackupManager.kt`、`WebDavClient.kt`、`MainActivity.kt`、`ui/mine/BackupSettingsScreen.kt`、`CloudBackupScreen.kt`、`SmartCaptureScreen.kt`、`ScreenshotImportScreen.kt`；测试新增 `BackupPayloadTest.kt`、`BackupImportBytesTest.kt`（另修了 `AiSummaryPayloadTest` 一个自相矛盾的旧用例：`prev=0` 却断言存在 `expenseChangePct`）。

## 9. 待办与已知限制

### 9.1 真机走查（模拟器已过）

1. 深色 + 大字号组合：深色切换、状态栏图标明暗已在模拟器验证；**大字号**仍待真机走查。
2. 分类管理的**手指拖动排序**（adb 无法模拟「长按后拖」，只能用行尾 ↑↓ 验证写盘）。
3. ~~网盘上传/恢复的真实账号联调~~ → **2026-11-12 已用真机坚果云账号完成**：上传、按 7 份清理、恢复预览均实测（§8.7）。
4. **本轮一键授权（§8.6）真机专项**：设备未连接，全部挂起。需验：Shizuku 写 `Settings.Secure` 在 HyperOS 上是否生效、音量键绑定→解除是否完整恢复、`requestAddTileService` 返回结果码与磁贴点击分流、无障碍一键开启/撤销后系统开关与服务连接状态、下拉菜单在深色 + 大字号下不溢出且无自捕获 SIGSEGV、分享图片冷启动链路。
5. **分享图片 → 临时图立刻删**：adb 侧 `ACTION_SEND` 拿不到 MediaStore URI 授权（应用未声明 `READ_MEDIA_IMAGES`，只靠调用方 grant），分享根本没被接收。需手动从相册/微信分享一次，再用 debug 包 `run-as com.iji.app ls cache` 确认 `shared_receipt_*` 已删。
6. **旧格式（版本 1）`.ijbak` 真机还原**：需要一份升级前写下的备份；密钥不可导出、外部造不出来。目前兼容分支只有单测覆盖。

### 9.2 C 类空白领域（未开工）

云同步/多设备（WebDAV 已有，S3 无 SDK）、Excel/PDF 导出（无依赖）、桌面小部件（无 Glance）、多语言（只有 `values/`）、自定义主题色、共享账本/AA 分账。

已完成、不再属于 C 类：**隐私政策同意门禁**、**高级筛选**（范围 / 类型 / 金额区间 / 日期区间 / 分类）、CSV 导出、多币种。

#### 9.2.1 开局引导配置页（2026-11-11）

- 流程：首次同意隐私 → 5 步引导（个性化 / 记账口径 / AI 服务 / 权限 / 提醒）→ 完成或跳过 → 主页。「引导教程」页底部「重新配置引导」→ 同一向导的重配模式 → 完成覆盖现有配置并回主页、取消回教程页。全程不重启 App、不触发应用锁、不配置 PIN。
- 返回逻辑：系统返回键只回到进入引导前的上一个页面（重配 → 引导教程页；首启的上一页是已同意的隐私政策页，故只消费返回事件，退出用右上角「跳过」），不在向导内部倒退；底部「上一步」从第 2 步起可用（第一页没有）。第 4 步的权限项直接调系统开关、不离开向导，`step` 与草稿都走 `rememberSaveable`（含 `OnboardingDraftSaver`），从设置页返回或进程重建都停在原步骤。
- 标记 `onboarding_state` 三态：0 存量用户 / 未同意隐私（不打扰）、1 待引导（只在同意隐私时写入，同意后被杀进程仍能兜住）、2 完成或跳过。政策版本升级后重新同意隐私不会重复弹引导。
- 首启用 NavHost 的 `startDestination` 条件化（隐私门禁时 NavController 尚不存在），非 tab 路由自动隐藏底栏；`ImmersiveRoutes` 已登记两个引导路由，页面自吃 `statusBarsPadding()`。通知冷启动 pendingRoute 在引导放行后才消费。
- 视觉：固定纯白底 / 黑字 / 主色蓝，不跟随主题；顶栏只有 5 段短横条推进、零文字。主题与字号只做页内预览，点「完成」才写 `ThemeState` / `FontScaleState`。第 1 步主题是一行三张卡片（缩略图与文字同卡），字号为 12–24sp 滑块（1sp 一档）；AI Key 始终掩码，右侧「✕」清除。
- `OnboardingApplier.apply` 一次性提交草稿：主题、字号、昵称头像、记账口径、币种（Room 写完再放行 + `CurrencyDisplay`）、AI 全套、提醒排期。权限不进草稿，向导第 4 步复用 `PermissionCheckScreen.detectPermissions` 显示就绪度，点某项直接走共享的 `runPermissionAction`（与权限页同一份跳转 / 回落链），不再跳权限检验页；权限页本身保留首次说明卡与「查看说明」入口。
- 验证：`compileDebugKotlin` / `compileDebugUnitTestKotlin` 通过，lint 0；**真机走查待设备接入**（新装完成 / 跳过 / 同意后被杀重进 / 重配覆盖 / 深色模式下白底与状态栏图标）。

### 9.3 技术债

- `exportSchema = true` 但未配 `room.schemaLocation`：KSP 每次编译打 warning，schema 实际没导出。
- `CategoryManageScreen` 的支出 / 收入切换已由 Material3 `TabRow` 换成共用 `IjiSegmentedRow`（2026-11-09），deprecated 警告随之消除。
- release 构建：`ShizukuShellService` 的 `Instantiatable` lint 误报已用 `tools:ignore` 修掉（2026-11-04，`assembleRelease` 通过）。
- `app/build.gradle.kts` 对 AIDL 生成物 >5 MB 直接删除只是防御（曾写出 1.08 GB），根因未查；本轮 release 已能走完 AIDL 阶段。
- 历史遗留但已可接受：`AiSettingsScreen` 已删除（配置内嵌 AI 记账页）；`ruleBasedParse` 已由 `LocalRuleEngine` 取代。

### 9.4 已知限制

- 多币种是「账本级币种 + 仅展示」，**不做汇率换算**，跨币种金额不相加。
- 回退安装低版本 APK 触发破坏性重建（数据清空）；升级路径不受影响。
- 统计的时间范围由维度切换提供，不支持任意起止日期。
- 单测只能直跑 JUnitCore（`testDebugUnitTest` 本机必失败）。

### 9.5 版本管理

仓库已初始化：`master` 跟踪 `origin/master`，已有 2 次提交（2026-11-03，初始工程 + 文档/我的页原型）。
但**本轮 UI 重构、共享 VM、设置预热与 5 个新文件仍未提交**；另有 `HANDOFF.md` / `README.md` 的文档整合改动。
提交前先 `git status`，确认 `IjiSegmentedRow.kt`、`IjiOptionCardPopup.kt`、`LiquidTopBarButton.kt`、`LocalAppBackdrop.kt`、`SharedMainViewModel.kt`、`tools/icon_source.png` 这些 untracked 文件已 `git add`。
2026-11-12 新增的 untracked：`backup/BackupPayload.kt`、`ai/TempImages.kt`、`test/backup/BackupPayloadTest.kt`、`test/backup/BackupImportBytesTest.kt`。

### 9.6 页面切换转场与首帧卡顿（2026-11 排查 + 11-04 收口）

**转场（已落地，只动了 `ui/nav/NavMotion.kt` 与 `AppRoot.kt` 注释）**
- 转场改**两页锁步**：同一次转场的入场/出场页共用同一条曲线（FastOutSlowIn）与同时长。旧版入场减速、出场加速，转场中段两页位移差最大可达半屏、中间露出底色带——这是「切页割裂感」的根因。
- tab 转场 250→300ms：与底栏玻璃胶囊弹簧（stiffness=300，约 300ms 落定）同节奏，页面与胶囊同起同落。

**首帧减负（2026-11-04 已收口）**
- **MainViewModel 全 App 一份**：新增 `ui/util/SharedMainViewModel.kt`，所有页面/对话框默认 `sharedMainViewModel()`（以 Activity 为 ViewModelStoreOwner）。四个 tab 不再各建一份、不再重复播种与订阅 Room。
- **组合期禁阻塞 IO**：`SettingsRepository.warmUp` 改为在 `IjiApplication.onCreate` 同步执行一次，把整份 DataStore（含 SharedPreferences 迁移）读进内存并置 `warmed`；之后 `getXxx` 只查缓存，未落盘的键直接返回默认值。此前 PerfState / HapticsState / FontScaleState 在 onCreate 里已经各 `runBlocking` 读一次，现在合并为一次。
- 首页日期折叠、记一笔的按账本记忆、通知规则、首次引导等组合期读取因此都不再碰磁盘。

**剩余**
1. 真机复测首帧（本次只做了模拟器功能走查，未重新采集 gfxinfo；目标 <100ms）。
2. 图标加载：`SvgIconStore` 仍是休眠代码（无 UI 入口、无渲染），不是「重构进行中」；要启用需同时补上传入口与 Coil-svg / AndroidSVG 渲染。
3. 富内容首绘：首页预算卡、统计趋势图仍是首帧成本大头；若要进一步压，先做数据/布局分层（本次未动）。

**排查证据（模拟器 Pixel_9_Pro_2 / API 37，帧级录屏 + gfxinfo + 转场日志）**
- 转场链路本身无问题：把目的地临时换成空页后，滑动逐帧完整（12 帧连续动画）。
- 真实 tab 页首帧组合成本 150~250ms（gfxinfo：切换瞬间 Slow UI thread 帧 100~200ms+），首帧落下时动画时间已被吃光 → 表现为「点 tab 页面瞬移/卡顿」。
- 已排除：系统动画缩放（=1.0）、`isLowRamDevice` 降级分支（=false）、NavHost 的 `initialState in visibleEntries` 跳过分支（转场 lambda 均被调用）、`layerBackdrop` recordKey 重录（关掉后仍瞬移）。

**2026-11-04 顺手修的两个 UI 回归**
- `IjiOptionCardPopup` 补 `width(200dp)`：`OverlayListPopup` 的 `minWidth` 只保证下限，条目 `fillMaxWidth` 会把面板撑成通栏白条。
- 状态栏/导航栏图标明暗改用 `isAppDarkTheme()`（跟随 `ThemeState`）：系统浅色 + App 手动深色时，原来只看 `isSystemInDarkTheme()` 会让图标留在深色底上不可见。

### 9.7 弹窗浮层卡片 NexioSchedule 化（2026-11-05 · 动画/玻璃线）

参考 HaoZai000/NexioSchedule 的 LiquidGlassDropdownMenu，把同款「玻璃 + 回弹 + 按压选中」落到 **GlassDialog**（全 App 中央浮层卡片）。只动了 `ui/components/dialog/GlassDialog.kt`（9 行）与 `GlassDialogContent.kt`，**没有碰** overlay 超级岛、IjiOptionCardPopup、AddTransactionSheet（各有归属）。

- **玻璃**：`LocalAppBackdrop` 采样 + `vibrancy + blur(16dp) + lens(6,36)` + NexioSchedule 面板档染色（浅 0xFFFAFAFA@0.76 / 深 0xFF242424@0.84）+ 环形模糊投影（ShadowBlur=12/Spread=2/AlphaGain=3.2，**与 IjiOptionCardPopup 同值副本，调参两处同步**）+ `PerfState.reducedEffects` / 无采样层时回退不透明卡片。按压高光（InteractiveHighlight.pressOnly，不消费事件，弹窗内按钮/输入框不受影响）夹在玻璃壳与内容之间。
- **回弹**：入场 spring(0.88,450)→(0.82,300)（滑到顶越过 ~15dp 再弹回落定）；大屏缩放入场 dampedSpring(0.9,0.3)→(0.78,0.4)；预测性返回取消由线性 tween 改欠阻尼 spring(0.6,250) 弹回复位。
- **实测**（模拟器，帧级录屏）：卡片顶边轨迹 1095→924→938px，过冲 ≈15.5dp 与设计值吻合；玻璃生效（卡片内灰度 201 与遮罩区同值，降级路径应为 ~247 纯白）；文字/按钮对比度正常。

### 9.9 下拉菜单卡片 NexioSchedule 1:1 复刻重写（2026-11-07 · 动画/玻璃线）

用户指令：旧下拉（AnchoredDropdownMenu/AnchoredIconDropdownMenu 的 miuix 官方白卡弹窗）全部删除，按 NexioSchedule 的 LiquidGlassDropdownMenu **1:1 拷贝代码**重写。

**1:1 拷贝的文件链**（`ui/components/menu/`，包名与少数适配点见各文件头注）：
- `LiquidGlassDropdownMenu.kt`（主体：42dp 玻璃圆钮 ↔ 200dp 面板容器变换、展开 spring(0.77,220)+锚点迁移 spring(0.77,420)、收起 spring(0.85,650)+退出位移回弹脉冲 spring(0.52,220)、内容缩放/alpha 窗口/中途模糊、环形阴影、拖动形变、拖选、预测返回跟手收回）
- `DropdownPanelDragSelect.kt`（拖选全套：条目登记/逐帧命中/命中变更高震/抬起提交/computeDragTransform 按压形变）
- `InteractiveHighlight.kt` + `DragGestureInspector.kt`（fork 增强版：radiusScale/radiusBaseDp/fixedRadius/dragOffset 回弹）
- `CanvasShadow.kt`（drawBlurredPathShadow，expect/actual 合并为 Android 单文件）
- `AppMaterialSettings.kt`（材质三档 API 桩，映射本工程 PerfState.reducedEffects）
- `NavigationBack.kt`（NavigationBackState 1:1 类定义；Android 实现改走 PredictiveBackHandler——本工程根 dispatcher 孤立问题，见 9.6/9.8 相关结论）
- `LiquidGlassDropdownOverlayHost.kt`（本工程新增的接线层，非上游文件）

**关键事故与机制（重要）**：面板初版放在页面布局里（NavHost 子树）→ drawBackdrop 采样 LocalAppBackdrop（layerBackdrop 录制层）时**把自己录进采样图**（自捕获）→ RenderNode 树自引用 → prepareTree 无限递归 → **RenderThread SIGSEGV 启动即崩**（与 blur/lens/edgeLight 无关，全部禁掉仍崩）。修复：面板必须挂 **Miuix DialogLayout 宿主（popup 层，录制树外）**——与 GlassDialog/IjiOptionCardPopup 同管线。上游无事是因为其控件在 chrome/overlay 层。

**接入点（3 处，上游 chrome 层模式：占位槽只定位，面板在 popup 层长出）**：
- HomeScreen 账本圆钮（panelAlignStart=true，左上角锚定；本工程相对上游的增量参数）
- AssetsScreen 账户行点状菜单（LazyColumn 裁剪 item → 状态与 anchor 上提到页面根，overlay 在根 Box）
- AccountDetailScreen 顶栏点状菜单
- 外点关闭由 OverlayHost 的 onOutsideTap 补齐（上游由 chrome 层处理）
- LiquidGlassDropdownMenuItem 增加可选 `selected` 参数（上游无选中显示；默认 false=上游行为）

**已验证**（模拟器，截图）：容器变换展开/玻璃透出/环形阴影/选中蓝+✓/拖选切账本（按住-滑动-抬起-提交）/外点关闭/无崩溃。收起动画回弹与预测返回跟手未逐帧验证（手测路径正确）。

**外点关闭的第一版实现有严重副作用（已修）**：外点手势层无条件消费所有 down → 菜单收起态也挡全屏触摸（页面点不了任何东西）。修复：改为 scrim 方案——`LiquidGlassDropdownOverlayHost(expanded=…)` 仅展开时在面板下方垫全屏手势层，收起态不挂。
**DialogLayout popup 宿主常驻会拦截全屏触摸**（二分实锤：show=false 后页面恢复可点）→ 宿主只在展开期间挂载，收起后 700ms 卸载（留收起弹簧+回弹脉冲的播放窗）；收起态玻璃圆钮由占位槽承担（HomeScreen 回归 LiquidTopBarButton、资产/详情为三点图标——页面内既有玻璃组件无自捕获先例）。
**验证状态**：HomeScreen 账本菜单全链路 ✓ + 收起态 tab 可点 ✓（最终代码）；AssetsScreen/AccountDetailScreen 两处接线相同（同控件同宿主）但**未真机验证**——验证期间模拟器被并行会话重装占用（当前前台为并行会话构建），接手方请优先补这两处走查。

### 9.8 底栏胶囊飞行卡顿修复（2026-11-05 · 动画/玻璃线）

症状：切 tab 胶囊比图标变色慢；跨多格时长距离飞行「每经过一个图标顿一下」。

- **根因**：平推窗口（450ms）内 `mustRecordBackdrop` 逐帧触发全屏 GPU 层录制（1280×2856 ≈ 14.6MB/帧），叠加底栏玻璃 vibrancy+blur+lens 重采样与胶囊 lens 折射，模拟器单帧 25~35ms → 飞行 ~30fps 且间隔不均。tab 等距分布，周期性掉帧正好落在胶囊跨过的每个图标上。
- **修复**（`AppRoot.kt` + `ui/effects/glass/DampedDragAnimation.kt`）：
  1. 平推窗口**隔帧录制**（玻璃 30fps 刷新，肉眼无感），窗口结束/被截断后的第一帧补录落定画面（`backdropWasSliding` 收尾分支），静息态不受影响；
  2. 胶囊飞行弹簧 spring(0.95,450)→**spring(0.9,600)**：~180ms 落定，紧跟图标当帧变色（胶囊是光标，先于页面到位；无可见过冲）；
  3. 速度跟踪 `updateVelocity` 由逐帧 `animateTo`（每帧取消重启一条弹簧）改 `snapTo`，落定后速度 snapTo 归零，删除无用的 velocityAnimationSpec。
- **验证受限**：本轮模拟器 screenrecord 系统级损坏（UNASSIGNED_LAYER_STACK，0 字节输出；共享模拟器上另有 NexioSchedule 被并行对话前台测试），gfxinfo/SF-latency 在 BLAST 下也无数据——效果未做帧级复测，待模拟器重启后或真机复测（预期：飞行帧间隔均匀化、胶囊 ~180ms 到位）。改动为保守参数调整，无行为分支变化。

**2026-11-06 追加：玻璃背景不跟随移动的真正根因（重要机制结论）**
- 症状「页面平推时底栏玻璃冻结在起始画面、页面落定后才更新一次」的根因**不是**录制节流，而是：**`invalidateDraw()` 在 draw 过程中调用不产生下一帧重绘**（模拟器实测：两次调用后 draw 链彻底停止，第二次请求后 0 次重放）。平推只改子页 graphicsLayer 位移、不会自行 invalidate 父节点 draw，因此依赖 invalidateDraw 续帧的逐帧录制（包括录屏背板最初的 `if (force) invalidateDraw()` 设计）从未真正生效过——玻璃一直只有「起点录一次 + 落定录一次」。
- **修复（`AppRoot.kt`，采样层库已还原为团队版本）**：新增 `backdropFrameTick` snapshot state，平推窗口内用 `withFrameNanos` 循环逐帧写入（≤40 帧兜底）；`mustRecordBackdrop` 在 draw 作用域里读它——draw 阶段读 snapshot state = 订阅该 state，每帧写入即重放 draw（Compose 动画的标准失效管线，可靠）。实测：窗口内 40 帧 draw、20 次录制精确落在偶数帧（30fps），落定帧补录，链路无断点。
- 经验教训：想强制 Compose 节点逐帧重绘，用「draw 阶段读每帧变化的 state」驱动，不要用 invalidateDraw 自续帧。

## 10. 交接备注

- 验证产物（截图、临时 db、导出 csv）用完即删，别留在 `.codebuddy/` 或 `Download/`。
- **签名材料离线另存**：根目录 `release.jks` + `keystore.properties` 是本项目唯一的发布身份，不进版本库、丢了无法再发升级包。换机器或重装前先备份这两个文件；交接时单独移交，不要走 git。**截至 2026-11-12 晚仍只有 Y 盘一份**，离线备份未完成。
- **签名指纹（2026-11-12 核实，比对用，别再重新推导）**：
  - release（别名 `iji-release`，密钥 2026-11-12 20:58 生成——此前从未有过 release 密钥，故真机上任何 release 签名包必然出自它）：SHA-256 `6F:29:35:45:E3:BE:DD:62:44:C5:51:C2:89:F6:2F:55:25:07:F7:D9:D1:8E:8A:65:38:FA:16:75:94:D1:C6:DF`，与 `app\build\outputs\apk\release\` 两个 APK 实测一致。
  - debug（本机 `~/.android/debug.keystore`，2026-08-03 生成）：SHA-256 `56:45:8F:72:5D:3D:4F:41:8D:D3:FB:90:F4:16:BE:60:E4:2E:35:30:C9:A7:6C:83:85:28:BE:C2:82:C5:FA:EE`，模拟器当前 debug 包即此签名。
  - 真机重连后核对：`adb shell dumpsys package com.haloyu.iji | grep signatures`（包名 2026-11-13 已换，见下）。等于 release 指纹 → 直接覆盖安装；等于 debug 指纹或其它 → 是旧包，覆盖会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，须先保全数据再卸载重装。
- **2026-11-13 换应用身份**：`applicationId` 改为 `com.haloyu.iji`（`app/build.gradle.kts`），`shortcuts.xml` 两处 `targetPackage` 同步；**namespace/代码包保持 `com.iji.app` 不动**（283 个文件的 package/import 无需迁移）。manifest 的 FileProvider/Shizuku authority 都是 `${applicationId}` 占位符，自动跟随；代码里的自定义 action 字符串（`com.iji.app.action.*`）是应用内部标识，与身份无关，刻意未动。验证：`assembleDebug` 通过（aapt2 badging = `com.haloyu.iji`）、256 单测全绿。**换身份 = 全新应用**：老包 `com.iji.app` 不会被它升级、数据互不相通，要带数据走导出/导入；adb/dumpsys/run-as 一律用新包名（MainActivity 的类路径仍是 `com.iji.app.MainActivity`，`am start -n com.haloyu.iji/com.iji.app.MainActivity`）。
  - `Downloads\iji_device_backup_20261112\appdata.tar` 是 11-12 20:58 拉的**模拟器**数据保全（598 笔流水，与模拟器现状逐一相符），纯属冗余，确认无误后可删。
- 保留 `tools/run_unit_tests.ps1`（单测入口）与 `tools/generate_icons.py` + `tools/icon_source.png`（可重生成各密度启动图标）；生成用底图不放 `res/drawable/`。
- 交付测试包：先 `adb push` 到设备 `Download/`，再让测试者安装。
- 本轮文档整合：`README.md` 保留为仓库落地页（只留简介与构建入口）；`HANDOFF-AI记账与自动记账重构.md`、`HANDOFF-超级岛与实时通知.md`、`HANDOFF-分类图标与账户功能.md`、`A类_补底层.md`、`D类_数据与健壮性.md`、`AI记账与自动记账重构进度.md` 的有效内容已并入本文并**已删除**。
- 后续若新增专题，请直接在本文件内扩写对应小节，不要再拆出平行交接文档，避免再次出现版本冲突（如版本号、DB 版本、测试计数各写一套）。
- **2026-11-12 交接状态**：真机装的是 **release 签名包**（`app-arm64-v8a-release.apk`）。debug 包与原安装包签名不兼容（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），而 release 包可覆盖且数据未丢；给这台机器发包继续走 release。
- **测试残留**：真机坚果云 `dav/iji/` 目录现有 7 份 2KB 测试备份（原本是空目录，可在网盘页手动删）；真机 `Download/iji_test_import.ijbak` 可能还在（清理命令执行时 adb 已掉线）。模拟器的假 WebDAV 配置已清空，测试图、`cache` 临时图已删。
- **设备状态**：真机 `d385580c` 本轮中途 adb 掉线（`adb devices` 只剩 `emulator-5554`），下次接手先重连真机再走 §9.1。
