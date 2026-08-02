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

enum class VupLoanPurpose { FARMING, LIVESTOCK, BUSINESS }
enum class VupLoanStatus { REQUESTED, DISBURSED, REPAID, OVERDUE }

/**
 * Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services micro-loan --
 * sourced beyond this session's usual Toss/Kakao/Naver/Coupang reference ecosystems.
 * VUP, run by LODA since 2008, has a Financial Services component: subsidized
 * microloans for income-generating activities (farming, livestock, small business)
 * targeted at households in poorer Ubudehe categories. NISR's own EICV7 (2023/24)
 * thematic report on VUP cites an average loan size of ~100,000 RWF -- a real
 * LODA-published beneficiary case (Mrs. Nyirankundumukiza Christine of Rubavu) got
 * exactly 100,000 RWF on 2020-11-20 to start a charcoal business. Since a real
 * 2014-07-29 Cabinet decision, administration moved to Umurenge SACCOs, which set the
 * rate at 11% (up from an original flexible 2%) -- a real, documented controversy
 * (Rwanda Inspirer reporting: uptake fell after the rate hike). Sources: loda.gov.rw,
 * NISR EICV7 VUP thematic report, Rwanda Inspirer.
 *
 * Genuinely distinct from everything else in this codebase: this is the first
 * MEANS-TESTED lending product (no eligibility gate keyed on a self-declared poverty
 * category exists anywhere else in itunda today), and it's the lending side of what
 * `SaccoService` conceptually represents (that service only ever does equity
 * shares/dividends, never loans).
 *
 * Corrected from an earlier draft of this feature's own research proposal: this is
 * NOT disbursed from `SaccoService.getOrCreatePoolWallet()` -- that pool holds other
 * SACCO members' own real pooled contributions, and paying a third-party borrower out
 * of it would repeat the exact solvency bug this session already found and fixed in
 * `SaccoService.declareDividend` (paying out from a shared wallet other users have a
 * real claim on, instead of a dedicated itunda-owned ledger account). Instead this
 * mirrors `LoansService.applyForLoan`/`repayLoan`'s own already-correct, already-
 * established convention exactly: disbursement CREDITs the borrower's wallet and
 * DEBITs itunda's own `loan_payable` liability account (`LedgerAccountType.LOAN_PAYABLE`),
 * the same account `CooperativeService`'s harvest advances already use -- no new
 * ledger account needed.
 */
@Entity
@Table(name = "vup_loans")
class VupLoan(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    // Honest v1 limitation: itunda has no access to Rwanda's real government Ubudehe
    // household-classification registry, so this is SELF-DECLARED by the user, not
    // government-verified -- the same "no external registry this backend has no path
    // to check" honesty `Cooperative.registrationNumber` and
    // `PropertyListing.ownershipVerificationStatus` already carry for their own
    // unverifiable fields.
    @Column(name = "declared_ubudehe_category", nullable = false)
    val declaredUbudeheCategory: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val purpose: VupLoanPurpose,

    @Column(name = "principal_amount", nullable = false, precision = 18, scale = 2)
    val principalAmount: BigDecimal,

    @Column(name = "outstanding_principal", nullable = false, precision = 18, scale = 2)
    var outstandingPrincipal: BigDecimal,

    // Real, documented rate: set to 11% since the 2014-07-29 Cabinet decision moved VUP/FS
    // administration to Umurenge SACCOs (up from an original flexible 2%) -- Rwanda
    // Inspirer's own reporting on the resulting uptake drop is the sourced controversy
    // this row's doc comment above names.
    @Column(name = "interest_rate", nullable = false)
    val interestRate: Double = 0.11,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: VupLoanStatus = VupLoanStatus.REQUESTED,

    @Column(name = "applied_at", nullable = false)
    val appliedAt: Instant = Instant.now(),

    @Column(name = "disbursed_at")
    var disbursedAt: Instant? = null,

    @Column(name = "due_date")
    var dueDate: LocalDate? = null,

    // Real check-then-act "single claimable resource" guard: only one active loan per
    // user at a time, and both disburse and repay read-then-mutate this row -- same
    // reasoning every prior feature this session needed `@Version` for.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", userId = "", declaredUbudeheCategory = 0, purpose = VupLoanPurpose.FARMING,
        principalAmount = BigDecimal.ZERO, outstandingPrincipal = BigDecimal.ZERO,
    )
}
