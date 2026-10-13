package com.itunda.payment.application

import com.itunda.payment.domain.Payment

/**
 * Command for confirming a payment, shaped after the Toss Payments confirm API.
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
 *
 * Current payment-db implementation is in-memory. Production use requires durable,
 * atomic state transitions and idempotency persistence.
 */
interface PaymentRepositoryPort {
    fun findByOrderId(orderId: String): Payment?
    fun save(payment: Payment): Payment
}

/**
 * Provider adapter must pass this key through to the provider's idempotency mechanism.
 * A stable key alone does not guarantee deduplication unless the provider honors it.
 */
interface PaymentGatewayPort {
    fun processMobileMoneyTransfer(
        paymentKey: String,
        amount: Long,
        idempotencyKey: String
    ): Boolean
}
