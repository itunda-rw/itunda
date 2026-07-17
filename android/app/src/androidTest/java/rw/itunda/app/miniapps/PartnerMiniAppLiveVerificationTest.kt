package rw.itunda.app.miniapps

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
                    id = "partner_app_aa868698-54ff-4e1e-8d57-eb7abcb1d1f2",
                    partnerId = "partner_2138e03e-3006-4360-92ea-4903c8393a63",
                    name = "OnDevice Partner Demo",
                    description = "Real on-device Partner SDK verification bundle",
                    iconUrl = null,
                    // "localhost" here (not the emulator-only "10.0.2.2" alias) because this
                    // test now runs on a real physical device reached via `adb reverse
                    // tcp:8098 tcp:8098` -- the device's own "localhost:8098" is forwarded to
                    // this host machine over the USB connection, same convention
                    // BuildConfig.API_BASE_URL's own physical-device override already uses.
                    bundleUrl = "http://localhost:8098/partner-demo.bundle.js",
                    permissions = "wallet:read",
                    status = "APPROVED",
                    createdAt = "2026-07-17T14:41:38.290861Z",
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

        // ActivityLifecycleMonitorRegistry.getActivitiesInStage() asserts it's called on
        // the main thread internally (ActivityLifecycleMonitorImpl.checkMainThread) --
        // this test method itself runs on the instrumentation test-runner thread, not
        // main, so the query must be dispatched via runOnMainSync, not called directly
        // (a real bug this pass's own on-device run caught: the direct call threw
        // IllegalStateException every time, never actually reaching the assertions below).
        var resumedActivities: Collection<android.app.Activity> = emptyList()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumedActivities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        }
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
