package com.remotepad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.remotepad.network.ConnectionState
import com.remotepad.viewmodel.ConnectionViewModel
import com.remotepad.viewmodel.InputValidator

/**
 * Connection form screen: IP, port, PIN inputs and a Connect button.
 */
@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel) {
    val ip by viewModel.ip.collectAsState()
    val port by viewModel.port.collectAsState()
    val pin by viewModel.pin.collectAsState()
    val connState by viewModel.connectionState.collectAsState()
    val error by viewModel.errorMessage.collectAsState()

    val isConnecting = connState == ConnectionState.CONNECTING || connState == ConnectionState.CONNECTED
    val formValid = InputValidator.isValidHost(ip) &&
            InputValidator.isValidPort(port) &&
            InputValidator.isValidPin(pin)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("RemotePad", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = ip,
            onValueChange = { viewModel.ip.value = it },
            label = { Text("Server") },
            placeholder = { Text("192.168.1.10 or hostname") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = port,
            onValueChange = { viewModel.port.value = it },
            label = { Text("Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { viewModel.pin.value = it },
            label = { Text("PIN (4 digits)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.connect() },
            enabled = formValid && !isConnecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isConnecting) "Connecting..." else "Connect")
        }

        Spacer(Modifier.height(16.dp))

        if (error != null) {
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
