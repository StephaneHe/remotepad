package com.remotepad.network

import com.remotepad.model.RemoteEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MessageSerializer].
 */
class MessageSerializerTest {

    @Test
    fun `serialize mouse move`() {
        val json = MessageSerializer.serialize(RemoteEvent.MouseMove(10, -5))
        val obj = JSONObject(json)
        assertEquals("mouse_move", obj.getString("type"))
        assertEquals(10, obj.getInt("dx"))
        assertEquals(-5, obj.getInt("dy"))
    }

    @Test
    fun `serialize mouse click`() {
        val json = MessageSerializer.serialize(RemoteEvent.MouseClick("left"))
        val obj = JSONObject(json)
        assertEquals("mouse_click", obj.getString("type"))
        assertEquals("left", obj.getString("button"))
    }

    @Test
    fun `serialize mouse double click`() {
        val json = MessageSerializer.serialize(RemoteEvent.MouseDoubleClick("right"))
        val obj = JSONObject(json)
        assertEquals("mouse_double_click", obj.getString("type"))
        assertEquals("right", obj.getString("button"))
    }

    @Test
    fun `serialize mouse scroll`() {
        val json = MessageSerializer.serialize(RemoteEvent.MouseScroll(0, 3))
        val obj = JSONObject(json)
        assertEquals("mouse_scroll", obj.getString("type"))
        assertEquals(0, obj.getInt("dx"))
        assertEquals(3, obj.getInt("dy"))
    }

    @Test
    fun `serialize key press`() {
        val json = MessageSerializer.serialize(RemoteEvent.KeyPress("a", listOf("ctrl")))
        val obj = JSONObject(json)
        assertEquals("key_press", obj.getString("type"))
        assertEquals("a", obj.getString("key"))
        val mods = obj.getJSONArray("modifiers")
        assertEquals(1, mods.length())
        assertEquals("ctrl", mods.getString(0))
    }

    @Test
    fun `serialize key combo`() {
        val json = MessageSerializer.serialize(RemoteEvent.KeyCombo(listOf("ctrl", "c")))
        val obj = JSONObject(json)
        assertEquals("key_combo", obj.getString("type"))
        val keys = obj.getJSONArray("keys")
        assertEquals(2, keys.length())
        assertEquals("ctrl", keys.getString(0))
        assertEquals("c", keys.getString(1))
    }

    @Test
    fun `serialize text input`() {
        val json = MessageSerializer.serialize(RemoteEvent.TextInput("hello"))
        val obj = JSONObject(json)
        assertEquals("text_input", obj.getString("type"))
        assertEquals("hello", obj.getString("text"))
    }

    @Test
    fun `serialize auth`() {
        val json = MessageSerializer.serialize(RemoteEvent.Auth("1234"))
        val obj = JSONObject(json)
        assertEquals("auth", obj.getString("type"))
        assertEquals("1234", obj.getString("pin"))
    }

    @Test
    fun `deserialize auth response success`() {
        val json = """{"type":"auth_response","success":true,"message":"Authenticated"}"""
        val resp = MessageSerializer.deserializeAuthResponse(json)
        assertTrue(resp.success)
        assertEquals("Authenticated", resp.message)
    }

    @Test
    fun `deserialize auth response failure`() {
        val json = """{"type":"auth_response","success":false,"message":"Invalid PIN"}"""
        val resp = MessageSerializer.deserializeAuthResponse(json)
        assertFalse(resp.success)
        assertEquals("Invalid PIN", resp.message)
    }
}
