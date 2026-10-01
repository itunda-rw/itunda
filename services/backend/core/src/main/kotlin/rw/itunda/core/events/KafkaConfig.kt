package rw.itunda.core.events

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

/**
 * Kafka is optional at application startup. Google Managed Kafka uses TLS + IAM OAuth.
 */
@Configuration
@ConditionalOnProperty(prefix = "itunda.kafka", name = ["enabled"], havingValue = "true")
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}") private val bootstrapServers: String,
) {
    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val configProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            "security.protocol" to "SASL_SSL",
            "sasl.mechanism" to "OAUTHBEARER",
            "sasl.login.callback.handler.class" to
                "com.google.cloud.hosted.kafka.auth.GcpLoginCallbackHandler",
            "sasl.jaas.config" to
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;",
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to 10000,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to 15000,
        )
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> = KafkaTemplate(producerFactory())
}
