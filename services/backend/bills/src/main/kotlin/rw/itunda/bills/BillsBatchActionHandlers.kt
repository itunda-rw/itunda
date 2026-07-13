package rw.itunda.bills

import org.springframework.stereotype.Component
import rw.itunda.core.batch.BatchActionHandler
import rw.itunda.core.batch.optionalString
import rw.itunda.core.batch.requiredBigDecimal
import rw.itunda.core.batch.requiredString
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.provider.ProviderDeclinedException

private fun mapError(e: Exception): Pair<Int, Map<String, Any?>> = when (e) {
    is IdempotencyConflictException -> 409 to mapOf("success" to false, "error" to mapOf("code" to "IDEMPOTENCY_KEY_CONFLICT", "message" to e.message))
    is IdempotencyInProgressException -> 409 to mapOf("success" to false, "error" to mapOf("code" to "IDEMPOTENT_REQUEST_PROCESSING", "message" to e.message))
    is NoWalletException -> 404 to mapOf("success" to false, "error" to mapOf("code" to "WALLET_NOT_FOUND", "message" to e.message))
    is InsufficientFundsException -> 422 to mapOf("success" to false, "error" to mapOf("code" to "INSUFFICIENT_FUNDS", "message" to e.message))
    is WalletFrozenException -> 403 to mapOf("success" to false, "error" to mapOf("code" to "WALLET_FROZEN", "message" to e.message))
    is ProviderDeclinedException -> 502 to mapOf("success" to false, "error" to mapOf("code" to "PROVIDER_DECLINED", "message" to e.message))
    is IllegalArgumentException -> 400 to mapOf("success" to false, "error" to mapOf("code" to "INVALID_ACTION_BODY", "message" to e.message))
    else -> throw e
}

@Component
class PayBillBatchActionHandler(
    private val billsService: BillsService,
    private val idempotencyService: IdempotencyService,
) : BatchActionHandler {
    override val actionType = "BILL_PAY"

    override fun handle(userId: String, idempotencyKey: String, body: Map<String, Any?>): Pair<Int, Map<String, Any?>> = try {
        val billId = body.requiredString("billId")
        val amount = body.requiredBigDecimal("amount")
        val accountNumber = body.optionalString("accountNumber")
        val provider = body.optionalString("provider")
        idempotencyService.replayOrExecute("POST /api/v1/bills/pay", idempotencyKey, body) {
            val transaction = billsService.payBill(userId, billId, amount, accountNumber, provider)
            200 to mapOf("success" to true, "message" to "Bill payment successful", "transaction" to transaction)
        }
    } catch (e: Exception) {
        mapError(e)
    }
}

@Component
class BuyAirtimeBatchActionHandler(
    private val billsService: BillsService,
    private val idempotencyService: IdempotencyService,
) : BatchActionHandler {
    override val actionType = "BUY_AIRTIME"

    override fun handle(userId: String, idempotencyKey: String, body: Map<String, Any?>): Pair<Int, Map<String, Any?>> = try {
        val phoneNumber = body.requiredString("phoneNumber")
        val amount = body.requiredBigDecimal("amount")
        val provider = body.optionalString("provider")
        idempotencyService.replayOrExecute("POST /api/v1/bills/airtime", idempotencyKey, body) {
            val transaction = billsService.buyAirtime(userId, phoneNumber, amount, provider)
            200 to mapOf("success" to true, "message" to "Airtime of $amount RWF sent to $phoneNumber", "transaction" to transaction)
        }
    } catch (e: Exception) {
        mapError(e)
    }
}
