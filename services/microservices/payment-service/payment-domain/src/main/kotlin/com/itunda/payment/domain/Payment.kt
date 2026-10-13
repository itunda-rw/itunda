package com.itunda.payment.domain

import java.time.OffsetDateTime

/**
 * Payment Aggregate Root.
 * Adapted for Rwanda's context (MTN MoMo, Airtel Money, RWF).
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
    val mobileMoney: MobileMoneyDetails?,
    val country: String = "RW",
    val method: String
)

enum class PaymentStatus {
    READY,
    IN_PROGRESS,
    /** Durable claim acquired before an external provider operation begins. */
    CONFIRMING,
    WAITING_FOR_DEPOSIT,
    DONE,
    CANCELED,
    PARTIAL_CANCELED,
    ABORTED,
    EXPIRED
}

data class MobileMoneyDetails(
    val provider: String,
    val phoneNumber: String,
    val receiptUrl: String
)
