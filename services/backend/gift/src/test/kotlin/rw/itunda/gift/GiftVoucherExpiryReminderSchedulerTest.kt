package rw.itunda.gift

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.GiftVoucher
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * First test coverage for GiftVoucherExpiryReminderScheduler -- and a real,
 * previously-live bug fix, not just a missing test. This file's own doc comment
 * claimed to already avoid the "transaction-poisoning pitfall," but that claim
 * addressed self-invocation (bypassing the Spring proxy), a different concern than
 * the one that actually applied: `sendExpiryReminder` had no try/catch of its own,
 * and this loop had none either. Fixed alongside this test (same commit) with a
 * per-voucher try/catch.
 */
class GiftVoucherExpiryReminderSchedulerTest : BehaviorSpec({

    fun voucher(id: String) = GiftVoucher(
        id = id, purchaserId = "user_$id", recipientId = "recipient_$id",
        conversationId = "conversation_$id", messageId = "message_$id", merchantId = "merchant_$id",
        amount = BigDecimal("3000"), holdTransactionId = "ledgertxn_$id", expiresAt = Instant.now().plus(2, ChronoUnit.DAYS),
    )

    Given("3 vouchers due for an expiry reminder, where sending the middle one fails") {
        val giftVoucherService = mockk<GiftVoucherService>()
        val v1 = voucher("v1")
        val v2 = voucher("v2")
        val v3 = voucher("v3")
        every { giftVoucherService.getVouchersDueForExpiryReminder() } returns listOf(v1, v2, v3)
        every { giftVoucherService.sendExpiryReminder("v1") } returns Unit
        every { giftVoucherService.sendExpiryReminder("v2") } throws RuntimeException("messaging service unreachable")
        every { giftVoucherService.sendExpiryReminder("v3") } returns Unit
        val scheduler = GiftVoucherExpiryReminderScheduler(giftVoucherService)

        When("the sweep runs") {
            val processed = scheduler.processDue()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { giftVoucherService.sendExpiryReminder("v1") }
                verify(exactly = 1) { giftVoucherService.sendExpiryReminder("v2") }
                verify(exactly = 1) { giftVoucherService.sendExpiryReminder("v3") }
            }
            Then("the real due count still reflects all 3, regardless of the middle failure") {
                processed shouldBe 3
            }
        }
    }

    Given("no vouchers due for a reminder") {
        val giftVoucherService = mockk<GiftVoucherService>()
        every { giftVoucherService.getVouchersDueForExpiryReminder() } returns emptyList()
        val scheduler = GiftVoucherExpiryReminderScheduler(giftVoucherService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is sent, no exception is thrown") {
                verify(exactly = 0) { giftVoucherService.sendExpiryReminder(any()) }
            }
        }
    }
})
