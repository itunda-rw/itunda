package rw.itunda.p2p

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.AutoTransfer
import rw.itunda.core.domain.AutoTransferFrequency
import rw.itunda.core.domain.User
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AutoTransferRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
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
        id = "autotransfer_1", userId = "user_1", accountId = "account_1",
        recipientIdentifier = "+250788000000", recipientName = "Recipient",
        amount = BigDecimal("5000"), frequency = AutoTransferFrequency.WEEKLY, dayOfWeek = 1,
        status = status, nextExecutionAt = Instant.parse("2026-08-17T00:00:00Z"),
    )

    Given("a due weekly auto-transfer whose recipient has insufficient funds available from the sender") {
        val autoTransferRepository = mockk<AutoTransferRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val p2pService = mockk<P2pService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = AutoTransferService(autoTransferRepository, accountRepository, userRepository, p2pService, rateLimiter, notificationRepository, pushNotificationService)

        every { autoTransferRepository.save(any()) } answers { firstArg() }
        // relaxed=true mishandles JpaRepository's generic `<S extends T> S save(S)` and
        // returns a raw Object, ClassCastException-ing at the call site -- same fix as
        // RewardsServiceTest's rewardClaimRepository.save stub.
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("executeOne runs and sendDirect throws InsufficientFundsException") {
            every { p2pService.sendDirect(any(), any(), any(), any()) } throws InsufficientFundsException("Insufficient balance")

            val transfer = autoTransfer()
            val succeeded = service.executeOne(transfer)

            Then("it returns false, records the real failure reason, still advances nextExecutionAt, and sends a real failure notification") {
                succeeded shouldBe false
                transfer.lastFailureReason shouldBe "Insufficient balance"
                transfer.nextExecutionAt shouldBe Instant.parse("2026-08-24T00:00:00Z")
                verify(exactly = 1) {
                    notificationRepository.save(match { it.userId == "user_1" && it.type == "AUTO_TRANSFER_FAILED" })
                }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any()) }
            }
        }

        When("executeOne runs and sendDirect succeeds") {
            every { p2pService.sendDirect(any(), any(), any(), any()) } returns Triple(mockk<rw.itunda.core.domain.Transaction>(), BigDecimal.ZERO, emptyList())

            val transfer = autoTransfer()
            val succeeded = service.executeOne(transfer)

            Then("it returns true, clears any prior failure reason, increments executionCount, and sends no failure notification") {
                succeeded shouldBe true
                transfer.lastFailureReason shouldBe null
                transfer.executionCount shouldBe 1
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }

    Given("creating an auto-transfer whose recipient has a real long firstName+lastName") {
        val autoTransferRepository = mockk<AutoTransferRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val p2pService = mockk<P2pService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = AutoTransferService(autoTransferRepository, accountRepository, userRepository, p2pService, rateLimiter, notificationRepository, pushNotificationService)

        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns
            Account(id = "account_1", userId = "user_1", accountNumber = "1", accountName = "x", type = AccountType.MAIN, balance = BigDecimal("500000"), availableBalance = BigDecimal("500000"))
        every { userRepository.findByPhoneNumber("+250788555666") } returns null
        every { accountRepository.findByAccountNumber("+250788555666") } returns
            Account(id = "account_recipient", userId = "recipient_1", accountNumber = "+250788555666", accountName = "y", type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO)
        every { userRepository.findById("recipient_1") } returns
            java.util.Optional.of(User(id = "recipient_1", phoneNumber = "+250788999888", firstName = "x".repeat(234), lastName = "y".repeat(255), passwordHash = "hash"))
        every { autoTransferRepository.save(any()) } answers { firstArg() }

        When("create runs") {
            val created = service.create("user_1", "+250788555666", BigDecimal("5000"), AutoTransferFrequency.WEEKLY, 1, null, "")

            Then("the cached recipientName is truncated to the real 255-char safe bound, not the naive 490") {
                created.recipientName.length shouldBe 255
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
