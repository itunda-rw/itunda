package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Notification

interface NotificationRepository : JpaRepository<Notification, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Notification>

    // Real itunda service channel (2026-08-28) -- a paginated overload alongside the
    // existing unbounded List-returning query above (kept unchanged for the existing
    // Home-bell feed, which already caps its own real usage). A real chat-style
    // thread view needs real, bounded pagination, matching PageResponse.kt's own
    // established convention.
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<Notification>
}
