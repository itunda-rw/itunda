package com.itunda.payment.application

import com.itunda.payment.domain.Payment
import com.itunda.payment.domain.PaymentStatus
import java.time.OffsetDateTime

class ConfirmPaymentService(
    private val paymentRepository: PaymentRepositoryPort,
    private val paymentGateway: PaymentGatewayPort
) : ConfirmPaymentUseCase {

    /**
     * Process-local guard only. It reduces duplicate gateway calls when this singleton
     * handles concurrent requests, but is NOT a distributed lock and does not survive
     * restarts. Production confirmation remains blocked until the repository provides
     * an atomic durable claim/idempotency record and the provider supports idempotency.
     */
    @Synchronized
    override fun confirm(command: ConfirmPaymentCommand): Payment {
        val payment = paymentRepository.findByOrderId(command.orderId)
            ?: throw IllegalArgumentException("Payment not found for orderId: ${command.orderId}")

        if (payment.totalAmount != command.amount) {
            throw IllegalArgumentException("Amount mismatch. Expected: ${payment.totalAmount}, but got: ${command.amount}")
        }

        if (payment.status == PaymentStatus.DONE) return payment

        if (payment.status != PaymentStatus.IN_PROGRESS && payment.status != PaymentStatus.READY) {
            throw IllegalStateException("Payment is in invalid state for confirmation: ${payment.status}")
        }

        // A stable fallback lets a future provider adapter deduplicate retries for this
        // order even when a caller omits Idempotency-Key. It is not a substitute for a
        // durable request fingerprint/response store in this service.
        val providerIdempotencyKey = command.idempotencyKey
            ?.takeIf { it.isNotBlank() }
            ?: "itunda-payment-confirm:${command.orderId}"

        val success = paymentGateway.processMobileMoneyTransfer(
            command.paymentKey,
            command.amount,
            providerIdempotencyKey
        )

        val confirmedPayment = if (success) {
            payment.copy(status = PaymentStatus.DONE, approvedAt = OffsetDateTime.now())
        } else {
            payment.copy(status = PaymentStatus.ABORTED)
        }

        return paymentRepository.save(confirmedPayment)
    }
}
