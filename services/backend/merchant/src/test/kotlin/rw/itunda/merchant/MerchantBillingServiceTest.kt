package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantBillingPlan
import rw.itunda.core.domain.MerchantBillingSubscription
import rw.itunda.core.domain.MerchantBillingSubscriptionStatus
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantBillingPlanRepository
import rw.itunda.core.repository.MerchantBillingSubscriptionRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * Real Kakao Pay 정기결제/Toss Payments billing-key-style recurring merchant billing --
 * see MerchantBillingService's own doc comment. Same Kotest + MockK convention as the
 * rest of this module (MerchantServiceTest, MerchantFollowServiceTest); LedgerService
 * itself is mocked, not exercised for real.
 */
class MerchantBillingServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a merchant with an active billing plan and a customer with a account") {
        val merchantBillingPlanRepository = mockk<MerchantBillingPlanRepository>()
        val merchantBillingSubscriptionRepository = mockk<MerchantBillingSubscriptionRepository>(relaxed = true)
        every { merchantBillingSubscriptionRepository.save(any()) } answers { firstArg() }
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Same known "relaxed mockk can't correctly infer JpaRepository's generic
        // save() signature" gotcha this project's own tests already document
        // repeatedly -- explicit stub, doubly needed here since executeCharge wraps
        // both the notification save and the new push call in one try/catch, so an
        // internally-thrown ClassCastException on save() silently skips the push too.
        every { notificationRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        val chargeExecutor = MerchantBillingChargeExecutor(ledgerService, transactionRepository, notificationRepository, pushNotificationService, accountRepository, autoTopUpService)
        val service = MerchantBillingService(
            merchantBillingPlanRepository, merchantBillingSubscriptionRepository, merchantRepository,
            accountRepository, chargeExecutor, rateLimiter, notificationRepository, pushNotificationService,
        )

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_merchant", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)
        val plan = MerchantBillingPlan(id = "plan_1", merchantId = "merchant_1", name = "Monthly coffee box", amount = BigDecimal("5000"), intervalDays = 30)
        val customerAccount = account("account_customer", "customer_1").also { it.type = AccountType.PAY }
        val merchantAccount = account("account_merchant", "owner_1")

        When("a customer subscribes and their first charge succeeds") {
            every { merchantBillingPlanRepository.findById("plan_1") } returns Optional.of(plan)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("customer_1", AccountType.PAY) } returns customerAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("txn_1", emptyList())

            val subscription = service.subscribe("customer_1", "plan_1")

            Then("it creates an active subscription with the first charge already recorded") {
                subscription.status shouldBe MerchantBillingSubscriptionStatus.ACTIVE
                subscription.chargeCount shouldBe 1
                (subscription.lastChargedAt != null) shouldBe true
                subscription.lastFailureReason shouldBe null
            }

            Then("it advances nextChargeAt by one real interval from the charge") {
                (subscription.nextChargeAt.isAfter(Instant.now().plusSeconds(29 * 24 * 3600))) shouldBe true
            }

            Then("it posts one real 3-leg ledger transaction for the first cycle") {
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
            }

            Then("it sends a real charged notification to the customer") {
                val notificationSlot = slot<rw.itunda.core.domain.Notification>()
                verify(exactly = 1) { notificationRepository.save(capture(notificationSlot)) }
                notificationSlot.captured.userId shouldBe "customer_1"
                notificationSlot.captured.type shouldBe "MERCHANT_BILLING_CHARGED"
            }

            Then("the customer also gets a real mobile push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("customer_1", "Subscription charged", any(), any()) }
            }
        }

        // Real Toss Bank/Toss Pay separation (2026-08-21) -- a subscription charge is
        // real merchant collection, same as MerchantService.collect()'s own QR path, so
        // it gets the same auto-topup-from-Bank-if-short treatment.
        When("a customer's Pay money is short but auto top-up from Bank covers it") {
            val shortPayAccount = account("account_customer_short", "customer_short").also {
                it.type = AccountType.PAY; it.availableBalance = BigDecimal("1000")
            }
            val toppedUpAccount = account("account_customer_short", "customer_short").also {
                it.type = AccountType.PAY; it.availableBalance = BigDecimal("10000")
            }
            every { merchantBillingPlanRepository.findById("plan_1") } returns Optional.of(plan)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("customer_short", AccountType.PAY) } returns shortPayAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { accountRepository.findById("account_customer_short") } returns Optional.of(toppedUpAccount)
            every { autoTopUpService.topUpPayFromMain("customer_short", "account_customer_short", BigDecimal("4000")) } returns
                rw.itunda.account.AutoTopUpTriggerResult(true, "Topped up 4000 RWF from itunda Bank")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("txn_topup", emptyList())

            val subscription = service.subscribe("customer_short", "plan_1")

            Then("it calls topUpPayFromMain for exactly the real shortfall, then completes the charge") {
                verify(exactly = 1) { autoTopUpService.topUpPayFromMain("customer_short", "account_customer_short", BigDecimal("4000")) }
                verify(exactly = 0) { autoTopUpService.topUpShortfall(any(), any(), any()) }
                subscription.status shouldBe MerchantBillingSubscriptionStatus.ACTIVE
            }
        }

        When("a customer's first charge fails for insufficient funds") {
            every { merchantBillingPlanRepository.findById("plan_1") } returns Optional.of(plan)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("customer_1", AccountType.PAY) } returns customerAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { ledgerService.postLedgerTransaction(any(), any()) } throws InsufficientFundsException("Insufficient balance")

            Then("subscribe itself fails honestly instead of silently creating a subscription with a hidden failed charge") {
                try {
                    service.subscribe("customer_1", "plan_1")
                    throw AssertionError("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    // expected -- real Kakao Pay/Toss billing-key acquisition never
                    // leaves a "successfully created" authorization behind a failed
                    // first payment.
                }
            }

            Then("no subscription row is ever persisted for the failed attempt") {
                verify(exactly = 0) { merchantBillingSubscriptionRepository.save(any()) }
            }
        }

        When("the merchant owns the plan and tries to subscribe to their own plan") {
            every { merchantBillingPlanRepository.findById("plan_1") } returns Optional.of(plan)
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

            Then("it is rejected") {
                try {
                    service.subscribe("owner_1", "plan_1")
                    throw AssertionError("expected SelfSubscriptionException")
                } catch (e: SelfSubscriptionException) {
                    // expected
                }
            }
        }

        When("the plan has been deactivated") {
            val inactivePlan = MerchantBillingPlan(id = "plan_2", merchantId = "merchant_1", name = "Retired plan", amount = BigDecimal("1000"), intervalDays = 30, active = false)
            every { merchantBillingPlanRepository.findById("plan_2") } returns Optional.of(inactivePlan)

            Then("subscribing is rejected") {
                try {
                    service.subscribe("customer_1", "plan_2")
                    throw AssertionError("expected BillingPlanNotFoundException")
                } catch (e: BillingPlanNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("an active subscription due for its recurring charge") {
        val merchantBillingPlanRepository = mockk<MerchantBillingPlanRepository>()
        val merchantBillingSubscriptionRepository = mockk<MerchantBillingSubscriptionRepository>(relaxed = true)
        every { merchantBillingSubscriptionRepository.save(any()) } answers { firstArg() }
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        // Same known "relaxed mockk can't correctly infer JpaRepository's generic
        // save() signature" gotcha this project's own tests already document
        // repeatedly -- explicit stub, doubly needed here since executeCharge wraps
        // both the notification save and the new push call in one try/catch, so an
        // internally-thrown ClassCastException on save() silently skips the push too.
        every { notificationRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        val chargeExecutor = MerchantBillingChargeExecutor(ledgerService, transactionRepository, notificationRepository, pushNotificationService, accountRepository, autoTopUpService)
        val service = MerchantBillingService(
            merchantBillingPlanRepository, merchantBillingSubscriptionRepository, merchantRepository,
            accountRepository, chargeExecutor, rateLimiter, notificationRepository, pushNotificationService,
        )

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_merchant", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)
        val plan = MerchantBillingPlan(id = "plan_1", merchantId = "merchant_1", name = "Monthly coffee box", amount = BigDecimal("5000"), intervalDays = 30)
        val customerAccount = account("account_customer", "customer_1").also { it.type = AccountType.PAY }
        val merchantAccount = account("account_merchant", "owner_1")
        val subscription = MerchantBillingSubscription(
            id = "billing_sub_1", planId = "plan_1", merchantId = "merchant_1", customerId = "customer_1",
            nextChargeAt = Instant.now(), chargeCount = 3, lastChargedAt = Instant.now().minusSeconds(30 * 24 * 3600),
        )

        When("the recurring charge succeeds") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("customer_1", AccountType.PAY) } returns customerAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("txn_2", emptyList())

            val succeeded = service.chargeOne(subscription, plan)

            Then("it returns true and advances the schedule without throwing") {
                succeeded shouldBe true
                subscription.chargeCount shouldBe 4
            }
        }

        When("a recurring charge is recorded inside an uncommitted transaction") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("customer_1", AccountType.PAY) } returns customerAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("txn_after_commit", emptyList())

            TransactionSynchronizationManager.initSynchronization()
            try {
                val succeeded = service.chargeOne(subscription, plan)

                Then("the charge is recorded, but its external push is still withheld") {
                    succeeded shouldBe true
                    verify(exactly = 1) { notificationRepository.save(any()) }
                    verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
                }

                Then("the customer push is emitted only by the commit callback") {
                    TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                    verify(exactly = 1) {
                        pushNotificationService.sendToUser(
                            "customer_1",
                            "Subscription charged",
                            any(),
                            mapOf("subscriptionId" to "billing_sub_1"),
                        )
                    }
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }

        When("the recurring charge fails for insufficient funds") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { accountRepository.findByUserIdAndType("customer_1", AccountType.PAY) } returns customerAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)
            every { ledgerService.postLedgerTransaction(any(), any()) } throws InsufficientFundsException("Insufficient balance")
            val staleChargeCount = subscription.chargeCount

            val succeeded = service.chargeOne(subscription, plan)

            Then("it is skipped honestly -- never thrown, never fabricated as success -- and the schedule still advances to the next cycle") {
                succeeded shouldBe false
                subscription.lastFailureReason shouldBe "Insufficient balance"
                subscription.chargeCount shouldBe staleChargeCount
            }

            Then("the customer receives a real MERCHANT_BILLING_FAILED notification and push naming the plan") {
                val notif = slot<rw.itunda.core.domain.Notification>()
                verify(exactly = 1) { notificationRepository.save(capture(notif)) }
                notif.captured.userId shouldBe "customer_1"
                notif.captured.type shouldBe "MERCHANT_BILLING_FAILED"
                notif.captured.body shouldBe "We couldn't charge your \"Monthly coffee box\" subscription: Insufficient balance. We'll try again next cycle."
                verify(exactly = 1) { pushNotificationService.sendToUser("customer_1", "Subscription payment failed", any(), any()) }
            }
        }

        When("the recurring charge fails because the merchant's account is no longer available") {
            every { merchantRepository.findById("merchant_1") } returns Optional.empty()
            every { accountRepository.findByUserIdAndType("customer_1", AccountType.PAY) } returns customerAccount

            val succeeded = service.chargeOne(subscription, plan)

            Then("it is skipped honestly and the customer is still notified") {
                succeeded shouldBe false
                subscription.lastFailureReason shouldBe "Merchant or account no longer available"
                verify(exactly = 1) {
                    notificationRepository.save(match { it.userId == "customer_1" && it.type == "MERCHANT_BILLING_FAILED" })
                }
            }
        }
    }

    // Real regression guard for the 2026-08-17 transaction-poisoning fix (see
    // MerchantBillingChargeExecutor's own doc comment for the full account). MockK
    // unit tests never create a real Spring AOP proxy, so they can never actually
    // observe the UnexpectedRollbackException this bug produced live -- only a
    // structural check like this one can catch a future regression (re-adding
    // @Transactional to chargeOne, or moving the charge logic back onto this class as
    // a self-invoked call) before it reaches a real deployed backend again.
    Given("the transaction-boundary fix for the scheduler's per-row charge loop") {
        Then("chargeOne itself must not carry @Transactional -- it delegates the real charge to a separate bean") {
            val method = MerchantBillingService::class.java.declaredMethods.first { it.name == "chargeOne" }
            method.isAnnotationPresent(Transactional::class.java) shouldBe false
        }

        Then("the real charge-posting step lives on MerchantBillingChargeExecutor, a genuinely separate @Transactional bean") {
            val method = MerchantBillingChargeExecutor::class.java.declaredMethods.first { it.name == "execute" }
            method.isAnnotationPresent(Transactional::class.java) shouldBe true
        }
    }
}) {
    // Same reasoning as MerchantServiceTest.kt: fresh fixtures per leaf test so a
    // mutation in one When (e.g. advancing chargeCount) can't leak into a sibling test.
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
