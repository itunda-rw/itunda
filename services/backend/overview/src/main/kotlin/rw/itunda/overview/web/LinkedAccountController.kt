package rw.itunda.overview.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.overview.LinkedAccountAlreadyUnlinkedException
import rw.itunda.overview.LinkedAccountNotFoundException
import rw.itunda.overview.LinkedAccountNotOwnedException
import rw.itunda.overview.LinkedAccountService

data class LinkAccountRequest(val provider: String, val externalAccountNumber: String)

/**
 * Real external bank/MoMo consent registry -- see LinkedAccount.kt's own doc comment
 * and docs/TOSS_PARITY_MATRIX.md's Account aggregation row for the full account.
 */
@RestController
@RequestMapping("/api/v1/accounts")
class LinkedAccountController(private val linkedAccountService: LinkedAccountService) {

    @PostMapping("/link")
    fun link(
        @RequestBody request: LinkAccountRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val account = linkedAccountService.link(currentUser.userId, request.provider, request.externalAccountNumber)
        return ResponseEntity.ok(mapOf("success" to true, "linkedAccount" to account))
    }

    @GetMapping("/linked")
    fun linked(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "linkedAccounts" to linkedAccountService.getMyLinkedAccounts(currentUser.userId)))

    @PostMapping("/link/{accountId}/unlink")
    fun unlink(
        @PathVariable accountId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val account = linkedAccountService.unlink(currentUser.userId, accountId)
        return ResponseEntity.ok(mapOf("success" to true, "linkedAccount" to account))
    }

    @ExceptionHandler(LinkedAccountNotFoundException::class)
    fun handleNotFound(ex: LinkedAccountNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LINKED_ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(LinkedAccountNotOwnedException::class)
    fun handleNotOwned(ex: LinkedAccountNotOwnedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("LINKED_ACCOUNT_NOT_OWNED", ex.message ?: "Forbidden"))

    @ExceptionHandler(LinkedAccountAlreadyUnlinkedException::class)
    fun handleAlreadyUnlinked(ex: LinkedAccountAlreadyUnlinkedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("LINKED_ACCOUNT_ALREADY_UNLINKED", ex.message ?: "Conflict"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REQUEST", ex.message ?: "Bad request"))
}
