package rw.itunda.core.reconciliation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class DemoExternalSettlementServiceTest : BehaviorSpec({

    val service = DemoExternalSettlementService()

    Given("zero real itunda successes for a rail/day") {
        When("simulating the external settled count") {
            val result = service.simulateExternalSettledCount("wasac", LocalDate.of(2026, 7, 17), 0L)

            Then("it's real zero, not a fabricated non-zero external record") {
                result shouldBe 0L
            }
        }
    }

    Given("the same rail and day") {
        When("simulating the external settled count twice") {
            val date = LocalDate.of(2026, 7, 17)
            val first = service.simulateExternalSettledCount("mtn_momo", date, 20L)
            val second = service.simulateExternalSettledCount("mtn_momo", date, 20L)

            Then("it's deterministic -- not randomly flaky on repeated calls") {
                first shouldBe second
            }
        }
    }

    Given("a real itunda success count") {
        When("simulating the external settled count") {
            val result = service.simulateExternalSettledCount("airtel_money", LocalDate.of(2026, 1, 1), 15L)

            Then("it never goes negative, even in the -1 discrepancy case") {
                (result >= 0L) shouldBe true
            }
        }
    }
})
