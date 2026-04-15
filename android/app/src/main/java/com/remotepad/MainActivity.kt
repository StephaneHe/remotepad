package com.remotepad

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.remotepad.input.SettingsRepository
import com.remotepad.network.ConnectionState
import com.remotepad.network.WebSocketClient
import com.remotepad.ui.ConnectionScreen
import com.remotepad.ui.TrackpadScreen
import com.remotepad.viewmodel.ConnectionViewModel
import com.remotepad.viewmodel.PreferencesStore
import com.remotepad.viewmodel.TrackpadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var connection: WebSocketClient
    private lateinit var connViewModel: ConnectionViewModel
    private lateinit var trackpadViewModel: TrackpadViewModel
    private lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        connection = WebSocketClient()

        val prefs = object : PreferencesStore {
            private val sp = getSharedPreferences("remotepad", MODE_PRIVATE)
            override fun getString(key: String, default: String): String {
                val value = sp.getString(key, default) ?: default
                Log.d("RemotePad", "PrefsStore.get($key) = '$value'")
                return value
            }
            override fun putString(key: String, value: String) {
                Log.d("RemotePad", "PrefsStore.put($key, '$value')")
                sp.edit().putString(key, value).commit()
            }
        }

        connViewModel = ConnectionViewModel(connection, prefs, scope)
        trackpadViewModel = TrackpadViewModel(connection, scope)
        settingsRepo = SettingsRepository(prefs)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    val connState by connection.connectionState.collectAsState()

                    LaunchedEffect(connState) {
                        val controller = WindowInsetsControllerCompat(window, window.decorView)
                        if (connState == ConnectionState.AUTHENTICATED) {
                            WindowCompat.setDecorFitsSystemWindows(window, false)
                            controller.hide(WindowInsetsCompat.Type.systemBars())
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            WindowCompat.setDecorFitsSystemWindows(window, true)
                            controller.show(WindowInsetsCompat.Type.systemBars())
                        }
                    }

                    if (connState == ConnectionState.AUTHENTICATED) {
                        // Switch to landscape for trackpad
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                        TrackpadScreen(
                            viewModel = trackpadViewModel,
                            settingsRepository = settingsRepo,
                            onDisconnect = {
                                connViewModel.disconnect()
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        )
                    } else {
                        // Portrait for connection screen
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

                        ConnectionScreen(viewModel = connViewModel)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.close()
        scope.cancel()
    }
}
