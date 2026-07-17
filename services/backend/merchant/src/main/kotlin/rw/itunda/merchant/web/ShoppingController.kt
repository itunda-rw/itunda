package rw.itunda.merchant.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantRepository

// The real "browse partner merchants" half of "Toss Shopping" -- see
// ShoppingCashbackService's own doc comment for why itunda's own real registered
// Merchant directory is the honest, non-fabricated catalog here (itunda has no external
// merchant-partnership network to draw one from instead). Requires a normal itunda-user
// JWT (default SecurityConfig .anyRequest().authenticated()) -- this is a real user
// browsing where they can earn cashback, not a public/partner-authenticated surface.
@RestController
@RequestMapping("/api/v1/shopping")
class ShoppingController(private val merchantRepository: MerchantRepository) {

    @GetMapping("/merchants")
    fun getEligibleMerchants(): ResponseEntity<Map<String, Any?>> {
        val merchants = merchantRepository.findByStatus(MerchantStatus.ACTIVE).map { merchant ->
            mapOf(
                "merchantId" to merchant.id,
                "businessName" to merchant.businessName,
                "cashbackRate" to "1%",
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "merchants" to merchants))
    }
}
