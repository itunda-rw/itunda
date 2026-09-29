package rw.itunda.system.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.system.ChatReportAlreadyOpenException
import rw.itunda.system.ChatReportForbiddenException
import rw.itunda.system.ChatReportMessageNotFoundException
import rw.itunda.system.ChatReportService

data class CreateChatReportRequest(val messageId: String, val reason: String)

@RestController
@RequestMapping("/api/v1/chat/reports")
class ChatReportController(private val chatReportService: ChatReportService) {
    @PostMapping
    fun create(@RequestBody request: CreateChatReportRequest, @AuthenticationPrincipal user: CurrentUser) =
        ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "report" to chatReportService.report(user.userId, request.messageId, request.reason)))

    @ExceptionHandler(ChatReportMessageNotFoundException::class, ChatReportForbiddenException::class)
    fun missing(ex: RuntimeException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CHAT_MESSAGE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ChatReportAlreadyOpenException::class)
    fun duplicate(ex: ChatReportAlreadyOpenException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CHAT_REPORT_ALREADY_OPEN", ex.message ?: "Conflict"))
}
