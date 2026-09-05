package rw.itunda.gift.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.GiftVoucher
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.gift.GiftVoucherExpiryReminderScheduler
import rw.itunda.gift.GiftVoucherService

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit):
 * before the fix, `extendExpiry` called GiftVoucherService.extendExpiry directly
 * with no Idempotency-Key protection -- a lost response after a successful extend
 * would resubmit here and hit GiftVoucherAlreadyExtendedException on the retry, a
 * confusing conflict for an extension that actually already succeeded. `redeem`
 * was already protected; this was the outlier. This file exists to make sure that
 * wiring can't silently regress.
 */
class GiftVoucherControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(service: GiftVoucherService, idempotencyService: IdempotencyService) = GiftVoucherController(
        service, idempotencyService, mockk<GiftVoucherExpiryReminderScheduler>(relaxed = true),
    )

    Given("a first-time extend-expiry request") {
        val service = mockk<GiftVoucherService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(service, idempotencyService)

        val voucher = mockk<GiftVoucher>(relaxed = true)
        every { service.extendExpiry("user_1", "voucher_1") } returns voucher

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers/voucher_1/extend", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("extending the voucher") {
            val response = controller.extendExpiry("voucher_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers/voucher_1/extend", "key-1", any(), any())
                }
                verify(exactly = 1) { service.extendExpiry("user_1", "voucher_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("voucher") shouldBe voucher
            }
        }
    }

    Given("a retried extend-expiry request using the same Idempotency-Key as a completed one") {
        val service = mockk<GiftVoucherService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers/voucher_1/extend", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "voucher" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.extendExpiry("voucher_1", "key-1", currentUser)

            Then("the cached response is returned and the voucher is never extended again") {
                response.body?.get("voucher") shouldBe "cached-result"
                verify(exactly = 0) { service.extendExpiry(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
