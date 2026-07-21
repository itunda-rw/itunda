package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
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

    // Real, merchant-set category/cuisine (2026-07-19) -- the foundation of restaurant
    // categories + search/filter for Eats (and Shopping, since both browse the same
    // Merchant directory). Free-form string, not an enum: a real merchant knows their
    // own category ("Rwandan", "Chinese", "Bakery", ...) better than a fixed list could
    // anticipate, same reasoning as businessName itself being free-form. Nullable: an
    // unset category falls back to "uncategorized" browse behavior, never a fabricated
    // default.
    @Column(length = 64, nullable = true)
    var category: String? = null,

    // Real restaurant-card enrichment (2026-07-21) -- closes the "browse card has no
    // photo/minOrderAmount" gap named in docs/DESIGN_REFERENCES.md's Eats section
    // (Baemin/Coupang Eats both put these directly on the list card so restaurants are
    // comparable before opening any of them). A plain URL string, not an upload/storage
    // pipeline -- same "real, not fabricated" bar as everywhere else: itunda has no
    // image-hosting system to invent one, but a merchant-set URL to their own hosted
    // photo is genuinely real, same category as webhookUrl. Nullable: an unset photo
    // falls back to the client's existing generic storefront icon, never a fabricated
    // image.
    @Column(name = "photo_url", length = 500, nullable = true)
    var photoUrl: String? = null,

    // Real merchant-set minimum order amount (2026-07-21) -- nullable: unset means no
    // minimum, the pre-existing behavior for every merchant that hasn't opted in yet.
    @Column(name = "min_order_amount", precision = 18, scale = 2, nullable = true)
    var minOrderAmount: BigDecimal? = null,

    // Real external-checkout API key (2026-07-21) -- mirrors Partner.apiKeyHash's exact
    // pattern (SHA-256 hash, never the raw key, which is shown to the merchant exactly
    // once at generation time). Nullable: unset means this merchant hasn't opted into
    // the external "Pay with itunda" checkout API -- their existing QR/POS/Eats/Commerce
    // flows are completely unaffected either way. See PaymentsApiController's own doc
    // comment for the real Toss Payments feature this mirrors (a merchant's own backend
    // server calling itunda directly, with no itunda user login involved at all).
    @Column(name = "api_key_hash", length = 64, nullable = true)
    var apiKeyHash: String? = null,
) {
    protected constructor() : this(id = "", ownerUserId = "", walletId = "", businessName = "")
}
