# AGENTS.md

## 全自动记账规则：严禁擅自改动

- 修改 `app/src/main/java/com/iji/app/autorecord/` 下的识别规则（`AutoPlatforms`、`AutoNode`、`AutoValues`），**必须先给出候选方案并获用户确认**，禁止直接改写。
- 新规则只能**追加**，不得覆盖、重排或删除已确认的原规则；放宽条件只能作为兜底叠加。
- 每条新规则必须同时给出：全局唯一的 `pageType`、页面成立条件、字段取值锚点、至少 1 个正向与 1 个负向单元测试。
- 金额禁止用 `firstAmount()` 裸兜底，必须锚定到标签或明确版式。
- 真机问题先取 `auto-bill.log`（自动记账页「识别异常？复制诊断日志」）再改规则，不许凭猜改。

## 注释

- 只写「为什么」（非显然约束、平台差异、系统限制），不复述代码，不写轮次/过程记录。
- 单条注释不超过 3 行；过时、重复、与代码不一致的注释直接删除。

## 验证

- 编译：`./gradlew :app:compileDebugUnitTestKotlin`（需 `JAVA_HOME=C:\Program Files\Java\jdk-24`）。
- 单测：`powershell -ExecutionPolicy Bypass -File tools/run_unit_tests.ps1`（本机 Gradle test worker 起不来，用脚本直跑 JUnit）。
- 改动后必须跑通上述两项，既有失败（`AutoAssetMatcherTest`、`AboutDataTest`）不属于本次回归。