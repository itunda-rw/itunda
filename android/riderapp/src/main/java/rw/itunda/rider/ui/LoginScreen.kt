package rw.itunda.rider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.rider.network.DevicePlatform
import rw.itunda.rider.network.LoginRequest
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.network.RegisterDeviceTokenRequest
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
        Text("Itunda Rider", style = IdsTypography.Title1, color = Ids.colors.textPrimary)
        Text("Log in with your existing itunda account to start delivering.", style = IdsTypography.Body1, color = Ids.colors.textSecondary)

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))

        IdsTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = "Phone number",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
        IdsTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = Ids.colors.danger, style = IdsTypography.Body2)
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))
        IdsButton(
            text = if (busy) "Logging in…" else "Log in",
            enabled = !busy,
            onClick = {
                if (phoneNumber.isBlank() || password.isBlank()) {
                    error = "Enter your phone number and password."
                    return@IdsButton
                }
                busy = true
                error = null
                scope.launch {
                    try {
                        val res = NetworkClient.authApi.login(LoginRequest(phoneNumber.trim(), password))
                        NetworkClient.currentTokenStore().saveSession(res.user.id, res.accessToken, res.refreshToken)
                        // Real push device-token registration (item 130) -- best-effort,
                        // fire-and-forget: a registration failure must never block an
                        // otherwise successful login. See ApiService.kt's own doc comment.
                        scope.launch {
                            try {
                                NetworkClient.apiService.registerDeviceToken(
                                    RegisterDeviceTokenRequest(DevicePlatform.ANDROID, NetworkClient.currentDeviceStore().getOrCreateDeviceId()),
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
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Don't have an itunda account yet? Register in the main itunda app first, then come back here to log in as a rider.",
            style = IdsTypography.Typography7,
            color = Ids.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
