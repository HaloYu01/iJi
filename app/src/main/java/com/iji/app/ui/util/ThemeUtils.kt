package com.iji.app.ui.util

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.iji.app.ui.theme.ThemeState

/**
 * 判断当前是否为深色主题。
 *
 * 与 i 记全局主题状态（ThemeState.themeMode）联动：
 * - "dark"  → 强制深色
 * - "light" → 强制浅色
 * - "system"/其他 → 跟随系统
 */
@Composable
fun isAppDarkTheme(): Boolean = when (ThemeState.themeMode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}
