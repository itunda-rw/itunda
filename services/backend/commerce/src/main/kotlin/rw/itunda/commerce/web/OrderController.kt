package rw.itunda.commerce.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.commerce.BuyerNoWalletException
import rw.itunda.commerce.EmptyOrderException
import rw.itunda.commerce.FavoriteProductNotFoundException
import rw.itunda.commerce.InvalidDeliveryAddressException
import rw.itunda.commerce.InvalidOrderStatusTransitionException
import rw.itunda.commerce.InvalidProductRatingException
import rw.itunda.commerce.InvalidQuantityException
import rw.itunda.commerce.MerchantNoWalletException
import rw.itunda.commerce.MerchantNotFoundException
import rw.itunda.commerce.OrderItemNotFoundException
import rw.itunda.commerce.OrderItemRequest
import rw.itunda.commerce.OrderNotFoundException
import rw.itunda.commerce.OrderProductNotFoundException
import rw.itunda.commerce.OrderService
import rw.itunda.commerce.ProductAlreadyReviewedException
import rw.itunda.commerce.ProductFavoriteService
import rw.itunda.commerce.ProductNotYetDeliveredException
import rw.itunda.commerce.ProductReviewService
import rw.itunda.commerce.SelfOrderException

data class PlaceOrderRequest(val merchantId: String, val items: List<OrderItemRequest>, val deliveryAddress: String)
data class UpdateOrderStatusRequest(val status: OrderStatus)
data class SubmitProductReviewRequest(val rating: Int, val comment: String? = null)

// Real Coupang-style checkout -- see OrderService's own doc comment for the full
// account, including the honest "self-declared fulfillment, no real courier network"
// scope. Normal itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val idempotencyService: IdempotencyService,
    private val productReviewService: ProductReviewService,
    private val productFavoriteService: ProductFavoriteService,
) {
    @PostMapping
    fun placeOrder(
        @RequestBody request: PlaceOrderRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/orders", idempotencyKey, request) {
            val detail = orderService.placeOrder(currentUser.userId, request.merchantId, request.items, request.deliveryAddress)
            201 to mapOf("success" to true, "order" to detail.order, "items" to detail.items)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/my-orders")
    fun getMyOrders(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = orderService.getMyOrders(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content) + pageMeta(page))
    }

    @GetMapping("/merchant-orders")
    fun getMerchantOrders(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = orderService.getMerchantOrders(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content) + pageMeta(page))
    }

    @GetMapping("/{orderId}")
    fun getOrder(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val detail = orderService.getOrderDetail(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to detail.order, "items" to detail.items))
    }

    @PostMapping("/{orderId}/status")
    fun updateStatus(
        @PathVariable orderId: String,
        @RequestBody request: UpdateOrderStatusRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = orderService.updateOrderStatus(currentUser.userId, orderId, request.status)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    // Real cancellation + refund (2026-07-18) -- buyer or seller, PLACED orders only.
    // See OrderService.cancelOrder's own doc comment for the full account.
    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = orderService.cancelOrder(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    // Real post-delivery product reviews (2026-07-20) -- see ProductReviewService's own
    // doc comment for the full account, mirroring EatsReviewService's already-proven
    // shape. Review submission is buyer-only, ownership-checked inside the service;
    // reading a product's reviews/rating is public (any authenticated itunda user can
    // browse a product's real reviews before buying, same as the catalog itself).
    @PostMapping("/items/{orderItemId}/review")
    fun submitProductReview(
        @PathVariable orderItemId: String,
        @RequestBody request: SubmitProductReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = productReviewService.submitReview(currentUser.userId, orderItemId, request.rating, request.comment)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "review" to review))
    }

    @GetMapping("/products/{productId}/reviews")
    fun getProductReviews(
        @PathVariable productId: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = productReviewService.getProductReviews(productId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "reviews" to page.content) + pageMeta(page))
    }

    @GetMapping("/products/{productId}/rating")
    fun getProductRating(@PathVariable productId: String): ResponseEntity<Map<String, Any?>> {
        val summary = productReviewService.getProductRating(productId)
        return ResponseEntity.ok(mapOf("success" to true, "average" to summary.average, "count" to summary.count))
    }

    // Real product wishlist (2026-07-20) -- see ProductFavoriteService's own doc
    // comment. Mirrors EatsController's own favorite-restaurant endpoints field-for-field.
    @PostMapping("/products/{productId}/favorite")
    fun addFavorite(
        @PathVariable productId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val favorite = productFavoriteService.addFavorite(currentUser.userId, productId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "favorite" to favorite))
    }

    @DeleteMapping("/products/{productId}/favorite")
    fun removeFavorite(
        @PathVariable productId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Boolean>> {
        productFavoriteService.removeFavorite(currentUser.userId, productId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/products/favorites")
    fun getMyFavorites(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = productFavoriteService.getMyFavorites(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "favorites" to page.content) + pageMeta(page))
    }

    @ExceptionHandler(FavoriteProductNotFoundException::class)
    fun handleFavoriteProductNotFound(ex: FavoriteProductNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PRODUCT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(OrderItemNotFoundException::class)
    fun handleOrderItemNotFound(ex: OrderItemNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ORDER_ITEM_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ProductNotYetDeliveredException::class)
    fun handleProductNotYetDelivered(ex: ProductNotYetDeliveredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PRODUCT_NOT_YET_DELIVERED", ex.message ?: "Conflict"))

    @ExceptionHandler(ProductAlreadyReviewedException::class)
    fun handleProductAlreadyReviewed(ex: ProductAlreadyReviewedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PRODUCT_ALREADY_REVIEWED", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidProductRatingException::class)
    fun handleInvalidProductRating(ex: InvalidProductRatingException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_RATING", ex.message ?: "Bad request"))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantNoWalletException::class)
    fun handleMerchantNoWallet(ex: MerchantNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(BuyerNoWalletException::class)
    fun handleBuyerNoWallet(ex: BuyerNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmptyOrderException::class)
    fun handleEmptyOrder(ex: EmptyOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_ORDER", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidDeliveryAddressException::class)
    fun handleInvalidAddress(ex: InvalidDeliveryAddressException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DELIVERY_ADDRESS", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidQuantityException::class)
    fun handleInvalidQuantity(ex: InvalidQuantityException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_QUANTITY", ex.message ?: "Bad request"))

    @ExceptionHandler(OrderProductNotFoundException::class)
    fun handleProductNotFound(ex: OrderProductNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PRODUCT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SelfOrderException::class)
    fun handleSelfOrder(ex: SelfOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_ORDER_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleOrderNotFound(ex: OrderNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ORDER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidOrderStatusTransitionException::class)
    fun handleInvalidTransition(ex: InvalidOrderStatusTransitionException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INVALID_ORDER_STATUS_TRANSITION", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))
}
