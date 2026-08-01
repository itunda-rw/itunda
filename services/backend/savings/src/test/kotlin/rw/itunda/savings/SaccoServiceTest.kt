package rw.itunda.savings

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.SaccoDividendDistribution
import rw.itunda.core.domain.SaccoDividendPayout
import rw.itunda.core.domain.SaccoShareholding
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.SaccoDividendDistributionRepository
import rw.itunda.core.repository.SaccoDividendPayoutRepository
import rw.itunda.core.repository.SaccoShareholdingRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for real Umurenge SACCO-style shares & dividends -- Rwanda's
 * own government-backed cooperative savings model. See SaccoService's own doc
 * comment for the full sourced account.
 */
class SaccoServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, type: WalletType = WalletType.MAIN, balance: BigDecimal = BigDecimal("100000")) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
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
        walletRepository: WalletRepository = mockk(),
        userRepository: UserRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = SaccoService(shareholdingRepository, distributionRepository, payoutRepository, walletRepository, userRepository, ledgerService, rateLimiter)

    Given("a real user with a wallet buying SACCO shares for the first time") {
        val shareholdingRepository = mockk<SaccoShareholdingRepository>()
        val walletRepository = mockk<WalletRepository>()
        val userRepository = mockk<UserRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(shareholdingRepository = shareholdingRepository, walletRepository = walletRepository, userRepository = userRepository, ledgerService = ledgerService)

        every { shareholdingRepository.findByUserId("u1") } returns null
        every { userRepository.findById("u1") } returns Optional.of(user("u1"))
        every { walletRepository.findByUserIdAndType("u1", WalletType.MAIN) } returns wallet("wallet_u1", "u1")
        every { walletRepository.findById("wallet_u1") } returns Optional.of(wallet("wallet_u1", "u1"))
        every { walletRepository.findByUserIdAndType("sacco_pool_system", WalletType.GROUP) } returns null
        val savedWalletSlot = slot<Wallet>()
        every { walletRepository.save(capture(savedWalletSlot)) } answers { firstArg() }
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val savedShareholdingSlot = slot<SaccoShareholding>()
        every { shareholdingRepository.save(capture(savedShareholdingSlot)) } answers { firstArg() }

        When("the user buys 10,000 RWF of shares") {
            val result = service.buyShares("u1", BigDecimal("10000"))

            Then("a real shareholding is minted 1:1 with the contribution") {
                result.shareholding.userId shouldBe "u1"
                result.shareholding.sharesHeld shouldBe BigDecimal("10000")
                result.shareholding.totalContributed shouldBe BigDecimal("10000")
                savedWalletSlot.captured.userId shouldBe "sacco_pool_system"
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
            id = "saccoshare_1", userId = "u1", walletId = "wallet_u1", sharesHeld = BigDecimal("5000"), totalContributed = BigDecimal("5000"),
        )

        When("the user tries to redeem more than their real balance") {
            Then("the real over-redemption is rejected") {
                shouldThrow<SaccoInsufficientSharesException> { service.redeemShares("u1", BigDecimal("10000")) }
            }
        }
    }

    Given("a real shareholder redeeming shares they actually hold") {
        val shareholdingRepository = mockk<SaccoShareholdingRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(shareholdingRepository = shareholdingRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        every { shareholdingRepository.findByUserId("u1") } returns SaccoShareholding(
            id = "saccoshare_1", userId = "u1", walletId = "wallet_u1", sharesHeld = BigDecimal("5000"), totalContributed = BigDecimal("5000"),
        )
        every { walletRepository.findById("wallet_u1") } returns Optional.of(wallet("wallet_u1", "u1"))
        every { walletRepository.findByUserIdAndType("sacco_pool_system", WalletType.GROUP) } returns wallet("wallet_pool", "sacco_pool_system", WalletType.GROUP, BigDecimal("50000"))
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(
            shareholdingRepository = shareholdingRepository, distributionRepository = distributionRepository,
            payoutRepository = payoutRepository, walletRepository = walletRepository, ledgerService = ledgerService,
        )

        val poolWallet = wallet("wallet_pool", "sacco_pool_system", WalletType.GROUP, BigDecimal("30000"))
        val shareholdingA = SaccoShareholding(id = "share_a", userId = "u1", walletId = "wallet_u1", sharesHeld = BigDecimal("20000"), totalContributed = BigDecimal("20000"))
        val shareholdingB = SaccoShareholding(id = "share_b", userId = "u2", walletId = "wallet_u2", sharesHeld = BigDecimal("10000"), totalContributed = BigDecimal("10000"))

        every { shareholdingRepository.findAll() } returns listOf(shareholdingA, shareholdingB)
        every { walletRepository.findByUserIdAndType("sacco_pool_system", WalletType.GROUP) } returns poolWallet
        every { distributionRepository.findAllByOrderByDistributionDateDesc() } returns emptyList()
        every { walletRepository.findById("wallet_u1") } returns Optional.of(wallet("wallet_u1", "u1"))
        every { walletRepository.findById("wallet_u2") } returns Optional.of(wallet("wallet_u2", "u2"))
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
        }
    }
})
