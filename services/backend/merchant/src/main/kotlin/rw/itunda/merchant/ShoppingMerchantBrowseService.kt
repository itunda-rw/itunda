package rw.itunda.merchant

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantBusinessType
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.geo.DeliveryEtaEstimator
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.EatsFavoriteRepository
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Extracted from ShoppingController.getEligibleMerchants (2026-08-28, keeping
 * ShoppingController under the real 500-line file-size-lint guideline as the
 * Shopping redesign pass added new endpoints/logic to it) -- same real search +
 * batched-enrichment + real in-page sort behavior, unchanged, just moved out of
 * the controller so "code that changes together lives together" (a real search/
 * sort algorithm, not request/response plumbing) stays true. See
 * ShoppingCashbackService's own doc comment for why this shares the `Shopping`
 * naming prefix with it.
 */
@Service
class ShoppingMerchantBrowseService(
    private val merchantRepository: MerchantRepository,
    private val eatsReviewRepository: EatsReviewRepository,
    private val eatsFavoriteRepository: EatsFavoriteRepository,
    private val eatsOrderRepository: EatsOrderRepository,
    private val merchantProductRepository: MerchantProductRepository,
) {
    fun browse(
        category: String?,
        businessType: MerchantBusinessType?,
        q: String?,
        buyerLat: Double?,
        buyerLng: Double?,
        sortBy: String?,
        pageable: Pageable,
    ): Pair<Page<Merchant>, List<Map<String, Any?>>> {
        val page = merchantRepository.search(MerchantStatus.ACTIVE, category?.trim()?.ifBlank { null }, businessType, q?.trim()?.ifBlank { null }, pageable)
        val hasBuyerLocation = buyerLat != null && buyerLng != null && GeoUtils.isValidCoordinate(buyerLat, buyerLng)
        // Real batched rating lookup -- one GROUP BY query for the whole page, not one
        // per-merchant call. See EatsReviewRepository.getRestaurantRatingSummaries's own
        // doc comment.
        val ratingByMerchant = if (page.content.isNotEmpty()) {
            eatsReviewRepository.getRestaurantRatingSummaries(page.content.map { it.id }).associateBy { it.restaurantId }
        } else {
            emptyMap()
        }
        val favoriteCountByMerchant = if (page.content.isNotEmpty()) {
            eatsFavoriteRepository.getFavoriteCounts(page.content.map { it.id }).associate { it.restaurantId to it.count }
        } else {
            emptyMap()
        }
        // Real Uber Eats-style "busy kitchen" signal -- see
        // EatsOrderRepository.getActiveKitchenOrderCounts's own doc comment.
        val kitchenOrderCountByMerchant = if (page.content.isNotEmpty()) {
            eatsOrderRepository.getActiveKitchenOrderCounts(
                page.content.map { it.id },
                listOf(EatsOrderStatus.PLACED, EatsOrderStatus.ACCEPTED, EatsOrderStatus.PREPARING),
            ).associate { it.restaurantId to it.count }
        } else {
            emptyMap()
        }
        // Real "Discount" sort mode (2026-08-28) -- see
        // getMaxDiscountByMerchantIds' own doc comment for why this is a real,
        // derived per-merchant signal rather than a fabricated one.
        val maxDiscountByMerchant = if (page.content.isNotEmpty()) {
            merchantProductRepository.getMaxDiscountByMerchantIds(page.content.map { it.id }).associate { it.merchantId to it.maxDiscountPercent }
        } else {
            emptyMap()
        }
        data class MerchantRow(val map: Map<String, Any?>, val deliveryTimeMinutes: Int?, val favoriteCount: Long, val rating: Double?, val distanceKm: Double?, val maxDiscountPercent: Int?, val minOrderAmount: java.math.BigDecimal?)
        var rows = page.content.map { merchant ->
            val distanceKm = if (hasBuyerLocation && merchant.latitude != null && merchant.longitude != null) {
                GeoUtils.haversineKm(buyerLat!!, buyerLng!!, merchant.latitude!!, merchant.longitude!!)
            } else {
                null
            }
            val rating = ratingByMerchant[merchant.id]
            val isBusy = (kitchenOrderCountByMerchant[merchant.id] ?: 0L) >= DeliveryEtaEstimator.BUSY_ORDER_THRESHOLD
            val deliveryTimeMinutes = distanceKm?.let { DeliveryEtaEstimator.estimateDeliveryMinutes(it, merchant.avgPrepTimeMinutes, isBusy) }
            val favoriteCount = favoriteCountByMerchant[merchant.id] ?: 0L
            MerchantRow(
                mapOf(
                    "merchantId" to merchant.id,
                    "businessName" to merchant.businessName,
                    "category" to merchant.category,
                    "cashbackRate" to "1%",
                    // Real Baemin 찜 (favorites) count (2026-08-16) -- see
                    // EatsFavoriteRepository.getFavoriteCounts's own doc comment.
                    "favoriteCount" to favoriteCount,
                    // Real Baemin CEO app 영업일시중지 (temporarily pause business) --
                    // see Merchant.isAcceptingOrders's own doc comment. A buyer needs
                    // this surfaced on the browse card itself, not just discovered as a
                    // real 400 after trying to check out.
                    "isAcceptingOrders" to merchant.isAcceptingOrders,
                    // Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day
                    // schedule) -- see Merchant.isClosedToday's own doc comment. So the
                    // buyer sees this on the browse card itself rather than discovering
                    // it as a 400 after trying to check out.
                    "closedToday" to merchant.isClosedToday(),
                    // Real Uber Eats-style "busy kitchen" signal (2026-08-16) -- see
                    // EatsOrderRepository.getActiveKitchenOrderCounts's own doc comment.
                    // deliveryTimeMinutes above already includes this restaurant's real
                    // delay bump when true; this flag is what drives a real UI badge
                    // explaining WHY the estimate is longer than usual.
                    "isBusy" to isBusy,
                    // Real optional location (2026-07-19) -- lets a real map view plot real
                    // merchants, same field already set via POST /api/v1/merchant/location for
                    // Eats' distance-based delivery fee. Null for a merchant that hasn't set one.
                    "latitude" to merchant.latitude,
                    "longitude" to merchant.longitude,
                    "photoUrl" to merchant.photoUrl,
                    "minOrderAmount" to merchant.minOrderAmount,
                    "rating" to rating?.average,
                    "reviewCount" to (rating?.count ?: 0L),
                    "distanceKm" to distanceKm?.let { BigDecimal(it).setScale(2, RoundingMode.HALF_UP) },
                    "deliveryTimeMinutes" to deliveryTimeMinutes,
                    // Real 단건배달 (single-order delivery) guarantee (2026-07-26) -- see
                    // EatsOrderService.claimDelivery's own doc comment. Universally true,
                    // not a per-merchant toggle: enforced at claim time for every real
                    // itunda delivery, the same real Coupang Eats/배민1 distinction
                    // docs/DESIGN_REFERENCES.md named.
                    "singleOrderDelivery" to true,
                    // Real merchant-set phone/hours (2026-08-09) -- see Merchant.kt's own
                    // doc comment. Null unless the merchant has actually set one.
                    "phoneNumber" to merchant.phoneNumber,
                    "openingHours" to merchant.openingHours,
                    // Real Coupang 와우(WOW)-style per-restaurant "member gets free
                    // delivery" badge (itunda Eats redesign, 2026-08-28) -- see
                    // Merchant.participatesInEatsMembership's own doc comment. Already
                    // computed on the entity, just never surfaced on this response
                    // before now.
                    "participatesInEatsMembership" to merchant.participatesInEatsMembership,
                    // Real "Discount" sort signal, also shown as a badge -- see
                    // getMaxDiscountByMerchantIds' own doc comment. Null (not 0) when
                    // this merchant genuinely has no active discounted product.
                    "maxDiscountPercent" to maxDiscountByMerchant[merchant.id],
                ),
                deliveryTimeMinutes,
                favoriteCount,
                rating?.average,
                distanceKm,
                maxDiscountByMerchant[merchant.id],
                merchant.minOrderAmount,
            )
        }
        // sortedBy is stable, so ties (or every row when no buyer location was supplied,
        // deliveryTimeMinutes null for all) keep the query's own existing order.
        // nullsLast: a merchant with no real distance/prep-time data sorts after every
        // merchant this sort CAN honestly rank, never fabricated to the front or back.
        if (sortBy == "delivery_time") {
            rows = rows.sortedWith(compareBy(nullsLast()) { it.deliveryTimeMinutes })
        } else if (sortBy == "favorites") {
            rows = rows.sortedByDescending { it.favoriteCount }
        } else if (sortBy == "rating") {
            // Real Baemin/Coupang Eats-style rating sort (2026-08-19) -- both `rating`
            // and `distanceKm` were already computed per-row above for display, just
            // never sortable. Explicit two-key comparator (is-null, then descending
            // rating) rather than compareByDescending(nullsFirst()/nullsLast()) --
            // that combinator's null-placement flips in a genuinely easy-to-get-backwards
            // way once wrapped in Descending, and this reads unambiguously instead: a
            // merchant with zero real reviews sorts after every merchant this sort CAN
            // honestly rank.
            rows = rows.sortedWith(compareBy<MerchantRow> { it.rating == null }.thenByDescending { it.rating })
        } else if (sortBy == "distance") {
            // Only meaningful when the caller supplied a real buyerLat/buyerLng, same
            // real-location gate delivery_time already requires -- distanceKm is null
            // for every row otherwise.
            rows = rows.sortedWith(compareBy(nullsLast()) { it.distanceKm })
        } else if (sortBy == "discount") {
            // Real Eats redesign quick-filter chip (2026-08-28) -- explicit two-key
            // comparator (is-null, then descending discount), same discipline the
            // rating sort above already establishes: wrapping nullsLast() in
            // .reversed() would silently flip it back to nulls-FIRST, exactly the
            // "easy to get backwards" trap that sort's own comment warns about.
            rows = rows.sortedWith(compareBy<MerchantRow> { it.maxDiscountPercent == null }.thenByDescending { it.maxDiscountPercent })
        } else if (sortBy == "min_order") {
            // Real Eats redesign quick-filter chip (2026-08-28) -- ascending (lowest
            // real minOrderAmount first); a merchant with no real minimum set sorts
            // after every merchant this sort CAN honestly rank.
            rows = rows.sortedWith(compareBy(nullsLast()) { it.minOrderAmount })
        }
        return page to rows.map { it.map }
    }
}
