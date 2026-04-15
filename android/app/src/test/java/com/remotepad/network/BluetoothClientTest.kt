package com.remotepad.network

import com.remotepad.model.AuthResponse
import com.remotepad.model.RemoteEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothClientTest {

    private lateinit var fakeSocket: FakeBtSocketProvider
    private lateinit var client: BluetoothClient

    @Before
    fun setUp() {
        fakeSocket = FakeBtSocketProvider()
        val scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher())
        client = BluetoothClient(fakeSocket, scope)
    }

    @Test
    fun `bt connection succeeds`() = runTest {
        client.connect("AA:BB:CC:DD:EE:FF", 0)
        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
        assertTrue(fakeSocket.connected)
    }

    @Test
    fun `bt send events`() = runTest {
        client.connect("AA:BB:CC:DD:EE:FF", 0)
        client.sendEvent(RemoteEvent.MouseMove(10, -5))
        val sent = fakeSocket.outputData()
        assertTrue("Should contain mouse_move", sent.contains("mouse_move"))
    }

    @Test
    fun `bt auth success`() = runTest {
        fakeSocket.responseLines.add("""{"type":"auth_response","success":true,"message":"OK"}""")
        client.connect("AA:BB:CC:DD:EE:FF", 0)
        val resp = client.authenticate("1234")
        assertTrue(resp.success)
        assertEquals(ConnectionState.AUTHENTICATED, client.connectionState.value)
    }

    @Test
    fun `bt disconnect`() = runTest {
        client.connect("AA:BB:CC:DD:EE:FF", 0)
        client.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    // -- Fake ----------------------------------------------------------------

    private class FakeBtSocketProvider : BtSocketProvider {
        var connected = false
        private val outStream = ByteArrayOutputStream()
        val responseLines = mutableListOf<String>()

        override fun connect(macAddress: String) { connected = true }

        override fun inputReader(): BufferedReader {
            val data = responseLines.joinToString("\n") + "\n"
            return BufferedReader(InputStreamReader(ByteArrayInputStream(data.toByteArray())))
        }

        override fun outputStream(): OutputStream = outStream
        override fun close() { connected = false }
        override val isConnected: Boolean get() = connected

        fun outputData(): String = outStream.toString(Charsets.UTF_8.name())
    }
}
