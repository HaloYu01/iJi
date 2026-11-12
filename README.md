# i记（iJi）

一款 Kotlin + Jetpack Compose 的安卓个人记账 App，界面为 MIUI / HyperOS（Miuix）风格，主色 `#2177E8`。
导航为**底部四 tab（账本 / 资产 / 日历 / 统计）+ 最右「记一笔」圆钮 + 右上角「我的」**。

功能包括：多账本、多币种（账本级，只换符号不换算）、分类自由编辑、预算与周期账单、模板、应收垫付、
标签与附件、应用锁、CSV 导入导出、JSON / 加密备份（`.ijbak`）、WebDAV 网盘、自动备份、AI 记账、截图批量导入、
Shizuku / 无障碍识屏、支付通知记账、小米超级岛通知。

## 开始

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

发布包用 `.\gradlew :app:assembleRelease`，产出按 ABI 分包的两个已签名 APK（真机 `app-arm64-v8a-release.apk`、模拟器 `app-x86_64-release.apk`），签名材料与体积口径见 [`HANDOFF.md`](HANDOFF.md) §2.1.1。

## 文档

**唯一权威文档是 [`HANDOFF.md`](HANDOFF.md)**：构建与走查、单测方法、技术栈与工程结构、
数据与持久化、硬约束、各子系统设计、待办与已知限制，都只在那里维护。

> 单测不要用 `gradlew testDebugUnitTest`（本机 Gradle worker 起不来），
> 改用 `tools/run_unit_tests.ps1` 直跑 JUnitCore，详见 `HANDOFF.md` §2.3。
