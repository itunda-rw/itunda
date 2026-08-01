package rw.itunda.commerce

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.TimeDeal
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.TimeDealRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class TimeDealProductNotFoundException(message: String) : RuntimeException(message)
class TimeDealNotFoundException(message: String) : RuntimeException(message)
class InvalidTimeDealException(message: String) : RuntimeException(message)

/** A raw `TimeDeal` alone has no product name/image/store name a real browse UI needs
 * -- this enriches it with a real, batch-resolved snapshot of that display context
 * (never persisted, computed fresh on every read), same N+1-avoidance discipline
 * `OrderService.placeOrder`'s own price-tier batch lookup already establishes. */
data class TimeDealView(val deal: TimeDeal, val productName: String, val productImageUrl: String?, val businessName: String)

/**
 * Real Coupang 타임특가 (Time Deal) -- see `TimeDeal.kt`'s own doc comment for the full
 * sourced account. Deliberately lives in `rw.itunda.commerce` alongside `OrderService`
 * (not the `merchant` module, where product-catalog management otherwise lives) because
 * its whole point is a real checkout-time price/eligibility hook into
 * `OrderService.placeOrder` -- keeping both in the same module avoids a cross-module
 * dependency for what is, underneath, one tightly coupled feature.
 */
@Service
class TimeDealService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val timeDealRepository: TimeDealRepository,
) {
    private fun getMyMerchant(ownerUserId: String) =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    /** Real ownership check on the product -- a real 404, not a 403, on a mismatched
     * owner, same IDOR discipline `MerchantProductService.setPriceTiers` already
     * establishes for this exact "does this product belong to this merchant" check. */
    @Transactional
    fun createTimeDeal(
        ownerUserId: String, productId: String, dealPrice: BigDecimal, totalQuantity: Int, startsAt: Instant, endsAt: Instant,
    ): TimeDeal {
        val merchant = getMyMerchant(ownerUserId)
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { TimeDealProductNotFoundException("Product not found") }
        if (product.merchantId != merchant.id) {
            throw TimeDealProductNotFoundException("Product not found")
        }
        if (dealPrice <= BigDecimal.ZERO) {
            throw InvalidTimeDealException("Deal price must be greater than zero")
        }
        if (dealPrice >= product.price) {
            throw InvalidTimeDealException("A time deal must cost less than the regular price (${product.price})")
        }
        if (totalQuantity <= 0) {
            throw InvalidTimeDealException("Total quantity must be at least 1")
        }
        if (!endsAt.isAfter(startsAt)) {
            throw InvalidTimeDealException("End time must be after start time")
        }
        return timeDealRepository.save(
            TimeDeal(
                id = "time_deal_${UUID.randomUUID()}", merchantId = merchant.id, productId = productId,
                dealPrice = dealPrice, originalPrice = product.price, totalQuantity = totalQuantity,
                remainingQuantity = totalQuantity, startsAt = startsAt, endsAt = endsAt,
            ),
        )
    }

    private fun enrich(deals: List<TimeDeal>): List<TimeDealView> {
        if (deals.isEmpty()) return emptyList()
        val productsById = merchantProductRepository.findAllById(deals.map { it.productId }.distinct()).associateBy { it.id }
        val merchantsById = merchantRepository.findAllById(deals.map { it.merchantId }.distinct()).associateBy { it.id }
        return deals.map { deal ->
            val product = productsById[deal.productId]
            val merchant = merchantsById[deal.merchantId]
            TimeDealView(deal, product?.name ?: "Unknown product", product?.imageUrl, merchant?.businessName ?: "Unknown store")
        }
    }

    fun getActiveDeals(pageable: Pageable): Page<TimeDealView> {
        val page = timeDealRepository.findActiveDeals(Instant.now(), pageable)
        return PageImpl(enrich(page.content), pageable, page.totalElements)
    }

    fun getMyDeals(ownerUserId: String, pageable: Pageable): Page<TimeDealView> {
        val merchant = getMyMerchant(ownerUserId)
        val page = timeDealRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.id, pageable)
        return PageImpl(enrich(page.content), pageable, page.totalElements)
    }

    fun getDeal(dealId: String): TimeDealView {
        val deal = timeDealRepository.findById(dealId).orElseThrow { TimeDealNotFoundException("Time deal not found") }
        return enrich(listOf(deal)).first()
    }

    /** Real early-end -- sets `endsAt` to now rather than deleting the row, preserving
     * a real historical record of what ran, same "never delete, just exclude from the
     * active query" discipline this class's own doc comment already establishes. */
    @Transactional
    fun endDeal(ownerUserId: String, dealId: String): TimeDeal {
        val merchant = getMyMerchant(ownerUserId)
        val deal = timeDealRepository.findById(dealId).orElseThrow { TimeDealNotFoundException("Time deal not found") }
        if (deal.merchantId != merchant.id) {
            throw TimeDealNotFoundException("Time deal not found")
        }
        deal.endsAt = Instant.now()
        return timeDealRepository.save(deal)
    }
}
