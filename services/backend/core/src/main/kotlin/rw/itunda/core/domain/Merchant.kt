package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class MerchantStatus { ACTIVE, SUSPENDED }

/**
 * A real, minimal merchant record -- registration + a settlement wallet reference.
 * Reuses the owner's existing MAIN wallet as the settlement wallet rather than
 * introducing a new WalletType, since AuthService.register already provisions one
 * for every user. See rw.itunda.merchant.MerchantService for the real subset of
 * docs/MERCHANT_SERVICES.md this implements (QR-style payment collection). Card
 * network/PSP integration stays genuinely blocked on a real commercial relationship
 * this repo has no path to certify (see MerchantService.chargeCard's real Luhn-
 * validated demo instead) -- webhooks and B2B payroll don't need one: webhooks are
 * real as of 2026-07-13 (webhookUrl below), and payroll (rw.itunda.merchant.
 * PayrollService, 2026-07-17) is a real WALLET-to-WALLET disbursement to an
 * employee's own itunda account, no external rail involved at all. kybVerified
 * (2026-07-17) is flipped by IdentityService.decide the same way User.kycVerified
 * is -- reusing the existing KYC submission/human-review pipeline with a
 * "BUSINESS_TIN" documentType rather than inventing a separate workflow, since
 * DemoKybVerificationService's real structural TIN pre-check and a human reviewer
 * are exactly the same shape of check identity verification already needed.
 */
@Entity
@Table(name = "merchants")
class Merchant(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "owner_user_id", nullable = false, unique = true, length = 64)
    val ownerUserId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(name = "business_name", nullable = false)
    var businessName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: MerchantStatus = MerchantStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "webhook_url", length = 500)
    var webhookUrl: String? = null,

    @Column(name = "kyb_verified", nullable = false)
    var kybVerified: Boolean = false,

    // Real lat/lng (2026-07-18), the foundation of itunda's own self-hosted maps effort
    // -- see rw.itunda.core.geo.GeoUtils and docs/TOSS_PARITY_MATRIX.md's Maps row.
    // Nullable: a merchant that hasn't set a location yet falls back to the pre-existing
    // flat-fee/no-proximity behavior, never a fabricated coordinate.
    @Column(nullable = true)
    var latitude: Double? = null,

    @Column(nullable = true)
    var longitude: Double? = null,
) {
    protected constructor() : this(id = "", ownerUserId = "", walletId = "", businessName = "")
}
