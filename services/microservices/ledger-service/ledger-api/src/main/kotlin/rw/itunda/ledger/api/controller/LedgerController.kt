package rw.itunda.ledger.api.controller

import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/ledger")
class LedgerController(
    private val ledgerApplicationService: rw.itunda.ledger.api.service.LedgerApplicationService
) {

    @PostMapping("/transfer")
    fun transfer(@RequestBody request: TransferRequest): Map<String, Any> {
        // Implementation of the Toss Bank style transfer coordination
        val transactionId = ledgerApplicationService.executeTransfer(request)
        
        return mapOf(
            "status" to "SUCCESS",
            "transactionId" to transactionId,
            "amount" to request.amount,
            "currency" to "RWF"
        )
    }
}

data class TransferRequest(
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amount: BigDecimal,
    val idempotencyKey: String // Critical for banking APIs
)
