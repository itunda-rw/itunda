package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.CommunityPost
import rw.itunda.core.domain.CommunityPostStatus
import java.time.Instant
import java.util.Optional

interface CommunityPostRepository : JpaRepository<CommunityPost, String> {
    fun findByStatusOrderByCreatedAtDesc(status: CommunityPostStatus, pageable: Pageable): Page<CommunityPost>
    fun findByStatusAndCategoryOrderByCreatedAtDesc(status: CommunityPostStatus, category: String, pageable: Pageable): Page<CommunityPost>
    fun findByAuthorIdOrderByCreatedAtDesc(authorId: String, pageable: Pageable): Page<CommunityPost>

    // Real 동네생활 topic-chip filter (see CommunityPost.topic's own doc comment) --
    // a real, separate axis from category, same query-shape precedent as the
    // category filters above.
    fun findByStatusAndTopicOrderByCreatedAtDesc(status: CommunityPostStatus, topic: String, pageable: Pageable): Page<CommunityPost>
    fun findByStatusAndCategoryAndTopicOrderByCreatedAtDesc(
        status: CommunityPostStatus,
        category: String,
        topic: String,
        pageable: Pageable,
    ): Page<CommunityPost>

    // Real proximity "near me" browse (see CommunityPost's own doc comment) -- same
    // bounded-candidate-set-then-Haversine-in-app shape ListingRepository's own note
    // already established, not a real geospatial DB index.
    fun findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(status: CommunityPostStatus): List<CommunityPost>

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see CommunityService.
    // myNeighborhood and User.neighborhood's own doc comments.
    fun findByStatusAndNeighborhoodOrderByCreatedAtDesc(status: CommunityPostStatus, neighborhood: String, pageable: Pageable): Page<CommunityPost>
    fun findByStatusAndNeighborhoodAndCategoryOrderByCreatedAtDesc(
        status: CommunityPostStatus,
        neighborhood: String,
        category: String,
        pageable: Pageable,
    ): Page<CommunityPost>

    // Real dual-neighborhood support (2026-08-04) -- see User.secondNeighborhood's own doc comment.
    fun findByStatusAndNeighborhoodInOrderByCreatedAtDesc(status: CommunityPostStatus, neighborhoods: Collection<String>, pageable: Pageable): Page<CommunityPost>
    fun findByStatusAndNeighborhoodInAndCategoryOrderByCreatedAtDesc(
        status: CommunityPostStatus,
        neighborhoods: Collection<String>,
        category: String,
        pageable: Pageable,
    ): Page<CommunityPost>

    // Real 당근모임-style "upcoming" browse (2026-07-25) -- see CommunityPost
    // .eventDate's own doc comment. Soonest-first, and only ever real future meetups --
    // a past eventDate just means the meetup already happened, not a fabricated filter.
    @Query(
        "SELECT p FROM CommunityPost p WHERE p.status = :status AND p.category = 'meetup' " +
            "AND p.eventDate IS NOT NULL AND p.eventDate > :now ORDER BY p.eventDate ASC",
    )
    fun findUpcomingMeetups(@Param("status") status: CommunityPostStatus, @Param("now") now: Instant, pageable: Pageable): Page<CommunityPost>

    // Real bug found live (2026-08-02): CommunityService.joinMeetup's own capacity
    // check ("member count >= post.capacity") reads the current member count, then
    // separately creates a new GroupConversationMember row -- classic TOCTOU. Two
    // different users concurrently joining the last open spot on a capped meetup/
    // group-buy could both read a count one below capacity before either of their
    // inserts committed, and both get admitted, overrunning the real, advertised
    // capacity cap (MAX_GROUP_BUY_CAPACITY for group-buy). The (group_conversation_id,
    // user_id) unique constraint only stops the SAME user joining twice, not two
    // different users both squeezing into the last spot. Fixed the same way this
    // codebase's own "reject if already exists"/capacity-race precedent works: lock
    // this post row to serialize concurrent joins against it, then re-check capacity
    // under that lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CommunityPost p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<CommunityPost>

    // Real short-query fallback (2026-08-14) -- see FullTextSearchUtil's own doc comment.
    @Query("SELECT p FROM CommunityPost p WHERE p.status = :status AND LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun searchShort(@Param("status") status: CommunityPostStatus, @Param("q") q: String, pageable: Pageable): Page<CommunityPost>

    // Real relevance-ranked full-text search (2026-08-14) -- see
    // MerchantProductRepository.searchFullText's own doc comment for the full "why".
    @Query(
        value = "SELECT p.* FROM community_posts p WHERE p.status = :#{#status.name()} " +
            "AND MATCH(p.title, p.body) AGAINST (:booleanQuery IN BOOLEAN MODE) " +
            "ORDER BY MATCH(p.title, p.body) AGAINST (:booleanQuery IN BOOLEAN MODE) DESC",
        countQuery = "SELECT COUNT(*) FROM community_posts p WHERE p.status = :#{#status.name()} " +
            "AND MATCH(p.title, p.body) AGAINST (:booleanQuery IN BOOLEAN MODE)",
        nativeQuery = true,
    )
    fun searchFullText(@Param("status") status: CommunityPostStatus, @Param("booleanQuery") booleanQuery: String, pageable: Pageable): Page<CommunityPost>
}
