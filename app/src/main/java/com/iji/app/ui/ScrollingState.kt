package com.iji.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局「正在滚动」信号。
 *
 * **为什么需要**：AppRoot 的全屏采样层（`layerBackdrop`）在没有 `recordKey` 时
 * **每帧**重录整屏，而滚动恰恰是每帧都要绘制的场景 —— 最贵的操作撞上最频繁的时机。
 *
 * 采样层其实只在**静止**时才真正需要刷新（此时偶尔录一次，成本可忽略，视觉也正确）；
 * 滚动期间底栏那枚 56dp 的胶囊玻璃冻结几百毫秒几乎察觉不到，但能省掉每帧一次全屏离屏渲染。
 *
 * 由各滚动页面用 `snapshotFlow { listState.isScrollInProgress }` 发布，
 * 在 AppRoot 的 `mustRecord` 谓词里读取（draw 阶段读 State，不触发组合）。
 */
object ScrollingState {

    var isScrolling by mutableStateOf(false)
        private set

    fun set(value: Boolean) {
        isScrolling = value
    }
}