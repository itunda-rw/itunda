package rw.itunda.ledger.api.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.ledger.api.controller.TransferRequest
import rw.itunda.ledger.domain.LedgerAccountRepository
import rw.itunda.ledger.domain.OutboxEvent
import rw.itunda.ledger.domain.OutboxEventRepository
import rw.itunda.ledger.domain.TransferService
import java.util.UUID

@Service
class LedgerApplicationService(
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val outboxEventRepository: OutboxEventRepository
) {
    private val transferService = TransferService()

    @Transactional
    fun executeTransfer(request: TransferRequest): String {
        require(request.sourceAccountId != request.destinationAccountId) {
            "Source and destination accounts must be different"
        }

        // Lock both rows in deterministic order. Every competing transfer touching
        // either account therefore serializes against the same database rows, while
        // deterministic ordering reduces opposite-direction transfer deadlocks.
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

        // Execute pure business logic only after the latest balances are locked/read.
        val (updatedSource, updatedDestination) = transferService.executeTransfer(
            source = source,
            destination = destination,
            amount = request.amount
        )

        ledgerAccountRepository.saveAll(listOf(updatedSource, updatedDestination))

        // Persist the event in the same transaction as the balance changes.
        val transactionId = "TXN_RW_${UUID.randomUUID().toString().take(8)}"
        val outboxEvent = OutboxEvent(
            eventId = UUID.randomUUID().toString(),
            aggregateType = "LEDGER_TRANSFER",
            aggregateId = transactionId,
            payload = """
                {
                    "transactionId": "$transactionId",
                    "sourceAccountId": "${request.sourceAccountId}",
                    "destinationAccountId": "${request.destinationAccountId}",
                    "amount": ${request.amount},
                    "currency": "${source.currency}",
                    "idempotencyKey": "${request.idempotencyKey}"
                }
            """.trimIndent()
        )
        outboxEventRepository.save(outboxEvent)

        return transactionId
    }
}
