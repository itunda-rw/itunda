package com.itunda.payment.domain

import java.time.OffsetDateTime

/**
 * Payment Aggregate Root.
 * Mapped 100% 1:1 to Toss Payments' official API spec (https://docs.tosspayments.com/reference),
 * but adapted for Rwanda's context (MTN MoMo, Airtel Money, RWF).
 */
data class Payment(
    val mId: String,
    val paymentKey: String,
    val orderId: String,
    val orderName: String,
    val status: PaymentStatus,
    val requestedAt: OffsetDateTime,
    val approvedAt: OffsetDateTime?,
    val totalAmount: Long,
    val balanceAmount: Long,
    val currency: String = "RWF",
    
    // Adapted from Toss 'easyPay' or 'card' to Rwanda's Mobile Money reality
    val mobileMoney: MobileMoneyDetails?,
    
    val country: String = "RW",
    val method: String // e.g., "MOMO", "AIRTEL", "CARD"
)

enum class PaymentStatus {
    READY,
    IN_PROGRESS,
    WAITING_FOR_DEPOSIT,
    DONE,
    CANCELED,
    PARTIAL_CANCELED,
    ABORTED,
    EXPIRED
}

data class MobileMoneyDetails(
    val provider: String, // "MTN" or "AIRTEL"
    val phoneNumber: String, // Masked, e.g. "078****123"
    val receiptUrl: String
)
