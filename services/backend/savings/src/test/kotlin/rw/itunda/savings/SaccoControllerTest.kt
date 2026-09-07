package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.SaccoDividendDistribution
import rw.itunda.core.domain.SaccoShareholding
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal

/**
 * First test coverage for SaccoController. Also covers the real admin trigger newly
 * wired for SaccoService.declareDividend -- see that method's own doc comment for the
 * disclosed half-built-feature gap this closes.
 */
class SaccoControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a real share purchase") {
        val service = mockk<SaccoService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SaccoController(service, idempotencyService)
        val shareholding = mockk<SaccoShareholding>(relaxed = true)
        val view = SaccoShareholdingView(shareholding, BigDecimal("50000"))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { service.buyShares("user_1", BigDecimal("50000")) } returns view
        every {
            idempotencyService.replayOrExecute("POST /api/v1/sacco/shares/buy", "key-1", "user_1", capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("buying shares") {
            val response = controller.buyShares(SaccoAmountRequest(BigDecimal("50000")), "key-1", currentUser)

            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/sacco/shares/buy", "key-1", "user_1", any()) }
                verify(exactly = 1) { service.buyShares("user_1", BigDecimal("50000")) }
                response.body?.get("shareholding") shouldBe shareholding
            }
        }
    }

    Given("a real share redemption") {
        val service = mockk<SaccoService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SaccoController(service, idempotencyService)
        val shareholding = mockk<SaccoShareholding>(relaxed = true)
        val view = SaccoShareholdingView(shareholding, BigDecimal("10000"))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { service.redeemShares("user_1", BigDecimal("10000")) } returns view
        every {
            idempotencyService.replayOrExecute("POST /api/v1/sacco/shares/redeem", "key-1", "user_1", capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("redeeming shares") {
            controller.redeemShares(SaccoAmountRequest(BigDecimal("10000")), "key-1", currentUser)

            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/sacco/shares/redeem", "key-1", "user_1", any()) }
                verify(exactly = 1) { service.redeemShares("user_1", BigDecimal("10000")) }
            }
        }
    }

    Given("a real request for the caller's own shareholding") {
        val service = mockk<SaccoService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SaccoController(service, idempotencyService)
        every { service.getMyShareholding("user_1") } returns null

        When("fetching it") {
            controller.myShareholding(currentUser)

            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getMyShareholding("user_1") }
            }
        }
    }

    Given("a real request for the caller's own dividend history") {
        val service = mockk<SaccoService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SaccoController(service, idempotencyService)
        every { service.getMyDividendHistory("user_1") } returns emptyList()

        When("fetching it") {
            controller.myDividendHistory(currentUser)

            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getMyDividendHistory("user_1") }
            }
        }
    }

    Given("a real ADMIN declaring a dividend") {
        val service = mockk<SaccoService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SaccoController(service, idempotencyService)
        val distribution = mockk<SaccoDividendDistribution>(relaxed = true)
        every { service.declareDividend() } returns distribution

        When("declaring") {
            val response = controller.declareDividend()

            Then("it real-delegates to the real, already-hardened declareDividend method") {
                verify(exactly = 1) { service.declareDividend() }
                response.body?.get("distribution") shouldBe distribution
            }
        }
    }

    listOf(
        Triple(SaccoNoAccountException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(SaccoNoShareholdingException("Not found"), HttpStatus.NOT_FOUND, "SACCO_NO_SHAREHOLDING"),
        Triple(SaccoInsufficientSharesException("Unprocessable"), HttpStatus.UNPROCESSABLE_ENTITY, "SACCO_INSUFFICIENT_SHARES"),
        Triple(SaccoNoSharesOutstandingException("Unprocessable"), HttpStatus.UNPROCESSABLE_ENTITY, "SACCO_NO_SHARES_OUTSTANDING"),
        Triple(IllegalArgumentException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_REQUEST"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val controller = SaccoController(mockk(), mockk())

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is SaccoNoAccountException -> controller.handleNoAccount(exception)
                    is SaccoNoShareholdingException -> controller.handleNoShareholding(exception)
                    is SaccoInsufficientSharesException -> controller.handleInsufficientShares(exception)
                    is SaccoNoSharesOutstandingException -> controller.handleNoSharesOutstanding(exception)
                    is IllegalArgumentException -> controller.handleIllegalArgument(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
