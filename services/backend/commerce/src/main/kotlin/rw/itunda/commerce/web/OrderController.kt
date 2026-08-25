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
import rw.itunda.core.domain.OrderReturnType
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.commerce.BuyerNoAccountException
import rw.itunda.commerce.DeliveryAlreadyClaimedException
import rw.itunda.commerce.EmptyOrderException
import rw.itunda.commerce.FavoriteProductNotFoundException
import rw.itunda.commerce.InvalidDeliveryAddressException
import rw.itunda.commerce.InvalidOrderStatusTransitionException
import rw.itunda.commerce.InvalidProductRatingException
import rw.itunda.commerce.InvalidProductReviewReplyException
import rw.itunda.commerce.InvalidQuantityException
import rw.itunda.commerce.InsufficientProductStockException
import rw.itunda.commerce.InvalidReturnReasonException
import rw.itunda.commerce.MerchantNoAccountException
import rw.itunda.commerce.MerchantNotAcceptingOrdersException
import rw.itunda.commerce.MerchantNotFoundException
import rw.itunda.commerce.MinOrderAmountNotMetException
import rw.itunda.commerce.OrderItemNotFoundException
import rw.itunda.commerce.OrderItemRequest
import rw.itunda.commerce.OrderNotFoundException
import rw.itunda.commerce.OrderProductNotFoundException
import rw.itunda.commerce.OrderReturnService
import rw.itunda.commerce.OrderService
import rw.itunda.commerce.ProductAlreadyReviewedException
import rw.itunda.commerce.ProductReviewNotFoundException
import rw.itunda.commerce.ProductFavoriteService
import rw.itunda.commerce.InvalidProductInquiryException
import rw.itunda.commerce.InvalidProductInquiryAnswerException
import rw.itunda.commerce.ProductInquiryNotFoundException
import rw.itunda.commerce.ProductInquiryService
import rw.itunda.commerce.ProductNotYetDeliveredException
import rw.itunda.commerce.ProductReviewService
import rw.itunda.commerce.ProductSoldOutException
import rw.itunda.commerce.SurplusDealExpiredException
import rw.itunda.commerce.ReturnAlreadyRequestedException
import rw.itunda.commerce.ReturnOrderNotDeliveredException
import rw.itunda.commerce.ReturnOrderNotFoundException
import rw.itunda.commerce.ReturnRequestAlreadyDecidedException
import rw.itunda.commerce.ReturnRequestNotFoundException
import rw.itunda.commerce.ReturnWindowExpiredException
import rw.itunda.commerce.RiderAlreadyOnDeliveryException
import rw.itunda.commerce.RiderNotAvailableException
import rw.itunda.commerce.RiderNotRegisteredException
import rw.itunda.commerce.SelfOrderException

data class PlaceOrderRequest(
    val merchantId: String, val items: List<OrderItemRequest>, val deliveryAddress: String,
    // Real 쿠팡파트너스 (Coupang Partners)-style affiliate link code (item 229) -- see
    // AffiliateService's own doc comment. Optional; omitted/unknown/self-referral all
    // fall through to a normal order with no commission paid.
    val referralCode: String? = null,
)
data class UpdateOrderStatusRequest(val status: OrderStatus)
data class SubmitProductReviewRequest(val rating: Int, val comment: String? = null)
data class ReplyToProductReviewRequest(val reply: String)
data class AskProductInquiryRequest(val question: String)
data class AnswerProductInquiryRequest(val answer: String)
data class RequestReturnRequest(val type: OrderReturnType, val reasonCode: String, val reasonNote: String? = null)
data class DecideReturnRequest(val approve: Boolean)

// Real Coupang-style checkout -- see OrderService's own doc comment for the full
// account, including the two real fulfillment paths (merchant self-declared, or
// itunda's own rider fleet claiming/tracking a delivery). Normal itunda-user JWT gate
// (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val idempotencyService: IdempotencyService,
    private val productReviewService: ProductReviewService,
    private val productFavoriteService: ProductFavoriteService,
    private val orderReturnService: OrderReturnService,
    private val productInquiryService: ProductInquiryService,
) {
    @PostMapping
    fun placeOrder(
        @RequestBody request: PlaceOrderRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/orders", idempotencyKey, request) {
            val detail = orderService.placeOrder(currentUser.userId, request.merchantId, request.items, request.deliveryAddress, request.referralCode)
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

    // Real itunda-own-fleet delivery claim/tracking (2026-07-26) -- see OrderService's
    // own doc comment. A rider registers once via POST /api/v1/eats/riders/register (the
    // Rider entity is shared -- no separate Commerce-side registration needed) and can
    // then claim either Eats or Commerce deliveries with the same account.
    @GetMapping("/available-deliveries")
    fun getAvailableDeliveries(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = orderService.getAvailableDeliveries(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content) + pageMeta(page))
    }

    @PostMapping("/{orderId}/claim-delivery")
    fun claimDelivery(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = orderService.claimDelivery(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    @PostMapping("/{orderId}/complete-delivery")
    fun completeDelivery(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = orderService.completeDelivery(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    @GetMapping("/my-deliveries")
    fun getMyDeliveries(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = orderService.getMyDeliveries(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content) + pageMeta(page))
    }

    // Real live rider-location tracking (2026-07-26) -- `available: false` (not an
    // error) is the honest, expected response whenever there's genuinely nothing to
    // show yet, mirroring EatsController's identical endpoint exactly.
    @GetMapping("/{orderId}/rider-location")
    fun getRiderLocation(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val location = orderService.getRiderLocation(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "available" to (location != null), "location" to location))
    }

    // Real post-delivery Return & Exchange requests (2026-07-26) -- see
    // OrderReturnService's own doc comment for the full account, including why this is
    // genuinely distinct from cancelOrder above.
    @PostMapping("/{orderId}/return")
    fun requestReturn(
        @PathVariable orderId: String,
        @RequestBody request: RequestReturnRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val returnRequest = orderReturnService.requestReturn(currentUser.userId, orderId, request.type, request.reasonCode, request.reasonNote)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "returnRequest" to returnRequest))
    }

    @GetMapping("/returns/my-requests")
    fun getMyReturnRequests(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = orderReturnService.getMyReturnRequests(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "returnRequests" to page.content) + pageMeta(page))
    }

    @GetMapping("/returns/merchant-queue")
    fun getMerchantReturnQueue(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = orderReturnService.getMerchantReturnQueue(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "returnRequests" to page.content) + pageMeta(page))
    }

    @PostMapping("/returns/{returnRequestId}/decide")
    fun decideReturnRequest(
        @PathVariable returnRequestId: String,
        @RequestBody request: DecideReturnRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val returnRequest = orderReturnService.decide(currentUser.userId, returnRequestId, request.approve)
        return ResponseEntity.ok(mapOf("success" to true, "returnRequest" to returnRequest))
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

    // Real owner-side reply (2026-07-26) -- see ProductReviewService's own doc comment.
    @PostMapping("/reviews/{reviewId}/reply")
    fun replyToProductReview(
        @PathVariable reviewId: String,
        @RequestBody request: ReplyToProductReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = productReviewService.replyToProductReview(currentUser.userId, reviewId, request.reply)
        return ResponseEntity.ok(mapOf("success" to true, "review" to review))
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

    // Real Coupang/Naver-style "helpful" idempotent toggle (2026-08-25) -- see
    // ProductReviewService.toggleHelpful's own doc comment. Mirrors
    // EatsController.toggleReviewHelpful's exact shape.
    @PostMapping("/reviews/{reviewId}/helpful")
    fun toggleProductReviewHelpful(
        @PathVariable reviewId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val helpful = productReviewService.toggleHelpful(currentUser.userId, reviewId)
        return ResponseEntity.ok(mapOf("success" to true, "helpful" to helpful))
    }

    // Real Coupang-style pre-purchase product Q&A (2026-07-26) -- see
    // ProductInquiryService's own doc comment for the full account, including why this
    // needs no real order/purchase at all, unlike the review endpoints above.
    @PostMapping("/products/{productId}/inquiries")
    fun askProductInquiry(
        @PathVariable productId: String,
        @RequestBody request: AskProductInquiryRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val inquiry = productInquiryService.askQuestion(currentUser.userId, productId, request.question)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "inquiry" to inquiry))
    }

    @GetMapping("/products/{productId}/inquiries")
    fun getProductInquiries(
        @PathVariable productId: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = productInquiryService.getProductInquiries(productId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "inquiries" to page.content) + pageMeta(page))
    }

    @GetMapping("/inquiries/my-questions")
    fun getMyInquiries(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = productInquiryService.getMyInquiries(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "inquiries" to page.content) + pageMeta(page))
    }

    @PostMapping("/inquiries/{inquiryId}/answer")
    fun answerProductInquiry(
        @PathVariable inquiryId: String,
        @RequestBody request: AnswerProductInquiryRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val inquiry = productInquiryService.answerQuestion(currentUser.userId, inquiryId, request.answer)
        return ResponseEntity.ok(mapOf("success" to true, "inquiry" to inquiry))
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

    @ExceptionHandler(ProductReviewNotFoundException::class)
    fun handleProductReviewNotFound(ex: ProductReviewNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("REVIEW_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidProductReviewReplyException::class)
    fun handleInvalidProductReviewReply(ex: InvalidProductReviewReplyException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REVIEW_REPLY", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidProductInquiryException::class)
    fun handleInvalidProductInquiry(ex: InvalidProductInquiryException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_INQUIRY", ex.message ?: "Bad request"))

    @ExceptionHandler(ProductInquiryNotFoundException::class)
    fun handleProductInquiryNotFound(ex: ProductInquiryNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("INQUIRY_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidProductInquiryAnswerException::class)
    fun handleInvalidProductInquiryAnswer(ex: InvalidProductInquiryAnswerException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_ANSWER", ex.message ?: "Bad request"))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantNoAccountException::class)
    fun handleMerchantNoAccount(ex: MerchantNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(BuyerNoAccountException::class)
    fun handleBuyerNoAccount(ex: BuyerNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmptyOrderException::class)
    fun handleEmptyOrder(ex: EmptyOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_ORDER", ex.message ?: "Bad request"))

    @ExceptionHandler(MerchantNotAcceptingOrdersException::class)
    fun handleMerchantNotAcceptingOrders(ex: MerchantNotAcceptingOrdersException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("MERCHANT_NOT_ACCEPTING_ORDERS", ex.message ?: "Bad request"))

    @ExceptionHandler(MinOrderAmountNotMetException::class)
    fun handleMinOrderAmountNotMet(ex: MinOrderAmountNotMetException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MIN_ORDER_AMOUNT_NOT_MET", ex.message ?: "Unprocessable"))

    @ExceptionHandler(InvalidDeliveryAddressException::class)
    fun handleInvalidAddress(ex: InvalidDeliveryAddressException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DELIVERY_ADDRESS", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidQuantityException::class)
    fun handleInvalidQuantity(ex: InvalidQuantityException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_QUANTITY", ex.message ?: "Bad request"))

    @ExceptionHandler(InsufficientProductStockException::class)
    fun handleInsufficientStock(ex: InsufficientProductStockException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INSUFFICIENT_PRODUCT_STOCK", ex.message ?: "Product is out of stock"))

    @ExceptionHandler(ProductSoldOutException::class)
    fun handleProductSoldOut(ex: ProductSoldOutException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PRODUCT_SOLD_OUT", ex.message ?: "Product is sold out"))

    @ExceptionHandler(SurplusDealExpiredException::class)
    fun handleSurplusDealExpired(ex: SurplusDealExpiredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SURPLUS_DEAL_EXPIRED", ex.message ?: "This closing deal has expired"))

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

    @ExceptionHandler(RiderNotRegisteredException::class)
    fun handleRiderNotRegistered(ex: RiderNotRegisteredException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RIDER_NOT_REGISTERED", ex.message ?: "Not found"))

    @ExceptionHandler(RiderNotAvailableException::class)
    fun handleRiderNotAvailable(ex: RiderNotAvailableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDER_NOT_AVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(RiderAlreadyOnDeliveryException::class)
    fun handleRiderAlreadyOnDelivery(ex: RiderAlreadyOnDeliveryException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDER_ALREADY_ON_DELIVERY", ex.message ?: "Conflict"))

    @ExceptionHandler(DeliveryAlreadyClaimedException::class)
    fun handleDeliveryAlreadyClaimed(ex: DeliveryAlreadyClaimedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("DELIVERY_ALREADY_CLAIMED", ex.message ?: "Conflict"))

    @ExceptionHandler(ReturnOrderNotFoundException::class)
    fun handleReturnOrderNotFound(ex: ReturnOrderNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ORDER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ReturnOrderNotDeliveredException::class)
    fun handleReturnOrderNotDelivered(ex: ReturnOrderNotDeliveredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ORDER_NOT_DELIVERED", ex.message ?: "Conflict"))

    @ExceptionHandler(ReturnWindowExpiredException::class)
    fun handleReturnWindowExpired(ex: ReturnWindowExpiredException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("RETURN_WINDOW_EXPIRED", ex.message ?: "Return window expired"))

    @ExceptionHandler(ReturnAlreadyRequestedException::class)
    fun handleReturnAlreadyRequested(ex: ReturnAlreadyRequestedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RETURN_ALREADY_REQUESTED", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidReturnReasonException::class)
    fun handleInvalidReturnReason(ex: InvalidReturnReasonException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_RETURN_REASON", ex.message ?: "Bad request"))

    @ExceptionHandler(ReturnRequestNotFoundException::class)
    fun handleReturnRequestNotFound(ex: ReturnRequestNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RETURN_REQUEST_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ReturnRequestAlreadyDecidedException::class)
    fun handleReturnRequestAlreadyDecided(ex: ReturnRequestAlreadyDecidedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RETURN_REQUEST_ALREADY_DECIDED", ex.message ?: "Conflict"))

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

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))
}
