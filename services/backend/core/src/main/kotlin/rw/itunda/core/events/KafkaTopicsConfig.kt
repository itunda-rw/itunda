package rw.itunda.core.events

import org.apache.kafka.clients.admin.AdminClientConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.config.TopicBuilder
import org.apache.kafka.clients.admin.NewTopic

/**
 * Makes the backend's real event topics explicit rather than relying on first-send
 * auto-creation side effects. Current private-cloud Kafka is still single-broker per
 * node, so replication-factor stays at 1 here; partition count stays at 3 because the
 * live rehearsal brokers already carry these topics at 3 partitions.
 */
@Configuration
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
