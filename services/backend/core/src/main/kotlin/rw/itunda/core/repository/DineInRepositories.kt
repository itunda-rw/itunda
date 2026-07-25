package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.DineInOrder
import rw.itunda.core.domain.DineInOrderItem

interface DineInOrderRepository : JpaRepository<DineInOrder, String> {
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String, pageable: Pageable): Page<DineInOrder>
    fun findByRestaurantIdOrderByCreatedAtDesc(restaurantId: String, pageable: Pageable): Page<DineInOrder>
}

interface DineInOrderItemRepository : JpaRepository<DineInOrderItem, String> {
    fun findByOrderId(orderId: String): List<DineInOrderItem>
}
