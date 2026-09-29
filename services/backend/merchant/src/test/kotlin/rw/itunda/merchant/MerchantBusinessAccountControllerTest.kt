package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep): before the
 * fix, `openBusinessAccount` called MerchantBusinessAccountService.openBusinessAccount
 * directly with no Idempotency-Key protection -- a lost response after a successful
 * open would resubmit here and hit BusinessAccountAlreadyExistsException on the
 * retry, a confusing conflict for an account that actually already got opened.
 * `move-to-business`/`move-to-personal` were already protected; this create
 * endpoint was the outlier. This file exists to make sure that wiring can't
 * silently regress.
 */
class MerchantBusinessAccountControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time business account open request") {
        val service = mockk<MerchantBusinessAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = MerchantBusinessAccountController(service, idempotencyService)

        val account = mockk<Account>(relaxed = true)
        every { service.openBusinessAccount("user_1") } returns account

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/merchant/business-account", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("opening the account") {
            val response = controller.openBusinessAccount("key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/merchant/business-account", "key-1", any(), any()) }
                verify(exactly = 1) { service.openBusinessAccount("user_1") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("account") shouldBe account
            }
        }
    }

    Given("a retried business account open request using the same Idempotency-Key as a completed one") {
        val service = mockk<MerchantBusinessAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = MerchantBusinessAccountController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/merchant/business-account", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "account" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.openBusinessAccount("key-1", currentUser)

            Then("the cached response is returned and the account is never opened again") {
                response.body?.get("account") shouldBe "cached-result"
                verify(exactly = 0) { service.openBusinessAccount(any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
