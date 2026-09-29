package rw.itunda.p2p

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.AutoTransfer
import rw.itunda.core.domain.AutoTransferFrequency
import java.math.BigDecimal
import java.time.Instant

/**
 * Real gap found and fixed 2026-09-05 (concurrency-audit continuation, same class as
 * MerchantBillingScheduler's identical fix): `executeOne`'s FINAL
 * `autoTransferRepository.save(autoTransfer)` call sits outside its own try/catch
 * (which only wraps the real P2P send), and this loop had no per-transfer try/catch
 * of its own either -- a genuine failure there used to propagate uncaught and
 * silently stop executing every OTHER due transfer in the same tick.
 */
class AutoTransferSchedulerTest : BehaviorSpec({

    fun autoTransfer(id: String) = AutoTransfer(
        id = id, userId = "user_$id", accountId = "account_$id", recipientIdentifier = "+250788111222",
        recipientName = "Eric", amount = BigDecimal("1000"), frequency = AutoTransferFrequency.WEEKLY,
        dayOfWeek = 1, nextExecutionAt = Instant.now(),
    )

    Given("3 due auto-transfers, where executing the middle one fails") {
        val autoTransferService = mockk<AutoTransferService>()
        val t1 = autoTransfer("t1")
        val t2 = autoTransfer("t2")
        val t3 = autoTransfer("t3")
        every { autoTransferService.getDueForExecution() } returns listOf(t1, t2, t3)
        every { autoTransferService.executeOne(t1) } returns true
        every { autoTransferService.executeOne(t2) } throws RuntimeException("optimistic lock failure")
        every { autoTransferService.executeOne(t3) } returns true
        val scheduler = AutoTransferScheduler(autoTransferService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop executing the rest") {
                verify(exactly = 1) { autoTransferService.executeOne(t1) }
                verify(exactly = 1) { autoTransferService.executeOne(t2) }
                verify(exactly = 1) { autoTransferService.executeOne(t3) }
            }
        }
    }

    Given("no due auto-transfers") {
        val autoTransferService = mockk<AutoTransferService>()
        every { autoTransferService.getDueForExecution() } returns emptyList()
        val scheduler = AutoTransferScheduler(autoTransferService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is executed, no exception is thrown") {
                verify(exactly = 0) { autoTransferService.executeOne(any()) }
            }
        }
    }
})
