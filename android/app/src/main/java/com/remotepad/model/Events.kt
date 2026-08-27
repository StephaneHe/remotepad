package com.remotepad.model

/**
 * Sealed class representing all remote input events sent to the server.
 */
sealed class RemoteEvent {
    data class MouseMove(val dx: Int, val dy: Int) : RemoteEvent()
    data class MouseClick(val button: String) : RemoteEvent()
    data class MouseDoubleClick(val button: String) : RemoteEvent()
    data class MouseScroll(val dx: Int, val dy: Int) : RemoteEvent()
    data class KeyPress(val key: String, val modifiers: List<String>) : RemoteEvent()
    data class KeyCombo(val keys: List<String>) : RemoteEvent()
    data class TextInput(val text: String) : RemoteEvent()
    data class Zoom(val steps: Int) : RemoteEvent()
    data class Auth(val pin: String) : RemoteEvent()
}

/**
 * Server response to an authentication request.
 */
data class AuthResponse(val success: Boolean, val message: String)
