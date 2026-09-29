package rw.itunda.notifications.web

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.Notification
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.security.CurrentUser
import java.time.Instant
import java.util.UUID

data class BroadcastNotificationRequest(val title: String, val body: String)

// Mapped under /api/v1/system/notifications specifically so it inherits
// SecurityConfig's existing hasRole("ADMIN") rule on the system path prefix,
// same convention PartnerAdminController/ComplianceController already
// established. A real, previously-missing B2B lever: ops had no way to
// announce an outage/promotion into every user's in-app inbox short of a DB
// script.
@RestController
@RequestMapping("/api/v1/system/notifications")
class NotificationAdminController(
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
) {
    @PostMapping("/broadcast")
    @Transactional
    fun broadcast(@RequestBody request: BroadcastNotificationRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val title = request.title.trim()
        val body = request.body.trim()
        val now = Instant.now()
        val userIds = userRepository.findAllUserIds()
        notificationRepository.saveAll(
            userIds.map { userId ->
                Notification(
                    id = "notification_${UUID.randomUUID()}", userId = userId, type = "system_broadcast",
                    title = title, body = body, isRead = false, createdAt = now, dataJson = null,
                    sentByUserId = currentUser.userId,
                )
            },
        )
        return ResponseEntity.ok(mapOf("success" to true, "sentCount" to userIds.size, "sentByUserId" to currentUser.userId))
    }
}
