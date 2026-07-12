package rw.itunda.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rw.itunda.app.network.SessionManager
import rw.itunda.app.network.SessionState
import rw.itunda.app.ui.ItundaAppScreen
import rw.itunda.app.ui.LoginScreen
import rw.itunda.core.risk.RootDetection

// FragmentActivity, not just ComponentActivity, so NIDABiometricAuth's real
// BiometricPrompt (androidx.biometric:1.1.0) can be constructed from screens
// below it -- see core/identity/NIDABiometricAuth.kt. FragmentActivity itself
// still supports Compose's setContent{} directly, no separate host needed.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root/FDS gate on the real app entry point, ported from
        // mobile_clients/android's BankActivity (see docs/ARCHITECTURE.md §3) --
        // money-moving screens should not render on a compromised device.
        if (!RootDetection.verifyDeviceIntegrity()) {
            setContent {
                Text("Itunda can't run on a rooted or compromised device.")
            }
            return
        }

        // Real login gate (2026-07-11) -- ItundaAppScreen previously rendered
        // unconditionally with no session at all; see SessionManager.kt.
        setContent {
            val sessionState by SessionManager.sessionState.collectAsStateWithLifecycle()
            when (sessionState) {
                is SessionState.LoggedIn -> ItundaAppScreen()
                is SessionState.LoggedOut -> LoginScreen(onLoggedIn = {})
            }
        }
    }
}
