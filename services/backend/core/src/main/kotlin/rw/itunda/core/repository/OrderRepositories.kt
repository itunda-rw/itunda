package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderItem

interface OrderRepository : JpaRepository<Order, String> {
    // Real pagination from day one -- this session's own established convention since
    // the Partner SDK finding (retrofitting it later is real, avoidable extra work).
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String, pageable: Pageable): Page<Order>
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String, pageable: Pageable): Page<Order>
}

interface OrderItemRepository : JpaRepository<OrderItem, String> {
    fun findByOrderId(orderId: String): List<OrderItem>
}
