package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * A real product wishlist entry (2026-07-20) -- the real "찜하기"/wishlist every real
 * Coupang/Naver/Kakao/Toss Shopping-style app has, mirroring [EatsFavorite]'s exact
 * shape (just a product instead of a restaurant). Real DB unique constraint on
 * (user_id, product_id) backs the same application-level "add is idempotent" check
 * `ProductFavoriteService.addFavorite` makes.
 *
 * `priceAtLastCheck` backs the real Naver Shopping 가격 변동 알림 (price-drop alert,
 * item 227) added 2026-08-01 -- see `ProductPriceDropScheduler`'s own doc comment. Set
 * to the product's real price at favorite time, then updated to the product's real
 * current price every time a drop notification actually fires, so a further later drop
 * notifies again but the same already-notified price never re-fires.
 */
@Entity
@Table(name = "product_favorites")
class ProductFavorite(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(name = "price_at_last_check", nullable = false, precision = 18, scale = 2)
    var priceAtLastCheck: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", productId = "")
}
