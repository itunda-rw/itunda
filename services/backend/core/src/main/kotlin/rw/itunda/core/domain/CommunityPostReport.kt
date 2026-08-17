package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

enum class CommunityReportReason { SPAM, HARASSMENT, PROHIBITED_CONTENT, PERSONAL_INFO_EXPOSURE, OTHER }

/**
 * Real 당근마켓 동네생활 신고하기 (report a neighborhood-life post) -- the third and
 * final content-moderation gap named across Sections 140 (`MarketplaceListingReport`)
 * and 141 (`EatsReviewReport`): those two closed reporting for listings and reviews,
 * community posts were the one remaining content type with none at all. Sourced from
 * the same real Karrot moderation behavior `MarketplaceListingReport.kt`'s own doc
 * comment already establishes (real seller-forum threads on daangn.com/kr/community
 * describing silent auto-hide with zero notification) -- 동네생활 is Karrot's own
 * second core surface, moderated under the identical real community-report system as
 * its marketplace, not a separate product with separate rules.
 *
 * `CommunityService.reportPost` mirrors `MarketplaceService.reportListing` exactly:
 * once `REPORT_THRESHOLD` distinct reporters accumulate on a still-`ACTIVE` post, its
 * status flips straight to the pre-existing `CommunityPostStatus.REMOVED` -- the exact
 * same real effect an author's own `removePost` already has (gone from browse/search/
 * myNeighborhood/nearby/upcomingMeetups, every one of which already filters on
 * `status = ACTIVE`, so zero read-path changes were needed here unlike Sections
 * 140/141's `hidden` column) -- deliberately no notification to the author, matching
 * the sourced real silence.
 *
 * DB-unique on (post, reporter) -- one real report per person per post, same
 * concurrency-safe discipline `CommunityLike`'s own doc comment already establishes for
 * this exact (post, user) shape.
 */
@Entity
@Table(name = "community_post_reports", uniqueConstraints = [UniqueConstraint(columnNames = ["post_id", "reporter_id"])])
class CommunityPostReport(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "post_id", nullable = false, length = 64)
    val postId: String,

    @Column(name = "reporter_id", nullable = false, length = 64)
    val reporterId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    val reason: CommunityReportReason,

    @Column(nullable = true, length = 500)
    val details: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", postId = "", reporterId = "", reason = CommunityReportReason.OTHER)
}
