package rw.itunda.loans

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.creditscore.CreditScoreResult
import rw.itunda.core.creditscore.CreditScoreService
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.OverdraftAccount
import rw.itunda.core.domain.OverdraftAccountStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.OverdraftAccountRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import org.springframework.transaction.support.TransactionSynchronizationManager

/** First test coverage for the real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving
 * line-of-credit) -- see OverdraftAccount.kt's own doc comment for the full sourced
 * account, especially the real revolving-vs-lump-sum distinction from LoansService. */
class OverdraftServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real user applying to open a real overdraft account") {
        val overdraftAccountRepository = mockk<OverdraftAccountRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = OverdraftService(overdraftAccountRepository, accountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService, mockk(relaxed = true))

        every { overdraftAccountRepository.findByUserIdAndStatus("user_1", OverdraftAccountStatus.ACTIVE) } returns null
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        val savedSlot = mutableListOf<OverdraftAccount>()
        every { overdraftAccountRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("with a real qualifying score and a real valid limit") {
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(700, emptyList(), Instant.now())

            val account = service.openOverdraft("user_1", BigDecimal("100000"))

            Then("it real-opens a real zero-drawn ACTIVE account at exactly the requested limit") {
                account.creditLimit shouldBe BigDecimal("100000")
                account.drawnBalance shouldBe BigDecimal.ZERO
                account.status shouldBe OverdraftAccountStatus.ACTIVE
            }

            Then("the account owner also gets a real mobile push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Overdraft account opened", any(), any()) }
            }
        }

        When("with a real disqualifying low score") {
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(300, emptyList(), Instant.now())

            Then("it's real-declined before ever touching the account or saving anything") {
                try {
                    service.openOverdraft("user_1", BigDecimal("100000"))
                    error("expected OverdraftApplicationDeclinedException")
                } catch (e: OverdraftApplicationDeclinedException) {
                    verify(exactly = 0) { overdraftAccountRepository.save(any()) }
                }
            }
        }

        When("requesting a real limit above the real maximum") {
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(700, emptyList(), Instant.now())

            Then("it's real-rejected before ever checking the real credit score") {
                try {
                    service.openOverdraft("user_1", BigDecimal("999999999"))
                    error("expected OverdraftLimitInvalidException")
                } catch (e: OverdraftLimitInvalidException) {
                    verify(exactly = 0) { creditScoreService.computeScore(any()) }
                }
            }
        }
    }

    Given("a real user who already has a real active overdraft account") {
        val overdraftAccountRepository = mockk<OverdraftAccountRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = OverdraftService(overdraftAccountRepository, accountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService, mockk(relaxed = true))

        every { overdraftAccountRepository.findByUserIdAndStatus("user_1", OverdraftAccountStatus.ACTIVE) } returns
            OverdraftAccount(id = "overdraft_1", userId = "user_1", accountId = "account_1", creditLimit = BigDecimal("100000"), interestRate = 8.0)

        When("applying for a second real overdraft account") {
            Then("it's real-rejected -- only one real active overdraft per user") {
                try {
                    service.openOverdraft("user_1", BigDecimal("50000"))
                    error("expected OverdraftAlreadyActiveException")
                } catch (e: OverdraftAlreadyActiveException) {
                    // expected
                }
            }
        }
    }

    Given("a real user with a real active overdraft account, drawing and repaying") {
        val overdraftAccountRepository = mockk<OverdraftAccountRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = OverdraftService(overdraftAccountRepository, accountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService, mockk(relaxed = true))

        val account = OverdraftAccount(id = "overdraft_1", userId = "user_1", accountId = "account_1", creditLimit = BigDecimal("100000"), interestRate = 8.0)
        every { overdraftAccountRepository.findByUserIdAndStatus("user_1", OverdraftAccountStatus.ACTIVE) } returns account
        every { overdraftAccountRepository.findByIdForUpdate("overdraft_1") } returns Optional.of(account)
        every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1"))
        every { overdraftAccountRepository.save(any()) } answers { firstArg() }
        // Given-level, not per-When: IsolationMode.InstancePerLeaf reruns this whole
        // Given block fresh for every leaf, so a stub set inside one sibling When block
        // never applies to another -- a real gotcha caught by the full monorepo sweep
        // (draw's own capture below narrows this same stub further, only for that leaf).
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())

        When("drawing a real 30,000 RWF amount within the real limit") {
            val legsSlot = mutableListOf<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_draw_1", emptyList())

            val result = service.draw("user_1", BigDecimal("30000"))

            Then("it real-locks the account row before mutating it, not just an unlocked read") {
                verify(exactly = 1) { overdraftAccountRepository.findByIdForUpdate("overdraft_1") }
            }

            Then("it real-credits the account and real-increases the drawn balance") {
                account.drawnBalance shouldBe BigDecimal("30000")
                result["availableCredit"] shouldBe BigDecimal("70000")
                val creditLeg = legsSlot.first().first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "account_1"
                creditLeg.amount shouldBe BigDecimal("30000")
                val debitLeg = legsSlot.first().first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountType shouldBe LedgerAccountType.LOAN_PAYABLE
            }
        }

        When("drawing more than the real available credit") {
            account.drawnBalance = BigDecimal("30000")

            Then("it's real-rejected before touching the ledger") {
                try {
                    service.draw("user_1", BigDecimal("80000"))
                    error("expected OverdraftLimitExceededException")
                } catch (e: OverdraftLimitExceededException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("repaying part of the real drawn balance") {
            account.drawnBalance = BigDecimal("30000")

            val result = service.repay("user_1", BigDecimal("10000"))

            Then("it real-reduces the drawn balance and the account stays ACTIVE, never closing") {
                account.drawnBalance shouldBe BigDecimal("20000")
                account.status shouldBe OverdraftAccountStatus.ACTIVE
                result["availableCredit"] shouldBe BigDecimal("80000")
            }
        }

        When("repaying the real full drawn balance") {
            account.drawnBalance = BigDecimal("20000")

            service.repay("user_1", BigDecimal("20000"))

            Then("the real account stays ACTIVE at real zero drawn balance -- revolving, not closed like a term loan") {
                account.drawnBalance shouldBe BigDecimal.ZERO
                account.status shouldBe OverdraftAccountStatus.ACTIVE
            }
        }

        When("repaying more than the real drawn balance") {
            account.drawnBalance = BigDecimal("5000")

            val result = service.repay("user_1", BigDecimal("20000"))

            Then("it real-caps the repayment at the real drawn balance, never going negative") {
                account.drawnBalance shouldBe BigDecimal.ZERO
                result["amount"] shouldBe BigDecimal("5000")
            }
        }
    }

    Given("a real user with no real active overdraft account, trying to draw or repay") {
        val overdraftAccountRepository = mockk<OverdraftAccountRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = OverdraftService(overdraftAccountRepository, accountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService, mockk(relaxed = true))

        every { overdraftAccountRepository.findByUserIdAndStatus("user_2", OverdraftAccountStatus.ACTIVE) } returns null

        When("attempting to draw") {
            Then("it throws OverdraftNotActiveException") {
                try {
                    service.draw("user_2", BigDecimal("1000"))
                    error("expected OverdraftNotActiveException")
                } catch (e: OverdraftNotActiveException) {
                    // expected
                }
            }
        }
    }

    Given("a real caller who has already exceeded a real overdraft rate limit") {
        val overdraftAccountRepository = mockk<OverdraftAccountRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val service = OverdraftService(overdraftAccountRepository, accountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService, rateLimiter)

        When("drawing against a real overdraft account") {
            every { rateLimiter.checkLimit("overdraft:draw:user_1", limit = 30, window = any()) } throws RateLimitExceededException("Too many requests")

            Then("a real RateLimitExceededException fires before ever touching the real account row") {
                try {
                    service.draw("user_1", BigDecimal("1000"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { overdraftAccountRepository.findByUserIdAndStatus(any(), any()) }
                }
            }
        }
    }

    Given("a real overdraft account with a real nonzero drawn balance, due for real daily interest accrual") {
        val overdraftAccountRepository = mockk<OverdraftAccountRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = OverdraftService(overdraftAccountRepository, accountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService, mockk(relaxed = true))

        val account = OverdraftAccount(id = "overdraft_1", userId = "user_1", accountId = "account_1", creditLimit = BigDecimal("100000"), interestRate = 8.0, drawnBalance = BigDecimal("36500"))
        every { overdraftAccountRepository.findByIdForUpdate("overdraft_1") } returns Optional.of(account)
        every { overdraftAccountRepository.save(any()) } answers { firstArg() }

        When("accrueInterest runs for one real day at a real 8% annual rate on a real 36,500 RWF balance") {
            val legsSlot = mutableListOf<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_interest_1", emptyList())

            service.accrueInterest(account)

            Then("it real-locks the account row before mutating it, not the detached scheduler-batch object directly") {
                verify(exactly = 1) { overdraftAccountRepository.findByIdForUpdate("overdraft_1") }
            }

            Then("it real-accrues exactly 8 RWF (36500 * 0.08 / 365) onto the real drawn balance") {
                account.drawnBalance shouldBe BigDecimal("36508.00")
                val debitLeg = legsSlot.first().first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountType shouldBe LedgerAccountType.LOAN_PAYABLE
                debitLeg.amount shouldBe BigDecimal("8.00")
                val creditLeg = legsSlot.first().first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountType shouldBe LedgerAccountType.INTEREST_INCOME
            }
        }
    }

    Given("real overdraft accounts of every real shape, checking which are due for real accrual") {
        val overdraftAccountRepository = mockk<OverdraftAccountRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = OverdraftService(overdraftAccountRepository, accountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService, mockk(relaxed = true))

        val neverAccrued = OverdraftAccount(id = "overdraft_a", userId = "user_a", accountId = "account_a", creditLimit = BigDecimal("100000"), interestRate = 8.0, drawnBalance = BigDecimal("10000"))
        val zeroBalance = OverdraftAccount(id = "overdraft_b", userId = "user_b", accountId = "account_b", creditLimit = BigDecimal("100000"), interestRate = 8.0, drawnBalance = BigDecimal.ZERO)
        val recentlyAccrued = OverdraftAccount(id = "overdraft_c", userId = "user_c", accountId = "account_c", creditLimit = BigDecimal("100000"), interestRate = 8.0, drawnBalance = BigDecimal("10000"), lastAccrualAt = Instant.now().minusSeconds(3600))
        every { overdraftAccountRepository.findByStatus(OverdraftAccountStatus.ACTIVE) } returns listOf(neverAccrued, zeroBalance, recentlyAccrued)

        When("getAccountsDueForAccrual runs") {
            val due = service.getAccountsDueForAccrual()

            Then("it real-includes only the never-yet-accrued nonzero-balance account -- honestly excluding a real zero balance and a too-recent accrual") {
                due shouldBe listOf(neverAccrued)
            }
        }
    }

    Given("an overdraft opening inside a transaction") {
        val overdraftAccountRepository = mockk<OverdraftAccountRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = OverdraftService(overdraftAccountRepository, accountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService, mockk(relaxed = true))
        every { overdraftAccountRepository.findByUserIdAndStatus("user_after_commit", OverdraftAccountStatus.ACTIVE) } returns null
        every { creditScoreService.computeScore("user_after_commit") } returns CreditScoreResult(700, emptyList(), Instant.now())
        every { accountRepository.findByUserIdAndType("user_after_commit", AccountType.MAIN) } returns account("account_after_commit", "user_after_commit")
        every { overdraftAccountRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        Then("the durable alert is saved, but its push waits for commit") {
            TransactionSynchronizationManager.initSynchronization()
            try {
                service.openOverdraft("user_after_commit", BigDecimal("100000"))
                verify(exactly = 1) { notificationRepository.save(any()) }
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }

                TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                verify(exactly = 1) { pushNotificationService.sendToUser("user_after_commit", any(), any(), any()) }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
