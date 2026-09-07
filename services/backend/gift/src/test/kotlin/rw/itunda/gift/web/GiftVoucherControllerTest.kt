package rw.itunda.gift.web

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
import rw.itunda.core.domain.GiftVoucher
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.gift.GiftVoucherAlreadyExtendedException
import rw.itunda.gift.GiftVoucherExpiredException
import rw.itunda.gift.GiftVoucherExpiryReminderScheduler
import rw.itunda.gift.GiftVoucherInvalidAmountException
import rw.itunda.gift.GiftVoucherMerchantNotFoundException
import rw.itunda.gift.GiftVoucherNoAccountException
import rw.itunda.gift.GiftVoucherNotActiveException
import rw.itunda.gift.GiftVoucherNotExtendableException
import rw.itunda.gift.GiftVoucherNotFoundException
import rw.itunda.gift.GiftVoucherProductNotFoundException
import rw.itunda.gift.GiftVoucherProductUnavailableException
import rw.itunda.gift.GiftVoucherRecipientNotFoundException
import rw.itunda.gift.GiftVoucherSelfException
import rw.itunda.gift.GiftVoucherService

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit):
 * before the fix, `extendExpiry` called GiftVoucherService.extendExpiry directly
 * with no Idempotency-Key protection -- a lost response after a successful extend
 * would resubmit here and hit GiftVoucherAlreadyExtendedException on the retry, a
 * confusing conflict for an extension that actually already succeeded. `redeem`
 * was already protected; this was the outlier. This file exists to make sure that
 * wiring can't silently regress.
 */
class GiftVoucherControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(service: GiftVoucherService, idempotencyService: IdempotencyService) = GiftVoucherController(
        service, idempotencyService, mockk<GiftVoucherExpiryReminderScheduler>(relaxed = true),
    )

    Given("a first-time extend-expiry request") {
        val service = mockk<GiftVoucherService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(service, idempotencyService)

        val voucher = mockk<GiftVoucher>(relaxed = true)
        every { service.extendExpiry("user_1", "voucher_1") } returns voucher

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers/voucher_1/extend", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("extending the voucher") {
            val response = controller.extendExpiry("voucher_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers/voucher_1/extend", "key-1", any(), any())
                }
                verify(exactly = 1) { service.extendExpiry("user_1", "voucher_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("voucher") shouldBe voucher
            }
        }
    }

    Given("a retried extend-expiry request using the same Idempotency-Key as a completed one") {
        val service = mockk<GiftVoucherService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers/voucher_1/extend", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "voucher" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.extendExpiry("voucher_1", "key-1", currentUser)

            Then("the cached response is returned and the voucher is never extended again") {
                response.body?.get("voucher") shouldBe "cached-result"
                verify(exactly = 0) { service.extendExpiry(any(), any()) }
            }
        }
    }

    // Real gap found live (Gift product-completeness pass, 2026-09-08): this
    // controller test file only ever covered extendExpiry's own idempotency
    // regression -- purchase/get/redeem/the admin reminder trigger, and every
    // exception handler but the ones above, had no coverage at all, unlike
    // every other controller this sweep has audited.
    Given("a real purchase-voucher request") {
        val service = mockk<GiftVoucherService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(service, idempotencyService)
        val request = PurchaseGiftVoucherRequest("+250788000002", "merchant_1", "product_1", null)
        val voucher = mockk<GiftVoucher>(relaxed = true)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { service.purchaseVoucher("user_1", "+250788000002", "merchant_1", "product_1", null) } returns voucher
        every {
            idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("purchasing it") {
            val response = controller.purchaseVoucher(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers", "key-1", request, any()) }
                verify(exactly = 1) { service.purchaseVoucher("user_1", "+250788000002", "merchant_1", "product_1", null) }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real single-voucher request") {
        val service = mockk<GiftVoucherService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(service, idempotencyService)
        val voucher = mockk<GiftVoucher>(relaxed = true)
        every { service.getVoucher("user_1", "voucher_1") } returns voucher

        When("fetching it") {
            controller.getVoucher("voucher_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getVoucher("user_1", "voucher_1") }
            }
        }
    }

    Given("a real per-conversation voucher history request") {
        val service = mockk<GiftVoucherService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(service, idempotencyService)
        every { service.getVouchersForConversation("user_1", "conversation_1") } returns emptyList()

        When("fetching it") {
            controller.getVouchersForConversation("conversation_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getVouchersForConversation("user_1", "conversation_1") }
            }
        }
    }

    Given("a real merchant redeem request") {
        val service = mockk<GiftVoucherService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(service, idempotencyService)
        val voucher = mockk<GiftVoucher>(relaxed = true)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { service.redeemVoucher("user_1", "voucher_1") } returns voucher
        every {
            idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers/voucher_1/redeem", "key-1", "voucher_1", capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("redeeming it") {
            val response = controller.redeemVoucher("voucher_1", "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId (the merchant), never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/gift-vouchers/voucher_1/redeem", "key-1", "voucher_1", any()) }
                verify(exactly = 1) { service.redeemVoucher("user_1", "voucher_1") }
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    Given("a real admin expiry-reminders trigger") {
        val service = mockk<GiftVoucherService>()
        val idempotencyService = mockk<IdempotencyService>()
        val scheduler = mockk<GiftVoucherExpiryReminderScheduler>()
        val controller = GiftVoucherController(service, idempotencyService, scheduler)
        every { scheduler.processDue() } returns 3

        When("triggering it") {
            val response = controller.processExpiryReminders(currentUser)
            Then("it delegates to the real scheduler's own processDue, not a duplicate implementation") {
                verify(exactly = 1) { scheduler.processDue() }
                response.body?.get("processed") shouldBe 3
            }
        }
    }

    listOf(
        Triple(GiftVoucherNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "GIFT_VOUCHER_NOT_FOUND"),
        Triple(GiftVoucherNotActiveException("Conflict"), HttpStatus.CONFLICT, "GIFT_VOUCHER_NOT_ACTIVE"),
        Triple(GiftVoucherExpiredException("Conflict"), HttpStatus.CONFLICT, "GIFT_VOUCHER_EXPIRED"),
        Triple(GiftVoucherSelfException("Bad request"), HttpStatus.BAD_REQUEST, "SELF_GIFT_NOT_ALLOWED"),
        Triple(GiftVoucherNoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(GiftVoucherRecipientNotFoundException("Not found"), HttpStatus.NOT_FOUND, "GIFT_VOUCHER_RECIPIENT_NOT_FOUND"),
        Triple(GiftVoucherInvalidAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_AMOUNT"),
        Triple(GiftVoucherMerchantNotFoundException("Not found"), HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND"),
        Triple(GiftVoucherProductNotFoundException("Not found"), HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND"),
        Triple(GiftVoucherProductUnavailableException("Conflict"), HttpStatus.CONFLICT, "PRODUCT_OUT_OF_STOCK"),
        Triple(GiftVoucherNotExtendableException("Unprocessable"), HttpStatus.UNPROCESSABLE_ENTITY, "GIFT_VOUCHER_NOT_EXTENDABLE"),
        Triple(GiftVoucherAlreadyExtendedException("Conflict"), HttpStatus.CONFLICT, "GIFT_VOUCHER_ALREADY_EXTENDED"),
        Triple(InsufficientFundsException("Insufficient funds"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS"),
        Triple(IdempotencyConflictException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val service = mockk<GiftVoucherService>()
            val idempotencyService = mockk<IdempotencyService>()
            val controller = controller(service, idempotencyService)

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is GiftVoucherNotFoundException -> controller.handleNotFound(exception)
                    is GiftVoucherNotActiveException -> controller.handleNotActive(exception)
                    is GiftVoucherExpiredException -> controller.handleExpired(exception)
                    is GiftVoucherSelfException -> controller.handleSelf(exception)
                    is GiftVoucherNoAccountException -> controller.handleNoAccount(exception)
                    is GiftVoucherRecipientNotFoundException -> controller.handleRecipientNotFound(exception)
                    is GiftVoucherInvalidAmountException -> controller.handleInvalidAmount(exception)
                    is GiftVoucherMerchantNotFoundException -> controller.handleMerchantNotFound(exception)
                    is GiftVoucherProductNotFoundException -> controller.handleProductNotFound(exception)
                    is GiftVoucherProductUnavailableException -> controller.handleProductUnavailable(exception)
                    is GiftVoucherNotExtendableException -> controller.handleNotExtendable(exception)
                    is GiftVoucherAlreadyExtendedException -> controller.handleAlreadyExtended(exception)
                    is InsufficientFundsException -> controller.handleInsufficientFunds(exception)
                    is IdempotencyConflictException -> controller.handleConflict(exception)
                    is IdempotencyInProgressException -> controller.handleInProgress(exception)
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

    Given("a real MissingRequestHeaderException") {
        val service = mockk<GiftVoucherService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = controller(service, idempotencyService)
        When("its exception handler maps it to a real HTTP response") {
            val response = controller.handleMissingHeader(mockk<MissingRequestHeaderException>(relaxed = true))
            Then("it maps to 400 with code IDEMPOTENCY_KEY_REQUIRED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "IDEMPOTENCY_KEY_REQUIRED"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
