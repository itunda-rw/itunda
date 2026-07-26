package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real Naver Smart Store-style "알림받기" (follow a store for promotional notices) --
 * sourced from Naver's own real, published seller-facing feature (관심고객/"interested
 * customers" is the real follower count shown to a seller; opted-in customers receive
 * real discount/event/new-product broadcasts). Distinct from [ProductFavorite] and
 * [EatsFavorite]: those are private, personal bookmarks with no notification behavior
 * at all; a `MerchantFollow` is an explicit opt-in to receive the merchant's OWN
 * broadcast messages, the real customer-relationship-management primitive Naver's own
 * feature exists to support. See `rw.itunda.merchant.MerchantFollowService` for the
 * full account, including the real anti-spam bound on how often a merchant can
 * broadcast to their followers.
 */
@Entity
@Table(name = "merchant_follows")
class MerchantFollow(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", merchantId = "")
}
