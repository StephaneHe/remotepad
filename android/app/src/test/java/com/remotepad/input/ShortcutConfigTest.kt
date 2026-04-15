package com.remotepad.input

import com.remotepad.model.RemoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutConfigTest {

    @Test
    fun `default shortcuts not empty`() {
        assertTrue(ShortcutConfig.defaultShortcuts.isNotEmpty())
    }

    @Test
    fun `ctrl+a shortcut`() {
        val shortcut = ShortcutConfig.defaultShortcuts.first { it.label == "Ctrl+A" }
        assertEquals(RemoteEvent.KeyCombo(listOf("ctrl", "a")), shortcut.event)
    }

    @Test
    fun `ctrl+c shortcut`() {
        val shortcut = ShortcutConfig.defaultShortcuts.first { it.label == "Ctrl+C" }
        assertEquals(RemoteEvent.KeyCombo(listOf("ctrl", "c")), shortcut.event)
    }

    @Test
    fun `ctrl+v shortcut`() {
        val shortcut = ShortcutConfig.defaultShortcuts.first { it.label == "Ctrl+V" }
        assertEquals(RemoteEvent.KeyCombo(listOf("ctrl", "v")), shortcut.event)
    }

    @Test
    fun `ctrl+z shortcut`() {
        val shortcut = ShortcutConfig.defaultShortcuts.first { it.label == "Ctrl+Z" }
        assertEquals(RemoteEvent.KeyCombo(listOf("ctrl", "z")), shortcut.event)
    }

    @Test
    fun `alt+tab shortcut`() {
        val shortcut = ShortcutConfig.defaultShortcuts.first { it.label == "Alt+Tab" }
        assertEquals(RemoteEvent.KeyCombo(listOf("alt", "tab")), shortcut.event)
    }

    @Test
    fun `win shortcut`() {
        val shortcut = ShortcutConfig.defaultShortcuts.first { it.label == "Win" }
        assertEquals(RemoteEvent.KeyPress("win", emptyList()), shortcut.event)
    }

    @Test
    fun `navigation shortcuts contains arrows`() {
        val labels = ShortcutConfig.navigationShortcuts.map { it.label }
        assertTrue(labels.contains("◀"))
        assertTrue(labels.contains("▲"))
        assertTrue(labels.contains("▼"))
        assertTrue(labels.contains("▶"))
    }

    @Test
    fun `left arrow sends Left key`() {
        val shortcut = ShortcutConfig.navigationShortcuts.first { it.label == "◀" }
        assertEquals(RemoteEvent.KeyPress("Left", emptyList()), shortcut.event)
    }
}
