# iji-android 交接文档

> **最后更新：2026-11-03**（识屏悬浮窗「详细版」按 Figma 重构：顶端固定、927 宽基准等比还原、三态与逐笔确认）
> 工程根：`Y:\iji-android`　包名：`com.iji.app`　**改动全部在工作区、未提交 git**（本机 shell 里没有 `git` 命令）
>
> 这份文件给接手的工具/人读：**第 2 节能跑起来，第 7 节别踩坑，第 8 节是可以接着做的活**。
> 旧版（2026-10-02，折叠顶栏那轮）的内容已合并进来，不再单独保留。

---

## 1. 现状一句话

一个功能已经相当完整的个人记账 App：四 tab（账本/资产/日历/统计）+ 记一笔悬浮面板 + 「我的」设置中心（6 组入口、27 个文件），
本地 Room 持久化、AI/截图/CSV 导入、CSV 导出、WebDAV 网盘备份、应用锁、预算/周期账单/模板/多账本/多币种/分类自由编辑都已落地。
**能编译、能装、能跑；单测 167 项（16 个测试类）全绿；最近两轮（多币种、分类管理）已在模拟器 + 真机走查通过。**

---

## 2. 快速上手

### 2.1 构建
```powershell
cd Y:\iji-android
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'   # 系统 PATH 里没有 java，必须显式设置
.\gradlew :app:compileDebugKotlin --console=plain   # 快速语法检查
.\gradlew :app:assembleDebug --console=plain        # 产物 app\build\outputs\apk\debug\app-debug.apk
```
> JDK 24 + Gradle 9.6 会打 `native-access` 警告，不影响编译。
> **构建成功 ≠ APK 可用**：曾出现「DEX 里没有 Kotlin 类、CLI 仍 SUCCESSFUL、真机闪退」。换过构建配置后校验一次
> `app-debug.apk` 的 dex 里是否含 `IjiApplication`（见 7.1）。

### 2.2 安装 / 走查
```powershell
$adb = 'D:\SDK\platform-tools\adb.exe'          # 不在 PATH
& $adb devices                                   # 期望：emulator-5554 和/或 真机 d385580c
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am force-stop com.iji.app
& $adb shell am start -n com.iji.app/.MainActivity
```
截图（**必须**用 `Start-Process -RedirectStandardOutput`，PowerShell 的 `>` 会破坏 PNG 二进制）：
```powershell
Start-Process -FilePath $adb -ArgumentList 'exec-out','screencap','-p' `
  -RedirectStandardOutput 'C:\Temp\s.png' -NoNewWindow -Wait
```
点击/滑动：`adb shell input tap X Y`、`adb shell input swipe X1 Y1 X2 Y2 400`（屏幕 1280×2856；
uiautomator dump 拿真实坐标更稳）。**长按**用 `input swipe X Y X Y 1000`。

### 2.3 单测（重要）
`.\gradlew testDebugUnitTest` **在本机必失败**（Gradle worker 起不来）。改用直跑 JUnitCore：
```powershell
.\gradlew :app:compileDebugUnitTestKotlin --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run_unit_tests.ps1   # 需先设 JAVA_HOME（脚本读它找 java）；当前 OK (167 tests / 16 classes)
```
脚本自动扫描编译产物里的 `*Test.class` 并拼 classpath（junit / hamcrest / kotlin-stdlib / org.json / coroutines / okhttp / okio），
**加测试类不用改脚本**；只在新引入了运行期依赖时才要补一条 jar。
纯 JVM 单测里没有 Android Context：分类目录靠 `CategoryStore.all` 未 `init` 时回落到 `CategoryDefaults` 来支撑，别把它改成抛异常。

---

## 3. 技术栈（以 `gradle/libs.versions.toml` 为准）

| 项 | 版本 |
|---|---|
| AGP / Gradle / Kotlin | 9.3.0 / 9.6.0 / 2.4.20（**Kotlin 由 AGP 内置**，见 7.1） |
| compileSdk / targetSdk / minSdk | 37 / 35 / 33 |
| UI | Compose BOM 2026.08.00 + Material3 + **Miuix 0.9.4**（`miuix-ui`/`miuix-icons`/`miuix-blur`/`miuix-squircle`） |
| 持久化 | Room 2.8.5（KSP 2.3.12），数据库 **v22** |
| 其他 | Coil 3.3.0、DataStore 1.1.4、WorkManager 2.10.0、ML Kit 中文 OCR 16.0.1、navigation-compose 2.8.5 |
| 版本号 | versionName `1.0.0` / versionCode `2` |

**架构**：单 Activity（`ComponentActivity`）+ Compose；`IjiApplication` 里初始化 Room/分类目录/字体缩放；
`MainViewModel` 是跨页共享大 VM（每月/每账本查询用 `stateIn(WhileSubscribed)`，**导出类功能不要读它的 value，直接查库**）；
各页面另有自己的 ViewModel；Repository 层封 Room/SP。

---

## 4. 工程结构

```
app/src/main/java/com/iji/app/
├── IjiApplication.kt          # 初始化顺序：FontScale → CategoryStore.init → Room(DbProvider.init) → WarmUp
├── MainActivity.kt            # 纯 Compose 宿主（**必须 ComponentActivity**，见 7.3）
├── MainViewModel.kt           # 账本/账单/汇总/添加/删除/分类删除迁移
├── data/
│   ├── model/                 # Models.kt、Currency.kt(币种)
│   ├── db/                    # AppDatabase(v22) + 各 Entity/Dao + Converters + Migrations
│   └── repository/            # Transaction / Account / Ledger / Budget / Recurring / Template /
│                              # CategoryCatalog+CategoryStore+CategoryDefaults / CategoryIconStore /
│                              # BackupRepository(CSV) / ConfigBackupRepository(JSON) / SettingsRepository(DataStore)
├── ai/                        # AiBookkeeping(文本解析) / ScreenshotBillParser(截图解析) / Ocr
├── backup/                    # BackupManager / ConfigBackup / WebDavClient / CloudBackupManager / AutoBackupWorker
├── accessibility/             # 无障碍截屏导入（用户主动触发 + 3 秒倒计时）
├── lock/                      # 应用锁（PIN 加密存储，防暴破）
├── reminder/  update/         # 预算提醒通知 / 检查更新
└── ui/
    ├── AppRoot.kt              # NavHost + 底部四 tab + 记一笔圆钮 + 我的入口；ImmersiveRoutes 登记
    ├── components/ (32)        # IjiTopBar / IjiDialog / IjiTextAction / IjiSettingsRows / Formatters / IconLoader / CategoryIcons …
    ├── effects/ (14)           # AGSL 渐进模糊、layerBackdrop、折叠顶栏相关
    ├── home/ assets/ calendar/ stats/ add/ budget/ ai/ mine/(27) / theme/ nav/ util/
```

---

## 5. 功能完成度

| 模块 | 状态 |
|---|---|
| 首页账本 | ✅ 月度卡片、按天分组、点行进详情、多选（导出/删除）、逐行滑动（右滑编辑等）、折叠顶栏 + AGSL 模糊 + 日头吸顶 |
| 记一笔 | ✅ 支出/收入/转账（双账户）、组合付款、优惠、报销/未对账、标签、附件、四则运算键盘、保存再记、模板、AI 预填、**退出即存草稿** |
| 资产 | ✅ 账户增删改、净资产/总资产/负债、借贷记录（借入/借出）、对账与报销列 |
| 日历 | ✅ 卡片化、横滑切月、固定 6 行、选中/今天区分、每日账单 |
| 统计 | ✅ 维度切换（月/周/年等）、分类构成（看一级/看细分）、趋势图、预算进度 |
| 我的 | ✅ 设置、分类管理（**可增删改排序**）、账本管理（含币种）、标签、模板、周期账单、预算、数据管理（CSV/JSON 导入导出、本地备份、WebDAV）、应用锁、无障碍截屏导入、字体大小、关于/更新日志/许可/隐私。**2026-10-27 收口**：时间设置并入偏好设置（新增「时间」组）；数据管理并入备份与恢复并改名「数据与备份」（含加密备份/自动备份/网盘 WebDAV/配置与全量备份/CSV 导入导出/删除账单）；删除孤儿页 AiConfigScreen（旧版 AI 配置，现役为 ui/ai/AiSettingsScreen）；设置主页重排为 6 组：记账 / 预算与账单 / 自动化 / 数据与备份 / 偏好（含密码保护）/ 关于。**2026-11-02 偏好设置页重排**：6 组 16 行 → **5 组**（外观 / 提醒 / 超级岛 / 时间 / 动效与手感），主题由 3 行单选改滚轮；周开始日从两行同名条目（原 bug）改一行 + 滚轮；行构造抽到 `components/IjiSettingsRows.kt`，页面 888 → 310 行。**同日回归修复**：样式切换不能藏进「超级岛」组改名「记账提醒上岛」（用户认不出），已放回「提醒」组的 **「通知样式」开关行**（开=小米超级岛，关=原生通知，副标题写明当前模式）；`NotificationHelper.sendNative` 不再叠 MIUI 焦点参数（协议 1 会被 HyperOS 3 按 `focusType=PARAMS` 接管，导致既不上岛也无实时通知）。**2026-11-03 识屏悬浮窗「详细版」按 Figma 重构**（`figma.com/design/oDTuOgpdTgGBK8VCrec3u8` → 详细版页面）：新组件 `ui/overlay/DetailedOverlayCard.kt` 以 927×607 稿为基准、`scale = 实际宽/927` 等比换算全部尺寸/间距/字号，窗口改 `Gravity.TOP`（状态栏下 8dp）；含支出/收入/转账三态、金额就地编辑、分类/账户/日期/标签行内选择、不计入预算与计入资产、取消/保存（多笔时右上角 1/N，保存逐笔推进）；写入走 `QuickBillWriter.addDetailed`；字体 Actor/Inter 与图标（check/alarm/comment/fast_forward）已落到 `res/font`、`res/drawable`；同日二轮：账户/分类改用真实素材图标（`AccountIconImage` / `CategoryIconImage`），展开列表带图标且分类按一级+二级展示，时间改紧凑月历可选任意日期（不做时分，`Transaction.date` 只有天），展开态用固定 dp（不随 927 稿缩放，否则行高 19dp 点不准），悬浮窗再上移到 `statusBarHeight - 8dp` |
| 数据能力 | ✅ CSV 导出（UTF-8 BOM、14 列含币种）、全量备份 JSON、配置备份、自动备份（WorkManager）、截图/OCR/CSV 批量导入、多币种（**账本级、不换算**） |
| 数据与备份页 | ✅ 2026-10-27 精简为三入口：**导入**（自动识别全量/仅配置/旧版流水，先预览后一次确认）／**导出**（全量/仅配置/账单 CSV × 下载目录或自选位置）／**网盘备份**（目标目录 + 内容选择 + 就地状态反馈）；自动备份与「删除账单」各占一行 |

---

## 6. 数据与持久化（动表必读）

- **Room**：`iji.db`，`version = 22`，`addMigrations(1→2 … 21→22)` 全链显式迁移；`exportSchema = true`。
  7 张表：`transactions / accounts / ledgers / recurring_rules / budgets / budget_alerts / import_history`
  （标签、模板、分类目录、图标覆盖**不在 DB**，走 SharedPreferences）。
  金额一律 **Long（分）**；日期 epochDay。
- **降级会清库**：`fallbackToDestructiveMigrationOnDowngrade()`（刻意为之：旧版 APK 覆盖新版时避免崩溃），
  **回退版本/装低版本包 = 数据全丢**，别在真机用户数据上试。
- **SharedPreferences**（不随 DB 走）：`CategoryStore`（分类全量目录 + `seeded_v2` 标记）、`CategoryIconStore`
  （`override_id_<分类id>` 图标覆盖，读时懒迁移旧的名字 key）、`TemplateRepository`、`AppLockManager`（加密 PIN）、偏好项。
- **DataStore**：`SettingsRepository`（字体缩放、更新源、备份偏好等）。
- 备份 JSON / 配置 JSON 里的分类是**整份**导出（`categoriesFull = true`），恢复时整表替换；老备份只有自定义分类时按 id 补缺。
- 新增 DB 列：`ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT …` + 版本号 +1 + 注册迁移（参考 `MIGRATION_20_21`、`MIGRATION_21_22`）。

---

## 7. 硬约束（踩过坑，别改回去）

1. **AGP 9.3 内置 Kotlin**：`app/build.gradle.kts` **不要** apply `org.jetbrains.kotlin.android`；`gradle.properties` **不要**设
   `android.builtInKotlin=false`。设错的后果是 Kotlin 类不进 dex、CLI 仍 SUCCESSFUL、真机 `ClassNotFoundException`。
   （**`README.md` 里残留了一句旧说明，以本节为准**。）
2. **`MainActivity` 必须继承 `ComponentActivity`**：改成 `FragmentActivity`（例如为了 `androidx.biometric`）会让全项目
   ActivityResult（选图/选文件）一起崩。
3. **合规弹层**：所有弹窗统一 `IjiDialog`；`OverlayCascadingListPopup` 之类的 Popup 若由**长按**触发，松手那一帧会被
   当成"点外部"关掉（表现为一闪而过）——长按菜单请用 `IjiDialog`。
4. **子页面**：一律 `MineSubPage`，并在 `AppRoot.kt` 的 `ImmersiveRoutes` 登记，否则安全区算两次（底部多 52dp）。
5. **底栏选中态只单向跟随路由**，不要在 `onTabSelected` 里反向改 index（点「我的」会被弹回首页）。
6. **`layerBackdrop` 必须传 `recordKey`**，否则每帧全屏录制；底栏传当前路由、顶栏传 `Unit + mustRecord = isScrollInProgress`。
7. **`awaitEachGesture` 里 down 之前不能 `return`**（会让主线程空转 ANR），手势骨架要完整走完。
8. **`CurrencyDisplay` / `CategoryCatalog` 这类无 Context 全局单例，不要用 `mutableStateOf`**：单测是纯 JVM 直跑，
   引 Compose 运行时会 `NoClassDefFoundError`。UI 刷新靠各自的状态（如 `CategoryCatalog.cacheVersion`、Flow）。
9. **金额格式化统一 `formatMoney(cents, currency)`**（默认取当前账本币种）；不要硬编码 `¥`。
10. **分类 id 是稳定标识**：改名不改 id；`of(未知 id)` 返回「未分类」占位是**刻意**的（分类可被删除）。
11. **不要造假入口**（本项目的既定原则）：做不了就不放按钮；配置必须能清除；不许写死"已是最新/成功"。
12. **性能优先于动画**：动画卡顿且无法优化时，直接删动画（已删底栏/记一笔按钮的按压缩放）。

---

## 8. 待办（按优先级）

0. ~~AI 记账 / 自动记账页面重构~~ **已完成（2026-10-28）**：支付通知识别规则对齐 AutoAccounting v0.5.6
   （`PaymentNotificationParser` 26 条 App 专属规则 + 通用兜底）；解析管线三级化（通知规则 → AI → 本机关键词兜底，
   `AiBookkeepingService` 抽出 `postChat`/`billFromJson`，两处浮点已改 `Math.round`）；
   识别方式（本地 / AI）**下沉到管线**：`parseBill(text, ctx)` 不传 `useAi` 时读 `AiConfig.parseMode`，
   默认本地、未配 Key 强制本地（`useAi=true` 也绕不过 `AiConfig.aiReady`），三个入口行为一致；
   新增 `PaymentNotificationFilter` 关键词闸门（服务在 `parse` 前按配置过滤，页面可编辑 / 恢复默认）；
   自动记账页两个近似卡片合一 + 「已支持识别的来源」只读清单；`AiBookkeepingScreen` 收敛解析入口并支持转账类型展示。
   方案与取舍见 [`HANDOFF-AI记账与自动记账重构.md`](HANDOFF-AI记账与自动记账重构.md)。
   **第二轮精简（同日）**：AI 记账页 500 → 362 行（结果卡/按钮/示例/图片压缩拆到 `ui/ai/AiBookkeepingComponents.kt`，
   四个按钮模板合一，`encodeForOcr` 移到 `Dispatchers.Default`）；自动记账页刷新与读取去重、过滤输入框单行改多行、
   卡片文案压缩；规则解析的备注改取商户名（不再把整条通知塞进备注）。
   **第三轮重整合并（同日，参照 AutoAccounting / Hyper-pick-up-code）**：
   - **自动记账页从 8 张卡 → 2 个入口**：「识屏记账」（→ `Routes.SCREEN_CAPTURE`）、「支付通知记账」（→ `Routes.NOTIFICATION_RULES`）；
     AI 配置 / 图片识别不再从这里绕，改由 AI 记账页进入；
   - **AI 记账页**新增「AI 服务：模型名 / 未配置」+「AI 服务」「提示词」两个胶囊入口（配置不再散落在自动记账页）；
   - **新页 `PromptManageScreen`**（`Routes.PROMPTS`，设置 → 自动化 → 提示词管理）：账单识别（文本）/ 小票识别（图片）
     两段 system prompt 整页编辑，默认值抽到 `AiConfig.DEFAULT_PROMPT_TEXT/IMAGE`，「留空 = 默认」，
     与默认逐字相同则存空；`AiBookkeepingService` 两处硬编码 prompt 改为读配置；
   - **新页 `ScreenCaptureSettingsScreen`**（`Routes.SCREEN_CAPTURE`）：授权方式 chips（自动 / Shizuku / 无障碍）、
     音量键快捷开关、悬浮窗确认、立即识屏（原来三张卡 + 一个确认弹窗收成一页）；
   - **新页 `NotificationRulesScreen`**（`Routes.NOTIFICATION_RULES`）：无障碍状态 + 白/黑名单多行编辑 + 支持来源列表，
     替代原「过滤关键词」「支持来源」两个弹窗；
   - **音量键快捷识屏**：新增 `accessibility/VolumeKeyCaptureService`（系统「无障碍 → 音量键快捷键」按需启动，
     截完 `disableSelf()`，不常驻、不读界面内容）+ `res/xml/volume_key_accessibility_service.xml` + Manifest 注册；
     `CapturePreferences` 存授权方式与开关（`resolveMode` 纯函数：指定就只看那一个，自动才回落）；
   - 新增单测 `CapturePreferencesTest`（5 例）、`AiPromptTest`（4 例），全量 **165 tests / 16 classes** 全绿。
   **第四轮：记账偏好（对照 AutoAccounting「记账设置」）**：新增 `Routes.BOOKKEEPING_PREFS` 页
   （自动记账第 3 个入口）：落账账本（chips，含「跟随当前账本」）、备注模板（`{商户}` / `{来源}` + 实时预览）、
   重复通知合并窗口（30/60/120/300 秒），改一项即时生效；
   落点：`ScreenCaptureFlow` 悬浮窗账本、`AppRoot` 通知确认前 `LedgerSelection.select`、
   `PaymentBillStore.consume` 用模板生成备注（`put` 新增 `sourceLabel`）、
   无障碍服务去重窗口改读设置（原 `DEDUP_WINDOW_MS` 常量删除）。新增 `AutoBookkeepingPrefsTest`（8 例）。
   刻意不做：手续费 / 债务 / 自动创建分类 / 资产映射 / AI 月末总结 / 双击背部触发 —— iji 没有对应数据模型或触发面，
   不造空开关。
   **第五轮：AI 记账页整体重写（对齐 AutoAccounting「AI 助理」）**：删除 `AiSettingsScreen.kt` 与
   `Routes.AI_SETTINGS`（配置全部内嵌进 AI 记账页的折叠区块 `ui/ai/AiServiceSection.kt`）；
   页面改为「AI 状态卡（总开关 + 状态 + 配置展开）→ 记账区 → AI 功能开关 → 示例」；
   AI 功能新增两个**真实生效**的开关：`图片识别用 AI`（关 = 图片永不出设备，`AiConfig.KEY_VISION`）、
   `AI 识别分类`（关 = 分类落「其他」，`categoryHintFor` 纯函数 + 2 例单测）。
   原「识别方式」滚轮改为「文本识别用 AI」开关（未配 Key 时点开自动展开配置并提示）。
   全量 **167 tests / 16 classes** 全绿。
   未做的可选项（有意）：`AiSettingsScreen.testConnection` 仍自带 10s/15s 短超时，不复用 30s/60s 的 `postChat`；
   `ruleBasedParse` 仍内联在 service（只有一个调用点，抽文件反而多一层跳转）。
   **顶栏对齐修复（同日）**：`Routes.AI` 漏登记 `ImmersiveRoutes`（删 `AI_SETTINGS` 时只删未补）→
   `ijiRoute` 在外层又垫一次安全区，整条顶栏与内容比其它子页低一个状态栏高度（实测 390 vs 234px @3x）；
   子页纯色顶栏底色取 Miuix `background`（#FFFFFF）与页面底色（#F5F6F8）不同 → 状态栏下留一条悬空白带。
   已修三处：`AppRoot` 补登记、`IjiTopBar` 纯色底改取 M3 `background`、`CollapsibleTopBar` 居中标题按左右
   操作区宽度收紧上限（长标题不再压按钮）。**顶栏对齐口径**：返回左 16dp / 标题全栏水平居中 / 操作右 16dp，
   三者垂直中心同一线。细节见 `HANDOFF-AI记账与自动记账重构.md` 8.5。
   **提示词与本地规则（第六轮）**：`prompts` 页由「两段提示词 chips 切换」改为规则中心
   （提示词 5 类 / 本地识别规则 / 规则数据三段，行=标题+摘要+箭头），二级页 `prompt_edit/{kind}`、
   `local_rule_edit/{ruleId}`。提示词种类集中在 `ai/AiPromptKind`（账单文本、小票图片、分类识别、
   账户识别、AI 摘要，**每一类都有真实调用点**）；本地识别规则 `ai/LocalRuleStore` + `LocalRuleEngine`
   取代旧 `ruleBasedParse` 的硬编码分类，可增删改、可导出导入、可恢复内置；AI 结果的分类/账户对不上真实
   数据时会用「分类识别」「账户识别」补一次映射；统计页新增「AI 小结」（只发当期汇总）。单测 167 → **201**。
   细节见 `HANDOFF-AI记账与自动记账重构.md` 第九节。
   **识屏过程态上岛（同日后续）**：识屏从倒计时开始就发一条**进行中**的超级岛（「准备识屏」→「正在识别」），
   识别结束整条撤下；开关在偏好设置 → 超级岛通知 →「识屏识别中上岛」（默认开）。踩到的真根因是**焦点协议代数**：
   HyperOS 3（`notification_focus_protocol=3`）只认 `protocol: 3` 模板，沿用 `protocol: 1` 会「建出岛事件但不浮出」，
   已按 Maling-Island 的模板补 `islandParamsJsonV3`，由 `sendIsland` 按 `islandLevel` 自动选方言。
   细节见 `HANDOFF-AI记账与自动记账重构.md` 第十节。
1. **真机走查 3 项**（模拟器已过）：
   - 深色模式 + 大字号组合（四 tab + 我的子页）；
   - 分类管理的**手指拖动排序**（adb 注入无法模拟"长按后拖"，只能用行尾 ↑↓ 验证过写盘）；
   - 网盘上传 / 恢复的真实账号联调（2026-10-27 已用假凭据验证 401 错误反馈与配置收起态；「导出 → 导入配置」整链已实测通过）。
2. **C 类空白领域**（用户口述清单，均未开工）：云同步/多设备（WebDAV 已有，S3 无 SDK）、Excel/PDF 导出（无依赖）、
   桌面小部件（无 Glance）、多语言（全中文硬编码）、首次引导/新手教程、自定义主题色、共享账本/AA 分账、高级筛选（金额/日期/分类组合）。
3. ~~低优先技术债 3 项~~ **已完成（2026-10-28）**：
   - `AppDatabase` 改用 Room 2.8 新重载 `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`
     （deprecated 告警消失；`dropAllTables = true` 是官方推荐值，连非 Room 表一起清）；
   - `README.md` 按实际重写：版本/技术栈（补 Coil / ML Kit / WorkManager / DataStore / Shizuku）、
     `JAVA_HOME` 改为本机实测路径、补单测命令与 148/13 计数、重写工程结构树、
     删除自相矛盾的「后续迭代候选」、修「30 个设置子页」（实为 6 组 14 入口）、
     修「红色品牌色」（主色早已是 `#2177E8`）、更新降级重建的 API 名；
   - 分类排序页 ↑↓ 命中区 36×40dp → **48×48dp**（`uiautomator dump` 实测节点 144×144px @3x），
     实测点「交通」上移 → Snackbar「排序已保存」，再点下移恢复原序。
   新发现但**未处理**（不在本次范围）：① `exportSchema = true` 但没配 `room.schemaLocation`
   （KSP 每次编译都打 warning，schema 实际没导出）；② `CategoryManageScreen` 的 Material3 `TabRow` 已 deprecated
   （建议换 `PrimaryTabRow`，会影响指示器样式，需走查）。
4. **git**：整个工作区未初始化提交，接手方需要自己决定仓库策略。

---

## 9. 交接备注

- 验证产物（截图、临时 db、导出 csv）用完即删，别留在 `.codebuddy/` 或 `Download/`。
- `y:\iji-android` 根目录下的 `A类_补底层.md`、`D类_数据与健壮性.md`、`_harness_out/`、`fix_*.py`、`generate_icons.py`、
  `build_log_*.txt` 是历史工作文件，**不是构建输入**，可清理但别误以为有用。
- `HANDOFF-分类图标与账户功能.md` 是更早一轮的专题交接，内容已被本文覆盖。
- 交付测试包时：先复制到设备 `Download/` 再让测试者安装（`adb push`）。
