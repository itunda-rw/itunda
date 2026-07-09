package com.itunda.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.*
import java.util.UUID

@SpringBootApplication
@org.springframework.context.annotation.ComponentScan(basePackages = ["com.itunda", "rw.itunda"])
class PaymentServiceApplication

fun main(args: Array<String>) {
    runApplication<PaymentServiceApplication>(*args)
}

@org.springframework.context.annotation.Configuration
class PaymentDomainConfig {
    @org.springframework.context.annotation.Bean
    fun confirmPaymentUseCase(
        paymentRepositoryPort: com.itunda.payment.application.PaymentRepositoryPort,
        paymentGatewayPort: com.itunda.payment.application.PaymentGatewayPort
    ): com.itunda.payment.application.ConfirmPaymentUseCase {
        return com.itunda.payment.application.ConfirmPaymentService(paymentRepositoryPort, paymentGatewayPort)
    }
}

data class PaymentConfirmRequest(
    val paymentKey: String,
    val orderId: String,
    val amount: Long
)

@RestController
@RequestMapping("/v1/payments")
class PaymentController(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val confirmPaymentUseCase: com.itunda.payment.application.ConfirmPaymentUseCase
) {

    // Toss-aligned endpoint mimicking docs.tosspayments.com API
    @PostMapping("/confirm")
    fun confirmPayment(
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: PaymentConfirmRequest
    ): ResponseEntity<Map<String, Any>> {
        
        val command = com.itunda.payment.application.ConfirmPaymentCommand(
            paymentKey = request.paymentKey,
            orderId = request.orderId,
            amount = request.amount,
            idempotencyKey = idempotencyKey
        )
        
        // Use domain logic which integrates with RNP Gateway
        val payment = confirmPaymentUseCase.confirm(command)
        
        // Publish event
        val eventPayload = """{"paymentKey": "${request.paymentKey}", "status": "${payment.status}"}"""
        kafkaTemplate.send("payment-events", eventPayload)

        return ResponseEntity.ok(
            mapOf(
                "mId" to payment.mId,
                "paymentKey" to payment.paymentKey,
                "orderId" to payment.orderId,
                "status" to payment.status.name,
                "totalAmount" to payment.totalAmount
            )
        )
    }
}
