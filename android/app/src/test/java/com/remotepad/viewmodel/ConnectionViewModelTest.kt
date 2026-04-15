package com.remotepad.viewmodel

import com.remotepad.model.AuthResponse
import com.remotepad.model.RemoteEvent
import com.remotepad.network.ConnectionState
import com.remotepad.network.RemoteConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = kotlinx.coroutines.CoroutineScope(testDispatcher)

    private lateinit var fakeConnection: FakeRemoteConnection
    private lateinit var fakePrefs: FakePreferencesStore
    private lateinit var viewModel: ConnectionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeConnection = FakeRemoteConnection()
        fakePrefs = FakePreferencesStore()
        viewModel = ConnectionViewModel(fakeConnection, fakePrefs, testScope)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
    }

    // -- IP validation -------------------------------------------------------

    @Test
    fun `valid IP accepted`() {
        assertTrue(InputValidator.isValidIp("192.168.1.10"))
        assertTrue(InputValidator.isValidIp("10.0.0.1"))
        assertTrue(InputValidator.isValidIp("0.0.0.0"))
        assertTrue(InputValidator.isValidIp("255.255.255.255"))
    }

    @Test
    fun `invalid IP rejected`() {
        assertFalse(InputValidator.isValidIp("999.999.999.999"))
        assertFalse(InputValidator.isValidIp("abc"))
        assertFalse(InputValidator.isValidIp(""))
        assertFalse(InputValidator.isValidIp("192.168.1"))
        assertFalse(InputValidator.isValidIp("192.168.1.256"))
    }

    // -- Port validation -----------------------------------------------------

    @Test
    fun `valid port accepted`() {
        assertTrue(InputValidator.isValidPort("9876"))
        assertTrue(InputValidator.isValidPort("1024"))
        assertTrue(InputValidator.isValidPort("65535"))
    }

    @Test
    fun `invalid port rejected`() {
        assertFalse(InputValidator.isValidPort("1023"))
        assertFalse(InputValidator.isValidPort("65536"))
        assertFalse(InputValidator.isValidPort("abc"))
        assertFalse(InputValidator.isValidPort(""))
    }

    // -- PIN validation ------------------------------------------------------

    @Test
    fun `valid pin accepted`() {
        assertTrue(InputValidator.isValidPin("1234"))
        assertTrue(InputValidator.isValidPin("0000"))
    }

    @Test
    fun `invalid pin rejected`() {
        assertFalse(InputValidator.isValidPin("123"))
        assertFalse(InputValidator.isValidPin("12345"))
        assertFalse(InputValidator.isValidPin("abcd"))
        assertFalse(InputValidator.isValidPin(""))
    }

    // -- Form validation -----------------------------------------------------

    @Test
    fun `form valid only when all fields valid`() {
        assertFalse(viewModel.isFormValid.value)

        viewModel.ip.value = "192.168.1.10"
        assertFalse(viewModel.isFormValid.value)

        viewModel.port.value = "9876"
        assertFalse(viewModel.isFormValid.value)

        viewModel.pin.value = "1234"
        assertTrue(viewModel.isFormValid.value)
    }

    // -- Connection state display -------------------------------------------

    @Test
    fun `connection state reflects remote connection`() {
        assertEquals(ConnectionState.DISCONNECTED, viewModel.connectionState.value)

        fakeConnection.setState(ConnectionState.CONNECTING)
        assertEquals(ConnectionState.CONNECTING, viewModel.connectionState.value)

        fakeConnection.setState(ConnectionState.CONNECTED)
        assertEquals(ConnectionState.CONNECTED, viewModel.connectionState.value)

        fakeConnection.setState(ConnectionState.AUTHENTICATED)
        assertEquals(ConnectionState.AUTHENTICATED, viewModel.connectionState.value)
    }

    // -- Settings persistence -----------------------------------------------

    @Test
    fun `settings persisted on connect`() {
        viewModel.ip.value = "192.168.1.50"
        viewModel.port.value = "5555"
        viewModel.pin.value = "9999"

        fakeConnection.authResponse = AuthResponse(success = true, message = "OK")
        viewModel.connect()

        assertEquals("192.168.1.50", fakePrefs.getString("last_ip", ""))
        assertEquals("5555", fakePrefs.getString("last_port", ""))
    }

    @Test
    fun `settings restored on init`() {
        fakePrefs.putString("last_ip", "10.0.0.5")
        fakePrefs.putString("last_port", "4444")

        val vm = ConnectionViewModel(fakeConnection, fakePrefs, testScope)
        assertEquals("10.0.0.5", vm.ip.value)
        assertEquals("4444", vm.port.value)
    }

    // -- Auth error ----------------------------------------------------------

    @Test
    fun `auth error displayed`() {
        viewModel.ip.value = "192.168.1.10"
        viewModel.port.value = "9876"
        viewModel.pin.value = "0000"

        fakeConnection.authResponse = AuthResponse(success = false, message = "Invalid PIN")
        viewModel.connect()

        assertEquals("Invalid PIN", viewModel.errorMessage.value)
    }

    @Test
    fun `successful auth clears error`() {
        viewModel.ip.value = "192.168.1.10"
        viewModel.port.value = "9876"
        viewModel.pin.value = "1234"

        fakeConnection.authResponse = AuthResponse(success = true, message = "OK")
        viewModel.connect()

        assertNull(viewModel.errorMessage.value)
    }

    // -- Fakes ---------------------------------------------------------------

    private class FakeRemoteConnection : RemoteConnection {
        private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
        override val connectionState: StateFlow<ConnectionState> = _state
        override val cursorRegionFrame: StateFlow<ByteArray?> = MutableStateFlow(null)

        var authResponse = AuthResponse(success = true, message = "OK")

        fun setState(state: ConnectionState) { _state.value = state }

        override suspend fun connect(host: String, port: Int) {
            _state.value = ConnectionState.CONNECTING
            _state.value = ConnectionState.CONNECTED
        }

        override suspend fun disconnect() {
            _state.value = ConnectionState.DISCONNECTED
        }

        override suspend fun sendEvent(event: RemoteEvent) {}

        override suspend fun authenticate(pin: String): AuthResponse {
            if (authResponse.success) _state.value = ConnectionState.AUTHENTICATED
            return authResponse
        }
    }

    private class FakePreferencesStore : PreferencesStore {
        private val data = mutableMapOf<String, String>()
        override fun getString(key: String, default: String): String = data[key] ?: default
        override fun putString(key: String, value: String) { data[key] = value }
    }
}
