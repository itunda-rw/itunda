package rw.itunda.merchant

import rw.itunda.core.domain.PaymentIntentStatus
import java.math.BigDecimal
import java.time.LocalDate

// Real fix (2026-08-26): split out of MerchantService.kt once that file grew past its
// file-size-lint baseline. Pure data classes, no behavior -- zero-risk mechanical
// move, same package so no import changes anywhere.

// Real external-checkout DTOs (2026-07-21) -- see PaymentsApiController's own doc
// comment for the full account of the real Toss Payments feature this mirrors.
data class CheckoutInfo(
    val paymentKey: String,
    val merchantName: String,
    val amount: BigDecimal,
    val description: String,
    val status: PaymentIntentStatus,
    val successUrl: String?,
    val failUrl: String?,
)

data class MerchantReportDay(
    val date: LocalDate,
    val collectionCount: Int,
    val grossAmount: BigDecimal,
    val fees: BigDecimal,
    val netAmount: BigDecimal,
    val byChannel: Map<String, Int>,
)

// Real Coupang WING-style 베스트 상품 (best-selling products) report -- see
// MerchantService.getTopSellingProducts's own doc comment.
data class TopSellingProduct(
    val productId: String,
    val productName: String,
    val unitsSold: Int,
    val revenue: BigDecimal,
)
