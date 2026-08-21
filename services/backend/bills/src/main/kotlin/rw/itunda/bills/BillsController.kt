package rw.itunda.bills

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
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
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class PayBillRequest(val billId: String, val amount: BigDecimal, val accountNumber: String? = null, val provider: String? = null)
data class BuyAirtimeRequest(val phoneNumber: String, val amount: BigDecimal, val provider: String? = null)
data class SetAutoPayRequest(val providerId: String, val accountNumber: String, val maxAmount: BigDecimal)

@RestController
@RequestMapping("/api/v1/bills")
class BillsController(
    private val billsService: BillsService,
    private val billAutoPayProcessor: BillAutoPayProcessor,
    private val idempotencyService: IdempotencyService,
) {

    @GetMapping("/providers")
    fun getProviders() = ResponseEntity.ok(mapOf("success" to true, "providers" to billsService.getProviders()))

    @GetMapping("/pending")
    fun getPending() = ResponseEntity.ok(mapOf("success" to true, "bills" to billsService.getPendingBills()))

    @PostMapping("/pay")
    fun payBill(
        @RequestBody request: PayBillRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/bills/pay", idempotencyKey, request) {
            val transaction = billsService.payBill(currentUser.userId, request.billId, request.amount, request.accountNumber, request.provider)
            200 to mapOf("success" to true, "message" to "Bill payment successful", "transaction" to transaction)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/airtime")
    fun buyAirtime(
        @RequestBody request: BuyAirtimeRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/bills/airtime", idempotencyKey, request) {
            val transaction = billsService.buyAirtime(currentUser.userId, request.phoneNumber, request.amount, request.provider)
            200 to mapOf("success" to true, "message" to "Airtime of ${request.amount} RWF sent to ${request.phoneNumber}", "transaction" to transaction)
        }
        return ResponseEntity.status(status).body(body)
    }

    /** Real Kakao Pay 자동납부 -- register recurring auto-pay for one provider. */
    @PostMapping("/auto-pay")
    fun setAutoPay(@RequestBody request: SetAutoPayRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val setting = billsService.setAutoPay(currentUser.userId, request.providerId, request.accountNumber, request.maxAmount)
        return ResponseEntity.ok(mapOf("success" to true, "autoPay" to setting))
    }

    @GetMapping("/auto-pay")
    fun getAutoPay(@AuthenticationPrincipal currentUser: CurrentUser) = ResponseEntity.ok(mapOf("success" to true, "autoPay" to billsService.getAutoPaySettings(currentUser.userId)))

    @DeleteMapping("/auto-pay")
    fun clearAutoPay(@RequestParam providerId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        billsService.clearAutoPay(currentUser.userId, providerId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // ADMIN-gated same as WeeklySavingsController.processDue -- this runs auto-pay for every
    // user with a due, in-cap bill, never scoped to the caller's own account.
    @PostMapping("/process-auto-payments")
    @PreAuthorize("hasRole('ADMIN')")
    fun processAutoPayments(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = billAutoPayProcessor.process()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    @ExceptionHandler(BillProviderNotFoundException::class)
    fun handleProviderNotFound(ex: BillProviderNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BILL_PROVIDER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(NoAccountException::class)
    fun handleNoAccount(ex: NoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(ProviderDeclinedException::class)
    fun handleProviderDeclined(ex: ProviderDeclinedException) = ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError("PROVIDER_DECLINED", ex.message ?: "Provider declined"))
}
