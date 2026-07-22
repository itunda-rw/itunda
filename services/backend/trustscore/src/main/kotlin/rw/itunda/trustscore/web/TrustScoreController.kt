package rw.itunda.trustscore.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.trust.TrustScoreService
import rw.itunda.core.trust.TrustScoreUserNotFoundException
import rw.itunda.core.web.ApiError

/**
 * Real Karrot-Score-style numeric trust badge -- see TrustScoreService's own doc
 * comment for the full account (why 0-1000 starting at 30, not a manner-temperature
 * metaphor). Mirrors CreditScoreController's own shape exactly: computing on every
 * call keeps the account-tenure factor honestly current even between mark-sold/
 * mark-filled/mark-taken events, the same reasoning CreditScoreController's own doc
 * comment already established for its own account-age factor.
 */
@RestController
@RequestMapping("/api/v1/trust-score")
class TrustScoreController(private val trustScoreService: TrustScoreService) {

    @GetMapping
    fun getScore(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val result = trustScoreService.computeScore(currentUser.userId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "score" to result.score,
                "factors" to result.factors,
                "computedAt" to result.computedAt,
            ),
        )
    }

    @ExceptionHandler(TrustScoreUserNotFoundException::class)
    fun handleUserNotFound(ex: TrustScoreUserNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("USER_NOT_FOUND", ex.message ?: "Not found"))
}
