package com.remotepad.input

import com.remotepad.model.RemoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyboardInputHandlerTest {

    private val events = mutableListOf<RemoteEvent>()
    private lateinit var handler: KeyboardInputHandler

    @Before
    fun setUp() {
        events.clear()
        handler = KeyboardInputHandler { events.add(it) }
    }

    // -- Text input -----------------------------------------------------------

    @Test
    fun `text input sent`() {
        handler.onTextCommitted("hello")
        assertEquals(1, events.size)
        assertEquals(RemoteEvent.TextInput("hello"), events[0])
    }

    @Test
    fun `empty text not sent`() {
        handler.onTextCommitted("")
        assertTrue(events.isEmpty())
    }

    @Test
    fun `single character text input`() {
        handler.onTextCommitted("a")
        assertEquals(RemoteEvent.TextInput("a"), events[0])
    }

    // -- Special keys ---------------------------------------------------------

    @Test
    fun `enter key`() {
        handler.onSpecialKey(KeyMap.KEYCODE_ENTER)
        assertEquals(1, events.size)
        assertEquals(RemoteEvent.KeyPress("Return", emptyList()), events[0])
    }

    @Test
    fun `backspace key`() {
        handler.onSpecialKey(KeyMap.KEYCODE_DEL)
        assertEquals(RemoteEvent.KeyPress("BackSpace", emptyList()), events[0])
    }

    @Test
    fun `delete key`() {
        handler.onSpecialKey(KeyMap.KEYCODE_FORWARD_DEL)
        assertEquals(RemoteEvent.KeyPress("Delete", emptyList()), events[0])
    }

    @Test
    fun `tab key`() {
        handler.onSpecialKey(KeyMap.KEYCODE_TAB)
        assertEquals(RemoteEvent.KeyPress("Tab", emptyList()), events[0])
    }

    @Test
    fun `escape key`() {
        handler.onSpecialKey(KeyMap.KEYCODE_ESCAPE)
        assertEquals(RemoteEvent.KeyPress("Escape", emptyList()), events[0])
    }

    @Test
    fun `arrow keys`() {
        handler.onSpecialKey(KeyMap.KEYCODE_DPAD_LEFT)
        handler.onSpecialKey(KeyMap.KEYCODE_DPAD_RIGHT)
        handler.onSpecialKey(KeyMap.KEYCODE_DPAD_UP)
        handler.onSpecialKey(KeyMap.KEYCODE_DPAD_DOWN)

        assertEquals(4, events.size)
        assertEquals(RemoteEvent.KeyPress("Left", emptyList()), events[0])
        assertEquals(RemoteEvent.KeyPress("Right", emptyList()), events[1])
        assertEquals(RemoteEvent.KeyPress("Up", emptyList()), events[2])
        assertEquals(RemoteEvent.KeyPress("Down", emptyList()), events[3])
    }

    @Test
    fun `unknown key code ignored`() {
        handler.onSpecialKey(9999)
        assertTrue(events.isEmpty())
    }
}
