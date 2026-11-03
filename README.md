# i记 (iJi)

一款 Kotlin + Jetpack Compose 的安卓记账 App，页面设计参考 **iCost**（结余卡片、弹出式记账面板），导航采用**底部四 tab（账本 / 资产 / 日历 / 统计）+ 最右「记一笔」圆钮 + 右上角「我的」头像入口**，整体为 **MIUI / HyperOS 风格**（Miuix 组件库，主色 `#2177E8` + 手绘线性分类图标）。

> 构建 / 单测 / 走查方法与硬约束、当前待办，以仓库根的 **[`HANDOFF.md`](HANDOFF.md)** 为准；
> AI 记账与自动记账的改造细节见 **[`HANDOFF-AI记账与自动记账重构.md`](HANDOFF-AI记账与自动记账重构.md)**。

当前为 **v1.0.0（versionCode 2）**：四 tab 全功能（账本/资产/日历/统计）+ 记一笔悬浮面板（支出/收入/转账、组合付款、四则运算键盘、草稿自动保存）+ 「我的」设置中心（6 组 14 个入口，`ui/mine` 下 28 个页面文件）+ Room 本地持久化（DB v22）+ 多账本、多币种、分类自由编辑、预算 / 周期账单 / 模板 / 应收垫付、应用锁、CSV 导入导出、WebDAV 网盘备份、AI 记账、整屏截图批量导入、Shizuku 识屏、无障碍支付通知识别。

## 技术栈

| 项 | 版本 |
|---|---|
| Kotlin | 2.4.20（**由 AGP 内置，见「构建环境备注」**） |
| Android Gradle Plugin | 9.3.0 |
| Gradle | 9.6.0 |
| compileSdk | 37（miuix 要求 ≥37） |
| targetSdk / minSdk | 35 / 33 |
| UI | Jetpack Compose (Material 3, BOM 2026.08.00) + **Miuix 0.9.4** |
| 持久化 | **Room 2.8.5**（runtime / ktx / compiler，KSP 2.3.12 注解处理），数据库 **v22**；偏好走 **DataStore 1.1.4** |
| 图标 | **top.yukonga.miuix.kmp:miuix-icons:0.9.4**（MIUI 系统符号字体派生）+ 自绘 MIUI 线性分类图标 |
| 图片 / OCR | **Coil 3.3.0**（头像等）、**ML Kit 文本识别 16.0.1**（小票 OCR 本地兜底） |
| 后台 / 系统 | **WorkManager 2.10.0**（自动备份、预算提醒）、**Shizuku 13.1.5**（识屏截屏）、无障碍服务（支付通知） |
| 导航 | androidx.navigation:navigation-compose |
| 架构 | 单 Activity + Application 初始化数据库 + ViewModel + StateFlow + Repository（MVVM） |

> **图标方案说明**：底部导航（账本/资产/日历/统计）、右上角「我的」、记账面板关闭按钮、月份切换箭头、我的页（设置/分类/导出/关于）等直接引用 `MiuixIcons`（miuix-icons 扩展库，实心、24dp、源自 MIUI 系统符号字体）或自绘 `MiuixStyleIcons`（资产/日历/统计）；由于该库以系统 UI 图标为主，缺少餐饮/医疗/教育等生活分类图标，故记账分类图标（15 类 + 图表柱状图 + 日历 + 钱包）按 MIUI 线性风格（1.7dp 线宽、圆角端点）在 `MiuixStyleIcons.kt` 中手绘。

## 如何运行

1. 电脑安装 **Android Studio**（本项目用 2026.1 验证），JDK 17+ 即可；本机实际验证过的是
   `C:\Program Files\Java\jdk-24`（Studio 自带 JBR 同理），编译时把 `JAVA_HOME` 指向它。
2. `File → Open`，选择本项目根目录 `iji-android`，等待 Gradle Sync 完成（首次会下载依赖，需要网络）。
3. 连接安卓真机（开启 USB 调试）或启动模拟器，点击 **Run ▶** 即可安装。

命令行构建（Windows PowerShell）：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"   # 换成你机器上的 JDK 路径
.\gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

单测（**不要用 `gradlew testDebugUnitTest`**：本机 Gradle worker 起不来，用户目录含中文）：

```powershell
.\gradlew :app:compileDebugUnitTestKotlin
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run_unit_tests.ps1
```

当前 **211 用例 / 21 个测试类**全绿；脚本自动扫描编译产物里的 `*Test.class`，加测试类不用改脚本。

> 国内网络下若 Gradle 发行版下载超时，可从镜像下载 zip 后放入 `%USERPROFILE%\.gradle\wrapper\dists\gradle-9.6.0-bin\<hash>\` 并创建同名 `.ok` 文件，wrapper 即用本地缓存。

## 已实现

- **导航布局**：底部四 tab（账本 / 资产 / 日历 / 统计）+ 最右「记一笔」圆形按钮 + 右上角圆形「我的」入口（我的页自身隐藏）；四个 tab 都是完整功能页（日历含横滑切月与每日账单）。
- **资产页**：净资产总览（净资产 + 总资产/总负债 + 总借入/总借出）+ 资金账户列表 + 添加/编辑账户
  （品牌徽章图标、实时校验、未保存确认），净资产随账户余额实时联动。
- **首页（账本）**：月度概览卡（支出/收入/结余 + 预算剩余）、按天分组的账单列表（点行进详情、支持多选批量操作）、四种空态。
- **记一笔**（最右「＋」弹出 iCost 风格**悬浮面板**，约 70% 屏高）：
  - 顶部：左上 × 关闭 | 支出 / 收入 / **转账** 三段切换 | 右上角**选择账本**；
  - 功能排（固定于键盘上方）：账户、报销、优惠、标签、标记、附件；
  - 数字键盘支持**混合四则运算**（先乘除后加减）与「保存再记」；日期 / 备注 / 分类（分类可自由增删改，转账不分类）；
  - 组合付款（splits）与退款关联（refundOfId）；转账用双账户且不计入收支统计；
  - 关闭面板不弹「放弃编辑」：退出即保存草稿，下次打开自动恢复。
- **日历页**：卡片化 + 横滑切月 + 固定 6 行（选中=主色实心、今天=描边、相邻月淡显可点）。
- **统计页**：维度切换口径统一（支出/收入 × 当期）、分类占比环形图 + 分类卡、近 6 个月收支趋势柱状图。
- **Room 本地持久化**：`AppDatabase`（iji.db，当前 **v22**，含 1→22 全链显式迁移），金额以「分」存 Long，
  日期存 epochDay、标签存逗号分隔字符串（TypeConverter）；空库不播种模拟账单，只补默认账本与 2 个默认账户；
  降级安装走破坏性重建（`fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`）。
- **我的（设置中心）**：右上角头像进入，6 组 14 个入口——记账（分类 / 账本 / 模板 / 标签）、
  预算与账单（预算 / 周期账单 / 应收·垫付）、自动化（自动记账 / AI 记账）、数据与备份、
  偏好（偏好设置 / 密码保护）、关于（新手教程 / 关于应用）。
- **智能录入**：AI 记账（本地规则优先 → 按需 AI → 本机关键词兜底，默认不联网）、小票拍照/相册 OCR、
  整屏截图按行拆分批量导入、Shizuku 识屏记账、无障碍读取支付通知（关键词白/黑名单过滤，确认后入账）。
- **数据进出**：CSV 导入（微信 / 支付宝 / 通用）、CSV 导出、WebDAV 网盘备份与恢复、自动备份（WorkManager）。
- 深色模式自动跟随系统；收入绿色 `#34C759`，金额格式化统一走 `formatMoney(cents, currency)`（多币种只换符号、不换算）。

## 工程结构

```
app/src/main/java/com/iji/app/
├── IjiApplication.kt          # Application：初始化 Room 与预置数据
├── MainActivity.kt            # 单 Activity，纯 Compose（必须继承 ComponentActivity）
├── MainViewModel.kt           # 页面状态与业务编排（按月查询、分类、批量操作…）
├── data/
│   ├── model/                 # 领域模型（Transaction / Category / Account …）
│   ├── db/                    # Room：AppDatabase(v22) + Entity / Dao / Converter + 迁移链
│   └── repository/            # 仓库层（账单 / 账户 / 分类 / 预算 / 模板 / 周期账单 / 备份 / 设置…）
├── ai/                        # AI 记账：三级解析管线、AiConfig、本地 OCR、截图解析
├── accessibility/             # 支付通知：解析器（26 条 App 规则 + 兜底）、关键词过滤、无障碍服务
├── backup/                    # 备份：导入导出、WebDAV 客户端、加密、自动备份 Worker
├── budget/                    # 预算计算、提醒调度与 Worker
├── lock/                      # 应用锁（PIN）
├── reminder/                  # 周期账单提醒
├── shizuku/                   # Shizuku 桥接（识屏截屏）
├── update/                    # 检查更新（默认不联网）
└── ui/
    ├── AppRoot.kt             # 路由与底部导航（新路由须登记 ImmersiveRoutes）
    ├── nav/                   # 路由常量
    ├── theme/                 # 颜色 / 主题 / 排版 / Dimens
    ├── components/            # 通用组件（IjiDialog / IjiTopBar / 状态视图 / 滚轮选择…）
    ├── effects/               # 玻璃、边缘光、背景等视觉效果
    ├── home/ assets/ calendar/ stats/   # 四个 tab
    ├── add/                   # 记一笔面板
    ├── mine/                  # 我的 + 设置子页（28 个文件）
    ├── ai/                    # AI 记账页 + AI 设置
    ├── budget/                # 预算页
    ├── overlay/               # 悬浮窗确认
    └── util/                  # 工具
```

测试在 `app/src/test/java/com/iji/app/`（纯 JVM，13 个测试类），用 `tools/run_unit_tests.ps1` 直跑。

## 数据说明

- 账单持久化于应用私有数据库 `iji.db`（Room，v22），**重启不丢**；新增/修改即时写入。
- **不再向真实库播种模拟数据**：首次启动只有一个默认账本与 2 个默认账户，账单由用户录入或导入。数据在 `/data/data/com.iji.app/databases/iji.db`。

## 构建环境备注

- 本机 `D:\SDK` 的 API 37 平台目录名为 `android-37.0`（新版 SDK 命名），AGP 9.x 可原生识别；若降回 AGP 8.x 会遇到「无法找到 platforms;android-37」问题。
- **AGP 9.3 内置 Kotlin（红线）**：`app/build.gradle.kts` **不要** apply `org.jetbrains.kotlin.android`，`gradle.properties` **不要**设 `android.builtInKotlin=false`。
  设错的后果是 Kotlin 类不进 dex、命令行仍报 BUILD SUCCESSFUL、真机启动即 `ClassNotFoundException`。JVM target 由 `compileOptions` 的 `VERSION_11` 决定。
- Room 注解处理走 KSP（`com.google.devtools.ksp:2.3.12`）。

## 待办与已知限制

待办（真机走查缺口、C 类空白领域）见 **[`HANDOFF.md`](HANDOFF.md) 第 8 节**。

已知限制：

- 多币种是「账本级币种 + 仅展示」，**不做汇率换算**，跨币种金额不相加。
- 回退安装更低版本 APK 会触发破坏性重建（`fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`），
  数据清空；升级路径不受影响。
- 统计的时间范围由维度切换提供，不支持任意起止日期。
- 单测只能直跑 JUnitCore：`gradlew testDebugUnitTest` 在本机会因 Gradle worker 起不来而失败。
