package com.itunda.payment.api.controller

import com.itunda.payment.application.ConfirmPaymentCommand
import com.itunda.payment.application.ConfirmPaymentUseCase
import com.itunda.payment.domain.Payment
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/payments")
class PaymentController(
    private val confirmPaymentUseCase: ConfirmPaymentUseCase
) {

    data class PaymentConfirmRequest(
        val paymentKey: String,
        val orderId: String,
        val amount: Long
    )

    @PostMapping("/confirm")
    fun confirmPayment(@RequestBody request: PaymentConfirmRequest): ResponseEntity<Payment> {
        return try {
            val command = ConfirmPaymentCommand(
                paymentKey = request.paymentKey,
                orderId = request.orderId,
                amount = request.amount
            )
            val confirmedPayment = confirmPaymentUseCase.confirm(command)
            ResponseEntity.ok(confirmedPayment)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().build()
        }
    }
}
