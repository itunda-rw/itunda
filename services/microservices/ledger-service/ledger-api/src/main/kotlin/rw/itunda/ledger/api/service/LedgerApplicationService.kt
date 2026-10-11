package rw.itunda.ledger.api.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import rw.itunda.ledger.api.controller.TransferRequest

private const val MAX_IDEMPOTENCY_KEY_LENGTH = 300
private const val TRANSFER_ENDPOINT_SCOPE = "POST:/api/v1/ledger/transfer:"

@Service
class LedgerApplicationService(
    private val idempotencyClaimStore: LedgerIdempotencyClaimStore,
    private val transferExecutor: LedgerTransferExecutor,
    private val objectMapper: ObjectMapper
) {
    fun executeTransfer(request: TransferRequest): String {
        if (request.idempotencyKey.isBlank() || request.idempotencyKey.length > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED_OR_INVALID")
        }
        if (request.sourceAccountId.isBlank() || request.destinationAccountId.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ACCOUNT_ID_REQUIRED")
        }

        val recordKey = TRANSFER_ENDPOINT_SCOPE + request.idempotencyKey
        // Canonicalize decimal amounts so semantically equal JSON numbers such as
        // 25.0 and 25.00 do not spuriously conflict on a retry.
        val requestJson = objectMapper.writeValueAsString(
            mapOf(
                "sourceAccountId" to request.sourceAccountId,
                "destinationAccountId" to request.destinationAccountId,
                "amount" to request.amount.stripTrailingZeros().toPlainString(),
                "idempotencyKey" to request.idempotencyKey
            )
        )

        return when (val claim = idempotencyClaimStore.claim(recordKey, requestJson)) {
            LedgerIdempotencyClaim.Conflict ->
                throw ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT")
            LedgerIdempotencyClaim.InProgress ->
                throw ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING")
            is LedgerIdempotencyClaim.Replay -> claim.transactionId
            LedgerIdempotencyClaim.Claimed -> {
                try {
                    transferExecutor.executeTransfer(request, recordKey)
                } catch (rejected: TransferRejectedException) {
                    // This exception is emitted only by validation paths that run
                    // before account/outbox writes. Keep claims on other exceptions:
                    // a database commit error can have an ambiguous outcome, and
                    // deleting a possibly committed key could permit a duplicate debit.
                    try {
                        idempotencyClaimStore.release(recordKey)
                    } catch (releaseFailure: Exception) {
                        rejected.addSuppressed(releaseFailure)
                    }
                    throw rejected
                }
            }
        }
    }
}
