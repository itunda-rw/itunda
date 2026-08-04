package rw.itunda.merchant.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.merchant.network.NetworkClient
import rw.itunda.merchant.network.VerifyDeviceRequest
import java.io.IOException

/**
 * Real device binding step-up (2026-07-28 port) -- shown when a money-moving action
 * (chargeCard, moveToBusiness/moveToPersonal) real-403s with DEVICE_NOT_VERIFIED. Same
 * real re-verification the main app's DeviceStepUpHost/DeviceStepUpDialog already
 * establish (password re-entry -> POST /devices/verify -> caller retries), a single
 * self-contained composable here since merchantapp has no multi-module Features split
 * to separate host logic from dialog visuals.
 *
 * Usage: hold a `needsDeviceVerification: Boolean` in the caller; on a real 403 caught
 * via `isDeviceNotVerifiedError`, set it true and render this composable; on success
 * it clears the flag and the caller re-runs the original action.
 */
@Composable
fun DeviceStepUpDialog(onVerified: () -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("🔒 Verify this device") },
        text = {
            Column {
                Text("This is a new device for your account. Re-enter your password to allow it to move money, then try again.")
                Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                IdsTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (error != null) {
                    Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotEmpty(),
                onClick = {
                    error = null
                    busy = true
                    scope.launch {
                        try {
                            NetworkClient.authApi.verifyDevice(VerifyDeviceRequest(password))
                            busy = false
                            onVerified()
                        } catch (e: HttpException) {
                            busy = false
                            error = if (e.code() == 400) "Incorrect password." else "Something went wrong. Please try again."
                        } catch (_: IOException) {
                            busy = false
                            error = "Couldn't reach itunda. Check your connection and try again."
                        }
                    }
                },
            ) { Text(if (busy) "Verifying…" else "Verify device") }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") }
        },
    )
}
