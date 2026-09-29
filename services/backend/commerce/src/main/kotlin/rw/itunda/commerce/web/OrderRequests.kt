package rw.itunda.commerce.web

import rw.itunda.core.domain.OrderReturnType
import rw.itunda.core.domain.OrderStatus
import rw.itunda.commerce.OrderItemRequest

// Real fix (2026-08-26): split out of OrderController.kt once that file grew past its
// file-size-lint baseline. Pure request DTOs, no behavior -- zero-risk mechanical
// move, same package so no import changes anywhere.

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
