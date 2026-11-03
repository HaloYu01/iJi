package com.iji.app.reminder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 岛参数 JSON 的稳定部分。
 *
 * 这段 JSON 是「系统认不认这张岛」的全部依据，改错一个字段的结果是**静默降级**成普通通知
 * （不报错、不崩溃，只是不上岛），所以关键字段必须被测试钉住：
 * - `param_island.islandTimeout`：过程态（识别中）要给短超时，结束后不会永久占着状态栏；
 * - `protocol` / `updatable` / `business`：决定能不能原地更新同一条岛；
 * - `hintInfo.actionInfo.actionIntentType = 1` + 非空 intent：点「查看」能回应用。
 */
class SuperIslandNotifierTest {

    private fun params(timeoutSeconds: Int = 3600, glow: Boolean = true): JSONObject {
        val json = SuperIslandNotifier.islandParamsJson(
            title = "正在识别",
            content = "已截屏，正在分析账单",
            businessTag = "screenshot_import",
            hintTitle = "正在识别",
            clickUri = "intent:#Intent;component=com.iji.app/.MainActivity;end",
            glowEnabled = glow,
            islandTimeoutSeconds = timeoutSeconds,
        )
        return JSONObject(json)
    }

    private fun JSONObject.paramV2(): JSONObject = getJSONObject("param_v2")

    private fun JSONObject.island(): JSONObject = paramV2().getJSONObject("param_island")

    @Test
    fun `过程态岛的存活时间按参数下发`() {
        // 识屏过程态：2 分钟，异常时自己过期
        assertEquals(120, params(timeoutSeconds = 120).island().getInt("islandTimeout"))
        // 结果态沿用默认的一小时
        assertEquals(3600, params().island().getInt("islandTimeout"))
    }

    @Test
    fun `岛可原地更新且带运营标识`() {
        val v2 = params().paramV2()
        assertEquals(1, v2.getInt("protocol"))
        assertTrue("updatable 为假时同 id 重发会被当成新岛", v2.getBoolean("updatable"))
        assertEquals("screenshot_import", v2.getString("business"))
        // enableFloat 关掉就只有通知、不浮出岛
        assertTrue(v2.getBoolean("enableFloat"))
    }

    @Test
    fun `卡片与横条文案按入参渲染`() {
        val v2 = params().paramV2()
        assertEquals("正在识别", v2.getJSONObject("baseInfo").getString("title"))
        assertEquals("已截屏，正在分析账单", v2.getJSONObject("baseInfo").getString("content"))
        assertEquals("正在识别", v2.getJSONObject("hintInfo").getString("title"))
    }

    @Test
    fun `光效开关关闭时传空串`() {
        assertEquals("outer_glow", params(glow = true).paramV2().getString("outEffectSrc"))
        assertEquals("", params(glow = false).paramV2().getString("outEffectSrc"))
    }

    @Test
    fun `按钮走 activity intent 而不是广播`() {
        val action = params().paramV2().getJSONObject("hintInfo").getJSONObject("actionInfo")
        assertEquals(1, action.getInt("actionIntentType"))
        assertTrue(action.getString("actionIntent").isNotBlank())
        assertEquals("查看", action.getString("actionTitle"))
    }

    @Test
    fun `大岛模板为左标题右正文`() {
        val big = params().island().getJSONObject("bigIslandArea")
        assertEquals(2, big.getInt("templateNo"))
        assertEquals("正在识别", big.getJSONObject("imageTextInfoLeft").getJSONObject("textInfo").getString("title"))
        assertEquals("已截屏，正在分析账单", big.getJSONObject("textInfo").getString("title"))
    }

    // ── 协议 3（HyperOS 3）：真机 23127PN0CC / OS3.0 上一旦少了下面任一项就不浮出 ──

    private fun paramsV3(timeoutSeconds: Int = 120, glow: Boolean = true): JSONObject = JSONObject(
        SuperIslandNotifier.islandParamsJsonV3(
            title = "正在识别",
            content = "已截屏，正在分析账单",
            businessTag = "screenshot_import",
            ticker = "准备识屏",
            islandTimeoutSeconds = timeoutSeconds,
            glowEnabled = glow,
        ),
    )

    @Test
    fun `协议3带上浮出与存活字段`() {
        val v2 = paramsV3().paramV2()
        assertEquals(3, v2.getInt("protocol"))
        // 这三个字段是「建出岛事件但不浮出」与「正常浮出」的分水岭
        assertTrue(v2.getBoolean("isShowNotification"))
        assertTrue(v2.getBoolean("islandFirstFloat"))
        assertEquals(120, v2.getInt("timeout"))
        assertEquals(120, v2.getJSONObject("param_island").getInt("islandTimeout"))
        assertEquals("准备识屏", v2.getString("ticker"))
        assertEquals("准备识屏", v2.getString("aodTitle"))
    }

    @Test
    fun `协议3的大岛是左图标右文字两栏`() {
        val big = paramsV3().island().getJSONObject("bigIslandArea")
        assertEquals("miui.focus.pic_app", big.getJSONObject("imageTextInfoLeft").getJSONObject("picInfo").getString("pic"))
        assertEquals("正在识别", big.getJSONObject("imageTextInfoRight").getJSONObject("textInfo").getString("title"))
        // 协议 1 的 templateNo 在协议 3 里不存在，混着发会让系统取不到模板
        assertTrue(!big.has("templateNo"))
    }

    @Test
    fun `协议3的图标键与悬浮关掉时的光效`() {
        val v2 = paramsV3(glow = false).paramV2()
        assertEquals("miui.focus.pic_app", v2.getJSONObject("picInfo").getString("pic"))
        assertEquals("miui.focus.pic_ticker", v2.getString("tickerPic"))
        assertEquals("miui.focus.pic_aod", v2.getString("aodPic"))
        assertEquals("", v2.getString("outEffectSrc"))
    }

    @Test
    fun `协议3的展开卡文案来自 baseInfo`() {
        val base = paramsV3().paramV2().getJSONObject("baseInfo")
        assertEquals("正在识别", base.getString("title"))
        assertEquals("已截屏，正在分析账单", base.getString("content"))
    }
}
