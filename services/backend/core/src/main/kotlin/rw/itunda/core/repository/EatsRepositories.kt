package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderItem
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.Rider

interface RiderRepository : JpaRepository<Rider, String> {
    fun findByUserId(userId: String): Rider?
}

interface EatsOrderRepository : JpaRepository<EatsOrder, String> {
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String, pageable: Pageable): Page<EatsOrder>
    fun findByRestaurantIdOrderByCreatedAtDesc(restaurantId: String, pageable: Pageable): Page<EatsOrder>
    fun findByRiderIdOrderByCreatedAtDesc(riderId: String, pageable: Pageable): Page<EatsOrder>

    // Real query backing a rider's "available deliveries" list -- any order a restaurant
    // has marked READY_FOR_PICKUP that no rider has claimed yet.
    fun findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(status: EatsOrderStatus, pageable: Pageable): Page<EatsOrder>
}

interface EatsOrderItemRepository : JpaRepository<EatsOrderItem, String> {
    fun findByOrderId(orderId: String): List<EatsOrderItem>
}
