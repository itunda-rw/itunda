package rw.itunda.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rw.itunda.core.network.AppLocalePreference
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SessionManager
import rw.itunda.core.network.SessionState
import rw.itunda.core.network.ThemeMode
import rw.itunda.core.network.ThemePreference
import java.util.Locale
import rw.itunda.app.ui.AppLockScreen
import rw.itunda.app.ui.ItundaAppScreen
import rw.itunda.app.ui.LocalRealActivity
import rw.itunda.app.ui.LoginScreen
import rw.itunda.app.ui.PinEntryScreen
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
    private var mapSharedFolderFromDeepLink by mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Real screenshot/screen-recording protection (2026-08-09) -- found missing during a
        // Toss-parity security audit: zero FLAG_SECURE usage existed anywhere in this codebase,
        // meaning every screen (wallet balance, transfer amounts, card details, PIN/password
        // entry) was screenshottable and screen-recordable by any other app or the OS itself,
        // and would appear as plaintext in the recent-apps task switcher thumbnail. Every real
        // fintech app (Toss included) blocks this app-wide, not per-screen -- itunda shows a
        // real balance on nearly every tab (see HomeTab's WalletHeroCard), so a per-screen
        // allowlist would be both more fragile and less protective than a blanket flag set once
        // here, before setContent, so it also covers the task-switcher thumbnail.
        // Skipped in debug builds only (2026-08-11): a live UI-consistency audit against real
        // Toss reference screenshots needed real on-device screenshots to compare, which
        // FLAG_SECURE blocks entirely (screencap and even uiautomator's pixel path return solid
        // black). Release builds are unaffected -- BuildConfig.DEBUG is compiled false there, so
        // this branch never runs and the real protection stays exactly as it was.
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

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
            // Real app-root locale wiring (2026-08-08 fix) -- see AppLocalePreference's
            // own doc comment: this used to be missing entirely, so the in-app language
            // switcher only ever affected LoginScreen's own subtree, never anything a
            // user sees after logging in. Wrapping the whole tree here, above both
            // branches of the session-state `when`, is what actually makes every
            // screen's stringResource() calls -- in :app and every feature module --
            // respect the stored choice instead of the device's raw OS locale.
            val baseContext = LocalContext.current
            val locale by AppLocalePreference.locale.collectAsStateWithLifecycle()
            // Real fix (2026-08-09), found via physical-device logcat: createConfigurationContext()
            // alone returns a bare android.app.ContextImpl with no ContextWrapper chain back to
            // the real Activity. That silently breaks every AndroidX mechanism that discovers the
            // hosting Activity by walking LocalContext.current's ContextWrapper.getBaseContext()
            // chain -- not just the explicit `as FragmentActivity` casts LocalRealActivity now
            // covers below, but also Compose-internal owner lookups such as
            // LocalActivityResultRegistryOwner (crashed live: "No ActivityResultRegistryOwner was
            // provided", HoodShared.kt's rememberRealLocationRequester -> MarketplaceScreen ->
            // HoodTab). Wrapping the config-overridden Resources in a ContextWrapper around the
            // REAL baseContext -- instead of using the bare configuration context directly --
            // keeps that chain intact while still serving localized strings.
            val localizedContext = remember(locale) {
                val config = Configuration(baseContext.resources.configuration)
                config.setLocale(Locale(locale))
                val configResources = baseContext.createConfigurationContext(config).resources
                object : android.content.ContextWrapper(baseContext) {
                    override fun getResources() = configResources
                }
            }
            // LocalRealActivity carries the real FragmentActivity (`this`) alongside the
            // locale-overridden LocalContext -- some call sites still cast LocalContext.current
            // directly instead of relying on chain-walking, so screens needing the real Activity
            // (biometrics, app-lock) must
            // read LocalRealActivity.current instead. See LocalRealActivity.kt.
            CompositionLocalProvider(LocalContext provides localizedContext, LocalRealActivity provides this) {
                val themeMode by ThemePreference.mode.collectAsStateWithLifecycle()
                val darkTheme = when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                IdsTheme(darkTheme = darkTheme) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val sessionState by SessionManager.sessionState.collectAsStateWithLifecycle()
                        when (sessionState) {
                            is SessionState.LoggedIn -> {
                                val tokenStore = remember { NetworkClient.currentTokenStore() }
                                val biometricAvailable = remember { NIDABiometricAuth(this).isAvailable() }
                                var unlocked by remember { mutableStateOf(AppUnlockState.unlockedThisProcess) }
                                // Real fix (2026-08-13, direct user correction: real
                                // Toss always challenges a returning user with a PIN
                                // OR biometric, never neither). This used to fall
                                // straight through to the app with zero re-entry
                                // challenge whenever biometrics weren't available (no
                                // enrollment, unsupported hardware) even with app-lock
                                // switched on -- PIN is that missing fallback, and
                                // also the one already offered as an explicit
                                // alternative if a biometric prompt itself fails. See
                                // TokenStore.setPin's own doc comment for why this
                                // isn't the "fake security" the original version of
                                // this gate worried a PIN store would be.
                                var useBiometricPreferred by remember { mutableStateOf(biometricAvailable) }
                                val hasPin = remember { tokenStore.hasPin() }
                                if (tokenStore.isAppLockEnabled() && !unlocked && (biometricAvailable || hasPin)) {
                                    if (useBiometricPreferred && biometricAvailable) {
                                        AppLockScreen(
                                            activity = this,
                                            onUnlocked = {
                                                AppUnlockState.unlockedThisProcess = true
                                                unlocked = true
                                            },
                                            onUsePinInstead = if (hasPin) { { useBiometricPreferred = false } } else null,
                                        )
                                    } else {
                                        PinEntryScreen(
                                            onUnlocked = {
                                                AppUnlockState.unlockedThisProcess = true
                                                unlocked = true
                                            },
                                            onUseBiometricInstead = if (biometricAvailable) { { useBiometricPreferred = true } } else null,
                                        )
                                    }
                                } else {
                                    ItundaAppScreen(
                                        openMapFromDeepLink = mapDeepLinkRequested,
                                        initialMapSearchQuery = mapSearchFromDeepLink,
                                        initialMapSharedFolder = mapSharedFolderFromDeepLink,
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
        // itunda://maps/shared/{userId}/{folderName} -- the exact link MapsScreen's own
        // folder-share sheet sends. Only /search was ever parsed here, so every shared
        // link the app itself handed out landed the recipient on a blank Maps tab
        // (found 2026-08-14). getSharedMapFolder is deliberately permitAll'd on the
        // backend, so this resolves even for a recipient who isn't signed in.
        mapSharedFolderFromDeepLink = if (mapDeepLinkRequested) {
            val segments = uri?.pathSegments.orEmpty()
            if (segments.size == 3 && segments[0].equals("shared", ignoreCase = true)) {
                val ownerId = segments[1].trim()
                val folderName = segments[2].trim()
                if (ownerId.isNotEmpty() && folderName.isNotEmpty()) ownerId to folderName else null
            } else {
                null
            }
        } else {
            null
        }
    }
}
