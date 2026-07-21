package rw.itunda.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.app.network.NetworkClient
import rw.itunda.app.network.VerifyDeviceRequest
import java.io.IOException

/**
 * Real device binding step-up, factored out 2026-07-21 after the third copy of this
 * exact pattern (Gift send/claim, Commerce checkout, Eats checkout, Stocks buy/sell --
 * see SuperAppTabs.kt/InvestScreen.kt) would otherwise have hand-duplicated the same
 * busy/error state + verify-then-retry logic. Lives in :app (not
 * :features:payments:impl, which owns the visual `DeviceStepUpDialog` this wraps) --
 * it needs `NetworkClient`, which feature modules can't depend on (same constraint
 * iOS's TransferFlowContainer/SavingsFlowContainer doc comments already name for why
 * they live in the App target instead of Features/Payments).
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
    rw.itunda.feature.payments.impl.DeviceStepUpDialog(
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
                    error = if (e.code() == 400) "Incorrect password." else "Something went wrong. Please try again."
                } catch (_: IOException) {
                    busy = false
                    error = "Couldn't reach itunda. Check your connection and try again."
                }
            }
        },
    )
}
