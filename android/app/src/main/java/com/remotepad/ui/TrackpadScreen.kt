package com.remotepad.ui

import android.graphics.BitmapFactory
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotepad.input.KeyMap
import com.remotepad.input.KeyboardInputHandler
import com.remotepad.input.Pointer
import com.remotepad.input.ShortcutConfig
import com.remotepad.input.TouchAction
import com.remotepad.input.TouchEventProcessor
import com.remotepad.input.TouchInput
import com.remotepad.input.MotionProcessor
import com.remotepad.input.SettingsRepository
import com.remotepad.model.RemoteEvent
import com.remotepad.network.ConnectionState
import com.remotepad.viewmodel.TrackpadViewModel

/**
 * Main trackpad screen with touch zone, scroll zone, mouse buttons, and shortcuts.
 *
 * Layout (landscape):
 * ┌──────────────────────────┬──────┐
 * │                          │      │
 * │     Trackpad Zone        │Scroll│
 * │                          │      │
 * ├──────────────────────────┤      │
 * │ [Left] [Middle] [Right]  │      │
 * ├──────────────────────────┴──────┤
 * │ [Ctrl+C][Ctrl+V][Ctrl+Z]...[⌨] │
 * └─────────────────────────────────┘
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TrackpadScreen(
    viewModel: TrackpadViewModel,
    settingsRepository: SettingsRepository,
    onDisconnect: () -> Unit
) {
    val connState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val softKeyboard = LocalSoftwareKeyboardController.current

    val motionProcessor = remember { MotionProcessor(settingsRepository) }

    // Keyboard input capture
    val keyboardHandler = remember {
        KeyboardInputHandler { event -> viewModel.sendEvent(event) }
    }
    var keyboardText by remember { mutableStateOf(TextFieldValue("")) }
    var lastSentLength by remember { mutableStateOf(0) }
    val keyboardFocusRequester = remember { FocusRequester() }
    var fullscreen by remember { mutableStateOf(false) }
    val keyboardRowsVisible = !fullscreen

    val activity = context as? android.app.Activity
    LaunchedEffect(fullscreen) {
        val win = activity?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowInsetsControllerCompat(win, win.decorView)
        if (fullscreen) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, true)
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    // Touch processor emits events via the ViewModel
    val touchProcessor = remember {
        TouchEventProcessor(
            emitter = { event ->
                when (event) {
                    is RemoteEvent.MouseMove -> {
                        val (dx, dy) = motionProcessor.processMouseMove(
                            event.dx.toFloat(), event.dy.toFloat()
                        )
                        viewModel.sendEvent(RemoteEvent.MouseMove(dx, dy))
                    }
                    is RemoteEvent.MouseScroll -> {
                        val (sdx, sdy) = motionProcessor.processScroll(
                            event.dx.toFloat(), event.dy.toFloat()
                        )
                        viewModel.sendEvent(RemoteEvent.MouseScroll(sdx, sdy))
                    }
                    is RemoteEvent.MouseClick -> {
                        viewModel.sendEvent(event)
                        vibrate(context, settingsRepository)
                    }
                    else -> viewModel.sendEvent(event)
                }
            }
        )
    }

    // Scroll zone processor
    val scrollProcessor = remember {
        TouchEventProcessor(
            emitter = { event ->
                when (event) {
                    is RemoteEvent.MouseMove -> {
                        // In scroll zone, convert move to scroll
                        val (sdx, sdy) = motionProcessor.processScroll(
                            event.dx.toFloat(), event.dy.toFloat()
                        )
                        viewModel.sendEvent(RemoteEvent.MouseScroll(sdx, sdy))
                    }
                    else -> {}
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Hidden text field for keyboard input capture
        BasicTextField(
            value = keyboardText,
            onValueChange = { newValue ->
                keyboardText = newValue
                if (newValue.composition == null) {
                    val newLen = newValue.text.length
                    when {
                        newLen > lastSentLength -> {
                            val added = newValue.text.substring(lastSentLength)
                            val parts = added.split('\n')
                            parts.forEachIndexed { index, part ->
                                if (part.isNotEmpty()) {
                                    keyboardHandler.onTextCommitted(part)
                                }
                                if (index < parts.size - 1) {
                                    keyboardHandler.onSpecialKey(KeyMap.KEYCODE_ENTER)
                                }
                            }
                        }
                        newLen < lastSentLength -> {
                            repeat(lastSentLength - newLen) {
                                keyboardHandler.onSpecialKey(KeyMap.KEYCODE_DEL)
                            }
                        }
                    }
                    lastSentLength = newLen
                    if (newLen > 500) {
                        keyboardText = TextFieldValue("")
                        lastSentLength = 0
                    }
                }
            },
            modifier = Modifier
                .width(1.dp)
                .height(1.dp)
                .alpha(0f)
                .focusRequester(keyboardFocusRequester)
        )

        // Top row: trackpad + scroll zone
        Row(modifier = Modifier.weight(1f)) {
            // Trackpad zone
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF2D2D2D))
                    .pointerInteropFilter { motionEvent ->
                        handleMotionEvent(motionEvent, touchProcessor)
                        true
                    }
            ) {
                val cursorFrame by viewModel.cursorRegionFrame.collectAsState()
                val bitmap = remember(cursorFrame) {
                    cursorFrame?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = if (connState == ConnectionState.AUTHENTICATED) "⬆ Trackpad" else "Disconnected",
                        color = Color(0xFF666666),
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Scroll zone
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF3A3A3A))
                    .pointerInteropFilter { motionEvent ->
                        handleMotionEvent(motionEvent, scrollProcessor)
                        true
                    }
            ) {
                Text(
                    text = "⇕",
                    color = Color(0xFF888888),
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Mouse buttons row + compact toolbar (keyboard toggle, disconnect, kb rows toggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MouseButton("Left", Modifier.weight(1f)) {
                viewModel.onButtonClick("left")
                vibrate(context, settingsRepository)
            }
            MouseButton("Mid", Modifier.weight(1f)) {
                viewModel.onButtonClick("middle")
                vibrate(context, settingsRepository)
            }
            MouseButton("Right", Modifier.weight(1f)) {
                viewModel.onButtonClick("right")
                vibrate(context, settingsRepository)
            }
            // Fullscreen toggle (hides system bar + keyboard rows)
            Button(
                onClick = { fullscreen = !fullscreen },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (fullscreen) Color(0xFF4A7A9A) else Color(0xFF5A5A5A),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(if (fullscreen) "⤢" else "⛶", fontSize = 18.sp, color = Color.White)
            }
            // IME keyboard toggle
            Button(
                onClick = {
                    keyboardFocusRequester.requestFocus()
                    softKeyboard?.show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5A5A5A),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("⌨", fontSize = 18.sp, color = Color.White)
            }
            // Disconnect
            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFA83838),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("✕", fontSize = 16.sp, color = Color.White)
            }
        }

        if (keyboardRowsVisible) {

        // Navigation bar (arrows, home/end, pgup/pgdn, tab, enter, esc)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFF151515))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(ShortcutConfig.navigationShortcuts) { shortcut ->
                    Button(
                        onClick = {
                            viewModel.sendEvent(shortcut.event)
                            vibrate(context, settingsRepository)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4A7A9A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(shortcut.label, fontSize = 14.sp, maxLines = 1, color = Color.White)
                    }
                }
            }
        }

        // Shortcut bar (text shortcuts + keyboard toggle + disconnect)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFF111111))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(ShortcutConfig.defaultShortcuts) { shortcut ->
                    Button(
                        onClick = {
                            viewModel.sendEvent(shortcut.event)
                            vibrate(context, settingsRepository)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5A5A5A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(shortcut.label, fontSize = 12.sp, maxLines = 1, color = Color.White)
                    }
                }
            }

        }

        } // end if (keyboardRowsVisible)
    }
}

@Composable
private fun MouseButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6A6A6A),
            contentColor = Color.White
        ),
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
    ) {
        Text(label, fontSize = 15.sp, color = Color.White)
    }
}

/**
 * Convert Android MotionEvent to our TouchInput and feed to processor.
 */
private fun handleMotionEvent(event: MotionEvent, processor: TouchEventProcessor) {
    val action = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> TouchAction.DOWN
        MotionEvent.ACTION_POINTER_DOWN -> TouchAction.POINTER_DOWN
        MotionEvent.ACTION_MOVE -> TouchAction.MOVE
        MotionEvent.ACTION_POINTER_UP -> TouchAction.POINTER_UP
        MotionEvent.ACTION_UP -> TouchAction.UP
        MotionEvent.ACTION_CANCEL -> TouchAction.UP
        else -> return
    }

    val pointers = (0 until event.pointerCount).map { i ->
        Pointer(event.getPointerId(i), event.getX(i), event.getY(i))
    }

    processor.onTouchInput(TouchInput(action, pointers, event.eventTime))
}

/**
 * Trigger haptic feedback if enabled.
 */
private fun vibrate(context: Context, settings: SettingsRepository) {
    if (!settings.hapticEnabled) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        mgr.defaultVibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
