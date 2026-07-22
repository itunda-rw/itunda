package rw.itunda.app.miniapps

import android.app.Application
import com.brickmodule.BrickModulePackage
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import rw.itunda.app.BuildConfig
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SessionManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Custom Application class providing the React Native host that Apps-in-Itunda
 * mini-apps run on -- the "Toss Bank" native `MainActivity`/`ItundaAppScreen`
 * Compose UI is completely unaffected; this only exists to satisfy
 * `ReactActivity`'s requirement that the Application implement `ReactApplication`.
 *
 * Registered in AndroidManifest.xml as `android:name=".miniapps.ItundaApplication"`.
 *
 * Rewritten 2026-07-12 (granite-adoption stage 4) from the legacy
 * `ReactNativeHost`/`ReactInstanceManager` pattern to RN 0.84's real `ReactHost`/
 * Bridgeless API -- not a style preference, a hard requirement: a live,
 * instrumented on-device test (`MiniAppNewArchitectureTest`) crashed with
 * `ReactInstanceManager.createReactContext is unsupported` the moment New
 * Architecture was enabled, because Bridgeless mode (default under New
 * Architecture at this RN version) removes the legacy bridge entirely. Matches
 * the real official `@react-native-community/template@0.84.0`'s
 * `MainApplication.kt` shape exactly (fetched and compared directly, not
 * guessed), with itunda's two real additions layered on: the manually-registered
 * `SaronitePackage` (autolinking, wired at the settings level in stage 2, doesn't
 * pick this up -- `@itunda/saronite-react-native` has no autolinking config
 * markers) alongside the now-real autolinked `PackageList`, and the CDN
 * mini-app-bundle downloader (`docs/ARCHITECTURE.md` §2's backlog item) via
 * `getDefaultReactHost`'s `jsBundleFilePath` parameter, which accepts a real file
 * path directly -- the modern equivalent of the old `getJSBundleFile()` override.
 */
class ItundaApplication : Application(), ReactApplication {

    override val reactHost: ReactHost by lazy {
        // Same conditional logic as the pre-rewrite getJSBundleFile() override:
        // debug builds (every mode exercised on-device this session) and an unset
        // CDN URL both fall through to null -> getDefaultReactHost's own default
        // asset-bundle loader; only an explicit -PminiAppBundleCdnUrl in a release
        // build activates the downloader. Same honest, documented risk as before:
        // this makes a synchronous network call, not yet confirmed live off the
        // main thread under the new ReactHost startup sequence.
        val cdnUrl = BuildConfig.MINIAPP_BUNDLE_CDN_URL
        val cdnBundleFilePath = if (BuildConfig.DEBUG || cdnUrl.isBlank()) {
            null
        } else {
            MiniAppBundleDownloader.downloadAndCache(this, cdnUrl)
        }
        getDefaultReactHost(
            context = applicationContext,
            packageList = PackageList(this).packages.apply {
                add(SaronitePackage(ItundaSaroniteHostBridge()))
                // Real granite native bridge (2026-07-12, granite-adoption stage 7) --
                // BrickModulePackage is what makes the "BrickModule" TurboModule (the
                // aggregated class brick-codegen generates at android/.brick,
                // confirmed by reading BrickModulePackage.getModule's real
                // Class.forName("com.brickmodule.codegen.BrickModule", ...) lookup)
                // resolvable at all -- without this, importing granite's real
                // closeView/getSchemeUri throws
                // "TurboModuleRegistry.getEnforcing(...): 'BrickModule' could not be
                // found", confirmed live before this was added.
                add(BrickModulePackage())
            },
            jsBundleFilePath = cdnBundleFilePath,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // loadReactNative() replaces the old explicit SoLoader.init(...) call --
        // confirmed against the real official template, which calls only this, no
        // separate SoLoader call. It handles the merged-.so mapping internally
        // (see the git history on this file for the UnsatisfiedLinkError on
        // libreact_featureflagsjni.so that the old explicit
        // SoLoader.init(this, OpenSourceMergedSoMapping) call fixed, one step
        // before this rewrite superseded that call site).
        loadReactNative(this)
        // Real login/session flow (2026-07-11) -- must run before any screen can make
        // an authenticated request. See network/NetworkClient.kt/SessionManager.kt.
        NetworkClient.init(this, rw.itunda.app.BuildConfig.API_BASE_URL)
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
