package rw.itunda.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rw.itunda.app.network.NetworkClient
import rw.itunda.app.network.SessionManager
import rw.itunda.app.network.SessionState
import rw.itunda.app.ui.AppLockScreen
import rw.itunda.app.ui.ItundaAppScreen
import rw.itunda.app.ui.LoginScreen
import rw.itunda.core.identity.NIDABiometricAuth
import rw.itunda.core.risk.RootDetection

// Real app-launch biometric unlock gate (2026-07-21) -- tracked per PROCESS, not per
// Activity instance or persisted to disk, so a config change/Activity recreation
// doesn't re-prompt but a genuine fresh app launch always does. See
// docs/DESIGN_REFERENCES.md Section 8 and AppLockScreen.kt's own doc comment.
private object AppUnlockState {
    var unlockedThisProcess = false
}

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
                is SessionState.LoggedIn -> {
                    val tokenStore = remember { NetworkClient.currentTokenStore() }
                    val biometricAvailable = remember { NIDABiometricAuth(this).isAvailable() }
                    var unlocked by remember { mutableStateOf(AppUnlockState.unlockedThisProcess) }
                    if (biometricAvailable && tokenStore.isAppLockEnabled() && !unlocked) {
                        AppLockScreen(activity = this) {
                            AppUnlockState.unlockedThisProcess = true
                            unlocked = true
                        }
                    } else {
                        ItundaAppScreen()
                    }
                }
                is SessionState.LoggedOut -> {
                    AppUnlockState.unlockedThisProcess = false
                    LoginScreen(onLoggedIn = {})
                }
            }
        }
    }
}
