package rw.itunda.core.identity

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Ported from mobile_clients/android (2026-07-10) into its correct bounded-context
 * module — see docs/ARCHITECTURE.md §3. Local Android Keystore biometric check, intended
 * to gate access before a National ID (NIDA) server-side verification call, and reused
 * as a generic transaction-confirm gate (see [authenticateForTransaction]).
 *
 * The NIDA server-side call and the CryptoObject-bound Keystore signing are not
 * implemented — this is local biometric gating only, honestly labeled as such
 * rather than claimed as a finished identity-verification flow (see
 * docs/TOSS_PARITY_MATRIX.md's KYC/AML gate, which is still open).
 *
 * Fixed (2026-07-11): the original version built a [BiometricPrompt.PromptInfo] and
 * then never called `.authenticate()` on it, unconditionally reporting success without
 * ever actually showing the system prompt — the same fake-success pattern this repo's
 * own SECURITY.md/blog posts document finding and fixing elsewhere. `requireActivity`
 * is a real [FragmentActivity] (required by [BiometricPrompt]'s constructor), not just
 * a [android.content.Context] — that's why [rw.itunda.app.MainActivity] now extends
 * `FragmentActivity` instead of `ComponentActivity`.
 */
class NIDABiometricAuth(private val requireActivity: FragmentActivity) {

    fun authenticateUser(nidNumber: String, onAuthResult: (Boolean, String?) -> Unit) {
        showPrompt(
            subtitle = "Verify identity for NID: $nidNumber",
            unavailableMessage = "Biometrics unavailable. Falling back to MTN/Airtel USSD OTP.",
            onAuthResult = onAuthResult,
        )
    }

    /** Generic transaction-confirm gate — e.g. before a transfer/payment is sent. */
    fun authenticateForTransaction(reason: String, onAuthResult: (Boolean, String?) -> Unit) {
        showPrompt(
            subtitle = reason,
            unavailableMessage = "Biometrics unavailable on this device.",
            onAuthResult = onAuthResult,
        )
    }

    private fun showPrompt(subtitle: String, unavailableMessage: String, onAuthResult: (Boolean, String?) -> Unit) {
        val biometricManager = BiometricManager.from(requireActivity)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            onAuthResult(false, unavailableMessage)
            return
        }

        val executor = ContextCompat.getMainExecutor(requireActivity)
        val prompt = BiometricPrompt(
            requireActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthResult(true, null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onAuthResult(false, errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // A single failed attempt (e.g. unrecognized fingerprint) -- the
                    // system prompt stays open for a retry, so this is not terminal.
                }
            },
        )

        // Not implemented: binding this prompt to a CryptoObject and the server-side
        // NIDA verification call for authenticateUser's use case. Local biometric
        // success only, honestly labeled above.
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Itunda Secure Confirmation")
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(promptInfo)
    }
}
