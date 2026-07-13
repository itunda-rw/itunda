package rw.itunda.app.miniapps

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real, live verification that the mini-app React Native runtime initializes under
 * New Architecture (granite-adoption stage 4, 2026-07-12) without reproducing the
 * class of native crash (UnsatisfiedLinkError on libreact_featureflagsjni.so) that
 * forced the original 0.80.3 -> 0.72.17 downgrade -- see the git history on
 * app/build.gradle.kts's react-android dependency and android/gradle.properties's
 * newArchEnabled comment for that incident.
 *
 * PayBillsMiniAppActivity/etc. are android:exported="false" (AndroidManifest.xml),
 * so they can't be launched via `adb shell am start` from outside the app's own
 * process -- SecurityException, confirmed live. ActivityScenario launches within
 * the instrumentation's own process/UID instead, which is the correct way to test
 * an internal-only Activity, and does not require a logged-in session or a running
 * backend: this test only asserts the RN runtime brings the Activity to RESUMED
 * without crashing, not that its data actually loads (a real, separate concern --
 * the mini-app's own bridge calls will correctly fail with "no active session"
 * without a login, exactly as already documented elsewhere in this repo).
 *
 * Extended to all four mini-apps (2026-07-13) now that wallet-balance/reward-tasks/
 * insurance all migrated onto real granite alongside pay-bills -- each one was also
 * separately live-verified end-to-end (real data load, real writes: a reward claim
 * and an insurance enrollment, both confirmed via direct MySQL queries afterward,
 * not just this activity-alive check) against a real running backend, on a real
 * emulator, per this session's own account in docs/TOSS_PARITY_MATRIX.md's Mini-app
 * host row -- this test only covers the New-Architecture-doesn't-crash guarantee,
 * a real but narrower claim than that full manual pass.
 */
@RunWith(AndroidJUnit4::class)
class MiniAppNewArchitectureTest {

    private fun assertActivityAliveUnderNewArchitecture(activityClass: Class<out android.app.Activity>) {
        val scenario = ActivityScenario.launch(activityClass)
        // Give the RN runtime (Fabric/TurboModule init under New Architecture) real
        // wall-clock time to either finish initializing or crash -- not an arbitrary
        // wait, matched to the same order of magnitude used for the on-device manual
        // verification checks elsewhere in this session.
        Thread.sleep(8000)
        scenario.onActivity { activity ->
            assertTrue(
                "Activity should still be alive (not finished/crashed) after RN init",
                !activity.isFinishing && !activity.isDestroyed,
            )
        }
        scenario.close()
    }

    @Test
    fun payBillsMiniAppActivity_initializesUnderNewArchitectureWithoutCrashing() {
        assertActivityAliveUnderNewArchitecture(PayBillsMiniAppActivity::class.java)
    }

    @Test
    fun walletBalanceMiniAppActivity_initializesUnderNewArchitectureWithoutCrashing() {
        assertActivityAliveUnderNewArchitecture(WalletBalanceMiniAppActivity::class.java)
    }

    @Test
    fun rewardTasksMiniAppActivity_initializesUnderNewArchitectureWithoutCrashing() {
        assertActivityAliveUnderNewArchitecture(RewardTasksMiniAppActivity::class.java)
    }

    @Test
    fun insuranceMiniAppActivity_initializesUnderNewArchitectureWithoutCrashing() {
        assertActivityAliveUnderNewArchitecture(InsuranceMiniAppActivity::class.java)
    }
}
