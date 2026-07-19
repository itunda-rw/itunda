package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderItem
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.Rider
import java.time.Instant

interface RiderRepository : JpaRepository<Rider, String> {
    fun findByUserId(userId: String): Rider?

    // Real candidate pool for proactive nearest-rider push notification (2026-07-19) --
    // every online rider with a real known position, the same real coordinate fields
    // getAvailableDeliveries' own proximity ranking already reads.
    fun findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull(): List<Rider>
}

interface EatsOrderRepository : JpaRepository<EatsOrder, String> {
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String, pageable: Pageable): Page<EatsOrder>
    fun findByRestaurantIdOrderByCreatedAtDesc(restaurantId: String, pageable: Pageable): Page<EatsOrder>
    fun findByRiderIdOrderByCreatedAtDesc(riderId: String, pageable: Pageable): Page<EatsOrder>

    // Real query backing a rider's "available deliveries" list -- any order a restaurant
    // has marked READY_FOR_PICKUP that no rider has claimed yet.
    fun findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(status: EatsOrderStatus, pageable: Pageable): Page<EatsOrder>

    // Real automatic-dispatch reassignment query (2026-07-20) -- every real unassigned
    // order whose exclusive offer window has expired, backing DispatchOfferScheduler.
    fun findByOfferExpiresAtBeforeAndRiderIdIsNull(cutoff: Instant): List<EatsOrder>
}

interface EatsOrderItemRepository : JpaRepository<EatsOrderItem, String> {
    fun findByOrderId(orderId: String): List<EatsOrderItem>
}
