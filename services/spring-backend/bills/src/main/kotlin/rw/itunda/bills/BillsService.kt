package rw.itunda.bills

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.domain.WalletType
import java.math.BigDecimal
import java.time.Instant

class NoWalletException(message: String) : RuntimeException(message)

/**
 * Port of backend/src/controllers/bills.controller.ts's payBill/buyAirtime. Same
 * omission as the Express version carries forward faithfully: these don't create a
 * persisted Transaction row, only ledger legs — bills/airtime never went through the
 * same transaction-history path as P2P transfers there either. Not yet ported: rail
 * inference and the simulated provider-connector call (backend/src/services/rails.ts,
 * providerConnectors.ts) — every payment here always succeeds against a flat rail.
 */
@Service
class BillsService(
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
) {
    fun getProviders() = BillsCatalog.providers
    fun getPendingBills() = BillsCatalog.pendingBills

    @Transactional
    fun payBill(userId: String, billId: String, amount: BigDecimal, accountNumber: String?): Map<String, Any?> {
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw NoWalletException("No wallet found for this account")
        val result = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Bill payment $billId"),
                LedgerLeg("rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, amount, "Biller settlement $billId"),
            ),
        )
        return mapOf(
            "id" to result.transactionId,
            "referenceNumber" to "BILL${System.currentTimeMillis()}",
            "amount" to amount,
            "fee" to BigDecimal.ZERO,
            "type" to "BILL",
            "status" to "COMPLETED",
            "description" to "Bill payment - $billId${accountNumber?.let { " ($it)" } ?: ""}",
            "completedAt" to Instant.now().toString(),
        )
    }

    @Transactional
    fun buyAirtime(userId: String, phoneNumber: String, amount: BigDecimal, provider: String?): Map<String, Any?> {
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw NoWalletException("No wallet found for this account")
        val result = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Airtime $phoneNumber"),
                LedgerLeg("rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, amount, "Airtime settlement $phoneNumber"),
            ),
        )
        return mapOf(
            "id" to result.transactionId,
            "referenceNumber" to "AIR${System.currentTimeMillis()}",
            "amount" to amount,
            "fee" to BigDecimal.ZERO,
            "type" to "AIRTIME",
            "status" to "COMPLETED",
            "description" to "${provider ?: "MTN"} Airtime - $phoneNumber",
            "completedAt" to Instant.now().toString(),
        )
    }
}
