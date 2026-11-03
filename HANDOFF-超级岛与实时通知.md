# HANDOFF — 记账提醒「小米超级岛 / 原生实时通知」功能（真机澎湃OS 3 排障中）

> 交接时间：2026-10-29。**§7 修复计划已于 2026-10-29 全部实施并编译通过**（assembleDebug 成功）。
> 2026-10-30 又完成了「原生实时通知」排障（见 §10）：根因是缺 `POST_PROMOTED_NOTIFICATIONS`
> 权限 + targetSdk 35 + autoCancel 与 ongoing 冲突，已全部修复并编译通过。
> 剩余工作只有真机验证：装新 APK → 测试发送 → 把 snackbar 的完整 detail 文案回报上来。

---

## 1. 需求原话（用户）

- 记账提醒通知支持**小米超级岛**，利用 **Shizuku 授权**，参考 https://github.com/HaoZai000/NexioSchedule （不用太复杂，简单的通知就行）。
- 再做一个**安卓原生实时通知**，参考 https://github.com/badnng/Hyper-pick-up-code 的实时通知。
- 做一个**选项可以开启和切换**。
- 真机（小米14，澎湃OS 3）实测：**原生和超级岛都只显示普通状态栏通知**，要求修复。

## 2. 参考项目（曾浅克隆到 /tmp/ref，接手时可能已没了，需要时重克隆）

| 项目 | 用途 | 关键文件 |
|---|---|---|
| HaoZai000/NexioSchedule | 超级岛 + Shizuku XMSF 断网 bypass（**HyperOS 2 时代方案**） | `androidApp/.../reminder/IslandNotificationHelper.kt`（miui.focus.param JSON，`protocol:1`）；`shizuku/ShizukuManager.kt`（`setXmsfNetworkingEnabled`，防火墙链 9） |
| badnng/Hyper-pick-up-code | 超级岛模板16 + 原生实况通知（**HyperOS 3 / OS2 方案**） | `helper/SuperIslandHelper.kt`（`protocol:3` 模板16、pics/actions Bundle、`canShowFocus`、`notification_focus_protocol>=3` 检测）；`helper/NotificationHelper.kt`（原生分支 `.setOngoing(true)` + `extras["android.requestPromotedOngoing"]=true`） |
| 小米官方文档 | 岛通知构建方式（**权威**） | https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131 （岛通知构建）、?pId=2146（焦点通知是什么） |
| 能力边界调研 | 准入门槛说明 | https://github.com/Makia9879/ElectricWave/blob/master/docs/research/hyperos-dynamic-island-capability.md |

## 3. 已完成的改动（全部已编译通过，assembleDebug 成功产出 APK）

| 文件（`Y:\iji-android\app\src\main\java\com\iji\app\...`） | 内容 |
|---|---|
| `shizuku/ShizukuBridge.kt` | 新增 `setXmsfNetworkingEnabled(context, enabled)`：用 `ShizukuBinderWrapper` 包 connectivity 服务，反射调 `setFirewallChainEnabled(9,true)` + `setUidFirewallRule(9, uid, 0/2)` 给 `com.xiaomi.xmsf` 断网/恢复。**未改 AIDL/UserService** |
| `reminder/SuperIslandNotifier.kt`（新建） | 焦点通知发送器。双协议 JSON（`protocol1ParamsJson` / `protocol3ParamsJson`）；能力检测 `isIslandFeature()`（读 `persist.sys.feature.island`）、`focusProtocol()`（读 Settings.System `notification_focus_protocol`，读不到返回 0）、`hasFocusPermission()`（`content://miui.statusbar.notification.public` call `canShowFocus`）；发送时按 `protocol>=3` 选 JSON，`needBypass = !useProtocol3 || !focusAllowed` 时走 XMSF bypass；返回 `SendResult(sent, detail)` |
| `reminder/ReminderScheduler.kt` | 新增样式键：`KEY_STYLE="reminder_notify_style"`，`STYLE_NATIVE="native"` / `STYLE_ISLAND="xiaomi_island"`，`currentStyle()/setStyle()` |
| `budget/NotificationHelper.kt` | `sendRecordReminder(context, title, content)` 返回 `SendResult`；样式分发：岛样式且 `isIslandFeature()` 为真 → SuperIslandNotifier，否则回落原生（detail 里说明原因）；原生分支 Android 15+ 补了 `setOngoing(true)` + `requestPromotedOngoing` extra |
| `ui/mine/PreferencesScreen.kt` | 提醒卡片下新增「提醒通知样式」行（WheelOptionDialog 二选一）+「发送测试通知」行；`islandCapability`（Triple(feature, protocol, focusPerm)）ON_RESUME 重查；副标题显示本机检测结论；选中岛但焦点权限未开时跳 `ACTION_APP_NOTIFICATION_SETTINGS`；测试发送 snackbar 回显 `SendResult.detail` |

现状代码里**发岛通知的完整链路**：`PreferencesScreen(测试)` 或 `ReminderWorker(每晚)` → `NotificationHelper.sendRecordReminder` → `SuperIslandNotifier.sendReminder` → （条件触发）`ShizukuBridge.setXmsfNetworkingEnabled`。

## 4. 当前问题（唯一未解决项）

小米14 + 澎湃OS 3：测试发送后仍是普通状态栏通知。**用户没有回报 snackbar 里的 detail 内容**，所以无法确定走了哪条分支——这正是 §6 假设清单存在的原因。

## 5. 已查明的关键事实（接手必读，来自官方文档与参考源码）

1. **`notification_focus_protocol` 系统设置的返回值**：`1` = OS1 焦点模板、`2` = OS2 焦点模板、`3` = **OS3 小米超级岛模板**。它是判断设备支持哪代模板的**官方**方式。
2. **只有 OS3 支持岛**（bigIslandArea/smallIslandArea 的渲染）；OS2 只做状态栏焦点通知。第三方客户端接入方式 = 在普通 Notification extras 里放 `miui.focus.param`（+ `miui.focus.pics`/`miui.focus.actions` Bundle）。
3. **JSON 内的 `protocol` 字段**：官方示例里是 **1**（注释标注 OS2/OS3 通用）；但 Hyper-pick-up-code 实际用 **3**（配合模板16）。两者矛盾，未在真机验证过哪个是必须的。NexioSchedule 用 1（OS2 + 强制 bypass）。
4. **必填字段**（官方）：`business`(String)、`param_island`、`bigIslandArea`、`smallIslandArea`、`islandProperty`、`aodTitle`、`ticker`。注意：**protocol1ParamsJson 目前缺 `ticker`**。
5. **准入门槛**（ElectricWave 调研）：官方流程要企业认证+场景审核+设备白名单；未获权限降级普通通知。**但** Hyper-pick-up-code 作为个人应用不带任何 bypass 直接发，其超级岛功能面向 OS3 用户发布——说明 OS3 客户端方式对未审核应用大概率可用，运行时门槛主要是**用户侧「焦点通知」开关**（`canShowFocus`）。
6. **XMSF bypass 的原理**（NexioSchedule）：MIUI 的推送框架 XMSF 会把第三方焦点通知送云端白名单校验，断网窗口内校验跳过、直接上岛。它是 OS2 时代第三方上岛的主流邪道；OS3 上是否仍需要**未验证**。
7. 原生实况通知（`requestPromotedOngoing`）在 HyperOS 上**本来就不会渲染成岛**（小米走自家焦点通知体系），小米机型上原生样式为普通通知属预期；该选项主要惠及其它 Android 15+ 机型。
8. 编译环境：`JAVA_HOME="/c/Program Files/Java/jdk-24" ./gradlew assembleDebug`。输出里 aidl.exe 对 `IShizukuShell.aidl` 中文注释报的「编码 UTF-8 的不可映射字符」是中文 Windows 的**既有噪音**，退出码 0、APK 正常产出，不要被它带偏。

## 6. 根因假设（按可能性排序，均未验证）

1. **`persist.sys.feature.island` 在小米14上为 false** → `NotificationHelper` 里 `isIslandFeature()` gate 直接回落原生，**岛路径根本没被触发**。这是当前最可疑的一条：如果成立，snackbar detail 会是「未检测到超级岛特性，已回落 原生…」。小米14是挖孔屏（无官方"岛"硬件形态），该属性是否为真存疑。**修复方向：不再单独依赖它做硬门槛**（见 §7.1）。
2. **`focusProtocol()` 读到 0**（设置项不存在/读取异常）→ `useProtocol3=false` → 发了 `protocol:1` JSON，OS3 不认 → 普通通知。detail 会是「岛 protocol=1 · …」。
3. **焦点权限（canShowFocus）未开** 且用户没找到/没开系统里的「焦点通知」开关 → 系统降级普通通知。detail 会是「焦点权限=无」。
4. **bypass 门控太保守**：当前只在 `!useProtocol3 || !focusAllowed` 时才 bypass；若 OS3 对未审核应用仍做云端校验，权限已开也需要 bypass。NexioSchedule 是**每次发岛都 bypass**。
5. **JSON `protocol` 字段取值**（1 vs 3，见 §5.3）或模板字段缺失（§5.4 的 `ticker`）。

## 7. 待执行的修复计划（具体到代码）

### 7.1 `SuperIslandNotifier.kt` —— 放宽门槛 + 兜底协议判定 + 总是尝试 bypass

```kotlin
// 新增：MIUI/HyperOS 判定（读 ro.miui.ui.version.name 非空）
fun isMiui(): Boolean = runCatching {
    val m = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
    !(m.invoke(null, "ro.miui.ui.version.name") as? String).isNullOrBlank()
}.getOrDefault(false)

// 新增：综合判定设备该走哪代协议。
// 官方口径是 notification_focus_protocol（1/2/3）；读不到（返回0）时兜底：
// 只要是 MIUI 且有岛特性开关（或干脆只要是 MIUI），就按 3 发——系统不认最多降普通通知，不会更糟
fun islandLevel(context: Context): Int {
    val p = focusProtocol(context)
    if (p >= 1) return p
    return if (isIslandFeature() || isMiui()) 3 else 0
}
```

`sendReminder` 内：

- `val level = islandLevel(context)`；`useProtocol3 = level >= 3`（替换现在的 `protocol >= 3`）。
- **bypass 改为：Shizuku READY 就总是尝试**（对齐 NexioSchedule 的做法），不再用 `needBypass` 门控；Shizuku 不 READY 记日志直接发。
- `protocol1ParamsJson` 补 `ticker` 字段（官方必填）。
- `detail` 字符串扩展成包含全部诊断量，例如：
  `"岛 protocol=${level} · 岛特性=${isIslandFeature()} · 焦点权限=${..} · bypass=已生效/未生效(Shizuku未就绪)/无需"` —— 真机回报靠它。

### 7.2 `NotificationHelper.sendRecordReminder` —— 岛门槛换掉

把 `SuperIslandNotifier.isIslandFeature()` 的 gate 改为 `SuperIslandNotifier.islandLevel(context) >= 3`；回落文案同步改（说「未检测到焦点通知能力」而不是「未检测到超级岛特性」）。

### 7.3 `PreferencesScreen.kt` —— 检测文案与引导同步

- `islandCapability` / `islandCapabilityText` 改用 `islandLevel()` + `isIslandFeature()` + `hasFocusPermission()` 三项分开显示（如「本机：协议3 · 岛特性√ · 焦点权限未开」）。
- 焦点权限未开的引导文案，除跳 `ACTION_APP_NOTIFICATION_SETTINGS` 外，再提示另一条路径：「设置 → 通知与控制中心 → 焦点通知」（OS3 的全局/按应用开关两种位置都提到，因为确切位置未验证）。
- （可选）WebSearch 确认 OS3 上「焦点通知」开关的准确路径：上一会话最后一步搜索因并发限制失败，查询词可用「澎湃OS3 焦点通知 权限 开启 设置 第三方应用 超级岛」。

### 7.4 验证（真机）

1. 装新 APK → 偏好设置 → 提醒 → 提醒通知样式 = 小米超级岛。看副标题的三项检测值。
2. 授权 Shizuku（应用内已有引导：ShizukuBridge.state/requestPermission/openManager）。
3. 发送测试通知，**记录 snackbar 的完整 detail 文案**——这是判定走哪条分支的唯一证据。若仍为普通通知且 detail 显示 protocol=3 · 焦点权限=有 · bypass=已生效，则剩余嫌疑只有 JSON `protocol` 字段（试改回 1，官方示例口径）和模板字段，此时可对照官方模板库 PDF（dev.mi.com 文档站内）逐字段核对 `protocol3ParamsJson`。

## 8. 其它注意点

- `ReminderWorker` 对 `sendRecordReminder` 的返回值不关心，类型改成 `SendResult` 不影响它。
- `PreferencesScreen` 里 `islandCapability` 等局部 val 的**声明顺序敏感**（ensureShizukuForIsland 引用它，必须声明在前）；上次就因此返工过一次。
- 项目无 git（`Is a git repository: no`），改动直接落盘，没有版本回退手段——改前注意别覆盖别人的东西。
- 用户机器上 JDK 不在 PATH，编译必须带 `JAVA_HOME="/c/Program Files/Java/jdk-24"`。
- 项目 UI 组件约定：Miuix（`top.yukonga.miuix.kmp`）+ 少量 material3；设置行用 `Card` + `RowLeadingIcon` + `Switch`；滚轮选择用 `com.iji.app.ui.components.WheelOptionDialog`（必须在 Miuix Scaffold 子树内）。

## 9. 源码位置速查

- 发送链路入口：`Y:\iji-android\app\src\main\java\com\iji\app\budget\NotificationHelper.kt`（`sendRecordReminder` ~L160）
- 岛通知实现：`Y:\iji-android\app\src\main\java\com\iji\app\reminder\SuperIslandNotifier.kt`
- XMSF bypass：`Y:\iji-android\app\src\main\java\com\iji\app\shizuku\ShizukuBridge.kt`（`setXmsfNetworkingEnabled`）
- 设置 UI：`Y:\iji-android\app\src\main\java\com\iji\app\ui\mine\PreferencesScreen.kt`（「提醒」分组）
- 夜间提醒触发：`Y:\iji-android\app\src\main\java\com\iji\app\reminder\{ReminderScheduler,ReminderWorker}.kt`

## 10. 第二轮排障：原生实时通知仍是普通通知（2026-10-30 已修复）

对照两个**真机验证可上岛**的实现后，找到原生路径与它们的全部差异并补齐：

| 差异点 | Hyper-pick-up-code / Halcyon | 本项目（修复前） | 修复 |
|---|---|---|---|
| manifest 权限 | 声明 `POST_PROMOTED_NOTIFICATIONS`（Android 16 实况通知前置，缺失时系统**静默降级**普通通知） | 无 | 已加（AndroidManifest.xml） |
| targetSdk | 37 | 35 | 已升 36（Live Updates 最低要求） |
| autoCancel | false（与 ongoing 语义互斥） | true | 已改 false |
| 渠道重要性 | HIGH | DEFAULT（渠道创建后不可改，新开 `record_reminder_live` 渠道，删旧 `record_reminder`） | 已换 |
| 芯片短文案 | `setShortCriticalText`（API 36，状态栏芯片只认它） | 无 | 已加 |
| Builder | platform `Notification.Builder` | NotificationCompat | 已换 |

另外两个新兜底（原生路径在 MIUI 上）：
- **叠加焦点通知参数**：`miui.focus.param`（协议 3）+ pics/actions extras 与实况 extra 同发，HyperOS 认哪条渲染哪条（小米14 的 HyperOS 3 可能是 Android 15 基底，原生实况框架 API 不存在，焦点参数是另一条路）；
- **XMSF bypass**：Shizuku 可用时断网窗口内发送（HyperOS 可能对实况/焦点通知走云端白名单）。

诊断 detail（测试发送 snackbar）新增：`实况请求=已附加 · promoted权限=有/无 · 岛参数=已叠加(protocol=3) · 焦点权限=有 · bypass=已生效`。
若 `promoted权限=无` 说明 ROM 不认该权限（需 Android 16 基底）；实况渲染的用户侧开关在「设置 → 通知与控制中心」或应用通知设置里的「实况通知/焦点通知」。

参考：Halcyon `LiveLyricNotificationBridge.kt`（promoted ongoing + setShortCriticalText + POST_PROMOTED_NOTIFICATIONS 权限 + HyperOS 超级岛歌词）；CustomizeLiveUpdate README（最低 Android 16 / HyperOS 3.0.300+，需系统开「实时动态」开关）。

## 11. Shizuku 超级岛路线可行性确认与加固（2026-10-30）

**结论：能实现，且 App 里已有完整实现**（「小米超级岛」选项 = 焦点通知 `miui.focus.param` + Shizuku 切 XMSF 网络绕过云端白名单）。可行性证据链：
- NexioSchedule：同一机制在 MIUI（OS2）真机验证；
- **Halcyon（Kifranei/Halcyon）：在 HyperOS 3 真机用同一防火墙机制（链 9 + XMSF uid DENY）投送超级岛歌词**，其 `ShizukuXmsfNetworkHelper.kt` 与本项目 `ShizukuBridge.setXmsfNetworkingEnabled` 同源；
- 阿里云推送文档确认 HyperOS 3 支持应用进程活跃时以原生通知方式上岛。

Halcyon 揭示的兼容性要点（本轮已全部加固进 ShizukuBridge）：
1. Android 16 起 `setUidFirewallRule` 等隐藏方法被移出反射列表 → 引入 `org.lsposed.hiddenapibypass:hiddenapibypass` 兜底直调；
2. 老系统走 `network_management` 服务后端；方法名/签名有多个变体（setUidFirewallRule/setFirewallUidRule/复数批量形式）→ 逐个尝试；
3. 下发失败重试 2 次、间隔 500ms；
4. 焦点通知**必须挂在高重要性（HIGH）渠道**，低重要性渠道 HyperOS 不把它当焦点载荷（岛渠道 `record_reminder_island` 本就是 HIGH）；
5. 焦点通知建议 `setShowWhen(false)` + `setLocalOnly(true)` + `setOnlyAlertOnce(true)`（已加）。

真机验证方法：样式选「小米超级岛」+ Shizuku 运行并授权 → 发送测试通知 → snackbar detail 里 `bypass=已生效` 即防火墙路径通了；若 bypass 生效仍不上岛，剩余排查点只剩协议 JSON 的字段（对照官方模板库）。

## 12. 超级岛通知扩展到三类业务（2026-10-30 已实现）

`SuperIslandNotifier` 泛化为通用焦点通知发送器：新增 `sendIsland(context, channelId, channelName, channelDesc, notificationId, businessTag, title, content, hintTitle, clickIntent)`，任意业务可上岛（协议自动选择 + Shizuku XMSF bypass + 右上角小图标 + 底部短横条布局与记账提醒一致）；`protocol3ParamsJson` 参数化 business/hintTitle。

三类新业务各有**独立开关**（偏好设置 →「超级岛通知」分组，键 `island_budget` / `island_screenshot` / `island_recurring`，默认关；开且 `islandLevel>=3` 走岛，否则回落普通通知）：

| 业务 | 普通渠道 | 超级岛渠道 | business | 触发点 |
|---|---|---|---|---|
| 预算提醒 | `budget_alert` | `budget_alert_island` | `budget_alert` | `sendBudgetAlert` 岛分支（点击进预算详情） |
| 截屏记账状态 | `screenshot_import` | `screenshot_import_island` | `screenshot_import` | `sendScreenshotNotice` 岛分支（截屏失败/无结果/完成/异常，点击进截屏导入页） |
| 周期账单自动记账 | `recurring_auto`（新增，原先完全静默无通知） | `recurring_auto_island` | `recurring_auto` | `MainViewModel.processRecurringRules` 汇总 N 笔后调 `sendRecurringNotice`（IO 协程；点击进周期账单管理页） |

注意：开关读写走 `SettingsRepository`（DataStore + 内存缓存）；`processRecurringRules` 由 App 启动/规则增改/手动执行多处调用，都会带出通知（手动「立即执行」也会通知，属预期）。

## 13. 「测试有时不弹出」修复（2026-10-30）

根因：焦点通知的云端白名单校验由状态栏框架**异步**执行，原实现发完通知仅保持 100ms 就恢复 XMSF 网络——校验在网络恢复后才跑时通知被静默吞掉；且岛/原生两条路径各自独立切防火墙，连续测试时两个窗口交错（A 恢复了 B 还没发完）。

修复（ShizukuBridge 新增 `withXmsfBypass(context, block)`）：「断网 → notify → **保持 1.5 秒** → 恢复」全局串行（synchronized），恢复失败用 `xmsfCutByUs` 标记、下个窗口先补恢复；`sendIsland` 与 `sendNative` 都改走它，且 `sendNative` 仅在叠加了焦点参数时才 bypass。若修后仍偶发不弹出，下一步排查方向是「同 id + updatable 导致岛只更新不重新浮出」（测试路径可改用递增通知 id）。

## 14. 关键实测结论：OS3 上 bypass 是反效果（2026-10-30）

真机（小米14 澎湃OS3）表现：bypass=已生效但整条通知被吞，且 1.5 秒长窗口比 100ms 更糟。结论：**HyperOS 3 的焦点白名单校验在离线时是 fail-closed（拒绝整条通知）**，不是 OS2 时代的"离线跳过"。因此：

- **协议 3（OS2/OS3）：一律直发，不做 XMSF bypass**（对齐 Hyper-pick-up-code 的发布形态），上岛靠本机「焦点通知」开关 + 本地白名单缓存；
- **协议 1/2（老 MIUI）：保留 `ShizukuBridge.withXmsfBypass`**（窗口 200ms，全局串行 + 恢复失败补恢复），那是 NexioSchedule 验证过的 OS2 场景；
- 卡片布局回退到确认能渲染的 `iconTextInfo`（不带 animIconInfo，文字有轻微回退缩进）——此前换 `baseInfo` 纯文本组件导致整个岛参数不被识别、通知完全不显示，已回退。**结论：模板 16 的卡片区组件不可替换为 baseInfo**；
- `sendNative` 的岛参数叠加路径同样直发（它只发生在协议 3）。

另：工作区有另一会话（CodeBuddy）并行开发截屏悬浮窗样式（`CapturePreferences.overlayStyle` / `BillOverlay.detailed` / OVERLAY_ISLAND 直调 `sendReminder`），与本功能的岛路径并存不冲突。

## 14. 实测结论修正：bypass 是必需的，100ms 窗口有效（2026-10-30 终版）

真机（小米14 澎湃OS3）三组对照实验的最终结论：

| 配置 | 结果 |
|---|---|
| bypass 100ms + iconTextInfo 布局 | **岛稳定渲染**（多轮布局截图均在此配置下） |
| bypass 1.5s + baseInfo 布局 | 整条通知被吞 |
| 不 bypass + iconTextInfo 布局 | 白名单在线校验拒绝 → **回落普通通知** |

此前「OS3 离线校验 fail-closed」的判断有误——「有时不弹出」实为同期混入的 **baseInfo 布局实验**（模板 16 的卡片区组件不可换成 baseInfo，整个岛参数不被识别）所致，与窗口时序无关。终版实现：

- **协议 3 与协议 1/2 都走 `ShizukuBridge.withXmsfBypass`**（100ms 窗口，全局串行 + 恢复失败补恢复）；
- 卡片布局固定为 iconTextInfo（标题+正文，无 animIconInfo）+ picInfo 右上角小图标 + hintInfo 短横条；
- `sendNative` 的岛参数叠加路径同样走 bypass 窗口。

遗留小问题：iconTextInfo 不带必传的 animIconInfo 时文字有轻微回退缩进（顶部 vs 底栏不完全对齐），待后续找模板库中可替代的无图组件。

## 15. 终版：整体采用 NexioSchedule 成熟结构（2026-10-30）

用户实测仍不显示/不对齐/退出 App 后岛消失后，克隆到 NexioSchedule 源码（此前网络失败，重试成功）逐字段比对，发现此前「简化自 NexioSchedule」的协议 1 JSON 缺了几个关键件，而协议 3（模板 16）路径则一直在与组件替换坑缠斗。**终版：岛路径整体换成 NexioSchedule 的协议 1 结构**（`islandParamsJson`，逐字段对齐其 buildIslandParamsJson）：

- 卡片 = baseInfo（纯文本，天然对齐，无图标位坑）+ hintInfo（底栏短文案 + 「查看」按钮）；按钮走 `actionInfo.actionIntent`（`actionIntentType=1` 跳 Activity，`clickIntent.toUri(URI_INTENT_SCHEME)` 生成），**不再需要 actions Bundle**（actionsExtras/protocol1/protocol3ParamsJson 已删除）；
- 岛 = 模板 2（左标题右正文）+ `islandProperty:1` + **`islandTimeout:3600`**（修「退出 App 后岛消失」）+ `outEffectSrc:"outer_glow"` + 缩略态应用图标（pics 只备 4 个 launcher 键）；
- **测试发送用独立 id（`ISLAND_TEST_NOTIFICATION_ID=5000`）**：同 id 重复发会被岛框架视为静默替换、不重新浮出（NexioSchedule 注释原话）——这是「测试有时不弹出」的另一半根因；`sendRecordReminder/sendReminder` 增加 `testMode` 参数，PreferencesScreen 测试传 true；
- bypass 维持 100ms 窗口 + 全局串行。
- **岛展开光效开关**（2026-10-30 新增）：`SuperIslandNotifier.isGlowEnabled`（键 `island_glow`，默认开，对齐 NexioSchedule 的 `island_expand_glow_enabled`），控制 JSON 的 `outEffectSrc: "outer_glow"`/空串；开关在偏好设置「超级岛」分组末行。
  **2026-11-02 修正**：该开关现在只对岛路径生效——`sendNative` 已不再叠加焦点参数（详下条）。

## 16. 2026-11-02 回归修复：原生路径不要叠焦点参数

「切到原生通知后实时通知不触发」的真根因不是发送失败，而是 `sendNative` 同时给通知塞了
`android.requestPromotedOngoing` 与 `miui.focus.param`（protocol 1）。HyperOS 3 看到焦点参数就按焦点通知接管
（`dumpsys notification` 显示 `focusType=PARAMS`），而协议 1 模板在 OS3 上不渲染 → 通知既不上岛、也不算实时通知。
现在原生样式只走 Android 15+ 实况通道；要岛请选「小米超级岛」。样式开关位于偏好设置 →「提醒」→「通知样式」。

验证提醒：**应用在前台时岛不浮出**是系统行为（logcat `DynamicIslandEvent$EnterApp` / `AppExpanded`），
必须按 HOME 退到后台再截图；原生路径查 `dumpsys notification --noredact | grep id=9001` 应为 `focusType=null`。

—— 完 ——
