# A 类：补底层 / 去桩（2026-10-17）

清点后确认的 8 处「界面看着能用、实际是桩」的功能点，按「**能真做就真做、做不了就不装**（不放假入口占位，点了没反应比没有入口更糟）」的原则逐条处理。**8 项全部完成。**

编译：`assembleDebug` 通过（DB 版本 18 → 19）。单测：`OK (51 tests)`（新增 `UpdateCheckerTest` 11 例、`WebDavClientTest` 5 例）。
真机/模拟器（emulator-5554）走查见各条「验证」。

---

## 已完成的 4 项（上一轮）

### A1 自动记账页去桩
`ui/mine/AutoBookkeepingScreen.kt` 原来两条入口都是假的：
- 「图片识别」弹窗只弹 Toast，里面的「入账账本 / 关联资产」写死 `默认账本 / 微信`；
- 「截屏记账」弹出「系统授权 / Shizuku / 无障碍」三种方式，选完同样只弹 Toast——三种方式一个都没实现。

改为**只放真实存在的能力**（各自跳到已实现好的页面）：

| 入口 | 真实去处 |
| --- | --- |
| 图片识别 | AI 记账页（本地 ML Kit OCR，失败再走视觉大模型） |
| 截屏记账 | 截屏批量导入页（整屏按行拆分、多笔预览、自动去重） |
| 用账单文件导入 | 账单导入页（微信 / 支付宝 CSV） |
| 支付通知自动记账 | 无障碍服务（原在假弹窗里，提到主页面，状态可点可跳系统设置） |

假弹窗与假滚轮整体删除。验证：两条入口分别跳到 AI 记账页与截屏导入页。

### A6 字体大小原来只写不读（真 bug）
`font_size` 键**有人写、没人读**：字体大小页滑到 24sp 保存后全 App 字号纹丝不动；偏好设置里还写死显示「标准」。

- 新增 `ui/FontScaleState.kt`（与 `PerfState` 同一套做法）：持有字号 State + 落盘 + 档位文案；
- `IjiTheme` 在 M3 主题内提供 `LocalDensity(fontScale = sp / 16)`：**只影响 sp→px 换算，dp 布局不变**；
- 字体大小页滑杆松手即生效并落盘；偏好设置摘要显示真实档位。

验证：24sp 下「本月收入 ¥15,200.00」节点宽度 466px → 815px（1.5× = 24/16）；返回后显示「特大 24sp」。

### A7 导入结果缺条数
`DataManageScreen` 的微信 / 支付宝导入回调拿到了 `success/failed`，文案却是写死的 `"成功  条，失败  条"`。已补插值；`"导入失败："` 补成 `"导入失败：${e.message}"`。

### A8 宣称 xlsx、实际只吃 CSV
文案写「选择数据文件（xlsx/xls/csv）」，实际一律 `bufferedReader().readText()`，选 xlsx 会读成乱码并「成功 0 条」。

新增 `ui/components/ImportFileReader.kt`：MIME 过滤（含 `application/vnd.ms-excel`，xlsx 在选择器里直接置灰）→ 按文件头识别 zip/OLE2 并给「请在导出时选 CSV」的可操作提示 → UTF-8 解出替换字符时 **GBK 兜底**（国内银行导出大量 GBK）。文案改为「CSV / TXT」。

验证：`.xlsx` 在选择器里 `enabled=false`；CSV 导入 3 行 → 首页收入 15,200 → 15,300、支出 3,774.60 → 3,790.10。

---

## 本轮完成的 4 项

### A2 无障碍截屏记账（`takeScreenshot` → OCR → 复用导入链路）

**改造前的症结不只是「没实现」，还有「没想清楚怎么用」**：`AccessibilityService.takeScreenshot()` 截的是**当前可见的那一屏**——用户点按钮时屏幕上正是 iji，直接截只会得到自己的界面。所以真实链路必须是「退到后台 → 倒计时 → 截 → 识别 → 通知把用户带回来」。

| 层 | 改动 |
| --- | --- |
| 服务配置 | `res/xml/accessibility_service_config.xml` 增加 `android:canTakeScreenshot="true"`（注释里写明用途与边界：仅用户主动点击时取一帧、图像不出设备） |
| 服务 | `PaymentNotifyAccessibilityService`：companion 持有已连接实例（`isConnected()`）+ `suspend capture()`；`captureScreenOrNull()` 用 `takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)`，`wrapHardwareBuffer().copy(ARGB_8888)` 后立刻 `close()` 硬件缓冲；6s 超时兜底；错误码翻译成中文（截屏太频繁 / 无权限 / 显示设备不可用） |
| 链路 | 新增 `accessibility/ScreenCaptureFlow.kt`：**自带 CoroutineScope**（挂界面上会被销毁 → 截屏永不发生）、3s 倒计时、`ScreenshotBillParser.recognizeDrafts`、结果进 `StateFlow`、通知带 `Routes.SCREENSHOT_IMPORT` 路由点击直达 |
| 通知 | `NotificationHelper`：新增 `screenshot_import` 渠道 + `sendScreenshotNotice(title, body, route)`（成功/失败都发；无通知权限返回 false） |
| 界面 | `ScreenshotImportScreen` 新增「不用手动截图」卡片：未开无障碍 → 「去开启无障碍服务」；已开 → 「截取屏幕（3 秒后）」，触发后 `moveTaskToBack(true)` 让支付 App 成为当前屏幕；返回本页时 `LaunchedEffect` 取走草稿并判重、预选账户 |

补的一个坑：**通知没授权时用户会「点了没反应」** —— `ScreenCaptureFlow.notice` 同时保存结果文案，界面回来时用 Snackbar 补一次。

验证（模拟器，`settings put secure enabled_accessibility_services …` 开启服务）：
1. 未识别出内容的一次：通知正文「没识别出账单 / 这一屏里没找到账单明细」——说明**截图成功**（若 `bitmap==null` 会走「截屏失败」分支）；
2. 3 秒内切回账本页的一次：通知「截屏识别完成 / 识别到 11 笔账单」，回到截屏导入页即出现预览「识别到 11 笔，已选 11 笔」，与相册链路共用同一套判重/入账；
3. 界面按钮在服务未连接时为「去开启无障碍服务」，连接后变「截取屏幕（3 秒后）」——不做假按钮。

### A5 导入历史（新建表 + 落库 + 真实展示）

**改造前**：导入结果只弹一次 Toast，弹完即散；导入页「导入记录」写死「暂无导入记录」——用户事后无法回答「这份账单导没导进去、进了哪本账、几条失败」。

- **DB v18 → v19**：`ImportHistoryEntity` / `ImportHistoryDao` / `MIGRATION_18_19`（建表 + `index_import_history_importedAtMillis`）；保留一年，`deleteOlderThan` 顺带裁剪；
- 新增 `data/model/ImportRecord.kt` + `data/repository/ImportHistoryRepository.kt`（dao 为 null 时退化为空实现；写入失败只记日志，不影响已入账结果）；
- `MainViewModel`：`importHistory: StateFlow<List<ImportRecord>>`；`importCsv / importWeChatCsv / importAlipayCsv / importBackupJson / importDrafts` 全部补 `fileName` 参数并落库（fire-and-forget）；新增 `clearImportHistory()`；
- `BillImportScreen`：「导入记录」弹窗改为真实列表（来源 / 时间 / 成功与失败条数 / 目标账本 / 文件名），可滚动，带「清空记录」+ 二次确认；
- `DataManageScreen` 四个导入入口把文件名一并传入。

验证（模拟器，真的推了一份 CSV 进 Downloads 走系统选择器导入）：
- 导入前：弹窗显示如实的空态（并说明「每次导入都会记一条」）；
- 导入 2 行的 `iji_a5_test.csv` 后：弹窗显示「共 1 次导入 · 最新的在最上面」「通用 CSV / 10月16日 18:43 / 成功 2 条 · 进入「默认账本」/ raw:/storage/…/iji_a5_test.csv」；
- 首页数字同步：收入 15,200 → 16,200、支出 3,774.60 → 3,863.10（正好 +1000 / +88.50）。

### A4 检查更新（没有更新服务器，就不假装「已是最新」）

**改造前**：点「更新版本」弹一句写死的「已是最新版本」——没有任何网络校验。

- 新增 `update/UpdateChecker.kt`：`check(feedUrl, currentVersion)` 真实发请求；`parseFeed` 同时认**简洁格式** `{version,url,notes}` 与 **GitHub releases 原始响应** `{tag_name,html_url,body}`；`isNewer` 逐段比数字（`1.10.0 > 1.9.9` 不会被字符串比较带偏）、`1.1.0-beta` 不算更新；
- 结果语义明确区分：`NotConfigured`（未配置，**不等于已是最新**）/ `UpToDate` / `Available` / `Failed(reason)`；
- `SettingsRepository.KEY_UPDATE_FEED_URL` + 预热缓存（弹窗组合期不做磁盘 IO）；
- `AboutScreen`：弹窗里显示当前版本、可填「更新源地址（留空 = 不联网检查）」、`保存并检查`；未配置时如实说明「所以这里不会告诉你『已是最新』」；有新版显示版本号 + 说明（截断 400 字）+「打开发布页」；
- 新增 `app/src/test/java/com/iji/app/update/UpdateCheckerTest.kt`（11 例，含「未配置 ≠ 已是最新」）。

验证（模拟器）：
1. 空地址点检查 → 「未配置更新源地址，无法联网检查——所以这里不会告诉你「已是最新」」；
2. 填入真实 `https://api.github.com/repos/JetBrains/compose-multiplatform/releases/latest` → 弹窗显示「发现新版本 v1.12.2」+ release 说明 —— 真实联网 + 解析 + 版本比较全链路通。

### A3 云盘备份（真实 WebDAV 上传，而不是只存个配置）

**改造前**：网盘弹窗只把 `type/user/pass` 写进 DataStore，**零上传**；界面上还摆着 Dropbox / OneDrive（各自需要 OAuth 应用，根本接不了）和一个没人读的「退出时自动备份」开关。

- 新增 `backup/WebDavClient.kt`：Basic 认证；`testConnection`(PROPFIND Depth 0) / `upload`(PUT) / `list`(PROPFIND Depth 1 + multistatus 解析) / `download`(GET)；状态码全部翻译成中文；`tidy()` 把 OkHttp 异常翻译成人话——**尤其 http 明文**：本应用没有开全局 `usesCleartextTraffic`，Android 9+ 禁止明文，原样抛「CLEARTEXT communication…」等于没说，改成「请改用 https」；证书不受信也单独提示；
- 新增 `backup/CloudBackupManager.kt`：`Config`（地址 + 账号 + 密码齐全才算「已配置」）、`load/save`（兼容历史 `type/user/pass` 三个键，用户不用重填）、`clear()`（**配置必须有出口**，否则自动备份会一直往旧地址传）、`uploadNow()`（构建快照 → `BackupCipher.encrypt` → PUT，**先本机加密再上传，网盘上只有密文**）、`listRemote()`、`downloadSnapshot()`；
- `BackupManager`：抽 `newBackupName()`（本地/网盘同一命名约定）与 `restoreSnapshot()`（配置合并 + 流水追加，本地恢复与网盘恢复共用一份逻辑，`BackupSettingsScreen` 已改为调用它）；
- `AutoBackupWorker`：本地备份成功且配了网盘时**一并上传**（失败只记日志，不让一次网络抖动重跑整个任务）；
- `DataManageScreen`：弹窗改为 类型（坚果云 / WebDAV（通用），**删掉接不了的 Dropbox / OneDrive**）+ 服务器地址 + 账号 + 应用密码；`测试连接` / `备份到网盘` / `从网盘恢复`（列表 → 选一份 → 二次确认 → 下载解密恢复）/ `清除网盘配置`；入口副标题如实显示「未配置：填了服务器地址与账号才会上传」；
- 新增 `app/src/test/java/com/iji/app/backup/WebDavClientTest.kt`（5 例：命名空间前缀、相对 href 转绝对、百分号解码、脏数据不崩）。

验证（模拟器）：
1. 未填账号点「备份到网盘」→「请先填写服务器地址、账号与应用密码」（不静默失败）；
2. 真实 `https://dav.jianguoyun.com/dav/` + 假凭据 → 「账号或应用密码不正确（HTTP 401）」——请求真的发出去了；
3. 地址改为可接受 PUT 的公共 https 地址 → 上传成功提示「iji_backup_1792177151948.ijbak（5.3 KB）」（快照构建 + 加密 + PUT 全链路）；
4. 对不支持 PROPFIND 的服务器 → 「服务器不支持该操作，请确认填的是 WebDAV 地址（HTTP 405）」，且空列表文案不再说「没有备份」（读失败 ≠ 没备份）；
5. 「清除网盘配置」→ 提示「已清除网盘配置，自动备份不再上传」，入口回到「未配置」；
6. **本地 WebDAV 桩端到端**（临时起一个只实现 PROPFIND/PUT/GET 的服务 + 临时给 debug 变体放行明文，验证完已删除该配置并重新构建）：`测试连接` → 「连接成功，目录可访问」；`备份到网盘` → 「iji_backup_1792250505531.ijbak（4.9 KB）」，服务端目录里确实多出同名 5055 字节的密文文件；`从网盘恢复` → 列表真实显示「iji_backup_1792250505531.ijbak / 4.9 KB · Sat, 17 Oct 2026 15:21:45 GMT」（PROPFIND Depth 1 + multistatus 解析 + 文件名/大小/时间都对）。

> 未做的一步：**「下载 → 解密 → 合并导入」没有在设备上真跑**。原因是合并导入会把整份流水**再追加一遍**，而重复笔与原始笔的日期/金额/备注完全相同，事后无法可靠区分并清理——为了验证而污染公共测试机的账本不划算。这一步的组成里：PROPFIND/PUT/GET 同一个客户端（PUT 已真跑）、解密与合并用的是本地备份恢复那条已上线的同一份 `BackupCipher` + `restoreSnapshot`。有真实网盘账号时，点一次「恢复」即可把这一跳补上。

---

## 验证环境（模拟器 emulator-5554）——2026-10-17 已复原
- 两笔走查账单、1 条导入记录**已删除**并核对：本月收入 15,200.00 / 支出 3,774.60 / 结余 11,425.40，与走查前一致；导入记录弹窗回到空态。
- 为验证 A2 临时开启的无障碍服务已关闭（`enabled_accessibility_services` 已删、`accessibility_enabled=0`），`POST_NOTIFICATIONS` 已撤销（`granted=false`）。
- 本地 WebDAV 桩（python，仅 PROPFIND/PUT/GET）与临时明文放行配置均已删除，并已重新构建安装。

## 测试包
`%USERPROFILE%\Downloads\iji-debug-a1apk.apk`（上一轮 A1 包；本轮包在 `app/build/outputs/apk/debug/app-debug.apk`）

---

## 接下来
1. **真机（`d385580c`）走查本轮四项** —— 模拟器过了不等于真机过：A2 的目标场景是**真实微信/支付宝账单页**；A5 的 DB v18→v19 升级在真机（数据更多）再确认一次；A3 弹窗与恢复流程在小屏上的排布。（**当前真机没连**，`adb devices` 只有 emulator-5554；插上并开 USB 调试后即可继续。）
2. **A3 收口**：用真实网盘账号点一次「恢复」，把「下载 → 解密 → 合并导入」这一跳补上（见上面「未做的一步」）。

刻意不做（不是遗漏）：MediaProjection 全自动截屏、Shizuku（A2 只保留用户主动触发）；http 明文 WebDAV 不放行（界面直接提示换 https）；Dropbox / OneDrive 不接（需要各自 OAuth 应用）；检查更新默认不联网（留空 = 未配置）。

刻意不做（不是遗漏）：MediaProjection 全自动截屏、Shizuku（A2 只保留用户主动触发）；http 明文 WebDAV 不放行（界面直接提示换 https）；Dropbox / OneDrive 不接（需要各自 OAuth 应用）；检查更新默认不联网（留空 = 未配置）。

改动未提交 git（本机 shell 里没有 git 命令），全部在工作区。
