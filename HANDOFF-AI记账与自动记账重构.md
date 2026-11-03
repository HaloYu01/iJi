# i记 · 交接文档（AI 记账 / 自动记账页面重构）

> 本文档供 CodeBuddy 接续开发使用。
> 任务：**根据已移植的 AutoAccounting 识别规则，重构 AI 记账页与自动记账页**——功能完善、不冗余、代码精简。
> 现状：**已完成（2026-10-28）**——第一~三节为当时的现状分析与方案（保留作决策依据），
> 实际落地结果、验收数据与有意未做的可选项见第六节。

---

## 一、任务需求描述

### 1.1 已完成（本轮基线，接手后不必重做）

对照 [AutoAccountingOrg/AutoAccounting](https://github.com/AutoAccountingOrg/AutoAccounting) 的规则体系，
其通知/短信识别规则已全量移植到本仓库：

1. **`app/src/main/java/com/iji/app/accessibility/PaymentNotificationParser.kt` 全量重写**，两层结构（与 AutoAccounting 同构）：
   - **App 专属规则层 `APP_RULES`（26 条）**：对应 AutoAccounting 规则包 **AutoRule v0.5.6** 的全部 24 条
     notice 规则——微信支付、支付宝花呗、网商银行（滴滴）、云闪付（消费/退款/转入/转出）、招商银行
     （信用卡/快捷支付/入账/收款/一卡通转出）、掌上生活、工商银行、农业银行、中信银行（消费/退款/笔笔返还现）、
     美团月付（支出/退款）、小米智能卡、数字人民币（两式）、广西北部湾银行、OPPO/一加钱包。
     每条按来源包名精确限定（`app != null && app != sourceApp` 即跳过），正则与原版一致。
   - **通用兜底层**：收支关键词对齐 AutoAccounting `common.js` 的 `isPaymentType`
     （支出：支付/消费/支出/转出/取出/支取/扣款/购买/付款/出账/取款；收入：收入/收款/入账/到账/转入/存入/存款/退款/退回/返现）；
     金额提取三级优先「人民币→元→¥」；「余额/额度」前缀排除；短信签名`【银行名】`作账户提示。
   - 防误判三闸：`RE_BLACKLIST`（验证码/校验码/动态密码/中奖/兑奖直接拒绝）、金额 0 拒绝、超 100 万元拒绝。
   - `cents()` 已改为**字符串精确解析**（修复 `"19.90".toDouble()*100 = 1989` 浮点 bug，旧代码同样有此 bug）。
   - 对外入口：`parse(sourceApp: String?, text: String)`；`parse(text)` 兼容旧调用。
2. **`PaymentNotifyAccessibilityService.kt`**（约 74 行处）：`onAccessibilityEvent` 已把 `sourcePkg` 传入解析器。
3. **新增 `app/src/test/java/com/iji/app/accessibility/PaymentNotificationParserTest.kt`（28 用例）**：
   文案取自 AutoAccounting 规则模板与其测试数据 `test_data_didi.json` 的真实通知。
4. 顺带修复 `BackupRepositoryTest.kt` 微信 CSV 用例的**测试数据列错位**（「零钱」误写在「当前状态」列，
   已改为 `"支付成功","零钱"`；`parseWeChatCsv` 代码本身没问题——列位置按表头找，不写死下标）。
5. 单测基线（重构收口后）**148 tests / 13 classes 全绿**、`:app:assembleDebug` 通过；`HANDOFF.md` 2.3 节计数已同步。

### 1.2 剩余（本次要做的重构）

按 AutoAccounting 的架构思路（**本地规则优先、AI 兜底、关键词过滤闸门**）重构四个文件，
详见第三节方案：

| 文件 | 行数 | 动作 |
|---|---|---|
| `ai/AiBookkeepingService.kt` | 239 | 抽共享请求助手、三级解析管线、修浮点 bug |
| `ui/ai/AiBookkeepingScreen.kt` | 468 | 收敛三处重复解析启动块、支持转账类型展示 |
| `ui/mine/AutoBookkeepingScreen.kt` | 310 | 合并双卡片组件、新增关键词过滤与支持来源展示 |
| 新增 `accessibility/PaymentNotificationFilter.kt` + 单测 | — | 白/黑名单过滤闸门（纯 JVM 可测） |

`ui/ai/AiSettingsScreen.kt`（683 行）结构完好，**不在本次范围**（最多让它的 `testConnection` 复用 service 的共享助手，可选）。

---

## 二、背景信息

### 2.1 构建与验证（本机硬约束）

- `JAVA_HOME = C:\Program Files\Java\jdk-24`（系统 PATH 里没有 java，必须显式设置）；
  构建 `.\gradlew :app:assembleDebug --console=plain`。
- **`gradlew testDebugUnitTest` 在本机必失败**（Gradle worker 起不来，用户目录含中文）。改用：
  ```powershell
  .\gradlew :app:compileDebugUnitTestKotlin --console=plain
  powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run_unit_tests.ps1
  ```
  脚本自动扫描 `*Test.class` 拼 classpath，**加测试类不用改脚本**；只在新引入运行期依赖时才补 jar。
- AGP 9.3 内置 Kotlin 红线：`app/build.gradle.kts` 不 apply `org.jetbrains.kotlin.android`、
  不写 jvmTarget 块、`gradle.properties` 不设 `android.builtInKotlin=false`。
- **纯 JVM 单测没有 Android Context**：`SettingsRepository`（DataStore）在单测里不可用——
  过滤/管线逻辑必须写成**纯函数**（名单作为参数传入），Context 读配置只放在服务端/UI 侧。

### 2.2 AutoAccounting 规则体系要点（已移植部分的知识沉淀）

- 规则本体在其**私有仓库 AutoRule**，打包发布在 `https://license.ez-book.org/rule/download/?version=…`
  （v0.5.6 及以下不加密，请求需带 `X-Device-*` 头 + 以设备 UUID 为密钥的 HMAC 签名，见其
  `LicenseNetwork.kt`；本机临时目录 `C:\Users\董\AppData\Local\Temp\AutoAccounting`、`...\rule_dist`
  有完整克隆与解包，**可能已被清理，知识已沉淀进 parser 与本文，无需重新拉取**）。
- 通知链路：`NotificationService.onNotificationPosted` → 包名白名单 → **关键词白/黑名单**
  （`AnalysisUtils.inWhitelist/inBlackList`，存 `PrefManager.dataFilter/dataFilterBlacklist`）→ JS 规则引擎 → 失败回落 AI。
- **默认白名单关键词**（`DefaultData.DATA_FILTER`，本次过滤闸门的默认值）：
  `银行、信用卡、借记卡、公积金、元、￥、¥、人民币、消费、支付、支出、转出、取出、取款、收入、转入、存入、存款、退款、还款、贷款、借款、逾期、转账、账户、余额、交易、动账、账单`（黑名单默认空）。
- `common.js` 关键词与工具：`isPaymentType`（收支判定词表）、`splitSms`（`【签名】` 解析）、
  `splitShop`（商户名「-」前主体）、`toFloat`、`RuleObject`——除 `toFloat`（有浮点问题，未照搬）外均已移植。

### 2.3 本仓库链路（重构涉及）

```
通知: PaymentNotifyAccessibilityService(无障碍,只读通知)
        → PaymentNotificationParser.parse(pkg,text)   ← 已移植规则
        → 去重(60s) → 发"记一笔/忽略"通知
        → PaymentActionReceiver → PaymentBillStore(落盘)
        → PendingBillHolder.post(BillResult) → 打开记一笔面板预填
粘贴/语音: AiBookkeepingScreen → AiBookkeepingService.parseBill(text) ┐
图片:     AiBookkeepingScreen → LocalOcrEngine(本地优先) → parseBillFromImage ┘ → onConfirm(BillResult) → AppRoot prefillBill → 记一笔面板
```

- **`AiBookkeepingService.BillResult` 是全链路共享的解析结果类型**（type/categoryHint/amountCents/note/accountHint/tags），
  `PaymentBillStore.consume` 也构造它——重构时保持它是唯一类型。
- `AiConfig`：多厂商（智谱/OpenAI/DeepSeek/千问/Kimi/自定义），`resolvedUrl/resolvedModel` 空值回落服务默认；
  `LEGACY_BASE_URL` 兼容逻辑（历史错误默认地址视同未设置）**必须保留**。

### 2.4 现状问题清单（已逐行分析，直接照此动手）

**`ai/AiBookkeepingService.kt`（239 行）**
- `parseBill` 与 `parseBillFromImage` 有约 60 行重复：okhttp 请求构造、`choices[0].message.content` 提取、
  markdown 代码块剥离（` ```json `）、`BillResult` 组装——两份几乎一样。
- `(amountYuan * 100).toLong()` 浮点 bug（19.90→1989 分）**两处都存在**，与 parser 已修的同源。
- `ruleBasedParse`（无 key 兜底）弱且与 `PaymentNotificationParser` 的金额/词表重复不互通；
  其独有的价值只有**自然语言→分类关键词映射**（饭/餐→餐饮、地铁/滴滴→交通……）。
- `parseBill` 不知道通知规则的存在：用户在输入框**粘贴一条支付通知/银行短信**时，明明移植规则能精确解析，
  却走了「AI 或弱兜底」。

**`ui/ai/AiBookkeepingScreen.kt`（468 行）**
- 「AI 解析」按钮与语音识别回调里**两段几乎相同的解析启动代码**（`isParsing=true; scope.launch{…}` 同构，
  第三处 OCR 也有半个）；应收敛为一个 `parseText(text)`。
- 结果卡类型只映射 `income/expense`，`BillResult.type` 还有 `"transfer"`（AI 会返回）未展示。
- 这些是好的，**保留**：本地优先 OCR + 云端兜底、EXIF 旋转、`encodeForOcr` 1280 降采样、
  识别来源隐私标注（本地识别·图片未离开手机 / 云端识别）、窄屏 compact 判断、示例提示。

**`ui/mine/AutoBookkeepingScreen.kt`（310 行）**
- `AutoEntryCard` 与 `StatusCard` 两个近似卡片组件（Row + 标题 + 副文案 + ChevronForward），可合一。
- 缺 AutoAccounting 的**关键词过滤闸门**——通知解析前没有用户可控的白/黑名单。
- 缺**支持来源透明度**：24 条规则覆盖了哪些 App 用户不可见。
- 无障碍 / Shizuku（五态机 + 授权弹窗）/ 悬浮窗三张状态卡 + ON_RESUME 刷新 + 截屏确认弹窗逻辑完善，**保留**。

---

## 三、重构方案（按序执行）

### A. 统一解析管线：`ai/AiBookkeepingService.kt`

1. **抽共享助手**（私有即可）：
   ```kotlin
   // 组装 chat/completions 请求 → 同步执行 → 剥离 markdown 围栏 → 返回 content 原文；失败返回 null
   private fun postChat(url: String, apiKey: String, body: JSONObject): String?
   private fun billFromJson(content: String, fallbackNote: String): BillResult?  // 含 tags 解析
   ```
   `parseBill`（文本 messages）与 `parseBillFromImage`（vision 多模态 content）都改为调它；
   响应解析/围栏剥离/`BillResult` 组装只写一遍。
2. **三级管线**（AutoAccounting 同构，`parseBill` 内）：
   - ① `PaymentNotificationParser.parse(null, text)` 命中（金额>0）→ 直接转 `BillResult`
     （type/amountCents/accountHint 来自规则；categoryHint 给「其他」；note 用原文；tags 空）。
     粘贴通知/短信即可精确解析，不联网。
   - ② 未命中且 `AiConfig.isEnabled && apiKey` 非空 → 走 AI（现逻辑）。
   - ③ 否则/异常 → 自然语言关键词兜底（`ruleBasedParse` 精简保留或抽出 `ai/LocalTextParser.kt`，
     复用其分类关键词表；金额正则放宽支持「块」）。
3. **修浮点**：`(amountYuan * 100).toLong()` → `Math.round(amountYuan * 100)`（两处）。
4. `parseBillFromImage` 的模型名现硬编码 `glm-4v-flash`，改为 `AiConfig.resolvedModel` 同源
   （自定义服务用户配的是自己的 vision 模型；智谱默认值可在 providers 里加 `defaultVisionModel` 或直接沿用 model）。

### B. 精简：`ui/ai/AiBookkeepingScreen.kt`

1. 收敛解析启动点为一个局部函数 `parseText(text: String)`（置 isParsing、清 errorMsg、launch、
   统一结果/失败文案）；「AI 解析」按钮与语音回调都调它。语音回调里那段解析代码删掉。
2. 类型行支持转账：`when (bill.type) { "income" -> "收入"/IncomeGreen; "transfer" -> "转账"/TextSecondary; else -> "支出"/MiuixPrimary }`。
3. 预期净减 60~80 行；不改 UI 结构与文案风格。

### C. 完善：`ui/mine/AutoBookkeepingScreen.kt` + 新增过滤闸门

1. **新增 `accessibility/PaymentNotificationFilter.kt`**（纯 JVM，照 `AnalysisUtils` 精简）：
   ```kotlin
   object PaymentNotificationFilter {
       const val DEFAULT_WHITELIST = "银行,信用卡,借记卡,公积金,元,￥,¥,人民币,消费,支付,支出,转出,取出,取款,收入,转入,存入,存款,退款,还款,贷款,借款,逾期,转账,账户,余额,交易,动账,账单"
       /** 任一白名单词命中才放行；空名单=全放行（本仓库语义，与 AutoAccounting 不同：它空名单全拦） */
       fun allowed(text: String, whitelist: String, blacklist: String): Boolean
   }
   ```
   `PaymentNotifyAccessibilityService.onAccessibilityEvent` 在 `parse` 之前调用；名单经
   `SettingsRepository.getString(context, "payment_filter_whitelist"/"payment_filter_blacklist", DEFAULT_WHITELIST/"")`
   读取（key 命名跟 `AiConfig` 现有风格）。
2. **页面合并双卡片组件**：`EntryCard(title, subtitle, onClick, status: String?=null, active: Boolean=false)`，
   三个入口卡与三张状态卡共用。
3. **新增「通知过滤关键词」卡**（放「支付通知自动记账」卡下方）：点开弹编辑对话框（白名单多行 +
   黑名单多行 + 「恢复默认」），保存写 SettingsRepository；说明文案讲清楚「命中任一白名单词、且不含黑名单词的通知才会识别」。
   编辑 UI 参考本页已有的 `IjiDialog` 用法。
4. **新增「已支持识别的来源」静态卡或折叠说明**：列表 = 微信支付、支付宝（花呗/网商银行）、云闪付、
   招商银行、掌上生活、工商银行、农业银行、中信银行、美团月付、小米智能卡、数字人民币、广西北部湾银行、
   OPPO/一加钱包 +「其他含金额与收支语义的通知走通用识别」。建议由 `PaymentNotificationParser`
   暴露 `val supportedSources: List<String>`（从 APP_RULES 的 name 提炼），UI 只读，避免两处维护。
5. Shizuku / 无障碍 / 悬浮窗 / 截屏确认弹窗 / ON_RESUME 刷新逻辑**原样保留**。

### D. 测试与文档

- 新增 `PaymentNotificationFilterTest`：白名单命中/未命中、黑名单优先、空名单放行、默认名单与 parser
  28 用例的通知文案兼容（即：默认过滤不拦截现有规则场景）。
- 管线①可测性：若①内联在 `parseBill`（含 Context/网络）不好测，可在 parser 或 service 里加纯函数
  `billResultOf(parsed: PaymentNotificationParser.Parsed): BillResult` 单测它。
- `AiBookkeepingService` 的浮点修复不必专门单测（私有），但若抽了纯函数就顺手测。
- 跑完更新 `HANDOFF.md` 2.3 节的 `(131 tests / 10 classes)` 计数。

---

## 四、验收标准

1. `.\gradlew.bat :app:assembleDebug --console=plain` 通过（JAVA_HOME 指向 AS 自带 jbr）。
2. `tools/run_unit_tests.ps1` 全绿（用例数只会增加），`HANDOFF.md` 计数已同步。
3. 行为不回退：
   - 粘贴「微信支付凭证已支付¥48.10」类文案 → 不配 key 也能出精确结果（走规则）；
   - 「今天午饭花了35块」类自然语言 → 未配 key 走本地兜底出金额+分类；
   - 通知过滤默认白名单不拦截现有 28 个规则用例对应的文案；
   - AI 解析（文本/图片/语音）、连接测试、多厂商配置全链路不变。
4. 代码量净减或持平（`AiBookkeepingScreen` 与 `AutoBookkeepingScreen` 合计应减少，
   新增的过滤/来源功能计入后总行数不应显著膨胀）。

## 五、注意事项（红线）

- **宁可漏判不误判**：解析链路的任何改动不得放宽到「猜」；验证码/中奖黑名单、金额上限、余额排除不可删。
- 通知无障碍服务三自律（只读不模拟点击、先确认后落库、60s 去重）不可破坏。
- `BillResult` 字段与 `PaymentBillStore` 落盘 JSON 的兼容（老版本落盘的 pending bill 还会被新代码 consume）。
- UI 遵循 Miuix 现有节奏：卡片 `Radius.xxl`、辅助文字 `TextSecondary`、主色 `MiuixPrimary`、
  收入 `IncomeGreen`；新弹窗用现成 `IjiDialog`。
- 不新增第三方依赖（`run_unit_tests.ps1` 的 classpath 不用动）；不改数据库。

---

## 六、落地结果（2026-10-28 收口）

### 6.1 实际改动

| 文件 | 结果 |
|---|---|
| `ai/AiBookkeepingService.kt` | 抽出 `postChat`（请求→`choices[0].message.content`）与 `billFromJson`（剥围栏→`BillResult`），文本/图片两条路径共用；`parseBill` 改为三级管线（`PaymentNotificationParser.parse(null, text)` → `AiConfig.aiReady` 时调 AI → `ruleBasedParse`）；新增公开纯函数 `billResultOf(parsed, note)`；两处 `(amount*100).toLong()` 改 `Math.round(...)`；图片识别模型改 `AiConfig.resolvedModel`；**识别方式下沉**：`parseBill`/`parseBillFromImage` 的 `useAi` 改 `Boolean?`，null 时读 `AiConfig.parseMode`（默认本地、未配 Key 强制本地），新增纯函数 `useAiFor` |
| `ui/ai/AiBookkeepingScreen.kt` | 「AI 解析」按钮与语音回调统一走 `parseText(text)`；结果卡支持 `transfer`（转账 / `TextSecondary`）；保留本地优先 OCR、EXIF 旋转、1280 降采样、来源隐私标注、compact 宽度判断；新增「识别方式」本地/AI 切换（未配 Key 时不可切换，不留必然失败的选项） |
| `ui/mine/AutoBookkeepingScreen.kt` | `AutoEntryCard` + `StatusCard` 合并为 `EntryCard(title, subtitle, onClick, status, active)`；新增「通知过滤关键词」卡（白/黑名单 + 恢复默认，写 `SettingsRepository`）与「已支持识别的来源」卡（读 `PaymentNotificationParser.supportedSources`，UI 只读、不重复维护清单）；Shizuku/无障碍/悬浮窗/ON_RESUME 逻辑原样 |
| 新增 `accessibility/PaymentNotificationFilter.kt` | 纯 JVM 闸门：黑名单优先 → 白名单空则放行 → 否则须命中白名单；`KEY_WHITELIST`/`KEY_BLACKLIST`、`DEFAULT_WHITELIST`（与 AutoAccounting `DATA_FILTER` 对齐，多一个「储蓄卡」）、`wordCount` 供界面计数 |
| `accessibility/PaymentNotifyAccessibilityService.kt` | `parse` 前按配置过滤（每次事件读 DataStore，名单改了立刻生效，不需要重启服务） |
| 单测 | 新增 `PaymentNotificationFilterTest`（7 例）、`AiBookkeepingServiceTest`（2 例）、`AiParseModeTest`（8 例）；加上 `PaymentNotificationParserTest` 28 例 |

**第二轮精简（同日，按「功能完善、不冗余、代码精简」再过一遍）**

- `ui/ai/AiBookkeepingScreen.kt`：**500 → 362 行（-28%）**。四个动作按钮的重复模板
  （weight + 焦点圈 + `Icon` + `Spacer` + `Text` ×4）收成一个 `RowScope.ActionButton`；
  70 行内联结果卡抽成 `ParseResultCard`；示例列表提为常量 `SAMPLE_INPUTS`；
  `encodeForOcr` 从主线程挪到 `Dispatchers.Default`（4000px 原图不再卡一帧）。
- 新增 `ui/ai/AiBookkeepingComponents.kt`（189 行）：结果卡 / 动作按钮 / 空态示例 / `encodeForOcr` / 来源常量，
  页面文件只留状态机与布局骨架。
- `ui/mine/AutoBookkeepingScreen.kt`：三处重复的「刷新系统开关」合成 `refreshSystemSwitches()`；
  `savedWhitelist` + `savedBlacklist` 合成一次读取的 `savedFilter()`；过滤关键词输入框**单行改多行**
  （默认 30 个词，单行根本看不清改的是哪个）；支付通知 / Shizuku / 悬浮窗 / AI 配置四张卡的副文案压缩。
- `AiBookkeepingService.parseBill`：规则路径的备注由「通知原文」改为**商户名**（无商户才截原文 40 字）——
  原行为会让确认后的账单备注塞进整条短信。
- 行数口径：两个页面文件合计 **863 → 734**；算上新增组件文件（189）为 923。减的是重复模板与内联大块，
  增的是拆文件的头部与说明——判断标准是「同一处逻辑只写一遍」。

### 6.2 验证

- `:app:compileDebugKotlin` / `:app:compileDebugUnitTestKotlin`：无 `e:`，这几个文件无 `w:`。
- `tools/run_unit_tests.ps1`：**OK (148 tests / 13 classes)**。
- `:app:assembleDebug`：**BUILD SUCCESSFUL**。
- 模拟器（emulator-5554，1280×2856）UI 冒烟已过，明细见 6.4；**真机 + 深色/大字号尚未走查**，
  已并入 `HANDOFF.md` 第 8 节待办。

### 6.3 有意未做（评估过，不做更合理）

- **`AiSettingsScreen.testConnection` 不复用 `postChat`**：连接测试刻意用 10s/15s 短超时与 `max_tokens=5`，
  复用业务请求的 30s/60s 会让「配错了」的反馈慢得多；它也不解析 `BillResult`，共用收益只剩 6 行样板。
- **`ruleBasedParse` 不抽成 `ai/LocalTextParser.kt`**：只有一个调用点，抽出去只是多一层跳转；等出现第二个
  调用方（例如截屏导入也走它）再抽。
- **`supportedSources` 用 `Map<包名, 展示名>` 而非 `List<String>`**：来源卡要展示可读名字，规则与展示同源，
  按包名索引还能顺手查「这个包有没有专属规则」。
- **两个页面的行数没有净减**：重复逻辑（两段解析启动、两套请求构造、两个卡片组件）确实消掉了，
  但同期加了识别方式切换、过滤弹窗、来源清单与说明文字，总量基本持平——按第 4 节第 4 条「不显著膨胀」验收，不是「必须变短」。

### 6.4 模拟器走查记录（2026-10-28）

路径：启动 → 右上角头像进「设置」→ 自动化 · 自动记账 / AI 记账。坐标系 1280×2856。

| 检查项 | 结果 |
|---|---|
| 自动记账页 8 张卡 | 全部渲染，`EntryCard` 合一后标题/状态/副文案/箭头对齐一致；过滤卡显示「白 30 · 黑 0」，来源卡「13 个」 |
| 过滤关键词弹窗 | 白名单带默认 30 词、黑名单空；键盘弹出后弹窗整体上移、保存按钮可达 |
| 写入 → 计数 | 黑名单输入 `test` → 保存 →「白 30 · 黑 1」+ Snackbar「已保存…」 |
| 重启后持久化 | `force-stop` 再进页，仍显示「黑 1」（DataStore 生效） |
| 清空 → 恢复 | 清空黑名单保存后回到「黑 0」，证明配置可清除 |
| 来源清单弹窗 | 13 项与 `supportedSources` 一一对应，含「通用兜底」说明 |
| AI 记账页 | 「识别方式」行（未配 Key → 「本地识别」且不可切换）、四按钮、示例提示正常 |
| 解析失败反馈 | 输入 `35`（无「元/块」）点本地解析 → 红字「未能识别金额，请换种说法试试」 |
| 未能覆盖 | 中文输入无法用 `adb input text` 注入，**管线①（粘贴通知走规则）只由单测覆盖**；转账结果卡需 AI 或规则返回 transfer，模拟器上无法构造 |

第二轮精简后复走（同日晚）：

| 检查项 | 结果 |
|---|---|
| AI 记账页 | 四按钮（图标 + 文案，窄屏阈值不变）、识别方式行、示例列表渲染与重构前一致 |
| 自动记账页 | 8 张卡文案压缩后无截断/溢出；过滤卡「白 30 · 黑 0」、来源卡「13 个」不变 |
| 过滤关键词弹窗 | 白名单框改为多行：30 个词显示 4 行可滚动，黑名单空框 72dp，弹窗不溢出；点「保存」→ 关闭 + Snackbar「已保存：白名单 30 词，黑名单 0 词」，卡片状态不变（写盘路径未回退） |

---

## 七、第三轮：页面重整合并（2026-10-28）

参照对象与取舍：

| 参考 | 取什么 | 不取什么 |
|---|---|---|
| AutoAccounting `AIAssistantPreferenceFragment` | AI 侧收口：配置入口 + 提示词都在 AI 记账这条线上 | 月度总结 / 资产映射 / 分类识别等我们没有的功能，不造假开关 |
| AutoAccounting `AiComponent` | 服务商 / Token / URL / 模型 + 测试连接 | 已由 `AiSettingsScreen` 覆盖，不改 |
| AutoAccounting `RecordingPreferenceFragment` | 「记账设置」按用途分组、一项一行的观感 | 延迟同步阈值 / 自动分组（我们没有第三方同步） |
| Hyper-pick-up-code `MiuixRulesScreen` / `SimpleRuleCenterContent` | Miuix 卡片 + chip 的规则页布局 | — |
| Hyper-pick-up-code `VolumeShortcutAccessibilityService` | **音量键快捷键 = 系统按需启动无障碍服务**，`onServiceConnected` 干活后 `disableSelf()` | `canRequestFilterKeyEvents` 常驻按键过滤（权限面太大） |

### 7.1 新信息架构

| 页面 | 路由 | 内容 |
|---|---|---|
| 自动记账 | `auto_bookkeeping` | 说明 + **识屏记账**（状态：Shizuku / 无障碍 / 未授权）+ **支付通知记账**（状态：已开启 / 未开启） |
| 识屏与授权 | `screen_capture`（新） | 授权方式 chips（自动 / Shizuku / 无障碍）、音量键快捷开关、悬浮窗确认、立即识屏 |
| 通知识别规则 | `notification_rules`（新） | 无障碍状态 + 白/黑名单多行编辑 + 支持来源（13 项）列表 |
| AI 记账 | `ai` | 识别方式 + 「AI 服务：模型名 / 未配置」+「AI 服务」「提示词」入口 + 输入/取图/语音 + 结果卡 |
| AI 设置 | `ai_settings` | 不变（厂商 / Key / URL / 模型 / 测试连接） |
| 提示词管理 | `prompts`（新，设置 → 自动化） | 两段 system prompt 整页编辑：Chips 切换、默认/自定义状态、恢复默认、保存 |

### 7.2 行为变化

- 规则/示例提示词不再硬编码在 `AiBookkeepingService`：默认值移到 `AiConfig.DEFAULT_PROMPT_TEXT/IMAGE`，
  用户在提示词页改的存 DataStore（空 = 默认，与默认逐字相同也存空）。
- 自动记账页不再直接弹窗：过滤关键词、支持来源都改成整页（`IjiDialog` 只留「立即识屏」的确认说明）。
- 音量键快捷识屏：`CapturePreferences.resolveMode` 决定这次用 Shizuku 还是无障碍；
  两种都不可用时**打开应用**让用户授权，不静默失败。延迟 1.6s 等音量条淡出。
  该能力**未在模拟器上实测**（模拟器绑不了系统音量键快捷键），需真机走查。

### 7.3 验证

- 编译无 `e:`；`:app:assembleDebug` SUCCESSFUL；单测 **157 tests / 15 classes** 全绿（新增 `CapturePreferencesTest` 5 例、`AiPromptTest` 4 例）。
- 模拟器走查：提示词页（说明卡 / chips / 默认态 / 320dp 编辑框 / 保存禁用态）、自动记账页（2 卡 + 状态点）、
  识屏与授权页（chips + 红字提示 + 两个开关 + 立即识屏禁用）、通知规则页（多行白名单 / 保存 / 恢复默认 / 来源 13 项）、
  AI 记账页（AI 服务行 + 两个胶囊入口，点「提示词」正确跳转）。
- 未覆盖：音量键快捷键、Shizuku 授权、无障碍截屏实截 —— 需真机。

### 7.4 记账偏好（对照 AutoAccounting `settings_recording.xml` / `Setting.kt`）

只落**有数据模型支撑**的项；没有对应字段的一律不做空开关。

| AutoAccounting 设置键 | 我们的处理 |
|---|---|
| `AUTO_GROUP` / `AUTO_GROUP_TIME_THRESHOLD` | ✅ 重复通知合并窗口可配（30/60/120/300 秒），服务改读设置（原 `DEDUP_WINDOW_MS` 常量删除） |
| `NOTE_FORMAT` | ✅ 备注模板：`{商户}` / `{来源}`，带实时预览；`PaymentBillStore.consume` 生成备注 |
| `DEFAULT_BOOK_NAME` | ✅ 落账账本：识屏悬浮窗与通知确认都按此落账，留空 = 跟随当前账本 |
| `AI_BILL_RECOGNITION` / `AI_VISION_RECOGNITION` | ✅ 已有「识别方式」+ 图片视觉识别 |
| `AI_CATEGORY_RECOGNITION` | ✅ AI 返回 `category` 已直接使用 |
| `OCR_AUTH_MODE` | ✅ 识屏与授权页的授权方式 chips |
| `SETTING_TAG` / 资产 / 多币种 / 报销 / 债务 | ✅ 这些功能 iji 本来就有（标签、资产、多币种、应收垫付） |
| `AUTO_RECORD_BILL` | 不重复造：等价物是系统层的「无障碍服务是否开启」 |
| `AUTO_TRANSFER_RECOGNITION` | 解析器已能出 `transfer` 类型，不需要额外开关 |
| `SHOW_RULE_NAME` | 暂不做：需要 `Parsed` 带规则名并改通知文案，留作后续透明度项 |
| `SETTING_FEE` / `BILL_FLAG_NOT_COUNT` / `BILL_FLAG_NOT_BUDGET` | 账单表没有手续费 / 不计收支 / 不计预算字段，不做 |
| `AUTO_CREATE_CATEGORY` | 不做：自动往用户分类树里塞节点，容易污染 |
| `AUTO_ASSET_MAPPING` / `AI_ASSET_MAPPING` | 不做：通知里只有 `accountHint` 字符串，没有「资产映射」概念 |
| `BOOK_APP_ID` / `MANUAL_SYNC` / `DELAYED_SYNC_THRESHOLD` | 不做：iji 是本地记账，没有第三方记账软件同步 |
| `OCR_BACK_TAP_TRIGGER` / `OCR_ACCESSIBILITY_AUTO_TRIGGER` / `OCR_SHOW_ANIMATION` / `OCR_ACCESSIBILITY_KEEP_ALIVE` | 不做：无背部手势、无页面切换自动触发；动画违背「性能优先」；服务常驻由系统决定 |
| `FLOAT_*`（位置 / 超时 / 点击事件） | 悬浮窗确认已有；位置与超时不做，避免多一层配置 |

验证：165 tests / 16 classes 全绿；模拟器走查记账偏好页（4 个账本 chip、备注模板改为 `{商户}X` 预览即时变「瑞幸咖啡X」、
切「旅行」后返回自动记账页状态同步为「旅行」，再切回「跟随当前账本」状态同步刷新）。

---

## 八、AI 记账页重写（2026-10-29）

对象：AutoAccounting `res/xml/settings_ai_assistant.xml`（三段：AI 配置 / AI 功能 / 提示词管理）。

### 8.1 功能映射

| AutoAccounting | iji 的处理 |
|---|---|
| AI 配置（跳子页：服务商 / Key / URL / 模型 / 测试连接） | **内嵌**为折叠区块 `ui/ai/AiServiceSection.kt`，不再有独立设置页 |
| `featureAiAvailable` 总开关 | AI 状态卡的开关（即时保存） |
| `aiBillRecognition` | 「文本识别用 AI」开关（原「识别方式」滚轮换成开关） |
| `aiVisionRecognition` | 「图片识别用 AI」开关；关闭时 `parseBillFromImage` 直接返回 null，图片不出设备 |
| `aiCategoryRecognition` | 「AI 识别分类」开关；关闭时 `categoryHintFor` 强制落「其他」 |
| `aiAssetMapping` | 不做：iji 没有「资产映射」数据模型 |
| `aiMonthlySummary` | 不做：没有月度总结能力 |
| 提示词 4 条（账单 / 资产 / 分类 / 总结） | 2 条（账单识别、小票识别），仍是独立整页 `prompts` |

### 8.2 新页面结构

```
AI 状态卡：AI 识别 [开关] · 状态文案 · 配置/收起
  └ 折叠：模型服务（6 家网格）/ 接口凭据 / 接口参数 / 保存配置 / 连接测试 / 提示词入口
记账区：小票预览 · 解析中 · 输入框 · 拍照/相册 · 语音/解析 · 错误 · 结果卡
AI 功能：文本识别用 AI / 图片识别用 AI / AI 识别分类 / 提示词管理
空态示例（无结果时）
```

### 8.3 删除的结构

- `ui/ai/AiSettingsScreen.kt` 整文件（原独立设置页）
- `Routes.AI_SETTINGS` 常量、ImmersiveRoutes 项、`ijiRoute` 注册、`onNavigateAiSettings` 参数
- 「识别方式」滚轮弹窗（`WheelOptionDialog` + `showModePicker`）
- 顶层 `Scaffold + TopAppBar`，统一改 `MineSubPage`（与其它子页一致）

### 8.4 验证

- 编译无 `e:`；`assembleDebug` SUCCESSFUL；**167 tests / 16 classes** 全绿
  （新增 `categoryHintFor` 2 例）。
- 模拟器走查：默认收起态（状态卡 + 记账区 + 3 个开关 + 示例）；点「配置」展开服务商网格
  （智谱 GLM 选中、6 家含自定义）/ Key 眼睛 / 模型与地址回填；地址两行完整显示、
  「保存配置」未改动时禁用、Key 空时「测试连接」禁用并提示；点「提示词管理」正确跳转；
  关总开关后状态变「已关闭 · 只用本地规则与本地 OCR，不联网」，再开恢复。
- 未覆盖：真实 Key 下的解析/测试连接（无 Key），与上一轮真机遗留项合并走查。

### 8.5 顶栏对齐修复（2026-10-29）

改 `MineSubPage` 时删掉了 `Routes.AI_SETTINGS` 的 ImmersiveRoutes 项，却没有把 `Routes.AI` 加进去，
于是 AI 记账页被 `ijiRoute` 在外层**又垫了一次安全区**：整条顶栏连同内容一起比其它子页低一个状态栏高度
（实测 1280×2856@3x：返回图标中心 y=390px，而「设置」页同为子页时是 234px，差 156px = 52dp）。
同时子页顶栏（`backdrop = null` 的纯色实底）取的是 Miuix `background`（#FFFFFF），页面底色是 M3
`background`（#F5F6F8），两者不同 → 状态栏下方留出一条**悬空的白带**，视觉上就是「顶栏错位」。

| 改动 | 文件 |
|---|---|
| `Routes.AI` 补登记 `ImmersiveRoutes` | `ui/AppRoot.kt` |
| 纯色顶栏底色改取页面底色（M3 `background`），浅/深色都与页面同色 | `ui/components/IjiTopBar.kt` |
| 居中标题按左右操作区实测宽度收紧 `maxWidth`（`softWrap = false` + Ellipsis），长标题不再压到返回/操作按钮 | `ui/components/topbar/CollapsibleTopBar.kt` |

对齐口径（此后新增子页照此执行，别再引入第二个约定）：**返回在左（距边 16dp，图标中心 36dp）、
标题在全栏水平居中并与栏中心垂直居中（折叠态 52dp 的 y 中心）、操作项在右（距边 16dp）**；
三者垂直中心必须落在同一条线上，由 `CollapsibleTopBar` 统一摆放，页面只提供内容。

验证（模拟器 1280×2856，`uiautomator dump` 取 bounds）：

- AI 记账页：顶栏实底 `[0,0][1280,312]`；返回图标 `[75,201][141,267]`、标题 `[545,193][734,275]`
  → 水平中心 640（= 屏宽中点）、垂直中心同为 **234**；首卡顶端 y=348（栏底 312 + `cardGap` 12dp）。与「设置」页一致。
- 顶栏/页面像素采样：浅色 `#F5F6F8`、深色 `#0E0F12`，栏内外同色，接缝消失。
- 提示词管理（带右侧「保存」）：返回 / 标题 / 保存 三者中心 y 同为 234。
- 320dp 窄屏（`wm size 960x2142`）：标题仍居中、不换行、不压住右侧操作；日历页折叠态小标题同样正常。

---

## 九、第六轮：AI 提示词与本地识别规则（2026-10-29）

参照 AutoAccounting 的「提示词管理」（`AIAssistantPreferenceFragment` + `settings_ai_assistant.xml` 的四条提示词）
与 Hyper-pick-up-code 的规则页（`MiuixRulesScreen` / `SimpleRuleCenterContent`：小节标题 + 分组卡片 +
「标题 / 状态摘要 / 右箭头」一行一个入口，点进去是二级页）。

### 9.1 取了什么 / 不取什么

| 参考项 | 取什么 | 不取什么（原因） |
|---|---|---|
| `AI_BILL_RECOGNITION_PROMPT` | 账单识别（文本 / 小票）两段，默认值逐字沿用旧版 | — |
| `AI_CATEGORY_RECOGNITION_PROMPT` | **从用户真实分类里挑一个**的二次映射阶段 | 那边的「根据分类映射表批量改写历史账单」——我们没有批量改分类的产品面 |
| `AI_ASSET_MAPPING_PROMPT` | 账户名对齐（把「微信」对到已有账户） | 「资产映射」（账单 ↔ 资产标的的对应关系），iji 没有资产标的 |
| `AI_SUMMARY_PROMPT` | 「AI 摘要」：给当期汇总写一段中文点评 | 那边的 32 字段报告 JSON + WebView 报告页（雷达图/词云/健康分），我们没有渲染面，抄字段只会得到"生成了没人看" |
| `SimpleRulePack`（关键词→分类） | 本地识别规则：可增删改、可导出导入、可恢复内置 | 那边的正则/定位词/JS 扩展点（权限面与复杂度都不划算） |

### 9.2 五类提示词与生效点（都必须真有调用点，否则就是假开关）

| 种类 | 存储键 | 生效点 |
|---|---|---|
| 账单识别（文本） | `ai_prompt_bill_text` | `AiBookkeepingService.requestAi`（「文本识别用 AI」开 + 已配 Key） |
| 小票识别（图片） | `ai_prompt_bill_image` | `parseBillFromImage`（本机 OCR 认不出金额 + 「图片识别用 AI」开） |
| 分类识别 | `ai_prompt_category` | `enrich()`：模型给的分类不在分类表里时补一次 |
| 账户识别 | `ai_prompt_account` | `enrich()`：线索对不上已有账户名时补一次（「账户识别用 AI」开） |
| AI 摘要 | `ai_prompt_summary` | 统计页「AI 小结」（`AiSummaryDialog`） |

旧键 `ai_prompt_text` / `ai_prompt_image` 仍会被 `AiConfig.getPromptCustom` 回退读取，首次保存时迁到新键并清空旧键。

### 9.3 本地识别规则

- 数据：`LocalRule(id,name,type,category,account,keywords,enabled,builtin)`；内置 13 条与旧硬编码分类逐条对齐，
  另补教育 / 人情 / 旅行（旧版这些词一律落「其他」）。
- 命中口径（`LocalRuleEngine.match`）：命中词数多者胜 → 最长命中词更长者胜 → **用户规则优先于内置** → 配置顺序。
  收支方向由收入语义词（工资/收入/红包/报销/理财/到账/入账）决定；**「退款」不算收入**（iji 的退款是负数支出）。
- 存储：SharedPreferences + JSON（同 `CategoryStore`），不新增表、不动数据库版本；`IjiApplication` 里 init，
  本机 OCR 在拿不到 Context 的地方也能同步读。
- 管线：`AiBookkeepingService.localRuleParse(text, rules)` 是**纯函数**（金额正则 + 引擎），
  单测逐条锁住分类/方向/金额精度；规则原先是 `ruleBasedParse` 里的硬编码 `when(regex)`，已删除。
- 本机 OCR（`LocalOcrEngine`）：先走用户规则，未命中再退回小票专用词表 —— 改词表对图片识别同样生效。
- 规则数据：导出 JSON / 导入（整表替换，必确认）/ 恢复内置（**只补缺失项**，用户改动与自建规则保留）。

### 9.4 页面

| 页面 | 路由 | 内容 |
|---|---|---|
| 提示词与规则 | `prompts` | 说明卡 + 「提示词」5 行 + 「本地识别规则」13 行 + 新增 + 「规则数据」3 行；行 = 标题 + 状态摘要 + `ChevronForward` |
| 提示词编辑 | `prompt_edit/{kind}` | 顶部说明（用途 / 生效条件 / 留空=默认）+ 当前默认/自定义 + 恢复默认 + 320dp 多行编辑 + 保存 |
| 规则编辑 | `local_rule_edit/{ruleId}` | 名称 / 收支类型 chips / 分类（**单选弹窗**，几十个二级分类铺胶囊会占满整屏）/ 关键词多行 / 预填账户 / 启用 / 删除 |

`AI_PROMPTS` 与两个二级路由都已登记 `ImmersiveRoutes`（见 8.5 的 52dp 双重安全区坑）。
旧 `ui/ai/PromptManageScreen.kt` 已删除；设置页入口与 AI 记账页入口文案统一为「提示词与规则」。

### 9.5 验证

- 单测：`LocalRuleEngineTest` 15 例、`LocalRecognitionPipelineTest` 8 例、`AiPromptRegistryTest` 6 例、
  `AiSummaryPayloadTest` 5 例 —— 合计 **201 tests / 20 classes** 全绿。
- 编译：`compileDebugKotlin` / `compileDebugUnitTestKotlin` / `assembleDebug` 均无 `e:`、无新增 `w:`。
- 模拟器（1280×2856）走查：规则中心三段渲染、提示词编辑（默认 → 自定义 → 保存 → 列表摘要同步 → 恢复默认）、
  规则编辑（内置规则回填 / 分类弹窗选中 / 新增→保存→列表出现 / 删除确认→列表消失）、
  规则中心顶部说明卡与行摘要（无账户时不再出现「账户 —」）、AI 记账页「账户识别用 AI」开关与入口、
  统计页「AI 小结」（未配 Key → 明确文案 + 重试）。
- 未覆盖：真实 Key 下的分类/账户二次映射与摘要正文（本机无 Key），与 6.4 的中文注入限制一并留待真机。

---

## 十、识屏过程态上岛（2026-10-30）

参照 [SiberiaApp/Maling-Island](https://github.com/SiberiaApp/Maling-Island)（码灵属，HyperOS 取码上岛 App）的
「正在识别」实现：识别请求一发起就发一条 **进行中** 的超级岛，识别结束整条撤下。

### 10.1 为什么要它

截屏识别的整条链路（3 秒倒计时 → 截屏 → 本机 OCR）都发生在**用户已经切到微信/支付宝**的时刻，
App 界面不在前台，原来这段时间没有任何反馈：用户不知道截了没有、识别卡住没有。岛是唯一看得见的进度。

### 10.2 协议代数的坑（这次的真根因）

第一次实现沿用了项目原有的 `protocol: 1` 模板，真机（23127PN0CC / HyperOS OS3.0 / focusProtocol=3）上
**建出了岛事件但不浮出**。logcat 证据：

```
D/DynamicIslandEventCoordinator: 当前事件: DynamicIslandEvent$UpdateDynamicIsland ...
    key:0|com.iji.app|9104|null|10598, tickerData:{"islandProperty":1,"islandTimeout":120,...}
```

系统收到了、也解析了 `tickerData`，屏幕上什么都没有。换成 Maling-Island 的 **协议 3** 模板后立刻浮出
（截图为「准备识屏 / 马上自动截屏，请把账单页露出来」的大岛卡片）。

协议 3 与协议 1 的差别（都写进 `islandParamsJsonV3` 的注释）：

| 字段 | 协议 1（老设备） | 协议 3（HyperOS 2/3） |
|---|---|---|
| 顶层 | `protocol: 1` | `protocol: 3` + `ticker` / `tickerPic` / `isShowNotification` / `islandFirstFloat` / `timeout` / `aodTitle` / `aodPic` |
| 大岛 | `bigIslandArea.templateNo = 2` + `imageTextInfoLeft.textInfo` + `textInfo` | `bigIslandArea.imageTextInfoLeft`(图标) + `imageTextInfoRight`(文字)，**没有** templateNo |
| 底栏 | `hintInfo`（含「查看」按钮，actionIntent URI） | 无 hintInfo（按钮改由 shareData/actions 承载，本轮不做） |
| 图标键 | `pic_small` / `pic_app_icon` | `pic_app` / `pic_icon_main` / `pic_ticker` / `pic_aod` |

`sendIsland` 按 `islandLevel(context) >= 3` 自动选模板，两套图标键都塞进 `miui.focus.pics`（系统各取所需）。
副作用：协议 3 设备上的结果岛没有「查看」按钮，点岛仍走 `contentIntent` 回应用。

### 10.3 本次改动

| 文件 | 改动 |
|---|---|
| `reminder/SuperIslandNotifier.kt` | `sendIsland(ongoing, islandTimeoutSeconds)`：`setOngoing(true)` + `CATEGORY_PROGRESS` + `setTimeoutAfter` 兜底；新增 `islandParamsJsonV3()`（协议 3 模板）与 `cancel(id)`；`islandParamsJson` 的点击参数从 `Intent` 改为 `clickUri: String`（纯函数，可单测） |
| `budget/NotificationHelper.kt` | `showScreenshotProgress()` / `clearScreenshotProgress()`：开关 `island_screenshot_progress`（默认**开**）+ 通知权限 + 岛能力三重闸门；进度岛 id 9104 与结果通知 9102 分开；`sendScreenshotNotice(forceIsland)` 供「通知样式」模式显式走岛 |
| `accessibility/ScreenCaptureFlow.kt` | 倒计时开始发「准备识屏」→ 截到图刷「正在识别」→ `finally` 撤下；`OVERLAY_ISLAND` 分支改走 `report(forceIsland = true)`，不再借用「记账提醒」的岛 id（9002）互相顶掉 |
| `ui/mine/PreferencesScreen.kt` | 超级岛通知卡新增「识屏识别中上岛」（默认开）+ 原开关改名「截屏记账结果上岛」并改文案区分过程态/结果态 |

失败/无权限一律静默（`showScreenshotProgress` 返回 false，后续不再白刷文案）：识屏本身不依赖岛。

### 10.4 验证（真机 23127PN0CC / 1200×2670 / OS3.0）

- 开关打开 + 识屏：截图中确实浮出大岛「准备识屏 / 马上自动截屏，请把账单页露出来」，2 秒后被结果通知替换；
  logcat 有 `UpdateDynamicIsland` → `DeletedDynamicIsland`（收尾撤岛生效）。
- 开关关掉再识屏：logcat 中**没有任何** DynamicIsland 事件（闸门有效，不残留 9104）。
- 岛参数单测：`SuperIslandNotifierTest` 11 例（协议 1 / 协议 3 两套模板的关键字段）。
- 全量单测 **211 tests / 21 classes**；`compileDebugKotlin` / `assembleDebug` 无 `e:`。
- 未覆盖：结果岛在协议 3 下的实际渲染（本轮只验证了过程态；两者走同一个 `sendIsland` 与同一渠道）、
  非 HyperOS 设备（`islandLevel < 3` 时过程态直接跳过）。
