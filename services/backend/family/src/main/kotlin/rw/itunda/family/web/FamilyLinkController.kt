package rw.itunda.family.web

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
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.family.FamilyLinkAlreadyExistsException
import rw.itunda.family.FamilyLinkChildNotFoundException
import rw.itunda.family.FamilyLinkNotActiveException
import rw.itunda.family.FamilyLinkNotFoundException
import rw.itunda.family.FamilyLinkNotPendingException
import rw.itunda.family.FamilyLinkSelfException
import rw.itunda.family.FamilyLinkService
import rw.itunda.family.FamilyLinkUnauthorizedException

data class InviteChildRequest(val childPhoneNumber: String)
data class RespondToInviteRequest(val accept: Boolean)

// Real Toss 유스 (Toss Youth)-style guardian-child account link -- see
// FamilyLinkService's own doc comment for the full sourced account.
@RestController
@RequestMapping("/api/v1/family")
class FamilyLinkController(private val familyLinkService: FamilyLinkService) {

    @PostMapping("/invite")
    fun invite(@RequestBody request: InviteChildRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val link = familyLinkService.inviteChild(currentUser.userId, request.childPhoneNumber)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "link" to link))
    }

    @GetMapping("/invites")
    fun myInvites(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "invites" to familyLinkService.getMyInvitesAsChild(currentUser.userId)))

    @PostMapping("/invites/{id}/respond")
    fun respond(
        @PathVariable id: String,
        @RequestBody request: RespondToInviteRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "link" to familyLinkService.respondToInvite(currentUser.userId, id, request.accept)))

    @GetMapping("/children")
    fun myChildren(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "children" to familyLinkService.getMyChildren(currentUser.userId)))

    @GetMapping("/guardians")
    fun myGuardians(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "guardians" to familyLinkService.getMyGuardians(currentUser.userId)))

    @GetMapping("/children/{childUserId}/overview")
    fun childOverview(@PathVariable childUserId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "overview" to familyLinkService.getChildOverview(currentUser.userId, childUserId)))

    @PostMapping("/links/{id}/revoke")
    fun revoke(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "link" to familyLinkService.revokeLink(currentUser.userId, id)))

    @ExceptionHandler(FamilyLinkNotFoundException::class)
    fun handleNotFound(ex: FamilyLinkNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("FAMILY_LINK_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(FamilyLinkChildNotFoundException::class)
    fun handleChildNotFound(ex: FamilyLinkChildNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("FAMILY_LINK_ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(FamilyLinkSelfException::class)
    fun handleSelf(ex: FamilyLinkSelfException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_LINK_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(FamilyLinkAlreadyExistsException::class)
    fun handleAlreadyExists(ex: FamilyLinkAlreadyExistsException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("FAMILY_LINK_ALREADY_EXISTS", ex.message ?: "Conflict"))

    @ExceptionHandler(FamilyLinkNotPendingException::class)
    fun handleNotPending(ex: FamilyLinkNotPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("FAMILY_LINK_NOT_PENDING", ex.message ?: "Conflict"))

    @ExceptionHandler(FamilyLinkNotActiveException::class)
    fun handleNotActive(ex: FamilyLinkNotActiveException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("FAMILY_LINK_NOT_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(FamilyLinkUnauthorizedException::class)
    fun handleUnauthorized(ex: FamilyLinkUnauthorizedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("FAMILY_LINK_UNAUTHORIZED", ex.message ?: "Forbidden"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
