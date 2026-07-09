package rw.itunda.app.SaroniteHost

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

/**
 * Hybrid App-in-Toss (React Native) Wrapper for Android
 * Hosts the Saronite SDK (Mini-Apps) within the secure native Kotlin shell.
 * 
 * Following Toss Granite's Brownfield integration pattern:
 * We use concrete Activity subclasses per mini-app rather than reading Intent extras.
 * This fixes the crash where ReactActivity builds its delegate during <init> before
 * Android attaches the launch Intent.
 */
abstract class SaroniteMiniAppActivity : ReactActivity() {
    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}

class WalletBalanceMiniAppActivity : SaroniteMiniAppActivity() {
    override fun getMainComponentName(): String = "wallet-balance"
}

class PayBillsMiniAppActivity : SaroniteMiniAppActivity() {
    override fun getMainComponentName(): String = "pay-bills"
}

class RewardTasksMiniAppActivity : SaroniteMiniAppActivity() {
    override fun getMainComponentName(): String = "reward-tasks"
}
