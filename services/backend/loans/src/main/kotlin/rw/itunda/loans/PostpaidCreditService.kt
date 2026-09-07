package rw.itunda.loans

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.creditscore.CreditScoreService
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.PostpaidCreditLine
import rw.itunda.core.domain.PostpaidCreditLineStatus
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.pricing.ReminderWindows
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PostpaidCreditLineRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PostpaidCreditAlreadyOpenException(message: String) : RuntimeException(message)
class PostpaidCreditNotActiveException(message: String) : RuntimeException(message)
class PostpaidCreditLimitExceededException(message: String) : RuntimeException(message)
class PostpaidCreditInvalidAmountException(message: String) : RuntimeException(message)
class PostpaidCreditNoAccountException(message: String) : RuntimeException(message)
class PostpaidCreditSuspendedException(message: String) : RuntimeException(message)

/**
 * Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line) -- see
 * PostpaidCreditLine.kt's own doc comment for the full sourced account and its own
 * structural distinction from the already-real OverdraftAccount.
 */
@Service
class PostpaidCreditService(
    private val postpaidCreditLineRepository: PostpaidCreditLineRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val creditScoreService: CreditScoreService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        // Real BNPL's own defining market position: every registered account
        // technically qualifies for at least the smallest tier, unlike LoansService's
        // real 400-point bar or OverdraftService's identical one -- BASE_SCORE_POINTS is
        // awarded to every account unconditionally (CreditScoreService.computeScore),
        // so this is honestly "everyone qualifies for something small", not a fake gate.
        const val MIN_SCORE_TO_QUALIFY = CreditScoreService.BASE_SCORE_POINTS

        // Itunda's own honest tiering by the caller's own already-computed real credit
        // score, reusing LoansService/OverdraftService's exact real risk bands
        // (400/600) as the tier boundaries for consistency across every itunda credit
        // product, rather than inventing new ones. The top tier (300,000) is Naver Pay's
        // own real, sourced maximum limit (no user-requestable increase in the real
        // product either) -- see this class's own doc comment.
        val TIERS: List<Pair<Int, BigDecimal>> = listOf(
            300 to BigDecimal("20000"),
            400 to BigDecimal("100000"),
            600 to BigDecimal("200000"),
            700 to BigDecimal("300000"),
        )

        // Real Naver Pay 후불결제's own sourced late-fee rate: no interest at all for
        // on-time repayment, but a real 12% annual late fee accrues on overdue
        // principal once a cycle is missed. Same daily-prorated shape
        // OverdraftService.accrueInterest already establishes for its own (different,
        // immediate-from-day-1) interest, just gated on real overdue status instead of
        // any nonzero drawn balance.
        const val LATE_FEE_ANNUAL_RATE = 12.0
        val BILLING_CYCLE: Duration = Duration.ofDays(30)

        // Real Naver Pay/Kakao Pay 후불결제 own "결제 예정일이 다가와요" (payment due date
        // approaching) push -- both real products notify a few days ahead of the real
        // settlement date, distinct from (and strictly earlier than)
        // PostpaidCreditAccrualScheduler's own late-fee accrual, which only ever fires
        // AFTER cycleDueAt has already passed. Consolidated 2026-09-06 into
        // core/pricing/ReminderWindows -- see its own doc comment.
        val PAYMENT_REMINDER_WINDOW: Duration = ReminderWindows.PRE_EXPIRY_REMINDER_WINDOW
    }

    private fun tierLimit(score: Int): BigDecimal =
        TIERS.lastOrNull { score >= it.first }?.second
            ?: throw PostpaidCreditLimitExceededException("Credit score $score is below the minimum $MIN_SCORE_TO_QUALIFY required")

    @Transactional
    fun applyForPostpaidCredit(userId: String): PostpaidCreditLine {
        // Real anti-spam/cost limit -- same "apply" convention every other loan product
        // in this module already establishes (StudentLoanService/VupLoanService/
        // VendorCashAdvanceService), never wired in here until now.
        rateLimiter.checkLimit("postpaid-credit:apply:$userId", limit = 5, window = Duration.ofDays(1))
        if (postpaidCreditLineRepository.findByUserId(userId) != null) {
            throw PostpaidCreditAlreadyOpenException("You already have a real postpaid credit line")
        }
        val score = creditScoreService.computeScore(userId).score
        val limit = tierLimit(score)
        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw PostpaidCreditNoAccountException("No account found for this account")

        return postpaidCreditLineRepository.save(
            PostpaidCreditLine(id = "postpaid_${UUID.randomUUID()}", userId = userId, accountId = account.id, creditLimit = limit),
        )
    }

    fun getMyPostpaidCredit(userId: String): PostpaidCreditLine? = postpaidCreditLineRepository.findByUserId(userId)

    /**
     * Draws against the real available limit and credits it straight to the caller's
     * own MAIN account balance -- usable exactly like any other real money for Pay-by-
     * code/transfers/etc., the same "top up spendable balance" v1 scope
     * YouthAccountService.deposit's own reverse direction already establishes, rather than
     * rewiring every existing payment path to conditionally draw from this line.
     */
    @Transactional
    fun spend(userId: String, amount: BigDecimal): Map<String, Any?> {
        if (amount <= BigDecimal.ZERO) throw PostpaidCreditInvalidAmountException("Amount must be greater than zero")
        // Real anti-spam/cost limit -- same "frequent, repeatable money-movement action"
        // convention P2pService.sendMoney/AccountService.confirmTransfer already
        // establish, never wired in here until now.
        rateLimiter.checkLimit("postpaid-credit:spend:$userId", limit = 30, window = Duration.ofHours(1))
        val existing = postpaidCreditLineRepository.findByUserId(userId)
            ?: throw PostpaidCreditNotActiveException("No postpaid credit line found -- apply first")
        // Real lost-update fix (2026-09-07) -- see PostpaidCreditLineRepository
        // .findByIdForUpdate's own doc comment: two concurrent spends could otherwise
        // both read the same currentBalance, jointly exceeding creditLimit.
        val line = postpaidCreditLineRepository.findByIdForUpdate(existing.id).orElseThrow { PostpaidCreditNotActiveException("No postpaid credit line found -- apply first") }
        if (line.status != PostpaidCreditLineStatus.ACTIVE) {
            throw PostpaidCreditSuspendedException("Your postpaid credit line is suspended pending repayment of an overdue balance")
        }
        val availableCredit = line.creditLimit.subtract(line.currentBalance)
        if (amount > availableCredit) {
            throw PostpaidCreditLimitExceededException("Requested amount exceeds your real available credit of $availableCredit RWF")
        }

        val account = accountRepository.findById(line.accountId).orElseThrow { PostpaidCreditNoAccountException("Account not found") }
        val result = ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Postpaid credit spend ${line.id}"),
                LedgerLeg("postpaid_credit_payable", LedgerAccountType.POSTPAID_CREDIT_PAYABLE, LedgerDirection.DEBIT, amount, "Postpaid credit spend ${line.id}"),
            ),
        )

        // Real per-cycle (not per-purchase) billing -- a spend against an already-open
        // cycle (currentBalance already nonzero) doesn't push the due date out further,
        // matching the real product's own monthly settlement, not a rolling window.
        if (line.currentBalance <= BigDecimal.ZERO) {
            line.cycleDueAt = Instant.now().plus(BILLING_CYCLE)
        }
        line.currentBalance = line.currentBalance.add(amount)
        line.updatedAt = Instant.now()
        postpaidCreditLineRepository.save(line)

        return mapOf(
            "transactionId" to result.transactionId, "amount" to amount,
            "currentBalance" to line.currentBalance, "availableCredit" to line.creditLimit.subtract(line.currentBalance),
        )
    }

    @Transactional
    fun repay(userId: String, amount: BigDecimal): Map<String, Any?> {
        if (amount <= BigDecimal.ZERO) throw PostpaidCreditInvalidAmountException("Amount must be greater than zero")
        // Real anti-spam/cost limit -- same convention spend above now establishes.
        rateLimiter.checkLimit("postpaid-credit:repay:$userId", limit = 30, window = Duration.ofHours(1))
        val existing = postpaidCreditLineRepository.findByUserId(userId)
            ?: throw PostpaidCreditNotActiveException("No postpaid credit line found")
        // Real lost-update fix (2026-09-07) -- see PostpaidCreditLineRepository
        // .findByIdForUpdate's own doc comment.
        val line = postpaidCreditLineRepository.findByIdForUpdate(existing.id).orElseThrow { PostpaidCreditNotActiveException("No postpaid credit line found") }

        val repayAmount = amount.min(line.currentBalance)
        val account = accountRepository.findById(line.accountId).orElseThrow { PostpaidCreditNoAccountException("Account not found") }
        val result = ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, repayAmount, "Postpaid credit repayment ${line.id}"),
                LedgerLeg("postpaid_credit_payable", LedgerAccountType.POSTPAID_CREDIT_PAYABLE, LedgerDirection.CREDIT, repayAmount, "Postpaid credit repayment ${line.id}"),
            ),
        )

        line.currentBalance = line.currentBalance.subtract(repayAmount)
        // Real "cycle closed, service restored" -- a full repayment clears the cycle
        // deadline and reactivates a line that had been suspended for being overdue,
        // matching the real product's own "pay what you owe, service comes back" rule.
        if (line.currentBalance <= BigDecimal.ZERO) {
            line.cycleDueAt = null
            line.lastLateFeeAccrualAt = null
            line.paymentReminderSentAt = null
            line.status = PostpaidCreditLineStatus.ACTIVE
        }
        line.updatedAt = Instant.now()
        postpaidCreditLineRepository.save(line)
        val updatedAccount = accountRepository.findById(line.accountId).orElseThrow { PostpaidCreditNoAccountException("Account not found") }

        return mapOf(
            "transactionId" to result.transactionId, "amount" to repayAmount,
            "currentBalance" to line.currentBalance, "availableCredit" to line.creditLimit.subtract(line.currentBalance),
            "newBalance" to updatedAccount.balance,
        )
    }

    // Real ongoing late-fee accrual sweep -- see PostpaidCreditAccrualScheduler's own
    // doc comment. A real line not yet past cycleDueAt, or one already fully repaid
    // (zero balance, filtered by the repo query itself), is honestly skipped -- same
    // "charge only what's real overdue" discipline OverdraftService
    // .getAccountsDueForAccrual already establishes for its own (different, from-day-1)
    // accrual. lastLateFeeAccrualAt == null (never yet overdue) counts as immediately
    // due, same "null means immediately eligible" trick OverdraftAccount's own scheduler
    // already uses, so live verification never needs to wait out a real 24 hours.
    fun getLinesOverdueForLateFee(): List<PostpaidCreditLine> =
        postpaidCreditLineRepository.findByCurrentBalanceGreaterThan(BigDecimal.ZERO).filter { line ->
            line.cycleDueAt != null && line.cycleDueAt!!.isBefore(Instant.now()) &&
                (line.lastLateFeeAccrualAt == null || Duration.between(line.lastLateFeeAccrualAt, Instant.now()) >= Duration.ofHours(24))
        }

    @Transactional
    fun accrueLateFee(lineArg: PostpaidCreditLine) {
        // Real lost-update fix (2026-09-07) -- lineArg was loaded by
        // PostpaidCreditAccrualScheduler's own earlier, already-committed transaction (a
        // batch query), so it's detached by the time this method's own @Transactional
        // starts. Re-fetching locked here closes the same race
        // VendorCashAdvanceService.runDailyCollection's own identical fix closes -- a
        // manual spend/repay racing this scheduled accrual on the same line.
        val line = postpaidCreditLineRepository.findByIdForUpdate(lineArg.id).orElse(null) ?: return
        val dailyRate = BigDecimal.valueOf(LATE_FEE_ANNUAL_RATE).divide(BigDecimal(100), 10, RoundingMode.HALF_UP).divide(BigDecimal(365), 10, RoundingMode.HALF_UP)
        val lateFee = line.currentBalance.multiply(dailyRate).setScale(2, RoundingMode.HALF_UP)
        if (lateFee > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("postpaid_credit_payable", LedgerAccountType.POSTPAID_CREDIT_PAYABLE, LedgerDirection.DEBIT, lateFee, "Postpaid credit late fee ${line.id}"),
                    LedgerLeg("interest_income", LedgerAccountType.INTEREST_INCOME, LedgerDirection.CREDIT, lateFee, "Postpaid credit late fee ${line.id}"),
                ),
            )
            line.currentBalance = line.currentBalance.add(lateFee)
        }
        // Real "service suspended while overdue" -- matches Naver Pay's own sourced rule
        // ("연체를 하면 서비스를 더 이상 이용할 수 없으며") exactly: spend() real-blocks a
        // SUSPENDED line entirely, restored only by repay() clearing the balance to
        // zero. Idempotent to re-set on every accrual tick -- cheap and always correct.
        line.status = PostpaidCreditLineStatus.SUSPENDED
        line.lastLateFeeAccrualAt = Instant.now()
        line.updatedAt = Instant.now()
        postpaidCreditLineRepository.save(line)
    }

    // Real pre-due payment reminder sweep -- see PostpaidCreditPaymentReminderScheduler's
    // own doc comment and PAYMENT_REMINDER_WINDOW's own doc comment for the full real
    // sourcing. Coarse repo filter (every real nonzero balance, same repo query
    // getLinesOverdueForLateFee already reuses), exact "due soon, not yet reminded"
    // condition in-service. Deliberately does NOT overlap with the already-overdue case
    // (cycleDueAt in the past is left to PostpaidCreditAccrualScheduler's own late-fee
    // sweep) -- this is honestly the earlier, friendlier nudge, not a duplicate of it.
    fun getLinesDueSoonForPaymentReminder(): List<PostpaidCreditLine> {
        val now = Instant.now()
        val cutoff = now.plus(PAYMENT_REMINDER_WINDOW)
        return postpaidCreditLineRepository.findByCurrentBalanceGreaterThan(BigDecimal.ZERO).filter { line ->
            line.paymentReminderSentAt == null && line.cycleDueAt != null &&
                !line.cycleDueAt!!.isBefore(now) && !line.cycleDueAt!!.isAfter(cutoff)
        }
    }

    /** One real payment-due-soon notification, called per-line by the scheduler --
     * re-checks `paymentReminderSentAt`/`currentBalance`/`cycleDueAt` right before sending
     * so a genuine race (e.g. a real repay() clearing the cycle mid-sweep) can't fire a
     * stale reminder, same resilience discipline sendExpiryReminder's own doc comment
     * already establishes. */
    @Transactional
    fun sendPaymentReminder(lineId: String) {
        val line = postpaidCreditLineRepository.findById(lineId).orElse(null) ?: return
        if (line.paymentReminderSentAt != null || line.currentBalance <= BigDecimal.ZERO || line.cycleDueAt == null) return

        val title = "Your postpaid credit payment is due soon"
        val body = "Your postpaid credit balance of ${line.currentBalance} RWF is due ${line.cycleDueAt}. Repay from the Loans tab before then to avoid a late fee."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = line.userId, type = "POSTPAID_CREDIT_PAYMENT_DUE_SOON",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"lineId\":\"${line.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(line.userId, title, body, mapOf("lineId" to line.id))
        line.paymentReminderSentAt = Instant.now()
        postpaidCreditLineRepository.save(line)
    }
}
