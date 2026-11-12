package com.iji.app.ui.effects.glass

import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop

/**
 * 全站液态玻璃采样层：[com.iji.app.ui.AppRoot] 创建页面级 layerBackdrop 并录制 NavHost
 * 内容，弹层（Scaffold popup 层，不在录制树内）通过它采样页面内容做真·液态玻璃，
 * 且不会把弹层自身录进采样图（无自捕获反馈）。
 *
 * 无采样层 / 不需要玻璃的调用方拿到 null，按各组件自己的降级策略回退普通材质。
 */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }
