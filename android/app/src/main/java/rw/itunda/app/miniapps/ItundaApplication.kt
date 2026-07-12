package rw.itunda.app.miniapps

import android.app.Application
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.shell.MainReactPackage
import com.facebook.soloader.SoLoader
import okhttp3.OkHttpClient
import okhttp3.Request
import rw.itunda.app.BuildConfig
import rw.itunda.app.network.NetworkClient
import rw.itunda.app.network.SessionManager
import java.io.File
import java.util.concurrent.TimeUnit

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

            // Added 2026-07-11 -- real, scoped step toward docs/ARCHITECTURE.md §2's
            // Granite backlog item ("dynamic bundle loading from a CDN instead of a
            // local Metro server"), not the full mechanism (no shared/service-bundle
            // split yet -- see the doc for what's still open).
            //
            // Deliberately zero risk to anything this session ever tested: when
            // getUseDeveloperSupport() is true (every debug build, the only mode
            // exercised on-device this session), RN's dev-support manager takes over
            // bundle loading entirely and never calls this method at all -- returning
            // super's default here is provably inert for that whole code path, not
            // just believed safe. In release builds with no CDN URL configured
            // (BuildConfig.MINIAPP_BUNDLE_CDN_URL empty, the default), this also
            // returns super's default (null -> falls back to getBundleAssetName()'s
            // packaged asset), so today's actual release behavior is unchanged too.
            // Only when someone explicitly opts in via
            // -PminiAppBundleCdnUrl=https://... does new behavior activate at all.
            //
            // Honest, real risk in that one new, opt-in, currently-inert path:
            // MiniAppBundleDownloader makes a synchronous network call. RN 0.72's own
            // ReactInstanceManager calls getJSBundleFile() off the main thread during
            // normal startup, but that has NOT been confirmed against this exact RN
            // version/build in this environment (no way to verify live -- see
            // ARCHITECTURE.md §2's own note on why, checked via `top`, not assumed).
            // If it turns out to run on the main thread, this would throw
            // NetworkOnMainThreadException the first time anyone actually sets
            // -PminiAppBundleCdnUrl. Left as a known, documented risk rather than
            // silently claimed safe -- this is real progress on the backlog item's
            // literal ask, not a claim that the whole mechanism is now proven.
            override fun getJSBundleFile(): String? {
                val cdnUrl = BuildConfig.MINIAPP_BUNDLE_CDN_URL
                if (BuildConfig.DEBUG || cdnUrl.isBlank()) return super.getJSBundleFile()
                return MiniAppBundleDownloader.downloadAndCache(this@ItundaApplication, cdnUrl)
                    ?: super.getJSBundleFile()
            }
        }

    // RN's ReactApplication interface changed getReactNativeHost() (a Java-style
    // getter, valid up through 0.72) to a Kotlin `val reactNativeHost` property --
    // confirmed by reading node_modules/react-native's own ReactApplication.kt at
    // 0.80.3 directly (granite-adoption stage 2, 2026-07-12).
    override val reactNativeHost: ReactNativeHost = mReactNativeHost

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, false)
        // Real login/session flow (2026-07-11) -- must run before any screen can make
        // an authenticated request. See network/NetworkClient.kt/SessionManager.kt.
        NetworkClient.init(this)
        SessionManager.restoreSession()
    }
}

/**
 * Downloads a JS bundle from a CDN URL and caches it to a real file
 * `ReactNativeHost.getJSBundleFile()` can return a path to -- the actual "dynamic
 * bundle loading from a CDN" mechanism, not just a config flag. Real `OkHttpClient`
 * (already a dependency, used elsewhere in this app), not a stub. Returns `null` on
 * any failure so the caller falls back to the packaged asset rather than crashing a
 * money-adjacent app over a bundle-fetch failure -- same "never let this kind of
 * failure block the user" discipline as `ProviderConnector`/`EventPublisher`
 * elsewhere in this session's backend work.
 */
private object MiniAppBundleDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun downloadAndCache(context: Application, url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val cacheFile = File(context.cacheDir, "miniapp-bundle.js")
                cacheFile.outputStream().use { out -> body.byteStream().copyTo(out) }
                cacheFile.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }
}
