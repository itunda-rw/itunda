package rw.itunda.merchant

import org.springframework.stereotype.Service
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.MerchantRepository

// Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up: "it
// should look 100% like toss pay UI/UX features everything") -- the reference's own
// "345 stores nearby where you can earn rewards" banner. A separate file/service from
// MerchantService/MerchantController (both already over their file-size-lint baseline)
// rather than growing either further -- mirrors AgentService.nearby/
// AgentDiscoveryController's own identical split for the exact same reason. Same
// coarse-findAll-then-Haversine-filter pattern already used by AgentService,
// PropertyListingService, and MarketplaceService for a merchant table at itunda's
// current real scale.
data class NearbyMerchant(
    val id: String,
    val businessName: String,
    val category: String?,
    val cashbackRate: Double,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
)

@Service
class MerchantDiscoveryService(private val merchantRepository: MerchantRepository) {
    fun nearby(latitude: Double, longitude: Double, radiusKm: Double): List<NearbyMerchant> {
        require(GeoUtils.isValidCoordinate(latitude, longitude) && GeoUtils.isWithinRwanda(latitude, longitude)) { "Search location must be within Rwanda" }
        require(radiusKm in 0.1..100.0) { "radiusKm must be between 0.1 and 100" }
        return merchantRepository.findAll().asSequence()
            .filter { it.status == MerchantStatus.ACTIVE && it.latitude != null && it.longitude != null }
            .map {
                NearbyMerchant(
                    it.id, it.businessName, it.category,
                    (it.cashbackRate ?: ShoppingCashbackService.DEFAULT_CASHBACK_RATE).toDouble(),
                    it.latitude!!, it.longitude!!,
                    GeoUtils.haversineKm(latitude, longitude, it.latitude!!, it.longitude!!),
                )
            }
            .filter { it.distanceKm <= radiusKm }
            .sortedBy { it.distanceKm }
            .take(50)
            .toList()
    }
}
