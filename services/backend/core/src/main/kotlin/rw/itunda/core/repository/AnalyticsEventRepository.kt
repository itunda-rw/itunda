package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import rw.itunda.core.domain.AnalyticsEvent
import java.time.Instant

interface AnalyticsEventRepository : JpaRepository<AnalyticsEvent, String> {
    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.eventName = :eventName AND e.createdAt >= :since")
    fun countByEventNameSince(eventName: String, since: Instant): Long

    @Query("SELECT COUNT(DISTINCT e.userId) FROM AnalyticsEvent e WHERE e.eventName = :eventName AND e.createdAt >= :since")
    fun countDistinctUsersByEventNameSince(eventName: String, since: Instant): Long

    @Query("SELECT COUNT(DISTINCT e.userId) FROM AnalyticsEvent e WHERE e.createdAt >= :since")
    fun countDistinctUsersSince(since: Instant): Long

    // Real per-user first-occurrence lookup -- backs the "did tapping the coop rail
    // correlate with a return visit" question in AnalyticsController.getSummary. Kept
    // as a plain per-user existence check (called from Kotlin, not one large JOIN
    // query) since itunda's real event volume is nowhere near the scale where that
    // would be the wrong call, and a plain boolean is far easier to verify correct
    // than a hand-written retention JOIN.
    fun findFirstByUserIdAndEventNameOrderByCreatedAtAsc(userId: String, eventName: String): AnalyticsEvent?

    fun existsByUserIdAndCreatedAtAfter(userId: String, after: Instant): Boolean

    @Query("SELECT DISTINCT e.userId FROM AnalyticsEvent e WHERE e.eventName = :eventName AND e.createdAt >= :since")
    fun findDistinctUserIdsByEventNameSince(eventName: String, since: Instant): List<String>

    // Real frequency-capping primitive (2026-08-11) -- see DiscoverService's own doc
    // comment for the full account: this is the same "has this user already seen X
    // enough times" question the coop-rail retention query above already answers for
    // one event, just parameterized by metadataJson (itunda's per-banner id) so a
    // single event name ("discover_banner_impression") can frequency-cap many
    // distinct banners without a new column or event name per banner.
    fun countByUserIdAndEventNameAndMetadataJson(userId: String, eventName: String, metadataJson: String): Long
}
