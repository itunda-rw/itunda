package rw.itunda.system.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.system.ChatReportMessageNotFoundException
import rw.itunda.system.ChatReportNotFoundException
import rw.itunda.system.ChatReportService

@RestController
@RequestMapping("/api/v1/system/chat-reports")
class ChatReportAdminController(private val chatReportService: ChatReportService) {
    @GetMapping
    fun queue(@PageableDefault(size = 30) pageable: Pageable): ResponseEntity<Map<String, Any>> {
        val page = chatReportService.queue(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "reports" to page.content) + pageMeta(page))
    }

    @PostMapping("/{id}/resolve")
    fun resolve(@PathVariable id: String, @AuthenticationPrincipal user: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "report" to chatReportService.resolve(id, user.userId)))

    @PostMapping("/{id}/remove-message")
    fun removeMessage(@PathVariable id: String, @AuthenticationPrincipal user: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "report" to chatReportService.removeMessage(id, user.userId)))

    @ExceptionHandler(ChatReportNotFoundException::class)
    fun missing(ex: ChatReportNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CHAT_REPORT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ChatReportMessageNotFoundException::class)
    fun messageMissing(ex: ChatReportMessageNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CHAT_REPORT_MESSAGE_NOT_FOUND", ex.message ?: "Not found"))
}
