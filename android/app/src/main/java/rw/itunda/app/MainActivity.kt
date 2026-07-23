package rw.itunda.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SessionManager
import rw.itunda.core.network.SessionState
import rw.itunda.app.ui.AppLockScreen
import rw.itunda.app.ui.ItundaAppScreen
import rw.itunda.app.ui.LoginScreen
import rw.itunda.core.designsystem.theme.IdsTheme
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
    private var mapDeepLinkRequested by mutableStateOf(false)
    private var mapSearchFromDeepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyMapsDeepLink(intent)

        // Root/FDS gate on the real app entry point, ported from
        // mobile_clients/android's BankActivity (see docs/ARCHITECTURE.md §3) --
        // money-moving screens should not render on a compromised device.
        if (!RootDetection.verifyDeviceIntegrity()) {
            setContent {
                IdsTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Text("Itunda can't run on a rooted or compromised device.")
                    }
                }
            }
            return
        }

        // Real login gate (2026-07-11) -- ItundaAppScreen previously rendered
        // unconditionally with no session at all; see SessionManager.kt.
        //
        // Real single-root background (2026-07-24) -- fixes a real bug found via
        // screenshot audit: several destinations (AgentCashScreen, MenuScreen,
        // InvestScreen, OverviewScreen, LoansScreen, SupportScreen,
        // CreditScoreScreen, CertificateScreen, IdentityScreen,
        // WeeklySavingsScreen) never painted their own background, so they fell
        // through to whatever the platform theme's windowBackground happened to
        // be (previously hardcoded light, see AndroidManifest.xml) instead of
        // Ids.colors.background -- a real inconsistent-background bug, not a
        // deliberate per-screen choice. This one opaque Surface, painted once at
        // the true root before any destination renders, means a screen that
        // still forgets to paint its own background now shows the correct
        // themed color underneath instead of a stale/wrong one.
        setContent {
            IdsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                                ItundaAppScreen(
                                    openMapFromDeepLink = mapDeepLinkRequested,
                                    initialMapSearchQuery = mapSearchFromDeepLink,
                                    onMapDeepLinkConsumed = { mapDeepLinkRequested = false },
                                )
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyMapsDeepLink(intent)
    }

    private fun applyMapsDeepLink(intent: Intent?) {
        val uri = intent?.data
        mapDeepLinkRequested = intent?.action == Intent.ACTION_VIEW &&
            uri?.scheme.equals("itunda", ignoreCase = true) &&
            uri?.host.equals("maps", ignoreCase = true)
        mapSearchFromDeepLink = if (mapDeepLinkRequested && uri?.path.equals("/search", ignoreCase = true)) {
            uri?.getQueryParameter("query")?.trim()?.takeIf { it.isNotEmpty() }?.take(160)
        } else {
            null
        }
    }
}
