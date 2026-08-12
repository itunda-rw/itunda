package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
        Text("Itunda Merchant", style = IdsTypography.Title1, color = Ids.colors.textPrimary)
        Text("Log in with your existing itunda account to run your shop.", style = IdsTypography.Body1, color = Ids.colors.textSecondary)

        Spacer(modifier = Modifier.height(24.dp))

        IdsTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = "Phone number",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        IdsTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = Ids.colors.danger, style = IdsTypography.Body2)
        }

        Spacer(modifier = Modifier.height(20.dp))
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
                        // Real Toss-style error handling (2026-08-12) -- 401 stays a
                        // real, friendly, specific message (not itunda's own to
                        // second-guess), but every OTHER real HTTP error (429 rate
                        // limit, 403 account suspended, 5xx, etc.) was discarding any
                        // real backend message for a vague "Couldn't reach itunda" --
                        // wrong on two counts: it wasn't a reachability problem if a
                        // real response came back, and it silently dropped whatever
                        // specific reason the backend actually gave.
                        error = if (e.code() == 401) {
                            "Incorrect phone number or password."
                        } else {
                            rw.itunda.merchant.network.apiErrorMessage(e)
                                ?: "itunda is having a brief hiccup on our end -- not something you did. Try again in a moment."
                        }
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } finally {
                        busy = false
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Don't have an itunda account yet? Register in the main itunda app first, then come back here to run your shop.",
            style = IdsTypography.Typography7,
            color = Ids.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
