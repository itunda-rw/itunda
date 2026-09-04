package rw.itunda.account

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.AccountAutoTopUpSetting
import java.math.BigDecimal

/**
 * First test coverage for AutoTopUpScheduler -- the real resilience property this
 * class's own doc comment describes ("one bad row never blocks the sweep for every
 * other enabled account") had never been directly exercised. Real, easy regression:
 * moving the per-setting try/catch outside the loop, or `return`ing instead of
 * `continue`-by-exception on a failure, would silently stop processing every account
 * after the first bad one -- exactly what this test proves doesn't happen.
 */
class AutoTopUpSchedulerTest : BehaviorSpec({

    fun setting(id: String, accountId: String) = AccountAutoTopUpSetting(
        id = id, userId = "user_$accountId", accountId = accountId, linkedAccountId = "linked_$accountId",
        thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 3,
    )

    Given("3 enabled auto-top-up settings, where the middle one throws") {
        val autoTopUpService = mockk<AutoTopUpService>()
        every { autoTopUpService.getEnabledSettings() } returns listOf(
            setting("s1", "account_1"), setting("s2", "account_2"), setting("s3", "account_3"),
        )
        every { autoTopUpService.evaluateAndTopUp("user_account_1", "account_1") } returns AutoTopUpTriggerResult(true, "threshold crossed")
        every { autoTopUpService.evaluateAndTopUp("user_account_2", "account_2") } throws RuntimeException("provider unreachable")
        every { autoTopUpService.evaluateAndTopUp("user_account_3", "account_3") } returns AutoTopUpTriggerResult(false, "above threshold")
        val scheduler = AutoTopUpScheduler(autoTopUpService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop the sweep") {
                verify(exactly = 1) { autoTopUpService.evaluateAndTopUp("user_account_1", "account_1") }
                verify(exactly = 1) { autoTopUpService.evaluateAndTopUp("user_account_2", "account_2") }
                verify(exactly = 1) { autoTopUpService.evaluateAndTopUp("user_account_3", "account_3") }
            }
        }
    }

    Given("no enabled settings at all") {
        val autoTopUpService = mockk<AutoTopUpService>()
        every { autoTopUpService.getEnabledSettings() } returns emptyList()
        val scheduler = AutoTopUpScheduler(autoTopUpService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is evaluated, no exception is thrown") {
                verify(exactly = 0) { autoTopUpService.evaluateAndTopUp(any(), any()) }
            }
        }
    }
})
