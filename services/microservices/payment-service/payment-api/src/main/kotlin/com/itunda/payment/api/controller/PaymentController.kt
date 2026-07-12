package com.itunda.payment.api.controller

import com.itunda.payment.application.ConfirmPaymentCommand
import com.itunda.payment.application.ConfirmPaymentUseCase
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.*

// Merged (2026-07-11) with a second, duplicate PaymentController that lived directly in
// PaymentServiceApplication.kt -- both were @RestController-annotated with the same bean
// name, which this session's first-ever live boot of payment-service surfaced as a real
// ConflictingBeanDefinitionException, not a hypothetical risk. This version keeps this
// file's proper module layering and explicit error handling, and ports over the other's
// two genuinely distinct features it lacked: the Idempotency-Key header and the Toss-
// aligned (docs.tosspayments.com-shaped) response body/Kafka publish, rather than
// dropping either half's real work.
@RestController
// "/api" prefix required to match services/api-gateway's /api/v1/payments proxy target --
// http-proxy-middleware forwards the full incoming path unmodified (no pathRewrite), and
// ledger-service's own @RequestMapping("/api/v1/ledger") already follows this convention.
// Found via this session's first-ever live gateway-proxy test: this controller's original
// "/v1/payments" mapping (missing "/api") made every client-facing call 404 through the
// gateway despite the service itself working fine when hit directly on its own port.
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val confirmPaymentUseCase: ConfirmPaymentUseCase,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    data class PaymentConfirmRequest(
        val paymentKey: String,
        val orderId: String,
        val amount: Long
    )

    @PostMapping("/confirm")
    fun confirmPayment(
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: PaymentConfirmRequest
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val command = ConfirmPaymentCommand(
                paymentKey = request.paymentKey,
                orderId = request.orderId,
                amount = request.amount,
                idempotencyKey = idempotencyKey
            )
            val payment = confirmPaymentUseCase.confirm(command)

            val eventPayload = """{"paymentKey": "${request.paymentKey}", "status": "${payment.status}"}"""
            kafkaTemplate.send("payment-events", eventPayload)

            ResponseEntity.ok(
                mapOf(
                    "mId" to payment.mId,
                    "paymentKey" to payment.paymentKey,
                    "orderId" to payment.orderId,
                    "status" to payment.status.name,
                    "totalAmount" to payment.totalAmount
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().build()
        }
    }
}
