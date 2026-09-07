package rw.itunda.account

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

/** First test coverage for the real KakaoBank mini-style capped starter account. */
class YouthAccountServiceTest : BehaviorSpec({
    val rwandaZone = ZoneId.of("Africa/Kigali")

    fun account(id: String, userId: String, type: AccountType, balance: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    fun user(id: String, birthDate: LocalDate?) = User(
        id = id, phoneNumber = "+25078800${id.takeLast(4)}", firstName = "A", lastName = "B", passwordHash = "x",
        birthDate = birthDate,
    )

    Given("a real, age-eligible user opening a Youth account") {
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val userRepository = mockk<UserRepository>()
        val service = YouthAccountService(accountRepository, transactionRepository, ledgerService, userRepository, mockk<AccountNumberGenerator>(relaxed = true), mockk(relaxed = true))

        When("they don't already have one") {
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MINI) } returns null
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1", AccountType.MAIN, "10000")
            every { userRepository.findById("user_1") } returns Optional.of(user("user_1", LocalDate.now().minusYears(15)))
            every { accountRepository.findByIdForUpdate("account_main") } returns Optional.of(account("account_main", "user_1", AccountType.MAIN, "10000"))
            every { accountRepository.save(any()) } answers { firstArg() }

            val result = service.openYouthAccount("user_1")

            Then("it real-creates a new zero-balance Youth account") {
                result.type shouldBe AccountType.MINI
                result.balance shouldBe BigDecimal.ZERO
            }
            // Real bug found live (2026-08-02) -- see openYouthAccount's own doc comment:
            // this asserts the actual fix mechanism, the same
            // "lock a different already-existing row" precedent this codebase already
            // establishes for a reject-if-already-exists check-then-CREATE race.
            Then("it real-locks the user's own MAIN account row before creating the MINI one") {
                verify(exactly = 1) { accountRepository.findByIdForUpdate("account_main") }
            }
        }

        // Real bug found live (2026-08-02) -- see openYouthAccount's own doc comment: two
        // concurrent openYouthAccount calls could both pass the unlocked MINI-existence
        // check at the top before either committed. This simulates the second caller's
        // view of the world AFTER the first caller has already locked and committed --
        // the exact real-world moment the fix's locked re-check exists to catch.
        When("a concurrent caller already created the MINI account while this call was blocked on the lock") {
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1", AccountType.MAIN, "10000")
            every { userRepository.findById("user_1") } returns Optional.of(user("user_1", LocalDate.now().minusYears(15)))
            val raceWinnerYouthAccount = account("account_mini_race_winner", "user_1", AccountType.MINI, "0")
            // First (unlocked, top-of-method) MINI check sees nothing yet; the SECOND
            // (locked, post-findByIdForUpdate) re-check is what real-observes the other
            // transaction's now-committed MINI row -- mockk's `returnsMany` models that
            // exact before/after ordering across the two real calls.
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MINI) } returnsMany listOf(null, raceWinnerYouthAccount)
            every { accountRepository.findByIdForUpdate("account_main") } returns Optional.of(account("account_main", "user_1", AccountType.MAIN, "10000"))

            val result = service.openYouthAccount("user_1")

            Then("it real-returns the other caller's real MINI account instead of creating a real duplicate") {
                result.id shouldBe "account_mini_race_winner"
                verify(exactly = 0) { accountRepository.save(any()) }
            }
        }

        When("they already have one") {
            val existing = account("account_mini_existing", "user_1", AccountType.MINI, "5000")
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MINI) } returns existing

            val result = service.openYouthAccount("user_1")

            Then("it real-returns the existing account, never a duplicate, without even checking age") {
                result.id shouldBe "account_mini_existing"
                verify(exactly = 0) { userRepository.findById(any()) }
            }
        }

        When("they have no real MAIN account at all") {
            every { accountRepository.findByUserIdAndType("user_2", AccountType.MINI) } returns null
            every { accountRepository.findByUserIdAndType("user_2", AccountType.MAIN) } returns null
            every { userRepository.findById("user_2") } returns Optional.of(user("user_2", LocalDate.now().minusYears(15)))

            Then("it throws AccountNotFoundException") {
                try {
                    service.openYouthAccount("user_2")
                    error("expected AccountNotFoundException")
                } catch (e: AccountNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("real age-eligibility gate (2026-07-28), KakaoBank's real sourced 만 7세~18세 window") {
        When("the account has no birth date on file at all") {
            Then("it throws YouthAccountBirthDateRequiredException before ever checking for a MAIN account") {
                val accountRepository = mockk<AccountRepository>()
                val userRepository = mockk<UserRepository>()
                val service = YouthAccountService(accountRepository, mockk(), mockk(), userRepository, mockk<AccountNumberGenerator>(relaxed = true), mockk(relaxed = true))
                every { accountRepository.findByUserIdAndType("user_no_birth_date", AccountType.MINI) } returns null
                every { userRepository.findById("user_no_birth_date") } returns Optional.of(user("user_no_birth_date", null))
                try {
                    service.openYouthAccount("user_no_birth_date")
                    error("expected YouthAccountBirthDateRequiredException")
                } catch (e: YouthAccountBirthDateRequiredException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType("user_no_birth_date", AccountType.MAIN) }
                }
            }
        }

        When("the account is younger than the real minimum age of 7") {
            Then("it throws YouthAccountAgeIneligibleException") {
                val accountRepository = mockk<AccountRepository>()
                val userRepository = mockk<UserRepository>()
                val service = YouthAccountService(accountRepository, mockk(), mockk(), userRepository, mockk<AccountNumberGenerator>(relaxed = true), mockk(relaxed = true))
                every { accountRepository.findByUserIdAndType("user_too_young", AccountType.MINI) } returns null
                every { userRepository.findById("user_too_young") } returns Optional.of(user("user_too_young", LocalDate.now(rwandaZone).minusYears(6)))
                try {
                    service.openYouthAccount("user_too_young")
                    error("expected YouthAccountAgeIneligibleException")
                } catch (e: YouthAccountAgeIneligibleException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType("user_too_young", AccountType.MAIN) }
                }
            }
        }

        When("the account is older than the real maximum age of 18") {
            Then("it throws YouthAccountAgeIneligibleException") {
                val accountRepository = mockk<AccountRepository>()
                val userRepository = mockk<UserRepository>()
                val service = YouthAccountService(accountRepository, mockk(), mockk(), userRepository, mockk<AccountNumberGenerator>(relaxed = true), mockk(relaxed = true))
                every { accountRepository.findByUserIdAndType("user_too_old", AccountType.MINI) } returns null
                every { userRepository.findById("user_too_old") } returns Optional.of(user("user_too_old", LocalDate.now(rwandaZone).minusYears(19)))
                try {
                    service.openYouthAccount("user_too_old")
                    error("expected YouthAccountAgeIneligibleException")
                } catch (e: YouthAccountAgeIneligibleException) {
                    // expected
                }
            }
        }

        When("the account is exactly at the real minimum eligible age of 7") {
            Then("it real-opens the Youth account -- the boundary age is inclusive, not excluded") {
                val accountRepository = mockk<AccountRepository>()
                val userRepository = mockk<UserRepository>()
                val service = YouthAccountService(accountRepository, mockk(), mockk(), userRepository, mockk<AccountNumberGenerator>(relaxed = true), mockk(relaxed = true))
                every { accountRepository.findByUserIdAndType("user_minimum_age", AccountType.MINI) } returns null
                every { userRepository.findById("user_minimum_age") } returns Optional.of(user("user_minimum_age", LocalDate.now(rwandaZone).minusYears(7)))
                every { accountRepository.findByUserIdAndType("user_minimum_age", AccountType.MAIN) } returns account("account_main", "user_minimum_age", AccountType.MAIN, "10000")
                every { accountRepository.findByIdForUpdate("account_main") } returns Optional.of(account("account_main", "user_minimum_age", AccountType.MAIN, "10000"))
                every { accountRepository.save(any()) } answers { firstArg() }

                val result = service.openYouthAccount("user_minimum_age")
                result.type shouldBe AccountType.MINI
            }
        }
    }

    Given("a real user with an open Youth account, depositing from their real MAIN account") {
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val userRepository = mockk<UserRepository>()
        val service = YouthAccountService(accountRepository, transactionRepository, ledgerService, userRepository, mockk<AccountNumberGenerator>(relaxed = true), mockk(relaxed = true))

        val mainAccount = account("account_main", "user_1", AccountType.MAIN, "1000000")
        val miniAccount = account("account_mini", "user_1", AccountType.MINI, "0")

        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns mainAccount
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MINI) } returns miniAccount
        every { accountRepository.findByIdForUpdate(any()) } answers { java.util.Optional.of(miniAccount) }
        every { transactionRepository.save(any()) } answers { firstArg() }

        When("depositing a real amount within all three real caps") {
            every { transactionRepository.sumAmountByToAccountIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(any(), any(), any(), any()) } returns BigDecimal.ZERO
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())

            service.deposit("user_1", BigDecimal("50000"))

            Then("it real-debits MAIN and real-credits MINI for the exact amount") {
                val legs = legsSlot.captured
                legs.first { it.accountId == "account_main" }.amount shouldBe BigDecimal("50000")
                legs.first { it.accountId == "account_mini" }.amount shouldBe BigDecimal("50000")
            }

            // Real concurrency-audit regression (same "coarse repo filter, live SUM(),
            // no lock" shape CardService.chargeWithCard/P2pTransferLimitService.enforce/
            // FamilyLinkService.enforceSpendLimit already fixed): proves the youth
            // account row is actually locked before the daily/monthly SUM() checks, not
            // just that the checks happen.
            Then("it locks the youth account row before checking the daily/monthly caps") {
                verify { accountRepository.findByIdForUpdate("account_mini") }
            }
        }

        When("the deposit would push the real balance over the real 500,000 RWF cap") {
            val fullYouthAccount = account("account_mini", "user_1", AccountType.MINI, "480000")
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MINI) } returns fullYouthAccount

            Then("it throws YouthAccountBalanceCapExceededException before ever touching the ledger") {
                try {
                    service.deposit("user_1", BigDecimal("30000"))
                    error("expected YouthAccountBalanceCapExceededException")
                } catch (e: YouthAccountBalanceCapExceededException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("today's real deposits already sit right at the real 300,000 RWF daily limit") {
            every { transactionRepository.sumAmountByToAccountIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(any(), any(), any(), any()) } returns BigDecimal("300000")

            Then("it throws YouthAccountDailyLimitExceededException before ever touching the ledger") {
                try {
                    service.deposit("user_1", BigDecimal("1"))
                    error("expected YouthAccountDailyLimitExceededException")
                } catch (e: YouthAccountDailyLimitExceededException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a non-positive amount is submitted") {
            Then("it throws InvalidYouthAccountDepositAmountException before ever looking up a account") {
                try {
                    service.deposit("user_1", BigDecimal.ZERO)
                    error("expected InvalidYouthAccountDepositAmountException")
                } catch (e: InvalidYouthAccountDepositAmountException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }

    Given("a real user with no open Youth account trying to deposit") {
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val userRepository = mockk<UserRepository>()
        val service = YouthAccountService(accountRepository, transactionRepository, ledgerService, userRepository, mockk<AccountNumberGenerator>(relaxed = true), mockk(relaxed = true))

        every { accountRepository.findByUserIdAndType("user_3", AccountType.MAIN) } returns account("account_main", "user_3", AccountType.MAIN, "10000")
        every { accountRepository.findByUserIdAndType("user_3", AccountType.MINI) } returns null

        When("they try to deposit anyway") {
            Then("it throws AccountNotFoundException") {
                try {
                    service.deposit("user_3", BigDecimal("100"))
                    error("expected AccountNotFoundException")
                } catch (e: AccountNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real caller who has already exceeded a real Youth account deposit rate limit") {
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val userRepository = mockk<UserRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val service = YouthAccountService(accountRepository, transactionRepository, ledgerService, userRepository, mockk<AccountNumberGenerator>(relaxed = true), rateLimiter)
        every { rateLimiter.checkLimit("youth-account:deposit:user_1", limit = 30, window = any()) } throws RateLimitExceededException("Too many requests")

        When("depositing") {
            Then("a real RateLimitExceededException fires before ever touching the real account row") {
                try {
                    service.deposit("user_1", BigDecimal("1000"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
