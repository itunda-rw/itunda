package rw.itunda.merchant

import org.springframework.stereotype.Service
import rw.itunda.core.domain.MerchantProfileView
import rw.itunda.core.repository.MerchantProfileViewRepository
import rw.itunda.core.repository.MerchantRepository
import java.time.LocalDate

/**
 * Real 비즈프로필 (Karrot Business Profile) visitor-count tracking -- see
 * `MerchantProfileView`'s own doc comment for the full account. `recordView` is
 * called from `MapsPlaceDetailService.getPlaceDetail` (the real, already-built
 * consumer place-detail fetch from this session's Maps work) -- every real consumer
 * open of a merchant's place-detail counts as one real visit, same "count every real
 * detail-page fetch, no per-viewer dedup" honesty scope `Listing.viewCount` already
 * established.
 */
@Service
class MerchantProfileViewService(
    private val merchantProfileViewRepository: MerchantProfileViewRepository,
    private val merchantRepository: MerchantRepository,
) {
    fun recordView(merchantId: String) {
        val today = LocalDate.now()
        merchantProfileViewRepository.upsertView("merchant_profile_view_${merchantId}_$today", merchantId, today)
    }

    fun getTrend(ownerUserId: String, days: Int = 7): List<MerchantProfileView> {
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        val today = LocalDate.now()
        return merchantProfileViewRepository.findByMerchantIdAndViewDateBetweenOrderByViewDateAsc(
            merchant.id, today.minusDays((days - 1).toLong()), today,
        )
    }
}
