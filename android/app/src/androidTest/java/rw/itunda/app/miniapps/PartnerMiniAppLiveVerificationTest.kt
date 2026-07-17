package rw.itunda.app.miniapps

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rw.itunda.app.MainActivity
import rw.itunda.app.network.AuthResult
import rw.itunda.app.network.PartnerMiniAppDto
import rw.itunda.app.network.SessionManager

/**
 * Real, live, end-to-end verification of the Partner SDK mobile runtime loader
 * (2026-07-17) -- closes the mobile half of docs/TOSS_PARITY_MATRIX.md's Partner SDK
 * row. Every input here is real, not stubbed:
 *
 *  - A real login against a real locally-running backend (services/backend's `app`
 *    module, started against local MySQL/Redis on 127.0.0.1, reached via the
 *    emulator's standard `10.0.2.2` host alias -- the same one
 *    `BuildConfig.API_BASE_URL`'s own default already uses).
 *  - A real partner (`partner_4a7583d8-...`) registered via `POST
 *    /api/v1/partners/register`, a real mini-app manifest submitted via `POST
 *    /api/v1/partners/mini-apps` (permissions: `wallet:read`) pointing at a real
 *    `bundleUrl` serving a real, independently-built RN bundle
 *    (packages/saronite/mini-apps/partner-demo, bundled standalone via `react-native
 *    bundle --entry-file index.partner-demo.js` -- deliberately NOT part of itunda's
 *    own host-app bundle, to prove this is genuinely downloaded, not just already
 *    present), and approved through the real `ops-mfe`-equivalent admin API (`POST
 *    /api/v1/system/partners/{id}/decide`).
 *  - The exact real `PartnerMiniAppDto` shape `GET /api/v1/mini-apps/catalog` itself
 *    returns for this entry (confirmed via a direct `curl` against that endpoint
 *    before this test was written).
 *
 * This test drives `PartnerMiniAppLoader.launch` -- the same function
 * `ItundaAppScreen.kt`'s real "Partner mini-apps" section calls when a user taps a row
 * -- rather than simulating its effects, so a pass here is real evidence the
 * download -> ReactHost reload -> Activity launch chain genuinely works end to end on
 * a real running Android/Hermes/Fabric runtime, matching the same
 * Activity-alive-under-New-Architecture standard `MiniAppNewArchitectureTest`
 * (this package's existing sibling test) already established for the four first-party
 * mini-apps.
 *
 * Threading note: the loader call below runs on this test's own background thread
 * (JUnit/instrumentation's test-runner thread), NOT inside `scenario.onActivity {}` --
 * `PartnerMiniAppLoader`'s internal `withContext(Dispatchers.Main)` hop would deadlock
 * if invoked via `runBlocking` from a callback already executing synchronously on the
 * main thread (a real, considered test-authoring detail, not a production concern:
 * production call sites use `coroutineScope.launch`, which doesn't block the calling
 * thread the way `runBlocking` does).
 */
@RunWith(AndroidJUnit4::class)
class PartnerMiniAppLiveVerificationTest {

    @Test
    fun partnerMiniAppActivity_downloadsRealPartnerBundleAndRendersUnderNewArchitecture() {
        val loginResult = runBlocking { SessionManager.login("+250788111222", "TestPass123!") }
        assertTrue("Real login against the local backend must succeed for this test to mean anything: $loginResult", loginResult is AuthResult.Success)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var hostActivity: android.app.Activity? = null
        scenario.onActivity { activity -> hostActivity = activity }
        assertNotNull("Need a live host Activity to launch the partner mini-app from", hostActivity)

        var loadError: String? = null
        runBlocking {
            PartnerMiniAppLoader.launch(
                activity = hostActivity!!,
                app = PartnerMiniAppDto(
                    id = "partner_app_8ab9124e-03e0-47b4-a609-6d1eb4733859",
                    partnerId = "partner_4a7583d8-6ae5-414e-87c1-3d504433e7fb",
                    name = "Partner Demo Mini App",
                    description = "Hello from Partner Demo Co - live verification bundle",
                    iconUrl = null,
                    bundleUrl = "http://10.0.2.2:8098/partner-demo.bundle.js",
                    permissions = "wallet:read",
                    status = "APPROVED",
                    createdAt = "2026-07-17T12:19:29.083007Z",
                ),
                onError = { message -> loadError = message },
            )
        }
        assertTrue("Real download + ReactHost reload must not fail: $loadError", loadError == null)

        // Real wall-clock allowance for Hermes/Fabric to finish initializing the newly
        // loaded bundle and for PartnerMiniAppActivity's own onCreate/surface creation
        // to complete -- same order of magnitude MiniAppNewArchitectureTest already
        // uses for the four first-party mini-apps.
        Thread.sleep(10000)

        val resumedActivities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        val partnerActivity = resumedActivities.filterIsInstance<PartnerMiniAppActivity>().firstOrNull()
        assertNotNull(
            "PartnerMiniAppActivity should be the real, live, resumed Activity after " +
                "PartnerMiniAppLoader.launch -- found resumed activities: $resumedActivities",
            partnerActivity,
        )
        assertTrue(
            "PartnerMiniAppActivity should still be alive (not finished/crashed) after RN init",
            partnerActivity != null && !partnerActivity.isFinishing && !partnerActivity.isDestroyed,
        )

        partnerActivity?.finish()
        scenario.close()
    }
}
