package rw.itunda.commerce.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
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
import rw.itunda.commerce.InvalidTimeDealException
import rw.itunda.commerce.MerchantNotFoundException
import rw.itunda.commerce.TimeDealNotFoundException
import rw.itunda.commerce.TimeDealProductNotFoundException
import rw.itunda.commerce.TimeDealService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import java.math.BigDecimal
import java.time.Instant

data class CreateTimeDealRequest(
    val productId: String, val dealPrice: BigDecimal, val totalQuantity: Int, val startsAt: Instant, val endsAt: Instant,
)

// Real Coupang 타임특가 (Time Deal) -- see TimeDealService's own doc comment. Normal
// itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/time-deals")
class TimeDealController(private val timeDealService: TimeDealService) {

    @PostMapping
    fun createTimeDeal(
        @RequestBody request: CreateTimeDealRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val deal = timeDealService.createTimeDeal(
            currentUser.userId, request.productId, request.dealPrice, request.totalQuantity, request.startsAt, request.endsAt,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "deal" to deal))
    }

    @GetMapping
    fun getActiveDeals(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any?>> {
        val page = timeDealService.getActiveDeals(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "deals" to page.content) + pageMeta(page))
    }

    @GetMapping("/mine")
    fun getMyDeals(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = timeDealService.getMyDeals(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "deals" to page.content) + pageMeta(page))
    }

    @GetMapping("/{dealId}")
    fun getDeal(@PathVariable dealId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "deal" to timeDealService.getDeal(dealId)))

    @PostMapping("/{dealId}/end")
    fun endDeal(@PathVariable dealId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "deal" to timeDealService.endDeal(currentUser.userId, dealId)))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(TimeDealProductNotFoundException::class)
    fun handleProductNotFound(ex: TimeDealProductNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PRODUCT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(TimeDealNotFoundException::class)
    fun handleDealNotFound(ex: TimeDealNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("TIME_DEAL_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidTimeDealException::class)
    fun handleInvalid(ex: InvalidTimeDealException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_TIME_DEAL", ex.message ?: "Bad request"))
}
