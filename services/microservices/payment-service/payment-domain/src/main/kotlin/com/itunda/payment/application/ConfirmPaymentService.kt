package com.itunda.payment.application

import com.itunda.payment.domain.Payment
import com.itunda.payment.domain.PaymentStatus
import java.time.OffsetDateTime

class ConfirmPaymentService(
    private val paymentRepository: PaymentRepositoryPort,
    private val paymentGateway: PaymentGatewayPort
) : ConfirmPaymentUseCase {

    override fun confirm(command: ConfirmPaymentCommand): Payment {
        val existing = paymentRepository.findByOrderId(command.orderId)
            ?: throw IllegalArgumentException("Payment not found for orderId: ${command.orderId}")

        if (existing.totalAmount != command.amount) {
            throw IllegalArgumentException(
                "Amount mismatch. Expected: ${existing.totalAmount}, but got: ${command.amount}"
            )
        }

        if (existing.status == PaymentStatus.DONE) return existing

        if (existing.status != PaymentStatus.IN_PROGRESS && existing.status != PaymentStatus.READY) {
            throw IllegalStateException(
                "Payment is in invalid state for confirmation: ${existing.status}"
            )
        }

        // The repository performs a single conditional database UPDATE. A competing
        // request/replica cannot also claim this order once status becomes CONFIRMING.
        val claimed = paymentRepository.claimForConfirmation(command.orderId)
            ?: run {
                val latest = paymentRepository.findByOrderId(command.orderId)
                if (latest?.status == PaymentStatus.DONE) return latest
                throw IllegalStateException(
                    "Payment confirmation is already in progress or its state changed; check status before retrying."
                )
            }

        // Always derive the provider key from the order, not a caller-supplied key,
        // so retries for this order cannot accidentally create a new provider operation.
        // The provider must still implement idempotency for this protection to work.
        val providerIdempotencyKey = "itunda-payment-confirm:${claimed.orderId}"
        val success = paymentGateway.processMobileMoneyTransfer(
            command.paymentKey,
            command.amount,
            providerIdempotencyKey
        )

        val confirmedPayment = if (success) {
            claimed.copy(status = PaymentStatus.DONE, approvedAt = OffsetDateTime.now())
        } else {
            claimed.copy(status = PaymentStatus.ABORTED)
        }

        return paymentRepository.save(confirmedPayment)
    }
}
