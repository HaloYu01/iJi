package com.iji.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 识屏授权方式的选择规则：**用户指定就只看那一个**，自动才按「Shizuku → 无障碍」回落。
 */
class CapturePreferencesTest {

    @Test
    fun autoPrefersShizuku() {
        assertEquals(
            CapturePreferences.MODE_SHIZUKU,
            CapturePreferences.resolveMode(CapturePreferences.MODE_AUTO, shizukuReady = true, a11yConnected = true),
        )
    }

    @Test
    fun autoFallsBackToAccessibility() {
        assertEquals(
            CapturePreferences.MODE_A11Y,
            CapturePreferences.resolveMode(CapturePreferences.MODE_AUTO, shizukuReady = false, a11yConnected = true),
        )
    }

    @Test
    fun autoWithoutAnyPermissionIsNull() {
        assertNull(CapturePreferences.resolveMode(CapturePreferences.MODE_AUTO, false, false))
    }

    @Test
    fun explicitShizukuDoesNotFallBack() {
        // 用户明确选了 Shizuku，就不能偷偷改用无障碍截屏
        assertNull(CapturePreferences.resolveMode(CapturePreferences.MODE_SHIZUKU, false, true))
        assertEquals(
            CapturePreferences.MODE_SHIZUKU,
            CapturePreferences.resolveMode(CapturePreferences.MODE_SHIZUKU, true, false),
        )
    }

    @Test
    fun explicitAccessibilityDoesNotFallBack() {
        assertNull(CapturePreferences.resolveMode(CapturePreferences.MODE_A11Y, true, false))
        assertEquals(
            CapturePreferences.MODE_A11Y,
            CapturePreferences.resolveMode(CapturePreferences.MODE_A11Y, false, true),
        )
    }
}
