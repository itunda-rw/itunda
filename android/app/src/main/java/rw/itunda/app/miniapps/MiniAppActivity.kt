package rw.itunda.app.miniapps

import com.brickmodule.BrickModuleRegistrar
import com.brickmodule.BrickModuleRegistry
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate
import rw.itunda.app.BuildConfig

/**
 * One concrete Activity subclass per mini-app, each hardcoding its own RN
 * component name -- deliberately not a single generic Activity reading the
 * target mini-app from an Intent extra. `ReactActivity` builds its delegate
 * during `<init>`, before Android attaches the launch `Intent`, so an
 * Intent-extra-based design crashes with a null Intent on first launch. This
 * exact bug (and fix) was documented, but never actually verified building or
 * running, in an earlier pass -- see saronite/README.md's correction and
 * ARCHITECTURE.md §3. This is the first time it's built into a real,
 * launchable app.
 *
 * `BrickModuleRegistrar` (2026-07-12, granite-adoption stage 7): the real
 * generated `BrickModuleImpl` (android/.brick, produced by `brick-codegen`)
 * looks up modules via `reactContext.currentActivity as? BrickModuleRegistrar`
 * -- confirmed by reading that generated file directly -- so the Activity
 * itself, not just the Application, has to implement this. Registration is
 * lazy (on first access, not in `<init>`/`onCreate`): `reactHost.currentReactContext`
 * is null until RN actually finishes initializing, and this is only ever
 * queried from within a real JS-to-native bridge call, which can't happen
 * before that anyway.
 */
abstract class SaroniteMiniAppActivity : ReactActivity(), BrickModuleRegistrar {
    override fun createReactActivityDelegate(): ReactActivityDelegate =
        // fabricEnabled reads the real, plugin-generated BuildConfig flag
        // (granite-adoption stage 4, 2026-07-12) rather than a hardcoded `false`
        // -- it must track newArchEnabled, since Fabric is New Architecture's
        // renderer; leaving this false while New Architecture is on elsewhere
        // would mismatch the RootView's renderer against the ReactHost built in
        // ItundaApplication.kt.
        DefaultReactActivityDelegate(this, mainComponentName!!, BuildConfig.IS_NEW_ARCHITECTURE_ENABLED)

    private val brickModuleRegistry: BrickModuleRegistry by lazy {
        BrickModuleRegistry().apply {
            val reactContext = requireNotNull(reactHost.currentReactContext) {
                "BrickModuleRegistry requested before ReactContext is ready"
            }
            push(
                GraniteBrownfieldModuleImpl(
                    reactContext = reactContext,
                    getActivity = { this@SaroniteMiniAppActivity },
                    // Matches ItundaSaroniteHostBridge.getSchemeUri() ("itunda://saronite")
                    // extended with a per-mini-app path segment, granite's own real
                    // getSchemePrefix convention (scheme://host/appName).
                    scheme = "itunda://saronite/${mainComponentName?.removePrefix("Saronite")?.lowercase()}",
                )
            )
        }
    }

    override fun getModuleRegistry(): BrickModuleRegistry = brickModuleRegistry
}

// All four mini-apps now register via real granite (`Granite.registerApp({ appName, ... })`
// in each mini-app's own app.tsx, 2026-07-13) -- this `getMainComponentName()` must match that
// `appName` exactly, same requirement as the old plain `AppRegistry.registerComponent` name it
// replaced (granite calls that same underlying API internally, confirmed by reading its source).
class AccountBalanceMiniAppActivity : SaroniteMiniAppActivity() {
    override fun getMainComponentName(): String = "SaroniteAccountBalance"
}

class PayBillsMiniAppActivity : SaroniteMiniAppActivity() {
    override fun getMainComponentName(): String = "SaronitePayBills"
}

class RewardTasksMiniAppActivity : SaroniteMiniAppActivity() {
    override fun getMainComponentName(): String = "SaroniteRewardTasks"
}

class InsuranceMiniAppActivity : SaroniteMiniAppActivity() {
    override fun getMainComponentName(): String = "SaroniteInsurance"
}

/**
 * The real, dynamic fifth mini-app Activity (2026-07-17) -- unlike the four above, this
 * one's JS was never bundled into the app at build time. `PartnerMiniAppLoader.launch`
 * downloads a REAL approved partner's real `bundleUrl`, reloads it into the shared
 * ReactHost, and only then starts this Activity; see that file's own header comment for
 * the full architecture and why this reuses the same single ReactHost/bridge rather
 * than a second one.
 *
 * `getMainComponentName()` is fixed, not per-partner: every partner bundle must register
 * under this one well-known name (`PartnerMiniAppLoader.PARTNER_COMPONENT_NAME`), the
 * real, honest, one-partner-active-at-a-time constraint this pass's approach implies --
 * documented in docs/TOSS_PARITY_MATRIX.md's Partner SDK row, not hidden.
 *
 * `onDestroy` restores the shared ReactHost back to the first-party bundle so
 * Account/PayBills/RewardTasks/Insurance keep working the next time any of them opens --
 * without this, the host would stay pointed at the partner's bundle forever and every
 * first-party mini-app Activity would try (and fail) to find its own component name in
 * JS that no longer registers it. Fired via `PartnerMiniAppLoader`'s own
 * process-lifetime coroutine scope, deliberately not this Activity's `lifecycleScope` --
 * that scope is cancelled by the very `ON_DESTROY` event this method runs inside, so a
 * `lifecycleScope.launch` called from here would race its own cancellation.
 */
class PartnerMiniAppActivity : SaroniteMiniAppActivity() {
    override fun getMainComponentName(): String = PartnerMiniAppLoader.PARTNER_COMPONENT_NAME

    override fun onDestroy() {
        PartnerMiniAppLoader.restoreFirstPartyBundleAsync(this)
        super.onDestroy()
    }
}
