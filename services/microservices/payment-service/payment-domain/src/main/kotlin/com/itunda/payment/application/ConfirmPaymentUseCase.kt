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
 * Durable payment repository contract. claimForConfirmation must atomically move
 * exactly one eligible payment into CONFIRMING using a database-level conditional
 * update; process-local locks are not sufficient when the service has replicas.
 */
interface PaymentRepositoryPort {
    fun findByOrderId(orderId: String): Payment?
    fun claimForConfirmation(orderId: String): Payment?
    fun save(payment: Payment): Payment
}

/**
 * The adapter must send this stable key to a provider that actually honors
 * idempotency. The key alone does not create provider-side deduplication.
 */
interface PaymentGatewayPort {
    fun processMobileMoneyTransfer(
        paymentKey: String,
        amount: Long,
        idempotencyKey: String
    ): Boolean
}
