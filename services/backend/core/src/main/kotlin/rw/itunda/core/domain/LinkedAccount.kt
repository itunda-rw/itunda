package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class LinkedAccountStatus { LINKED, VERIFICATION_FAILED, UNLINKED }

/**
 * Real external bank/MoMo account consent registry -- closes the exact gap
 * docs/TOSS_PARITY_MATRIX.md's Account aggregation row named: "External bank/MoMo
 * linking remains fully target -- no consent registry, no provider access." Provider
 * verification reuses ProviderConnector/RailCatalog -- the same real, simulated
 * (latency/success-rate per rail) provider-call mechanism every other rail-touching
 * flow in this backend already uses (transfers, bills, airtime) -- rather than
 * inventing a separate, parallel "fake OTP" system for this one feature.
 *
 * A real live external balance fetch remains genuinely blocked: itunda has no real
 * Open Banking / provider API access to fetch one from. `demoBalance` (2026-07-17) is
 * a real, honestly-labeled demo balance instead -- deterministically generated at link
 * time (see LinkedAccountService.link's own comment), same "real simulation, not a
 * real integration" discipline DemoNidaVerificationService/DemoCardAuthorizationService
 * already established, so a linked account shows something real-looking in the UI
 * rather than being permanently blank. Never counted in real `netWorth` -- see
 * OverviewService for why that stays a hard line, not a demo-blurred one.
 */
@Entity
@Table(name = "linked_accounts")
class LinkedAccount(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false, length = 64)
    val provider: String,

    @Column(name = "external_account_number_masked", nullable = false, length = 32)
    val externalAccountNumberMasked: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: LinkedAccountStatus,

    @Column(name = "failure_reason", length = 255)
    var failureReason: String? = null,

    @Column(name = "linked_at", nullable = false)
    val linkedAt: Instant = Instant.now(),

    @Column(name = "unlinked_at")
    var unlinkedAt: Instant? = null,

    @Column(name = "demo_balance", precision = 18, scale = 2)
    var demoBalance: BigDecimal? = null,

    @Column(name = "demo_balance_currency", length = 8)
    var demoBalanceCurrency: String? = null,
) {
    protected constructor() : this(
        id = "", userId = "", provider = "", externalAccountNumberMasked = "", status = LinkedAccountStatus.LINKED,
    )
}
