package rw.itunda.gift

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.GiftVoucher
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * First test coverage for GiftVoucherExpiryScheduler -- and a real, previously-live
 * bug fix, not just a missing test. `expireVoucher` calls
 * `ledgerService.postLedgerTransaction` (the real partial refund) with no try/catch
 * of its own, and this sweep's `.forEach` had none either -- a single bad voucher
 * would throw uncaught and silently stop refunding for every OTHER real expired
 * voucher in the same tick. Fixed alongside this test (same commit) with a
 * per-voucher try/catch.
 */
class GiftVoucherExpirySchedulerTest : BehaviorSpec({

    fun voucher(id: String) = GiftVoucher(
        id = id, purchaserId = "user_$id", recipientId = "recipient_$id",
        conversationId = "conversation_$id", messageId = "message_$id", merchantId = "merchant_$id",
        amount = BigDecimal("3000"), holdTransactionId = "ledgertxn_$id", expiresAt = Instant.now().minus(1, ChronoUnit.DAYS),
    )

    Given("3 expired unredeemed vouchers, where refunding the middle one fails") {
        val giftVoucherService = mockk<GiftVoucherService>()
        val v1 = voucher("v1")
        val v2 = voucher("v2")
        val v3 = voucher("v3")
        every { giftVoucherService.getExpiredActiveVouchers() } returns listOf(v1, v2, v3)
        every { giftVoucherService.expireVoucher(v1) } returns Unit
        every { giftVoucherService.expireVoucher(v2) } throws RuntimeException("unexpected ledger error")
        every { giftVoucherService.expireVoucher(v3) } returns Unit
        val scheduler = GiftVoucherExpiryScheduler(giftVoucherService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop refunding the rest") {
                verify(exactly = 1) { giftVoucherService.expireVoucher(v1) }
                verify(exactly = 1) { giftVoucherService.expireVoucher(v2) }
                verify(exactly = 1) { giftVoucherService.expireVoucher(v3) }
            }
        }
    }

    Given("no expired vouchers") {
        val giftVoucherService = mockk<GiftVoucherService>()
        every { giftVoucherService.getExpiredActiveVouchers() } returns emptyList()
        val scheduler = GiftVoucherExpiryScheduler(giftVoucherService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is refunded, no exception is thrown") {
                verify(exactly = 0) { giftVoucherService.expireVoucher(any()) }
            }
        }
    }
})
