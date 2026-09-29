package rw.itunda.app.miniapps

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import rw.itunda.core.network.PartnerMiniAppDto
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real runtime loader for a REAL approved partner mini-app -- closes the mobile half of
 * docs/TOSS_PARITY_MATRIX.md's Partner SDK row (2026-07-17). Until this file, a partner
 * got a real account, a real reviewed submission, and a real `GET
 * /api/v1/mini-apps/catalog` listing (services/backend/partners), but nothing on a real
 * device ever downloaded or ran their code -- this is that missing mechanism.
 *
 * Architecture, and why it's built this way rather than a second concurrent RN runtime:
 * `ItundaApplication.reactHost` is built once via RN 0.84's `DefaultReactHost` helper,
 * which is itself a process-wide singleton (confirmed by reading
 * `com.facebook.react.defaults.DefaultReactHost`'s own source: `private var reactHost:
 * ReactHost? = null`, only ever built once, and its own `invalidate()` is `internal` --
 * not visible outside RN's own Gradle module, so this app can't call it to force a
 * second independent instance). Standing up a second, wholly separate `ReactHostImpl`
 * side-by-side was considered and rejected: it would mean two live Hermes/Fabric
 * runtimes racing for the same native singletons (SoLoader, `DefaultComponentsRegistry`,
 * the JSI-backed component factory) in one process, a genuinely novel and risky
 * combination this pass has no way to soak-test properly. Instead, this reuses RN's own
 * real, public, intended-for-exactly-this mechanism: `ReactHost.devSupportManager` is a
 * public, mutable `bundleFilePath` -- confirmed by reading
 * `ReactHostImpl.setBundleSource(filePath)`'s own implementation, which does nothing
 * more than `devSupportManager.bundleFilePath = filePath; reload(...)` -- and
 * `ReactHostImpl`'s private `jsBundleLoader` getter checks `bundleFilePath != null`
 * *before* it ever looks at dev-support/Metro/asset fallback, so this genuinely
 * overrides whatever bundle source the host would otherwise use, in both debug (Metro)
 * and release (packaged asset) builds. The one thing the public `setBundleSource(String)`
 * overload doesn't expose is a way back to null -- so this talks to
 * `reactHost.devSupportManager.bundleFilePath` directly (also public) to restore it,
 * rather than reaching for anything internal.
 *
 * Net effect: loading a partner mini-app reloads the ONE shared ReactHost onto the
 * partner's downloaded bundle (tearing down whichever first-party mini-app JS was
 * loaded), and leaving a partner mini-app reloads it back. Two mini-apps -- first-party
 * or partner -- are never rendered at the same instant; this matches how the four
 * first-party mini-app Activities were already being used in practice (one at a time,
 * never stacked), so it's a real, not merely convenient, fit rather than a corner cut to
 * make the mechanism easier.
 *
 * Reuses the exact same native bridge every first-party mini-app already runs on --
 * `SaronitePackage`/`ItundaSaroniteHostBridge` (SaroniteBridge.kt) and granite's own
 * `BrickModulePackage`/`GraniteBrownfieldModuleImpl` -- nothing here is a second,
 * parallel bridge. `MiniAppSecurityContext` (below) is the one real behavioral
 * difference: while a partner bundle is loaded, `SaroniteBrownfieldModule` consults it
 * before serving any bridge call, so a partner mini-app's JS is held to its own real
 * approved scopes rather than getting the same trusted, unrestricted access the four
 * first-party mini-apps have always had.
 */
object PartnerMiniAppLoader {
    // The one fixed AppRegistry/granite `appName` convention every partner bundle must
    // register under -- this backend has no concept of a mobile "component name" field
    // on `PartnerMiniApp` (only `bundleUrl`), so the host needs one well-known name to
    // look for, the same way each first-party mini-app Activity hardcodes its own.
    // Documented in the Partner SDK matrix row as the real, current limit this implies:
    // only one partner mini-app's bundle can be the "active" one at a time (whichever
    // was downloaded most recently), since there's exactly one slot for this name.
    const val PARTNER_COMPONENT_NAME = "SaronitePartnerMiniApp"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Process-lifetime scope for the one restore call that must outlive the Activity
    // that triggers it (PartnerMiniAppActivity.onDestroy) -- see that call site's own
    // comment for why the Activity's lifecycleScope can't be used for this specific call.
    private val restoreScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Downloads [app]'s real `bundleUrl`, swaps it into the shared ReactHost, waits for
     * the reload to actually finish, arms [MiniAppSecurityContext] with this partner's
     * real approved scopes, and only then launches [PartnerMiniAppActivity] -- in that
     * order, so the Activity's `ReactActivityDelegate.onCreate()` never races a JS bundle
     * that hasn't registered `SaronitePartnerMiniApp` yet.
     *
     * Every failure path (download, HTTP error, reload fault) calls [onError] with a
     * real, specific message instead of silently doing nothing or crashing -- launching
     * a partner's arbitrary remote code is exactly the kind of call site that must never
     * paper over a real failure.
     */
    suspend fun launch(activity: Activity, app: PartnerMiniAppDto, onError: (String) -> Unit) {
        val application = activity.applicationContext as ItundaApplication
        try {
            val bundleFile = downloadBundle(activity, app)
            reloadHostWithBundle(application, bundleFile.absolutePath)
            MiniAppSecurityContext.activeScopes = app.permissions
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            activity.startActivity(Intent(activity, PartnerMiniAppActivity::class.java))
        } catch (e: IOException) {
            onError(e.message ?: "Could not download this mini-app's bundle")
        } catch (e: Exception) {
            onError(e.message ?: "Could not load this mini-app")
        }
    }

    /** Reverts the shared ReactHost back to the first-party bundle (packaged asset in a
     * release build, Metro in debug -- whichever it would have used had a partner bundle
     * never been loaded) and disarms [MiniAppSecurityContext]. Called from
     * [PartnerMiniAppActivity]'s `onDestroy` so the four first-party mini-apps keep
     * working correctly the next time any of them is opened. */
    suspend fun restoreFirstPartyBundle(activity: Activity) {
        val application = activity.applicationContext as ItundaApplication
        MiniAppSecurityContext.activeScopes = null
        reloadHostWithBundle(application, filePath = null)
    }

    /** Fire-and-forget wrapper around [restoreFirstPartyBundle] for call sites (namely
     * `PartnerMiniAppActivity.onDestroy`) that run after their own lifecycle scope is
     * no longer safe to launch on. */
    fun restoreFirstPartyBundleAsync(activity: Activity) {
        restoreScope.launch { restoreFirstPartyBundle(activity) }
    }

    private suspend fun downloadBundle(context: Context, app: PartnerMiniAppDto): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(app.bundleUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Bundle download failed: HTTP ${response.code} for ${app.bundleUrl}")
            }
            val body = response.body ?: throw IOException("Empty bundle response from ${app.bundleUrl}")
            // Keyed by mini-app id so two different partner apps don't clobber each
            // other's cached file if a user backs out and taps a different one before
            // this one's file would otherwise be evicted.
            val file = File(context.cacheDir, "partner-mini-app-${app.id}.bundle.js")
            file.outputStream().use { out -> body.byteStream().copyTo(out) }
            file
        }
    }

    /**
     * [filePath] non-null: point the shared ReactHost's bundle source at that real
     * downloaded file and reload. Null: restore the default source by clearing
     * `devSupportManager.bundleFilePath` directly (the one thing the public
     * `setBundleSource(String)` overload can't do -- see this file's own header comment)
     * and reloading again.
     *
     * `reload()` is `@ThreadConfined(UI)` in RN's own source, so the mutation + call
     * happen on Main; the returned `TaskInterface` has no coroutine-friendly API of its
     * own (only a blocking `waitForCompletion()`), so that wait is pushed onto IO rather
     * than blocking Main.
     */
    private suspend fun reloadHostWithBundle(application: ItundaApplication, filePath: String?) {
        val reactHost = application.reactHost
        val task = withContext(Dispatchers.Main) {
            reactHost.devSupportManager?.bundleFilePath = filePath
            reactHost.reload(if (filePath != null) "Load partner mini-app bundle" else "Restore first-party mini-app bundle")
        }
        // Bounded wait, not an indefinite block -- a hung reload (a malformed partner
        // bundle that never finishes evaluating, say) must surface as a real, timely
        // error rather than freezing this coroutine (and, transitively, whatever caller
        // is awaiting it) forever. 30s is well past the ~8-10s this pass's own live
        // verification saw a real reload actually take.
        val completed = withContext(Dispatchers.IO) { task.waitForCompletion(30, TimeUnit.SECONDS) }
        if (!completed) {
            throw IOException("React Native reload timed out")
        }
        if (task.isFaulted()) {
            throw IOException("React Native reload failed: ${task.getError()?.message ?: "unknown error"}")
        }
    }
}

/**
 * Real, minimal runtime scope enforcement for partner mini-apps (2026-07-17) -- the
 * "if time permits" stretch goal named in docs/TOSS_PARITY_MATRIX.md's Partner SDK row.
 * `null` means "no partner mini-app is currently loaded" -- the four first-party
 * mini-apps keep their existing, fully-trusted, unrestricted access to every bridge
 * call, exactly as before this pass. A non-null set means a partner bundle is the one
 * currently loaded into the shared ReactHost, holding exactly the real scopes
 * `PartnerMiniAppPermissions.ALLOWED` let it request at submission time
 * (`account:read`/`transactions:read`/`profile:read` -- see
 * services/backend/partners/.../PartnerService.kt).
 *
 * Deliberately a single process-wide var, not per-ReactContext state: there is only ever
 * one active mini-app bundle loaded at a time (see PartnerMiniAppLoader's own header
 * comment), so this mirrors that real constraint rather than inventing per-instance
 * state nothing in this host actually has.
 */
object MiniAppSecurityContext {
    @Volatile
    var activeScopes: Set<String>? = null

    /**
     * `null` [requiredScope] means "no real backend scope covers this bridge call at
     * all" -- today that's every `SaroniteBrownfieldModule` method except
     * `getAccountBalance` (see that file's own per-method comments), since
     * `PartnerMiniAppPermissions.ALLOWED` is a deliberately small, conservative,
     * read-only allow-list that doesn't yet cover bill payment, reward claims, insurance
     * enrollment, or profile writes. A first-party mini-app (activeScopes == null) is
     * always allowed, matching its existing trusted behavior; a partner mini-app is only
     * allowed a call whose real scope it was actually approved for.
     */
    fun isAllowed(requiredScope: String?): Boolean {
        val scopes = activeScopes ?: return true
        return requiredScope != null && scopes.contains(requiredScope)
    }
}
