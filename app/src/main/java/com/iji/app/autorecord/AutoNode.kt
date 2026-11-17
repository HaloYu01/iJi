package com.iji.app.autorecord

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 全自动记账的节点快照。
 *
 * 与半自动（截屏 + OCR）是**两条互不相干的链路**：这里只用无障碍读到的界面文本，
 * 不截图、不识别图片、不依赖截屏授权。
 *
 * 一个节点只保留识别要用的三样东西与文档顺序（[index]），
 * 顺序是"取下一段文本"这类规则的基础，所以必须按视觉顺序拍平。
 */
data class AutoNode(
    val text: String,
    val className: String?,
    val viewId: String?,
    val index: Int,
)

/**
 * 把 `rootInActiveWindow` 拍平成有序文本节点列表。
 *
 * 只收「有文本或 contentDescription」的节点——纯布局容器对识别没有意义，
 * 而它们的子节点会被依次访问，顺序与视觉顺序一致。
 * 设上限防止异常页面（长列表 / WebView）把内存与耗时拉爆。
 */
object AutoNodeCollector {

    private const val MAX_NODES = 600
    private const val MAX_DEPTH = 40

    fun collect(root: AccessibilityNodeInfo?): List<AutoNode> {
        if (root == null) return emptyList()
        val out = ArrayList<AutoNode>(64)
        walk(root, 0, out)
        return out
    }

    /**
     * 多个窗口的根依次拍平，[AutoNode.index] 保持连续。
     *
     * 微信/支付宝的账单内容常常**不在活动窗口里**：活动窗口只是一个空的 FrameLayout，
     * 真正的文本在兄弟窗口（WebView）。只取 `rootInActiveWindow` 会得到 0 个节点，
     * 而 `uiautomator` 合并所有窗口就能读到 74 个——差的就是这一层。
     */
    fun collectAll(roots: List<AccessibilityNodeInfo>): List<AutoNode> {
        if (roots.isEmpty()) return emptyList()
        val out = ArrayList<AutoNode>(128)
        roots.forEach { walk(it, 0, out) }
        return out
    }

    private fun walk(node: AccessibilityNodeInfo?, depth: Int, out: MutableList<AutoNode>) {
        if (node == null || out.size >= MAX_NODES || depth > MAX_DEPTH) return
        // 入列前统一清洗：零宽字符会让 `exact("交易成功")`、`^-?\d+\.\d{2}$` 全部失配，
        // 真机表现就是「同一个账单页，有的能识别、有的不能」
        val raw = AutoValues.normalize(
            node.text?.toString().orEmpty().ifBlank { node.contentDescription?.toString().orEmpty() },
        )
        if (raw.isNotEmpty()) {
            out.add(
                AutoNode(
                    text = raw,
                    className = node.className?.toString(),
                    viewId = node.viewIdResourceName,
                    index = out.size,
                ),
            )
        }
        val count = runCatching { node.childCount }.getOrDefault(0)
        for (i in 0 until count) {
            walk(runCatching { node.getChild(i) }.getOrNull(), depth + 1, out)
        }
    }
}

// ── 匹配原语 ───────────────────────────────────────────────────────────────
// 语义对齐钱迹的 matcher 模型：每个 matcher 在「全部节点」里找**任意一个**满足的节点，
// 一条规则的全部 matcher 都命中才算页面成立。

sealed interface NodeMatcher {
    fun find(nodes: List<AutoNode>): AutoNode?
}

class ExactText(private val value: String) : NodeMatcher {
    override fun find(nodes: List<AutoNode>) = nodes.firstOrNull { it.text == value }
}

class ContainsText(private val value: String) : NodeMatcher {
    override fun find(nodes: List<AutoNode>) = nodes.firstOrNull { it.text.contains(value) }
}

class StartsWithText(private val value: String) : NodeMatcher {
    override fun find(nodes: List<AutoNode>) = nodes.firstOrNull { it.text.startsWith(value) }
}

class EndsWithText(private val value: String) : NodeMatcher {
    override fun find(nodes: List<AutoNode>) = nodes.firstOrNull { it.text.endsWith(value) }
}

class RegexText(private val regex: Regex) : NodeMatcher {
    override fun find(nodes: List<AutoNode>) = nodes.firstOrNull { regex.matches(it.text) }
}

class IdMatch(private val viewId: String) : NodeMatcher {
    override fun find(nodes: List<AutoNode>) = nodes.firstOrNull { it.viewId == viewId }
}

class AnyOfMatcher(private val matchers: List<NodeMatcher>) : NodeMatcher {
    override fun find(nodes: List<AutoNode>) = matchers.firstNotNullOfOrNull { it.find(nodes) }
}

/** 所有条件**同时**命中才算命中（钱迹 ForbiddenCoexistAllMatcher），与 [AnyOfMatcher] 的「任一命中」相反。 */
class ForbiddenCoexistMatcher(private val matchers: List<NodeMatcher>) : NodeMatcher {
    override fun find(nodes: List<AutoNode>): AutoNode? {
        if (matchers.isEmpty()) return null
        return if (matchers.all { it.find(nodes) != null }) matchers.first().find(nodes) else null
    }
}

fun exact(text: String): NodeMatcher = ExactText(text)
fun contains(text: String): NodeMatcher = ContainsText(text)
fun startsWith(text: String): NodeMatcher = StartsWithText(text)
fun endsWith(text: String): NodeMatcher = EndsWithText(text)
fun regex(pattern: String): NodeMatcher = RegexText(Regex(pattern))
fun idMatch(viewId: String): NodeMatcher = IdMatch(viewId)
fun anyOf(vararg matchers: NodeMatcher): NodeMatcher = AnyOfMatcher(matchers.toList())

/** 页面必须存在一个可解析金额（钱迹把金额当页面硬条件的写法） */
class AmountText(private val strict: Boolean) : NodeMatcher {
    override fun find(nodes: List<AutoNode>) = nodes.firstOrNull { AutoValues.isAmount(it.text, strict) }
}

fun amount(strict: Boolean = false): NodeMatcher = AmountText(strict)

/** 用于 `forbidden` 列表：如「首页+点单」同时出现才排除，避免单个词误杀正常页。 */
fun forbiddenCoexist(vararg matchers: NodeMatcher): NodeMatcher =
    ForbiddenCoexistMatcher(matchers.toList())

// ── 取值关系 ───────────────────────────────────────────────────────────────
// 规则命中后要按「哪个标签的下一段文本」取值；这些关系就是钱迹里的
// Next / Previous / NextByType / NextTextNotEmpty 等。

/** 节点列表查询封装：规则只写「找哪个标签、取它的下一段」，不手动管理下标。 */
class NodeList(val all: List<AutoNode>) {

    /** 跳过图片节点；Button 不能跳过（支付方式、状态常挂在按钮上）。 */
    private val imageClasses = setOf(
        "android.widget.ImageView",
        "android.widget.Image",
    )

    fun find(label: String): AutoNode? = all.firstOrNull { it.text == label }

    fun findContains(label: String): AutoNode? = all.firstOrNull { it.text.contains(label) }

    fun findStarts(label: String): AutoNode? = all.firstOrNull { it.text.startsWith(label) }

    fun anyText(vararg labels: String): Boolean = labels.all { label -> all.any { it.text == label } }

    fun anyContains(vararg labels: String): Boolean = labels.all { label -> all.any { it.text.contains(label) } }

    fun exists(vararg labels: String): Boolean = labels.any { label -> all.any { it.text.contains(label) } }

    /** 标签后第一段文本（跳过图片/按钮类节点，它们通常没有值） */
    fun after(label: String, maxSteps: Int = 10): String? = afterAny(label, maxSteps = maxSteps)


    /** 多个候选标签里，第一个后面能取到金额的（按标签顺序试，不跨标签猜） */
    fun amountAfterAny(vararg labels: String, strict: Boolean = false, maxSteps: Int = 10): Long? =
        labels.firstNotNullOfOrNull { amountAfter(it, strict, maxSteps) }

    /** 标签后第一个指定控件类型的文本（钱迹 NextByType）。 */
    fun nextByType(label: String, className: String, maxSteps: Int = 10): String? {
        val anchor = find(label) ?: return null
        return nextByTypeOf(anchor, className, maxSteps)
    }

    /** 从已知节点（如 `startsWith("零钱充值-来自")` 命中的动态文本）往后取指定类型文本 */
    fun nextByTypeOf(anchor: AutoNode, className: String, maxSteps: Int = 10): String? {
        var i = anchor.index + 1
        while (i < all.size && i <= anchor.index + maxSteps) {
            val node = all[i]
            if (node.text.isNotBlank() && node.className == className) return node.text
            i++
        }
        return null
    }

    /** 标签后、同类节点不超过 [maxCount] 个内第一个匹配 [pattern] 的文本（钱迹 NextMatch）。 */
    fun nextMatch(
        label: String,
        pattern: Regex,
        className: String? = null,
        maxCount: Int = 2,
    ): String? {
        val anchor = find(label) ?: return null
        var count = 0
        var i = anchor.index + 1
        while (i < all.size) {
            val node = all[i]
            if (className != null && node.className != className) {
                i++
                continue
            }
            count++
            if (count > maxCount) break
            if (pattern.matches(node.text)) return node.text
            i++
        }
        return null
    }

    /** 标签后第一段非空且不等于 [exclude] 的文本（钱迹 `NextTextExcludeText`） */
    fun nextExclude(
        label: String,
        exclude: String,
        className: String? = null,
        maxSteps: Int = 10,
    ): String? {
        val anchor = find(label) ?: return null
        var i = anchor.index + 1
        while (i < all.size && i <= anchor.index + maxSteps) {
            val node = all[i]
            val classOk = className == null || node.className == className
            if (node.text.isNotBlank() && node.text != exclude && classOk) return node.text
            i++
        }
        return null
    }


    /** 从已知节点往前取（淘宝的店铺名要按「状态词节点」定位，而不是按固定标签） */
    fun prevTextOf(
        anchor: AutoNode,
        exclude: Set<String> = emptySet(),
        skip: Int = 0,
        maxSteps: Int = 10,
    ): String? {
        var i = anchor.index - 1
        var skipped = 0
        while (i >= 0 && i >= anchor.index - maxSteps) {
            val node = all[i]
            if (node.text.isNotBlank() && node.text !in exclude) {
                if (skipped >= skip) return node.text
                skipped++
            }
            i--
        }
        return null
    }

    fun afterAny(vararg labels: String, maxSteps: Int = 10): String? {
        val anchor = all.firstOrNull { node -> labels.any { node.text == it } } ?: return null
        return nextText(anchor, maxSteps)
    }


    /** 标签前第一段文本（金额常出现在状态文案上方） */
    fun before(label: String, maxSteps: Int = 10): String? {
        val anchor = all.firstOrNull { node -> node.text == label || node.text.contains(label) }
            ?: return null
        var i = anchor.index - 1
        while (i >= 0 && i >= anchor.index - maxSteps) {
            val node = all[i]
            if (node.text.isNotBlank() && node.className !in imageClasses) return node.text
            i--
        }
        return null
    }

    /** 从某个标签往后找第一个满足正则的文本 */
    fun regexAfter(label: String, pattern: Regex, maxSteps: Int = 10): String? {
        val anchor = find(label) ?: return null
        var i = anchor.index + 1
        while (i < all.size && i <= anchor.index + maxSteps) {
            val text = all[i].text
            if (pattern.matches(text)) return text
            i++
        }
        return null
    }

    /** 全列表里第一段匹配正则的文本 */
    fun firstRegex(pattern: Regex): String? = all.firstOrNull { pattern.matches(it.text) }?.text

    /** 第一段能解析成金额的文本 */
    fun firstAmount(strict: Boolean = false): Long? = all.firstNotNullOfOrNull {
        if (AutoValues.isAmount(it.text, strict)) AutoValues.amountCents(it.text) else null
    }

    /** 某个标签后的第一个金额 */
    fun amountAfter(label: String, strict: Boolean = false, maxSteps: Int = 10): Long? {
        val anchor = find(label) ?: return null
        var i = anchor.index + 1
        while (i < all.size && i <= anchor.index + maxSteps) {
            val text = all[i].text
            if (AutoValues.isAmount(text, strict)) return AutoValues.amountCents(text)
            i++
        }
        return null
    }

    /** 某个标签前第一个金额（状态文案在金额下一行的版式） */
    fun amountBefore(label: String, strict: Boolean = false, maxSteps: Int = 10): Long? {
        val anchor = all.firstOrNull { it.text == label || it.text.contains(label) } ?: return null
        var i = anchor.index - 1
        while (i >= 0 && i >= anchor.index - maxSteps) {
            val text = all[i].text
            if (AutoValues.isAmount(text, strict)) return AutoValues.amountCents(text)
            i--
        }
        return null
    }

    fun firstTime(): String? = all.firstOrNull { AutoValues.isTime(it.text) }?.text

    fun timeAfter(label: String, maxSteps: Int = 10): String? {
        val anchor = find(label) ?: return null
        var i = anchor.index + 1
        while (i < all.size && i <= anchor.index + maxSteps) {
            if (AutoValues.isTime(all[i].text)) return all[i].text
            i++
        }
        return null
    }

    fun timeAfterAny(vararg labels: String, maxSteps: Int = 10): String? =
        labels.firstNotNullOfOrNull { timeAfter(it, maxSteps) }

    private fun nextText(anchor: AutoNode, maxSteps: Int): String? {
        var i = anchor.index + 1
        while (i < all.size && i <= anchor.index + maxSteps) {
            val node = all[i]
            if (node.text.isNotBlank() && node.className !in imageClasses) return node.text
            i++
        }
        return null
    }

}
