package com.iji.app.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import com.iji.app.ui.FontScaleState
import com.iji.app.ui.HapticsState
import com.iji.app.ui.ToggleableHapticFeedback
import com.iji.app.ui.components.IjiButtonFx


private val LightColors = lightColorScheme(
    primary = MiuixPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F0FF),
    onPrimaryContainer = Color(0xFF0A4B9E),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    background = AppBackgroundLight,
    onBackground = Color(0xFF1D1D1F),
    surface = AppSurfaceLight,
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = AppSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFFE5E5EA),
)

private val DarkColors = darkColorScheme(
    primary = MiuixPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A3D7A),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    background = AppBackgroundDark,
    onBackground = Color(0xFFF5F5F7),
    surface = AppSurfaceDark,
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = AppSurfaceVariantDark,
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF3A3A3C),
)

// 全局主题状态
object ThemeState {
    var themeMode by mutableStateOf("system")
}

@Composable
fun IjiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val themeMode = ThemeState.themeMode

    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> darkTheme
    }

    val miuixMode = when (themeMode) {
        "dark" -> ColorSchemeMode.Dark
        "light" -> ColorSchemeMode.Light
        else -> ColorSchemeMode.System
    }

    val miuixController = remember(miuixMode) { ThemeController(miuixMode) }
    MiuixTheme(
        controller = miuixController,
        content = {
            // 应用触感反馈总开关：包装真实实现后全局注入，关闭时所有触感调用点统一静音
            val toggleableHaptics = ToggleableHapticFeedback(LocalHapticFeedback.current)
            CompositionLocalProvider(
                LocalSquircleEnabled provides true,
                // App 所有主要按钮的统一按压反馈：水波纹(MiuixIndication) + 按压缩放(SinkFeedback 0.94)。
                // Miuix 的 Button / IconButton / TextButton 的 indication 默认取 LocalIndication.current，
                // 因此在这里全局注入即可，无需逐个调用点传参；单个按钮想覆盖就显式传 indication。
                LocalIndication provides IjiButtonFx.PressIndication,
                LocalHapticFeedback provides toggleableHaptics,
            ) {
                // 注入响应式尺寸（屏幕左右内边距随屏宽自适应）
                ProvideDimens {
                    val scheme = if (isDark) DarkColors else LightColors
                    MaterialTheme(
                        colorScheme = scheme,
                        typography = Typography,
                    ) {
                        // 全局字体大小：font_size 这个键此前只写不读，滑杆保存后界面毫无变化。
                        // 直接改 fontScale（而不是逐个替换 typography 的十几个 slot）：
                        // 只影响 sp → px 的换算，dp 布局尺寸不变，Miuix 组件内部的 sp 文本也一并生效。
                        val baseDensity = LocalDensity.current
                        CompositionLocalProvider(
                            LocalDensity provides Density(
                                density = baseDensity.density,
                                fontScale = FontScaleState.scale,
                            ),
                        ) {
                        // 修复：深色模式下「未显式指定颜色」的文字仍是纯黑（等于看不见）。
                        //
                        // 原因：`LocalContentColor` 的组合默认值就是 `Color.Black`，真正把它设成
                        // onSurface 的是 M3 的 `Surface`；而本项目的卡片/容器几乎都走 Miuix 的
                        // `Card` / `Box`（不经 Surface），于是没人提供内容色 —— 浅色下看不出问题，
                        // 深色下就成了黑底黑字。
                        //
                        // 在主题根显式提供（等价于 M3 `Surface` 的既有行为）。组件内部若自带
                        // contentColor（Miuix Button、IjiTopBar 等）仍以它们自己的为准，不受影响。
                        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                            content()
                        }
                        }
                    }
                }
            }
        }
    )
}
