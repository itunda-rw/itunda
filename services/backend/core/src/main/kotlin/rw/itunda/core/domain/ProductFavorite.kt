package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real product wishlist entry (2026-07-20) -- the real "찜하기"/wishlist every real
 * Coupang/Naver/Kakao/Toss Shopping-style app has, mirroring [EatsFavorite]'s exact
 * shape (just a product instead of a restaurant). Deliberately minimal: the (buyer,
 * product) pair and when it was saved, nothing else. Real DB unique constraint on
 * (user_id, product_id) backs the same application-level "add is idempotent" check
 * `ProductFavoriteService.addFavorite` makes.
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

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", productId = "")
}
