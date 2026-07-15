package com.itunda.payment.infrastructure

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin

const val TOPIC_TRANSFER_CONFIRMED = "transfer.confirmed"
const val TOPIC_PAYMENT_EVENTS = "payment-events"

@Configuration
class PaymentTopicsConfig(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}") private val bootstrapServers: String,
) {
    @Bean
    fun kafkaAdmin(): KafkaAdmin = KafkaAdmin(
        mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)
    )

    @Bean
    fun transferConfirmedTopic(): NewTopic = TopicBuilder.name(TOPIC_TRANSFER_CONFIRMED)
        .partitions(3)
        .replicas(1)
        .build()

    @Bean
    fun paymentEventsTopic(): NewTopic = TopicBuilder.name(TOPIC_PAYMENT_EVENTS)
        .partitions(3)
        .replicas(1)
        .build()
}
