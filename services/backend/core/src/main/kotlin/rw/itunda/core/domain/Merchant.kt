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

    // Real Toss Payments-style grace-period key reissue (2026-07-28) -- see
    // MerchantService.generateApiKey's own doc comment for why this closes a real,
    // sourced gap the old hard-cutover rotation had. NULL means either no rotation has
    // ever happened, or the previous key's grace period has already elapsed (checked at
    // resolve time, not swept by a scheduler -- same "check the timestamp directly on
    // read" simplicity KeywordAlertQuietHours already establishes).
    @Column(name = "previous_api_key_hash", length = 64, nullable = true)
    var previousApiKeyHash: String? = null,

    @Column(name = "previous_api_key_expires_at", nullable = true)
    var previousApiKeyExpiresAt: Instant? = null,

    // Real Naver Pay-style boosted merchant cashback opt-in (2026-07-26) -- Naver Pay's
    // own real membership program pays "최대 5%" (up to 5%) back on real "N Pay+"-marked
    // purchases, well above the flat 1% every itunda QR payment already earns via
    // ShoppingCashbackService. Nullable: unset means this merchant hasn't opted in, the
    // pre-existing flat 1% ShoppingCashbackService.DEFAULT_CASHBACK_RATE stays
    // unchanged for every merchant that never sets one. Validated 0-5% inclusive at
    // write time (ShoppingCashbackService.MAX_CASHBACK_RATE) -- itunda's own real,
    // sourced ceiling, not an unbounded merchant-set discount that could drain
    // rewards_expense arbitrarily.
    @Column(name = "cashback_rate", precision = 6, scale = 4, nullable = true)
    var cashbackRate: BigDecimal? = null,

    // Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program,
    // 2026-07-31) -- Naver Pay's own real, currently-running campaign waives itunda-side
    // transaction fees for small/thin-margin merchants. Same nullable-override shape
    // `cashbackRate` already establishes: unset means this merchant pays
    // `MerchantService.feeRate`'s own standard rate unchanged; a real, itunda-computed
    // "small merchant" eligibility check (see `MerchantFeeWaiverService`'s own doc
    // comment) sets this to a real, honest 0% (a full waiver, matching Naver's own real
    // "지원" framing, not a fabricated partial discount) rather than requiring external
    // SME-certification data this codebase has no access to.
    @Column(name = "fee_rate_override", precision = 6, scale = 4, nullable = true)
    var feeRateOverride: BigDecimal? = null,

    // Real Baemin Club (배민클럽)-style participating-restaurant opt-in (2026-07-26) --
    // see EatsMembership.kt's own doc comment. Free delivery for a real active member
    // only ever applies here when true -- never a blanket waiver across every
    // restaurant, mirroring Baemin's own real "참여 가게" scoping.
    @Column(name = "participates_in_eats_membership", nullable = false)
    var participatesInEatsMembership: Boolean = false,

    // Real 배달의민족 예약주문 (scheduled ordering) opt-in (2026-07-26) -- sourced from
    // Baemin's own real, actively-growing feature (ceo.baemin.com's own seller guide:
    // "가게 관리 > 예약주문 설정" -- not every restaurant supports it, only the ones
    // whose owner explicitly turns it on). Same real per-restaurant opt-in shape
    // `participatesInEatsMembership` already establishes -- never a blanket capability
    // every restaurant is forced into. See EatsOrderService.placeOrder's own doc
    // comment for the real scheduling window this enables.
    @Column(name = "accepts_scheduled_orders", nullable = false)
    var acceptsScheduledOrders: Boolean = false,
) {
    protected constructor() : this(id = "", ownerUserId = "", walletId = "", businessName = "")
}
