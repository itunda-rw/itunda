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
        // Toss Pattern: Fetch Domain Models
        val source = ledgerAccountRepository.findById(request.sourceAccountId)
            ?: throw IllegalArgumentException("Source account not found")
        val destination = ledgerAccountRepository.findById(request.destinationAccountId)
            ?: throw IllegalArgumentException("Destination account not found")

        // Toss Pattern: Execute Pure Business Logic in Domain Layer
        val (updatedSource, updatedDestination) = transferService.executeTransfer(
            source = source,
            destination = destination,
            amount = request.amount
        )

        // Toss Pattern: Save State
        ledgerAccountRepository.saveAll(listOf(updatedSource, updatedDestination))

        // Toss Pattern: Transactional Outbox for Kafka
        val transactionId = "TXN_RW_${UUID.randomUUID().toString().take(8)}"
        
        // This event will be swept up by a background Kafka Connect/Debezium worker or Spring Scheduler
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
