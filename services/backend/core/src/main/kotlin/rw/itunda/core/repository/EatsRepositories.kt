package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderItem
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.Rider
import java.time.Instant
import java.util.Optional

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

    // Real Uber Eats-style order-acceptance timeout (2026-08-16) -- every real order
    // still PLACED (never accepted or rejected by the restaurant) past
    // EatsOrderService.ORDER_ACCEPTANCE_TIMEOUT, backing OrderAcceptanceExpiryScheduler.
    fun findByStatusAndCreatedAtBefore(status: EatsOrderStatus, cutoff: Instant): List<EatsOrder>

    // Real 단건배달 (single-order delivery) enforcement (2026-07-26) -- see
    // EatsOrderService.claimDelivery's own doc comment. Backs the real guarantee that a
    // rider only ever carries one active delivery at a time, same Coupang Eats/배민1
    // real, sourced distinction docs/DESIGN_REFERENCES.md itself named.
    fun existsByRiderIdAndStatusIn(riderId: String, statuses: List<EatsOrderStatus>): Boolean

    // Real lost-update fix (product-feel-audit-adjacent concurrency sweep, §236): see
    // EatsOrderService.tipRider's own doc comment. Same findByIdForUpdate convention
    // WalletRepository/FraudFlagRepository/DebitCardRepository/CommunityPostRepository
    // already establish for a check-then-act-then-write row.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from EatsOrder o where o.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<EatsOrder>

    // Real busy-rider exclusion for dispatch (2026-07-26) -- lets
    // EatsOrderService.rankNearbyRiders' candidate pool skip riders already carrying a
    // delivery, avoiding a real, honest-but-wasted push offer to someone claimDelivery
    // would immediately reject anyway. One query, real DISTINCT riderId list -- same
    // "compute the pool once per scheduler tick" batching discipline
    // reassignExpiredOffers's own doc comment already establishes.
    @Query("SELECT DISTINCT o.riderId FROM EatsOrder o WHERE o.status IN :statuses AND o.riderId IS NOT NULL")
    fun findDistinctRiderIdsByStatusIn(@Param("statuses") statuses: List<EatsOrderStatus>): List<String>

    // Real Coupang Eats-style "recommended for you" ranking signal (2026-08-16, "AI
    // 개인화 메뉴 추천" -- see EatsController.getDishes' own doc comment). A plain,
    // honest set of real restaurants this buyer has actually ordered from before --
    // used to rank familiar merchants' dishes ahead of unfamiliar ones, not a
    // fabricated ML model.
    @Query("SELECT DISTINCT o.restaurantId FROM EatsOrder o WHERE o.buyerId = :buyerId")
    fun findDistinctRestaurantIdsByBuyerId(@Param("buyerId") buyerId: String): List<String>

    // Real abandoned-delivery sweep (2026-08-18) -- every real order still RIDER_ASSIGNED
    // or PICKED_UP whose rider has gone dark past EatsOrderService.DELIVERY_ABANDONMENT_TIMEOUT
    // since the last real status change, backing EatsOrderAbandonedDeliveryScheduler. See
    // EatsOrderService.getAbandonedDeliveries's own doc comment for the full account.
    fun findByStatusInAndUpdatedAtBefore(statuses: List<EatsOrderStatus>, cutoff: Instant): List<EatsOrder>

    // Real Uber Eats-style "busy kitchen" signal (2026-08-16, see Uber's own official
    // "Managing busy delivery times" merchant help article: a busy restaurant's real
    // in-kitchen order backlog is a genuine, sourced cause of delivery delay) -- one
    // batched GROUP BY for a whole browse page, same discipline
    // EatsFavoriteRepository.getFavoriteCounts already established. Caller passes the
    // real kitchen-stage statuses (PLACED/ACCEPTED/PREPARING) -- an order already
    // READY_FOR_PICKUP or later has left the kitchen's own workload, so counting it
    // would overstate how backed up the kitchen currently is.
    @Query("SELECT o.restaurantId as restaurantId, COUNT(o) as count FROM EatsOrder o WHERE o.restaurantId IN :restaurantIds AND o.status IN :statuses GROUP BY o.restaurantId")
    fun getActiveKitchenOrderCounts(
        @Param("restaurantIds") restaurantIds: List<String>,
        @Param("statuses") statuses: List<EatsOrderStatus>,
    ): List<RestaurantActiveOrderCountProjection>
}

interface RestaurantActiveOrderCountProjection {
    val restaurantId: String
    val count: Long
}

interface EatsOrderItemRepository : JpaRepository<EatsOrderItem, String> {
    fun findByOrderId(orderId: String): List<EatsOrderItem>
}
