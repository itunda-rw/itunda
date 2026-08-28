package rw.itunda.merchant

import java.math.BigDecimal

// Real fix (2026-08-26): split out of MerchantController.kt once that file grew past
// its file-size-lint baseline. Pure request DTOs, no behavior -- zero-risk mechanical
// move, same package so no import changes anywhere.

data class RegisterMerchantRequest(val businessName: String)
data class GenerateQrRequest(val amount: BigDecimal, val description: String)
data class SetWebhookUrlRequest(val webhookUrl: String)
data class SetLocationRequest(val latitude: Double, val longitude: Double)
data class SetCategoryRequest(val category: String)
data class SetCashbackRateRequest(val rate: BigDecimal?)
data class SetParticipatesInEatsMembershipRequest(val participates: Boolean)
data class SetAcceptsScheduledOrdersRequest(val accepts: Boolean)
data class SetAcceptingOrdersRequest(val accepting: Boolean)
data class SetClosedWeekdaysRequest(val weekdays: Set<Int>)
data class SetPhotoUrlRequest(val photoUrl: String)
data class SetPhotoUrlsRequest(val photoUrls: List<String>)
data class SetMinOrderAmountRequest(val minOrderAmount: BigDecimal?)
data class SetPhoneNumberRequest(val phoneNumber: String?)
data class SetOpeningHoursRequest(val openingHours: String?)
data class SetAvgPrepTimeMinutesRequest(val avgPrepTimeMinutes: Int?)
data class SetPickupDiscountRequest(val pickupDiscountPercent: Int?)
data class CollectPaymentRequest(val couponId: String? = null, val pointsToRedeem: BigDecimal? = null)
// Real customer-presented payment code (2026-08-11) -- see
// MerchantService.chargeByCustomerCode's own doc comment.
data class ChargeByCustomerCodeRequest(val code: String, val amount: BigDecimal)
data class GenerateCustomerPaymentCodeRequest(val accountId: String? = null)
// Real Kakao Pay 정액 QR (static/fixed merchant QR) -- see MerchantStaticQrService's own
// doc comment.
data class StaticQrPayRequest(val amount: BigDecimal, val description: String? = null)
data class ChargeCardRequest(
    val amount: BigDecimal,
    val description: String,
    val cardNumber: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val cvc: String,
)
