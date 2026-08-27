package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderItem
import rw.itunda.core.domain.OrderReturnRequest
import rw.itunda.core.domain.OrderReturnStatus
import rw.itunda.core.domain.OrderStatus
import java.time.Instant

// Same real average/count-style projection shape as ProductRatingSummaryProjection
// (ProductReviewRepository.kt, same package) -- just productId + a real count.
interface ProductOrderCountProjection {
    val productId: String
    val count: Long
}

// Same real productId + count shape as ProductOrderCountProjection above, just for
// a co-occurring product rather than the product itself -- see
// OrderItemRepository.getFrequentlyOrderedWith's own doc comment.
interface FrequentlyOrderedWithProjection {
    val productId: String
    val count: Long
}

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

    // Real Coupang WING-style top-selling-products report (2026-08-16) -- see
    // MerchantService.getTopSellingProducts's own doc comment. Unpaginated, same as
    // MerchantService.getReport's own bounded-31-day-window transaction fetch.
    fun findByMerchantIdAndCreatedAtBetween(merchantId: String, from: Instant, to: Instant): List<Order>
}

interface OrderItemRepository : JpaRepository<OrderItem, String> {
    fun findByOrderId(orderId: String): List<OrderItem>
    fun findByOrderIdIn(orderIds: List<String>): List<OrderItem>

    // Real Coupang WING 전환율 (conversion rate) support -- see
    // MerchantProductService.getProduct's own doc comment. Real distinct-order count
    // for one product, backing view-to-order conversion alongside MerchantProduct.viewCount.
    fun countByProductId(productId: String): Long

    // Real batched version of countByProductId above (2026-08-28) -- same no-N+1
    // discipline as ProductReviewRepository.getProductRatingSummaries: one GROUP BY
    // query for a whole deals/search/catalog page of products, backing a real
    // "Best seller" badge (genuine gross order count, same "gross collected at
    // placement" definition MerchantService.getTopSellingProducts already established
    // -- not filtered to a completed/settled status, so this stays a single simple
    // query rather than needing to join back to Order for its status).
    @Query(
        "SELECT oi.productId as productId, COUNT(oi) as count " +
            "FROM OrderItem oi WHERE oi.productId IN :productIds GROUP BY oi.productId",
    )
    fun getProductOrderCounts(@Param("productIds") productIds: List<String>): List<ProductOrderCountProjection>

    // Real "frequently ordered together" cross-sell (itunda Eats redesign, 2026-08-28)
    // -- see the reference's own "다른 고객은 함께 주문했어요" (other customers also
    // ordered) rail. A real, derived co-occurrence signal: for a given real productId,
    // find every OTHER real product that has genuinely shared a real Order with it, and
    // how many real orders they've co-occurred in. Self-join on orderId (standard JPQL
    // cross-join-with-WHERE-condition, same shape MerchantProductRepository.search's own
    // doc comment already documents for a raw-string-id join with no JPA relationship
    // mapping). Ranked highest-co-occurrence-first; the caller applies a real minimum
    // threshold (see ShoppingController's own bestSellerProductIds precedent) so a
    // product that's only ever shared ONE past order with another never gets surfaced
    // as a fabricated-feeling "frequently" bought pair.
    @Query(
        "SELECT other.productId as productId, COUNT(other) as count " +
            "FROM OrderItem target, OrderItem other " +
            "WHERE target.productId = :productId AND other.orderId = target.orderId AND other.productId <> :productId " +
            "GROUP BY other.productId ORDER BY COUNT(other) DESC",
    )
    fun getFrequentlyOrderedWith(@Param("productId") productId: String): List<FrequentlyOrderedWithProjection>
}

interface OrderReturnRequestRepository : JpaRepository<OrderReturnRequest, String> {
    fun findByOrderId(orderId: String): List<OrderReturnRequest>
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String, pageable: Pageable): Page<OrderReturnRequest>
    fun findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId: String, status: OrderReturnStatus, pageable: Pageable): Page<OrderReturnRequest>
}
