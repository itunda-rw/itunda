package rw.itunda.savings

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class SetRoundUpSettingsRequest(val enabled: Boolean, val roundToNearest: BigDecimal, val targetGoalId: String? = null)

// Real round-up auto-saving -- see RoundUpSettings.kt's own doc comment. Not
// money-moving directly (processRoundUp fires from inside P2pService.sendDirect, whose
// own real Idempotency-Key already covers the triggering transfer), so no
// Idempotency-Key requirement on this settings endpoint itself, same discipline
// SavingsController's own goal-creation endpoint already uses.
@RestController
@RequestMapping("/api/v1/savings/round-up")
class RoundUpController(private val roundUpService: RoundUpService) {

    @GetMapping
    fun getSettings(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "settings" to roundUpService.getSettings(currentUser.userId)))

    @PostMapping
    fun setSettings(
        @RequestBody request: SetRoundUpSettingsRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val settings = roundUpService.setSettings(currentUser.userId, request.enabled, request.roundToNearest, request.targetGoalId)
        return ResponseEntity.status(HttpStatus.OK).body(mapOf("success" to true, "settings" to settings))
    }

    @ExceptionHandler(InvalidRoundUpIncrementException::class)
    fun handleInvalidIncrement(ex: InvalidRoundUpIncrementException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_ROUND_UP_INCREMENT", ex.message ?: "Bad request"))

    @ExceptionHandler(RoundUpTargetRequiredException::class)
    fun handleTargetRequired(ex: RoundUpTargetRequiredException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("ROUND_UP_TARGET_REQUIRED", ex.message ?: "Bad request"))

    @ExceptionHandler(GoalNotFoundException::class)
    fun handleGoalNotFound(ex: GoalNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GOAL_NOT_FOUND", ex.message ?: "Not found"))
}
