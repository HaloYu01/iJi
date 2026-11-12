package com.kyant.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.internal.recordLayer
import kotlin.math.roundToInt

/**
 * @param recordKey 被录制内容的指纹（壁纸 bitmap / 缩放 / 偏移 / 亮度 / 主题色等）。
 *   传了它且值没变时，说明这一帧录出来的内容和上一帧逐像素相同，可以整段跳过 ——
 *   滑动课表时壁纸是静止的，每帧重录一次全屏层纯属白烧。
 *   传 null（默认）保持原行为：每帧录制。
 *   注意：指纹必须覆盖所有能改变被录制内容的因素，否则会用到过期采样。
 * @param mustRecord draw 阶段强制录制谓词。返回 true 时即使 recordKey 未变也重录。
 *   用于「结构稳定但滚动/动画仍在改像素」的主内容 liquidGlass：谓词里读 ScrollState/Animatable，
 *   不进组合，滚动帧只多一次比较。引用须 remember 稳定，否则每次重组 update→markNeedsRecord，跳过失效。
 *   ⚠️ 平推/滚动只改 graphicsLayer 位移，不会自行 invalidate 父节点 draw——想让录制逐帧
 *   发生，必须在 mustRecord 里**读一个每帧变化的 snapshot state**（draw 阶段读 =
 *   订阅该 state，写入即触发 draw 重放；实测 invalidateDraw() 在 draw 过程中调用无效）。
 */
fun Modifier.layerBackdrop(
    backdrop: LayerBackdrop,
    recordKey: Any? = null,
    mustRecord: (() -> Boolean)? = null,
    /** 为 true 的帧即使没录制（「假帧」）也调用 invalidateDraw 续链，否则录制窗口内的
     *  跳录帧会让 draw 停在那帧、下一次轮到录制的帧永远不会到来（录制链断掉，
     *  玻璃冻结到落定）。调用方负责该谓词读取的 snapshot state 每帧推进。 */
    invalidateWhile: (() -> Boolean)? = null,
    /**
     * 只录制节点顶部的这段高度（条带录制）。采样层的消费方（模糊带 / 玻璃圆钮）全部
     * 位于节点顶部时，滚动帧的重录可从「整屏」降到「条带」，GPU 层内存与填充开销按
     * 高度比例下降（1280×2856 整屏 ≈ 14.6MB/帧 → 顶栏条带 ≈ 3MB/帧）。
     *
     * 坐标语义：条带原点 = 节点原点，消费方采样坐标**不变**；条带以下区域在采样图中
     * 为透明（与整屏录制时「节点外」的区域一致）。要求所有消费方都落在节点顶部
     * [recordHeight] 内，且节点顶边 ≤ 消费方上缘的采样范围。
     * null（默认）= 录全层。
     */
    recordHeight: Dp? = null
): Modifier =
    this then LayerBackdropElement(backdrop, recordKey, mustRecord, invalidateWhile, recordHeight)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop,
    val recordKey: Any? = null,
    val mustRecord: (() -> Boolean)? = null,
    val invalidateWhile: (() -> Boolean)? = null,
    val recordHeight: Dp? = null
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop, recordKey, mustRecord, invalidateWhile, recordHeight)
    }

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.recordKey = recordKey
        node.mustRecord = mustRecord
        node.invalidateWhile = invalidateWhile
        node.recordHeight = recordHeight
        node.markNeedsRecord()
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (recordKey != other.recordKey) return false
        // mustRecord / invalidateWhile 按引用比较：remember 出来的稳定 lambda 才能让 equals 为 true
        if (mustRecord !== other.mustRecord) return false
        if (invalidateWhile !== other.invalidateWhile) return false
        if (recordHeight != other.recordHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + (recordKey?.hashCode() ?: 0)
        result = 31 * result + (mustRecord?.hashCode() ?: 0)
        result = 31 * result + (invalidateWhile?.hashCode() ?: 0)
        result = 31 * result + (recordHeight?.hashCode() ?: 0)
        return result
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop,
    var recordKey: Any? = null,
    var mustRecord: (() -> Boolean)? = null,
    var invalidateWhile: (() -> Boolean)? = null,
    var recordHeight: Dp? = null
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var needsRecord = true
    private var recordedW = 0
    private var recordedH = 0

    fun markNeedsRecord() { needsRecord = true }

    override fun ContentDrawScope.draw() {
        drawContent()
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        // recordKey == null：内容可能每帧变化（滚动中的课表/顶栏），必须每帧重录。
        // 之前误写成只在 needsRecord/尺寸变化时录，与文档「null = 每帧录制」不一致，
        // 会导致采样层停在旧帧，和当帧内容对不齐，慢滑时看起来发闪。
        // mustRecord：recordKey 非空但滚动/动画仍在改像素时强制重录（draw 阶段读，不触发组合）。
        val force = mustRecord?.invoke() == true
        val shouldRecord = recordKey == null || force || needsRecord || recordedW != w || recordedH != h
        if (shouldRecord) {
            needsRecord = false
            recordedW = w
            recordedH = h
            val stripPx = recordHeight
                ?.let { it.toPx() }
                ?.coerceIn(0f, size.height)
            if (stripPx != null && stripPx < size.height) {
                // 条带录制：层尺寸 = 宽 × 条带高，层原点 = 节点原点（消费方坐标不变），
                // 条带以下不录（采样图中为透明）。onDraw 仍走整棵内容树，由 clipRect
                // 把 GPU 填充裁到条带内；显示列表重放本身免不了，省的是层内存与光栅化。
                recordLayer(
                    this@LayerBackdropNode,
                    backdrop.graphicsLayer,
                    size = IntSize(w, stripPx.roundToInt())
                ) {
                    clipRect(0f, 0f, size.width.toFloat(), stripPx) {
                        backdrop.onDraw(this@draw)
                    }
                }
            } else {
                recordLayer(this@LayerBackdropNode, backdrop.graphicsLayer) {
                    backdrop.onDraw(this@draw)
                }
            }
            backdrop.contentVersion++
        }
        // mustRecord 为 true 表示「内容仍在变」（tab 平推、滚动、胶囊拖动）。
        // 平推只改 graphicsLayer 位移，不会 invalidate draw：若不主动续帧，draw() 停在
        // 第一帧，采样层就冻结在转场起始画面，底栏玻璃看不到移动的内容。
        // invalidateWhile：假帧（本帧没录）也要续链——跳录帧若不续，draw 停在该帧，
        // 下一轮「该录」的帧永远不会到来，录制链断掉。
        // ⚠️ 实测 invalidateDraw() 在 draw 过程中调用不产生下一帧重绘（2026-11-05 验证），
        // 逐帧续帧必须由 mustRecord / invalidateWhile 谓词内部读取「每帧写入的 snapshot state」
        // 来驱动（draw 阶段读 state = 订阅，写入即重放 draw），调用方负责推进该 state。
        if (force || invalidateWhile?.invoke() == true) invalidateDraw()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            val prev = backdrop.layerCoordinates
            // LayoutCoordinates 通常按实例比较 equals。位置/尺寸未变时不要换实例，
            // 否则下游读 layerCoordinates 的 draw 节点会被无意义地整批 invalidate。
            val changed = prev == null ||
                prev.isAttached != coordinates.isAttached ||
                prev.positionInWindow() != coordinates.positionInWindow() ||
                prev.size != coordinates.size
            if (changed) {
                backdrop.layerCoordinates = coordinates
            }
        }
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}
