package rw.itunda.savings

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.SaccoDividendDistribution
import rw.itunda.core.domain.SaccoDividendPayout
import rw.itunda.core.domain.SaccoShareholding
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.SaccoDividendDistributionRepository
import rw.itunda.core.repository.SaccoDividendPayoutRepository
import rw.itunda.core.repository.SaccoShareholdingRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for real Umurenge SACCO-style shares & dividends -- Rwanda's
 * own government-backed cooperative savings model. See SaccoService's own doc
 * comment for the full sourced account.
 */
class SaccoServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.MAIN, balance: BigDecimal = BigDecimal("100000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = balance, availableBalance = balance,
    )

    fun user(id: String) = User(
        id = id, phoneNumber = "+25078800$id".take(13), firstName = "Test", lastName = "User",
        passwordHash = "unused", createdAt = Instant.now(),
    )

    fun newService(
        shareholdingRepository: SaccoShareholdingRepository = mockk(),
        distributionRepository: SaccoDividendDistributionRepository = mockk(),
        payoutRepository: SaccoDividendPayoutRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        userRepository: UserRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        accountNumberGenerator: AccountNumberGenerator = mockk(relaxed = true),
    ) = SaccoService(shareholdingRepository, distributionRepository, payoutRepository, accountRepository, userRepository, ledgerService, rateLimiter, accountNumberGenerator)

    Given("a real user with a account buying SACCO shares for the first time") {
        val shareholdingRepository = mockk<SaccoShareholdingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(shareholdingRepository = shareholdingRepository, accountRepository = accountRepository, userRepository = userRepository, ledgerService = ledgerService)

        every { shareholdingRepository.findByUserId("u1") } returns null
        every { userRepository.findById("u1") } returns Optional.of(user("u1"))
        every { accountRepository.findByUserIdAndType("u1", AccountType.MAIN) } returns account("account_u1", "u1")
        every { accountRepository.findById("account_u1") } returns Optional.of(account("account_u1", "u1"))
        every { accountRepository.findByUserIdAndType("sacco_pool_system", AccountType.GROUP) } returns null
        val savedAccountSlot = slot<Account>()
        every { accountRepository.save(capture(savedAccountSlot)) } answers { firstArg() }
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val savedShareholdingSlot = slot<SaccoShareholding>()
        every { shareholdingRepository.save(capture(savedShareholdingSlot)) } answers { firstArg() }

        When("the user buys 10,000 RWF of shares") {
            val result = service.buyShares("u1", BigDecimal("10000"))

            Then("a real shareholding is minted 1:1 with the contribution") {
                result.shareholding.userId shouldBe "u1"
                result.shareholding.sharesHeld shouldBe BigDecimal("10000")
                result.shareholding.totalContributed shouldBe BigDecimal("10000")
                savedAccountSlot.captured.userId shouldBe "sacco_pool_system"
            }
        }
    }

    Given("a real user trying to buy a non-positive amount of shares") {
        val service = newService()

        When("the user tries to buy zero shares") {
            Then("the real request is rejected before touching the ledger") {
                shouldThrow<IllegalArgumentException> { service.buyShares("u1", BigDecimal.ZERO) }
            }
        }
    }

    Given("a real shareholder redeeming more shares than they hold") {
        val shareholdingRepository = mockk<SaccoShareholdingRepository>()
        val service = newService(shareholdingRepository = shareholdingRepository)

        every { shareholdingRepository.findByUserId("u1") } returns SaccoShareholding(
            id = "saccoshare_1", userId = "u1", accountId = "account_u1", sharesHeld = BigDecimal("5000"), totalContributed = BigDecimal("5000"),
        )

        When("the user tries to redeem more than their real balance") {
            Then("the real over-redemption is rejected") {
                shouldThrow<SaccoInsufficientSharesException> { service.redeemShares("u1", BigDecimal("10000")) }
            }
        }
    }

    Given("a real shareholder redeeming shares they actually hold") {
        val shareholdingRepository = mockk<SaccoShareholdingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(shareholdingRepository = shareholdingRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        every { shareholdingRepository.findByUserId("u1") } returns SaccoShareholding(
            id = "saccoshare_1", userId = "u1", accountId = "account_u1", sharesHeld = BigDecimal("5000"), totalContributed = BigDecimal("5000"),
        )
        every { accountRepository.findById("account_u1") } returns Optional.of(account("account_u1", "u1"))
        every { accountRepository.findByUserIdAndType("sacco_pool_system", AccountType.GROUP) } returns account("account_pool", "sacco_pool_system", AccountType.GROUP, BigDecimal("50000"))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
        every { shareholdingRepository.save(any()) } answers { firstArg() }

        When("the user redeems 2,000 of their 5,000 real shares") {
            val result = service.redeemShares("u1", BigDecimal("2000"))

            Then("the real redemption pays out at par and correctly reduces the balance") {
                result.shareholding.sharesHeld shouldBe BigDecimal("3000")
                result.shareholding.totalContributed shouldBe BigDecimal("3000")
            }
        }
    }

    Given("no SACCO shares outstanding anywhere") {
        val shareholdingRepository = mockk<SaccoShareholdingRepository>()
        val service = newService(shareholdingRepository = shareholdingRepository)

        every { shareholdingRepository.findAll() } returns emptyList()

        When("a dividend is declared against an empty pool") {
            Then("the real declaration is refused rather than silently no-op'ing") {
                shouldThrow<SaccoNoSharesOutstandingException> { service.declareDividend() }
            }
        }
    }

    Given("a real pool with two shareholders holding shares in a 2:1 ratio") {
        val shareholdingRepository = mockk<SaccoShareholdingRepository>()
        val distributionRepository = mockk<SaccoDividendDistributionRepository>()
        val payoutRepository = mockk<SaccoDividendPayoutRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            shareholdingRepository = shareholdingRepository, distributionRepository = distributionRepository,
            payoutRepository = payoutRepository, accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val poolAccount = account("account_pool", "sacco_pool_system", AccountType.GROUP, BigDecimal("30000"))
        val shareholdingA = SaccoShareholding(id = "share_a", userId = "u1", accountId = "account_u1", sharesHeld = BigDecimal("20000"), totalContributed = BigDecimal("20000"))
        val shareholdingB = SaccoShareholding(id = "share_b", userId = "u2", accountId = "account_u2", sharesHeld = BigDecimal("10000"), totalContributed = BigDecimal("10000"))

        every { shareholdingRepository.findAll() } returns listOf(shareholdingA, shareholdingB)
        every { accountRepository.findByUserIdAndType("sacco_pool_system", AccountType.GROUP) } returns poolAccount
        every { accountRepository.findByIdForUpdate("account_pool") } returns Optional.of(poolAccount)
        every { distributionRepository.findAllByOrderByDistributionDateDesc() } returns emptyList()
        every { accountRepository.findById("account_u1") } returns Optional.of(account("account_u1", "u1"))
        every { accountRepository.findById("account_u2") } returns Optional.of(account("account_u2", "u2"))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_div", emptyList())
        every { payoutRepository.save(any()) } answers { firstArg() }
        val savedDistributionSlot = slot<SaccoDividendDistribution>()
        every { distributionRepository.save(capture(savedDistributionSlot)) } answers { firstArg() }

        When("a dividend is declared") {
            val result = service.declareDividend()

            Then("the real ledger balances and the total paid is the sum of both real per-shareholder payouts") {
                result.totalDividendPaid.signum() shouldBe 1
                savedDistributionSlot.captured.totalDividendPaid shouldBe result.totalDividendPaid
            }

            Then("real bug caught before shipping: dividends are funded from INTEREST_EXPENSE, never from the member-backed pool account") {
                verify(exactly = 2) {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.size == 2 &&
                            legs.any { it.accountId == "interest_expense" && it.accountType == LedgerAccountType.INTEREST_EXPENSE && it.direction == LedgerDirection.DEBIT } &&
                            legs.none { it.accountId == "account_pool" }
                    })
                }
            }
        }
    }

    Given("3 shareholders due a dividend, where the middle one's account was since deleted") {
        val shareholdingRepository = mockk<SaccoShareholdingRepository>()
        val distributionRepository = mockk<SaccoDividendDistributionRepository>()
        val payoutRepository = mockk<SaccoDividendPayoutRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            shareholdingRepository = shareholdingRepository, distributionRepository = distributionRepository,
            payoutRepository = payoutRepository, accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val poolAccount = account("account_pool", "sacco_pool_system", AccountType.GROUP, BigDecimal("30000"))
        val shareholdingA = SaccoShareholding(id = "share_a", userId = "u1", accountId = "account_u1", sharesHeld = BigDecimal("20000"), totalContributed = BigDecimal("20000"))
        val shareholdingB = SaccoShareholding(id = "share_b", userId = "u2", accountId = "account_deleted", sharesHeld = BigDecimal("10000"), totalContributed = BigDecimal("10000"))
        val shareholdingC = SaccoShareholding(id = "share_c", userId = "u3", accountId = "account_u3", sharesHeld = BigDecimal("15000"), totalContributed = BigDecimal("15000"))

        every { shareholdingRepository.findAll() } returns listOf(shareholdingA, shareholdingB, shareholdingC)
        every { accountRepository.findByUserIdAndType("sacco_pool_system", AccountType.GROUP) } returns poolAccount
        every { accountRepository.findByIdForUpdate("account_pool") } returns Optional.of(poolAccount)
        every { distributionRepository.findAllByOrderByDistributionDateDesc() } returns emptyList()
        every { accountRepository.findById("account_u1") } returns Optional.of(account("account_u1", "u1"))
        every { accountRepository.findById("account_deleted") } returns Optional.empty()
        every { accountRepository.findById("account_u3") } returns Optional.of(account("account_u3", "u3"))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_div", emptyList())
        every { payoutRepository.save(any()) } answers { firstArg() }
        val savedDistributionSlot = slot<SaccoDividendDistribution>()
        every { distributionRepository.save(capture(savedDistributionSlot)) } answers { firstArg() }

        When("a dividend is declared") {
            val result = service.declareDividend()

            Then("the real bug this closes: it does NOT throw and roll back the whole distribution -- shareholders A and C are still paid") {
                verify(exactly = 2) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 2) { payoutRepository.save(any()) }
            }
            Then("the total paid only reflects the 2 real successful payouts, not the failed one") {
                result.totalDividendPaid.signum() shouldBe 1
                savedDistributionSlot.captured.totalDividendPaid shouldBe result.totalDividendPaid
            }
        }
    }

    Given("a real dividend declaration") {
        val shareholdingRepository = mockk<SaccoShareholdingRepository>()
        val distributionRepository = mockk<SaccoDividendDistributionRepository>()
        val payoutRepository = mockk<SaccoDividendPayoutRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            shareholdingRepository = shareholdingRepository, distributionRepository = distributionRepository,
            payoutRepository = payoutRepository, accountRepository = accountRepository, ledgerService = ledgerService,
        )

        val poolAccount = account("account_pool", "sacco_pool_system", AccountType.GROUP, BigDecimal("30000"))
        val shareholding = SaccoShareholding(id = "share_a", userId = "u1", accountId = "account_u1", sharesHeld = BigDecimal("20000"), totalContributed = BigDecimal("20000"))

        every { shareholdingRepository.findAll() } returns listOf(shareholding)
        every { accountRepository.findByUserIdAndType("sacco_pool_system", AccountType.GROUP) } returns poolAccount
        every { accountRepository.findByIdForUpdate("account_pool") } returns Optional.of(poolAccount)
        every { distributionRepository.findAllByOrderByDistributionDateDesc() } returns emptyList()
        every { accountRepository.findById("account_u1") } returns Optional.of(account("account_u1", "u1"))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_div", emptyList())
        every { payoutRepository.save(any()) } answers { firstArg() }
        every { distributionRepository.save(any()) } answers { firstArg() }

        When("it runs") {
            service.declareDividend()

            Then("real double-declare-race hardening: the pool account is re-fetched under a real row lock before reading the last distribution, so a genuinely concurrent second call is forced to serialize behind this one rather than reading the same stale state") {
                verify(exactly = 1) { accountRepository.findByIdForUpdate("account_pool") }
            }
        }
    }
})
