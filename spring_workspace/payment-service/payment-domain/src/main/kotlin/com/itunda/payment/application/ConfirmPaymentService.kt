package com.itunda.payment.application

import com.itunda.payment.domain.Payment
import com.itunda.payment.domain.PaymentStatus
import java.time.OffsetDateTime

class ConfirmPaymentService(
    private val paymentRepository: PaymentRepositoryPort,
    private val paymentGateway: PaymentGatewayPort
) : ConfirmPaymentUseCase {

    override fun confirm(command: ConfirmPaymentCommand): Payment {
        val payment = paymentRepository.findByOrderId(command.orderId)
            ?: throw IllegalArgumentException("Payment not found for orderId: ${command.orderId}")

        // Toss Payments explicitly requires validation of amount to prevent tampering
        if (payment.totalAmount != command.amount) {
            throw IllegalArgumentException("Amount mismatch. Expected: ${payment.totalAmount}, but got: ${command.amount}")
        }

        if (payment.status == PaymentStatus.DONE) {
            // Already processed (Idempotency handling)
            return payment
        }

        if (payment.status != PaymentStatus.IN_PROGRESS && payment.status != PaymentStatus.READY) {
            throw IllegalStateException("Payment is in invalid state for confirmation: ${payment.status}")
        }

        // Connect to external Rwandan gateways (MTN MoMo API, Airtel Money API)
        val success = paymentGateway.processMobileMoneyTransfer(command.paymentKey, command.amount)
        
        val confirmedPayment = if (success) {
            payment.copy(
                status = PaymentStatus.DONE,
                approvedAt = OffsetDateTime.now()
            )
        } else {
            payment.copy(
                status = PaymentStatus.ABORTED
            )
        }

        return paymentRepository.save(confirmedPayment)
    }
}
