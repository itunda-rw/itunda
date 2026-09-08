package rw.itunda.merchant

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.longs.shouldBeLessThan
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.WebhookDelivery
import rw.itunda.core.domain.WebhookDeliveryStatus
import rw.itunda.core.repository.MerchantRepository
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
        val service = WebhookDeliveryService(ObjectMapper(), mockk(), mockk())

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
        val merchantRepository = mockk<MerchantRepository>()
        val saved = slot<WebhookDelivery>()
        every { repository.save(capture(saved)) } answers { firstArg() }
        val service = WebhookDeliveryService(ObjectMapper(), repository, merchantRepository)

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
        val merchantRepository = mockk<MerchantRepository>()
        val delivered = exhaustedDelivery().also {
            it.status = WebhookDeliveryStatus.DELIVERED
            it.deliveredAt = Instant.parse("2026-07-01T00:01:00Z")
        }
        every { repository.findTop100ByMerchantIdOrderByCreatedAtDesc("merchant_1") } returns listOf(delivered)
        val service = WebhookDeliveryService(ObjectMapper(), repository, merchantRepository)

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
        val merchantRepository = mockk<MerchantRepository>()
        val service = WebhookDeliveryService(ObjectMapper(), repository, merchantRepository)
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

    // Real ops visibility (Merchant product-completeness pass) -- see
    // WebhookDeliveryService.getExhaustedQueue/replayExhaustedAsAdmin's own doc
    // comments.
    Given("EXHAUSTED deliveries across multiple merchants") {
        val repository = mockk<WebhookDeliveryRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = WebhookDeliveryService(ObjectMapper(), repository, merchantRepository)
        val pageable = org.springframework.data.domain.PageRequest.of(0, 50)
        val deliveries = listOf(exhaustedDelivery("merchant_1"), exhaustedDelivery("merchant_2"))
        every { repository.findByStatusOrderByCreatedAtDesc(WebhookDeliveryStatus.EXHAUSTED, pageable) } returns
            org.springframework.data.domain.PageImpl(deliveries)

        When("an admin lists the exhausted queue") {
            val page = service.getExhaustedQueue(pageable)

            Then("it real-spans every merchant, not just one") {
                page.content.map { it["merchantId"] } shouldBe listOf("merchant_1", "merchant_2")
            }
        }
    }

    Given("an exhausted delivery an admin replays") {
        val repository = mockk<WebhookDeliveryRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = WebhookDeliveryService(ObjectMapper(), repository, merchantRepository)
        val original = exhaustedDelivery("merchant_1")
        val merchant = Merchant(
            id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "Kigali Diner",
            status = MerchantStatus.ACTIVE, webhookUrl = "https://current.example.test/webhook",
        )
        every { repository.findById("whd_original") } returns Optional.of(original)
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
        val saved = slot<WebhookDelivery>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        When("the admin replays it") {
            val result = service.replayExhaustedAsAdmin("whd_original")

            Then("it real-looks-up the merchant's CURRENT webhookUrl server-side, not a client-supplied one") {
                result["replayOf"] shouldBe "whd_original"
                saved.captured.webhookUrl shouldBe "https://current.example.test/webhook"
            }
        }
    }

    // Real off-by-one found during the Merchant developer-center audit: MAX_ATTEMPTS
    // used to be 7, one short of the 8 attempts (1 initial + 7 retries) Toss's own
    // documented scheme actually needs to use every configured interval, including the
    // final 4096-minute one -- silently shrinking the real retry window from ~3.8 days
    // to under 24 hours.
    Given("a delivery that has already failed 6 times in a row") {
        val repository = mockk<WebhookDeliveryRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        every { repository.save(any()) } answers { firstArg() }
        val service = WebhookDeliveryService(ObjectMapper(), repository, merchantRepository)
        // A loopback URL always fails WebhookUrlPolicy's public-address check --
        // deterministic, instant, no real network call, no MockWebServer needed.
        val delivery = WebhookDelivery(
            id = "whd_flaky", merchantId = "merchant_1", eventType = "PAYMENT_STATUS_CHANGED",
            webhookUrl = "https://127.0.0.1/webhook", payload = "{}",
            attemptCount = 6, status = WebhookDeliveryStatus.PENDING, nextAttemptAt = Instant.now(),
        )

        When("its 7th attempt also fails") {
            service.retry(delivery)

            Then("it schedules the real final 4096-minute interval instead of exhausting early") {
                delivery.attemptCount shouldBe 7
                delivery.status shouldBe WebhookDeliveryStatus.PENDING
                val expected = Instant.now().plusSeconds(4096L * 60)
                Math.abs(delivery.nextAttemptAt.epochSecond - expected.epochSecond) shouldBeLessThan 5L
            }
        }
    }

    Given("a delivery that has already failed 7 times in a row") {
        val repository = mockk<WebhookDeliveryRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        every { repository.save(any()) } answers { firstArg() }
        val service = WebhookDeliveryService(ObjectMapper(), repository, merchantRepository)
        val delivery = WebhookDelivery(
            id = "whd_flaky", merchantId = "merchant_1", eventType = "PAYMENT_STATUS_CHANGED",
            webhookUrl = "https://127.0.0.1/webhook", payload = "{}",
            attemptCount = 7, status = WebhookDeliveryStatus.PENDING, nextAttemptAt = Instant.now(),
        )

        When("its 8th and final attempt also fails") {
            service.retry(delivery)

            Then("it exhausts, having used the full documented retry window") {
                delivery.attemptCount shouldBe 8
                delivery.status shouldBe WebhookDeliveryStatus.EXHAUSTED
            }
        }
    }

    Given("an exhausted delivery for a merchant who has since removed their webhook URL") {
        val repository = mockk<WebhookDeliveryRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = WebhookDeliveryService(ObjectMapper(), repository, merchantRepository)
        val original = exhaustedDelivery("merchant_1")
        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "Kigali Diner", status = MerchantStatus.ACTIVE)
        every { repository.findById("whd_original") } returns Optional.of(original)
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)

        When("an admin tries to replay it") {
            Then("it real-blocks with WebhookUrlNotConfiguredException rather than replaying to nowhere") {
                io.kotest.assertions.throwables.shouldThrow<WebhookUrlNotConfiguredException> {
                    service.replayExhaustedAsAdmin("whd_original")
                }
            }
        }
    }
})
