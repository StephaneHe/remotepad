package com.remotepad.network

import com.remotepad.model.AuthResponse
import com.remotepad.model.RemoteEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerTest {

    private lateinit var fakeWifi: FakeConnection
    private lateinit var fakeBt: FakeConnection
    private lateinit var manager: ConnectionManager

    @Before
    fun setUp() {
        fakeWifi = FakeConnection("wifi")
        fakeBt = FakeConnection("bt")
        val scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher())
        manager = ConnectionManager(fakeWifi, fakeBt, scope)
        manager.configure("192.168.1.1", 9876, "AA:BB:CC:DD:EE:FF")
    }

    // -- WiFi priority -------------------------------------------------------

    @Test
    fun `wifi is preferred when available`() = runTest {
        manager.connect("192.168.1.1", 9876)
        assertEquals(ConnectionState.CONNECTED, manager.connectionState.value)
        assertEquals(fakeWifi, manager.getActiveTransport())
    }

    // -- Fallback to BT ------------------------------------------------------

    @Test
    fun `fallback to bt when wifi fails`() = runTest {
        fakeWifi.shouldFail = true
        manager.connect("192.168.1.1", 9876)
        assertEquals(ConnectionState.CONNECTED, manager.connectionState.value)
        assertEquals(fakeBt, manager.getActiveTransport())
    }

    // -- Both fail -----------------------------------------------------------

    @Test
    fun `both unavailable yields disconnected`() = runTest {
        fakeWifi.shouldFail = true
        fakeBt.shouldFail = true
        manager.connect("192.168.1.1", 9876)
        assertEquals(ConnectionState.DISCONNECTED, manager.connectionState.value)
        assertNull(manager.getActiveTransport())
    }

    // -- Events routed -------------------------------------------------------

    @Test
    fun `events sent via active connection`() = runTest {
        manager.connect("192.168.1.1", 9876)
        manager.sendEvent(RemoteEvent.MouseMove(5, 3))
        assertEquals(1, fakeWifi.sentEvents.size)
        assertEquals(RemoteEvent.MouseMove(5, 3), fakeWifi.sentEvents[0])
    }

    // -- Unified state -------------------------------------------------------

    @Test
    fun `connection state reflects active transport`() = runTest {
        assertEquals(ConnectionState.DISCONNECTED, manager.connectionState.value)
        manager.connect("192.168.1.1", 9876)
        assertEquals(ConnectionState.CONNECTED, manager.connectionState.value)
        manager.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, manager.connectionState.value)
    }

    // -- Fake ----------------------------------------------------------------

    private class FakeConnection(val name: String) : RemoteConnection {
        private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
        override val connectionState: StateFlow<ConnectionState> = _state
        override val cursorRegionFrame: StateFlow<ByteArray?> = MutableStateFlow(null)

        var shouldFail = false
        var sentEvents = mutableListOf<RemoteEvent>()
        var authResponse = AuthResponse(true, "OK")

        override suspend fun connect(host: String, port: Int) {
            if (shouldFail) throw Exception("$name connection failed")
            _state.value = ConnectionState.CONNECTED
        }

        override suspend fun disconnect() {
            _state.value = ConnectionState.DISCONNECTED
        }

        override suspend fun sendEvent(event: RemoteEvent) {
            sentEvents.add(event)
        }

        override suspend fun authenticate(pin: String): AuthResponse {
            if (authResponse.success) _state.value = ConnectionState.AUTHENTICATED
            return authResponse
        }
    }
}
