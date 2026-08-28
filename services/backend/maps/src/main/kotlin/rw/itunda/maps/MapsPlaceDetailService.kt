package rw.itunda.maps

import org.springframework.stereotype.Service
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantUpdate
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.eats.EatsReviewService
import rw.itunda.eats.RatingSummary
import rw.itunda.merchant.MerchantNotFoundException
import rw.itunda.merchant.MerchantProfileViewService
import rw.itunda.merchant.MerchantUpdateService

data class MapsPlaceDetail(
    val merchantId: String,
    val businessName: String,
    val category: String?,
    val photoUrl: String?,
    val photoUrls: List<String>,
    val openingHours: String?,
    val phoneNumber: String?,
    val aiSummary: String?,
    val rating: RatingSummary,
    val goodPointCounts: Map<String, Int>,
    val menu: List<MerchantProduct>,
    val updates: List<MerchantUpdate>,
)

/**
 * A real consolidated place-detail assembly -- see MapsPlaceDetail's own shape for the
 * full account (itunda Maps redesign, 2026-08-28, direct Naver Map reference: the
 * place-detail Home/Menu/Reviews/Photos/News tabs). Before this, itunda had no
 * backend place-detail endpoint at all -- every client independently re-derived a
 * place's enrichment by matching a searched result's lat/lng against a separately-
 * fetched Merchant directory. This becomes the one real source every client's
 * place-detail panel reads from instead.
 *
 * Lives in `:maps` (not `:merchant`/`:eats`) since it's the real cross-cutting
 * assembly point -- reuses EatsReviewService's rating/tag methods and
 * MerchantUpdateService's real news-feed method directly, never duplicating their
 * logic (confirmed no circular module dependency before adding these as real
 * `:maps` -> `:eats`/`:merchant` dependencies).
 */
@Service
class MapsPlaceDetailService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val eatsReviewService: EatsReviewService,
    private val merchantUpdateService: MerchantUpdateService,
    private val merchantProfileViewService: MerchantProfileViewService,
) {
    fun getPlaceDetail(merchantId: String): MapsPlaceDetail {
        val merchant = merchantRepository.findById(merchantId).orElseThrow { MerchantNotFoundException("Merchant not found") }
        // Real 비즈프로필 (Karrot Business Profile) visitor-count tracking (itunda Hood
        // redesign, 2026-08-28) -- every real consumer open of this place-detail counts
        // as one real visit. Best-effort: never blocks the real place-detail response
        // itself if the write somehow fails.
        try { merchantProfileViewService.recordView(merchantId) } catch (e: Exception) { /* honest best-effort, see doc comment */ }
        return MapsPlaceDetail(
            merchantId = merchant.id,
            businessName = merchant.businessName,
            category = merchant.category,
            photoUrl = merchant.photoUrl,
            photoUrls = merchant.photoUrlList(),
            openingHours = merchant.openingHours,
            phoneNumber = merchant.phoneNumber,
            aiSummary = merchant.aiSummary,
            rating = eatsReviewService.getRestaurantRating(merchantId),
            goodPointCounts = eatsReviewService.restaurantGoodPointCounts(merchantId),
            menu = merchantProductRepository.findByMerchantIdAndActiveTrue(merchantId),
            updates = merchantUpdateService.getUpdates(merchantId),
        )
    }
}
