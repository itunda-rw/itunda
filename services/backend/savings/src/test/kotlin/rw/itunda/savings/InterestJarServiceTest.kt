package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * Extracted out of SavingsServiceTest.kt (2026-09-03, first crossing of the 500-line
 * file-size guideline) -- see InterestJarService.kt's own doc comment for the full
 * "why" behind the split.
 */
class InterestJarServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.MAIN, balance: BigDecimal = BigDecimal("100000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = balance, availableBalance = balance,
    )

    Given("a user's interest jar") {
        val accountRepository = mockk<AccountRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        // Real mockk gotcha (carried over from SavingsServiceTest.kt's own identical
        // comment): JpaRepository's generic `<S extends T> S save(S entity)` return type
        // defeats relaxed auto-mocking.
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val service = InterestJarService(accountRepository, interestJarRepository, ledgerService, transactionRepository, notificationRepository, pushNotificationService, ledgerEntryRepository)

        When("claiming available interest") {
            val jar = InterestJar(
                userId = "user_1", accountId = "account_1", balance = BigDecimal("45000"), rate = 7.5,
                earnedThisMonth = BigDecimal("2500"), earnedTotal = BigDecimal("45000"),
            )
            every { interestJarRepository.findById("user_1") } returns Optional.of(jar)
            every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1"))
            every { interestJarRepository.save(any()) } answers { firstArg() }

            val result = service.claimInterest("user_1")

            // Real fix (2026-08-11): interest now auto-credits the account the instant
            // it accrues (see accrueInterest's own doc comment) -- claimInterest no
            // longer posts a second ledger transaction for money that already
            // arrived, it just clears the running display counter.
            Then("it reports the earned amount and zeroes the jar's display counter, without posting a second ledger credit") {
                result["claimed"] shouldBe BigDecimal("2500")
                jar.earnedThisMonth shouldBe BigDecimal.ZERO
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("claiming interest with nothing earned this month") {
            val jar = InterestJar(
                userId = "user_1", accountId = "account_1", balance = BigDecimal("45000"), rate = 7.5,
                earnedThisMonth = BigDecimal.ZERO, earnedTotal = BigDecimal("45000"),
            )
            every { interestJarRepository.findById("user_1") } returns Optional.of(jar)

            Then("it throws NoInterestAvailableException rather than paying out zero") {
                try {
                    service.claimInterest("user_1")
                    error("expected NoInterestAvailableException")
                } catch (e: NoInterestAvailableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a second claim immediately follows a successful one (double-claim attempt)") {
            val jar = InterestJar(
                userId = "user_1", accountId = "account_1", balance = BigDecimal("45000"), rate = 7.5,
                earnedThisMonth = BigDecimal("2500"), earnedTotal = BigDecimal("45000"),
            )
            every { interestJarRepository.findById("user_1") } returns Optional.of(jar)
            every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1"))
            every { interestJarRepository.save(any()) } answers { firstArg() }
            service.claimInterest("user_1")

            Then("the second claim throws NoInterestAvailableException since the jar was already zeroed") {
                try {
                    service.claimInterest("user_1")
                    error("expected NoInterestAvailableException")
                } catch (e: NoInterestAvailableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("interest jars due for real daily accrual") {
        val accountRepository = mockk<AccountRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        // See the earlier "a user's interest jar" Given block's own identical comment --
        // relaxed mockk can't synthesize a valid return for JpaRepository's generic
        // save(), and this stub must live at the Given level (IsolationMode.InstancePerLeaf
        // only re-walks the path to each leaf).
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val service = InterestJarService(accountRepository, interestJarRepository, ledgerService, transactionRepository, notificationRepository, pushNotificationService, ledgerEntryRepository)

        fun jar(userId: String, nextPayoutAt: Instant, rate: Double = 7.5) = InterestJar(
            userId = userId, accountId = "account_$userId", balance = BigDecimal.ZERO, rate = rate,
            earnedThisMonth = BigDecimal.ZERO, earnedTotal = BigDecimal.ZERO,
            lastPaidAt = Instant.now(), nextPayoutAt = nextPayoutAt,
        )

        When("finding what's due") {
            val overdue = jar("user_overdue", Instant.now().minus(2, java.time.temporal.ChronoUnit.DAYS))
            val notYet = jar("user_notyet", Instant.now().plus(20, java.time.temporal.ChronoUnit.HOURS))
            every { interestJarRepository.findAll() } returns listOf(overdue, notYet)

            val due = service.getJarsDueForAccrual()

            Then("only the jar whose real day has actually elapsed qualifies") {
                due.map { it.userId } shouldBe listOf("user_overdue")
            }
        }

        When("accruing interest for a jar backed by a real nonzero savings balance") {
            // Real bug found live (2026-09-14, BigDecimal scale-sensitivity sweep):
            // earnedTotal is a real DECIMAL(18,2) column, so every jar this method is
            // EVER called with in production comes back from Hibernate at scale 2
            // ("0.00"), never the bare scale-0 BigDecimal.ZERO the shared jar() helper
            // above constructs in-memory -- using the helper's default here would have
            // kept masking the exact "==" vs "compareTo" scale bug this Then block
            // exists to catch (the original `==` comparison happened to pass against
            // this file's own scale-0 test data despite being structurally broken
            // against real, scale-2 production data).
            val theJar = jar("user_1", Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS)).also { it.earnedTotal = BigDecimal("0.00") }
            every { accountRepository.findById("account_user_1") } returns Optional.of(account("account_user_1", "user_1", AccountType.SAVINGS, BigDecimal("36500")))
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_interest_1", emptyList())
            every { interestJarRepository.save(any()) } answers { firstArg() }
            every { transactionRepository.save(any()) } answers { firstArg() }

            service.accrueInterest(theJar)

            Then("it grows earnedThisMonth/earnedTotal off the real account balance, syncs the cached balance, and advances a real day") {
                // 36500 * 7.5% / 365 = 7.50 per day
                theJar.earnedThisMonth shouldBe BigDecimal("7.50")
                theJar.earnedTotal shouldBe BigDecimal("7.50")
                theJar.balance shouldBe BigDecimal("36500")
                verify(exactly = 1) { interestJarRepository.save(theJar) }
            }

            // Real fix (2026-08-11): interest now credits the real account balance the
            // instant it accrues, matching real Toss Bank passbook interest (user-
            // provided screenshots) posting as its own real transaction-history line
            // item, not a separate manually-claimed jar. Replaces the old "unclaimed
            // interest nudge" behavior this same accrual path used to trigger --
            // there's nothing left unclaimed to nudge about anymore.
            Then("it posts a real double-entry ledger credit and a real transaction-history row, not just a display counter") {
                verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", any()) }
                verify(exactly = 1) {
                    transactionRepository.save(match { it.type == TransactionType.INTEREST && it.recipientId == "user_1" && it.amount == BigDecimal("7.50") })
                }
            }

            Then("a real celebratory notification and push fire once for this jar's first-ever accrual") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "FIRST_INTEREST_ACCRUAL" && it.userId == "user_1" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any(), "FIRST_INTEREST_ACCRUAL") }
            }
        }

        When("accruing interest for a jar whose savings account is still empty") {
            val originalNextPayoutAt = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS)
            val theJar = jar("user_2", originalNextPayoutAt)
            every { accountRepository.findById("account_user_2") } returns Optional.of(account("account_user_2", "user_2", AccountType.SAVINGS, BigDecimal.ZERO))
            every { interestJarRepository.save(any()) } answers { firstArg() }

            service.accrueInterest(theJar)

            Then("no interest accrues on a zero balance, but the payout window still advances -- no stuck jar") {
                theJar.earnedThisMonth shouldBe BigDecimal.ZERO
                theJar.earnedTotal shouldBe BigDecimal.ZERO
                theJar.nextPayoutAt shouldBe originalNextPayoutAt.plus(1, java.time.temporal.ChronoUnit.DAYS)
            }

            Then("no celebratory push fires when nothing was actually earned") {
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any(), "FIRST_INTEREST_ACCRUAL") }
            }
        }

        When("accruing across repeated real days keeps building on top of the running total") {
            val theJar = jar("user_3", Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
            every { accountRepository.findById("account_user_3") } returns Optional.of(account("account_user_3", "user_3", AccountType.SAVINGS, BigDecimal("36500")))
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_interest_3", emptyList())
            every { interestJarRepository.save(any()) } answers { firstArg() }
            every { transactionRepository.save(any()) } answers { firstArg() }

            service.accrueInterest(theJar)
            service.accrueInterest(theJar)

            Then("two real days of accrual add up rather than overwrite") {
                theJar.earnedThisMonth shouldBe BigDecimal("15.00")
                theJar.earnedTotal shouldBe BigDecimal("15.00")
            }

            Then("each real day posts its own real ledger credit and transaction row -- two real days, two real postings") {
                verify(exactly = 2) { ledgerService.postLedgerTransaction("RWF", any()) }
                verify(exactly = 2) { transactionRepository.save(match { it.type == TransactionType.INTEREST }) }
            }

            Then("the celebratory push fires only once -- day 2 already has a nonzero earnedTotal, not a first accrual") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_3", any(), any(), any(), "FIRST_INTEREST_ACCRUAL") }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
