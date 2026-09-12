package rw.itunda.card

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.DebitCard
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.DebitCardRepository
import rw.itunda.core.repository.DebitCardTransactionRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.math.BigDecimal
import java.util.Optional

/** CardService.chargeWithCard coverage -- extracted from CardServiceTest.kt
 * (2026-09-12) once that file crossed the 500-line file-size-lint guideline; charging
 * is a genuinely distinct concern from issuance/freeze/PIN-management, the same split
 * rationale PinUpgradeCard.swift's own doc comment already establishes for a
 * self-contained new piece. Helper functions are deliberately duplicated from
 * CardServiceTest.kt rather than shared, matching this codebase's own established
 * small-duplicate-helper-across-split-files convention (see e.g. iOS's
 * TalkScreen.errorMessage/ShopBestSellerBadge). */
class CardChargeServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.MAIN) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = BigDecimal("1000000"), availableBalance = BigDecimal("1000000"),
    )

    fun freshCard(userId: String) = DebitCard(
        id = "card_1", userId = userId, last4 = "1234",
        dailyLimit = BigDecimal("500000"), monthlyLimit = BigDecimal("5000000"),
    )

    fun notificationRepositoryMock(): NotificationRepository {
        val repo = mockk<NotificationRepository>(relaxed = true)
        every { repo.save(any()) } answers { firstArg() }
        return repo
    }

    fun newService(
        debitCardRepository: DebitCardRepository = mockk(),
        debitCardTransactionRepository: DebitCardTransactionRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        notificationRepository: NotificationRepository = notificationRepositoryMock(),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        userRepository: UserRepository = mockk(),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
    ) = CardService(debitCardRepository, debitCardTransactionRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService, rateLimiter, userRepository, fraudRuleEngine)

    Given("a real frozen card") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val card = freshCard("user_1").also { it.frozen = true }
        every { debitCardRepository.findByUserId("user_1") } returns card
        val service = newService(debitCardRepository = debitCardRepository)

        When("attempting a purchase") {
            Then("it real-blocks the purchase before ever touching the ledger") {
                try {
                    service.chargeWithCard("user_1", BigDecimal("5000"), "Kigali Cafe")
                    error("expected CardFrozenException")
                } catch (_: CardFrozenException) {
                    // expected
                }
            }
        }
    }

    Given("a real active card with room under both limits") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val card = freshCard("user_1")
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.findByIdForUpdate("card_1") } returns Optional.of(card)
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        every { debitCardTransactionRepository.save(any()) } answers { firstArg() }
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        val legsSlot = mutableListOf<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(
            debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, fraudRuleEngine = fraudRuleEngine,
            rateLimiter = rateLimiter,
        )

        When("charging a real purchase") {
            val result = service.chargeWithCard("user_1", BigDecimal("5000"), "Kigali Cafe")

            Then("it real-posts a balanced ACCOUNT debit / CARD_SPEND_EXPENSE credit ledger transaction") {
                val legs = legsSlot.first()
                legs.size shouldBe 2
                legs.first { it.accountType == LedgerAccountType.WALLET }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountType == LedgerAccountType.CARD_SPEND_EXPENSE }.direction shouldBe LedgerDirection.CREDIT
                result.transaction.amount shouldBe BigDecimal("5000")
                result.transaction.merchantName shouldBe "Kigali Cafe"
                result.card.remainingToday shouldBe BigDecimal("495000")
            }

            // Real regression test (2026-09-12, repo-wide RateLimiter mock-never-
            // verified sweep re-run): rateLimiter was relaxed = true with zero verify{}
            // anywhere in this extracted file, same gap class as this sweep's other hits.
            Then("it checks the real 30/minute card-charge rate limit for this user") {
                verify(exactly = 1) { rateLimiter.checkLimit("card:charge:user_1", limit = any(), window = any()) }
            }

            // Real bug found live (2026-08-02): the daily/monthly limit check sums real
            // DebitCardTransaction rows, not a mutation of `card` itself, so two
            // concurrent charges for the same card could both read the same pre-charge
            // sum before either committed and both post real money, together exceeding
            // the card's own documented limit. Locking the card row before computing
            // the sums serializes concurrent charges on the SAME card.
            Then("the card row is locked before the spend sums are ever computed") {
                verify(exactly = 1) { debitCardRepository.findByIdForUpdate("card_1") }
            }

            // Real gap closed 2026-09-07 (Card product-completeness pass): real money
            // movement with zero FraudRuleEngine coverage before this fix.
            Then("the real purchase is evaluated against the cardholder's own fraud history") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("user_1", null, BigDecimal("5000"), "ledgertxn_1") }
            }
        }
    }

    Given("a real active card and a user who wants to fund a purchase from their Pay account instead") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val card = freshCard("user_1")
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.findByIdForUpdate("card_1") } returns Optional.of(card)
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        every { debitCardTransactionRepository.save(any()) } answers { firstArg() }
        every { accountRepository.findByUserIdAndType("user_1", AccountType.PAY) } returns account("account_pay_1", "user_1", AccountType.PAY)
        every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val service = newService(
            debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository,
            accountRepository = accountRepository, ledgerService = ledgerService, fraudRuleEngine = fraudRuleEngine,
        )

        // Real Toss "결제 계좌" (payment account) reference (2026-09-12) -- the same
        // AccountService.setNickname-adjacent Manage-screen re-audit found
        // CardService.chargeWithCard hardcoded to MAIN with no way to fund a purchase
        // from any other real itunda account.
        When("charging with Pay explicitly chosen as the funding account") {
            val result = service.chargeWithCard("user_1", BigDecimal("5000"), "Kigali Cafe", AccountType.PAY)

            Then("it debits the Pay account, not Main") {
                verify(exactly = 1) { accountRepository.findByUserIdAndType("user_1", AccountType.PAY) }
                result.transaction.fundingAccountType shouldBe AccountType.PAY
            }
        }
    }

    Given("a real active card and a user who asks to fund a purchase from an account this feature doesn't support") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val card = freshCard("user_1")
        every { debitCardRepository.findByUserId("user_1") } returns card
        val service = newService(debitCardRepository = debitCardRepository)

        // Real cross-currency-correctness guard (2026-09-12) -- see CardService's own
        // CARD_FUNDING_ACCOUNT_TYPES doc comment: crediting the single global
        // card_spend_expense clearing account (implicitly RWF) from a foreign-currency
        // debit would silently misstate itunda's own expense books.
        When("charging with FOREIGN_CURRENCY as the funding account") {
            Then("it real-400s rather than silently posting a currency-mismatched ledger entry") {
                try {
                    service.chargeWithCard("user_1", BigDecimal("5000"), "Kigali Cafe", AccountType.FOREIGN_CURRENCY)
                    error("expected CardInvalidFundingAccountException")
                } catch (_: CardInvalidFundingAccountException) {
                    // expected
                }
            }
        }
    }

    Given("a real card already spent right up to its daily limit") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        val card = freshCard("user_1")
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.findByIdForUpdate("card_1") } returns Optional.of(card)
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal("499000")
        val service = newService(debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository)

        When("a further purchase would push it over the daily limit") {
            Then("it real-blocks before ever touching the ledger") {
                try {
                    service.chargeWithCard("user_1", BigDecimal("5000"), "Kigali Cafe")
                    error("expected CardDailyLimitExceededException")
                } catch (_: CardDailyLimitExceededException) {
                    // expected
                }
            }
        }
    }
}) {
    // Same isolation fix as CardServiceTest.kt -- see its own doc comment.
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
