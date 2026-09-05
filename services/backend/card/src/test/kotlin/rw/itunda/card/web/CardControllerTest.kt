package rw.itunda.card.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.card.CardService
import rw.itunda.card.CardView
import rw.itunda.core.domain.DebitCard
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep, same class as
 * StudentLoanController.apply's identical fix): before the fix, `issue` called
 * CardService.issueCard directly with no Idempotency-Key protection -- a lost
 * response after a successful issue would resubmit here and hit
 * CardAlreadyIssuedException on the retry, a confusing conflict for an issuance
 * that actually already succeeded. `charge` was already protected; `issue` was the
 * outlier. This file exists to make sure that wiring can't silently regress.
 */
class CardControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time card issuance request") {
        val cardService = mockk<CardService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = CardController(cardService, idempotencyService)

        val card = mockk<DebitCard>(relaxed = true)
        every { card.id } returns "card_1"
        val cardView = mockk<CardView>(relaxed = true)
        every { cardService.issueCard("user_1", "DEFAULT") } returns card
        every { cardService.getMyCard("user_1") } returns cardView

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/card/issue", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("issuing the card") {
            val response = controller.issue(IssueCardRequest("DEFAULT"), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/card/issue", "key-1", any(), any()) }
                verify(exactly = 1) { cardService.issueCard("user_1", "DEFAULT") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("cardId") shouldBe "card_1"
            }
        }
    }

    Given("a retried card issuance request using the same Idempotency-Key as a completed one") {
        val cardService = mockk<CardService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = CardController(cardService, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/card/issue", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "cardId" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.issue(IssueCardRequest("DEFAULT"), "key-1", currentUser)

            Then("the cached response is returned and a second card is never issued") {
                response.body?.get("cardId") shouldBe "cached-result"
                verify(exactly = 0) { cardService.issueCard(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
