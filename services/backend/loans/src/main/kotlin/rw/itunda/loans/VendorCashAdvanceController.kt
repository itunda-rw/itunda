package rw.itunda.loans

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class ApplyForVendorCashAdvanceRequest(val merchantId: String)
data class RepayVendorCashAdvanceEarlyRequest(val amount: BigDecimal)

// Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see
// VendorCashAdvanceService's own doc comment for the full sourced account. Sourced
// beyond this session's usual Toss/Kakao/Naver/Coupang reference ecosystems, the same
// standing instruction as VupLoanController/StudentLoanController/CooperativeController
// above.
@RestController
@RequestMapping("/api/v1/vendor-advance")
class VendorCashAdvanceController(
    private val vendorCashAdvanceService: VendorCashAdvanceService,
    private val idempotencyService: IdempotencyService,
) {
    @GetMapping("/offer")
    fun getOffer(@RequestParam merchantId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true) + vendorCashAdvanceService.getOffer(currentUser.userId, merchantId))

    @PostMapping("/apply")
    fun apply(@RequestBody request: ApplyForVendorCashAdvanceRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val advance = vendorCashAdvanceService.applyForAdvance(currentUser.userId, request.merchantId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "advance" to advance))
    }

    @PostMapping("/{advanceId}/disburse")
    fun disburse(
        @PathVariable advanceId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/vendor-advance/$advanceId/disburse", idempotencyKey, currentUser.userId) {
            val advance = vendorCashAdvanceService.disburse(currentUser.userId, advanceId)
            200 to mapOf("success" to true, "advance" to advance)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/me")
    fun getMyAdvance(@RequestParam merchantId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "advance" to vendorCashAdvanceService.getMyAdvance(currentUser.userId, merchantId)))

    @GetMapping("/{advanceId}")
    fun getAdvance(@PathVariable advanceId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "advance" to vendorCashAdvanceService.getAdvance(currentUser.userId, advanceId)))

    @GetMapping("/{advanceId}/collection-history")
    fun getCollectionHistory(@PathVariable advanceId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true) + vendorCashAdvanceService.getCollectionHistory(currentUser.userId, advanceId))

    @PostMapping("/{advanceId}/repay-early")
    fun repayEarly(
        @PathVariable advanceId: String,
        @RequestBody request: RepayVendorCashAdvanceEarlyRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/vendor-advance/$advanceId/repay-early", idempotencyKey, request) {
            val advance = vendorCashAdvanceService.repayEarly(currentUser.userId, advanceId, request.amount)
            200 to mapOf("success" to true, "advance" to advance)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(VendorCashAdvanceNotFoundException::class)
    fun handleNotFound(ex: VendorCashAdvanceNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("VENDOR_CASH_ADVANCE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(VendorCashAdvanceNoAccountException::class)
    fun handleNoAccount(ex: VendorCashAdvanceNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(VendorCashAdvanceNotRequestedException::class)
    fun handleNotRequested(ex: VendorCashAdvanceNotRequestedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("VENDOR_CASH_ADVANCE_NOT_REQUESTED", ex.message ?: "Conflict"))

    @ExceptionHandler(VendorCashAdvanceNotRepayableException::class)
    fun handleNotRepayable(ex: VendorCashAdvanceNotRepayableException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("VENDOR_CASH_ADVANCE_NOT_REPAYABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(VendorCashAdvanceAlreadyActiveException::class)
    fun handleAlreadyActive(ex: VendorCashAdvanceAlreadyActiveException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("VENDOR_CASH_ADVANCE_ALREADY_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(VendorCashAdvanceNotEligibleException::class)
    fun handleNotEligible(ex: VendorCashAdvanceNotEligibleException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("VENDOR_CASH_ADVANCE_NOT_ELIGIBLE", ex.message ?: "Bad request"))

    @ExceptionHandler(VendorCashAdvanceInvalidAmountException::class)
    fun handleInvalidAmount(ex: VendorCashAdvanceInvalidAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REPAY_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))
}
