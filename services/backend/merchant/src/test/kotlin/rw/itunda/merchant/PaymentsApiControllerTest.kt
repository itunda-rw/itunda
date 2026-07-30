package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.PaymentIntent
import rw.itunda.core.domain.PaymentIntentStatus
import rw.itunda.core.idempotency.IdempotencyService
import java.math.BigDecimal
import java.time.Instant

class PaymentsApiControllerTest : BehaviorSpec({
    Given("an external payment creation request") {
        val merchantService = mockk<MerchantService>()
        val idempotencyService = mockk<IdempotencyService>()
        val rateLimiter = mockk<RateLimiter>()
        val controller = PaymentsApiController(merchantService, idempotencyService, rateLimiter, "https://pay.itunda.rw")
        val merchant = mockk<Merchant>()
        val intent = mockk<PaymentIntent>()
        val request = CreatePaymentRequest(BigDecimal("1200"), "Market order", "order_1")

        every { merchant.id } returns "merchant_1"
        every { rateLimiter.checkLimit(any(), any(), any()) } returns Unit
        every { merchantService.resolveMerchantByApiKey("merchant-api-key") } returns merchant
        every { intent.id } returns "pi_1"
        every { intent.status } returns PaymentIntentStatus.PENDING
        every { intent.expiresAt } returns Instant.parse("2026-07-30T00:15:00Z")
        every {
            merchantService.createExternalPayment(merchant, request.amount, request.description, request.orderId, request.successUrl, request.failUrl)
        } returns intent

        When("it is submitted with an idempotency key") {
            val endpoint = slot<String>()
            every {
                idempotencyService.replayOrExecute(capture(endpoint), "create_1", request, any())
            } answers {
                @Suppress("UNCHECKED_CAST")
                (invocation.args[3] as () -> Pair<Int, Map<String, Any?>>).invoke()
            }

            val response = controller.createPayment("merchant-api-key", "create_1", request)

            Then("it creates at most one intent within that merchant's scope") {
                response.statusCode.value() shouldBe 201
                response.body?.get("paymentKey") shouldBe "pi_1"
                endpoint.captured shouldBe "POST /api/v1/pay/merchants/merchant_1/payments"
                verify(exactly = 1) { rateLimiter.checkLimit("merchant-api:merchant_1:payment-create", limit = 30, window = java.time.Duration.ofMinutes(1)) }
                verify(exactly = 1) { merchantService.createExternalPayment(merchant, request.amount, request.description, request.orderId, request.successUrl, request.failUrl) }
            }
        }
    }
})
