package rw.itunda.p2p

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.P2pDelayedTransfer
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for P2pDelayedTransferReleaseScheduler -- real money release
 * (a 지연이체 delayed transfer's held funds). This class's own doc comment names the
 * exact bug class it exists to prevent: "the previously-recurring scheduler
 * transaction-poisoning bug class this codebase's own Sections 115-181 already found
 * and fixed nine times" -- worth verifying directly, not just trusting the comment.
 */
class P2pDelayedTransferReleaseSchedulerTest : BehaviorSpec({

    fun transfer(id: String) = P2pDelayedTransfer(
        id = id, senderUserId = "sender_$id", senderAccountId = "account_sender_$id",
        recipientUserId = "recipient_$id", recipientAccountId = "account_recipient_$id", amount = BigDecimal("2000"),
        description = "Rent", holdTransactionId = "ledgertxn_$id", releaseAt = Instant.now(),
    )

    Given("3 delayed transfers due for release, where the middle one's recipient account was since deleted") {
        val p2pDelayedTransferService = mockk<P2pDelayedTransferService>()
        every { p2pDelayedTransferService.getDueForRelease() } returns listOf(transfer("t1"), transfer("t2"), transfer("t3"))
        every { p2pDelayedTransferService.release("t1") } returns Unit
        every { p2pDelayedTransferService.release("t2") } throws RuntimeException("recipient account no longer exists")
        every { p2pDelayedTransferService.release("t3") } returns Unit
        val scheduler = P2pDelayedTransferReleaseScheduler(p2pDelayedTransferService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still released -- the middle failure doesn't poison the sweep for the others") {
                verify(exactly = 1) { p2pDelayedTransferService.release("t1") }
                verify(exactly = 1) { p2pDelayedTransferService.release("t2") }
                verify(exactly = 1) { p2pDelayedTransferService.release("t3") }
            }
        }
    }

    Given("no delayed transfers due") {
        val p2pDelayedTransferService = mockk<P2pDelayedTransferService>()
        every { p2pDelayedTransferService.getDueForRelease() } returns emptyList()
        val scheduler = P2pDelayedTransferReleaseScheduler(p2pDelayedTransferService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is released, no exception is thrown") {
                verify(exactly = 0) { p2pDelayedTransferService.release(any()) }
            }
        }
    }
})
