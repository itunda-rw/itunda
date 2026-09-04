package rw.itunda.merchant

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.WebhookDelivery
import rw.itunda.core.domain.WebhookDeliveryStatus
import rw.itunda.core.repository.WebhookDeliveryRepository
import java.time.Instant
import java.net.URI
import java.util.Optional

/**
 * Recovery operations must preserve the original delivery record: an exhausted webhook
 * may be replayed only by its owning merchant, and the replay is a fresh delivery to
 * the merchant's currently configured endpoint. This makes support recovery auditable
 * instead of mutating the delivery history in place.
 */
class WebhookDeliveryServiceTest : BehaviorSpec({
    fun exhaustedDelivery(merchantId: String = "merchant_1") = WebhookDelivery(
        id = "whd_original",
        merchantId = merchantId,
        eventType = "PAYMENT_STATUS_CHANGED",
        webhookUrl = "https://old.example.test/webhook",
        payload = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"}",
        attemptCount = WebhookDeliveryService.MAX_ATTEMPTS,
        status = WebhookDeliveryStatus.EXHAUSTED,
        nextAttemptAt = Instant.parse("2026-07-01T00:00:00Z"),
    )

    Given("an outbound payment-status webhook") {
        val service = WebhookDeliveryService(ObjectMapper(), mockk())

        When("it is prepared for delivery") {
            val request = service.buildRequest(
                URI("https://merchant.example/webhooks/itunda"),
                "{}",
                "whd_delivery_1",
                "PAYMENT_STATUS_CHANGED",
            )

            Then("it carries stable deduplication and event-contract headers") {
                request.header("X-Itunda-Delivery-Id") shouldBe "whd_delivery_1"
                request.header("X-Itunda-Event-Type") shouldBe "PAYMENT_STATUS_CHANGED"
            }
        }
    }

    Given("a payment flow that emits a webhook") {
        val repository = mockk<WebhookDeliveryRepository>()
        val saved = slot<WebhookDelivery>()
        every { repository.save(capture(saved)) } answers { firstArg() }
        val service = WebhookDeliveryService(ObjectMapper(), repository)

        When("it completes the financial operation") {
            service.deliverPaymentStatusChanged("merchant_1", "https://merchant.example/hooks", mapOf("paymentKey" to "pay_1"))

            Then("it only queues the pending delivery for the post-commit scheduler") {
                verify(exactly = 1) {
                    repository.save(match {
                        it.status == WebhookDeliveryStatus.PENDING && it.attemptCount == 0 && it.nextAttemptAt <= Instant.now()
                    })
                }
                ObjectMapper().readTree(saved.captured.payload)["eventId"].asText() shouldBe saved.captured.id
            }
        }
    }

    Given("a successfully delivered webhook") {
        val repository = mockk<WebhookDeliveryRepository>()
        val delivered = exhaustedDelivery().also {
            it.status = WebhookDeliveryStatus.DELIVERED
            it.deliveredAt = Instant.parse("2026-07-01T00:01:00Z")
        }
        every { repository.findTop100ByMerchantIdOrderByCreatedAtDesc("merchant_1") } returns listOf(delivered)
        val service = WebhookDeliveryService(ObjectMapper(), repository)

        When("the merchant views delivery history") {
            val history = service.deliveryHistory("merchant_1")

            Then("it includes the successful delivery as an auditable event") {
                history.single()["id"] shouldBe "whd_original"
                history.single()["eventType"] shouldBe "PAYMENT_STATUS_CHANGED"
                history.single()["status"] shouldBe "DELIVERED"
                history.single()["deliveredAt"] shouldBe "2026-07-01T00:01:00Z"
            }
        }
    }

    Given("an exhausted merchant webhook delivery") {
        val repository = mockk<WebhookDeliveryRepository>()
        val service = WebhookDeliveryService(ObjectMapper(), repository)
        val original = exhaustedDelivery()

        When("its owning merchant replays it after correcting their endpoint") {
            val saved = slot<WebhookDelivery>()
            every { repository.findById("whd_original") } returns Optional.of(original)
            every { repository.save(capture(saved)) } answers { saved.captured }

            val result = service.replayExhausted(
                merchantId = "merchant_1",
                deliveryId = "whd_original",
                currentWebhookUrl = "https://current.example.test/webhook",
            )

            Then("it creates a separate pending delivery with the same event payload") {
                result!!["id"] shouldBe saved.captured.id
                result["status"] shouldBe "PENDING"
                result["replayOf"] shouldBe "whd_original"
                (saved.captured.id == original.id) shouldBe false
                saved.captured.merchantId shouldBe "merchant_1"
                saved.captured.eventType shouldBe "PAYMENT_STATUS_CHANGED"
                saved.captured.webhookUrl shouldBe "https://current.example.test/webhook"
                saved.captured.payload shouldBe original.payload
                saved.captured.status shouldBe WebhookDeliveryStatus.PENDING
                saved.captured.attemptCount shouldBe 0
            }
        }

        When("a different merchant tries to replay it") {
            clearMocks(repository)
            every { repository.findById("whd_original") } returns Optional.of(original)

            val result = service.replayExhausted(
                merchantId = "merchant_other",
                deliveryId = "whd_original",
                currentWebhookUrl = "https://attacker.example.test/webhook",
            )

            Then("it exposes no delivery and creates no replay") {
                result shouldBe null
                verify(exactly = 0) { repository.save(any()) }
            }
        }

        When("a merchant asks to replay a delivery that has not exhausted retries") {
            clearMocks(repository)
            val pending = exhaustedDelivery().also { it.status = WebhookDeliveryStatus.PENDING }
            every { repository.findById("whd_original") } returns Optional.of(pending)

            val result = service.replayExhausted(
                merchantId = "merchant_1",
                deliveryId = "whd_original",
                currentWebhookUrl = "https://current.example.test/webhook",
            )

            Then("it leaves the active retry schedule untouched") {
                result shouldBe null
                verify(exactly = 0) { repository.save(any()) }
            }
        }
    }
})
