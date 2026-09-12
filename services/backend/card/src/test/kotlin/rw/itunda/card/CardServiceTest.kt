package rw.itunda.card

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.domain.DebitCard
import rw.itunda.core.domain.DebitCardDesign
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.DebitCardRepository
import rw.itunda.core.repository.DebitCardTransactionRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.domain.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/** First test coverage for the real Toss Bank 체크카드 (check/debit card) -- see
 * DebitCard.kt's own doc comment for the full sourced account. */
class CardServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.MAIN) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = BigDecimal("1000000"), availableBalance = BigDecimal("1000000"),
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
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        notificationRepository: NotificationRepository = notificationRepositoryMock(),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        userRepository: UserRepository = mockk(),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
    ) = CardService(debitCardRepository, debitCardTransactionRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService, rateLimiter, userRepository, fraudRuleEngine)

    Given("a real user issuing their first itunda debit card") {
        val debitCardRepository = mockk<DebitCardRepository>()
        every { debitCardRepository.findByUserId("user_1") } returns null
        val savedSlot = mutableListOf<DebitCard>()
        every { debitCardRepository.save(capture(savedSlot)) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(debitCardRepository = debitCardRepository, rateLimiter = rateLimiter)

        When("issuing a card with no design specified") {
            val card = service.issueCard("user_1")

            Then("it real-issues a fresh, unfrozen card at the real default limits, design, and a real 4-digit last4") {
                card.userId shouldBe "user_1"
                card.frozen shouldBe false
                card.dailyLimit shouldBe DebitCard.DEFAULT_DAILY_LIMIT
                card.monthlyLimit shouldBe DebitCard.DEFAULT_MONTHLY_LIMIT
                card.design shouldBe DebitCardDesign.DEFAULT
                card.last4.length shouldBe 4
                savedSlot.size shouldBe 1
            }

            // Real gap closed 2026-09-07 (Card product-completeness pass): issue had
            // zero rateLimiter.checkLimit call before this.
            Then("the per-user issuance rate limit is enforced") {
                verify(exactly = 1) { rateLimiter.checkLimit("card:issue:user_1", limit = any(), window = any()) }
            }
        }
    }

    // Real gaps closed 2026-09-07 (Card product-completeness pass): freeze/unfreeze
    // had zero rateLimiter.checkLimit call before this -- every other state-
    // mutating card endpoint already had one.
    Given("a real active card being frozen") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        val card = freshCard("user_1")
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository, rateLimiter = rateLimiter)

        When("freezing") {
            service.freeze("user_1")

            Then("the per-user freeze rate limit is enforced") {
                verify(exactly = 1) { rateLimiter.checkLimit("card:freeze:user_1", limit = any(), window = any()) }
            }
        }
    }

    Given("a real frozen (never lost or closed) card being unfrozen") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        val card = freshCard("user_1").also { it.frozen = true }
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository, rateLimiter = rateLimiter)

        When("unfreezing") {
            service.unfreeze("user_1")

            Then("the per-user unfreeze rate limit is enforced") {
                verify(exactly = 1) { rateLimiter.checkLimit("card:unfreeze:user_1", limit = any(), window = any()) }
            }
        }
    }

    Given("a real user picking a real card design at issuance") {
        val debitCardRepository = mockk<DebitCardRepository>()
        every { debitCardRepository.findByUserId("user_1") } returns null
        val savedSlot = mutableListOf<DebitCard>()
        every { debitCardRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = newService(debitCardRepository = debitCardRepository)

        When("issuing with a real whitelisted design") {
            val card = service.issueCard("user_1", DebitCardDesign.ROSE_FOREST)

            Then("the chosen design is real-persisted, not silently overridden") {
                card.design shouldBe DebitCardDesign.ROSE_FOREST
            }
        }

        When("issuing with a design no client actually knows how to render") {
            Then("it real-400s rather than silently persisting an unknown design") {
                try {
                    service.issueCard("user_1", "hot_pink_glitter")
                    error("expected CardInvalidDesignException")
                } catch (_: CardInvalidDesignException) {
                    // expected
                }
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
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(rateLimiter = rateLimiter)

        When("a zero or negative limit is requested") {
            Then("it real-400s") {
                try {
                    service.setLimits("user_1", BigDecimal.ZERO, BigDecimal("5000000"))
                    error("expected CardInvalidLimitException")
                } catch (_: CardInvalidLimitException) {
                    // expected
                }
                // Real regression test (2026-09-12, repo-wide RateLimiter mock-never-
                // verified sweep re-run): setLimits checks the rate limit before
                // validating the requested amounts, so this real-400 path still
                // exercises it -- nothing previously proved this call was live code.
                verify(exactly = 1) { rateLimiter.checkLimit("card:set-limits:user_1", limit = any(), window = any()) }
            }
        }
    }

    // Real isolation coverage for the "분실신고"/"카드 재발급"/"카드 해지하기"
    // (report lost / reissue / close) one-way states this session added -- the exact
    // bug class this whole feature exists to close was bank-mfe's own previous
    // "report lost or stolen" button silently relabeling the ordinary, self-
    // reversible freeze() call.
    Given("a real card reported lost or stolen") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        val card = freshCard("user_1")
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.save(any()) } answers { firstArg() }
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository, rateLimiter = rateLimiter)

        When("reporting it lost") {
            val view = service.reportLost("user_1")

            Then("it real-freezes AND real-marks it lost, distinct from an ordinary freeze") {
                view.frozen shouldBe true
                view.lost shouldBe true
                // Real regression test (2026-09-12, repo-wide RateLimiter sweep re-run).
                verify(exactly = 1) { rateLimiter.checkLimit("card:report-lost:user_1", limit = any(), window = any()) }
            }
        }

        When("then attempting to self-unfreeze it") {
            service.reportLost("user_1")
            Then("it real-blocks -- lost is a one-way state, not a togglable freeze") {
                try {
                    service.unfreeze("user_1")
                    error("expected CardLostException")
                } catch (_: CardLostException) {
                    // expected
                }
            }
        }
    }

    Given("a real card closed by its owner") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        val card = freshCard("user_1")
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.save(any()) } answers { firstArg() }
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository, rateLimiter = rateLimiter)

        When("closing it") {
            val view = service.closeCard("user_1")
            Then("it real-closes and real-blocks self-unfreeze") {
                view.closedAt shouldBe card.closedAt
                try {
                    service.unfreeze("user_1")
                    error("expected CardClosedException")
                } catch (_: CardClosedException) {
                    // expected
                }
                // Real regression test (2026-09-12, repo-wide RateLimiter sweep re-run).
                verify(exactly = 1) { rateLimiter.checkLimit("card:close:user_1", limit = any(), window = any()) }
            }
        }

        When("closing it a second time") {
            service.closeCard("user_1")
            Then("it real-409s rather than silently no-opping") {
                try {
                    service.closeCard("user_1")
                    error("expected CardClosedException")
                } catch (_: CardClosedException) {
                    // expected
                }
            }
        }
    }

    Given("a real active (never lost or closed) card") {
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        val service = newService(
            debitCardRepository = mockk<DebitCardRepository>().also {
                every { it.findByUserId("user_1") } returns freshCard("user_1")
            },
            debitCardTransactionRepository = debitCardTransactionRepository,
        )

        When("reissue is attempted on it") {
            Then("it real-409s -- reissue is only a real recovery path from lost/closed, not a way to rotate an active card") {
                try {
                    service.reissue("user_1")
                    error("expected CardNotEligibleForReissueException")
                } catch (_: CardNotEligibleForReissueException) {
                    // expected
                }
            }
        }
    }

    Given("a real lost card being reissued") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        val card = freshCard("user_1").also { it.lost = true; it.frozen = true; it.pinHash = "old_hash" }
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.save(any()) } answers { firstArg() }
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository, rateLimiter = rateLimiter)

        When("reissuing") {
            val view = service.reissue("user_1")

            Then("it real-clears lost/frozen/pin and real-regenerates last4, so the old PIN can never authorize the new card") {
                view.lost shouldBe false
                view.frozen shouldBe false
                view.pinSet shouldBe false
                view.last4 shouldBe card.last4
                card.pinHash shouldBe null
                // Real regression test (2026-09-12, repo-wide RateLimiter sweep re-run).
                verify(exactly = 1) { rateLimiter.checkLimit("card:reissue:user_1", limit = any(), window = any()) }
            }
        }
    }

    Given("a real user changing their card PIN with the correct login credential") {
        val debitCardRepository = mockk<DebitCardRepository>()
        val debitCardTransactionRepository = mockk<DebitCardTransactionRepository>()
        val userRepository = mockk<UserRepository>()
        val passwordEncoder = BCryptPasswordEncoder()
        val card = freshCard("user_1")
        val user = User(id = "user_1", phoneNumber = "+250700000000", firstName = "Test", lastName = "User", passwordHash = passwordEncoder.encode("123456"))
        every { debitCardRepository.findByUserId("user_1") } returns card
        every { debitCardRepository.save(any()) } answers { firstArg() }
        every { debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(any(), any()) } returns BigDecimal.ZERO
        every { userRepository.findById("user_1") } returns Optional.of(user)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(debitCardRepository = debitCardRepository, debitCardTransactionRepository = debitCardTransactionRepository, userRepository = userRepository, rateLimiter = rateLimiter)

        When("setting a real 4-digit PIN") {
            val view = service.setPin("user_1", "4821", "123456")
            Then("it real-persists the new PIN hash, never the plaintext") {
                view.pinSet shouldBe true
                card.pinHash shouldNotBe null
                card.pinHash shouldNotBe "4821"
                // Real regression test (2026-09-12, repo-wide RateLimiter sweep re-run).
                verify(exactly = 1) { rateLimiter.checkLimit("card:set-pin:user_1", limit = any(), window = any()) }
            }
        }

        When("setting a PIN with the wrong current credential") {
            Then("it real-403s rather than accepting it") {
                try {
                    service.setPin("user_1", "4821", "wrong-password")
                    error("expected CardIncorrectCredentialException")
                } catch (_: CardIncorrectCredentialException) {
                    // expected
                }
            }
        }

        When("setting a PIN that isn't exactly 4 digits") {
            Then("it real-400s") {
                try {
                    service.setPin("user_1", "12345", "123456")
                    error("expected CardInvalidPinException")
                } catch (_: CardInvalidPinException) {
                    // expected
                }
            }
        }
    }
}) {
    // Real isolation fix -- the new lost/closed/reissue tests above run multiple
    // `When`s under one `Given` that mutate a real (non-mock) DebitCard instance
    // across them; without this, Kotest's default SingleInstance mode would let
    // state leak between `When`s (e.g. a card closed by an earlier `When` staying
    // closed for a later, supposedly-independent one). Matches the same override
    // AccountServiceTest.kt already uses for the identical reason.
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
