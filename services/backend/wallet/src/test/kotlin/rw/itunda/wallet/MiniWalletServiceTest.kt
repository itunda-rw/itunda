package rw.itunda.wallet

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.wallet.AccountNumberGenerator
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

/** First test coverage for the real KakaoBank mini-style capped starter wallet. */
class MiniWalletServiceTest : BehaviorSpec({
    val rwandaZone = ZoneId.of("Africa/Kigali")

    fun wallet(id: String, userId: String, type: WalletType, balance: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = type, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    fun user(id: String, birthDate: LocalDate?) = User(
        id = id, phoneNumber = "+25078800${id.takeLast(4)}", firstName = "A", lastName = "B", passwordHash = "x",
        birthDate = birthDate,
    )

    Given("a real, age-eligible user opening a Mini wallet") {
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val userRepository = mockk<UserRepository>()
        val service = MiniWalletService(walletRepository, transactionRepository, ledgerService, userRepository, mockk<AccountNumberGenerator>(relaxed = true))

        When("they don't already have one") {
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MINI) } returns null
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_main", "user_1", WalletType.MAIN, "10000")
            every { userRepository.findById("user_1") } returns Optional.of(user("user_1", LocalDate.now().minusYears(15)))
            every { walletRepository.findByIdForUpdate("wallet_main") } returns Optional.of(wallet("wallet_main", "user_1", WalletType.MAIN, "10000"))
            every { walletRepository.save(any()) } answers { firstArg() }

            val result = service.openMiniWallet("user_1")

            Then("it real-creates a new zero-balance Mini wallet") {
                result.type shouldBe WalletType.MINI
                result.balance shouldBe BigDecimal.ZERO
            }
            // Real bug found live (2026-08-02) -- see openMiniWallet's own doc comment:
            // this asserts the actual fix mechanism, the same
            // "lock a different already-existing row" precedent this codebase already
            // establishes for a reject-if-already-exists check-then-CREATE race.
            Then("it real-locks the user's own MAIN wallet row before creating the MINI one") {
                verify(exactly = 1) { walletRepository.findByIdForUpdate("wallet_main") }
            }
        }

        // Real bug found live (2026-08-02) -- see openMiniWallet's own doc comment: two
        // concurrent openMiniWallet calls could both pass the unlocked MINI-existence
        // check at the top before either committed. This simulates the second caller's
        // view of the world AFTER the first caller has already locked and committed --
        // the exact real-world moment the fix's locked re-check exists to catch.
        When("a concurrent caller already created the MINI wallet while this call was blocked on the lock") {
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_main", "user_1", WalletType.MAIN, "10000")
            every { userRepository.findById("user_1") } returns Optional.of(user("user_1", LocalDate.now().minusYears(15)))
            val raceWinnerMiniWallet = wallet("wallet_mini_race_winner", "user_1", WalletType.MINI, "0")
            // First (unlocked, top-of-method) MINI check sees nothing yet; the SECOND
            // (locked, post-findByIdForUpdate) re-check is what real-observes the other
            // transaction's now-committed MINI row -- mockk's `returnsMany` models that
            // exact before/after ordering across the two real calls.
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MINI) } returnsMany listOf(null, raceWinnerMiniWallet)
            every { walletRepository.findByIdForUpdate("wallet_main") } returns Optional.of(wallet("wallet_main", "user_1", WalletType.MAIN, "10000"))

            val result = service.openMiniWallet("user_1")

            Then("it real-returns the other caller's real MINI wallet instead of creating a real duplicate") {
                result.id shouldBe "wallet_mini_race_winner"
                verify(exactly = 0) { walletRepository.save(any()) }
            }
        }

        When("they already have one") {
            val existing = wallet("wallet_mini_existing", "user_1", WalletType.MINI, "5000")
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MINI) } returns existing

            val result = service.openMiniWallet("user_1")

            Then("it real-returns the existing wallet, never a duplicate, without even checking age") {
                result.id shouldBe "wallet_mini_existing"
                verify(exactly = 0) { userRepository.findById(any()) }
            }
        }

        When("they have no real MAIN wallet at all") {
            every { walletRepository.findByUserIdAndType("user_2", WalletType.MINI) } returns null
            every { walletRepository.findByUserIdAndType("user_2", WalletType.MAIN) } returns null
            every { userRepository.findById("user_2") } returns Optional.of(user("user_2", LocalDate.now().minusYears(15)))

            Then("it throws WalletNotFoundException") {
                try {
                    service.openMiniWallet("user_2")
                    error("expected WalletNotFoundException")
                } catch (e: WalletNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("real age-eligibility gate (2026-07-28), KakaoBank's real sourced 만 7세~18세 window") {
        When("the account has no birth date on file at all") {
            Then("it throws MiniWalletBirthDateRequiredException before ever checking for a MAIN wallet") {
                val walletRepository = mockk<WalletRepository>()
                val userRepository = mockk<UserRepository>()
                val service = MiniWalletService(walletRepository, mockk(), mockk(), userRepository, mockk<AccountNumberGenerator>(relaxed = true))
                every { walletRepository.findByUserIdAndType("user_no_birth_date", WalletType.MINI) } returns null
                every { userRepository.findById("user_no_birth_date") } returns Optional.of(user("user_no_birth_date", null))
                try {
                    service.openMiniWallet("user_no_birth_date")
                    error("expected MiniWalletBirthDateRequiredException")
                } catch (e: MiniWalletBirthDateRequiredException) {
                    verify(exactly = 0) { walletRepository.findByUserIdAndType("user_no_birth_date", WalletType.MAIN) }
                }
            }
        }

        When("the account is younger than the real minimum age of 7") {
            Then("it throws MiniWalletAgeIneligibleException") {
                val walletRepository = mockk<WalletRepository>()
                val userRepository = mockk<UserRepository>()
                val service = MiniWalletService(walletRepository, mockk(), mockk(), userRepository, mockk<AccountNumberGenerator>(relaxed = true))
                every { walletRepository.findByUserIdAndType("user_too_young", WalletType.MINI) } returns null
                every { userRepository.findById("user_too_young") } returns Optional.of(user("user_too_young", LocalDate.now(rwandaZone).minusYears(6)))
                try {
                    service.openMiniWallet("user_too_young")
                    error("expected MiniWalletAgeIneligibleException")
                } catch (e: MiniWalletAgeIneligibleException) {
                    verify(exactly = 0) { walletRepository.findByUserIdAndType("user_too_young", WalletType.MAIN) }
                }
            }
        }

        When("the account is older than the real maximum age of 18") {
            Then("it throws MiniWalletAgeIneligibleException") {
                val walletRepository = mockk<WalletRepository>()
                val userRepository = mockk<UserRepository>()
                val service = MiniWalletService(walletRepository, mockk(), mockk(), userRepository, mockk<AccountNumberGenerator>(relaxed = true))
                every { walletRepository.findByUserIdAndType("user_too_old", WalletType.MINI) } returns null
                every { userRepository.findById("user_too_old") } returns Optional.of(user("user_too_old", LocalDate.now(rwandaZone).minusYears(19)))
                try {
                    service.openMiniWallet("user_too_old")
                    error("expected MiniWalletAgeIneligibleException")
                } catch (e: MiniWalletAgeIneligibleException) {
                    // expected
                }
            }
        }

        When("the account is exactly at the real minimum eligible age of 7") {
            Then("it real-opens the Mini wallet -- the boundary age is inclusive, not excluded") {
                val walletRepository = mockk<WalletRepository>()
                val userRepository = mockk<UserRepository>()
                val service = MiniWalletService(walletRepository, mockk(), mockk(), userRepository, mockk<AccountNumberGenerator>(relaxed = true))
                every { walletRepository.findByUserIdAndType("user_minimum_age", WalletType.MINI) } returns null
                every { userRepository.findById("user_minimum_age") } returns Optional.of(user("user_minimum_age", LocalDate.now(rwandaZone).minusYears(7)))
                every { walletRepository.findByUserIdAndType("user_minimum_age", WalletType.MAIN) } returns wallet("wallet_main", "user_minimum_age", WalletType.MAIN, "10000")
                every { walletRepository.findByIdForUpdate("wallet_main") } returns Optional.of(wallet("wallet_main", "user_minimum_age", WalletType.MAIN, "10000"))
                every { walletRepository.save(any()) } answers { firstArg() }

                val result = service.openMiniWallet("user_minimum_age")
                result.type shouldBe WalletType.MINI
            }
        }
    }

    Given("a real user with an open Mini wallet, depositing from their real MAIN wallet") {
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val userRepository = mockk<UserRepository>()
        val service = MiniWalletService(walletRepository, transactionRepository, ledgerService, userRepository, mockk<AccountNumberGenerator>(relaxed = true))

        val mainWallet = wallet("wallet_main", "user_1", WalletType.MAIN, "1000000")
        val miniWallet = wallet("wallet_mini", "user_1", WalletType.MINI, "0")

        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns mainWallet
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MINI) } returns miniWallet
        every { transactionRepository.save(any()) } answers { firstArg() }

        When("depositing a real amount within all three real caps") {
            every { transactionRepository.sumAmountByToWalletIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(any(), any(), any(), any()) } returns BigDecimal.ZERO
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())

            service.deposit("user_1", BigDecimal("50000"))

            Then("it real-debits MAIN and real-credits MINI for the exact amount") {
                val legs = legsSlot.captured
                legs.first { it.accountId == "wallet_main" }.amount shouldBe BigDecimal("50000")
                legs.first { it.accountId == "wallet_mini" }.amount shouldBe BigDecimal("50000")
            }
        }

        When("the deposit would push the real balance over the real 500,000 RWF cap") {
            val fullMiniWallet = wallet("wallet_mini", "user_1", WalletType.MINI, "480000")
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MINI) } returns fullMiniWallet

            Then("it throws MiniWalletBalanceCapExceededException before ever touching the ledger") {
                try {
                    service.deposit("user_1", BigDecimal("30000"))
                    error("expected MiniWalletBalanceCapExceededException")
                } catch (e: MiniWalletBalanceCapExceededException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("today's real deposits already sit right at the real 300,000 RWF daily limit") {
            every { transactionRepository.sumAmountByToWalletIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(any(), any(), any(), any()) } returns BigDecimal("300000")

            Then("it throws MiniWalletDailyLimitExceededException before ever touching the ledger") {
                try {
                    service.deposit("user_1", BigDecimal("1"))
                    error("expected MiniWalletDailyLimitExceededException")
                } catch (e: MiniWalletDailyLimitExceededException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a non-positive amount is submitted") {
            Then("it throws InvalidMiniWalletDepositAmountException before ever looking up a wallet") {
                try {
                    service.deposit("user_1", BigDecimal.ZERO)
                    error("expected InvalidMiniWalletDepositAmountException")
                } catch (e: InvalidMiniWalletDepositAmountException) {
                    verify(exactly = 0) { walletRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }

    Given("a real user with no open Mini wallet trying to deposit") {
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val userRepository = mockk<UserRepository>()
        val service = MiniWalletService(walletRepository, transactionRepository, ledgerService, userRepository, mockk<AccountNumberGenerator>(relaxed = true))

        every { walletRepository.findByUserIdAndType("user_3", WalletType.MAIN) } returns wallet("wallet_main", "user_3", WalletType.MAIN, "10000")
        every { walletRepository.findByUserIdAndType("user_3", WalletType.MINI) } returns null

        When("they try to deposit anyway") {
            Then("it throws WalletNotFoundException") {
                try {
                    service.deposit("user_3", BigDecimal("100"))
                    error("expected WalletNotFoundException")
                } catch (e: WalletNotFoundException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
