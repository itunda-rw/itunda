package rw.itunda.bills

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.provider.ProviderDeclinedException
import java.math.BigDecimal

@Suppress("UNCHECKED_CAST")
class BillsBatchActionHandlersTest : BehaviorSpec({

    fun idempotencyServiceThatJustRuns(): IdempotencyService {
        val idempotencyService = mockk<IdempotencyService>()
        every { idempotencyService.replayOrExecute(any(), any(), any(), any()) } answers {
            (arg<() -> Pair<Int, Map<String, Any?>>>(3)).invoke()
        }
        return idempotencyService
    }

    Given("a real bill payment that succeeds") {
        val billsService = mockk<BillsService>()
        val idempotencyService = idempotencyServiceThatJustRuns()
        val handler = PayBillBatchActionHandler(billsService, idempotencyService)

        every { billsService.payBill("user_1", "bill_2", BigDecimal("100"), "WASAC-1", "WASAC") } returns
            mapOf("id" to "ledgertxn_1", "status" to "COMPLETED")

        When("the batch handler dispatches it") {
            val (status, body) = handler.handle(
                "user_1", "key_1",
                mapOf("billId" to "bill_2", "amount" to 100, "accountNumber" to "WASAC-1", "provider" to "WASAC"),
            )

            Then("it returns 200 with the real transaction, same shape as the single-action endpoint") {
                status shouldBe 200
                body["success"] shouldBe true
            }
        }
    }

    Given("a bill payment that declines because the rail is down") {
        val billsService = mockk<BillsService>()
        val idempotencyService = idempotencyServiceThatJustRuns()
        val handler = PayBillBatchActionHandler(billsService, idempotencyService)

        every { billsService.payBill(any(), any(), any(), any(), any()) } throws ProviderDeclinedException("WASAC declined")

        When("the batch handler dispatches it") {
            val (status, body) = handler.handle("user_1", "key_2", mapOf("billId" to "bill_2", "amount" to 100))

            Then("it maps to 502 PROVIDER_DECLINED instead of throwing and failing the whole batch") {
                status shouldBe 502
                (body["error"] as Map<*, *>)["code"] shouldBe "PROVIDER_DECLINED"
            }
        }
    }

    Given("a bill payment the account can't afford") {
        val billsService = mockk<BillsService>()
        val idempotencyService = idempotencyServiceThatJustRuns()
        val handler = PayBillBatchActionHandler(billsService, idempotencyService)

        every { billsService.payBill(any(), any(), any(), any(), any()) } throws InsufficientFundsException("not enough")

        When("the batch handler dispatches it") {
            val (status, body) = handler.handle("user_1", "key_3", mapOf("billId" to "bill_2", "amount" to 999999))

            Then("it maps to 422 INSUFFICIENT_FUNDS") {
                status shouldBe 422
                (body["error"] as Map<*, *>)["code"] shouldBe "INSUFFICIENT_FUNDS"
            }
        }
    }

    Given("an action body missing a required field") {
        val billsService = mockk<BillsService>()
        val idempotencyService = idempotencyServiceThatJustRuns()
        val handler = PayBillBatchActionHandler(billsService, idempotencyService)

        When("billId is absent from the queued action's body") {
            val (status, body) = handler.handle("user_1", "key_4", mapOf("amount" to 100))

            Then("it's a real 400, not a crash that takes the whole batch down") {
                status shouldBe 400
                (body["error"] as Map<*, *>)["code"] shouldBe "INVALID_ACTION_BODY"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
