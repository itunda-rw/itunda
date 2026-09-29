package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Notification
import java.time.Instant

interface NotificationRepository : JpaRepository<Notification, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Notification>

    // Real itunda service channel (2026-08-28) -- a paginated overload alongside the
    // existing unbounded List-returning query above (kept unchanged for the existing
    // Home-bell feed, which already caps its own real usage). A real chat-style
    // thread view needs real, bounded pagination, matching PageResponse.kt's own
    // established convention.
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<Notification>

    // Real cleanup (2026-09-07) -- see NotificationCleanupScheduler's own doc
    // comment. Only READ rows are ever deleted, and only once genuinely old; an
    // unread notification is kept forever regardless of age so a user can never
    // silently lose one.
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.isRead = true AND n.createdAt < :before")
    fun deleteByIsReadTrueAndCreatedAtBefore(@Param("before") before: Instant): Int
}
