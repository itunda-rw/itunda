package rw.itunda.community.web

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
import rw.itunda.community.InvalidNeighborhoodReviewException
import rw.itunda.community.NeighborhoodReviewAlreadySubmittedException
import rw.itunda.community.NeighborhoodReviewService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

data class SubmitNeighborhoodReviewRequest(val residencyYears: Int? = null, val body: String)

// Real 살아본 후기 (Karrot "lived here" reviews) -- see NeighborhoodReviewService's
// own doc comment. Normal itunda-user JWT gate (default SecurityConfig
// .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/community/neighborhoods")
class NeighborhoodReviewController(private val neighborhoodReviewService: NeighborhoodReviewService) {

    @GetMapping("/{neighborhood}/reviews")
    fun getReviews(@PathVariable neighborhood: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "reviews" to neighborhoodReviewService.getReviews(neighborhood)))

    @PostMapping("/{neighborhood}/reviews")
    fun submitReview(
        @PathVariable neighborhood: String,
        @RequestBody request: SubmitNeighborhoodReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = neighborhoodReviewService.submitReview(currentUser.userId, neighborhood, request.residencyYears, request.body)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "review" to review))
    }

    @ExceptionHandler(InvalidNeighborhoodReviewException::class)
    fun handleInvalid(ex: InvalidNeighborhoodReviewException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_NEIGHBORHOOD_REVIEW", ex.message ?: "Bad request"))

    @ExceptionHandler(NeighborhoodReviewAlreadySubmittedException::class)
    fun handleAlreadySubmitted(ex: NeighborhoodReviewAlreadySubmittedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("NEIGHBORHOOD_REVIEW_ALREADY_SUBMITTED", ex.message ?: "Conflict"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
