package com.remotepad.network

import com.remotepad.model.AuthResponse
import com.remotepad.model.RemoteEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes [RemoteEvent] instances to JSON and deserializes server responses.
 */
object MessageSerializer {

    fun serialize(event: RemoteEvent): String = when (event) {
        is RemoteEvent.MouseMove -> JSONObject().apply {
            put("type", "mouse_move")
            put("dx", event.dx)
            put("dy", event.dy)
        }.toString()

        is RemoteEvent.MouseClick -> JSONObject().apply {
            put("type", "mouse_click")
            put("button", event.button)
        }.toString()

        is RemoteEvent.MouseDoubleClick -> JSONObject().apply {
            put("type", "mouse_double_click")
            put("button", event.button)
        }.toString()

        is RemoteEvent.MouseScroll -> JSONObject().apply {
            put("type", "mouse_scroll")
            put("dx", event.dx)
            put("dy", event.dy)
        }.toString()

        is RemoteEvent.KeyPress -> JSONObject().apply {
            put("type", "key_press")
            put("key", event.key)
            put("modifiers", JSONArray(event.modifiers))
        }.toString()

        is RemoteEvent.KeyCombo -> JSONObject().apply {
            put("type", "key_combo")
            put("keys", JSONArray(event.keys))
        }.toString()

        is RemoteEvent.TextInput -> JSONObject().apply {
            put("type", "text_input")
            put("text", event.text)
        }.toString()

        is RemoteEvent.Auth -> JSONObject().apply {
            put("type", "auth")
            put("pin", event.pin)
        }.toString()
    }

    fun deserializeAuthResponse(json: String): AuthResponse {
        val obj = JSONObject(json)
        return AuthResponse(
            success = obj.getBoolean("success"),
            message = obj.optString("message", "")
        )
    }
}
