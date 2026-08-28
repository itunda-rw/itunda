package rw.itunda.notifications.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.pageMeta
import rw.itunda.notifications.ServiceChannelService

// Real itunda service channel (2026-08-28) -- see ServiceChannelService's own doc
// comment. Mark-read reuses the existing POST /api/v1/notifications/{id}/read
// endpoint unchanged -- this controller is read-only.
@RestController
@RequestMapping("/api/v1/talk/service-channel")
class ServiceChannelController(private val serviceChannelService: ServiceChannelService) {

    @GetMapping
    fun getThread(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = serviceChannelService.getServiceChannelThread(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "bubbles" to page.content) + pageMeta(page))
    }
}
