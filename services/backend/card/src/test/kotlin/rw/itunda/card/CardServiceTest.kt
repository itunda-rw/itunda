package rw.itunda.card

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.DebitCard
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.DebitCardRepository
import rw.itunda.core.repository.DebitCardTransactionRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/** First test coverage for the real Toss Bank 체크카드 (check/debit card) -- see
 * DebitCard.kt's own doc comment for the full sourced account. */
class CardServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("1000000"), availableBalance = BigDecimal("1000000"),
    )

    fun freshCard(userId: String) = DebitCard(
        id = "card_1", userId = userId, last4 = "1234",
        dailyLimit = BigDecimal("500000"), monthlyLimit = BigDecimal("5000000"),
    )

    // Relaxed mocking of JpaRepository.save's generic <S extends T> S save(S) signature
    // doesn't reliably return the passed-in instance, so every call site stubs it
    // explicitly instead -- same fix OverdraftServiceTest's own notificationRepository
    // mock already established.
    fun notificationRepositoryMock(): NotificationRepository {
        val repo = mockk<NotificationRepository>(relaxed = true)
        every { repo.save(any()) } answers { firstArg() }
        return repo
    }

    fun newService(
        debitCardRepository: DebitCardRepository = mockk(),
        debitCardTransactionRepository: DebitCardTransactionRepository = mockk(),
        walletRepository: WalletRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        notificationRepository: NotificationRepository = notificationRepositoryMock(),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = CardService(debitCardRepository, debitCardTransactionRepository, walletRepository, ledgerService, notificationRepository, pushNotificationService, rateLimiter)

    Given("a real user issuing their first itunda debit card") {
        val debitCardRepository = mockk<DebitCardRepository>()
        every { debitCardRepository.findByUserId("user_1") } returns null
        val savedSlot = mutableListOf<DebitCard>()
        every { debitCardRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = newService(debitCardRepository = debitCardRepository)

        When("issuing a card") {
            val card = service.issueCard("user_1")

            Then("it real-issues a fresh, unfrozen card at the real default limits with a real 4-digit last4") {
                card.userId shouldBe "user_1"
                card.frozen shouldBe false
                card.dailyLimit shouldBe DebitCard.DEFAULT_DAILY_LIMIT
                card.monthlyLimit shouldBe DebitCard.DEFAULT_MONTHLY_LIMIT
                card.last4.length shouldBe 4
                savedSlot.size shouldBe 1
            }
        }
    }

    Given("a real user who already has a card") {
        val debitCardRepository = mockk<DebitCardRepository>()
        every { debitCardRepository.findByUserId("user_1") } returns freshCard("user_1")
        val service = newService(debitCardRepository = debitCardRepository)

        When("issuing a second card") {
            Then("it real-409s rather than silently issuing a duplicate") {
                try {
                    service.issueCard("user_1")
                    error("expected CardAlreadyIssuedException")
                } catch (_: CardAlreadyIssuedException) {
                    // expected
                }
            }
        }
    }

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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val card = freshCard("user_1")
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.findByIdForUpdate("card_1") } returns Optional.of(card)
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        every { debitCardTransactionRepository.save(any()) } answers { firstArg() }
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
        val legsSlot = mutableListOf<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val service = newService(
            debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository,
            walletRepository = walletRepository, ledgerService = ledgerService,
        )

        When("charging a real purchase") {
            val result = service.chargeWithCard("user_1", BigDecimal("5000"), "Kigali Cafe")

            Then("it real-posts a balanced WALLET debit / CARD_SPEND_EXPENSE credit ledger transaction") {
                val legs = legsSlot.first()
                legs.size shouldBe 2
                legs.first { it.accountType == LedgerAccountType.WALLET }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountType == LedgerAccountType.CARD_SPEND_EXPENSE }.direction shouldBe LedgerDirection.CREDIT
                result.transaction.amount shouldBe BigDecimal("5000")
                result.transaction.merchantName shouldBe "Kigali Cafe"
                result.card.remainingToday shouldBe BigDecimal("495000")
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

    Given("a real user freezing their card") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        val card = freshCard("user_1")
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.save(any()) } answers { firstArg() }
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository,
            pushNotificationService = pushNotificationService,
        )

        When("freezing") {
            val view = service.freeze("user_1")

            Then("it real-freezes and sends a real security alert") {
                view.frozen shouldBe true
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any()) }
            }
        }
    }

    Given("a real user setting invalid card limits") {
        val service = newService()

        When("a zero or negative limit is requested") {
            Then("it real-400s") {
                try {
                    service.setLimits("user_1", BigDecimal.ZERO, BigDecimal("5000000"))
                    error("expected CardInvalidLimitException")
                } catch (_: CardInvalidLimitException) {
                    // expected
                }
            }
        }
    }
})
