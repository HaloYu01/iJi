// ThemeSnapshot 兼容桩：NexioSchedule 的增强版 InteractiveHighlight 在 draw 阶段读
// backdrop 库的全局主题/壁纸快照（AppThemeSnapshot.isDark / PageBackdropSnapshot.hasWallpaper）。
// 本工程 vendored 的 com.kyant.backdrop 没有这两个对象，这里按同名同包补齐：
// - isDark 由 AppRoot 组合期同步（AppThemeSnapshotSync()），draw 阶段读 State.value
// - hasWallpaper 恒 false（本工程无壁纸采样场景）
// 属 1:1 复刻的兼容层，非上游原始文件。

package com.kyant.backdrop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.iji.app.ui.util.isAppDarkTheme

private val appDarkState = mutableStateOf(false)

object AppThemeSnapshot {
    val isDark: State<Boolean> get() = appDarkState
}

object PageBackdropSnapshot {
    val hasWallpaper: State<Boolean> = mutableStateOf(false)
}

/** 组合期把当前主题同步进快照（AppRoot 根部调用一次）。 */
@Composable
fun AppThemeSnapshotSync() {
    appDarkState.value = isAppDarkTheme()
}
