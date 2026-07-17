package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.YearMonth

class DemoCardAuthorizationServiceTest : BehaviorSpec({

    val service = DemoCardAuthorizationService()
    val futureExpiry = YearMonth.now().plusYears(2)

    Given("a card number that fails the real Luhn checksum") {
        When("authorizing") {
            val result = service.authorize("1234567812345678", futureExpiry.monthValue, futureExpiry.year, "123")

            Then("it real-declines as an invalid card") {
                result.status shouldBe CardAuthorizationStatus.DECLINED_INVALID_CARD
            }
        }
    }

    Given("a real Luhn-valid card number") {
        When("the CVC is too short") {
            val result = service.authorize(DemoCardAuthorizationService.TEST_CARD_APPROVE, futureExpiry.monthValue, futureExpiry.year, "12")

            Then("it real-declines as an invalid card") {
                result.status shouldBe CardAuthorizationStatus.DECLINED_INVALID_CARD
            }
        }

        When("the expiry date is in the past") {
            val result = service.authorize(DemoCardAuthorizationService.TEST_CARD_APPROVE, 1, 2020, "123")

            Then("it real-declines as an expired card") {
                result.status shouldBe CardAuthorizationStatus.DECLINED_EXPIRED_CARD
            }
        }
    }

    Given("itunda's own fixed demo test cards, same convention real PSPs like Stripe publish") {
        When("authorizing the approve test card") {
            val result = service.authorize(DemoCardAuthorizationService.TEST_CARD_APPROVE, futureExpiry.monthValue, futureExpiry.year, "123")

            Then("it real-approves") {
                result.status shouldBe CardAuthorizationStatus.APPROVED
                result.last4 shouldBe "4242"
            }
        }

        When("authorizing the generic-decline test card") {
            val result = service.authorize(DemoCardAuthorizationService.TEST_CARD_DECLINE_GENERIC, futureExpiry.monthValue, futureExpiry.year, "123")

            Then("it real-declines generically") {
                result.status shouldBe CardAuthorizationStatus.DECLINED_GENERIC
            }
        }

        When("authorizing the insufficient-funds test card") {
            val result = service.authorize(DemoCardAuthorizationService.TEST_CARD_DECLINE_INSUFFICIENT_FUNDS, futureExpiry.monthValue, futureExpiry.year, "123")

            Then("it real-declines for insufficient funds") {
                result.status shouldBe CardAuthorizationStatus.DECLINED_INSUFFICIENT_FUNDS
            }
        }
    }

    Given("a real Luhn-valid card number outside the fixed demo set") {
        When("authorizing it twice") {
            // A real Luhn-valid card, not one of the three fixed demo numbers.
            val first = service.authorize("5555555555554444", futureExpiry.monthValue, futureExpiry.year, "123")
            val second = service.authorize("5555555555554444", futureExpiry.monthValue, futureExpiry.year, "123")

            Then("it deterministically returns the same simulated outcome both times -- not randomly flaky") {
                first.status shouldBe second.status
            }
        }
    }
})
