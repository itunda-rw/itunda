package rw.itunda.system

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.FraudFlag
import rw.itunda.core.domain.FraudFlagDecision
import rw.itunda.core.domain.FraudRule
import rw.itunda.core.repository.FraudFlagRepository
import java.math.BigDecimal
import java.util.Optional

class FraudReviewServiceTest : BehaviorSpec({

    Given("a real unreviewed fraud flag") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val service = FraudReviewService(fraudFlagRepository)

        val flag = FraudFlag(id = "flag_1", userId = "user_1", transactionId = "txn_1", rule = FraudRule.HIGH_VALUE, description = "test", amount = BigDecimal("150000"))
        every { fraudFlagRepository.findById("flag_1") } returns Optional.of(flag)
        every { fraudFlagRepository.save(any()) } answers { firstArg() }

        When("an admin clears it") {
            val decided = service.decide("flag_1", "admin_1", FraudFlagDecision.CLEARED)

            Then("it's marked reviewed with the real reviewer and decision recorded") {
                decided.reviewed shouldBe true
                decided.decision shouldBe FraudFlagDecision.CLEARED
                decided.reviewedBy shouldBe "admin_1"
            }
        }
    }

    Given("a flag that's already been reviewed") {
        val fraudFlagRepository = mockk<FraudFlagRepository>()
        val service = FraudReviewService(fraudFlagRepository)

        val flag = FraudFlag(id = "flag_2", userId = "user_2", transactionId = "txn_2", rule = FraudRule.VELOCITY, description = "test", amount = BigDecimal("100"), reviewed = true)
        every { fraudFlagRepository.findById("flag_2") } returns Optional.of(flag)

        When("trying to decide it again") {
            Then("it throws FraudFlagAlreadyReviewedException rather than silently re-deciding") {
                try {
                    service.decide("flag_2", "admin_1", FraudFlagDecision.CONFIRMED)
                    error("expected FraudFlagAlreadyReviewedException")
                } catch (e: FraudFlagAlreadyReviewedException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
