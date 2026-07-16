package rw.itunda.bills

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.events.EventPublisher
import rw.itunda.core.events.PaymentProviderFailedEvent
import rw.itunda.core.events.PaymentProviderSucceededEvent
import rw.itunda.core.events.TOPIC_PAYMENT_PROVIDER_FAILED
import rw.itunda.core.events.TOPIC_PAYMENT_PROVIDER_SUCCEEDED
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailCatalog
import rw.itunda.core.provider.RailProfile
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.domain.WalletType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class NoWalletException(message: String) : RuntimeException(message)

/**
 * Port of backend/src/controllers/bills.controller.ts's payBill/buyAirtime.
 *
 * Fixed (2026-07-11): every payment here previously always succeeded against a flat
 * rail with no provider simulation at all -- explicitly flagged open in
 * docs/TOSS_RWANDA_ALIGNMENT.md's gap list ("Replace mocked provider calls with
 * provider connector interfaces and typed fake providers"). Now calls
 * [ProviderConnector.attempt] *before* touching the ledger -- see
 * core/.../provider/ProviderConnector.kt's own doc comment for why these are
 * rebuilt fresh rather than a literal port (the Express file they mirror the design
 * of no longer exists in this repo).
 *
 * Fixed (2026-07-16): the omission this file's doc comment used to name here ("these
 * don't create a persisted Transaction row, only ledger legs") is closed -- both flows
 * now save a real `Transaction` row, same shape/convention as
 * `WalletService.confirmTransfer`. Found while wiring rewards-task verification
 * (RewardsService's `task_first_bill`): without a real Transaction row there was
 * nothing for a "has this user ever paid a bill" query to check, and bill/airtime
 * payments were invisible to `GET /wallet/transactions` the same way merchant
 * collections were before that gap was closed (see the Merchant row in
 * docs/TOSS_PARITY_MATRIX.md) -- the identical class of bug, same fix.
 */
@Service
class BillsService(
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val providerConnector: ProviderConnector,
    private val eventPublisher: EventPublisher,
    private val transactionRepository: TransactionRepository,
) {
    fun getProviders() = BillsCatalog.providers
    fun getPendingBills() = BillsCatalog.pendingBills

    /** Wraps [ProviderConnector.attempt] so a decline publishes `payment.provider_failed`
     * before rethrowing. Published via [EventPublisher.publishImmediately] rather than
     * [EventPublisher.publishAfterCommit] -- the caller's @Transactional method is about
     * to roll back once this exception propagates, so an afterCommit hook would never
     * fire for it. */
    private fun attemptOrPublishFailure(rail: RailProfile, description: String, amount: BigDecimal, currency: String) {
        try {
            providerConnector.attempt(rail, description)
        } catch (e: ProviderDeclinedException) {
            eventPublisher.publishImmediately(
                TOPIC_PAYMENT_PROVIDER_FAILED,
                rail.id,
                PaymentProviderFailedEvent(
                    railId = rail.id,
                    railDisplayName = rail.displayName,
                    description = description,
                    amount = amount,
                    currency = currency,
                    reason = e.message ?: "declined",
                    failedAt = Instant.now(),
                ),
            )
            throw e
        }
    }

    @Transactional
    fun payBill(userId: String, billId: String, amount: BigDecimal, accountNumber: String?, provider: String? = null): Map<String, Any?> {
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw NoWalletException("No wallet found for this account")

        val rail = RailCatalog.resolve(provider)
        attemptOrPublishFailure(rail, "Bill payment $billId", amount, wallet.currency)

        val result = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Bill payment $billId"),
                LedgerLeg("rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, amount, "Biller settlement $billId"),
            ),
        )
        val referenceNumber = "BILL${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}"
        val completedAt = Instant.now()
        val description = "Bill payment - $billId${accountNumber?.let { " ($it)" } ?: ""}"
        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = referenceNumber,
                senderId = userId,
                recipientId = "external",
                fromWalletId = wallet.id,
                amount = amount,
                fee = BigDecimal.ZERO,
                currency = wallet.currency,
                type = TransactionType.BILL,
                status = TransactionStatus.COMPLETED,
                description = description,
                completedAt = completedAt,
            ),
        )
        eventPublisher.publishAfterCommit(
            TOPIC_PAYMENT_PROVIDER_SUCCEEDED,
            result.transactionId,
            PaymentProviderSucceededEvent(
                transactionId = result.transactionId,
                railId = rail.id,
                railDisplayName = rail.displayName,
                description = "Bill payment $billId",
                amount = amount,
                currency = wallet.currency,
                succeededAt = Instant.now(),
            ),
        )
        return mapOf(
            "id" to result.transactionId,
            "referenceNumber" to referenceNumber,
            "amount" to amount,
            "fee" to BigDecimal.ZERO,
            "type" to "BILL",
            "status" to "COMPLETED",
            "description" to description,
            "completedAt" to completedAt.toString(),
        )
    }

    @Transactional
    fun buyAirtime(userId: String, phoneNumber: String, amount: BigDecimal, provider: String?): Map<String, Any?> {
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw NoWalletException("No wallet found for this account")

        val rail = RailCatalog.resolve(provider)
        attemptOrPublishFailure(rail, "Airtime $phoneNumber", amount, wallet.currency)

        val result = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Airtime $phoneNumber"),
                LedgerLeg("rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, amount, "Airtime settlement $phoneNumber"),
            ),
        )
        val referenceNumber = "AIR${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}"
        val completedAt = Instant.now()
        val description = "${provider ?: "MTN"} Airtime - $phoneNumber"
        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = referenceNumber,
                senderId = userId,
                recipientId = "external",
                fromWalletId = wallet.id,
                amount = amount,
                fee = BigDecimal.ZERO,
                currency = wallet.currency,
                type = TransactionType.AIRTIME,
                status = TransactionStatus.COMPLETED,
                description = description,
                completedAt = completedAt,
            ),
        )
        eventPublisher.publishAfterCommit(
            TOPIC_PAYMENT_PROVIDER_SUCCEEDED,
            result.transactionId,
            PaymentProviderSucceededEvent(
                transactionId = result.transactionId,
                railId = rail.id,
                railDisplayName = rail.displayName,
                description = "Airtime $phoneNumber",
                amount = amount,
                currency = wallet.currency,
                succeededAt = Instant.now(),
            ),
        )
        return mapOf(
            "id" to result.transactionId,
            "referenceNumber" to referenceNumber,
            "amount" to amount,
            "fee" to BigDecimal.ZERO,
            "type" to "AIRTIME",
            "status" to "COMPLETED",
            "description" to description,
            "completedAt" to completedAt.toString(),
        )
    }
}
