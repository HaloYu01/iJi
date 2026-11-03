# AI记账与自动记账页面重构进度记录

> 状态：**2026-10-28 已完成**。
> 权威交接文档：`HANDOFF.md` 第 8 节第 0 条 + `HANDOFF-AI记账与自动记账重构.md`（第六节为落地结果与走查记录）。
> 本文件只保留任务级勾选，细节与取舍不在此重复。

## 已完成工作

### 1. 规则移植与验证 ✅
- **PaymentNotificationParser.kt**：两层结构（26 条 App 专属规则 + 通用兜底），与 AutoAccounting v0.5.6 对齐
- **规则测试**：28 个用例，覆盖典型通知场景
- **浮点精度修复**：`cents()` 改字符串精确解析（19.90 → 1990 分）

### 2. A. 重构 AiBookkeepingService.kt ✅
- [x] 共享请求助手 `postChat()` / `billFromJson()`（文本与图片两条路径共用）
- [x] 三级解析管线：通知规则 → AI（配置可用才走）→ 本机关键词兜底
- [x] 浮点修复：两处 `(amount * 100).toLong()` 改 `Math.round(...)`
- [x] 图片识别模型改用 `AiConfig.resolvedModel`（不再硬编码 `glm-4v-flash`）

### 3. B. 精简 AiBookkeepingScreen.kt ✅
- [x] 「AI 解析」按钮与语音回调收敛为 `parseText(text)`
- [x] 结果卡支持 `transfer`（转账类型，`TextSecondary`）
- [x] 保留：本地优先 OCR + 云端兜底、EXIF 旋转、1280 降采样、来源隐私标注、窄屏 compact

### 4. C. 完善 AutoBookkeepingScreen.kt ✅
- [x] 双卡片组件合并为 `EntryCard(title, subtitle, onClick, status, active)`
- [x] 新增「通知过滤关键词」卡（白/黑名单 + 恢复默认）
- [x] 新增「已支持识别的来源」只读清单（读 `PaymentNotificationParser.supportedSources`）

### 5. D. 过滤组件与测试 ✅
- [x] `accessibility/PaymentNotificationFilter.kt`（纯 JVM；黑名单优先、白名单空=放行）
- [x] `PaymentNotificationFilterTest`（7 例）、`AiBookkeepingServiceTest`（2 例）
- [x] `HANDOFF.md` 测试计数同步

### 6. 识别方式：本地 / AI ✅
- [x] 默认本地（存储里无键即为 `MODE_LOCAL`）
- [x] 未配 AI（无 Key / 关总开关）**强制本地**，界面不给必然失败的选项
- [x] 识别方式**下沉到管线**：`parseBill` / `parseBillFromImage` 的 `useAi` 改 `Boolean?`，
      null 时读 `AiConfig.parseMode`；语音 / 文本 / 图片三入口不再各传布尔值
- [x] 纯函数 `AiConfig.resolveMode()` 与 `AiBookkeepingService.useAiFor()` + `AiParseModeTest`（8 例）

### 7. 第二轮精简（页面文件更薄）✅
- [x] AI 记账页 **500 → 362 行**：结果卡 / 动作按钮 / 空态示例 / 图片压缩拆到 `AiBookkeepingComponents.kt`（189 行）
- [x] 四个动作按钮的重复模板（weight + 焦点圈 + 图标 + 文案）合并为 `RowScope.ActionButton`
- [x] `encodeForOcr` 从主线程移到 `Dispatchers.Default`（4000px 原图不再卡一帧）
- [x] 自动记账页：三处刷新状态合一 `refreshSystemSwitches()`、两个读取函数合一 `savedFilter()`
- [x] 过滤关键词输入框**单行改多行**（默认 30 词单行看不清）+ 四张卡副文案压缩
- [x] 规则解析的备注改取**商户名**（无商户才截原文 40 字），不再把整条通知塞进账单备注

### 8. 页面重整合并（第三轮）✅
- [x] 自动记账页 8 张卡 → 2 个入口（识屏记账 / 支付通知记账）
- [x] 新页「识屏与授权」：授权方式 chips（自动 / Shizuku / 无障碍）+ 音量键开关 + 悬浮窗 + 立即识屏
- [x] 新页「通知识别规则」：无障碍状态 + 白/黑名单多行 + 支持来源，替代原两个弹窗
- [x] 新页「提示词管理」：设置 → 自动化，两段 system prompt 整页编辑（留空 = 默认）
- [x] AI 记账页：新增「AI 服务」与「提示词」入口，配置不再散落在自动记账页
- [x] 音量键快捷识屏：`VolumeKeyCaptureService` + xml + Manifest（按需启动、截完即退）
- [x] 提示词默认值下沉到 `AiConfig.DEFAULT_PROMPT_TEXT/IMAGE`，service 两处硬编码移除
- [x] 单测：`CapturePreferencesTest`（5）+ `AiPromptTest`（4）

### 9. 记账偏好（第四轮）✅
- [x] 新页「记账偏好」（自动记账第 3 个入口）：落账账本（含「跟随当前账本」）、备注模板 `{商户}`/`{来源}` + 预览、重复通知合并窗口
- [x] 落点：`ScreenCaptureFlow` 悬浮窗账本、`AppRoot` 通知确认前切账本、`PaymentBillStore` 备注模板、无障碍服务去重窗口
- [x] 改一项即时生效（无「保存」按钮）
- [x] 有意不做：手续费 / 债务 / 自动建分类 / 资产映射 / AI 月末总结 / 双击背部触发

### 10. AI 记账页重写（第五轮）✅
- [x] 删除 `AiSettingsScreen` 独立页与 `Routes.AI_SETTINGS`，配置内嵌为折叠区块（`AiServiceSection`）
- [x] 页面结构：AI 状态卡 → 记账区 → AI 功能开关 → 空态示例
- [x] 新增两个真实生效的开关：「图片识别用 AI」（关 = 图片不出设备）、「AI 识别分类」（关 = 落「其他」）
- [x] 「识别方式」滚轮 → 「文本识别用 AI」开关（未配 Key 时自动展开配置并提示）
- [x] 顶栏统一 `MineSubPage`
- [x] 单测：`categoryHintFor` 2 例

### 11. AI 记账页顶栏对齐修复 ✅（2026-10-29）
- [x] `Routes.AI` 补登记 `ImmersiveRoutes`：原先漏登记 → 外层再垫一次安全区，整条顶栏下移 52dp（实测 y 中心 390 vs 设置页 234）
- [x] 子页纯色顶栏底色改用页面底色（Miuix 白 #FFF → M3 #F5F6F8/#0E0F12），消除状态栏下方的悬空白带
- [x] 居中标题按左右操作区宽度收紧上限，长标题不再压到返回/操作按钮
- [x] 口径：返回左（16dp）/ 标题全栏水平居中 / 操作右（16dp），三者垂直中心同一线，由 `CollapsibleTopBar` 统一摆放
- [x] 走查：AI 记账页浅/深色、提示词管理（右侧「保存」）、320dp 窄屏、日历页折叠态小标题

### 12. AI 提示词与本地识别规则（第六轮）✅（2026-10-29）
- [x] `ai/AiPromptKind`：5 类提示词注册表（账单文本 / 小票图片 / 分类识别 / 账户识别 / AI 摘要），每类都写明"什么时候生效"，全部有真实调用点
- [x] `AiConfig`：`ai_prompt_<key>` 存储 + 旧键 `ai_prompt_text`/`ai_prompt_image` 回退读取；新增「账户识别用 AI」开关
- [x] `ai/LocalRule` + `LocalRuleEngine`：本地识别规则（关键词 → 分类/类型/账户），命中口径 命中词数 → 最长词 → 用户规则优先 → 顺序；内置 13 条与旧硬编码分类对齐并补了教育/人情/旅行
- [x] `ai/LocalRuleStore`：SharedPreferences + JSON（同 `CategoryStore`），支持导出/导入/恢复内置；`IjiApplication` 里 init
- [x] 管线接入：`ruleBasedParse` → `localRuleParse(text, rules)`（纯函数，可单测）；AI 结果对不上真实分类/账户时补一次「分类识别」/「账户识别」
- [x] 本机 OCR 优先走用户规则，未命中再退回小票专用词表（改词表对图片识别同样生效）
- [x] 新页「提示词与规则」（参照 Hyper-pick-up-code 规则页：小节标题 + 分组卡片 + 标题/摘要/箭头）+ 提示词编辑页 + 本地规则编辑页（分类走单选弹窗）
- [x] 统计页「AI 小结」：只发当期汇总（收支/笔数/分类前三），未配 Key / 失败给明确文案，可重试；同周期结果缓存
- [x] 删除旧 `ui/ai/PromptManageScreen.kt`；路由 `prompt_edit/{kind}`、`local_rule_edit/{ruleId}` 已登记 `ImmersiveRoutes`
- [x] 单测：`LocalRuleEngineTest`(15) + `LocalRecognitionPipelineTest`(8) + `AiPromptRegistryTest`(6) + `AiSummaryPayloadTest`(5)

## 验证

- `tools/run_unit_tests.ps1`：**OK (201 tests / 20 classes)**
- `:app:assembleDebug`：**BUILD SUCCESSFUL**
- 模拟器走查（过滤弹窗写盘 / 重启持久化 / 清空恢复 / 来源清单 / 识别方式 / 失败提示）：见
  `HANDOFF-AI记账与自动记账重构.md` 6.4；真机 + 深色大字号仍待走查

## 注意事项

- 保持「宁可漏判不误判」：验证码/中奖黑名单、金额上限、余额排除不可删
- 不破坏通知无障碍服务三自律（只读不模拟点击、先确认后落库、60s 去重）
- 保持 `BillResult` 字段与 `PaymentBillStore` 落盘 JSON 兼容
- 遵循 Miuix 现有 UI 风格（卡片 `Radius.xxl`、辅助文字 `TextSecondary`、弹窗走 `IjiDialog`）

### 13. 识屏过程态上岛（参照 Maling-Island）✅（2026-10-30）
- [x] `SuperIslandNotifier.sendIsland(ongoing, islandTimeoutSeconds)`：进行中岛（`setOngoing` + `CATEGORY_PROGRESS` + `setTimeoutAfter` 兜底）、`cancel(id)`
- [x] 新增 `islandParamsJsonV3()`：HyperOS 3 的 `protocol: 3` 模板（`ticker` / `islandFirstFloat` / `isShowNotification` / 左图标右文字两栏 / `pic_app|pic_ticker|pic_aod` 图标键）
- [x] 按 `islandLevel` 自动选协议：≥3 走协议 3，否则走原协议 1 —— **protocol 1 在 OS3 上会建事件不渲染（真机 logcat 实证）**
- [x] `NotificationHelper.showScreenshotProgress / clearScreenshotProgress`：开关（默认开）+ 通知权限 + 岛能力三重闸门；进度岛 id 9104 与结果 9102 分离
- [x] `ScreenCaptureFlow`：倒计时发「准备识屏」→ 截到图刷「正在识别」→ `finally` 撤下；「通知样式」分支改走 `report(forceIsland = true)`，不再占用记账提醒的岛 id
- [x] 偏好设置 → 超级岛通知：新增「识屏识别中上岛」，原项改名「截屏记账结果上岛」
- [x] 单测：`SuperIslandNotifierTest` 11 例（两套协议模板的关键字段）；全量 **211 tests / 21 classes**
- [x] 真机走查：岛浮出截图、撤销日志、关掉开关后无岛事件
