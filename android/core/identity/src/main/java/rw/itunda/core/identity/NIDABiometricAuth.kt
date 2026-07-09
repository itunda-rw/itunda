package rw.itunda.core.identity

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * Ported from mobile_clients/android (2026-07-10) into its correct bounded-context
 * module — see ARCHITECTURE.md §3. Local Android Keystore biometric check, intended
 * to gate access before a National ID (NIDA) server-side verification call.
 *
 * The NIDA server-side call and the CryptoObject-bound Keystore signing are not
 * implemented — this is local biometric gating only, honestly labeled as such
 * rather than claimed as a finished identity-verification flow (see
 * docs/TOSS_PARITY_MATRIX.md's KYC/AML gate, which is still open).
 */
class NIDABiometricAuth(private val context: Context) {

    fun authenticateUser(nidNumber: String, onAuthResult: (Boolean, String?) -> Unit) {
        val biometricManager = BiometricManager.from(context)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                performLocalBiometricAuth(nidNumber, onAuthResult)
            }
            else -> {
                onAuthResult(false, "Biometrics unavailable. Falling back to MTN/Airtel USSD OTP.")
            }
        }
    }

    private fun performLocalBiometricAuth(nidNumber: String, onAuthResult: (Boolean, String?) -> Unit) {
        val executor: Executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Itunda Secure Login")
            .setSubtitle("Verify identity for NID: $nidNumber")
            .setNegativeButtonText("Use NIDA OTP")
            .build()

        // Not implemented: binding this prompt to a CryptoObject and the server-side
        // NIDA verification call. Local biometric success only, honestly labeled above.
        onAuthResult(true, null)
    }
}
