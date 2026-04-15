package com.remotepad.network

import com.remotepad.model.AuthResponse
import com.remotepad.model.RemoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * Abstraction over a raw Bluetooth socket for testability.
 */
interface BtSocketProvider {
    /** Connect to the given MAC address. */
    fun connect(macAddress: String)
    /** Get the input reader (newline-delimited JSON). */
    fun inputReader(): BufferedReader
    /** Get the output stream. */
    fun outputStream(): OutputStream
    /** Close the connection. */
    fun close()
    /** Whether the socket is connected. */
    val isConnected: Boolean
}

/**
 * Bluetooth RFCOMM client implementing [RemoteConnection].
 *
 * Messages are sent as newline-delimited JSON, matching the server protocol.
 *
 * @param socketProvider Injectable socket layer (real BT or fake for tests).
 * @param scope Coroutine scope for background I/O.
 */
class BluetoothClient(
    private val socketProvider: BtSocketProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : RemoteConnection {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    override val cursorRegionFrame: StateFlow<ByteArray?> = MutableStateFlow(null)

    private var reader: BufferedReader? = null
    private var output: OutputStream? = null

    override suspend fun connect(host: String, port: Int) {
        _connectionState.value = ConnectionState.CONNECTING
        try {
            withContext(Dispatchers.IO) {
                socketProvider.connect(host) // host = MAC address for BT
            }
            output = socketProvider.outputStream()
            reader = socketProvider.inputReader()
            _connectionState.value = ConnectionState.CONNECTED
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.DISCONNECTED
            throw e
        }
    }

    override suspend fun disconnect() {
        try {
            withContext(Dispatchers.IO) {
                socketProvider.close()
            }
        } catch (_: Exception) { }
        reader = null
        output = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendEvent(event: RemoteEvent) {
        val json = MessageSerializer.serialize(event)
        withContext(Dispatchers.IO) {
            output?.let {
                it.write((json + "\n").toByteArray(Charsets.UTF_8))
                it.flush()
            }
        }
    }

    override suspend fun authenticate(pin: String): AuthResponse {
        val authJson = JSONObject().apply {
            put("type", "auth")
            put("pin", pin)
        }.toString()

        return withContext(Dispatchers.IO) {
            output?.let {
                it.write((authJson + "\n").toByteArray(Charsets.UTF_8))
                it.flush()
            }
            // Read server response (blocking on IO dispatcher)
            val line = reader?.readLine() ?: throw Exception("No response from server")
            val resp = MessageSerializer.deserializeAuthResponse(line)
            if (resp.success) {
                _connectionState.value = ConnectionState.AUTHENTICATED
            }
            resp
        }
    }
}
