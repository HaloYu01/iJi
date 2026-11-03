# i记 · 交接文档（分类图标 / 账户分类 / 资产页修复）

> 本文档供 CodeBuddy 接续 i记 记账 App（`Y:\iji-android`）开发使用。
> 内容：最近一批已完成改动的任务描述、背景、实现细节、验收标准与注意事项。

---

## 一、任务需求描述（最近一批已完成，接续开发的基线）

以下工作**已全部完成并编译通过、APK 已交付**，CodeBuddy 接手时先核对现状，再继续后续迭代：

1. **分类图标全局应用与同步**
   - 「我的 → 记账管理 → 分类管理」页接入真实分类数据，分类图标正确显示（素材包彩色圆底 + 白色线条图标）。
   - 分类管理中修改分类图标后，**全局同步**：记一笔分类面板、账单行图标同步更新。
   - 图标选择弹窗中，每个图标下方标注素材包文件名（严格按压缩包命名规则，即文件名去 `.png`）。
2. **记一笔分类数量与分类管理完全一致**
   - 记一笔分类面板改为全量平铺（一级 + 二级分类全部显示），不再折叠，数量与分类管理一致（支出 28 / 收入 6 / 转账不分类）。
3. **添加账户独立页面 + 账户分类选择**
   - 资产页左上角加号 → 独立「添加账户」页（原弹窗已删除）。
   - 页面含：账户分类 chips（全部/现金/银行/网银/充值/投资/信用/其他）→ 图标列表按分类过滤 → 分类内按常用优先级排序（五大行前置）。
   - 布局已紧凑化（图标 40dp、列表高 280dp、节间距 4dp）。
4. **资产页字体与白色区块重叠修复**
   - 根因：资产页 LazyColumn 漏挂 `collapsibleTopInset` + `nestedScroll`，顶栏白色模糊块（120dp）与内容（52dp 起始）错位。
   - 已补齐接线，与账本/日历/统计三页一致。

---

## 二、背景信息

### 工程与构建
- 工程：`Y:\iji-android`；applicationId `com.iji.app`（Kotlin + Jetpack Compose + Room + **Miuix 0.9.4**，与 `gradle/libs.versions.toml` 的 `miuix = "0.9.4"` 一致；AGP 9.3.0 / Kotlin 2.4.20）。
- JAVA_HOME：`E:\Android Studio\jbr`
- 构建命令：`Set-Location Y:\iji-android; .\gradlew.bat :app:assembleDebug --console=plain`
- APK 输出：`Y:\iji-android\app\build\outputs\apk\debug\app-debug.apk`
- **交付方式（2026-10-02 更新）**：**无需生成/复制 APK 交付包**，用户直接在 Android Studio 中用真机运行调试；本侧只需保证 `assembleDebug` 编译通过即可。
- 构建禁忌（沿用）：app/build.gradle.kts **不应用** kotlin-android 插件、不写 jvmTarget 块、gradle.properties 不设 builtInKotlin=false。

### 图标素材
- assets 目录：`Y:\iji-android\app\src\main\assets\icons\`（category 453 个白色线条 PNG / account 145 个彩色 logo PNG / placeholder 2 个）。
- **文件名 = 分类名/账户名（中文）**，APK 内 zip 条目字节为正确 UTF-8（PowerShell/Python 显示层乱码是显示问题，**不是 bug，勿改文件名**）。
- 加载器：`app\src\main\java\com\iji\app\ui\components\IconLoader.kt`
  - `CategoryIconImage(name, color, size, selected, fallback)`：彩色圆底 + 白色图标。
  - `AccountIconImage(iconKey, size)`：彩色 logo 直出，失败回退自绘徽章。
  - `loadCategoryIcon` 加载顺序：**用户覆盖 → 原名 → 别名 → 占位图**。
  - 分类别名映射 `CATEGORY_ALIAS`（正餐→午餐、食材→餐饮、居住→住房居家、宽带→网络、游玩→门票、教育→教育学习、借出→应收、报销→报销款、借入→其他、转账→其他）。

### 关键机制：分类图标全局同步
- 存储：`data\repository\CategoryIconStore.kt`（SharedPreferences，分类名 → 自定义图标文件名）。
- 记一笔分类面板、账单行、分类管理都走 `CategoryIconImage(name=分类名)` → 内部自动读覆盖存储 → **一处修改全局生效**，无需额外接线。

### 账户分类模型
- `data\model\AccountType.kt`：enum（ALL/CASH/BANK/ONLINE/RECHARGE/INVEST/CREDIT/OTHER），每项含 `label`、`keywords`（归类关键词）、`priorityKeywords`（排序优先级）。
- 归类：`typeOf(iconName)` 按声明顺序匹配关键词（信用最优先，避免「微信分付」误判网银）。
- 排序：`sortByPriority(names, priorityKeywords)` 稳定排序，未命中保持原序。
- 银行优先级：邮政、农业、中国银行、建设、招商 → 工商、交通 → 中信、浦发、民生、兴业、光大、平安、广发、华夏、浙商、渤海。
- 网银优先级：微信、支付宝、云闪付、QQ钱包、华为钱包、京东、数字人民币。

---

## 三、期望输出 / 目标（后续迭代方向参考）

### 2026-10-02 后续迭代进度（CodeBuddy 接续完成）

1. **分类管理页添加分类** ✅
   - 右上角「添加」已接入：支持新增**一级 / 二级**分类（二级需选父分类）、重名校验。
   - 图标自动按分类名匹配素材包（走 `loadCategoryIcon`），分类管理页可再「换图标」。
   - 自定义分类可删除（内置分类不可删）。
   - **未做**：分类改名、排序调整。
2. **账户分类持久化** ✅
   - `AccountEntity` 新增 `category`（存 `AccountType` 枚举名），Room **v6 → v7**（`MIGRATION_6_7`，`NOT NULL DEFAULT ''`）。
   - 全链路：`AddAccountScreen → AssetsViewModel → MainViewModel → AccountRepository → AccountEntity`。
   - 资产页已按分类**分组展示**（分组标题含数量与该组小计）。
3. **存量账户数据兼容** ✅
   - `AccountType.ofAccount(category, iconKey, name)`：category 为空时按「英文 iconKey → 中文名 → 账户名」逐级推断。
   - 修复崩溃隐患：`AccountCatalog.of()` 原用 `all.first{}`，新账户 iconKey 为中文名不在内置 12 项里会抛 `NoSuchElementException`，已改为兜底返回。
4. **SVG 图标**：✅ 上传与存储已完成 / ⏸ 渲染待定
   - `SvgIconStore`：选文件 → 复制到 `filesDir/svg_icons/<名称>.svg` → SharedPreferences 记录映射。
   - 入口：分类管理页图标弹窗右上角「上传 SVG」。
   - **渲染未接入**（等定 Coil-svg / AndroidSVG 方案后再做），存储结构已按后续可直接读取设计。

## 四、约束条件与注意事项

1. **UI 风格**：全部使用 Miuix 组件（`top.yukonga.miuix.kmp.*`）；Miuix TextField 用 `label` 参数（String），**无 placeholder 参数**；Material3 OutlinedTextField 才有 placeholder。
2. **弹窗**：应用内统一走 `IjiDialog`（NexioSchedule GlassOverlayDialog 外壳，液态玻璃），调用点须位于 Miuix Scaffold 子树内。
3. **底部导航**：只在账本/资产/日历/统计四个主 tab 显示；非主 tab 页面不显示底部导航栏。
4. **顶部顶栏**：四主 tab 页共用 `IjiTopBar`（AGSL 渐进模糊 + 折叠动画），LazyColumn 必须挂 `collapsibleTopInset(scrollBehavior)` + `nestedScroll(scrollBehavior.nestedScrollConnection)` + 条件 `layerBackdrop(topBarBackdrop)`，否则文字与顶栏白色块重叠。
5. **Kotlin KDoc 陷阱**：注释里写路径含 `/*`（如 `icons/category/*.png`）会触发嵌套注释导致整个文件语法错误，改写注释避开。
6. **图标文件名编码**：中文文件名是正确 UTF-8，勿用 Windows 工具"修复"文件名。
7. **数据库**：当前账户/分类均未入库（分类为 CategoryCatalog 硬编码），加字段需 Room migration（AppDatabase.kt / AccountEntity.kt）。
8. **用户偏好**：每轮小步迭代、直接交付可安装 APK 到桌面、对 UI 还原要求高（100%）、拒绝页面底部整块留白、不要改回液态玻璃底栏（blur 8dp / lens 24dp / downsample 1f 参数不可再降）。

---

## 五、验收标准（最近批次）

- [ ] 分类管理：支出 28 项 / 收入 6 项 / 转账不分类，图标为彩色圆底白线图标，二级分类标注「二级分类」。
- [ ] 分类管理换图标 → 记一笔分类面板 + 账单行同步变化（无需重启）。
- [ ] 图标选择弹窗：4 列网格、每图标下方有素材包中文名标注、选中高亮、可恢复默认。
- [ ] 添加账户页：分类 chips 过滤图标、银行/网银常用项前置、名称空校验提示、保存返回资产页列表刷新。
- [ ] 资产页：净资产卡完整显示无遮挡；滚动时内容上穿顶栏呈渐进模糊，无文字与白色块重叠。
