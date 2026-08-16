package rw.itunda.bills

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.BillAutoPaySetting
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
import rw.itunda.core.repository.BillAutoPaySettingRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.domain.WalletType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class NoWalletException(message: String) : RuntimeException(message)
class BillProviderNotFoundException(message: String) : RuntimeException(message)

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
    private val billAutoPaySettingRepository: BillAutoPaySettingRepository,
) {
    private val log = LoggerFactory.getLogger(BillsService::class.java)

    fun getProviders() = BillsCatalog.providers
    fun getPendingBills() = BillsCatalog.pendingBills

    /** Real Kakao Pay 자동납부 -- register (or replace, via `uq_bill_auto_pay_user_provider`)
     * a recurring auto-pay for one provider. `maxAmount` is the real sourced safety cap:
     * a due bill over this is skipped by [processAutoPayments], never silently auto-charged. */
    fun setAutoPay(userId: String, providerId: String, accountNumber: String, maxAmount: BigDecimal): BillAutoPaySetting {
        BillsCatalog.providers.find { it.id == providerId } ?: throw BillProviderNotFoundException("Unknown bill provider $providerId")
        val existing = billAutoPaySettingRepository.findByUserIdAndProviderId(userId, providerId)
        val setting = existing?.also {
            it.accountNumber = accountNumber
            it.maxAmount = maxAmount
            it.active = true
        } ?: BillAutoPaySetting(
            id = UUID.randomUUID().toString(),
            userId = userId,
            providerId = providerId,
            accountNumber = accountNumber,
            maxAmount = maxAmount,
        )
        return billAutoPaySettingRepository.save(setting)
    }

    fun clearAutoPay(userId: String, providerId: String) {
        val setting = billAutoPaySettingRepository.findByUserIdAndProviderId(userId, providerId) ?: return
        setting.active = false
        billAutoPaySettingRepository.save(setting)
    }

    fun getAutoPaySettings(userId: String) = billAutoPaySettingRepository.findByUserId(userId)

    /** Real Kakao Pay 자동납부 poll -- exposed as a manually-callable endpoint (same
     * "expose scheduler logic as a real POST" convention as `WeeklySavingsController.processDue`)
     * so this can be live-verified without waiting real wall-clock time. For every active
     * setting: resolve its real [rw.itunda.bills.BillProvider] name, find the matching
     * static [BillsCatalog.pendingBills] entry by provider name + account number, skip if
     * it's over the user's `maxAmount` cap or already paid (`lastPaidBillId` guard --
     * `BillsCatalog.pendingBills` never changes state on its own), otherwise reuse the
     * real [payBill] money-movement path and record the guard. */
    @Transactional
    fun processAutoPayments(): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        for (setting in billAutoPaySettingRepository.findByActiveTrue()) {
            val provider = BillsCatalog.providers.find { it.id == setting.providerId } ?: continue
            val pendingBill = BillsCatalog.pendingBills.find {
                it.provider == provider.name && it.accountNumber == setting.accountNumber
            } ?: continue
            if (pendingBill.id == setting.lastPaidBillId) continue
            if (BigDecimal(pendingBill.amount) > setting.maxAmount) continue

            // Real per-user resilience -- this whole method is one @Transactional unit
            // (payBill's own @Transactional has no effect on internal self-invocation, a
            // well-known Spring pitfall), so an uncaught InsufficientFundsException or
            // ProviderDeclinedException for one user here would silently roll back every
            // other user's already-processed auto-payment in the same sweep. Caught and
            // logged instead, same per-row resilience discipline
            // OrderAcceptanceExpiryScheduler/StockPriceAlertScheduler's own schedulers
            // already establish for an identical class of "one bad row" risk.
            try {
                val payment = payBill(setting.userId, pendingBill.id, BigDecimal(pendingBill.amount), setting.accountNumber, null)
                setting.lastPaidBillId = pendingBill.id
                billAutoPaySettingRepository.save(setting)
                results.add(payment + mapOf("providerId" to provider.id, "billId" to pendingBill.id))
            } catch (e: Exception) {
                log.error("Auto-pay failed for user {} provider {}: {}", setting.userId, provider.id, e.message)
            }
        }
        return results
    }

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
