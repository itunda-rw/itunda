package rw.itunda.app.miniapps

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.brickmodule.BrickModulePackage
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import rw.itunda.app.BuildConfig
import rw.itunda.app.push.NotificationChannels
import rw.itunda.core.network.AppLocalePreference
import rw.itunda.core.network.MapConfig
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SessionManager
import rw.itunda.core.network.TextScalePreference
import rw.itunda.core.network.ThemePreference
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

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
class ItundaApplication : Application(), ReactApplication, ImageLoaderFactory {

    // Real Toss motion research (2026-08-12): "loading moments get softened," the same
    // principle this session already applied to skeleton loading/celebration screens,
    // extended here to real image loads -- 18 real AsyncImage call sites across 6
    // modules (Marketplace/Shop/Eats/Talk/Maps listings, avatars) all used Coil's raw
    // defaults with no crossfade, a hard pop-in the moment each image finished
    // downloading. Coil's own documented ImageLoaderFactory mechanism applies this
    // app-wide from one place -- every AsyncImage call automatically picks up
    // Context.imageLoader, so no per-call-site changes were needed or made.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .build()

    override val reactHost: ReactHost by lazy {
        // Host construction must never perform network I/O. Use only a previously
        // downloaded, digest-verified cache; a cold install uses the packaged bundle.
        // onCreate() warms the cache on a background executor for the next launch.
        val cdnUrl = BuildConfig.MINIAPP_BUNDLE_CDN_URL
        val cdnSha256 = BuildConfig.MINIAPP_BUNDLE_CDN_SHA256
        val cdnBundleFilePath = if (BuildConfig.DEBUG || cdnUrl.isBlank() || cdnSha256.isBlank()) {
            null
        } else {
            MiniAppBundleDownloader.verifiedCachedBundlePath(this, cdnSha256)
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
        // Never block application startup or ReactHost construction on a CDN. A
        // successfully verified bundle becomes eligible on the next process launch.
        val cdnUrl = BuildConfig.MINIAPP_BUNDLE_CDN_URL
        val cdnSha256 = BuildConfig.MINIAPP_BUNDLE_CDN_SHA256
        if (!BuildConfig.DEBUG && cdnUrl.isNotBlank() && cdnSha256.isNotBlank()) {
            MiniAppBundleDownloader.warmCacheAsync(this, cdnUrl, cdnSha256)
        }
        // Real login/session flow (2026-07-11) -- must run before any screen can make
        // an authenticated request. See network/NetworkClient.kt/SessionManager.kt.
        NetworkClient.init(this, rw.itunda.app.BuildConfig.API_BASE_URL)
        // Real BuildConfig-avoidance (2026-07-23) -- see MapConfig.kt's own doc comment.
        MapConfig.init(rw.itunda.app.BuildConfig.TILES_BASE_URL, rw.itunda.app.BuildConfig.GLYPHS_BASE_URL)
        SessionManager.restoreSession()
        ThemePreference.restore()
        AppLocalePreference.restore(this)
        TextScalePreference.restore()
        // Real push notifications (2026-08-12) -- channels must exist before any
        // notification can post; see NotificationChannels.kt's own doc comment.
        NotificationChannels.createAll(this)
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
    private const val MAX_BUNDLE_BYTES = 50L * 1024L * 1024L
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "itunda-miniapp-bundle").apply { isDaemon = true }
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    fun warmCacheAsync(context: Application, url: String, expectedSha256: String) {
        if (!expectedSha256.matches(Regex("(?i)[0-9a-f]{64}"))) return
        executor.execute { downloadAndCache(context, url, expectedSha256) }
    }

    fun verifiedCachedBundlePath(context: Application, expectedSha256: String): String? {
        if (!expectedSha256.matches(Regex("(?i)[0-9a-f]{64}"))) return null
        val cacheFile = File(context.cacheDir, "miniapp-bundle.js")
        return try {
            if (!cacheFile.isFile || cacheFile.length() <= 0L ||
                cacheFile.length() > MAX_BUNDLE_BYTES) return null
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            cacheFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (actual.equals(expectedSha256, ignoreCase = true)) cacheFile.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    fun downloadAndCache(context: Application, url: String, expectedSha256: String): String? {
        if (!expectedSha256.matches(Regex("(?i)[0-9a-f]{64}"))) return null
        return try {
            val request = Request.Builder().url(url).build()
            if (!request.url.isHttps || request.url.username.isNotEmpty() || request.url.password.isNotEmpty()) {
                return null
            }
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || !response.request.url.isHttps) return null
                val body = response.body ?: return null
                if (body.contentLength() > MAX_BUNDLE_BYTES) return null

                val cacheFile = File(context.cacheDir, "miniapp-bundle.js")
                val tempFile = File(context.cacheDir, "miniapp-bundle.js.tmp")
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                var total = 0L
                try {
                    body.byteStream().use { input ->
                        tempFile.outputStream().buffered().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_BUNDLE_BYTES) throw IllegalStateException("Mini-app bundle exceeds size limit")
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    val actualSha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    if (total == 0L || !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        tempFile.delete()
                        return null
                    }
                    // Do not delete the last-known-good file until the replacement is verified.
                    val backupFile = File(context.cacheDir, "miniapp-bundle.previous.js")
                    if (cacheFile.exists() && !cacheFile.renameTo(backupFile)) {
                        tempFile.delete()
                        return null
                    }
                    if (!tempFile.renameTo(cacheFile)) {
                        if (backupFile.exists()) backupFile.renameTo(cacheFile)
                        tempFile.delete()
                        return null
                    }
                    backupFile.delete()
                    cacheFile.absolutePath
                } catch (e: Exception) {
                    tempFile.delete()
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
