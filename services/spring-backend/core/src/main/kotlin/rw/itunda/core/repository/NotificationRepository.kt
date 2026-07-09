package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Notification

interface NotificationRepository : JpaRepository<Notification, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Notification>
}
