package rw.itunda.app.miniapps

import android.app.Application
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.shell.MainReactPackage
import com.facebook.soloader.SoLoader
import rw.itunda.app.BuildConfig

/**
 * Custom Application class providing the React Native host that Apps-in-Itunda
 * mini-apps run on -- the "Toss Bank" native `MainActivity`/`ItundaAppScreen`
 * Compose UI is completely unaffected; this only exists to satisfy
 * `ReactActivity`'s requirement that the Application implement `ReactApplication`.
 *
 * Registered in AndroidManifest.xml as `android:name=".miniapps.ItundaApplication"`.
 * Old Native Modules architecture (newArchEnabled=false, matching
 * packages/saronite/packages/brownfield-module's proven-standalone setup) --
 * no Fabric/TurboModule codegen, deliberately, for the same reason documented
 * there: not worth the risk for a handful of simple mini-apps.
 */
class ItundaApplication : Application(), ReactApplication {

    // RN 0.72's ReactApplication is a plain Java-style getter (getReactNativeHost()),
    // not the Kotlin `val` property RN 0.80's interface exposes -- real API
    // difference hit while downgrading, see the comment on the react-android
    // dependency in build.gradle.kts for why.
    private val mReactNativeHost: ReactNativeHost =
        object : ReactNativeHost(this) {
            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

            // No RN CLI autolinking here (no react-native.config.js-driven codegen
            // wired into this Gradle build), so there's no generated PackageList --
            // core view managers come from MainReactPackage directly, plus the one
            // real bridge module mini-apps actually use.
            override fun getPackages(): List<ReactPackage> =
                listOf(MainReactPackage(), SaronitePackage(ItundaSaroniteHostBridge()))

            override fun getJSMainModuleName(): String = "index"

            override fun getBundleAssetName(): String = "index.android.bundle"
        }

    override fun getReactNativeHost(): ReactNativeHost = mReactNativeHost

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, false)
    }
}
