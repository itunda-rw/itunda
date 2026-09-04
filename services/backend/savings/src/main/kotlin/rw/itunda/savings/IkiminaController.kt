package rw.itunda.savings

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class CreateIkiminaRequest(val name: String, val contributionAmount: BigDecimal, val cycleFrequencyDays: Int, val memberCap: Int)
data class InviteIkiminaMemberRequest(val phoneNumber: String)

// Real ikimina (Rwanda's own rotating savings & credit association) -- see
// IkiminaService's own doc comment for the full sourced account. Genuinely distinct
// from every Toss/Kakao/Naver/Coupang-sourced feature in this backend.
@RestController
@RequestMapping("/api/v1/ikiminas")
class IkiminaController(
    private val ikiminaService: IkiminaService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping
    fun create(@RequestBody request: CreateIkiminaRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val ikimina = ikiminaService.createIkimina(currentUser.userId, request.name, request.contributionAmount, request.cycleFrequencyDays, request.memberCap)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "ikimina" to ikimina))
    }

    @GetMapping
    fun myIkiminas(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "ikiminas" to ikiminaService.getMyIkiminas(currentUser.userId)))

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true) + ikiminaService.getIkimina(currentUser.userId, id).toMap())

    @PostMapping("/{id}/members")
    fun invite(
        @PathVariable id: String,
        @RequestBody request: InviteIkiminaMemberRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val member = ikiminaService.inviteMember(currentUser.userId, id, request.phoneNumber)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "member" to member))
    }

    @PostMapping("/{id}/start")
    fun start(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val ikimina = ikiminaService.startCycle(currentUser.userId, id)
        return ResponseEntity.ok(mapOf("success" to true, "ikimina" to ikimina))
    }

    @PostMapping("/{id}/contribute")
    fun contribute(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/ikiminas/$id/contribute", idempotencyKey, currentUser.userId) {
            val result = ikiminaService.contributeThisRound(currentUser.userId, id)
            // Real bug fix: a contribution that completes the round now auto-triggers
            // the payout in the same call -- see IkiminaService.contributeThisRound's
            // own doc comment. `payout` is null on every contribution except the one
            // that completes a round.
            200 to mapOf("success" to true, "ikimina" to result.ikimina, "payout" to result.payout)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/{id}/payout")
    fun payout(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/ikiminas/$id/payout", idempotencyKey, currentUser.userId) {
            val result = ikiminaService.checkAndTriggerPayout(currentUser.userId, id)
            200 to mapOf("success" to true, "ikimina" to result.ikimina, "recipientUserId" to result.recipientUserId, "amount" to result.amount)
        }
        return ResponseEntity.status(status).body(body)
    }

    private fun IkiminaView.toMap() = mapOf("ikimina" to ikimina, "balance" to balance, "members" to members, "currentRoundContributions" to currentRoundContributions)

    @ExceptionHandler(IkiminaNotFoundException::class)
    fun handleNotFound(ex: IkiminaNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("IKIMINA_NOT_FOUND", ex.message ?: "Not found"))

    // Real IDOR fix (2026-08-30, pass 6) fixed the STATUS (403->404) but kept a
    // distinguishable error CODE, the exact residual leak pass 9 (2026-09-03) named
    // and fixed elsewhere (LoanNotOwned/AccountNotOwned/PaymentCodeAccountNotOwned)
    // but missed here. A stranger probing ikiminaId values could still distinguish
    // "exists, not a member/organizer" (IKIMINA_NOT_MEMBER/IKIMINA_NOT_ORGANIZER)
    // from "doesn't exist" (IKIMINA_NOT_FOUND) purely from the response body, even
    // though both returned 404. Fixed 2026-09-04 by having every membership/
    // organizer check in IkiminaService.kt throw the same IkiminaNotFoundException
    // instead -- both handlers (and their now-dead exception classes) removed.

    @ExceptionHandler(IkiminaMemberNotFoundException::class)
    fun handleMemberNotFound(ex: IkiminaMemberNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(IkiminaAlreadyMemberException::class)
    fun handleAlreadyMember(ex: IkiminaAlreadyMemberException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ALREADY_MEMBER", ex.message ?: "Conflict"))

    @ExceptionHandler(IkiminaFullException::class)
    fun handleFull(ex: IkiminaFullException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IKIMINA_FULL", ex.message ?: "Conflict"))

    @ExceptionHandler(IkiminaNoAccountException::class)
    fun handleNoAccount(ex: IkiminaNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(IkiminaNotFormingException::class)
    fun handleNotForming(ex: IkiminaNotFormingException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IKIMINA_NOT_FORMING", ex.message ?: "Conflict"))

    @ExceptionHandler(IkiminaNotActiveException::class)
    fun handleNotActive(ex: IkiminaNotActiveException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IKIMINA_NOT_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(IkiminaTooFewMembersException::class)
    fun handleTooFewMembers(ex: IkiminaTooFewMembersException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("TOO_FEW_MEMBERS", ex.message ?: "Bad request"))

    @ExceptionHandler(IkiminaAlreadyContributedException::class)
    fun handleAlreadyContributed(ex: IkiminaAlreadyContributedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ALREADY_CONTRIBUTED", ex.message ?: "Conflict"))

    @ExceptionHandler(IkiminaContributionsIncompleteException::class)
    fun handleIncomplete(ex: IkiminaContributionsIncompleteException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("CONTRIBUTIONS_INCOMPLETE", ex.message ?: "Unprocessable"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REQUEST", ex.message ?: "Invalid request"))
}
