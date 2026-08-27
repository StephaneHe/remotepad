package com.remotepad.network

import com.remotepad.model.AuthResponse
import com.remotepad.model.RemoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates WiFi and Bluetooth transports with automatic failover.
 *
 * Priority: WiFi first, Bluetooth as fallback.
 * Exposes a unified [ConnectionState] reflecting the active transport.
 *
 * NOTE: the Bluetooth transport is a **work in progress** and is disabled by
 * default ([bluetoothEnabled] = false). The runtime permissions required on
 * Android 12+ (BLUETOOTH_CONNECT/SCAN) are not yet requested, so the BT paths
 * would fail. The code and tests are kept for the eventual completion of the
 * feature, but production wiring uses WiFi only. Do not enable in a release.
 */
class ConnectionManager(
    private val wifiClient: RemoteConnection,
    private val btClient: RemoteConnection,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val bluetoothEnabled: Boolean = false
) : RemoteConnection {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _cursorRegionFrame = MutableStateFlow<ByteArray?>(null)
    override val cursorRegionFrame: StateFlow<ByteArray?> = _cursorRegionFrame.asStateFlow()

    private var activeConnection: RemoteConnection? = null
    private var host: String = ""
    private var port: Int = 0
    private var btAddress: String = ""
    private var pin: String = ""

    /**
     * Configure connection parameters.
     * @param wifiHost IP address for WiFi.
     * @param wifiPort Port for WiFi.
     * @param btMac MAC address for Bluetooth.
     */
    fun configure(wifiHost: String, wifiPort: Int, btMac: String = "") {
        host = wifiHost
        port = wifiPort
        btAddress = btMac
    }

    override suspend fun connect(host: String, port: Int) {
        this.host = host
        this.port = port
        _connectionState.value = ConnectionState.CONNECTING

        // Try WiFi first
        try {
            wifiClient.connect(host, port)
            activeConnection = wifiClient
            _connectionState.value = ConnectionState.CONNECTED
            monitorActive()
            return
        } catch (_: Exception) { }

        // Fallback to Bluetooth (WIP: disabled unless explicitly enabled)
        if (bluetoothEnabled && btAddress.isNotEmpty()) {
            try {
                btClient.connect(btAddress, 0)
                activeConnection = btClient
                _connectionState.value = ConnectionState.CONNECTED
                monitorActive()
                return
            } catch (_: Exception) { }
        }

        // Both failed
        activeConnection = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun disconnect() {
        activeConnection?.disconnect()
        activeConnection = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _cursorRegionFrame.value = null
    }

    override suspend fun sendEvent(event: RemoteEvent) {
        activeConnection?.sendEvent(event)
    }

    override suspend fun authenticate(pin: String): AuthResponse {
        this.pin = pin
        val conn = activeConnection ?: return AuthResponse(false, "Not connected")
        val resp = conn.authenticate(pin)
        if (resp.success) {
            _connectionState.value = ConnectionState.AUTHENTICATED
        }
        return resp
    }

    /**
     * Switch from current transport to WiFi.
     */
    suspend fun switchToWifi() {
        try {
            wifiClient.connect(host, port)
            val oldConnection = activeConnection
            activeConnection = wifiClient
            _connectionState.value = ConnectionState.CONNECTED
            oldConnection?.disconnect()
            if (pin.isNotEmpty()) {
                authenticate(pin)
            }
        } catch (_: Exception) { }
    }

    /**
     * Switch from current transport to Bluetooth.
     */
    suspend fun switchToBluetooth() {
        if (!bluetoothEnabled || btAddress.isEmpty()) return
        try {
            btClient.connect(btAddress, 0)
            val oldConnection = activeConnection
            activeConnection = btClient
            _connectionState.value = ConnectionState.CONNECTED
            oldConnection?.disconnect()
            if (pin.isNotEmpty()) {
                authenticate(pin)
            }
        } catch (_: Exception) { }
    }

    /**
     * Returns the currently active transport, or null.
     */
    fun getActiveTransport(): RemoteConnection? = activeConnection

    private fun monitorActive() {
        // Monitor the active connection state and relay changes
        val conn = activeConnection ?: return
        scope.launch {
            conn.connectionState.collect { state ->
                if (conn == activeConnection) {
                    if (state == ConnectionState.DISCONNECTED && _connectionState.value != ConnectionState.DISCONNECTED) {
                        // Active connection lost — attempt failover
                        attemptFailover()
                    }
                }
            }
        }
        // Forward cursor region frames from the active connection
        scope.launch {
            conn.cursorRegionFrame.collect { frame ->
                if (conn == activeConnection) {
                    _cursorRegionFrame.value = frame
                }
            }
        }
    }

    private suspend fun attemptFailover() {
        _connectionState.value = ConnectionState.CONNECTING

        // If WiFi was active, try BT (WIP: disabled unless explicitly enabled)
        if (bluetoothEnabled && activeConnection == wifiClient && btAddress.isNotEmpty()) {
            try {
                btClient.connect(btAddress, 0)
                activeConnection = btClient
                _connectionState.value = ConnectionState.CONNECTED
                if (pin.isNotEmpty()) authenticate(pin)
                return
            } catch (_: Exception) { }
        }

        // If BT was active, try WiFi
        if (activeConnection == btClient) {
            try {
                wifiClient.connect(host, port)
                activeConnection = wifiClient
                _connectionState.value = ConnectionState.CONNECTED
                if (pin.isNotEmpty()) authenticate(pin)
                return
            } catch (_: Exception) { }
        }

        activeConnection = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
