package rw.itunda.loans

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.VupLoan
import rw.itunda.core.domain.VupLoanPurpose
import rw.itunda.core.domain.VupLoanStatus
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.pricing.ReminderWindows
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.VupLoanRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class IneligibleUbudeheCategoryException(message: String) : RuntimeException(message)
class InvalidVupLoanAmountException(message: String) : RuntimeException(message)
class VupLoanAlreadyActiveException(message: String) : RuntimeException(message)
class VupLoanNotFoundException(message: String) : RuntimeException(message)
class VupLoanNotRequestedException(message: String) : RuntimeException(message)
class VupLoanNotRepayableException(message: String) : RuntimeException(message)
class VupLoanNoAccountException(message: String) : RuntimeException(message)
class VupLoanInvalidRepayAmountException(message: String) : RuntimeException(message)
class VupLoanNotOverdueException(message: String) : RuntimeException(message)
class InvalidVupLoanReviewNoteException(message: String) : RuntimeException(message)

// itunda's own honest ceiling on a single VUP loan: 500,000 RWF, 5x the real sourced
// NISR EICV7 average (~100,000 RWF) -- a reasonable bound, not a claimed reproduction
// of any real published VUP/FS cap (the sourcing didn't specify one precisely).
private val MAX_VUP_LOAN_AMOUNT = BigDecimal("500000")

// VUP/FS targets households in poorer Ubudehe categories (1-3); category 4+ (the
// wealthiest bands) is deliberately out of scope for this means-tested product.
private const val MIN_ELIGIBLE_UBUDEHE_CATEGORY = 1
private const val MAX_ELIGIBLE_UBUDEHE_CATEGORY = 3

private val ACTIVE_STATUSES = listOf(VupLoanStatus.REQUESTED, VupLoanStatus.DISBURSED, VupLoanStatus.OVERDUE)

/**
 * Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services micro-loan --
 * see `VupLoan.kt`'s own doc comment for the full sourced account and the correction
 * made from this feature's own earlier research proposal (disbursement/repayment
 * mirror `LoansService.applyForLoan`/`repayLoan`'s exact `loan_payable` ledger shape,
 * never `SaccoService`'s pooled account).
 *
 * Genuinely distinct from every other lending feature in this codebase: the first
 * MEANS-TESTED product, gated on a self-declared Ubudehe category rather than credit
 * score or collateral -- see `VupLoan.declaredUbudeheCategory`'s own doc comment for
 * why that field is honestly self-declared, not government-verified.
 */
@Service
class VupLoanService(
    private val vupLoanRepository: VupLoanRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    // Real bug found in this feature's own build-time review (2026-08-02): the
    // "reject a second active loan" check below reads-then-creates a brand-new row, a
    // shape @Version can never protect (there's no existing row to version until the
    // first request's insert commits). Two concurrent applyForLoan calls for the same
    // user with no existing loan yet could both read zero active loans and both create
    // one, each independently disbursable -- silently doubling a user's real
    // borrowing limit. Locking the caller's own MAIN account row first (same
    // findByIdForUpdate convention FloatMarketplaceService/LedgerAccountRepository
    // already use to close an identical class of race) serializes concurrent
    // applications for the same user without needing a new lock table.
    @Transactional
    fun applyForLoan(userId: String, declaredUbudeheCategory: Int, purpose: VupLoanPurpose, amount: BigDecimal): VupLoan {
        if (declaredUbudeheCategory < MIN_ELIGIBLE_UBUDEHE_CATEGORY || declaredUbudeheCategory > MAX_ELIGIBLE_UBUDEHE_CATEGORY) {
            throw IneligibleUbudeheCategoryException(
                "VUP Financial Services targets Ubudehe categories $MIN_ELIGIBLE_UBUDEHE_CATEGORY-$MAX_ELIGIBLE_UBUDEHE_CATEGORY; category $declaredUbudeheCategory is not eligible",
            )
        }
        if (amount <= BigDecimal.ZERO || amount > MAX_VUP_LOAN_AMOUNT) {
            throw InvalidVupLoanAmountException("Amount must be between 1 and $MAX_VUP_LOAN_AMOUNT RWF")
        }

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw VupLoanNoAccountException("No account found for this account")
        accountRepository.findByIdForUpdate(account.id)

        val activeLoans = vupLoanRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
        if (activeLoans.isNotEmpty()) {
            throw VupLoanAlreadyActiveException("You already have an active VUP loan -- repay it before applying for another")
        }

        rateLimiter.checkLimit("vup-loan:apply:$userId", limit = 5, window = Duration.ofDays(1))

        return vupLoanRepository.save(
            VupLoan(
                id = "vuploan_${UUID.randomUUID()}", userId = userId,
                declaredUbudeheCategory = declaredUbudeheCategory, purpose = purpose,
                principalAmount = amount, outstandingPrincipal = amount,
            ),
        )
    }

    @Transactional
    fun disburse(userId: String, loanId: String): VupLoan {
        val loan = vupLoanRepository.findById(loanId).orElseThrow { VupLoanNotFoundException("VUP loan not found") }
        if (loan.userId != userId) throw VupLoanNotFoundException("VUP loan not found")
        if (loan.status != VupLoanStatus.REQUESTED) throw VupLoanNotRequestedException("Only a REQUESTED loan can be disbursed")

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw VupLoanNoAccountException("No account found for this account")

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, loan.principalAmount, "VUP Financial Services loan disbursement"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, loan.principalAmount, "VUP Financial Services principal owed"),
            ),
        )

        loan.status = VupLoanStatus.DISBURSED
        loan.disbursedAt = Instant.now()
        // A reasonable, honestly-named term chosen by itunda itself -- 12 months --
        // not a claimed reproduction of VUP/FS's real exact term, which the sourcing
        // didn't specify precisely.
        loan.dueDate = LocalDate.now().plusMonths(12)
        return vupLoanRepository.save(loan)
    }

    @Transactional
    fun repay(userId: String, loanId: String, amount: BigDecimal): VupLoan {
        val loan = vupLoanRepository.findById(loanId).orElseThrow { VupLoanNotFoundException("VUP loan not found") }
        if (loan.userId != userId) throw VupLoanNotFoundException("VUP loan not found")
        if (loan.status != VupLoanStatus.DISBURSED && loan.status != VupLoanStatus.OVERDUE) {
            throw VupLoanNotRepayableException("Only a DISBURSED or OVERDUE loan can be repaid")
        }
        if (amount <= BigDecimal.ZERO) throw VupLoanInvalidRepayAmountException("Repayment amount must be positive")

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw VupLoanNoAccountException("No account found for this account")

        // Clamp BEFORE ever touching the ledger -- the exact overshoot-clamp lesson
        // this session just learned fixing InsuranceService.contributeToFund. Never
        // post the raw amount to the ledger and cap the field separately.
        val actualAmount = amount.min(loan.outstandingPrincipal)

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "VUP Financial Services loan repayment"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, actualAmount, "VUP Financial Services loan repayment"),
            ),
        )

        loan.outstandingPrincipal = loan.outstandingPrincipal.subtract(actualAmount)
        loan.status = when {
            loan.outstandingPrincipal <= BigDecimal.ZERO -> VupLoanStatus.REPAID
            loan.dueDate != null && loan.dueDate!!.isBefore(LocalDate.now()) -> VupLoanStatus.OVERDUE
            else -> VupLoanStatus.DISBURSED
        }
        return vupLoanRepository.save(loan)
    }

    fun getMyLoans(userId: String): List<VupLoan> = vupLoanRepository.findByUserId(userId)

    fun getLoan(userId: String, loanId: String): VupLoan {
        val loan = vupLoanRepository.findById(loanId).orElseThrow { VupLoanNotFoundException("VUP loan not found") }
        if (loan.userId != userId) throw VupLoanNotFoundException("VUP loan not found")
        return loan
    }

    fun getEligibility(userId: String): Map<String, Any?> {
        val hasActiveLoan = vupLoanRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES).isNotEmpty()
        return mapOf(
            "hasActiveLoan" to hasActiveLoan,
            "canApply" to !hasActiveLoan,
            "minUbudeheCategory" to MIN_ELIGIBLE_UBUDEHE_CATEGORY,
            "maxUbudeheCategory" to MAX_ELIGIBLE_UBUDEHE_CATEGORY,
            "interestRate" to 0.11,
            "maxAmount" to MAX_VUP_LOAN_AMOUNT,
        )
    }

    // For the overdue scheduler: DISBURSED loans past their due date, still owing.
    fun getLoansDueForOverdueCheck(): List<VupLoan> {
        val today = LocalDate.now()
        return vupLoanRepository.findAll().filter {
            it.status == VupLoanStatus.DISBURSED && it.outstandingPrincipal > BigDecimal.ZERO &&
                it.dueDate != null && it.dueDate!!.isBefore(today)
        }
    }

    @Transactional
    fun markOverdue(loan: VupLoan) {
        loan.status = VupLoanStatus.OVERDUE
        vupLoanRepository.save(loan)
        // Real gap found live (2026-08-10) -- see VupLoan.kt's own doc comment: this
        // used to be visibility-only (a server log line), so a real borrower had no
        // way to learn their loan had gone overdue short of opening the app and
        // checking. Same real Notification + push pattern OverdraftService.
        // openOverdraft already establishes for a credit-product state change.
        val title = "VUP loan payment overdue"
        val body = "Your VUP Financial Services loan (${loan.outstandingPrincipal} RWF outstanding) is now overdue. Repay from the Loans tab to avoid further delay."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = loan.userId, type = "VUP_LOAN_OVERDUE",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"loanId\":\"${loan.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(loan.userId, title, body, mapOf("loanId" to loan.id))
    }

    // Real, minimal pre-due reminder (2026-08-10) -- same finding as markOverdue's own
    // doc comment, the proactive half: a reminder a few days BEFORE a loan goes
    // overdue is what actually drives real repayment behavior (this is standard real
    // fintech practice -- Toss/KakaoBank both send a due-date-approaching push, not
    // just an after-the-fact overdue flag). `reminderSentAt` guards against
    // re-notifying on every scheduler tick. Default consolidated 2026-09-06 into
    // core/pricing/ReminderWindows -- same real 3-day policy this codebase's other
    // pre-deadline reminders use, kept as a Long default parameter (not a shared val)
    // since this is the only one of the family expressed as a function default rather
    // than a stored constant.
    fun getLoansDueSoonForReminder(withinDays: Long = ReminderWindows.PRE_EXPIRY_REMINDER_WINDOW.toDays()): List<VupLoan> {
        val today = LocalDate.now()
        val cutoff = today.plusDays(withinDays)
        return vupLoanRepository.findByStatus(VupLoanStatus.DISBURSED).filter {
            it.outstandingPrincipal > BigDecimal.ZERO && it.reminderSentAt == null &&
                it.dueDate != null && !it.dueDate!!.isBefore(today) && !it.dueDate!!.isAfter(cutoff)
        }
    }

    @Transactional
    fun sendDueReminder(loan: VupLoan) {
        loan.reminderSentAt = Instant.now()
        vupLoanRepository.save(loan)
        val title = "VUP loan payment due soon"
        val body = "Your VUP Financial Services loan (${loan.outstandingPrincipal} RWF outstanding) is due ${loan.dueDate}. Repay from the Loans tab anytime before then."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = loan.userId, type = "VUP_LOAN_DUE_SOON",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"loanId\":\"${loan.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(loan.userId, title, body, mapOf("loanId" to loan.id))
    }

    // Real ops loan-default review queue (2026-09-06, Bank product-completeness pass)
    // -- Bank had zero dedicated ops-mfe queue despite 14 real queues existing for
    // other products (fraud/compliance/disputes/etc.). VUP is the one loan product
    // with a real, already-computed OVERDUE status (VupLoanOverdueScheduler), so
    // this is the clean, honest starting point -- Student/Postpaid/Overdraft loan
    // review is a real, separate follow-up, not attempted here.
    fun getDefaultReviewQueue(pageable: Pageable): Page<VupLoan> =
        vupLoanRepository.findByStatusAndReviewedAtIsNull(VupLoanStatus.OVERDUE, pageable)

    // Mirrors PropertyOwnershipService.decide's exact shape: validate, record the
    // reviewer/note/timestamp, optionally transition status, notify the affected
    // user. Deliberately does NOT touch the ledger -- writing off a loan's real
    // accounting treatment is booking a bad-debt expense against itunda's own P&L,
    // which needs a real ledger account this pass doesn't invent (see
    // VupLoanStatus.WRITTEN_OFF's own doc comment). This only records the ops
    // decision and stops the loan reappearing in the queue; the outstanding
    // principal figure is left untouched and honestly still reflects what the
    // borrower owes.
    @Transactional
    fun decide(loanId: String, reviewerId: String, writeOff: Boolean, note: String?): VupLoan {
        val loan = vupLoanRepository.findById(loanId).orElseThrow { VupLoanNotFoundException("VUP loan not found") }
        if (loan.status != VupLoanStatus.OVERDUE) {
            throw VupLoanNotOverdueException("Only an OVERDUE loan can be reviewed here -- this loan is ${loan.status}")
        }
        // Same missing-bound bug class as the 2026-09-05 sweep -- see
        // PropertyOwnershipService.decide's identical check, matching ops-mfe's own
        // real 255-char decision-reason cap.
        if (note != null && note.length > 255) {
            throw InvalidVupLoanReviewNoteException("Review note must be 255 characters or fewer")
        }

        loan.reviewedBy = reviewerId
        loan.reviewedAt = Instant.now()
        loan.reviewNote = note
        if (writeOff) {
            loan.status = VupLoanStatus.WRITTEN_OFF
        }
        vupLoanRepository.save(loan)

        if (writeOff) {
            val title = "VUP loan written off"
            val body = "Your VUP Financial Services loan has been written off by itunda and is no longer being pursued for repayment."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = loan.userId, type = "VUP_LOAN_WRITTEN_OFF",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"loanId\":\"${loan.id}\"}",
                ),
            )
            pushNotificationService.sendToUser(loan.userId, title, body, mapOf("loanId" to loan.id))
        }
        return loan
    }
}
