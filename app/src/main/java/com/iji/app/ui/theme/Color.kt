package com.iji.app.ui.theme

import androidx.compose.ui.graphics.Color

// Miuix 风格主色
val MiuixPrimary = Color(0xFF2177E8)
val MiuixPrimaryDark = Color(0xFF4A90E2)

// Apple 风格功能色
val AppleGreen = Color(0xFF34C759)
val AppleRed = Color(0xFFFF3B30)
val AppleOrange = Color(0xFFFF9500)
val ApplePurple = Color(0xFFAF52DE)
val AppleIndigo = Color(0xFF5856D6)

// Apple 风格背景色
val AppleBackground = Color(0xFFFFFFFF)
val AppleSecondaryBackground = Color(0xFFF2F2F7)
val AppleTertiaryBackground = Color(0xFFF7F7FA)

// Apple 风格文字色
val AppleLabel = Color(0xFF1D1D1F)
val AppleSecondaryLabel = Color(0xFF8E8E93)
val AppleTertiaryLabel = Color(0xFFAEAEB2)

// Apple 风格分隔线
val AppleSeparator = Color(0xFFE5E5EA)

// ── 统一背景色系（集中定义，供 MaterialTheme / Miuix 共用，保证各页背景协调一致）──
// 说明：浅色用"近白 + 微冷灰"，深色去掉纯黑（改为低亮度深灰），整体更柔和、与 Miuix 风格统一。
val AppBackgroundLight = Color(0xFFF5F6F8)
val AppSurfaceLight = Color(0xFFFFFFFF)
val AppSurfaceVariantLight = Color(0xFFF0F1F4)

val AppBackgroundDark = Color(0xFF0E0F12)
val AppSurfaceDark = Color(0xFF1C1C1E)
val AppSurfaceVariantDark = Color(0xFF2C2C2E)

// 兼容旧代码
val IjiRed = MiuixPrimary
val IjiRedDark = MiuixPrimaryDark
// 消费金额统一红色 / 收入统一绿色（全局约定）：金额展示处一律用这两个语义色，
// 不要直接写 MiuixPrimary / onSurface，避免再出现「支出是蓝色」的不一致
val ExpenseAmount = AppleRed
val IncomeGreen = AppleGreen
val TextSecondary = AppleSecondaryLabel
val DividerGray = AppleSecondaryBackground
