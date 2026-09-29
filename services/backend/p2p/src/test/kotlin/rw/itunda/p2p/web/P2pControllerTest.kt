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
import rw.itunda.core.domain.P2pDelayedTransfer
import rw.itunda.core.domain.P2pPaymentRequest
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.family.FamilySpendLimitExceededException
import rw.itunda.p2p.P2pDelayedTransferNotCancellableException
import rw.itunda.p2p.P2pDelayedTransferNotFoundException
import rw.itunda.p2p.P2pDelayedTransferService
import rw.itunda.p2p.P2pInvalidAmountException
import rw.itunda.p2p.P2pNoAccountException
import rw.itunda.p2p.P2pRecipientNotFoundException
import rw.itunda.p2p.P2pRecipientPreview
import rw.itunda.p2p.P2pRequestNotFoundException
import rw.itunda.p2p.P2pRequestNotPayableException
import rw.itunda.p2p.P2pSelfPaymentException
import rw.itunda.p2p.P2pService
import rw.itunda.p2p.P2pTransferLimitExceededException
import rw.itunda.p2p.P2pTransferLimitService
import java.math.BigDecimal
import java.time.Instant

private fun testTransaction(id: String = "txn_1") = Transaction(
    id = id, referenceNumber = "REF1", senderId = "user_1", recipientId = "user_2", amount = BigDecimal("1000"),
    fee = BigDecimal.ZERO, currency = "RWF", type = TransactionType.TRANSFER, status = TransactionStatus.COMPLETED, description = "x",
)

private fun testRequest(id: String = "request_1") = P2pPaymentRequest(
    id = id, requesterUserId = "user_1", amount = BigDecimal("1000"), description = "x", expiresAt = Instant.now().plusSeconds(3600),
)

private fun testDelayedTransfer(id: String = "delayed_1") = P2pDelayedTransfer(
    id = id, senderUserId = "user_1", senderAccountId = "account_1", recipientUserId = "user_2", recipientAccountId = "account_2",
    amount = BigDecimal("1000"), description = "x", holdTransactionId = "txn_hold_1", releaseAt = Instant.now().plusSeconds(3600),
)

/**
 * First test coverage for P2pController -- previously untested despite P2pServiceTest.kt
 * already having full service-layer coverage, and despite this being the app's most
 * central money-movement controller. Covers all 10 endpoints' delegation (caller-scoped
 * userId, never client-supplied) and all 15 exception handlers, matching this sweep's
 * established BehaviorSpec/MockK shape.
 */
class P2pControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(
        p2pService: P2pService = mockk(),
        p2pDelayedTransferService: P2pDelayedTransferService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
        p2pTransferLimitService: P2pTransferLimitService = mockk(),
    ) = P2pController(p2pService, p2pDelayedTransferService, idempotencyService, p2pTransferLimitService)

    Given("a real transfer-limit request") {
        val p2pTransferLimitService = mockk<P2pTransferLimitService>()
        val ctl = controller(p2pTransferLimitService = p2pTransferLimitService)
        every { p2pTransferLimitService.getRemainingToday("user_1") } returns BigDecimal("2000000")

        When("fetching it") {
            ctl.getTransferLimit(currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { p2pTransferLimitService.getRemainingToday("user_1") }
            }
        }
    }

    Given("a real generate-request request") {
        val p2pService = mockk<P2pService>()
        val ctl = controller(p2pService = p2pService)
        val request = GenerateP2pRequest(BigDecimal("1000"), "Lunch")
        every { p2pService.generateRequest("user_1", BigDecimal("1000"), "Lunch") } returns testRequest()

        When("generating it") {
            val response = ctl.generateRequest(request, currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { p2pService.generateRequest("user_1", BigDecimal("1000"), "Lunch") }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real my-requests request") {
        val p2pService = mockk<P2pService>()
        val ctl = controller(p2pService = p2pService)
        every { p2pService.getMyRequests("user_1") } returns emptyList()

        When("fetching it") {
            ctl.getMyRequests(currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { p2pService.getMyRequests("user_1") }
            }
        }
    }

    Given("a real pay-request request") {
        val p2pService = mockk<P2pService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(p2pService = p2pService, idempotencyService = idempotencyService)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { p2pService.payRequest("user_1", "request_1") } returns (testTransaction() to BigDecimal("5000"))
        every {
            idempotencyService.replayOrExecute("POST /api/v1/p2p/pay/request_1", "key-1", "request_1", capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("paying it") {
            val response = ctl.pay("request_1", "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/p2p/pay/request_1", "key-1", "request_1", any()) }
                verify(exactly = 1) { p2pService.payRequest("user_1", "request_1") }
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    Given("a real resolve-recipient request") {
        val p2pService = mockk<P2pService>()
        val ctl = controller(p2pService = p2pService)
        every { p2pService.resolveRecipient("user_1", "+250788000002") } returns P2pRecipientPreview("user_2", "Jean B.")

        When("resolving it") {
            ctl.resolveRecipient("+250788000002", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { p2pService.resolveRecipient("user_1", "+250788000002") }
            }
        }
    }

    Given("a real send-direct request") {
        val p2pService = mockk<P2pService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(p2pService = p2pService, idempotencyService = idempotencyService)
        val request = SendDirectP2pRequest("+250788000002", BigDecimal("5000"), "Lunch", null)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { p2pService.sendDirect("user_1", "+250788000002", BigDecimal("5000"), "Lunch", null) } returns Triple(testTransaction(), BigDecimal("5000"), emptyList())
        every {
            idempotencyService.replayOrExecute("POST /api/v1/p2p/send", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("sending it") {
            val response = ctl.sendDirect(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/p2p/send", "key-1", request, any()) }
                verify(exactly = 1) { p2pService.sendDirect("user_1", "+250788000002", BigDecimal("5000"), "Lunch", null) }
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    Given("a real send-to-family-member request") {
        val p2pService = mockk<P2pService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(p2pService = p2pService, idempotencyService = idempotencyService)
        val request = SendToFamilyMemberRequest("child_1", BigDecimal("3000"), "Allowance")
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { p2pService.sendToFamilyMember("user_1", "child_1", BigDecimal("3000"), "Allowance") } returns Triple(testTransaction(), BigDecimal("3000"), emptyList())
        every {
            idempotencyService.replayOrExecute("POST /api/v1/p2p/send-to-family", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("sending it") {
            val response = ctl.sendToFamilyMember(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId as the guardian, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/p2p/send-to-family", "key-1", request, any()) }
                verify(exactly = 1) { p2pService.sendToFamilyMember("user_1", "child_1", BigDecimal("3000"), "Allowance") }
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    Given("a real send-delayed request") {
        val p2pDelayedTransferService = mockk<P2pDelayedTransferService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(p2pDelayedTransferService = p2pDelayedTransferService, idempotencyService = idempotencyService)
        val request = SendDelayedP2pRequest("+250788000002", BigDecimal("2000"), "Rent")
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { p2pDelayedTransferService.sendDelayed("user_1", "+250788000002", BigDecimal("2000"), "Rent") } returns testDelayedTransfer()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/p2p/send-delayed", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("sending it") {
            val response = ctl.sendDelayed(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/p2p/send-delayed", "key-1", request, any()) }
                verify(exactly = 1) { p2pDelayedTransferService.sendDelayed("user_1", "+250788000002", BigDecimal("2000"), "Rent") }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real my-delayed-transfers request") {
        val p2pDelayedTransferService = mockk<P2pDelayedTransferService>()
        val ctl = controller(p2pDelayedTransferService = p2pDelayedTransferService)
        every { p2pDelayedTransferService.getMyDelayedTransfers("user_1") } returns emptyList()

        When("fetching it") {
            ctl.getMyDelayedTransfers(currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { p2pDelayedTransferService.getMyDelayedTransfers("user_1") }
            }
        }
    }

    Given("a real cancel-delayed-transfer request") {
        val p2pDelayedTransferService = mockk<P2pDelayedTransferService>()
        val ctl = controller(p2pDelayedTransferService = p2pDelayedTransferService)
        every { p2pDelayedTransferService.cancel("user_1", "delayed_1") } returns testDelayedTransfer()

        When("cancelling it") {
            ctl.cancelDelayedTransfer("delayed_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { p2pDelayedTransferService.cancel("user_1", "delayed_1") }
            }
        }
    }

    listOf(
        Triple(P2pDelayedTransferNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "P2P_DELAYED_TRANSFER_NOT_FOUND"),
        Triple(P2pDelayedTransferNotCancellableException("Conflict"), HttpStatus.CONFLICT, "P2P_DELAYED_TRANSFER_NOT_CANCELLABLE"),
        Triple(P2pRequestNotFoundException("Not found"), HttpStatus.NOT_FOUND, "P2P_REQUEST_NOT_FOUND"),
        Triple(P2pRequestNotPayableException("Conflict"), HttpStatus.CONFLICT, "P2P_REQUEST_NOT_PAYABLE"),
        Triple(P2pSelfPaymentException("Bad request"), HttpStatus.BAD_REQUEST, "SELF_PAYMENT_NOT_ALLOWED"),
        Triple(P2pNoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(InsufficientFundsException("Insufficient funds"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS"),
        Triple(AccountFrozenException("Account is frozen"), HttpStatus.FORBIDDEN, "ACCOUNT_FROZEN"),
        Triple(FamilySpendLimitExceededException("Spend limit exceeded"), HttpStatus.FORBIDDEN, "FAMILY_SPEND_LIMIT_EXCEEDED"),
        Triple(IdempotencyConflictException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
        Triple(P2pRecipientNotFoundException("Not found"), HttpStatus.NOT_FOUND, "P2P_RECIPIENT_NOT_FOUND"),
        Triple(P2pInvalidAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_AMOUNT"),
        Triple(P2pTransferLimitExceededException("Transfer limit exceeded"), HttpStatus.UNPROCESSABLE_ENTITY, "P2P_TRANSFER_LIMIT_EXCEEDED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is P2pDelayedTransferNotFoundException -> ctl.handleDelayedTransferNotFound(exception)
                    is P2pDelayedTransferNotCancellableException -> ctl.handleDelayedTransferNotCancellable(exception)
                    is P2pRequestNotFoundException -> ctl.handleNotFound(exception)
                    is P2pRequestNotPayableException -> ctl.handleNotPayable(exception)
                    is P2pSelfPaymentException -> ctl.handleSelfPayment(exception)
                    is P2pNoAccountException -> ctl.handleNoAccount(exception)
                    is InsufficientFundsException -> ctl.handleInsufficientFunds(exception)
                    is AccountFrozenException -> ctl.handleAccountFrozen(exception)
                    is FamilySpendLimitExceededException -> ctl.handleFamilySpendLimit(exception)
                    is IdempotencyConflictException -> ctl.handleConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleInProgress(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    is P2pRecipientNotFoundException -> ctl.handleRecipientNotFound(exception)
                    is P2pInvalidAmountException -> ctl.handleInvalidAmount(exception)
                    is P2pTransferLimitExceededException -> ctl.handleTransferLimitExceeded(exception)
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
