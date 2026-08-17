package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class StudentLoanLevel { UNDERGRADUATE, POSTGRADUATE }
enum class StudentLoanStatus { REQUESTED, DISBURSED, IN_GRACE_PERIOD, REPAYING, REPAID, OVERDUE }

/**
 * Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan --
 * sourced beyond this session's usual Toss/Kakao/Naver/Coupang reference ecosystems.
 * Rwanda has run a national student-loan-and-bursary scheme since Law No. 44/2015,
 * administered by BRD since an October 2016 MINEDUC agreement. Real scale: Rwf 221.85
 * billion disbursed to 139,925 students (through mid-2023), fixed interest rates of
 * 11% undergraduate / 12% postgraduate, repayment terms of 2-10 years (brd.rw).
 * Eligibility runs through Financial Means Testing (FMT), distinct from Ubudehe. BRD
 * has publicly acknowledged real collection difficulty: only 13.3% repayment
 * compliance by mid-2023 (IGIHE), Rwf 7.2bn recovered in 2024, Rwf 34.7bn cumulative
 * since 2016 (KT Press, May 2025). Sources: brd.rw, IGIHE, KT Press.
 *
 * Genuinely distinct from every other lending feature in this codebase: eligibility
 * on self-declared household income (not Ubudehe, unlike `VupLoan`), a mandatory
 * grace period between disbursement and first-repayment obligation (unlike
 * `VupLoan`'s immediate-repayment shape), and income-percentage-SUGGESTED (not
 * fixed-installment) repayment.
 *
 * Honest v1 limitation: the real 8%-of-income monthly deduction (brd.rw) is
 * fundamentally an employer-payroll/RRA-integration mechanic that itunda has no path
 * to -- the same external-access category as the NIDA/PSP/carrier gaps named
 * elsewhere in this codebase. V1's `StudentLoanService.repay` is user-initiated,
 * itunda-wallet-sourced repayment; the 8%-of-declared-income figure is surfaced only
 * as a *suggested* amount (`StudentLoanService.getSuggestedMonthlyPayment`), never
 * automatically enforced or deducted.
 */
@Entity
@Table(name = "student_loans")
class StudentLoan(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val level: StudentLoanLevel,

    // Honest v1 limitation: itunda has no access to Rwanda's real BRD Financial Means
    // Testing (FMT) process, so this is SELF-DECLARED by the user, not
    // government-verified -- the same "no external registry this backend has no path
    // to check" honesty `VupLoan.declaredUbudeheCategory` already carries for its own
    // unverifiable field.
    @Column(name = "declared_annual_household_income", nullable = false, precision = 18, scale = 2)
    val declaredAnnualHouseholdIncome: BigDecimal,

    @Column(name = "principal_amount", nullable = false, precision = 18, scale = 2)
    val principalAmount: BigDecimal,

    @Column(name = "outstanding_balance", nullable = false, precision = 18, scale = 2)
    var outstandingBalance: BigDecimal,

    // Real, documented BRD fixed rates (brd.rw): 11% undergraduate, 12% postgraduate.
    @Column(name = "interest_rate", nullable = false)
    val interestRate: Double,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: StudentLoanStatus = StudentLoanStatus.REQUESTED,

    @Column(name = "applied_at", nullable = false)
    val appliedAt: Instant = Instant.now(),

    @Column(name = "disbursed_at")
    var disbursedAt: Instant? = null,

    @Column(name = "expected_graduation_date", nullable = false)
    val expectedGraduationDate: LocalDate,

    @Column(name = "grace_ends_at")
    var graceEndsAt: LocalDate? = null,

    // Real gap found live (2026-08-18, same lens as PostpaidCreditLine.cycleDueAt's own
    // reminder gap this session already found and fixed): before this,
    // StudentLoanGracePeriodScheduler flipped a loan to REPAYING the instant its
    // graceEndsAt elapsed with only a server-side log line -- zero borrower-facing
    // warning that the grace period was about to end and a real payment obligation was
    // about to start. Real, well-known student-loan-servicer practice (Navient/Nelnet/
    // MOHELA all send a "your grace period is ending soon, repayment begins on <date>"
    // notice roughly a week to a month before the first payment comes due) applied to
    // BRD's own grace period. Tracks whether that pre-end reminder has already fired for
    // this loan, so the scheduler's own polling doesn't re-notify on every tick.
    @Column(name = "grace_end_reminder_sent_at")
    var graceEndReminderSentAt: Instant? = null,

    // Real check-then-act "single claimable resource" guard: only one active loan per
    // user at a time, and both disburse and repay read-then-mutate this row -- same
    // reasoning `VupLoan` already needed `@Version` for.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", userId = "", level = StudentLoanLevel.UNDERGRADUATE,
        declaredAnnualHouseholdIncome = BigDecimal.ZERO, principalAmount = BigDecimal.ZERO,
        outstandingBalance = BigDecimal.ZERO, interestRate = 0.0, expectedGraduationDate = LocalDate.now(),
    )
}
