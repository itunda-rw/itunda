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

class WalletBalanceMiniAppActivity : SaroniteMiniAppActivity() {
    // Must match the AppRegistry.registerComponent name in
    // packages/saronite/host-app/index.js exactly.
    override fun getMainComponentName(): String = "SaroniteWalletBalance"
}

class PayBillsMiniAppActivity : SaroniteMiniAppActivity() {
    override fun getMainComponentName(): String = "SaronitePayBills"
}

class RewardTasksMiniAppActivity : SaroniteMiniAppActivity() {
    override fun getMainComponentName(): String = "SaroniteRewardTasks"
}
