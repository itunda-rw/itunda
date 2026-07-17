package rw.itunda.identity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DemoKybVerificationServiceTest : BehaviorSpec({

    val service = DemoKybVerificationService()

    Given("a TIN that isn't exactly 9 digits") {
        When("verifying a 5-digit number") {
            val result = service.verify("12345")

            Then("it real-fails format validation") {
                result.status shouldBe KybVerificationStatus.INVALID_FORMAT
            }
        }
        When("verifying a 9-character string with letters") {
            val result = service.verify("12345678A")

            Then("it real-fails format validation") {
                result.status shouldBe KybVerificationStatus.INVALID_FORMAT
            }
        }
    }

    Given("a structurally valid 9-digit TIN") {
        When("verifying it twice") {
            val first = service.verify("123456789")
            val second = service.verify("123456789")

            Then("it deterministically returns the same simulated outcome both times -- not randomly flaky") {
                first.status shouldBe second.status
            }
        }

        When("verifying a different structurally valid TIN") {
            val result = service.verify("987654321")

            Then("it real-passes format validation, whichever simulated outcome it lands on") {
                (result.status == KybVerificationStatus.MATCHED || result.status == KybVerificationStatus.NOT_FOUND) shouldBe true
            }
        }
    }
})
