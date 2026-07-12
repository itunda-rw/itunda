package rw.itunda.creditscore.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.creditscore.CreditScoreService
import rw.itunda.creditscore.CreditScoreUserNotFoundException

@RestController
@RequestMapping("/api/v1/credit-score")
class CreditScoreController(private val creditScoreService: CreditScoreService) {

    @GetMapping
    fun getScore(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val result = creditScoreService.computeScore(currentUser.userId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "score" to result.score,
                "factors" to result.factors,
                "computedAt" to result.computedAt,
            ),
        )
    }

    @ExceptionHandler(CreditScoreUserNotFoundException::class)
    fun handleUserNotFound(ex: CreditScoreUserNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("USER_NOT_FOUND", ex.message ?: "Not found"))
}
