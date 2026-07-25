package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * A real merchant product-catalog row -- closes the "Toss Place" gap from the expanded
 * 2026-07-17 goal. Toss Place bundles a real in-store register (product catalog + cart
 * + QR/card checkout) with hardware this repo has no path to build or certify -- but
 * the register *software* needs no external access at all: it's a product catalog plus
 * the exact same real `POST /qr/generate`/`POST /card/charge` collection flows
 * MerchantService already proved out for QR Pay, just checking out a cart total instead
 * of a single typed-in amount. See rw.itunda.merchant.MerchantProductService.
 *
 * **Real product images + discount pricing added 2026-07-21**, closing
 * `docs/DESIGN_REFERENCES.md` Section 5's #4 recommendation (Coupang/Baymard-sourced: a
 * real product card needs an image and a current/original price pair, not just a bare
 * price). `imageUrl` is deliberately a merchant-supplied external URL, not an uploaded
 * file -- this backend has no file-upload/storage layer anywhere (confirmed by repo-wide
 * search before building), so "bring your own publicly-hosted image URL" is the honest
 * v1 scope, not a fake upload pipeline. `originalPrice` is merchant-entered (must be
 * strictly greater than `price` -- see `MerchantProductService`'s validation);
 * `discountPercent` is deliberately NOT client-supplied -- it's computed server-side from
 * `price`/`originalPrice` at write time and stored, so it can never drift from the two
 * prices it's derived from (the same "never trust the client with a derived number"
 * discipline `OrderService` already applies to price resolution at checkout).
 *
 * **`description` added 2026-07-21**, backing the new dedicated product-detail screen
 * (all 3 clients) -- a merchant-entered free-text field, trimmed and bounded to 2000
 * chars server-side (`MerchantProductService`), same "STRICT_TRANS_TABLES will genuinely
 * throw on an over-length insert" discipline `ProductReviewService.submitReview` already
 * established for its own comment field.
 */
@Entity
@Table(name = "merchant_products")
class MerchantProduct(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, precision = 18, scale = 2)
    var price: BigDecimal,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "image_url", length = 2048)
    var imageUrl: String? = null,

    @Column(name = "original_price", precision = 18, scale = 2)
    var originalPrice: BigDecimal? = null,

    @Column(name = "discount_percent")
    var discountPercent: Int? = null,

    // Real merchant-entered description (2026-07-21) -- closes
    // docs/DESIGN_REFERENCES.md Section 5 recommendation #6 (a dedicated product-detail
    // screen needs something longer-form than the catalog card's name/price/image).
    // Free text, nullable: an unset description just means the detail screen falls back
    // to the name/price/image it already has, never a fabricated blurb.
    @Column(length = 2000)
    var description: String? = null,

    // Real bookable-service duration (2026-07-25) -- closes the "local business profile
    // + real appointment booking" gap independently converged on by Naver Smart Place,
    // Kakao Hair Shop, and Karrot's Business Profile research. A product with a real
    // durationMinutes set (e.g. "Haircut", 30) is bookable via MerchantBookingService;
    // null (the pre-existing default for every current product) just means "not a
    // bookable service," never a fabricated duration on a physical good.
    @Column(name = "duration_minutes")
    var durationMinutes: Int? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", merchantId = "", name = "", price = BigDecimal.ZERO)
}
