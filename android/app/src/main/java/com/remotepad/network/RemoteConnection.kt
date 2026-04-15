package com.remotepad.network

import com.remotepad.model.AuthResponse
import com.remotepad.model.RemoteEvent
import kotlinx.coroutines.flow.StateFlow

/**
 * Connection states for the remote server link.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATED
}

/**
 * Abstract interface for a remote connection transport.
 * Allows swapping WebSocket for Bluetooth in the future.
 */
interface RemoteConnection {
    val connectionState: StateFlow<ConnectionState>
    val cursorRegionFrame: StateFlow<ByteArray?>
    suspend fun connect(host: String, port: Int)
    suspend fun disconnect()
    suspend fun sendEvent(event: RemoteEvent)
    suspend fun authenticate(pin: String): AuthResponse
}
