package rw.itunda.notifications.web

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.repository.NotificationRepository

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationRepository: NotificationRepository,
    private val objectMapper: ObjectMapper
) {

    // Real bug found live (2026-07-13, while verifying the new budget-alert feature):
    // this used to read `Authentication.name`, which for a non-String, non-UserDetails
    // principal (our CurrentUser data class -- see JwtAuthenticationFilter) falls back
    // to Kotlin's synthesized `toString()`, something like
    // "CurrentUser(userId=user_1, role=USER)" -- never matching any real userId stored
    // in the notifications table. Confirmed live: a real notification was written to
    // MySQL with the correct user_id, but this endpoint returned zero results for that
    // same user. Every other controller in this backend already uses
    // @AuthenticationPrincipal CurrentUser; this one just hadn't been.
    @GetMapping
    fun getNotifications(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val userId = currentUser.userId
        val mine = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
        val unreadCount = mine.count { !it.isRead }

        val dtos = mine.map { n ->
            val dataMap: Map<String, Any> = if (n.dataJson.isNullOrBlank()) {
                emptyMap()
            } else {
                try {
                    objectMapper.readValue(n.dataJson, object : TypeReference<Map<String, Any>>() {})
                } catch (e: Exception) {
                    emptyMap()
                }
            }

            mapOf(
                "id" to n.id,
                "userId" to n.userId,
                "type" to n.type,
                "title" to n.title,
                "body" to n.body,
                "isRead" to n.isRead,
                "createdAt" to n.createdAt.toString(),
                "data" to dataMap
            )
        }

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "notifications" to dtos,
            "unreadCount" to unreadCount
        ))
    }

    @PostMapping("/{id}/read")
    fun markAsRead(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val userId = currentUser.userId
        if (id == "all") {
            val unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).filter { !it.isRead }
            unread.forEach { it.isRead = true }
            notificationRepository.saveAll(unread)
        } else {
            notificationRepository.findById(id).ifPresent { n ->
                if (n.userId == userId && !n.isRead) {
                    n.isRead = true
                    notificationRepository.save(n)
                }
            }
        }
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
