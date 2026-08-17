package rw.itunda.p2p

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.AutoTransfer
import rw.itunda.core.domain.AutoTransferFrequency
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.repository.AutoTransferRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant

/**
 * Real transaction-boundary regression guard (2026-08-17) -- see
 * AutoTransferService.executeOne's own doc comment for the full account: a third real
 * instance of the same self-invocation/separately-proxied-bean transaction-poisoning
 * pattern Sections 115/118 already closed for BillAutoPayProcessor/
 * ProductSubscriptionService/MerchantBillingService, found live in this file's own
 * `executeOne` (still `@Transactional`, still called `p2pService.sendDirect` -- a
 * separately-proxied bean -- from inside that ambient transaction) and fixed the same
 * way. MockK unit tests can't otherwise observe this bug class at all, since they never
 * create a real Spring AOP proxy -- see the reflection-based guard below.
 */
class AutoTransferServiceTest : BehaviorSpec({

    fun autoTransfer(status: rw.itunda.core.domain.AutoTransferStatus = rw.itunda.core.domain.AutoTransferStatus.ACTIVE) = AutoTransfer(
        id = "autotransfer_1", userId = "user_1", walletId = "wallet_1",
        recipientIdentifier = "+250788000000", recipientName = "Recipient",
        amount = BigDecimal("5000"), frequency = AutoTransferFrequency.WEEKLY, dayOfWeek = 1,
        status = status, nextExecutionAt = Instant.parse("2026-08-17T00:00:00Z"),
    )

    Given("a due weekly auto-transfer whose recipient has insufficient funds available from the sender") {
        val autoTransferRepository = mockk<AutoTransferRepository>()
        val walletRepository = mockk<WalletRepository>()
        val userRepository = mockk<UserRepository>()
        val p2pService = mockk<P2pService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = AutoTransferService(autoTransferRepository, walletRepository, userRepository, p2pService, rateLimiter)

        every { autoTransferRepository.save(any()) } answers { firstArg() }

        When("executeOne runs and sendDirect throws InsufficientFundsException") {
            every { p2pService.sendDirect(any(), any(), any(), any()) } throws InsufficientFundsException("Insufficient balance")

            val transfer = autoTransfer()
            val succeeded = service.executeOne(transfer)

            Then("it returns false, records the real failure reason, and still advances nextExecutionAt -- no exception escapes") {
                succeeded shouldBe false
                transfer.lastFailureReason shouldBe "Insufficient balance"
                transfer.nextExecutionAt shouldBe Instant.parse("2026-08-24T00:00:00Z")
            }
        }

        When("executeOne runs and sendDirect succeeds") {
            every { p2pService.sendDirect(any(), any(), any(), any()) } returns (mockk<rw.itunda.core.domain.Transaction>() to BigDecimal.ZERO)

            val transfer = autoTransfer()
            val succeeded = service.executeOne(transfer)

            Then("it returns true, clears any prior failure reason, and increments executionCount") {
                succeeded shouldBe true
                transfer.lastFailureReason shouldBe null
                transfer.executionCount shouldBe 1
            }
        }
    }

    Given("the transaction-boundary fix for the scheduler's per-row execution loop") {
        Then("executeOne itself must not carry @Transactional -- P2pService.sendDirect is already fully atomic on its own") {
            val method = AutoTransferService::class.java.declaredMethods.first { it.name == "executeOne" }
            method.isAnnotationPresent(Transactional::class.java) shouldBe false
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
