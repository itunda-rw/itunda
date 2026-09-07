package rw.itunda.loans

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.OverdraftAccount
import rw.itunda.core.domain.PostpaidCreditLine
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal
import org.springframework.web.bind.MissingRequestHeaderException

/**
 * First test coverage for LoansController -- the busiest controller in this module
 * (14 endpoints), despite every sibling controller (CooperativeController/
 * StudentLoanController/VendorCashAdvanceController/VupLoanController) already having
 * one.
 */
class LoansControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(
        loansService: LoansService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
        overdraftService: OverdraftService = mockk(),
        postpaidCreditService: PostpaidCreditService = mockk(),
        scheduler: PostpaidCreditPaymentReminderScheduler = mockk(),
    ) = LoansController(loansService, idempotencyService, overdraftService, postpaidCreditService, scheduler)

    Given("a real offers/lenders request") {
        val loansService = mockk<LoansService>()
        val ctl = controller(loansService = loansService)
        every { loansService.getOffers(null) } returns listOf(
            LoanOffer(id = "loan_1", lenderId = "lender_itunda", lenderName = "itunda", name = "Personal Loan", maxAmount = BigDecimal("500000"), interestRate = 3.0, term = "12mo", requirements = "none"),
        )
        every { loansService.getLenders() } returns listOf(Lender(id = "lender_itunda", name = "itunda", kind = "digital"))

        When("fetching offers with no lender filter") {
            val response = ctl.getOffers(null)
            Then("it delegates with a null filter") {
                verify(exactly = 1) { loansService.getOffers(null) }
                response.body?.get("success") shouldBe true
            }
        }

        When("fetching lenders") {
            val response = ctl.getLenders()
            Then("it delegates") {
                verify(exactly = 1) { loansService.getLenders() }
                response.body?.get("success") shouldBe true
            }
        }
    }

    Given("a real request for the caller's own loans") {
        val loansService = mockk<LoansService>()
        val ctl = controller(loansService = loansService)
        every { loansService.getMyLoans("user_1") } returns listOf(mapOf("id" to "loan_1"))

        When("fetching them") {
            ctl.getMyLoans(currentUser)
            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { loansService.getMyLoans("user_1") }
            }
        }
    }

    Given("a real loan application") {
        val loansService = mockk<LoansService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(loansService = loansService, idempotencyService = idempotencyService)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        val request = ApplyLoanRequest("loan_1", BigDecimal("100000"))
        every { loansService.applyForLoan("user_1", "loan_1", BigDecimal("100000")) } returns mapOf("status" to "approved")
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/apply", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("applying") {
            val response = ctl.apply(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/loans/apply", "key-1", request, any()) }
                verify(exactly = 1) { loansService.applyForLoan("user_1", "loan_1", BigDecimal("100000")) }
                response.body?.get("loan") shouldBe mapOf("status" to "approved")
            }
        }
    }

    Given("a real loan repayment") {
        val loansService = mockk<LoansService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(loansService = loansService, idempotencyService = idempotencyService)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        val request = RepayLoanRequest("loan_1", BigDecimal("1000"))
        every { loansService.repayLoan("user_1", "loan_1", BigDecimal("1000")) } returns mapOf("amount" to BigDecimal("1000"))
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/repay", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("repaying") {
            ctl.repay(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/loans/repay", "key-1", request, any()) }
                verify(exactly = 1) { loansService.repayLoan("user_1", "loan_1", BigDecimal("1000")) }
            }
        }
    }

    Given("a real loan refinance") {
        val loansService = mockk<LoansService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(loansService = loansService, idempotencyService = idempotencyService)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        val request = RefinanceLoanRequest("loan_1")
        every { loansService.refinanceLoan("user_1", "loan_1") } returns mapOf("newLoanId" to "loan_2")
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/refinance", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("refinancing") {
            ctl.refinance(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/loans/refinance", "key-1", request, any()) }
                verify(exactly = 1) { loansService.refinanceLoan("user_1", "loan_1") }
            }
        }
    }

    Given("a real overdraft open/get") {
        val overdraftService = mockk<OverdraftService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(overdraftService = overdraftService, idempotencyService = idempotencyService)
        val account = mockk<OverdraftAccount>(relaxed = true)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        val request = OpenOverdraftRequest(BigDecimal("100000"))
        every { overdraftService.openOverdraft("user_1", BigDecimal("100000")) } returns account
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/overdraft/open", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }
        every { overdraftService.getMyOverdraft("user_1") } returns account

        When("opening an overdraft") {
            val response = ctl.openOverdraft(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/loans/overdraft/open", "key-1", request, any()) }
                verify(exactly = 1) { overdraftService.openOverdraft("user_1", BigDecimal("100000")) }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }

        When("fetching the caller's own overdraft") {
            ctl.getMyOverdraft(currentUser)
            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { overdraftService.getMyOverdraft("user_1") }
            }
        }
    }

    Given("a real overdraft draw/repay") {
        val overdraftService = mockk<OverdraftService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(overdraftService = overdraftService, idempotencyService = idempotencyService)
        val drawActionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        val repayActionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        val drawRequest = OverdraftAmountRequest(BigDecimal("30000"))
        val repayRequest = OverdraftAmountRequest(BigDecimal("10000"))
        every { overdraftService.draw("user_1", BigDecimal("30000")) } returns mapOf("drawnBalance" to BigDecimal("30000"))
        every { overdraftService.repay("user_1", BigDecimal("10000")) } returns mapOf("drawnBalance" to BigDecimal("20000"))
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/overdraft/draw", "key-1", drawRequest, capture(drawActionSlot))
        } answers { drawActionSlot.captured.invoke() }
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/overdraft/repay", "key-2", repayRequest, capture(repayActionSlot))
        } answers { repayActionSlot.captured.invoke() }

        When("drawing") {
            ctl.drawOverdraft(drawRequest, "key-1", currentUser)
            Then("it real-delegates scoped to the caller's own userId") {
                verify(exactly = 1) { overdraftService.draw("user_1", BigDecimal("30000")) }
            }
        }

        When("repaying") {
            ctl.repayOverdraft(repayRequest, "key-2", currentUser)
            Then("it real-delegates scoped to the caller's own userId") {
                verify(exactly = 1) { overdraftService.repay("user_1", BigDecimal("10000")) }
            }
        }
    }

    Given("a real postpaid credit apply/get/spend/repay") {
        val postpaidCreditService = mockk<PostpaidCreditService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(postpaidCreditService = postpaidCreditService, idempotencyService = idempotencyService)
        val line = mockk<PostpaidCreditLine>(relaxed = true)
        val applyActionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        val spendActionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        val repayActionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        val spendRequest = PostpaidCreditAmountRequest(BigDecimal("30000"))
        val repayRequest = PostpaidCreditAmountRequest(BigDecimal("10000"))
        every { postpaidCreditService.applyForPostpaidCredit("user_1") } returns line
        every { postpaidCreditService.getMyPostpaidCredit("user_1") } returns line
        every { postpaidCreditService.spend("user_1", BigDecimal("30000")) } returns mapOf("currentBalance" to BigDecimal("30000"))
        every { postpaidCreditService.repay("user_1", BigDecimal("10000")) } returns mapOf("currentBalance" to BigDecimal("20000"))
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/postpaid-credit/apply", "key-1", "user_1", capture(applyActionSlot))
        } answers { applyActionSlot.captured.invoke() }
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/postpaid-credit/spend", "key-2", spendRequest, capture(spendActionSlot))
        } answers { spendActionSlot.captured.invoke() }
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/postpaid-credit/repay", "key-3", repayRequest, capture(repayActionSlot))
        } answers { repayActionSlot.captured.invoke() }

        When("applying") {
            val response = ctl.applyForPostpaidCredit("key-1", currentUser)
            Then("it real-delegates scoped to the caller's own userId") {
                verify(exactly = 1) { postpaidCreditService.applyForPostpaidCredit("user_1") }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }

        When("fetching the caller's own line") {
            ctl.getMyPostpaidCredit(currentUser)
            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { postpaidCreditService.getMyPostpaidCredit("user_1") }
            }
        }

        When("spending") {
            ctl.spendPostpaidCredit(spendRequest, "key-2", currentUser)
            Then("it real-delegates scoped to the caller's own userId") {
                verify(exactly = 1) { postpaidCreditService.spend("user_1", BigDecimal("30000")) }
            }
        }

        When("repaying") {
            ctl.repayPostpaidCredit(repayRequest, "key-3", currentUser)
            Then("it real-delegates scoped to the caller's own userId") {
                verify(exactly = 1) { postpaidCreditService.repay("user_1", BigDecimal("10000")) }
            }
        }
    }

    Given("a real ADMIN-only manual trigger for postpaid-credit payment reminders") {
        val scheduler = mockk<PostpaidCreditPaymentReminderScheduler>()
        val ctl = controller(scheduler = scheduler)
        every { scheduler.processDue() } returns 3

        When("triggering it") {
            val response = ctl.processPostpaidCreditPaymentReminders(currentUser)
            Then("it real-delegates and reports the real processed count") {
                verify(exactly = 1) { scheduler.processDue() }
                response.body?.get("processed") shouldBe 3
            }
        }
    }

    listOf(
        Triple(PostpaidCreditAlreadyOpenException("Conflict") as RuntimeException, HttpStatus.CONFLICT, "POSTPAID_CREDIT_ALREADY_OPEN"),
        Triple(PostpaidCreditNotActiveException("Not found"), HttpStatus.NOT_FOUND, "POSTPAID_CREDIT_NOT_ACTIVE"),
        Triple(PostpaidCreditLimitExceededException("Unprocessable"), HttpStatus.UNPROCESSABLE_ENTITY, "POSTPAID_CREDIT_LIMIT_EXCEEDED"),
        Triple(PostpaidCreditInvalidAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_AMOUNT"),
        Triple(PostpaidCreditNoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(PostpaidCreditSuspendedException("Forbidden"), HttpStatus.FORBIDDEN, "POSTPAID_CREDIT_SUSPENDED"),
        Triple(OverdraftAlreadyActiveException("Conflict"), HttpStatus.CONFLICT, "OVERDRAFT_ALREADY_ACTIVE"),
        Triple(OverdraftLimitInvalidException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_OVERDRAFT_LIMIT"),
        Triple(OverdraftApplicationDeclinedException("Declined"), HttpStatus.UNPROCESSABLE_ENTITY, "OVERDRAFT_APPLICATION_DECLINED"),
        Triple(OverdraftNotActiveException("Not found"), HttpStatus.NOT_FOUND, "OVERDRAFT_NOT_ACTIVE"),
        Triple(OverdraftLimitExceededException("Unprocessable"), HttpStatus.UNPROCESSABLE_ENTITY, "OVERDRAFT_LIMIT_EXCEEDED"),
        Triple(OverdraftInvalidAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_AMOUNT"),
        Triple(OverdraftNoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(NoBetterRateAvailableException("Unprocessable"), HttpStatus.UNPROCESSABLE_ENTITY, "NO_BETTER_RATE_AVAILABLE"),
        Triple(IdempotencyConflictException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
        Triple(LoanOfferNotFoundException("Not found"), HttpStatus.NOT_FOUND, "LOAN_OFFER_NOT_FOUND"),
        Triple(LoanNotFoundException("Not found"), HttpStatus.NOT_FOUND, "LOAN_NOT_FOUND"),
        Triple(NoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(BusinessAccountRequiredException("Conflict"), HttpStatus.CONFLICT, "BUSINESS_ACCOUNT_REQUIRED"),
        Triple(LoanAlreadyPaidException("Conflict"), HttpStatus.CONFLICT, "LOAN_ALREADY_PAID"),
        Triple(LoanAmountInvalidException("Unprocessable"), HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LOAN_AMOUNT"),
        Triple(LoanApplicationDeclinedException("Declined"), HttpStatus.UNPROCESSABLE_ENTITY, "LOAN_APPLICATION_DECLINED"),
        Triple(InsufficientFundsException("Insufficient"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS"),
        Triple(AccountFrozenException("Frozen"), HttpStatus.FORBIDDEN, "ACCOUNT_FROZEN"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is PostpaidCreditAlreadyOpenException -> ctl.handlePostpaidAlreadyOpen(exception)
                    is PostpaidCreditNotActiveException -> ctl.handlePostpaidNotActive(exception)
                    is PostpaidCreditLimitExceededException -> ctl.handlePostpaidLimitExceeded(exception)
                    is PostpaidCreditInvalidAmountException -> ctl.handlePostpaidInvalidAmount(exception)
                    is PostpaidCreditNoAccountException -> ctl.handlePostpaidNoAccount(exception)
                    is PostpaidCreditSuspendedException -> ctl.handlePostpaidSuspended(exception)
                    is OverdraftAlreadyActiveException -> ctl.handleOverdraftAlreadyActive(exception)
                    is OverdraftLimitInvalidException -> ctl.handleOverdraftLimitInvalid(exception)
                    is OverdraftApplicationDeclinedException -> ctl.handleOverdraftDeclined(exception)
                    is OverdraftNotActiveException -> ctl.handleOverdraftNotActive(exception)
                    is OverdraftLimitExceededException -> ctl.handleOverdraftLimitExceeded(exception)
                    is OverdraftInvalidAmountException -> ctl.handleOverdraftInvalidAmount(exception)
                    is OverdraftNoAccountException -> ctl.handleOverdraftNoAccount(exception)
                    is NoBetterRateAvailableException -> ctl.handleNoBetterRate(exception)
                    is IdempotencyConflictException -> ctl.handleConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleInProgress(exception)
                    is LoanOfferNotFoundException -> ctl.handleOfferNotFound(exception)
                    is LoanNotFoundException -> ctl.handleNotFound(exception)
                    is NoAccountException -> ctl.handleNoAccount(exception)
                    is BusinessAccountRequiredException -> ctl.handleBusinessAccountRequired(exception)
                    is LoanAlreadyPaidException -> ctl.handleAlreadyPaid(exception)
                    is LoanAmountInvalidException -> ctl.handleInvalidAmount(exception)
                    is LoanApplicationDeclinedException -> ctl.handleDeclined(exception)
                    is InsufficientFundsException -> ctl.handleInsufficientFunds(exception)
                    is AccountFrozenException -> ctl.handleAccountFrozen(exception)
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
            val response = ctl.handleMissingHeader(mockk(relaxed = true))

            Then("it maps to 400 with code IDEMPOTENCY_KEY_REQUIRED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "IDEMPOTENCY_KEY_REQUIRED"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
