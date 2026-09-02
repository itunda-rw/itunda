package rw.itunda.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.identity.DeviceKeyManager
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.VerifyDeviceRequest
import rw.itunda.core.network.VerifyDeviceSignatureRequest
import rw.itunda.core.network.apiErrorMessage
import rw.itunda.core.designsystem.components.DeviceStepUpDialog
import java.io.IOException
import java.util.Base64

/**
 * Real device binding step-up, factored out 2026-07-21 after the third copy of this
 * exact pattern (Gift send/claim, Commerce checkout, Eats checkout, Stocks buy/sell --
 * see SuperAppTabs.kt/InvestScreen.kt) would otherwise have hand-duplicated the same
 * busy/error state + verify-then-retry logic. Lives in :app, not a Feature module --
 * NOT because of `NetworkClient` (a stale reason this comment used to give; Feature
 * modules genuinely CAN depend on `:core:network`, confirmed 2026-09-02 when
 * :features:banking:impl/:features:home:impl were built referencing it directly).
 * The real reason: this reads `LocalRealActivity`, an `:app`-only CompositionLocal
 * (see its own doc comment) for the biometric-first path's activity handle. The
 * visual `DeviceStepUpDialog` this wraps now lives in `:core:designsystem` (promoted
 * 2026-09-02 out of :features:payments:impl so any Feature module's own copy of THIS
 * host -- once `LocalRealActivity` is itself shared -- can render it without a
 * forbidden cross-Feature impl-to-impl import).
 *
 * Usage: hold a `pendingDeviceRetry: (suspend () -> Unit)?` and a
 * `needsDeviceVerification: Boolean` in the caller: on a real 403
 * `isDeviceNotVerifiedError`, set the retry lambda and flip the flag, then render
 * this composable -- it verifies the current device and invokes the retry itself.
 */
@Composable
fun DeviceStepUpHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    onVerified: suspend () -> Unit,
) {
    if (!visible) return
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val activity = LocalRealActivity.current
    val deviceKeyManager = remember { DeviceKeyManager() }

    // Real biometric-first step-up (item 246): if this device already registered a
    // Keystore key (Settings > Security > "Verify this device with biometrics"), try
    // that path automatically as soon as this dialog opens -- a challenge + hardware-
    // bound signature, no password retyping. Any failure (declined, unavailable, no
    // key) falls through silently to the password field already rendered below, never
    // blocking the user on a biometric-only path.
    LaunchedEffect(visible) {
        if (!deviceKeyManager.hasKey()) return@LaunchedEffect
        busy = true
        try {
            val challenge = NetworkClient.authApi.issueDeviceChallenge().challenge
            deviceKeyManager.signChallenge(
                activity = activity,
                challenge = Base64.getDecoder().decode(challenge),
                reason = "Verify this device to continue",
            ) { signatureBase64, _ ->
                if (signatureBase64 == null) {
                    busy = false
                    return@signChallenge
                }
                scope.launch {
                    try {
                        NetworkClient.authApi.verifyDeviceSignature(VerifyDeviceSignatureRequest(signatureBase64))
                        busy = false
                        onVerified()
                    } catch (_: Exception) {
                        busy = false
                    }
                }
            }
        } catch (_: Exception) {
            busy = false
        }
    }

    DeviceStepUpDialog(
        busy = busy,
        error = error,
        onCancel = onDismiss,
        onVerify = { password ->
            error = null
            busy = true
            scope.launch {
                try {
                    NetworkClient.authApi.verifyDevice(VerifyDeviceRequest(password))
                    busy = false
                    onVerified()
                } catch (e: HttpException) {
                    busy = false
                    // Real backend message pass-through first (2026-08-10), same fix as
                    // superAppErrorMessage/SessionManager.httpErrorMessage: this was
                    // discarding any specific backend decline in favor of a hardcoded
                    // bucket.
                    error = apiErrorMessage(e) ?: when (e.code()) {
                        400 -> "Incorrect password."
                        // Real gap found live (2026-08-10), same fix as
                        // superAppErrorMessage's own: a 503/504 here is itunda's
                        // infrastructure having a bad moment, not a wrong password --
                        // say that plainly rather than leaving the user to assume
                        // they mistyped it and keep retrying the same password.
                        503, 504 -> "itunda is having a brief hiccup on our end -- not your password. Try again in a moment."
                        else -> "Something went wrong. Please try again."
                    }
                } catch (_: IOException) {
                    busy = false
                    error = "Couldn't reach itunda. Check your connection and try again."
                }
            }
        },
    )
}
