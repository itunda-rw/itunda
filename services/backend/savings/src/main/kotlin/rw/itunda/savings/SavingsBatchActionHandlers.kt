package rw.itunda.savings

import org.springframework.stereotype.Component
import rw.itunda.core.batch.BatchActionHandler
import rw.itunda.core.batch.optionalString
import rw.itunda.core.batch.requiredBigDecimal
import rw.itunda.core.batch.requiredString
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException

@Component
class SavingsDepositBatchActionHandler(
    private val savingsService: SavingsService,
    private val idempotencyService: IdempotencyService,
) : BatchActionHandler {
    override val actionType = "SAVINGS_DEPOSIT"

    override fun handle(userId: String, idempotencyKey: String, body: Map<String, Any?>): Pair<Int, Map<String, Any?>> = try {
        val goalId = body.requiredString("goalId")
        val amount = body.requiredBigDecimal("amount")
        val fromWalletId = body.optionalString("fromWalletId")
        idempotencyService.replayOrExecute("POST /api/v1/savings/deposit", idempotencyKey, body) {
            val goal = savingsService.depositToGoal(userId, goalId, amount, fromWalletId)
            200 to mapOf("success" to true, "message" to "Deposited $amount RWF to \"${goal.name}\"", "goal" to goal)
        }
    } catch (e: Exception) {
        when (e) {
            is IdempotencyConflictException -> 409 to mapOf("success" to false, "error" to mapOf("code" to "IDEMPOTENCY_KEY_CONFLICT", "message" to e.message))
            is IdempotencyInProgressException -> 409 to mapOf("success" to false, "error" to mapOf("code" to "IDEMPOTENT_REQUEST_PROCESSING", "message" to e.message))
            is GoalNotFoundException -> 404 to mapOf("success" to false, "error" to mapOf("code" to "GOAL_NOT_FOUND", "message" to e.message))
            is NoWalletException -> 404 to mapOf("success" to false, "error" to mapOf("code" to "WALLET_NOT_FOUND", "message" to e.message))
            is WalletNotOwnedException -> 403 to mapOf("success" to false, "error" to mapOf("code" to "WALLET_NOT_OWNED", "message" to e.message))
            is InsufficientFundsException -> 422 to mapOf("success" to false, "error" to mapOf("code" to "INSUFFICIENT_FUNDS", "message" to e.message))
            is IllegalArgumentException -> 400 to mapOf("success" to false, "error" to mapOf("code" to "INVALID_ACTION_BODY", "message" to e.message))
            else -> throw e
        }
    }
}
