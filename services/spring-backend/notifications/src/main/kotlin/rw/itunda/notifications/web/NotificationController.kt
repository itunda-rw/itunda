package rw.itunda.notifications.web

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import rw.itunda.core.repository.NotificationRepository

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationRepository: NotificationRepository,
    private val objectMapper: ObjectMapper
) {

    @GetMapping
    fun getNotifications(auth: Authentication): ResponseEntity<Map<String, Any>> {
        val userId = auth.name
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
    fun markAsRead(@PathVariable id: String, auth: Authentication): ResponseEntity<Map<String, Any>> {
        val userId = auth.name
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
