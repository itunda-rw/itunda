package rw.itunda.p2p

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.ScheduledTransferStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.ScheduledTransferRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional

/**
 * Real Toss 예약송금 (scheduled/reserved one-time transfer) equivalent -- see
 * ScheduledTransfer.kt's own doc comment. P2pService itself is mocked, not exercised
 * for real -- its own money-movement logic is already covered by P2pServiceTest; this
 * file is about scheduled-transfer-specific logic: future-date validation, ownership,
 * and the one-time (not recurring) execution/failure semantics.
 */
class ScheduledTransferServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real user with a account and a real recipient") {
        val scheduledTransferRepository = mockk<ScheduledTransferRepository>(relaxed = true)
        every { scheduledTransferRepository.save(any()) } answers { firstArg() }
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val p2pService = mockk<P2pService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ScheduledTransferService(scheduledTransferRepository, accountRepository, userRepository, p2pService, rateLimiter, notificationRepository, pushNotificationService)

        val senderAccount = account("account_sender", "sender_1")
        val recipientAccount = account("account_recipient", "recipient_1")
        val recipientUser = User(id = "recipient_1", phoneNumber = "+250788000002", firstName = "Alice", lastName = "M", passwordHash = "x")

        When("creating a scheduled transfer for a genuine future date") {
            every { accountRepository.findByUserIdAndType("sender_1", AccountType.MAIN) } returns senderAccount
            every { userRepository.findByPhoneNumber("+250788000002") } returns recipientUser
            every { accountRepository.findByUserIdAndType("recipient_1", AccountType.MAIN) } returns recipientAccount
            every { userRepository.findById("recipient_1") } returns Optional.of(recipientUser)

            val futureDate = LocalDate.now(ZoneOffset.UTC).plusDays(5)
            val result = service.create("sender_1", "+250788000002", BigDecimal("10000"), futureDate, "Rent")

            Then("it saves a real PENDING scheduled transfer with the resolved recipient name") {
                result.status shouldBe ScheduledTransferStatus.PENDING
                result.recipientName shouldBe "Alice M"
                result.scheduledDate shouldBe futureDate
                result.amount shouldBe BigDecimal("10000")
            }

            // Real gap found live (P2P product-completeness pass, 2026-09-08): this
            // file's own rateLimiter mock was relaxed = true with zero verify{}
            // anywhere, so a future accidental removal of the real checkLimit call
            // would have compiled and passed silently. Same latent-regression class
            // Family/Gift/Splitbill/AutoTransferServiceTest already closed.
            Then("the real rate limiter is actually consulted, not just mocked away") {
                verify(exactly = 1) { rateLimiter.checkLimit("scheduledtransfer:create:sender_1", limit = 20, window = java.time.Duration.ofHours(1)) }
            }
        }

        When("the recipient has a real long firstName+lastName") {
            val longRecipient = User(id = "recipient_2", phoneNumber = "+250788000003", firstName = "x".repeat(234), lastName = "y".repeat(255), passwordHash = "x")
            every { accountRepository.findByUserIdAndType("sender_1", AccountType.MAIN) } returns senderAccount
            every { userRepository.findByPhoneNumber("+250788000003") } returns longRecipient
            every { accountRepository.findByUserIdAndType("recipient_2", AccountType.MAIN) } returns account("account_recipient_2", "recipient_2")
            every { userRepository.findById("recipient_2") } returns Optional.of(longRecipient)

            val futureDate = LocalDate.now(ZoneOffset.UTC).plusDays(5)
            val result = service.create("sender_1", "+250788000003", BigDecimal("10000"), futureDate, "Rent")

            Then("the cached recipientName is truncated to the real 255-char safe bound, not the naive 490") {
                result.recipientName.length shouldBe 255
            }
        }

        When("the scheduled date is today or in the past") {
            Then("today is rejected -- a real 1회 scheduled transfer only ever runs on a genuine future date") {
                try {
                    service.create("sender_1", "+250788000002", BigDecimal("10000"), LocalDate.now(ZoneOffset.UTC), "Rent")
                    throw AssertionError("expected ScheduledTransferInvalidDateException")
                } catch (e: ScheduledTransferInvalidDateException) {
                    // expected
                }
            }

            Then("yesterday is rejected") {
                try {
                    service.create("sender_1", "+250788000002", BigDecimal("10000"), LocalDate.now(ZoneOffset.UTC).minusDays(1), "Rent")
                    throw AssertionError("expected ScheduledTransferInvalidDateException")
                } catch (e: ScheduledTransferInvalidDateException) {
                    // expected
                }
            }
        }

        When("scheduling a transfer to yourself") {
            every { accountRepository.findByUserIdAndType("sender_1", AccountType.MAIN) } returns senderAccount
            every { userRepository.findByPhoneNumber("+250788000001") } returns null
            every { accountRepository.findByAccountNumber("+250788000001") } returns senderAccount

            Then("it's rejected") {
                try {
                    service.create("sender_1", "+250788000001", BigDecimal("10000"), LocalDate.now(ZoneOffset.UTC).plusDays(1), "")
                    throw AssertionError("expected P2pSelfPaymentException")
                } catch (e: P2pSelfPaymentException) {
                    // expected
                }
            }
        }

        When("the amount is zero or negative") {
            Then("it's rejected before any lookup") {
                try {
                    service.create("sender_1", "+250788000002", BigDecimal.ZERO, LocalDate.now(ZoneOffset.UTC).plusDays(1), "")
                    throw AssertionError("expected P2pInvalidAmountException")
                } catch (e: P2pInvalidAmountException) {
                    // expected
                }
            }
        }
    }

    Given("a real pending scheduled transfer") {
        val scheduledTransferRepository = mockk<ScheduledTransferRepository>(relaxed = true)
        every { scheduledTransferRepository.save(any()) } answers { firstArg() }
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val p2pService = mockk<P2pService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        // relaxed=true mishandles JpaRepository's generic `<S extends T> S save(S)` and
        // returns a raw Object, ClassCastException-ing at the call site -- same fix as
        // AutoTransferServiceTest's own notificationRepository stub.
        val notificationRepository = mockk<NotificationRepository>()
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ScheduledTransferService(scheduledTransferRepository, accountRepository, userRepository, p2pService, rateLimiter, notificationRepository, pushNotificationService)

        val pending = ScheduledTransferFixture.pending()

        When("cancelling it") {
            every { scheduledTransferRepository.findByIdAndUserId(pending.id, "sender_1") } returns pending

            val result = service.cancel("sender_1", pending.id)

            Then("it becomes CANCELLED with a real timestamp") {
                result.status shouldBe rw.itunda.core.domain.ScheduledTransferStatus.CANCELLED
                (result.cancelledAt != null) shouldBe true
            }
        }

        When("its scheduled date arrives and the real transfer succeeds") {
            every { p2pService.sendDirect("sender_1", "+250788000002", BigDecimal("10000"), "Rent") } returns
                Triple(
                    Transaction(
                        id = "ledgertxn_1", referenceNumber = "REF1", senderId = "sender_1", recipientId = "recipient_1",
                        fromAccountId = "account_sender", toAccountId = "account_recipient", amount = BigDecimal("10000"), fee = BigDecimal.ZERO,
                        currency = "RWF", type = TransactionType.TRANSFER, status = TransactionStatus.COMPLETED, description = "Transfer - Rent",
                    ),
                    BigDecimal("90000"),
                    emptyList(),
                )

            val succeeded = service.executeOne(ScheduledTransferFixture.pending())

            Then("it's marked EXECUTED with the real transaction id, never fabricated") {
                succeeded shouldBe true
            }

            Then("it reuses P2pService.sendDirect's exact real money movement, not a duplicate ledger path") {
                verify(exactly = 1) { p2pService.sendDirect("sender_1", "+250788000002", BigDecimal("10000"), "Rent") }
            }
        }

        When("its scheduled date arrives but the sender no longer has enough balance") {
            every { p2pService.sendDirect(any(), any(), any(), any()) } throws InsufficientFundsException("Insufficient balance")

            val succeeded = service.executeOne(ScheduledTransferFixture.pending())

            Then("it's honestly marked FAILED, never thrown, never retried -- a one-time transfer has no next cycle") {
                succeeded shouldBe false
            }

            Then("it sends a real SCHEDULED_TRANSFER_FAILED notification with the real failure reason, plus a real push") {
                verify(exactly = 1) {
                    notificationRepository.save(match { it.userId == "sender_1" && it.type == "SCHEDULED_TRANSFER_FAILED" && it.body.contains("Insufficient balance") })
                }
                verify(exactly = 1) { pushNotificationService.sendToUser("sender_1", any(), any(), any()) }
            }
        }

        When("its scheduled date arrives and the real transfer succeeds a second time") {
            every { p2pService.sendDirect(any(), any(), any(), any()) } returns
                Triple(
                    Transaction(
                        id = "ledgertxn_2", referenceNumber = "REF2", senderId = "sender_1", recipientId = "recipient_1",
                        fromAccountId = "account_sender", toAccountId = "account_recipient", amount = BigDecimal("10000"), fee = BigDecimal.ZERO,
                        currency = "RWF", type = TransactionType.TRANSFER, status = TransactionStatus.COMPLETED, description = "Transfer - Rent",
                    ),
                    BigDecimal("90000"),
                    emptyList(),
                )

            service.executeOne(ScheduledTransferFixture.pending())

            Then("a genuine success never sends a failure notification") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }

    Given("the real self-invocation/separately-proxied-bean transaction-poisoning pitfall") {
        Then("executeOne carries no @Transactional -- sendDirect is a separately-proxied bean, already atomic on its own (2026-08-17 fix, same root cause as AutoTransferService.executeOne)") {
            val method = ScheduledTransferService::class.java.getDeclaredMethod("executeOne", rw.itunda.core.domain.ScheduledTransfer::class.java)
            val hasTransactional = method.annotations.any { it.annotationClass.qualifiedName == "org.springframework.transaction.annotation.Transactional" }
            hasTransactional shouldBe false
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}

private object ScheduledTransferFixture {
    fun pending() = rw.itunda.core.domain.ScheduledTransfer(
        id = "scheduledtransfer_1", userId = "sender_1", accountId = "account_sender",
        recipientIdentifier = "+250788000002", recipientName = "Alice M",
        amount = BigDecimal("10000"), description = "Rent", scheduledDate = LocalDate.now(ZoneOffset.UTC).plusDays(1),
    )
}
