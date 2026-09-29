package com.itunda.payment.infrastructure

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

@Component
@ConditionalOnProperty(
    name = ["itunda.payment.kafka.transfer-confirmation.enabled"],
    havingValue = "true",
)
class PaymentKafkaConsumer {
    private val log = LoggerFactory.getLogger(PaymentKafkaConsumer::class.java)

    @KafkaListener(topics = [TOPIC_TRANSFER_CONFIRMED], groupId = "payment-service-group")
    fun consumeLedgerEvent(message: String) {
        // transfer.confirmed identifies a wallet transfer, not a payment intent.
        // It has no provider payment key, order ID, or authorization proof, so it
        // must never be converted into a provider confirmation. A future consumer
        // needs a separately versioned payment-authorized event and a durable
        // processed-event store before this feature may be enabled.
        log.warn(
            "Ignoring transfer.confirmed event because no payment-authorization event contract is configured; payloadSize={}",
            message.length,
        )
    }
}
