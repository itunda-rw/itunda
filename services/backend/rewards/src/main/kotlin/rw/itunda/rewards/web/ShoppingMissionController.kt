package rw.itunda.rewards.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ExceptionHandler
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.rewards.MissionAlreadyCompletedException
import rw.itunda.rewards.RewardsNoWalletException
import rw.itunda.rewards.ShoppingMissionService
import rw.itunda.rewards.ShoppingMissionType

// Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row -- see
// ShoppingMissionService's own doc comment. Naturally idempotent (each mission only
// ever credits once, checked against a real stored flag before crediting), same
// convention RewardsController.reportSteps already uses -- no Idempotency-Key needed.
@RestController
@RequestMapping("/api/v1/shopping/points")
class ShoppingMissionController(private val missionService: ShoppingMissionService) {

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
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val missionType = ShoppingMissionType.entries.find { it.name == type.uppercase() }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("success" to false, "error" to "Unknown mission type"))
        val result = if (missionType == ShoppingMissionType.WELCOME_BONUS) {
            missionService.claimWelcomeBonus(currentUser.userId)
        } else {
            missionService.completeDailyMission(currentUser.userId, missionType)
        }
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "type" to result.type.name,
                "amountEarned" to result.amountEarned,
                "newWalletBalance" to result.newWalletBalance,
            ),
        )
    }

    @ExceptionHandler(MissionAlreadyCompletedException::class)
    fun handleAlreadyCompleted(ex: MissionAlreadyCompletedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MISSION_ALREADY_COMPLETED", ex.message ?: "Conflict"))

    @ExceptionHandler(RewardsNoWalletException::class)
    fun handleNoWallet(ex: RewardsNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))
}
