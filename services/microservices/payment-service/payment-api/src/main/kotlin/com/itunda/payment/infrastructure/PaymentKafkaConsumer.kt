package com.itunda.payment.infrastructure

import com.itunda.payment.application.ConfirmPaymentCommand
import com.itunda.payment.application.ConfirmPaymentUseCase
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentKafkaConsumer(
    private val confirmPaymentUseCase: ConfirmPaymentUseCase
) {

    @KafkaListener(topics = [TOPIC_TRANSFER_CONFIRMED], groupId = "payment-service-group")
    fun consumeLedgerEvent(message: String) {
        println("[Kafka Consumer] Received ledger transfer event: \$message")
        
        // In a real scenario, deserialize JSON to extract orderId, amount, paymentKey
        // For demonstration, simulating parsing
        // val event = objectMapper.readValue(message, TransferEvent::class.java)
        
        // Trigger Toss-style Confirm API
        try {
            // Hardcoded values for simulation based on the prompt's request
            val command = ConfirmPaymentCommand(
                paymentKey = "pay_key_simulated",
                orderId = "order_simulated",
                amount = 1000L, 
                idempotencyKey = "idemp_key_simulated"
            )
            confirmPaymentUseCase.confirm(command)
            println("[Kafka Consumer] Payment confirmed successfully.")
        } catch (e: Exception) {
            println("[Kafka Consumer] Failed to confirm payment: \${e.message}")
            // Typically send to Dead Letter Queue (DLQ)
        }
    }
}
