package rw.itunda.account

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Real transaction-history pattern detection has no injected clock to fake, but unlike
 * a live-timer feature (round-up, no-show forfeiture), this is a pure read-only
 * computation over already-stored `createdAt` timestamps -- constructing `Transaction`
 * rows with real, controlled timestamps spanning months is the correct way to verify
 * the actual weekly/monthly cadence classification, not a multi-week wall-clock wait.
 */
class SubscriptionDetectionServiceTest : BehaviorSpec({

    fun txn(recipientId: String, amount: BigDecimal, daysAgo: Long, type: TransactionType = TransactionType.PAYMENT, description: String = "QR payment"): Transaction =
        Transaction(
            id = "txn_${UUID.randomUUID()}", referenceNumber = "REF${UUID.randomUUID()}",
            senderId = "user_1", recipientId = recipientId, amount = amount, fee = BigDecimal.ZERO,
            currency = "RWF", type = type, status = TransactionStatus.COMPLETED, description = description,
            createdAt = Instant.now().minus(daysAgo, ChronoUnit.DAYS),
        )

    fun merchant(ownerUserId: String, name: String) = Merchant(
        id = "merchant_$ownerUserId", ownerUserId = ownerUserId, accountId = "account_$ownerUserId",
        businessName = name, status = MerchantStatus.ACTIVE,
    )

    Given("a user with 3 monthly-cadence payments to the same merchant, same amount") {
        val transactionRepository = mockk<TransactionRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = SubscriptionDetectionService(transactionRepository, merchantRepository)

        val transactions = listOf(
            txn("merchant_owner_1", BigDecimal("5000"), daysAgo = 60),
            txn("merchant_owner_1", BigDecimal("5000"), daysAgo = 30),
            txn("merchant_owner_1", BigDecimal("5000"), daysAgo = 0),
        )
        every { transactionRepository.findBySenderIdAndTypeInAndStatusOrderByCreatedAtAsc(any(), any(), any()) } returns transactions
        every { merchantRepository.findByOwnerUserId("merchant_owner_1") } returns merchant("merchant_owner_1", "Netflix Rwanda")

        When("detecting subscriptions") {
            val result = service.detectSubscriptions("user_1")

            Then("it real-flags exactly one MONTHLY subscription with the merchant's real name") {
                result.subscriptions.size shouldBe 1
                result.subscriptions[0].displayName shouldBe "Netflix Rwanda"
                result.subscriptions[0].cadence shouldBe SubscriptionCadence.MONTHLY
                result.subscriptions[0].occurrenceCount shouldBe 3
                result.subscriptions[0].monthlyEquivalent shouldBe BigDecimal("5000")
                result.estimatedMonthlyTotal shouldBe BigDecimal("5000")
            }
            Then("it correctly reports no real price change -- the last two real charges match") {
                result.subscriptions[0].priceIncreased shouldBe false
                result.subscriptions[0].previousAmount shouldBe null
            }
        }
    }

    Given("a user with 3 weekly-cadence bill payments, same amount") {
        val transactionRepository = mockk<TransactionRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = SubscriptionDetectionService(transactionRepository, merchantRepository)

        val transactions = listOf(
            txn("rail_suspense", BigDecimal("1000"), daysAgo = 14, type = TransactionType.BILL, description = "Bill payment WASAC"),
            txn("rail_suspense", BigDecimal("1000"), daysAgo = 7, type = TransactionType.BILL, description = "Bill payment WASAC"),
            txn("rail_suspense", BigDecimal("1000"), daysAgo = 0, type = TransactionType.BILL, description = "Bill payment WASAC"),
        )
        every { transactionRepository.findBySenderIdAndTypeInAndStatusOrderByCreatedAtAsc(any(), any(), any()) } returns transactions

        When("detecting subscriptions") {
            val result = service.detectSubscriptions("user_1")

            Then("it real-flags exactly one WEEKLY subscription, monthly-equivalent scaled by real weeks-per-month") {
                result.subscriptions.size shouldBe 1
                result.subscriptions[0].cadence shouldBe SubscriptionCadence.WEEKLY
                result.subscriptions[0].displayName shouldBe "Bill payment WASAC"
                // 1000 * 4.345 = 4345.00
                result.subscriptions[0].monthlyEquivalent shouldBe BigDecimal("4345.00")
            }
        }
    }

    Given("a single one-off payment (only 1 occurrence)") {
        val transactionRepository = mockk<TransactionRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = SubscriptionDetectionService(transactionRepository, merchantRepository)

        every { transactionRepository.findBySenderIdAndTypeInAndStatusOrderByCreatedAtAsc(any(), any(), any()) } returns
            listOf(txn("merchant_owner_2", BigDecimal("2000"), daysAgo = 0))

        When("detecting subscriptions") {
            val result = service.detectSubscriptions("user_1")

            Then("nothing is flagged -- a real subscription needs at least 2 occurrences") {
                result.subscriptions.size shouldBe 0
                result.estimatedMonthlyTotal shouldBe BigDecimal.ZERO
            }
        }
    }

    Given("two payments to the same merchant, same amount, but only 2 real days apart") {
        val transactionRepository = mockk<TransactionRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = SubscriptionDetectionService(transactionRepository, merchantRepository)

        every { transactionRepository.findBySenderIdAndTypeInAndStatusOrderByCreatedAtAsc(any(), any(), any()) } returns listOf(
            txn("merchant_owner_3", BigDecimal("1500"), daysAgo = 2),
            txn("merchant_owner_3", BigDecimal("1500"), daysAgo = 0),
        )

        When("detecting subscriptions") {
            val result = service.detectSubscriptions("user_1")

            Then("nothing is flagged -- 2 days apart is neither a real weekly nor monthly cadence, avoiding a false positive on buying coffee twice in a row") {
                result.subscriptions.size shouldBe 0
            }
        }
    }

    Given("two payments to the same merchant but a real price increase, 30 days apart") {
        val transactionRepository = mockk<TransactionRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = SubscriptionDetectionService(transactionRepository, merchantRepository)

        every { transactionRepository.findBySenderIdAndTypeInAndStatusOrderByCreatedAtAsc(any(), any(), any()) } returns listOf(
            txn("merchant_owner_4", BigDecimal("3000"), daysAgo = 30),
            txn("merchant_owner_4", BigDecimal("4500"), daysAgo = 0),
        )
        every { merchantRepository.findByOwnerUserId("merchant_owner_4") } returns merchant("merchant_owner_4", "Spotify Rwanda")

        When("detecting subscriptions") {
            val result = service.detectSubscriptions("user_1")

            Then("it's still real-flagged as the same recurring series -- the cadence didn't break just because the price did (2026-07-26 fix: this used to be silently missed entirely)") {
                result.subscriptions.size shouldBe 1
                result.subscriptions[0].amount shouldBe BigDecimal("4500")
            }
            Then("it real-surfaces the genuine price increase, matching Toss's own real subscription-price-change alert") {
                result.subscriptions[0].priceIncreased shouldBe true
                result.subscriptions[0].previousAmount shouldBe BigDecimal("3000")
            }
        }
    }

    Given("a genuinely non-recurring merchant relationship: irregular amounts AND irregular timing") {
        val transactionRepository = mockk<TransactionRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = SubscriptionDetectionService(transactionRepository, merchantRepository)

        every { transactionRepository.findBySenderIdAndTypeInAndStatusOrderByCreatedAtAsc(any(), any(), any()) } returns listOf(
            txn("merchant_owner_5", BigDecimal("3000"), daysAgo = 12),
            txn("merchant_owner_5", BigDecimal("4500"), daysAgo = 3),
        )

        When("detecting subscriptions") {
            val result = service.detectSubscriptions("user_1")

            Then("nothing is flagged -- real cadence consistency (not amount) is still what actually gates a real subscription; irregular timing alone correctly stays a real false-positive guard") {
                result.subscriptions.size shouldBe 0
            }
        }
    }
})
