package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    // Real 단건배달 (single-order delivery) enforcement (2026-07-26) -- see
    // EatsOrderService.claimDelivery's own doc comment. Backs the real guarantee that a
    // rider only ever carries one active delivery at a time, same Coupang Eats/배민1
    // real, sourced distinction docs/DESIGN_REFERENCES.md itself named.
    fun existsByRiderIdAndStatusIn(riderId: String, statuses: List<EatsOrderStatus>): Boolean

    // Real busy-rider exclusion for dispatch (2026-07-26) -- lets
    // EatsOrderService.rankNearbyRiders' candidate pool skip riders already carrying a
    // delivery, avoiding a real, honest-but-wasted push offer to someone claimDelivery
    // would immediately reject anyway. One query, real DISTINCT riderId list -- same
    // "compute the pool once per scheduler tick" batching discipline
    // reassignExpiredOffers's own doc comment already establishes.
    @Query("SELECT DISTINCT o.riderId FROM EatsOrder o WHERE o.status IN :statuses AND o.riderId IS NOT NULL")
    fun findDistinctRiderIdsByStatusIn(@Param("statuses") statuses: List<EatsOrderStatus>): List<String>
}

interface EatsOrderItemRepository : JpaRepository<EatsOrderItem, String> {
    fun findByOrderId(orderId: String): List<EatsOrderItem>
}
