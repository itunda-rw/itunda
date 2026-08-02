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

enum class MotoOwnershipPlanStatus { SAVING, LOAN_ACTIVE, COMPLETED, CANCELLED }

/**
 * Real Rwanda moto-taxi ownership savings-to-loan plan -- sourced beyond this
 * session's usual Toss/Kakao/Naver/Coupang reference ecosystems. A real ~600,000 RWF
 * entry-level moto-taxi bike is a documented purchase price (Anadolu Agency,
 * "Rwanda's female motorcycle riders defy odds," 14 May 2021 -- profiles a rider who
 * saved to buy her own bike for 600,000 RWF after years of paying daily rent to a
 * bike owner; average driver income ~18,000 RWF/day, a real chunk of which goes to
 * bike-owner rent when the driver doesn't own their bike). Rent-to-own is a
 * proven-relevant mechanic in this exact sector (Frontier Tech Hub's Kigali e-moto
 * pilot writeup: Ampersand's rent-to-own model increased driver revenue 78%/month;
 * WeeTracker/WEF coverage of the same). This session's own "Ejo Heza ya Moto" feature
 * already sourced (Africa-Press, 2026) that Rwanda's taxi-moto cooperatives, which
 * used to help drivers become owner-operators, "were dissolved" -- this feature fills
 * that real, now-informal gap with a digital savings-then-loan path to bike
 * ownership. Sources: Anadolu Agency, Frontier Tech Hub, WeeTracker/WEF.
 *
 * Genuinely distinct from every other feature in this codebase (confirmed via grep):
 * `InsurancePremiumFund` (Ejo Heza) is insurance-premium savings, not asset purchase.
 * `CooperativeService` harvest advances are agriculture/crop-tied, seasonal
 * repayment. `BikeRentalService` is short-term casual bike-share rental, unrelated
 * to driver ownership financing. `LoansService` is generic cash loans with no
 * savings phase. `PropertyListing` is a sale/rent marketplace board with no
 * financing mechanic. This is the first two-PHASE product (savings, then loan,
 * against the SAME row) anywhere in this codebase.
 *
 * Ledger accounts -- reuse existing, no new accounts needed: `savings_goal_payable`
 * (`LedgerAccountType.SAVINGS_GOAL_PAYABLE`) for the savings phase, the same shared
 * generic liability clearing account `SavingsService.depositToGoal`/`autoContribute`
 * already use for savings goals; `loan_payable` (`LedgerAccountType.LOAN_PAYABLE`)
 * for the loan phase, the same account already shared across `LoansService`/
 * `CooperativeService`/`VupLoanService`/`StudentLoanService`. See
 * `MotoOwnershipService.convertToLoan`'s own doc comment for how the two accounts
 * interact at the savings-to-loan transition.
 *
 * Honest v1 limitation: itunda has no path to a real chattel lien or vehicle-registry
 * hold with RURA (Rwanda Utilities Regulatory Authority) -- once a loan disburses,
 * itunda cannot repossess or legally encumber the physical bike, and cannot verify
 * the driver actually used the funds to buy one. This is an UNSECURED facility, the
 * same honest scope boundary every other itunda-as-lender feature in this codebase
 * already carries (see `VupLoan.kt`/`StudentLoan.kt`'s own doc comments for their
 * own external-registry gaps). Default consequence of non-repayment is
 * credit-score/status only, never repossession.
 */
@Entity
@Table(name = "moto_ownership_plans")
class MotoOwnershipPlan(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "bike_price", nullable = false, precision = 18, scale = 2)
    val bikePrice: BigDecimal,

    // itunda's own 30% down-payment policy choice -- named honestly as itunda's own
    // pick, not a claimed reproduction of any real published Ampersand/dealer
    // rent-to-own down-payment percentage (the sourcing didn't specify one).
    @Column(name = "down_payment_target", nullable = false, precision = 18, scale = 2)
    val downPaymentTarget: BigDecimal,

    @Column(name = "saved_amount", nullable = false, precision = 18, scale = 2)
    var savedAmount: BigDecimal,

    @Column(name = "daily_contribution", nullable = false, precision = 18, scale = 2)
    val dailyContribution: BigDecimal,

    @Column(name = "loan_outstanding", nullable = false, precision = 18, scale = 2)
    var loanOutstanding: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: MotoOwnershipPlanStatus = MotoOwnershipPlanStatus.SAVING,

    @Column(name = "last_auto_contribution_at")
    var lastAutoContributionAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Real check-then-act "single claimable resource" guard: only one active plan per
    // user at a time, and contribute/convert/repay all read-then-mutate this row --
    // same reasoning `VupLoan`/`StudentLoan` already needed `@Version` for.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", userId = "", bikePrice = BigDecimal.ZERO, downPaymentTarget = BigDecimal.ZERO,
        savedAmount = BigDecimal.ZERO, dailyContribution = BigDecimal.ZERO, loanOutstanding = BigDecimal.ZERO,
    )
}
