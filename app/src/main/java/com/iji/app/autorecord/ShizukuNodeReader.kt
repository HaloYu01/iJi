package com.iji.app.autorecord

import android.content.Context
import android.util.Xml
import com.iji.app.shizuku.ShizukuBridge
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

/**
 * 用 shell 身份读界面节点，作为无障碍的兜底通道。
 *
 * 为什么需要它：微信支付的账单页（`MMWebViewUI`）对**第三方无障碍服务**返回空树——
 * 即使 flags 全开（0x5b，含 `INCLUDE_NOT_IMPORTANT_VIEWS` 与
 * `ENHANCED_WEB_ACCESSIBILITY`），`rootInActiveWindow` 的 childCount 仍是 0；
 * 而系统工具（`uiautomator dump`，shell uid）能读到 74 个节点、27 段文本。
 *
 * 所以这里借 Shizuku 的 shell 身份跑一次 `uiautomator dump`，把 XML 解析成
 * 与无障碍同构的 [AutoNode] 列表，后续规则完全复用，不需要两套解析。
 *
 * 代价与约束：
 * - 一次 dump 约 1–2 秒，所以**只在无障碍确实读不到时**才走这条；
 * - 调用方必须限流（同窗口短时间内只 dump 一次），否则会拖慢整个解析队列；
 * - Shizuku 未运行时静默返回空列表，退回原来的 miss 行为，不打扰用户。
 */
object ShizukuNodeReader {

    private const val REMOTE_PATH = "/data/local/tmp/iji_ui_dump.xml"

    /**
     * dump 当前界面并解析成节点列表。
     *
     * @param packageName 只保留这个应用的节点；窗口里可能混有系统 UI 的浮层
     */
    suspend fun read(context: Context, packageName: String): List<AutoNode> {
        if (ShizukuBridge.state(context) != com.iji.app.shizuku.ShizukuState.READY) return emptyList()
        // 只 dump 一次：失败时的重试会把延迟翻倍（真机曾出现 2×5.5s = 11.3s），
        // 而解析池是单线程的，堵一次就拖垮整条队列。失败交给下一次页面事件。
        val xml = dumpXml(context) ?: return emptyList()
        val start = xml.indexOf("<hierarchy")
        if (start < 0) return emptyList()
        return runCatching { parse(xml.substring(start), packageName) }.getOrDefault(emptyList())
    }

    /**
     * 一条命令跑完一次 dump。
     *
     * dump 本身要 1–2 秒，分成四次 exec 会把总延迟放大到 4 秒以上（真机实测 4187ms），
     * 弹窗会晚到用户已经切走。先删旧文件再 dump，失败时不会读到上一次的陈旧内容。
     */
    private suspend fun dumpXml(context: Context): String? = ShizukuBridge.exec(
        context,
        // `timeout 5` 必须加：uiautomator dump 在复杂页面（京东首页）会卡十几秒，
        // 而 ShizukuBridge.exec 自身没有超时，会一直占着单线程解析池。
        "rm -f $REMOTE_PATH; timeout 5 uiautomator dump $REMOTE_PATH >/dev/null 2>&1; " +
            "cat $REMOTE_PATH 2>/dev/null; rm -f $REMOTE_PATH",
    )

    /** 把 `uiautomator dump` 的 XML 拍平成与无障碍一致的有序文本节点 */
    internal fun parse(xml: String, packageName: String): List<AutoNode> {
        if (xml.isBlank()) return emptyList()
        val out = ArrayList<AutoNode>(128)
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "node") {
                // 只有目标应用的节点才有识别价值，系统浮层一律跳过
                if (parser.getAttributeValue(null, "package") == packageName) {
                    val text = parser.getAttributeValue(null, "text").orEmpty().trim()
                        .ifEmpty { parser.getAttributeValue(null, "content-desc").orEmpty().trim() }
                    if (text.isNotEmpty() && out.size < 600) {
                        out.add(
                            AutoNode(
                                text = text,
                                className = parser.getAttributeValue(null, "class"),
                                viewId = parser.getAttributeValue(null, "resource-id"),
                                index = out.size,
                            ),
                        )
                    }
                }
            }
            event = parser.next()
        }
        return out
    }
}
