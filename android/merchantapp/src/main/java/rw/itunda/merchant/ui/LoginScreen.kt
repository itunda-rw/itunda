package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.merchant.network.DevicePlatform
import rw.itunda.merchant.network.LoginRequest
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.RegisterDeviceTokenRequest
import java.io.IOException

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Itunda Merchant", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Log in with your existing itunda account to run your shop.", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone number") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (phoneNumber.isBlank() || password.isBlank()) {
                    error = "Enter your phone number and password."
                    return@Button
                }
                busy = true
                error = null
                scope.launch {
                    try {
                        val deviceStore = NetworkClient.currentDeviceStore()
                        val res = NetworkClient.authApi.login(
                            LoginRequest(phoneNumber.trim(), password, deviceStore.getOrCreateDeviceId(), deviceStore.getDeviceName()),
                        )
                        NetworkClient.currentTokenStore().saveSession(res.user.id, res.accessToken, res.refreshToken)
                        // Real push device-token registration (item 130) -- best-effort,
                        // fire-and-forget: a registration failure must never block an
                        // otherwise successful login. See ApiService.kt's own doc comment.
                        scope.launch {
                            try {
                                NetworkClient.apiService.registerDeviceToken(
                                    RegisterDeviceTokenRequest(DevicePlatform.ANDROID, deviceStore.getOrCreateDeviceId()),
                                )
                            } catch (e: Exception) {
                                // Best-effort, see doc comment above.
                            }
                        }
                        onLoggedIn()
                    } catch (e: HttpException) {
                        error = if (e.code() == 401) "Incorrect phone number or password." else "Couldn't reach itunda. Try again."
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (busy) "Logging in…" else "Log in")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Don't have an itunda account yet? Register in the main itunda app first, then come back here to run your shop.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
