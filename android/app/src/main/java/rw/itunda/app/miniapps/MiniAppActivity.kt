package rw.itunda.app.miniapps

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate

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
 */
abstract class SaroniteMiniAppActivity : ReactActivity() {
    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName!!, false)
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
