package rw.itunda.rewards.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.rewards.RewardTaskAlreadyClaimedException
import rw.itunda.rewards.RewardTaskNotEligibleException
import rw.itunda.rewards.RewardTaskNotFoundException
import rw.itunda.rewards.RewardsNoWalletException
import rw.itunda.rewards.RewardsService
import rw.itunda.rewards.RewardsUserNotFoundException

data class ClaimRewardRequest(val taskId: String)

// The Saronite reward-tasks mini-app's native bridge (android/.../SaroniteBridge.kt) has
// called these two routes since it was built -- see docs/API_SPECIFICATION.md's Rewards
// section and docs/TOSS_PARITY_MATRIX.md's Rewards row for the corrected account of what was
// previously a false "real" claim.
@RestController
@RequestMapping("/api/v1/rewards")
class RewardsController(private val rewardsService: RewardsService, private val idempotencyService: IdempotencyService) {

    @GetMapping("/tasks")
    fun getTasks(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val result = rewardsService.getTasks(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "tasks" to result.tasks, "rewardsTotal" to result.rewardsTotal))
    }

    // Real referral subsystem (2026-07-17): the caller's own share code plus real
    // progress toward task_referral -- see RewardsService.getReferralInfo.
    @GetMapping("/referral")
    fun referral(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val result = rewardsService.getReferralInfo(currentUser.userId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "referralCode" to result.referralCode,
                "referredCount" to result.referredCount,
                "completedReferralCount" to result.completedReferralCount,
            ),
        )
    }

    // Idempotency-Key required, same convention as every other money-moving endpoint in this
    // backend -- distinct from the claim-once *business rule* (a task can never be claimed
    // twice, ever, enforced by a real DB unique constraint) which holds regardless of
    // idempotency keys.
    @PostMapping("/claim")
    fun claim(
        @RequestBody request: ClaimRewardRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/rewards/claim", idempotencyKey, request) {
            val result = rewardsService.claim(currentUser.userId, request.taskId)
            200 to mapOf("success" to true, "message" to result.message, "rewardAmount" to result.rewardAmount, "newBalance" to result.newBalance)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(RewardTaskNotFoundException::class)
    fun handleNotFound(ex: RewardTaskNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("REWARD_TASK_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RewardTaskAlreadyClaimedException::class)
    fun handleAlreadyClaimed(ex: RewardTaskAlreadyClaimedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("REWARD_TASK_ALREADY_CLAIMED", ex.message ?: "Conflict"))

    @ExceptionHandler(RewardTaskNotEligibleException::class)
    fun handleNotEligible(ex: RewardTaskNotEligibleException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("REWARD_TASK_NOT_ELIGIBLE", ex.message ?: "Forbidden"))

    @ExceptionHandler(RewardsNoWalletException::class)
    fun handleNoWallet(ex: RewardsNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RewardsUserNotFoundException::class)
    fun handleUserNotFound(ex: RewardsUserNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("USER_NOT_FOUND", ex.message ?: "Not found"))
}
