package rw.itunda.loans

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.creditscore.CreditScoreResult
import rw.itunda.core.creditscore.CreditScoreService
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.PostpaidCreditLine
import rw.itunda.core.domain.PostpaidCreditLineStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PostpaidCreditLineRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for the real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL
 * credit line) -- see PostpaidCreditLine.kt's own doc comment for the full sourced
 * account, especially the real structural distinction from OverdraftServiceTest's own
 * already-covered mechanics this file's own tests deliberately don't re-prove.
 */
class PostpaidCreditServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real user applying for a real postpaid credit line") {
        val postpaidCreditLineRepository = mockk<PostpaidCreditLineRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>()
        val service = PostpaidCreditService(
            postpaidCreditLineRepository, accountRepository, ledgerService, creditScoreService,
            notificationRepository, pushNotificationService, mockk(relaxed = true),
        )

        every { postpaidCreditLineRepository.findByUserId("user_1") } returns null
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        val savedSlot = mutableListOf<PostpaidCreditLine>()
        every { postpaidCreditLineRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("with a real brand-new account's own real base score (300)") {
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(300, emptyList(), Instant.now())

            val line = service.applyForPostpaidCredit("user_1")

            Then("it real-approves the real smallest starter tier -- BNPL's own defining 'everyone qualifies for something' bar, unlike loans/overdraft's real 400-point gate") {
                line.creditLimit shouldBe BigDecimal("20000")
                line.currentBalance shouldBe BigDecimal.ZERO
                line.status shouldBe PostpaidCreditLineStatus.ACTIVE
            }
        }

        When("with a real high score (700+)") {
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(750, emptyList(), Instant.now())

            val line = service.applyForPostpaidCredit("user_1")

            Then("it real-approves the real top tier, matching Naver Pay's own sourced 300,000 KRW maximum limit exactly") {
                line.creditLimit shouldBe BigDecimal("300000")
            }
        }
    }

    Given("a real user who already has a real postpaid credit line") {
        val postpaidCreditLineRepository = mockk<PostpaidCreditLineRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>()
        val service = PostpaidCreditService(
            postpaidCreditLineRepository, accountRepository, ledgerService, creditScoreService,
            notificationRepository, pushNotificationService, mockk(relaxed = true),
        )

        every { postpaidCreditLineRepository.findByUserId("user_1") } returns
            PostpaidCreditLine(id = "postpaid_1", userId = "user_1", accountId = "account_1", creditLimit = BigDecimal("100000"))

        When("applying for a second real line") {
            Then("it's real-rejected -- only one real line per user") {
                try {
                    service.applyForPostpaidCredit("user_1")
                    error("expected PostpaidCreditAlreadyOpenException")
                } catch (e: PostpaidCreditAlreadyOpenException) {
                    // expected
                }
            }
        }
    }

    Given("a real user with a real active postpaid credit line, spending and repaying") {
        val postpaidCreditLineRepository = mockk<PostpaidCreditLineRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>()
        val service = PostpaidCreditService(
            postpaidCreditLineRepository, accountRepository, ledgerService, creditScoreService,
            notificationRepository, pushNotificationService, mockk(relaxed = true),
        )

        val line = PostpaidCreditLine(id = "postpaid_1", userId = "user_1", accountId = "account_1", creditLimit = BigDecimal("100000"))
        every { postpaidCreditLineRepository.findByUserId("user_1") } returns line
        every { postpaidCreditLineRepository.findByIdForUpdate("postpaid_1") } returns Optional.of(line)
        every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1"))
        every { postpaidCreditLineRepository.save(any()) } answers { firstArg() }
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())

        When("spending a real 30,000 RWF within the real limit, starting a fresh billing cycle") {
            val legsSlot = mutableListOf<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_spend_1", emptyList())

            val result = service.spend("user_1", BigDecimal("30000"))

            Then("it real-locks the line row before mutating it, not just an unlocked read") {
                verify(exactly = 1) { postpaidCreditLineRepository.findByIdForUpdate("postpaid_1") }
            }

            Then("it real-credits the account, real-increases the balance, and real-opens a 30-day cycle due date") {
                line.currentBalance shouldBe BigDecimal("30000")
                (line.cycleDueAt != null) shouldBe true
                result["availableCredit"] shouldBe BigDecimal("70000")
                val creditLeg = legsSlot.first().first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "account_1"
                val debitLeg = legsSlot.first().first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountType shouldBe LedgerAccountType.POSTPAID_CREDIT_PAYABLE
            }
        }

        When("spending more than the real available credit") {
            line.currentBalance = BigDecimal("30000")

            Then("it's real-rejected before touching the ledger") {
                try {
                    service.spend("user_1", BigDecimal("80000"))
                    error("expected PostpaidCreditLimitExceededException")
                } catch (e: PostpaidCreditLimitExceededException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("repaying the real full balance") {
            line.currentBalance = BigDecimal("30000")
            line.cycleDueAt = Instant.now().plusSeconds(3600)
            line.status = PostpaidCreditLineStatus.SUSPENDED

            service.repay("user_1", BigDecimal("30000"))

            Then("it real-clears the balance, the real cycle due date, and real-restores an ACTIVE status") {
                line.currentBalance shouldBe BigDecimal.ZERO
                line.cycleDueAt shouldBe null
                line.status shouldBe PostpaidCreditLineStatus.ACTIVE
            }
        }

        When("repaying more than the real balance") {
            line.currentBalance = BigDecimal("5000")

            val result = service.repay("user_1", BigDecimal("20000"))

            Then("it real-caps the repayment at the real balance, never going negative") {
                line.currentBalance shouldBe BigDecimal.ZERO
                result["amount"] shouldBe BigDecimal("5000")
            }
        }
    }

    Given("a real caller who has already exceeded a real postpaid credit rate limit") {
        val postpaidCreditLineRepository = mockk<PostpaidCreditLineRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>()
        val rateLimiter = mockk<RateLimiter>()
        val service = PostpaidCreditService(
            postpaidCreditLineRepository, accountRepository, ledgerService, creditScoreService,
            notificationRepository, pushNotificationService, rateLimiter,
        )

        When("spending against a real postpaid credit line") {
            every { rateLimiter.checkLimit("postpaid-credit:spend:user_1", limit = 30, window = any()) } throws RateLimitExceededException("Too many requests")

            Then("a real RateLimitExceededException fires before ever touching the real line row") {
                try {
                    service.spend("user_1", BigDecimal("1000"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { postpaidCreditLineRepository.findByUserId(any()) }
                }
            }
        }
    }

    Given("a real SUSPENDED postpaid credit line, blocking further spend") {
        val postpaidCreditLineRepository = mockk<PostpaidCreditLineRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>()
        val service = PostpaidCreditService(
            postpaidCreditLineRepository, accountRepository, ledgerService, creditScoreService,
            notificationRepository, pushNotificationService, mockk(relaxed = true),
        )

        val line = PostpaidCreditLine(
            id = "postpaid_1", userId = "user_1", accountId = "account_1", creditLimit = BigDecimal("100000"),
            currentBalance = BigDecimal("30000"), status = PostpaidCreditLineStatus.SUSPENDED,
        )
        every { postpaidCreditLineRepository.findByUserId("user_1") } returns line
        every { postpaidCreditLineRepository.findByIdForUpdate("postpaid_1") } returns Optional.of(line)

        When("attempting to spend while real overdue") {
            Then("it's real-blocked, matching Naver Pay's own sourced 'service unusable while overdue' rule") {
                try {
                    service.spend("user_1", BigDecimal("1000"))
                    error("expected PostpaidCreditSuspendedException")
                } catch (e: PostpaidCreditSuspendedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real postpaid credit line with a real nonzero balance, real overdue for a late fee") {
        val postpaidCreditLineRepository = mockk<PostpaidCreditLineRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>()
        val service = PostpaidCreditService(
            postpaidCreditLineRepository, accountRepository, ledgerService, creditScoreService,
            notificationRepository, pushNotificationService, mockk(relaxed = true),
        )

        val line = PostpaidCreditLine(
            id = "postpaid_1", userId = "user_1", accountId = "account_1", creditLimit = BigDecimal("100000"),
            currentBalance = BigDecimal("36500"), cycleDueAt = Instant.now().minusSeconds(3600),
        )
        every { postpaidCreditLineRepository.findByIdForUpdate("postpaid_1") } returns Optional.of(line)
        every { postpaidCreditLineRepository.save(any()) } answers { firstArg() }

        When("accrueLateFee runs for one real day at the real sourced 12% annual rate on a real 36,500 RWF balance") {
            val legsSlot = mutableListOf<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_latefee_1", emptyList())

            service.accrueLateFee(line)

            Then("it real-locks the line row before mutating it, not the detached scheduler-batch object directly") {
                verify(exactly = 1) { postpaidCreditLineRepository.findByIdForUpdate("postpaid_1") }
            }

            Then("it real-accrues exactly 12 RWF (36500 * 0.12 / 365) onto the real balance and real-suspends the line") {
                line.currentBalance shouldBe BigDecimal("36512.00")
                line.status shouldBe PostpaidCreditLineStatus.SUSPENDED
                val debitLeg = legsSlot.first().first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountType shouldBe LedgerAccountType.POSTPAID_CREDIT_PAYABLE
                val creditLeg = legsSlot.first().first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountType shouldBe LedgerAccountType.INTEREST_INCOME
            }
        }
    }

    Given("real postpaid credit lines of every real shape, checking which are due for a real late-fee accrual") {
        val postpaidCreditLineRepository = mockk<PostpaidCreditLineRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>()
        val service = PostpaidCreditService(
            postpaidCreditLineRepository, accountRepository, ledgerService, creditScoreService,
            notificationRepository, pushNotificationService, mockk(relaxed = true),
        )

        val overdue = PostpaidCreditLine(id = "postpaid_a", userId = "user_a", accountId = "account_a", creditLimit = BigDecimal("100000"), currentBalance = BigDecimal("10000"), cycleDueAt = Instant.now().minusSeconds(3600))
        val notYetDue = PostpaidCreditLine(id = "postpaid_b", userId = "user_b", accountId = "account_b", creditLimit = BigDecimal("100000"), currentBalance = BigDecimal("10000"), cycleDueAt = Instant.now().plusSeconds(3600))
        val recentlyAccrued = PostpaidCreditLine(id = "postpaid_c", userId = "user_c", accountId = "account_c", creditLimit = BigDecimal("100000"), currentBalance = BigDecimal("10000"), cycleDueAt = Instant.now().minusSeconds(3600), lastLateFeeAccrualAt = Instant.now().minusSeconds(3600))
        every { postpaidCreditLineRepository.findByCurrentBalanceGreaterThan(BigDecimal.ZERO) } returns listOf(overdue, notYetDue, recentlyAccrued)

        When("getLinesOverdueForLateFee runs") {
            val due = service.getLinesOverdueForLateFee()

            Then("it real-includes only the real-overdue, never-yet-accrued line -- honestly excluding one not yet due and one too-recently accrued") {
                due shouldBe listOf(overdue)
            }
        }
    }

    Given("real postpaid credit lines of every real shape, checking which are due for a real pre-due payment reminder") {
        val postpaidCreditLineRepository = mockk<PostpaidCreditLineRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>()
        val service = PostpaidCreditService(
            postpaidCreditLineRepository, accountRepository, ledgerService, creditScoreService,
            notificationRepository, pushNotificationService, mockk(relaxed = true),
        )

        val dueSoon = PostpaidCreditLine(id = "postpaid_a", userId = "user_a", accountId = "account_a", creditLimit = BigDecimal("100000"), currentBalance = BigDecimal("10000"), cycleDueAt = Instant.now().plus(java.time.Duration.ofDays(2)))
        val notYetDueSoon = PostpaidCreditLine(id = "postpaid_b", userId = "user_b", accountId = "account_b", creditLimit = BigDecimal("100000"), currentBalance = BigDecimal("10000"), cycleDueAt = Instant.now().plus(java.time.Duration.ofDays(10)))
        val alreadyOverdue = PostpaidCreditLine(id = "postpaid_c", userId = "user_c", accountId = "account_c", creditLimit = BigDecimal("100000"), currentBalance = BigDecimal("10000"), cycleDueAt = Instant.now().minusSeconds(3600))
        val alreadyReminded = PostpaidCreditLine(id = "postpaid_d", userId = "user_d", accountId = "account_d", creditLimit = BigDecimal("100000"), currentBalance = BigDecimal("10000"), cycleDueAt = Instant.now().plus(java.time.Duration.ofDays(1)), paymentReminderSentAt = Instant.now())
        every { postpaidCreditLineRepository.findByCurrentBalanceGreaterThan(BigDecimal.ZERO) } returns listOf(dueSoon, notYetDueSoon, alreadyOverdue, alreadyReminded)

        When("getLinesDueSoonForPaymentReminder runs") {
            val due = service.getLinesDueSoonForPaymentReminder()

            Then("it real-includes only the real line due within the 3-day window, never yet reminded -- honestly excluding one due too far out, one already overdue (left to the late-fee sweep instead), and one already reminded") {
                due shouldBe listOf(dueSoon)
            }
        }
    }

    Given("a real postpaid credit line real due soon, sending its real payment reminder") {
        val postpaidCreditLineRepository = mockk<PostpaidCreditLineRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>()
        val service = PostpaidCreditService(
            postpaidCreditLineRepository, accountRepository, ledgerService, creditScoreService,
            notificationRepository, pushNotificationService, mockk(relaxed = true),
        )

        val line = PostpaidCreditLine(
            id = "postpaid_1", userId = "user_1", accountId = "account_1", creditLimit = BigDecimal("100000"),
            currentBalance = BigDecimal("30000"), cycleDueAt = Instant.now().plus(java.time.Duration.ofDays(2)),
        )
        every { postpaidCreditLineRepository.findById("postpaid_1") } returns Optional.of(line)
        every { postpaidCreditLineRepository.save(any()) } answers { firstArg() }
        val notifSlot = slot<Notification>()
        every { notificationRepository.save(capture(notifSlot)) } answers { firstArg() }
        every { pushNotificationService.sendToUser(any(), any(), any(), any()) } returns Unit

        When("sendPaymentReminder runs") {
            service.sendPaymentReminder("postpaid_1")

            Then("it real-saves an in-app notification, real-sends a push, and real-marks paymentReminderSentAt so a re-run can't double-fire") {
                line.paymentReminderSentAt shouldNotBe null
                notifSlot.captured.type shouldBe "POSTPAID_CREDIT_PAYMENT_DUE_SOON"
                notifSlot.captured.userId shouldBe "user_1"
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any()) }
            }
        }

        When("sendPaymentReminder is called again after the reminder already fired") {
            line.paymentReminderSentAt = Instant.now()

            service.sendPaymentReminder("postpaid_1")

            Then("it's real-skipped -- re-checking state right before sending guards against a genuine race double-firing") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
