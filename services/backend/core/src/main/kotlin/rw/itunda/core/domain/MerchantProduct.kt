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

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", merchantId = "", name = "", price = BigDecimal.ZERO)
}
