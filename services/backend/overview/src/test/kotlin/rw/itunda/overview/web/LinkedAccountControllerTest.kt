package rw.itunda.overview.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.LinkedAccount
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.overview.LinkedAccountAlreadyUnlinkedException
import rw.itunda.overview.LinkedAccountNotFoundException
import rw.itunda.overview.LinkedAccountService

/**
 * First test coverage for LinkedAccountController's REST layer -- same gap class
 * the Certificate/Vehicle/Partners/Identity passes already found and fixed once
 * each. Covers the real idempotency wiring added 2026-09-07, controller-to-
 * service delegation, and every real @ExceptionHandler mapping.
 */
class LinkedAccountControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time account-link request") {
        val service = mockk<LinkedAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = LinkedAccountController(service, idempotencyService)

        val account = LinkedAccount(
            id = "linked_1", userId = "user_1", provider = "mtn_momo", externalAccountNumberMasked = "•••• 1234", status = LinkedAccountStatus.LINKED,
        )
        every { service.link("user_1", "mtn_momo", "0788001234") } returns account

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        val request = LinkAccountRequest("mtn_momo", "0788001234")
        every {
            idempotencyService.replayOrExecute("POST /api/v1/accounts/link", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("linking") {
            val response = controller.link(request, "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route and the real request body") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/accounts/link", "key-1", request, any()) }
                verify(exactly = 1) { service.link("user_1", "mtn_momo", "0788001234") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("success") shouldBe true
                response.body?.get("linkedAccount") shouldBe account
            }
        }
    }

    Given("a declined verification during account-link") {
        val service = mockk<LinkedAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = LinkedAccountController(service, idempotencyService)

        val account = LinkedAccount(
            id = "linked_2", userId = "user_1", provider = "mtn_momo", externalAccountNumberMasked = "•••• 5678", status = LinkedAccountStatus.VERIFICATION_FAILED,
        )
        val request = LinkAccountRequest("mtn_momo", "0788005678")
        every { service.link("user_1", "mtn_momo", "0788005678") } returns account
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/accounts/link", "key-2", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("linking") {
            val response = controller.link(request, "key-2", currentUser)

            Then("success reflects the real verification outcome, not just that a row was written") {
                response.body?.get("success") shouldBe false
            }
        }
    }

    Given("a retried account-link request using the same Idempotency-Key as a completed one") {
        val service = mockk<LinkedAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = LinkedAccountController(service, idempotencyService)
        val request = LinkAccountRequest("mtn_momo", "0788001234")

        every {
            idempotencyService.replayOrExecute("POST /api/v1/accounts/link", "key-1", request, any())
        } returns (200 to mapOf("success" to true, "linkedAccount" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.link(request, "key-1", currentUser)

            Then("the cached response is returned and a duplicate account is never linked") {
                response.body?.get("linkedAccount") shouldBe "cached-result"
                verify(exactly = 0) { service.link(any(), any(), any()) }
            }
        }
    }

    Given("a real request for the caller's own linked accounts") {
        val service = mockk<LinkedAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = LinkedAccountController(service, idempotencyService)
        val accounts = listOf(LinkedAccount(id = "linked_1", userId = "user_1", provider = "mtn_momo", externalAccountNumberMasked = "•••• 1234", status = LinkedAccountStatus.LINKED))
        every { service.getMyLinkedAccounts("user_1") } returns accounts

        When("fetching them") {
            val response = controller.linked(currentUser)

            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getMyLinkedAccounts("user_1") }
                response.body?.get("linkedAccounts") shouldBe accounts
            }
        }
    }

    Given("a real unlink request") {
        val service = mockk<LinkedAccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = LinkedAccountController(service, idempotencyService)
        val unlinked = LinkedAccount(id = "linked_1", userId = "user_1", provider = "mtn_momo", externalAccountNumberMasked = "•••• 1234", status = LinkedAccountStatus.UNLINKED)
        every { service.unlink("user_1", "linked_1") } returns unlinked

        When("unlinking") {
            val response = controller.unlink("linked_1", currentUser)

            Then("it delegates to the service scoped to the caller's own userId and accountId") {
                verify(exactly = 1) { service.unlink("user_1", "linked_1") }
                response.body?.get("linkedAccount") shouldBe unlinked
            }
        }
    }

    listOf(
        Triple(LinkedAccountNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "LINKED_ACCOUNT_NOT_FOUND"),
        Triple(LinkedAccountAlreadyUnlinkedException("Conflict"), HttpStatus.CONFLICT, "LINKED_ACCOUNT_ALREADY_UNLINKED"),
        Triple(IllegalArgumentException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_REQUEST"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val service = mockk<LinkedAccountService>()
            val idempotencyService = mockk<IdempotencyService>()
            val controller = LinkedAccountController(service, idempotencyService)

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is LinkedAccountNotFoundException -> controller.handleNotFound(exception)
                    is LinkedAccountAlreadyUnlinkedException -> controller.handleAlreadyUnlinked(exception)
                    is IllegalArgumentException -> controller.handleBadRequest(exception)
                    is RateLimitExceededException -> controller.handleRateLimit(exception)
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
