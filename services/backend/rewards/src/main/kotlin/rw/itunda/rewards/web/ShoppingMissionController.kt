package rw.itunda.rewards.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ExceptionHandler
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.rewards.MissionAlreadyCompletedException
import rw.itunda.rewards.RewardsNoAccountException
import rw.itunda.rewards.ShoppingMissionService
import rw.itunda.rewards.ShoppingMissionType

// Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row -- see
// ShoppingMissionService's own doc comment.
// Correction, 2026-09-05 (see feedback_idempotency_key_sweep memory): the "naturally
// idempotent, no Idempotency-Key needed" reasoning this used to give conflated
// data-safety with UX-safety -- the stored per-day/once-ever flag genuinely prevents
// a DOUBLE credit, but it does so by throwing MissionAlreadyCompletedException, so a
// lost-response retry after a SUCCESSFUL completion hits that exact guard with a
// confusing conflict for a mission that actually already completed. Same recurring
// flaw this sweep already found on ForeignCurrencyController/MotoOwnershipController/
// SplitBillController, just with new wording.
@RestController
@RequestMapping("/api/v1/shopping/points")
class ShoppingMissionController(
    private val missionService: ShoppingMissionService,
    private val idempotencyService: IdempotencyService,
) {

    @GetMapping
    fun getStatus(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val status = missionService.getStatus(currentUser.userId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "missions" to status.map {
                    mapOf(
                        "type" to it.type.name,
                        "label" to it.label,
                        "rewardAmount" to it.rewardAmount,
                        "completedToday" to it.completedToday,
                        "claimedEver" to it.claimedEver,
                    )
                },
                // Real, stated odds (item 248 discipline) -- shown up front, not just
                // discovered after a spin.
                "spinOutcomes" to missionService.spinOutcomes().map { mapOf("amount" to it.amount, "odds" to it.odds) },
            ),
        )
    }

    @PostMapping("/missions/{type}/complete")
    fun completeMission(
        @PathVariable type: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val missionType = ShoppingMissionType.entries.find { it.name == type.uppercase() }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("success" to false, "error" to "Unknown mission type"))
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/shopping/points/missions/$type/complete", idempotencyKey, currentUser.userId) {
            val result = if (missionType == ShoppingMissionType.WELCOME_BONUS) {
                missionService.claimWelcomeBonus(currentUser.userId)
            } else {
                missionService.completeDailyMission(currentUser.userId, missionType)
            }
            200 to mapOf(
                "success" to true,
                "type" to result.type.name,
                "amountEarned" to result.amountEarned,
                "newAccountBalance" to result.newAccountBalance,
            )
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(MissionAlreadyCompletedException::class)
    fun handleAlreadyCompleted(ex: MissionAlreadyCompletedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MISSION_ALREADY_COMPLETED", ex.message ?: "Conflict"))

    @ExceptionHandler(RewardsNoAccountException::class)
    fun handleNoAccount(ex: RewardsNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
