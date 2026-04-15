package com.remotepad.input

import com.remotepad.model.RemoteEvent

/**
 * Maps Android key codes to RemotePad key names.
 *
 * Uses integer constants matching android.view.KeyEvent values,
 * but decoupled from the Android SDK for testability.
 */
object KeyMap {
    // Android KeyEvent constants (duplicated to avoid SDK dependency in tests)
    const val KEYCODE_ENTER = 66
    const val KEYCODE_DEL = 67        // Backspace
    const val KEYCODE_FORWARD_DEL = 112 // Delete
    const val KEYCODE_TAB = 61
    const val KEYCODE_ESCAPE = 111
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20

    private val SPECIAL_KEYS = mapOf(
        KEYCODE_ENTER to "Return",
        KEYCODE_DEL to "BackSpace",
        KEYCODE_FORWARD_DEL to "Delete",
        KEYCODE_TAB to "Tab",
        KEYCODE_ESCAPE to "Escape",
        KEYCODE_DPAD_LEFT to "Left",
        KEYCODE_DPAD_RIGHT to "Right",
        KEYCODE_DPAD_UP to "Up",
        KEYCODE_DPAD_DOWN to "Down"
    )

    /**
     * Returns the RemotePad key name for an Android keyCode, or null if not mapped.
     */
    fun toRemoteKey(keyCode: Int): String? = SPECIAL_KEYS[keyCode]
}

/**
 * Handles keyboard input from the Android IME and translates it
 * into [RemoteEvent] instances.
 */
class KeyboardInputHandler(private val emitter: (RemoteEvent) -> Unit) {

    /**
     * Called when the IME commits text (letters, numbers, punctuation).
     * Empty strings are ignored.
     */
    fun onTextCommitted(text: String) {
        if (text.isEmpty()) return
        emitter(RemoteEvent.TextInput(text))
    }

    /**
     * Called when a special key is pressed (Enter, Backspace, arrows, etc.).
     * Unknown key codes are silently ignored.
     */
    fun onSpecialKey(keyCode: Int) {
        val keyName = KeyMap.toRemoteKey(keyCode) ?: return
        emitter(RemoteEvent.KeyPress(keyName, emptyList()))
    }
}
