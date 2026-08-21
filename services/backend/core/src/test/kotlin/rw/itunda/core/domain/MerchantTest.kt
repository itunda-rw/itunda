package rw.itunda.core.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.ZoneId

class MerchantTest : BehaviorSpec({
    // Real current Rwanda weekday, computed the exact same way Merchant.isClosedToday's
    // own real implementation does -- this codebase has no Clock abstraction anywhere
    // (confirmed: every other date-dependent check, e.g. MiniAccountServiceTest's own
    // real age check, uses real current time directly rather than injecting a fake
    // clock), so this is the honest way to test a real "is today closed" check without
    // fabricating a scenario that could silently drift from the real implementation.
    val todayWeekday = LocalDate.now(ZoneId.of("Africa/Kigali")).dayOfWeek.value
    fun merchant(closedWeekdays: String?) = Merchant(
        id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1",
        businessName = "Test Diner", closedWeekdays = closedWeekdays,
    )

    Given("a merchant whose real closed-weekday schedule includes today") {
        When("checking isClosedToday") {
            val result = merchant("$todayWeekday").isClosedToday()

            Then("it's real-closed today") {
                result shouldBe true
            }
        }
    }

    Given("a merchant whose real closed-weekday schedule does NOT include today, mixed with other real days") {
        val otherDays = (1..7).filter { it != todayWeekday }
        When("checking isClosedToday") {
            val result = merchant(otherDays.joinToString(",")).isClosedToday()

            Then("it's real-open today") {
                result shouldBe false
            }
        }
    }

    Given("a merchant with no closed-weekday schedule set at all") {
        When("checking isClosedToday") {
            val result = merchant(null).isClosedToday()

            Then("it's real-open every day, the honest default for every pre-existing merchant") {
                result shouldBe false
            }
        }
    }
})
