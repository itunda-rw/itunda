package rw.itunda.wallet.transfer.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import rw.itunda.wallet.transfer.service.TransferRequest
import rw.itunda.wallet.transfer.service.TransferResponse
import rw.itunda.wallet.transfer.service.TransferService

@RestController
@RequestMapping("/api/v1/transfers")
class TransferController(
    private val transferService: TransferService
) {

    /**
     * POST /api/v1/transfers
     * Triggered by the "Pay" or "Transfer" button in the Itunda Native App.
     */
    @PostMapping
    fun initiateTransfer(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: TransferDto
    ): ResponseEntity<TransferResponse> {
        
        // Map DTO to internal Request object, injecting the Idempotency Key
        val transferRequest = TransferRequest(
            senderAccountId = request.senderAccountId,
            receiverAccountId = request.receiverAccountId,
            amount = request.amount,
            message = request.message,
            idempotencyKey = idempotencyKey
        )

        val response = transferService.executeP2PTransfer(transferRequest)
        return ResponseEntity.ok(response)
    }
}

data class TransferDto(
    val senderAccountId: String,
    val receiverAccountId: String,
    val amount: java.math.BigDecimal,
    val message: String?
)
