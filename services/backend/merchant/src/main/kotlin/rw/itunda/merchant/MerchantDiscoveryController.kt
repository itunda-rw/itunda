package rw.itunda.merchant

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.web.ApiError

/** Customer-facing "stores near me" discovery, mirrors AgentDiscoveryController exactly. */
@RestController
@RequestMapping("/api/v1/merchant")
class MerchantDiscoveryController(private val merchantDiscoveryService: MerchantDiscoveryService) {
    @GetMapping("/nearby")
    fun nearby(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(defaultValue = "5") radiusKm: Double,
    ): ResponseEntity<Map<String, Any>> = ResponseEntity.ok(
        mapOf("success" to true, "merchants" to merchantDiscoveryService.nearby(latitude, longitude, radiusKm)),
    )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleInvalidSearch(ex: IllegalArgumentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MERCHANT_SEARCH", ex.message ?: "Bad request"))
}
