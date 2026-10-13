package rw.itunda.eats.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.eats.EatsStampService
import java.math.BigDecimal
import java.time.Instant

/**
 * Small, dedicated HTTP surface for Eats stamp campaigns and buyer progress.
 * Rewards are entitlements only until checkout redemption is implemented.
 */
@RestController
@RequestMapping("/api/v1/eats")
class EatsStampController(
    private val eatsStampService: EatsStampService,
) {
        // Restaurant-owned stamp campaigns and buyer progress. Reward entries remain
        // entitlements only until checkout redemption is implemented and tested.
        @PostMapping("/restaurants/{restaurantId}/stamp-campaigns")
        fun createStampCampaign(
            @PathVariable restaurantId: String,
            @RequestBody request: CreateEatsStampCampaignRequest,
            @AuthenticationPrincipal currentUser: CurrentUser,
        ): ResponseEntity<Map<String, Any?>> {
            val campaign = eatsStampService.createCampaign(
                ownerUserId = currentUser.userId,
                restaurantId = restaurantId,
                name = request.name,
                stampsRequired = request.stampsRequired,
                rewardDescription = request.rewardDescription,
                discountAmount = request.discountAmount,
                startsAt = request.startsAt,
                endsAt = request.endsAt,
            )
            return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "campaign" to campaign))
        }
    
        @GetMapping("/restaurants/{restaurantId}/stamp-campaigns")
        fun getStampCampaigns(@PathVariable restaurantId: String): ResponseEntity<Map<String, Any?>> =
            ResponseEntity.ok(mapOf("success" to true, "campaigns" to eatsStampService.activeCampaigns(restaurantId)))
    
        @GetMapping("/stamp-campaigns/{campaignId}/progress")
        fun getStampProgress(
            @PathVariable campaignId: String,
            @AuthenticationPrincipal currentUser: CurrentUser,
        ): ResponseEntity<Map<String, Any?>> =
            ResponseEntity.ok(mapOf("success" to true, "progress" to eatsStampService.getBuyerProgress(currentUser.userId, campaignId)))
}

data class CreateEatsStampCampaignRequest(
    val name: String,
    val stampsRequired: Int,
    val rewardDescription: String,
    val discountAmount: BigDecimal? = null,
    val startsAt: Instant,
    val endsAt: Instant? = null,
)
