package com.iji.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一键授权里可纯 JVM 验证的部分：命令构造、服务列表追加/摘除、备份编解码。
 * 真正写 `Settings.Secure` 的 suspend 函数需要 Shizuku + 真机，留待设备接入后走查。
 */
class AccessibilityShortcutHelperTest {

    private val mine = "com.iji.app/.accessibility.VolumeKeyCaptureService"
    private val other = "com.other/.SomeService"

    @Test
    fun bindCommandsWriteAllFiveKeys() {
        val commands = AccessibilityShortcutHelper.buildBindCommands(mine)
        assertEquals(5, commands.size)
        assertTrue(commands.all { it.startsWith("settings put secure ") })
        assertTrue(commands.any { it.contains("accessibility_shortcut_target_service") && it.contains(mine) })
        assertTrue(commands.any { it.contains("accessibility_shortcut_targets") && it.contains(mine) })
        assertTrue(commands.any { it.contains("accessibility_shortcut_enabled \"1\"") })
    }

    @Test
    fun appendServiceKeepsOtherServicesAndDoesNotDuplicate() {
        val once = AccessibilityShortcutHelper.appendService(other, mine)
        assertEquals("$other:$mine", once)
        assertEquals(once, AccessibilityShortcutHelper.appendService(once, mine))
    }

    @Test
    fun appendServiceHandlesEmptyAndNull() {
        assertEquals(mine, AccessibilityShortcutHelper.appendService(null, mine))
        assertEquals(mine, AccessibilityShortcutHelper.appendService("", mine))
    }

    @Test
    fun removeServiceOnlyRemovesOurs() {
        assertEquals(
            other,
            AccessibilityShortcutHelper.removeService("$other:$mine", mine),
        )
        assertEquals("", AccessibilityShortcutHelper.removeService(mine, mine))
        assertEquals("", AccessibilityShortcutHelper.removeService(null, mine))
    }

    @Test
    fun backupRoundTripKeepsMissingKeysAsNull() {
        val values = mapOf(
            AccessibilityShortcutHelper.SHORTCUT_KEYS[0] to mine,
            AccessibilityShortcutHelper.SHORTCUT_KEYS[1] to null,
            AccessibilityShortcutHelper.SHORTCUT_KEYS[2] to "0",
            AccessibilityShortcutHelper.SHORTCUT_KEYS[3] to null,
            AccessibilityShortcutHelper.SHORTCUT_KEYS[4] to "1",
        )
        val decoded = AccessibilityShortcutHelper.decodeBackup(
            AccessibilityShortcutHelper.encodeBackup(values),
        )
        assertEquals(values, decoded)
    }

    @Test
    fun restoreUsesDeleteForKeysThatDidNotExist() {
        val commands = AccessibilityShortcutHelper.buildRestoreCommands(
            mapOf(AccessibilityShortcutHelper.SHORTCUT_KEYS[0] to other) +
                AccessibilityShortcutHelper.SHORTCUT_KEYS.drop(1).associateWith { null },
        )
        assertEquals(5, commands.size)
        assertTrue(commands[0].startsWith("settings put secure "))
        assertTrue(commands[0].contains(other))
        assertTrue(commands.drop(1).all { it.startsWith("settings delete secure ") })
    }

    @Test
    fun restoreNeverRewritesOtherServicesList() {
        val commands = AccessibilityShortcutHelper.buildRestoreCommands(emptyMap())
        assertTrue(commands.isEmpty())
        assertFalse(commands.any { it.contains("enabled_accessibility_services") })
    }

    @Test
    fun partialRestoreTouchesOnlyBackedUpKeys() {
        val commands = AccessibilityShortcutHelper.buildRestoreCommands(
            mapOf(AccessibilityShortcutHelper.SHORTCUT_KEYS[0] to null),
        )
        assertEquals(1, commands.size)
        assertTrue(commands[0].startsWith("settings delete secure "))
    }
}
