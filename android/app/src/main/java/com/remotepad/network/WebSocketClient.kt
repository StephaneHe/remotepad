package com.remotepad.network

import android.util.Log
import com.remotepad.model.AuthResponse
import com.remotepad.model.RemoteEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket-based implementation of [RemoteConnection].
 *
 * Manages connection lifecycle, authentication, and automatic
 * reconnection with exponential backoff.
 */
class WebSocketClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) : RemoteConnection {

    companion object {
        private const val TAG = "RemotePad"
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _cursorRegionFrame = MutableStateFlow<ByteArray?>(null)
    override val cursorRegionFrame: StateFlow<ByteArray?> = _cursorRegionFrame.asStateFlow()

    private var webSocket: WebSocket? = null
    private var host: String = ""
    private var port: Int = 0
    private var autoReconnect: Boolean = true

    // Pending auth response
    private var authDeferred: CompletableDeferred<AuthResponse>? = null

    // Connection attempt deferred — completes on open or failure
    private var connectDeferred: CompletableDeferred<Boolean>? = null

    // Reconnection backoff
    private var reconnectAttempt = 0
    private val maxBackoffMs = 30_000L
    private val baseBackoffMs = 1_000L

    override suspend fun connect(host: String, port: Int) {
        this.host = host
        this.port = port
        this.autoReconnect = false  // Don't auto-reconnect during initial connect
        this.reconnectAttempt = 0
        Log.i(TAG, "connect() called: host=$host port=$port")
        val deferred = CompletableDeferred<Boolean>()
        connectDeferred = deferred
        doConnect()
        // Wait for the WebSocket to either open or fail
        val success = deferred.await()
        if (!success) {
            throw Exception("WebSocket connection failed to $host:$port")
        }
        this.autoReconnect = true
        Log.i(TAG, "connect() succeeded")
    }

    override suspend fun disconnect() {
        autoReconnect = false
        webSocket?.close(1000, "Client disconnect")
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "disconnect()")
    }

    /**
     * Fully shut down this client, cancelling all coroutines.
     */
    fun close() {
        autoReconnect = false
        webSocket?.cancel()
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        job.cancel()
    }

    override suspend fun sendEvent(event: RemoteEvent) {
        if (_connectionState.value != ConnectionState.AUTHENTICATED) return
        val json = MessageSerializer.serialize(event)
        webSocket?.send(json)
    }

    override suspend fun authenticate(pin: String): AuthResponse {
        val state = _connectionState.value
        Log.i(TAG, "authenticate() state=$state")
        if (state != ConnectionState.CONNECTED && state != ConnectionState.AUTHENTICATED) {
            return AuthResponse(success = false, message = "Not connected")
        }
        val deferred = CompletableDeferred<AuthResponse>()
        authDeferred = deferred
        val json = MessageSerializer.serialize(RemoteEvent.Auth(pin))
        webSocket?.send(json)
        return deferred.await()
    }

    // -- Internal -----------------------------------------------------------

    private fun doConnect() {
        _connectionState.value = ConnectionState.CONNECTING
        val url = "ws://$host:$port"
        Log.i(TAG, "doConnect() url=$url")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, Listener())
    }

    private fun scheduleReconnect() {
        if (!autoReconnect) return
        scope.launch {
            val backoff = (baseBackoffMs * (1L shl reconnectAttempt.coerceAtMost(5)))
                .coerceAtMost(maxBackoffMs)
            reconnectAttempt++
            Log.i(TAG, "scheduleReconnect attempt=$reconnectAttempt backoff=${backoff}ms")
            delay(backoff)
            if (autoReconnect && _connectionState.value == ConnectionState.DISCONNECTED) {
                doConnect()
            }
        }
    }

    // -- WebSocket listener -------------------------------------------------

    private inner class Listener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "onOpen()")
            reconnectAttempt = 0
            _connectionState.value = ConnectionState.CONNECTED
            connectDeferred?.complete(true)
            connectDeferred = null
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.i(TAG, "onMessage: $text")
            try {
                val authResponse = MessageSerializer.deserializeAuthResponse(text)
                if (authResponse.success) {
                    _connectionState.value = ConnectionState.AUTHENTICATED
                }
                authDeferred?.complete(authResponse)
                authDeferred = null
            } catch (_: Exception) { }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            _cursorRegionFrame.value = bytes.toByteArray()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "onClosing code=$code reason=$reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "onClosed code=$code")
            _connectionState.value = ConnectionState.DISCONNECTED
            _cursorRegionFrame.value = null
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "onFailure: ${t.message}", t)
            _connectionState.value = ConnectionState.DISCONNECTED
            connectDeferred?.complete(false)
            connectDeferred = null
            authDeferred?.complete(AuthResponse(success = false, message = t.message ?: "Connection failed"))
            authDeferred = null
            scheduleReconnect()
        }
    }
}
