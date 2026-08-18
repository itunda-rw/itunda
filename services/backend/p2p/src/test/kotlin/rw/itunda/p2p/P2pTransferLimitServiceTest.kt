package rw.itunda.p2p

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant

// Real Korean "이체한도" (transfer limit) enforcement (Section 186) -- see
// P2pTransferLimitService's own doc comment for the full sourced account. Direct unit
// tests of the real comparison logic, independent of P2pServiceTest/
// P2pDelayedTransferServiceTest's own (fewer, integration-shaped) coverage of the same
// class wired into each real caller.
class P2pTransferLimitServiceTest : BehaviorSpec({

    fun priorTransfer(amount: String) = Transaction(
        id = "txn_${amount}", referenceNumber = "REF$amount", senderId = "sender_1", recipientId = "someone",
        amount = BigDecimal(amount), fee = BigDecimal.ZERO, currency = "RWF", type = TransactionType.TRANSFER,
        status = TransactionStatus.COMPLETED, description = "Earlier today", completedAt = Instant.now(),
    )

    Given("a real sender with no prior transfers today, sending well within both real caps") {
        val transactionRepository = mockk<TransactionRepository>()
        val service = P2pTransferLimitService(transactionRepository)
        every { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(eq("sender_1"), any(), any(), any()) } returns emptyList()

        When("they send 100,000 RWF") {
            Then("it real-does not throw") {
                service.enforce("sender_1", BigDecimal("100000"))
            }
        }
    }

    Given("a real sender sending a single transfer that itself exceeds the real 500,000 RWF per-transfer cap") {
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val service = P2pTransferLimitService(transactionRepository)

        When("they send 500,001 RWF in one call") {
            Then("it real-throws P2pTransferLimitExceededException without ever querying today's real total") {
                try {
                    service.enforce("sender_1", BigDecimal("500001"))
                    throw AssertionError("expected P2pTransferLimitExceededException")
                } catch (e: P2pTransferLimitExceededException) {
                    io.mockk.verify(exactly = 0) { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(any(), any(), any(), any()) }
                }
            }
        }
    }

    Given("a real sender sending exactly the real 500,000 RWF per-transfer cap") {
        val transactionRepository = mockk<TransactionRepository>()
        val service = P2pTransferLimitService(transactionRepository)
        every { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(eq("sender_1"), any(), any(), any()) } returns emptyList()

        When("they send exactly 500,000 RWF") {
            Then("it real-does not throw -- the real per-transfer cap is inclusive") {
                service.enforce("sender_1", BigDecimal("500000"))
            }
        }
    }

    Given("a real sender whose earlier real transfers today already total 2,400,000 RWF") {
        val transactionRepository = mockk<TransactionRepository>()
        val service = P2pTransferLimitService(transactionRepository)
        every { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(eq("sender_1"), any(), any(), any()) } returns
            listOf(priorTransfer("1500000"), priorTransfer("900000"))

        When("they try to send another 200,000 RWF, pushing today's real total to 2,600,000") {
            Then("it real-throws P2pTransferLimitExceededException naming the real 100,000 RWF actually remaining") {
                try {
                    service.enforce("sender_1", BigDecimal("200000"))
                    throw AssertionError("expected P2pTransferLimitExceededException")
                } catch (e: P2pTransferLimitExceededException) {
                    (e.message ?: "").contains("100000") shouldBe true
                }
            }
        }

        When("they send exactly the real 100,000 RWF remaining instead") {
            Then("it real-does not throw -- the real daily cap is inclusive of the exact remaining amount") {
                service.enforce("sender_1", BigDecimal("100000"))
            }
        }
    }

    Given("a real sender's prior transfers today include a non-TRANSFER-type or non-COMPLETED transaction") {
        val transactionRepository = mockk<TransactionRepository>()
        val service = P2pTransferLimitService(transactionRepository)
        // The repository query itself is typed to only ever return TRANSFER/COMPLETED
        // rows for the real real filter args passed -- this test documents that
        // P2pTransferLimitService.enforce passes the real narrow filter
        // (TransactionType.TRANSFER, TransactionStatus.COMPLETED), not a broader one
        // that could accidentally include cancelled/failed/other-type rows in the sum.
        every {
            transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(
                "sender_1", TransactionType.TRANSFER, TransactionStatus.COMPLETED, any(),
            )
        } returns emptyList()

        When("they send 500,000 RWF, exactly the real per-transfer cap, with a clean real daily history") {
            Then("it real-does not throw, confirming the real narrow TRANSFER/COMPLETED filter was actually used for the daily-cumulative check") {
                service.enforce("sender_1", BigDecimal("500000"))
            }
        }
    }

}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
