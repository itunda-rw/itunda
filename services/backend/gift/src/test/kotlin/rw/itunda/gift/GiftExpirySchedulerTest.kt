package rw.itunda.gift

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Gift
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for GiftExpiryScheduler -- and a real, previously-live bug fix,
 * not just a missing test. `expireGift` calls `ledgerService.postLedgerTransaction`
 * (the real refund) with no try/catch of its own, and this sweep's `.forEach` had
 * none either -- a single bad gift would throw uncaught and silently stop refunding
 * for every OTHER real expired gift in the same tick. Fixed alongside this test (same
 * commit) with a per-gift try/catch.
 */
class GiftExpirySchedulerTest : BehaviorSpec({

    fun gift(id: String) = Gift(
        id = id, senderId = "sender_$id", recipientId = "recipient_$id", conversationId = "conversation_$id",
        messageId = "message_$id", amount = BigDecimal("5000"), note = null, holdTransactionId = "ledgertxn_$id",
        expiresAt = Instant.now().minusSeconds(3600),
    )

    Given("3 expired unclaimed gifts, where refunding the middle one fails") {
        val giftService = mockk<GiftService>()
        val g1 = gift("g1")
        val g2 = gift("g2")
        val g3 = gift("g3")
        every { giftService.getExpiredPendingGifts() } returns listOf(g1, g2, g3)
        every { giftService.expireGift(g1) } returns Unit
        every { giftService.expireGift(g2) } throws RuntimeException("unexpected ledger error")
        every { giftService.expireGift(g3) } returns Unit
        val scheduler = GiftExpiryScheduler(giftService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop refunding the rest") {
                verify(exactly = 1) { giftService.expireGift(g1) }
                verify(exactly = 1) { giftService.expireGift(g2) }
                verify(exactly = 1) { giftService.expireGift(g3) }
            }
        }
    }

    Given("no expired gifts") {
        val giftService = mockk<GiftService>()
        every { giftService.getExpiredPendingGifts() } returns emptyList()
        val scheduler = GiftExpiryScheduler(giftService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is refunded, no exception is thrown") {
                verify(exactly = 0) { giftService.expireGift(any()) }
            }
        }
    }
})
