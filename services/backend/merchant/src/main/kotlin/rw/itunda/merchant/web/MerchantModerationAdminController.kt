package rw.itunda.merchant.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.merchant.MerchantNotFoundException
import rw.itunda.merchant.MerchantService

// Mapped under api/v1/system/merchants specifically so it inherits SecurityConfig's
// existing hasRole("ADMIN") rule on the system path prefix -- same real RBAC gate
// MarketplaceEscrowAdminController/PropertyOwnershipAdminController already use, no new
// attack surface. See MerchantService.suspendMerchant's own doc comment for why this
// exists: there was previously no way, anywhere in the app, to take a merchant out of
// public browse once created.
@RestController
@RequestMapping("/api/v1/system/merchants")
class MerchantModerationAdminController(
    private val merchantService: MerchantService,
    private val merchantRepository: MerchantRepository,
) {

    // Real moderation queue -- see MerchantRepository.findByStatusAndCategoryIsNull's
    // own doc comment for why "ACTIVE with no category" is the real signal.
    @GetMapping("/uncategorized")
    fun uncategorized(@PageableDefault(size = 50) pageable: Pageable): ResponseEntity<Map<String, Any?>> {
        val page = merchantRepository.findByStatusAndCategoryIsNull(MerchantStatus.ACTIVE, pageable)
        val merchants = page.content.map { mapOf("merchantId" to it.id, "businessName" to it.businessName, "kybVerified" to it.kybVerified, "createdAt" to it.createdAt.toString()) }
        return ResponseEntity.ok(mapOf("success" to true, "merchants" to merchants) + pageMeta(page))
    }

    @PostMapping("/{merchantId}/suspend")
    fun suspend(@PathVariable merchantId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "status" to merchantService.suspendMerchant(merchantId).status.name))

    @PostMapping("/{merchantId}/reactivate")
    fun reactivate(@PathVariable merchantId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "status" to merchantService.reactivateMerchant(merchantId).status.name))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))
}
