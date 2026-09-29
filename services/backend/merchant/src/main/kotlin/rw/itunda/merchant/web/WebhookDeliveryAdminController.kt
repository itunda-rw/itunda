package rw.itunda.merchant.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.merchant.WebhookDeliveryNotFoundException
import rw.itunda.merchant.WebhookDeliveryService
import rw.itunda.merchant.WebhookUrlNotConfiguredException

// Real ops visibility (Merchant product-completeness pass) -- WebhookDelivery/
// WebhookDeliveryStatus.EXHAUSTED was a real, already-modeled state
// (WebhookRetryScheduler marks deliveries EXHAUSTED after
// WebhookDeliveryService.MAX_ATTEMPTS real Toss-Payments-sourced retries) with zero
// admin surface anywhere -- a merchant's own self-service GET /webhook-deliveries
// (MerchantController.kt) is scoped to just that merchant, useless for spotting a
// systemic delivery problem (e.g. a shared downstream outage) across several.
// Mapped under api/v1/system/merchant-webhooks specifically so it inherits
// SecurityConfig's existing hasRole("ADMIN") rule on the shared /api/v1/system/**
// prefix, same real RBAC gate every other admin controller in this codebase uses.
@RestController
@RequestMapping("/api/v1/system/merchant-webhooks")
class WebhookDeliveryAdminController(private val webhookDeliveryService: WebhookDeliveryService) {

    @GetMapping("/exhausted")
    fun exhausted(@PageableDefault(size = 50) pageable: Pageable): ResponseEntity<Map<String, Any?>> {
        val page = webhookDeliveryService.getExhaustedQueue(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "deliveries" to page.content) + pageMeta(page))
    }

    // Real admin-accountability gap closed (2026-09-13) -- this cross-merchant replay
    // previously had zero record of which admin acted, the exact same gap class this
    // codebase already closed for Merchant/Vehicle-Inspection/Partner but missed here.
    @PostMapping("/{deliveryId}/replay")
    fun replay(@PathVariable deliveryId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "replay" to webhookDeliveryService.replayExhaustedAsAdmin(deliveryId, currentUser.userId)))

    @ExceptionHandler(WebhookDeliveryNotFoundException::class)
    fun handleNotFound(ex: WebhookDeliveryNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WEBHOOK_DELIVERY_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(WebhookUrlNotConfiguredException::class)
    fun handleUrlNotConfigured(ex: WebhookUrlNotConfiguredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("WEBHOOK_URL_NOT_CONFIGURED", ex.message ?: "Conflict"))
}
