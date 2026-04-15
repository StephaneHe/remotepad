package com.remotepad.viewmodel

import com.remotepad.model.RemoteEvent
import com.remotepad.network.ConnectionState
import com.remotepad.network.RemoteConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the trackpad screen.
 *
 * Relays [RemoteEvent] instances from the touch processor to the network layer.
 */
class TrackpadViewModel(
    private val connection: RemoteConnection,
    private val scope: CoroutineScope
) {
    val connectionState: StateFlow<ConnectionState> = connection.connectionState
    val cursorRegionFrame: StateFlow<ByteArray?> = connection.cursorRegionFrame

    /**
     * Send a remote event to the server.
     * Silently ignored if not authenticated.
     */
    fun sendEvent(event: RemoteEvent) {
        scope.launch {
            connection.sendEvent(event)
        }
    }

    /**
     * Convenience: send a mouse button click.
     */
    fun onButtonClick(button: String) {
        sendEvent(RemoteEvent.MouseClick(button))
    }
}
