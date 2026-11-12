package com.iji.app.ui.effects.glass

import androidx.compose.runtime.Stable

/**
 * 液态玻璃采样层（`rememberLayerBackdrop` + `layerBackdrop(mustRecord = …)`）的录制闸门。
 *
 * ## 为什么需要它
 * 玻璃的模糊半径普遍 ≥6dp，采样图本来就是低通滤波的结果——**半帧率刷新肉眼无感**。
 * 而 `mustRecord` 每帧返回 true 会让采样层按屏幕刷新率整屏重录：本机实测
 * 1280×2856 ≈ 14.6MB GPU 层/帧，是滚动与切页期间最重的单项开销，120Hz 设备上
 * 直接把滚动帧拖出预算（表现为「滑动卡卡的」）。tab 平推窗口早已用同款「隔帧录制」
 * 把成本减半（2026-11 实测无感），这里把同一条原则推广到**一切内容变化帧**。
 *
 * ## 调度规则
 * - `changing = true`（内容仍在变：滚动 / 平推 / 拖动）：**隔帧**录制，跳过的帧挂起补录标记；
 * - `changing = false`：若上一帧跳过了录制，**补录一帧落定画面**（只补一次），之后零录制。
 *   静止页零录制、落定画面与内容最终一致，与旧行为的静息结果相同。
 *
 * ## 调用方义务
 * 1. `mustRecord` 引用必须 remember 稳定（LayerBackdropElement 按引用比较，新引用会
 *    每次重组 markNeedsRecord 退化成每帧录）；
 * 2. 「内容停止变化」必须保证有一次 draw 期咨询才能触发补录：
 *    - 变化信号本身是 snapshot state（如 `isScrollInProgress`、scroll epoch）时，
 *      停止时的写入会 invalidate 订阅它的 draw，天然产生最后一次咨询；
 *    - 变化信号是「值不再推进」（如滚动 epoch 停在某个数）时，需要外部在停帧后写一次
 *      draw 期订阅的 state（如平推窗口收口时写一帧 tick）强制重放。
 */
@Stable
class BackdropRecordGate {

    /** 隔帧翻转位：true = 本帧录制。 */
    private var tick = false

    /** 上一帧轮到「跳」且内容在变：停帧后要补录一帧。 */
    private var pendingSettle = false

    /**
     * draw 阶段调用。[changing] = 自上次咨询以来被录制的像素仍在变
     * （滚动推进、平推窗口、动画进行中）。返回 true = 本帧重录采样层。
     */
    fun shouldRecord(changing: Boolean): Boolean {
        if (changing) {
            tick = !tick
            pendingSettle = !tick
            return tick
        }
        val settle = pendingSettle
        pendingSettle = false
        return settle
    }
}
