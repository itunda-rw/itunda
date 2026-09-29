package rw.itunda.marketplace.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.marketplace.InvalidEscrowStatusException
import rw.itunda.marketplace.MarketplaceEscrowNotFoundException
import rw.itunda.marketplace.MarketplaceService

data class ResolveEscrowDisputeRequest(val release: Boolean)

// Mapped under api/v1/system/marketplace-escrow specifically so it inherits
// SecurityConfig's existing hasRole("ADMIN") rule on the system path prefix, same real
// RBAC gate PropertyOwnershipAdminController already uses for its own human-review
// queue -- no new attack surface.
@RestController
@RequestMapping("/api/v1/system/marketplace-escrow")
class MarketplaceEscrowAdminController(private val marketplaceService: MarketplaceService) {

    @GetMapping("/disputes")
    fun disputes(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "disputes" to marketplaceService.getPendingDisputes()))

    // Real admin-accountability gap closed (Bank/Merchant product-completeness pass,
    // cycle 2, 2026-09-09): this endpoint previously had zero record of which admin
    // decided a real money release-vs-refund dispute -- see
    // MarketplaceService.resolveDispute's own doc comment.
    @PostMapping("/{escrowId}/resolve")
    fun resolve(
        @PathVariable escrowId: String,
        @RequestBody request: ResolveEscrowDisputeRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val escrow = marketplaceService.resolveDispute(escrowId, request.release, currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "escrow" to escrow))
    }

    @ExceptionHandler(MarketplaceEscrowNotFoundException::class)
    fun handleNotFound(ex: MarketplaceEscrowNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ESCROW_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidEscrowStatusException::class)
    fun handleInvalidStatus(ex: InvalidEscrowStatusException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INVALID_ESCROW_STATUS", ex.message ?: "Conflict"))
}
