package rw.itunda.account

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.CurrencyConversion
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep): unlike every
 * other endpoint in this sweep, `convert` had NO "already done" guard at all before
 * the fix -- it genuinely moves real money between the caller's own accounts, so a
 * lost-response retry with no Idempotency-Key protection wouldn't just show a
 * confusing error, it would silently execute the SAME conversion twice. This file
 * exists to make sure that wiring can't silently regress: `convert` must route
 * through `idempotencyService.replayOrExecute` rather than calling
 * ForeignCurrencyAccountService directly, and a replayed key must never re-invoke it.
 */
class ForeignCurrencyControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time currency conversion request") {
        val service = mockk<ForeignCurrencyAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = ForeignCurrencyController(service, idempotencyService)

        val conversion = CurrencyConversion(
            id = "conv_1",
            userId = "user_1",
            fromCurrency = "RWF",
            toCurrency = "USD",
            fromAmount = BigDecimal("130000"),
            toAmount = BigDecimal("100"),
            rate = BigDecimal("1300"),
            marginAmount = BigDecimal("2"),
            transactionId = "txn_1",
        )
        every { service.convert("user_1", "RWF", "USD", BigDecimal("130000")) } returns conversion

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute(
                "POST /api/v1/account/foreign-currency/convert",
                "key-1",
                any(),
                capture(actionSlot),
            )
        } answers { actionSlot.captured.invoke() }

        When("converting") {
            val response = controller.convert(
                ConvertCurrencyRequest("RWF", "USD", BigDecimal("130000")),
                "key-1",
                currentUser,
            )

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/account/foreign-currency/convert", "key-1", any(), any())
                }
                verify(exactly = 1) { service.convert("user_1", "RWF", "USD", BigDecimal("130000")) }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("conversion") shouldBe conversion
            }
        }
    }

    Given("a retried conversion request using the same Idempotency-Key as a completed one") {
        val service = mockk<ForeignCurrencyAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = ForeignCurrencyController(service, idempotencyService)

        // The real IdempotencyService would detect this as a replay and never invoke
        // the passed-in action lambda at all -- simulated here directly, since what
        // this controller test needs to prove is that money movement never happens
        // outside that lambda.
        every {
            idempotencyService.replayOrExecute("POST /api/v1/account/foreign-currency/convert", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "conversion" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.convert(
                ConvertCurrencyRequest("RWF", "USD", BigDecimal("130000")),
                "key-1",
                currentUser,
            )

            Then("the cached response is returned and the conversion never runs again") {
                response.body?.get("conversion") shouldBe "cached-result"
                verify(exactly = 0) { service.convert(any(), any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
