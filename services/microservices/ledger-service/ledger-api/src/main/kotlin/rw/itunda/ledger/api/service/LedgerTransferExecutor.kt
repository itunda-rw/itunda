package rw.itunda.ledger.api.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.ledger.api.controller.TransferRequest
import rw.itunda.ledger.domain.LedgerAccountRepository
import rw.itunda.ledger.domain.LedgerIdempotencyRepository
import rw.itunda.ledger.domain.OutboxEvent
import rw.itunda.ledger.domain.OutboxEventRepository
import rw.itunda.ledger.domain.TransferService
import java.util.UUID

@Service
class LedgerTransferExecutor(
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val idempotencyRepository: LedgerIdempotencyRepository,
    private val objectMapper: ObjectMapper
) {
    private val transferService = TransferService()

    @Transactional
    fun executeTransfer(request: TransferRequest, idempotencyRecordKey: String): String {
        require(request.sourceAccountId != request.destinationAccountId) {
            "Source and destination accounts must be different"
        }

        // Lock both rows in deterministic order to serialize competing transfers
        // and reduce opposite-direction deadlocks.
        val lockedAccounts = listOf(
            request.sourceAccountId,
            request.destinationAccountId
        ).sorted().associateWith { accountId ->
            ledgerAccountRepository.findByIdForUpdate(accountId)
                ?: throw IllegalArgumentException(
                    if (accountId == request.sourceAccountId) {
                        "Source account not found"
                    } else {
                        "Destination account not found"
                    }
                )
        }
        val source = lockedAccounts.getValue(request.sourceAccountId)
        val destination = lockedAccounts.getValue(request.destinationAccountId)

        val (updatedSource, updatedDestination) = transferService.executeTransfer(
            source = source,
            destination = destination,
            amount = request.amount
        )
        ledgerAccountRepository.saveAll(listOf(updatedSource, updatedDestination))

        val transactionId = "TXN_RW_${UUID.randomUUID().toString().take(8)}"
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "transactionId" to transactionId,
                "sourceAccountId" to request.sourceAccountId,
                "destinationAccountId" to request.destinationAccountId,
                "amount" to request.amount,
                "currency" to source.currency,
                "idempotencyKey" to request.idempotencyKey
            )
        )
        outboxEventRepository.save(
            OutboxEvent(
                eventId = UUID.randomUUID().toString(),
                aggregateType = "LEDGER_TRANSFER",
                aggregateId = transactionId,
                payload = payload
            )
        )

        // This completion shares the transaction with balance and outbox writes.
        // A crash cannot commit money movement while leaving a successful request
        // permanently marked as processing.
        check(idempotencyRepository.complete(idempotencyRecordKey, 200, transactionId) == 1) {
            "Transfer idempotency claim was lost before commit"
        }

        return transactionId
    }
}
