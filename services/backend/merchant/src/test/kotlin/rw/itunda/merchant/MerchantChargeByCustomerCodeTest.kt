package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.CustomerPaymentCode
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.CustomerPaymentCodeRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PaymentIntentRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * Real Toss Bank/Toss Pay separation (2026-08-21) -- chargeByCustomerCode (the
 * merchant-scans-customer "My code" flow) still defaulted to a direct AccountType.MAIN
 * debit after collect() (the customer-scans-merchant flow, see MerchantServiceTest) was
 * already fixed to use PAY money with auto-topup, a real inconsistency between two
 * sibling payment-collection methods. Previously zero test coverage existed for
 * chargeByCustomerCode at all. Split into its own file (not added to
 * MerchantServiceTest.kt) to keep that already-oversized file's real baseline from
 * growing further -- see docs/ARCHITECTURE_GUIDELINES.md §2.
 */
class MerchantChargeByCustomerCodeTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a registered merchant charging a customer's presented code") {
        val merchantRepository = mockk<MerchantRepository>()
        val paymentIntentRepository = mockk<PaymentIntentRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val webhookDeliveryService = mockk<WebhookDeliveryService>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val demoCardAuthorizationService = DemoCardAuthorizationService()
        val shoppingCashbackService = mockk<ShoppingCashbackService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val merchantCouponService = mockk<MerchantCouponService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val customerPaymentCodeRepository = mockk<CustomerPaymentCodeRepository>(relaxed = true)
        // Same real ClassCastException pitfall documented repeatedly across this
        // module's tests -- relaxed mockk's save() can't correctly infer JpaRepository's
        // generic <S extends T> S save(S) signature for paymentCode.save().
        every { customerPaymentCodeRepository.save(any()) } answers { firstArg() }
        val orderRepository = mockk<rw.itunda.core.repository.OrderRepository>(relaxed = true)
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>(relaxed = true)
        val merchantLoyaltyPointsService = mockk<MerchantLoyaltyPointsService>(relaxed = true)
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        // Real default: no top-up needed, the account passed in already covers the
        // charge -- the short-balance test below overrides this with a more specific
        // stub for its own exact (userId, account, amount) triple.
        every { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) } answers { secondArg() }
        val service = MerchantService(merchantRepository, paymentIntentRepository, accountRepository, ledgerService, webhookDeliveryService, transactionRepository, fraudRuleEngine, demoCardAuthorizationService, shoppingCashbackService, rateLimiter, ledgerEntryRepository, notificationRepository, merchantCouponService, pushNotificationService, customerPaymentCodeRepository, orderRepository, orderItemRepository, merchantLoyaltyPointsService, autoTopUpService)

        val ownerAccount = account("account_merchant", "owner_1")
        val merchant = Merchant(
            id = "merchant_1", ownerUserId = "owner_1", accountId = "account_merchant",
            businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE,
        )

        When("charging a customer's presented code with no explicit account selected and sufficient Pay money") {
            val payerPayAccount = account("account_paycode_1", "payer_code_1").also { it.type = AccountType.PAY }
            val paymentCode = CustomerPaymentCode(
                id = "cpc_1", userId = "payer_code_1", code = "code_1", expiresAt = Instant.now().plusSeconds(600),
            )
            val legsSlot = slot<List<LedgerLeg>>()
            every { customerPaymentCodeRepository.findByCode("code_1") } returns paymentCode
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { accountRepository.findByUserIdAndType("payer_code_1", AccountType.PAY) } returns payerPayAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns
                LedgerPostResult("ledgertxn_code_1", emptyList())

            val result = service.chargeByCustomerCode("owner_1", "code_1", BigDecimal("3000"))

            Then("it charges the real itunda Pay account, not Bank") {
                result["status"] shouldBe "COMPLETED"
                legsSlot.captured.first { it.accountId == "account_paycode_1" }.amount shouldBe BigDecimal("3000")
                verify(exactly = 1) { autoTopUpService.ensureSufficientPayBalance("payer_code_1", payerPayAccount, BigDecimal("3000")) }
            }

            // Real gap found live (repo-wide fraud-engine-verification sweep,
            // 2026-09-08): fraudRuleEngine was relaxed = true with zero verify{}
            // anywhere in this file, so a future accidental removal of the real
            // fraudRuleEngine.evaluate call would have compiled and passed silently.
            Then("the real fraud engine is actually consulted, not just mocked away") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("payer_code_1", "owner_1", BigDecimal("3000"), "ledgertxn_code_1") }
            }
        }

        When("charging a customer's presented code with no explicit account and Pay money is short") {
            val shortPayAccount = account("account_paycode_2", "payer_code_2")
                .also { it.type = AccountType.PAY; it.availableBalance = BigDecimal("1000") }
            val toppedUpAccount = account("account_paycode_2", "payer_code_2")
                .also { it.type = AccountType.PAY; it.availableBalance = BigDecimal("10000") }
            val paymentCode = CustomerPaymentCode(
                id = "cpc_2", userId = "payer_code_2", code = "code_2", expiresAt = Instant.now().plusSeconds(600),
            )
            val legsSlot = slot<List<LedgerLeg>>()
            every { customerPaymentCodeRepository.findByCode("code_2") } returns paymentCode
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { accountRepository.findByUserIdAndType("payer_code_2", AccountType.PAY) } returns shortPayAccount
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)
            every { autoTopUpService.ensureSufficientPayBalance("payer_code_2", shortPayAccount, BigDecimal("3000")) } returns toppedUpAccount
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns
                LedgerPostResult("ledgertxn_code_2", emptyList())

            val result = service.chargeByCustomerCode("owner_1", "code_2", BigDecimal("3000"))

            Then("it calls the shared top-up helper for exactly this payment's real amount, then completes the payment") {
                verify(exactly = 1) { autoTopUpService.ensureSufficientPayBalance("payer_code_2", shortPayAccount, BigDecimal("3000")) }
                result["status"] shouldBe "COMPLETED"
                legsSlot.captured.first { it.accountId == "account_paycode_2" }.amount shouldBe BigDecimal("3000")
            }
        }

        When("charging a customer's presented code where the customer explicitly picked a specific account") {
            val explicitAccount = account("account_explicit", "payer_code_3")
                .also { it.type = AccountType.FOREIGN_CURRENCY; it.availableBalance = BigDecimal("500") }
            val paymentCode = CustomerPaymentCode(
                id = "cpc_3", userId = "payer_code_3", code = "code_3", expiresAt = Instant.now().plusSeconds(600),
                accountId = "account_explicit",
            )
            every { customerPaymentCodeRepository.findByCode("code_3") } returns paymentCode
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { accountRepository.findById("account_explicit") } returns Optional.of(explicitAccount)
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)
            every { ledgerService.postLedgerTransaction(any(), any()) } throws
                rw.itunda.core.ledger.InsufficientFundsException("Insufficient available balance for this transfer")

            Then("no auto top-up is attempted -- the customer's explicit account choice is charged directly, even if short") {
                try {
                    service.chargeByCustomerCode("owner_1", "code_3", BigDecimal("3000"))
                    error("expected InsufficientFundsException")
                } catch (e: rw.itunda.core.ledger.InsufficientFundsException) {
                    // expected
                }
                verify(exactly = 0) { autoTopUpService.ensureSufficientPayBalance(any(), any(), any()) }
                verify(exactly = 0) { accountRepository.findByUserIdAndType("payer_code_3", AccountType.PAY) }
            }
        }

        // Real gap found+fixed (2026-08-21, direct user confirmation): the funding-
        // source picker had no account-type restriction at all -- a customer could
        // generate a code funded by a SAVINGS/INVESTMENT/LOAN account, none of which
        // are real payment-eligible products. See PAYMENT_ELIGIBLE_ACCOUNT_TYPES's
        // own doc comment.
        When("generating a payment code explicitly funded by a real non-payment-eligible account") {
            val savingsAccount = account("account_savings", "payer_code_4").also { it.type = AccountType.SAVINGS }
            every { accountRepository.findById("account_savings") } returns Optional.of(savingsAccount)

            Then("it rejects loudly at generation time, not silently at charge time") {
                try {
                    service.generateCustomerPaymentCode("payer_code_4", "account_savings")
                    error("expected PaymentCodeAccountNotEligibleException")
                } catch (e: PaymentCodeAccountNotEligibleException) {
                    // expected
                }
                verify(exactly = 0) { customerPaymentCodeRepository.save(any()) }
            }
        }

        When("generating a payment code explicitly funded by a real MAIN or FOREIGN_CURRENCY account") {
            val mainAccount = account("account_main_5", "payer_code_5").also { it.type = AccountType.MAIN }
            every { accountRepository.findById("account_main_5") } returns Optional.of(mainAccount)

            Then("it's accepted -- MAIN and FOREIGN_CURRENCY are real payment-eligible funding sources") {
                val code = service.generateCustomerPaymentCode("payer_code_5", "account_main_5")
                code.accountId shouldBe "account_main_5"
            }
        }

        // Defense-in-depth coverage for the charge-time re-check -- unreachable via
        // generateCustomerPaymentCode alone (which already rejects this), but a
        // payment code is a real bearer credential this service re-verifies rather
        // than trusting generation time alone.
        When("charging a customer's code whose stored account somehow carries a non-eligible type") {
            val savingsAccount = account("account_savings_6", "payer_code_6").also { it.type = AccountType.SAVINGS }
            val paymentCode = CustomerPaymentCode(
                id = "cpc_6", userId = "payer_code_6", code = "code_6", expiresAt = Instant.now().plusSeconds(600),
                accountId = "account_savings_6",
            )
            every { customerPaymentCodeRepository.findByCode("code_6") } returns paymentCode
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { accountRepository.findById("account_savings_6") } returns Optional.of(savingsAccount)
            every { accountRepository.findById("account_merchant") } returns Optional.of(ownerAccount)

            Then("it rejects before ever posting a real ledger transaction") {
                try {
                    service.chargeByCustomerCode("owner_1", "code_6", BigDecimal("3000"))
                    error("expected PaymentCodeAccountNotEligibleException")
                } catch (e: PaymentCodeAccountNotEligibleException) {
                    // expected
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }
}) {
    // Same reasoning as MerchantServiceTest.kt/LedgerServiceTest.kt: fresh mocks per
    // leaf test so one When's call-count history can't leak into a sibling test.
    override fun isolationMode() = io.kotest.core.spec.IsolationMode.InstancePerLeaf
}
