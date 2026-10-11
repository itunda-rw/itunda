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

        val recordKey = TRANSFER_ENDPOINT_SCOPE + request.idempotencyKey
        val requestJson = objectMapper.writeValueAsString(request)

        return when (val claim = idempotencyClaimStore.claim(recordKey, requestJson)) {
            LedgerIdempotencyClaim.Conflict ->
                throw ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT")
            LedgerIdempotencyClaim.InProgress ->
                throw ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING")
            is LedgerIdempotencyClaim.Replay -> claim.transactionId
            LedgerIdempotencyClaim.Claimed -> {
                try {
                    transferExecutor.executeTransfer(request, recordKey)
                } catch (failure: Exception) {
                    try {
                        idempotencyClaimStore.release(recordKey)
                    } catch (releaseFailure: Exception) {
                        failure.addSuppressed(releaseFailure)
                    }
                    throw failure
                }
            }
        }
    }
}
