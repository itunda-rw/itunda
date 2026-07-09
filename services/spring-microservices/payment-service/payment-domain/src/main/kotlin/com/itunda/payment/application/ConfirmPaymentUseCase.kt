package com.itunda.payment.application

import com.itunda.payment.domain.Payment
import com.itunda.payment.domain.PaymentStatus
import java.time.OffsetDateTime

/**
 * Command for confirming a payment, exactly mirroring Toss Payments' Confirm API parameters:
 * https://docs.tosspayments.com/reference#%EA%B2%B0%EC%A0%9C-%EC%8A%B9%EC%9D%B8
 */
data class ConfirmPaymentCommand(
    val paymentKey: String,
    val orderId: String,
    val amount: Long,
    val idempotencyKey: String? = null
)

interface ConfirmPaymentUseCase {
    fun confirm(command: ConfirmPaymentCommand): Payment
}

/**
 * Port for retrieving/saving Payment aggregate.
 */
interface PaymentRepositoryPort {
    fun findByOrderId(orderId: String): Payment?
    fun save(payment: Payment): Payment
}

/**
 * Port for talking to Itunda Bank (Ledger) or Mobile Money Provider
 */
interface PaymentGatewayPort {
    fun processMobileMoneyTransfer(paymentKey: String, amount: Long): Boolean
}
