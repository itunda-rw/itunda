package rw.itunda.marketplace.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.marketplace.InvalidKeywordException
import rw.itunda.marketplace.KeywordAlertCapReachedException
import rw.itunda.marketplace.KeywordAlertNotFoundException
import rw.itunda.marketplace.KeywordAlertService

data class AddKeywordAlertRequest(val keyword: String)

// Real 당근마켓-style Keyword Alert -- see KeywordAlertService's own doc comment.
@RestController
@RequestMapping("/api/v1/marketplace/keyword-alerts")
class KeywordAlertController(private val keywordAlertService: KeywordAlertService) {

    @PostMapping
    fun addAlert(
        @RequestBody request: AddKeywordAlertRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val alert = keywordAlertService.addAlert(currentUser.userId, request.keyword)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "alert" to alert))
    }

    @GetMapping
    fun listAlerts(
        @PageableDefault(size = 30) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = keywordAlertService.listAlerts(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "alerts" to page.content) + pageMeta(page))
    }

    @DeleteMapping("/{alertId}")
    fun removeAlert(
        @PathVariable alertId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Boolean>> {
        keywordAlertService.removeAlert(currentUser.userId, alertId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @ExceptionHandler(InvalidKeywordException::class)
    fun handleInvalidKeyword(ex: InvalidKeywordException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_KEYWORD", ex.message ?: "Bad request"))

    @ExceptionHandler(KeywordAlertCapReachedException::class)
    fun handleCapReached(ex: KeywordAlertCapReachedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("KEYWORD_ALERT_CAP_REACHED", ex.message ?: "Conflict"))

    @ExceptionHandler(KeywordAlertNotFoundException::class)
    fun handleNotFound(ex: KeywordAlertNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("KEYWORD_ALERT_NOT_FOUND", ex.message ?: "Not found"))
}
