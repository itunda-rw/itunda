package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

enum class CommunityPostStatus { ACTIVE, REMOVED }

/**
 * A real 동네생활 (Danggeun/Karrot "Neighborhood Life")-style community board -- Karrot's
 * own second core surface alongside its marketplace (already real here as `Listing`/
 * `MarketplaceService`), explicitly named by the user alongside 당근알바/당근부동산 as a
 * distinct neighborhood-services product, not just more marketplace listings. Unlike a
 * `Listing` (an item for sale), a post here is a real local community conversation --
 * a question, a piece of neighborhood news, a recommendation, a lost & found notice, or
 * a meetup -- with real comments and likes, not a price/buy-sell flow.
 *
 * `likeCount`/`commentCount` are real cached counters, updated transactionally in the
 * same transaction as the like-toggle/comment-create action, deliberately NOT computed
 * at read time -- unlike `EatsReview`'s aggregate rating (viewed on one restaurant page
 * at a time), this counter is shown on every post in a paginated browse list, and
 * computing it per-post at read time would reintroduce exactly the kind of N+1 this
 * project's own performance sweep already found and fixed elsewhere (see
 * `MessagingService.listConversations`/`GroupMessagingService.listMyGroups`).
 *
 * Real optional lat/lng (same `GeoUtils` foundation Marketplace/Eats already use) lets a
 * post opt into a real proximity ("near me") browse, matching Marketplace's own honest
 * "no address/district field on `User`, so hyperlocal filtering is opt-in per-post, not
 * automatic" scope.
 */
@Entity
@Table(name = "community_posts")
class CommunityPost(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "author_id", nullable = false, length = 64)
    val authorId: String,

    @Column(nullable = false, length = 32)
    var category: String,

    // Real 동네생활 topic chip (itunda Hood redesign, 2026-08-28, direct user
    // reference) -- a genuinely different axis from `category` above: `category` is
    // functional (question/news/lost-found/meetup/group-buy/free-talk), this is a
    // lifestyle topic (취미/여가, 운동/스포츠, 맛집/음식, ...) matching the real
    // reference's own separate topic-chip filter row. Optional -- a post can carry
    // no topic, same as it could always carry no location.
    @Column(nullable = true, length = 32)
    var topic: String? = null,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(nullable = false, length = 4000)
    var body: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: CommunityPostStatus = CommunityPostStatus.ACTIVE,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,

    @Column(name = "comment_count", nullable = false)
    var commentCount: Long = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = true)
    var latitude: Double? = null,

    @Column(nullable = true)
    var longitude: Double? = null,

    // Real hyperlocal neighborhood (2026-07-20) -- see Listing.neighborhood's own doc
    // comment for the full account; identical cached-at-creation shape here.
    @Column(nullable = true, length = 120)
    var neighborhood: String? = null,

    // Real 같이해요 (join-together) meetup group chat (2026-07-24) -- closes
    // docs/DESIGN_REFERENCES.md Section 4 recommendation #4's "joining their group chat
    // requires an explicit 참여하기 tap." Lazily created on the FIRST real join (not at
    // post-creation time) so a meetup nobody ever joins never creates an empty
    // GroupConversation row. Only meaningful when category == "meetup"; null for every
    // other post category, forever.
    @Column(name = "group_conversation_id", nullable = true, length = 64)
    var groupConversationId: String? = null,

    // Real 당근모임-style structured meetup fields (2026-07-25) -- closes the gap
    // between the freeform join-anyone-anytime 같이해요 group chat above and Karrot's
    // own real "모임" product, which spun out specifically because it added mandatory
    // date-setting and a real capacity cap on top of the original freeform post type.
    // Both only meaningful when category == "meetup", forever null for every other
    // post category -- see CommunityService.createPost/joinMeetup's own doc comments
    // for the real validation/enforcement this backs.
    @Column(name = "event_date", nullable = true)
    var eventDate: Instant? = null,

    // Null means unlimited (the pre-existing, unchanged behavior for every meetup
    // created before this field existed) -- a real, honest default, not a fabricated cap.
    @Column(nullable = true)
    var capacity: Int? = null,

    // Real self-hosted AI 모임 summary (itunda Hood redesign, 2026-08-28) -- see
    // HoodAiSummaryService's own doc comment. Only ever generated for category ==
    // "meetup"; null for every other post, forever.
    @Column(name = "ai_summary", nullable = true, length = 500)
    var aiSummary: String? = null,

    @Column(name = "ai_summary_generated_at", nullable = true)
    var aiSummaryGeneratedAt: Instant? = null,

    // Comments, likes, removal, and first-meetup initialization all update this row.
    // Versioning protects its cached counters and group linkage from lost updates.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", authorId = "", category = "", title = "", body = "")
}
