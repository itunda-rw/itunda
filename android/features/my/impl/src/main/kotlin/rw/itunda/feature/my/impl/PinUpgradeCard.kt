package rw.itunda.feature.my.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.network.AuthResult
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SessionManager
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.components.AccountPinPad

/**
 * Real Toss-sourced passwordless-login rollout (2026-08-23) -- see AccountPinPad.kt's
 * own doc comment and backend User.pinSet's own doc comment for the full account.
 * `pinSet == false` only for a pre-PIN-era account whose existing password hasn't been
 * upgraded to a real 6-digit PIN yet -- a real, non-blocking offer, never forced: the
 * existing password keeps working exactly as before either way
 * (AuthService.login is shape-agnostic). Own file (not inline in MyTab, which is
 * already one of ItundaAppScreen.kt's real oversized sections) matching this session's
 * own established file-size-lint discipline for new, self-contained pieces. Flat, no
 * IdsCard -- matches this codebase's own standing "new/touched screens are flat"
 * design law (docs/UI_UX_GUIDELINES.md).
 */
@Composable
fun PinUpgradeCard() {
    var pinSet by remember { mutableStateOf<Boolean?>(null) }
    var step by remember { mutableStateOf(PinUpgradeStep.CLOSED) }
    var currentCredential by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            pinSet = NetworkClient.authApi.getProfile().user.pinSet
        } catch (_: Exception) {
            // Best-effort, matching this screen's own real precedent (VerificationCard).
        }
    }

    if (pinSet != false) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (step == PinUpgradeStep.SUCCESS) {
            Text(
                "Your 6-digit PIN is set -- use it to sign in next time.",
                color = Ids.colors.success,
                fontSize = 13.sp,
            )
            return@Column
        }

        Text("Set your 6-digit PIN", color = Ids.colors.textPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 15.sp)
        Text(
            "A quick 6-digit PIN replaces typing your password to sign in -- the same real simplification Toss uses.",
            color = Ids.colors.textSecondary,
            fontSize = 12.sp,
        )

        when (step) {
            PinUpgradeStep.CLOSED -> {
                Button(onClick = { step = PinUpgradeStep.CREDENTIAL }) { Text("Set up my PIN") }
            }
            PinUpgradeStep.CREDENTIAL -> {
                OutlinedTextField(
                    value = currentCredential,
                    onValueChange = { currentCredential = it },
                    label = { Text("Your current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                Button(
                    enabled = currentCredential.isNotBlank(),
                    onClick = { error = null; step = PinUpgradeStep.CREATE },
                ) { Text("Continue") }
            }
            PinUpgradeStep.CREATE -> AccountPinPad(
                headline = "Create a 6-digit PIN",
                errorMessage = error,
                onComplete = { entered -> error = null; newPin = entered; step = PinUpgradeStep.CONFIRM },
            )
            PinUpgradeStep.CONFIRM -> AccountPinPad(
                headline = "Confirm your PIN",
                errorMessage = error,
                busy = busy,
                onComplete = { entered ->
                    if (entered != newPin) {
                        error = "That didn't match. Try again."
                        step = PinUpgradeStep.CREATE
                        return@AccountPinPad
                    }
                    busy = true
                    error = null
                    scope.launch {
                        when (val result = SessionManager.updateAccountPin(currentCredential, entered)) {
                            is AuthResult.Success -> {
                                busy = false
                                step = PinUpgradeStep.SUCCESS
                            }
                            is AuthResult.Failure -> {
                                busy = false
                                error = result.message
                                step = PinUpgradeStep.CREDENTIAL
                            }
                        }
                    }
                },
            )
            PinUpgradeStep.SUCCESS -> Unit
        }
    }
}

private enum class PinUpgradeStep { CLOSED, CREDENTIAL, CREATE, CONFIRM, SUCCESS }
