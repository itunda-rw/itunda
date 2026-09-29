package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.Merchant
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep): before the
 * fix, `register` called MerchantService.register directly with no Idempotency-Key
 * protection -- a lost response after a successful register would resubmit here and
 * hit MerchantAlreadyRegisteredException on the retry, a confusing conflict for a
 * registration that actually already succeeded. This file exists to make sure that
 * wiring can't silently regress.
 */
class MerchantControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(merchantService: MerchantService, idempotencyService: IdempotencyService) = MerchantController(
        merchantService,
        mockk(relaxed = true),
        idempotencyService,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
    )

    Given("a first-time merchant registration request") {
        val merchantService = mockk<MerchantService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(merchantService, idempotencyService)

        val merchant = mockk<Merchant>(relaxed = true)
        every { merchantService.register("user_1", "Kigali Fresh Mart") } returns merchant

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/merchant/register", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("registering") {
            val response = controller.register(RegisterMerchantRequest("Kigali Fresh Mart"), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/merchant/register", "key-1", any(), any()) }
                verify(exactly = 1) { merchantService.register("user_1", "Kigali Fresh Mart") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("merchant") shouldBe merchant
            }
        }
    }

    Given("a retried merchant registration request using the same Idempotency-Key as a completed one") {
        val merchantService = mockk<MerchantService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(merchantService, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/merchant/register", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "merchant" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.register(RegisterMerchantRequest("Kigali Fresh Mart"), "key-1", currentUser)

            Then("the cached response is returned and the account is never registered again") {
                response.body?.get("merchant") shouldBe "cached-result"
                verify(exactly = 0) { merchantService.register(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
