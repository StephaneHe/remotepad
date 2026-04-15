package com.remotepad.viewmodel

import android.util.Log
import com.remotepad.model.AuthResponse
import com.remotepad.network.ConnectionState
import com.remotepad.network.RemoteConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the connection screen.
 */
class ConnectionViewModel(
    private val connection: RemoteConnection,
    private val prefs: PreferencesStore,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "RemotePad"
        const val KEY_IP = "last_ip"
        const val KEY_PORT = "last_port"
        const val KEY_PIN = "last_pin"
        const val DEFAULT_PORT = "9876"
    }

    val ip = MutableStateFlow(prefs.getString(KEY_IP, ""))
    val port = MutableStateFlow(prefs.getString(KEY_PORT, DEFAULT_PORT))
    val pin = MutableStateFlow(prefs.getString(KEY_PIN, ""))

    val connectionState: StateFlow<ConnectionState> = connection.connectionState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isFormValid: StateFlow<Boolean> = combine(ip, port, pin) { ipVal, portVal, pinVal ->
        InputValidator.isValidHost(ipVal) &&
            InputValidator.isValidPort(portVal) &&
            InputValidator.isValidPin(pinVal)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    fun connect() {
        _errorMessage.value = null

        // Persist IP and port
        prefs.putString(KEY_IP, ip.value.trim())
        prefs.putString(KEY_PORT, port.value.trim())

        Log.i(TAG, "ViewModel.connect(): ip=${ip.value} port=${port.value}")

        scope.launch {
            try {
                Log.i(TAG, "ViewModel: connecting to ${ip.value}:${port.value}")
                connection.connect(ip.value.trim(), port.value.trim().toInt())

                Log.i(TAG, "ViewModel: connected, authenticating...")
                val response: AuthResponse = connection.authenticate(pin.value.trim())
                Log.i(TAG, "ViewModel: auth response: success=${response.success} msg=${response.message}")
                if (response.success) {
                    // PIN validated: persist it for next session
                    prefs.putString(KEY_PIN, pin.value.trim())
                } else {
                    _errorMessage.value = response.message
                    // PIN rejected: clear field and stored value
                    pin.value = ""
                    prefs.putString(KEY_PIN, "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "ViewModel: connection error", e)
                _errorMessage.value = e.message ?: "Connection failed"
                // Connection failed: clear saved PIN (server may have restarted with a new PIN)
                pin.value = ""
                prefs.putString(KEY_PIN, "")
            }
        }
    }

    fun disconnect() {
        scope.launch {
            connection.disconnect()
        }
    }
}
