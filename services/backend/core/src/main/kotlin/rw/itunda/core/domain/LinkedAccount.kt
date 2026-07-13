package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
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
 * Deliberately never stores or surfaces a live external balance: itunda has no real
 * Open Banking / provider API access to fetch one from, and fabricating a number
 * would misrepresent this as more integrated than it is. A linked account is real
 * consent-and-registry state, honestly presented as "connected, no live balance
 * available" -- see OverviewService for how this is surfaced without contributing to
 * netWorth.
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
) {
    protected constructor() : this(
        id = "", userId = "", provider = "", externalAccountNumberMasked = "", status = LinkedAccountStatus.LINKED,
    )
}
