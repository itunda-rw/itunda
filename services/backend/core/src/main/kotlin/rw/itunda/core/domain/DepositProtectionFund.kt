package rw.itunda.core.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

// Real itunda Deposit Protection Fund (2026-08-11) -- the user's own real point: this
// codebase already builds real, working internal mechanics for institutions it doesn't
// have a genuine external connection to (VUP.kt's government microloan terms, RSE
// stock pricing, SACCO/Ikimina cooperative accounts) rather than refusing to build them
// -- so a banking-style reserve is the same shape, not a special case. What makes this
// HONEST rather than a fabricated claim is the same distinction those other features
// already draw: the mechanics are real (a real ledger-backed reserve that really grows,
// really caps real per-user coverage), but it is disclosed everywhere it's shown as
// itunda's OWN internal scheme, not a filing with Rwanda's central bank (BNR) or any
// government-backed deposit insurance program. See docs/TOSS_PARITY_MATRIX.md's own
// confirmation that itunda holds no real banking license.
//
// Singleton row (id = "system") rather than per-user: a deposit protection fund is a
// single pooled reserve covering every depositor collectively, the same real shape a
// government deposit insurance fund has -- not a per-user balance like InterestJar or
// InsurancePremiumFund.
@Entity
@Table(name = "deposit_protection_fund")
class DepositProtectionFund(
    @Id @Column(length = 16) val id: String = "system",
    @Column(name = "reserve_balance", nullable = false, precision = 18, scale = 2) var reserveBalance: BigDecimal,
    // itunda's own set policy, not a claimed real BNR figure -- disclosed as such in
    // every client that renders it (see coverageCapPerUser's own doc comment on the
    // DTO). A round, clearly-itunda's-own number rather than mirroring a real scheme's
    // published limit this project has no way to verify.
    @Column(name = "coverage_cap_per_user", nullable = false, precision = 18, scale = 2) var coverageCapPerUser: BigDecimal = BigDecimal("500000"),
    // 50 basis points (0.5%) annual, of total covered deposits, contributed into the
    // reserve -- itunda's own chosen rate, not a claimed real regulatory requirement.
    @Column(name = "contribution_rate_bps", nullable = false) var contributionRateBps: Int = 50,
    @Column(name = "last_contribution_at") var lastContributionAt: Instant? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
    @Version @Column(nullable = false) var version: Long = 0,
) {
    protected constructor() : this(id = "system", reserveBalance = BigDecimal.ZERO)
}
