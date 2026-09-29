package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class MerchantUpdateLabel { NOTICE, EVENT, PROMO }

/**
 * Real business news/updates feed (itunda Maps redesign, 2026-08-28, direct Naver Map
 * reference: the place-detail 소식 tab -- a real business owner posting dated updates,
 * e.g. holiday-hours notices, promo announcements). No such entity existed anywhere in
 * this backend before this pass -- unlike most of this session's redesign gaps, this
 * one is genuinely new, not an unwired UI over existing data.
 *
 * `periodStart`/`periodEnd` are both nullable and independent -- a plain NOTICE rarely
 * has a real date range at all (matches the reference's own mix: some posts show a
 * real "기간" chip, most don't), while an EVENT/PROMO post typically does. `likeCount`
 * is a real denormalized counter backed by `MerchantUpdateLike` (one real vote per
 * (update, user), see `MerchantUpdateService.toggleLike`'s own doc comment) -- same
 * shape `EatsReview.helpfulCount`/`EatsReviewHelpfulVote` already establish.
 */
@Entity
@Table(name = "merchant_updates")
class MerchantUpdate(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val label: MerchantUpdateLabel,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, length = 2000)
    val body: String,

    @Column(name = "period_start")
    val periodStart: Instant? = null,

    @Column(name = "period_end")
    val periodEnd: Instant? = null,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", merchantId = "", label = MerchantUpdateLabel.NOTICE, title = "", body = "")
}
