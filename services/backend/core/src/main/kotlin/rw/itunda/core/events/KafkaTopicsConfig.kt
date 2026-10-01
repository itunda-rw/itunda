package rw.itunda.core.events

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.config.TopicBuilder

/**
 * Explicit topic definitions for Google Managed Kafka. Only created when Kafka is enabled.
 */
@Configuration
@ConditionalOnProperty(prefix = "itunda.kafka", name = ["enabled"], havingValue = "true")
class KafkaTopicsConfig(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}") private val bootstrapServers: String,
) {
    @Bean
    fun kafkaAdmin(): KafkaAdmin = KafkaAdmin(
        mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)
    )

    @Bean
    fun ledgerPostedTopic(): NewTopic = TopicBuilder.name(TOPIC_LEDGER_POSTED)
        .partitions(3)
        .replicas(1)
        .build()

    @Bean
    fun transferConfirmedTopic(): NewTopic = TopicBuilder.name(TOPIC_TRANSFER_CONFIRMED)
        .partitions(3)
        .replicas(1)
        .build()

    @Bean
    fun paymentProviderSucceededTopic(): NewTopic = TopicBuilder.name(TOPIC_PAYMENT_PROVIDER_SUCCEEDED)
        .partitions(3)
        .replicas(1)
        .build()

    @Bean
    fun paymentProviderFailedTopic(): NewTopic = TopicBuilder.name(TOPIC_PAYMENT_PROVIDER_FAILED)
        .partitions(3)
        .replicas(1)
        .build()
}
