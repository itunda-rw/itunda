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
import rw.itunda.core.domain.ScheduledTransfer
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.p2p.P2pInvalidAmountException
import rw.itunda.p2p.P2pNoAccountException
import rw.itunda.p2p.P2pRecipientNotFoundException
import rw.itunda.p2p.P2pSelfPaymentException
import rw.itunda.p2p.ScheduledTransferInvalidDateException
import rw.itunda.p2p.ScheduledTransferNotFoundException
import rw.itunda.p2p.ScheduledTransferNotPendingException
import rw.itunda.p2p.ScheduledTransferService
import java.math.BigDecimal
import java.time.LocalDate

private fun testScheduledTransfer(id: String = "scheduledtransfer_1") = ScheduledTransfer(
    id = id, userId = "user_1", accountId = "account_1", recipientIdentifier = "+250788000002",
    recipientName = "Jean B.", amount = BigDecimal("5000"), scheduledDate = LocalDate.now().plusDays(3),
)

/**
 * First test coverage for ScheduledTransferController -- previously untested despite
 * the service layer being fully tested. Covers all 3 endpoints' delegation
 * (caller-scoped userId, never client-supplied) and all 9 exception handlers.
 */
class ScheduledTransferControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(
        scheduledTransferService: ScheduledTransferService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
    ) = ScheduledTransferController(scheduledTransferService, idempotencyService)

    Given("a real create-scheduled-transfer request") {
        val scheduledTransferService = mockk<ScheduledTransferService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(scheduledTransferService, idempotencyService)
        val date = LocalDate.now().plusDays(3)
        val request = CreateScheduledTransferRequest("+250788000002", BigDecimal("5000"), date, "Rent")
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            scheduledTransferService.create("user_1", "+250788000002", BigDecimal("5000"), date, "Rent")
        } returns testScheduledTransfer()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/p2p/scheduled-transfers", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("creating it") {
            val response = ctl.create(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/p2p/scheduled-transfers", "key-1", request, any()) }
                verify(exactly = 1) { scheduledTransferService.create("user_1", "+250788000002", BigDecimal("5000"), date, "Rent") }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real my-scheduled-transfers request") {
        val scheduledTransferService = mockk<ScheduledTransferService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(scheduledTransferService, idempotencyService)
        every { scheduledTransferService.getMine("user_1") } returns emptyList()

        When("fetching it") {
            ctl.getMine(currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { scheduledTransferService.getMine("user_1") }
            }
        }
    }

    Given("a real cancel request") {
        val scheduledTransferService = mockk<ScheduledTransferService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(scheduledTransferService, idempotencyService)
        every { scheduledTransferService.cancel("user_1", "scheduledtransfer_1") } returns testScheduledTransfer()

        When("cancelling it") {
            ctl.cancel("scheduledtransfer_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { scheduledTransferService.cancel("user_1", "scheduledtransfer_1") }
            }
        }
    }

    listOf(
        Triple(ScheduledTransferNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "SCHEDULED_TRANSFER_NOT_FOUND"),
        Triple(ScheduledTransferNotPendingException("Conflict"), HttpStatus.CONFLICT, "SCHEDULED_TRANSFER_NOT_PENDING"),
        Triple(ScheduledTransferInvalidDateException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_SCHEDULED_DATE"),
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
                    is ScheduledTransferNotFoundException -> ctl.handleNotFound(exception)
                    is ScheduledTransferNotPendingException -> ctl.handleNotPending(exception)
                    is ScheduledTransferInvalidDateException -> ctl.handleInvalidDate(exception)
                    is P2pRecipientNotFoundException -> ctl.handleRecipientNotFound(exception)
                    is P2pSelfPaymentException -> ctl.handleSelfPayment(exception)
                    is P2pNoAccountException -> ctl.handleNoAccount(exception)
                    is P2pInvalidAmountException -> ctl.handleInvalidAmount(exception)
                    is InsufficientFundsException -> ctl.handleInsufficientFunds(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    is IdempotencyConflictException -> ctl.handleIdempotencyConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleIdempotencyInProgress(exception)
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
