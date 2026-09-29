package rw.itunda.p2p.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingRequestHeaderException
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.AutoTransfer
import rw.itunda.core.domain.AutoTransferFrequency
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.p2p.AutoTransferInvalidScheduleException
import rw.itunda.p2p.AutoTransferNotFoundException
import rw.itunda.p2p.AutoTransferService
import rw.itunda.p2p.P2pInvalidAmountException
import rw.itunda.p2p.P2pNoAccountException
import rw.itunda.p2p.P2pRecipientNotFoundException
import rw.itunda.p2p.P2pSelfPaymentException
import java.math.BigDecimal

private fun testAutoTransfer(id: String = "autotransfer_1") = AutoTransfer(
    id = id, userId = "user_1", accountId = "account_1", recipientIdentifier = "+250788000002",
    recipientName = "Jean B.", amount = BigDecimal("5000"), frequency = AutoTransferFrequency.MONTHLY, dayOfMonth = 1,
)

/**
 * First test coverage for AutoTransferController -- previously untested despite the
 * service layer being fully tested. Covers all 5 endpoints' delegation (caller-scoped
 * userId, never client-supplied) and all 9 exception handlers.
 */
class AutoTransferControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(
        autoTransferService: AutoTransferService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
    ) = AutoTransferController(autoTransferService, idempotencyService)

    Given("a real create-auto-transfer request") {
        val autoTransferService = mockk<AutoTransferService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(autoTransferService, idempotencyService)
        val request = CreateAutoTransferRequest("+250788000002", BigDecimal("5000"), AutoTransferFrequency.MONTHLY, null, 1, "Rent")
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            autoTransferService.create("user_1", "+250788000002", BigDecimal("5000"), AutoTransferFrequency.MONTHLY, null, 1, "Rent")
        } returns testAutoTransfer()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/p2p/auto-transfers", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("creating it") {
            val response = ctl.create(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/p2p/auto-transfers", "key-1", request, any()) }
                verify(exactly = 1) { autoTransferService.create("user_1", "+250788000002", BigDecimal("5000"), AutoTransferFrequency.MONTHLY, null, 1, "Rent") }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real my-auto-transfers request") {
        val autoTransferService = mockk<AutoTransferService>()
        val ctl = controller(autoTransferService = autoTransferService)
        every { autoTransferService.getMine("user_1") } returns emptyList()

        When("fetching it") {
            ctl.getMine(currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { autoTransferService.getMine("user_1") }
            }
        }
    }

    Given("a real pause request") {
        val autoTransferService = mockk<AutoTransferService>()
        val ctl = controller(autoTransferService = autoTransferService)
        every { autoTransferService.pause("user_1", "autotransfer_1") } returns testAutoTransfer()

        When("pausing it") {
            ctl.pause("autotransfer_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { autoTransferService.pause("user_1", "autotransfer_1") }
            }
        }
    }

    Given("a real resume request") {
        val autoTransferService = mockk<AutoTransferService>()
        val ctl = controller(autoTransferService = autoTransferService)
        every { autoTransferService.resume("user_1", "autotransfer_1") } returns testAutoTransfer()

        When("resuming it") {
            ctl.resume("autotransfer_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { autoTransferService.resume("user_1", "autotransfer_1") }
            }
        }
    }

    Given("a real cancel request") {
        val autoTransferService = mockk<AutoTransferService>()
        val ctl = controller(autoTransferService = autoTransferService)
        every { autoTransferService.cancel("user_1", "autotransfer_1") } returns testAutoTransfer()

        When("cancelling it") {
            ctl.cancel("autotransfer_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { autoTransferService.cancel("user_1", "autotransfer_1") }
            }
        }
    }

    listOf(
        Triple(AutoTransferNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "AUTO_TRANSFER_NOT_FOUND"),
        Triple(AutoTransferInvalidScheduleException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE"),
        Triple(P2pRecipientNotFoundException("Not found"), HttpStatus.NOT_FOUND, "P2P_RECIPIENT_NOT_FOUND"),
        Triple(P2pSelfPaymentException("Bad request"), HttpStatus.BAD_REQUEST, "SELF_PAYMENT_NOT_ALLOWED"),
        Triple(P2pNoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(P2pInvalidAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_AMOUNT"),
        Triple(InsufficientFundsException("Insufficient funds"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
        Triple(IdempotencyConflictException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is AutoTransferNotFoundException -> ctl.handleNotFound(exception)
                    is AutoTransferInvalidScheduleException -> ctl.handleInvalidSchedule(exception)
                    is P2pRecipientNotFoundException -> ctl.handleRecipientNotFound(exception)
                    is P2pSelfPaymentException -> ctl.handleSelfPayment(exception)
                    is P2pNoAccountException -> ctl.handleNoAccount(exception)
                    is P2pInvalidAmountException -> ctl.handleInvalidAmount(exception)
                    is InsufficientFundsException -> ctl.handleInsufficientFunds(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    is IdempotencyConflictException -> ctl.handleConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleInProgress(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }

    Given("a real MissingRequestHeaderException") {
        val ctl = controller()
        When("its exception handler maps it to a real HTTP response") {
            val response = ctl.handleMissingHeader(mockk<MissingRequestHeaderException>(relaxed = true))
            Then("it maps to 400 with code IDEMPOTENCY_KEY_REQUIRED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "IDEMPOTENCY_KEY_REQUIRED"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
