package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderItem
import rw.itunda.core.domain.OrderReturnRequest
import rw.itunda.core.domain.OrderReturnStatus
import rw.itunda.core.domain.OrderStatus

interface OrderRepository : JpaRepository<Order, String> {
    // Real pagination from day one -- this session's own established convention since
    // the Partner SDK finding (retrofitting it later is real, avoidable extra work).
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String, pageable: Pageable): Page<Order>
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String, pageable: Pageable): Page<Order>

    // Real rider-delivery browse/claim query (2026-07-26) -- see Order.kt's own doc
    // comment on `riderId`. Every real PACKED order no rider has claimed yet.
    fun findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(status: OrderStatus, pageable: Pageable): Page<Order>
    fun findByRiderIdOrderByCreatedAtDesc(riderId: String, pageable: Pageable): Page<Order>
    fun existsByRiderIdAndStatusIn(riderId: String, statuses: List<OrderStatus>): Boolean
}

interface OrderItemRepository : JpaRepository<OrderItem, String> {
    fun findByOrderId(orderId: String): List<OrderItem>
}

interface OrderReturnRequestRepository : JpaRepository<OrderReturnRequest, String> {
    fun findByOrderId(orderId: String): List<OrderReturnRequest>
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String, pageable: Pageable): Page<OrderReturnRequest>
    fun findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId: String, status: OrderReturnStatus, pageable: Pageable): Page<OrderReturnRequest>
}
