package com.remotepad.input

import com.remotepad.model.RemoteEvent

/**
 * A shortcut button displayed in the shortcut bar.
 */
data class Shortcut(val label: String, val event: RemoteEvent)

/**
 * Default shortcut configuration for the shortcut bar.
 */
object ShortcutConfig {

    val navigationShortcuts: List<Shortcut> = listOf(
        Shortcut("◀", RemoteEvent.KeyPress("Left", emptyList())),
        Shortcut("▲", RemoteEvent.KeyPress("Up", emptyList())),
        Shortcut("▼", RemoteEvent.KeyPress("Down", emptyList())),
        Shortcut("▶", RemoteEvent.KeyPress("Right", emptyList())),
        Shortcut("Home", RemoteEvent.KeyPress("home", emptyList())),
        Shortcut("End", RemoteEvent.KeyPress("end", emptyList())),
        Shortcut("PgUp", RemoteEvent.KeyPress("page_up", emptyList())),
        Shortcut("PgDn", RemoteEvent.KeyPress("page_down", emptyList())),
        Shortcut("Tab", RemoteEvent.KeyPress("Tab", emptyList())),
        Shortcut("⏎", RemoteEvent.KeyPress("Return", emptyList())),
        Shortcut("Esc", RemoteEvent.KeyPress("Escape", emptyList())),
        Shortcut("🔇", RemoteEvent.KeyPress("volume_mute", emptyList()))
    )

    val defaultShortcuts: List<Shortcut> = listOf(
        Shortcut("Ctrl+A", RemoteEvent.KeyCombo(listOf("ctrl", "a"))),
        Shortcut("Ctrl+C", RemoteEvent.KeyCombo(listOf("ctrl", "c"))),
        Shortcut("Ctrl+V", RemoteEvent.KeyCombo(listOf("ctrl", "v"))),
        Shortcut("Ctrl+X", RemoteEvent.KeyCombo(listOf("ctrl", "x"))),
        Shortcut("Ctrl+Z", RemoteEvent.KeyCombo(listOf("ctrl", "z"))),
        Shortcut("Ctrl+F", RemoteEvent.KeyCombo(listOf("ctrl", "f"))),
        Shortcut("Ctrl+S", RemoteEvent.KeyCombo(listOf("ctrl", "s"))),
        Shortcut("Alt+Tab", RemoteEvent.KeyCombo(listOf("alt", "tab"))),
        Shortcut("Alt+F4", RemoteEvent.KeyCombo(listOf("alt", "f4"))),
        Shortcut("Win", RemoteEvent.KeyPress("win", emptyList()))
    )
}
