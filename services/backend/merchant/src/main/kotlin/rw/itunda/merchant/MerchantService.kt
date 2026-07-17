package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent
import rw.itunda.core.domain.PaymentIntentStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.PaymentIntentRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class MerchantAlreadyRegisteredException(message: String) : RuntimeException(message)
class MerchantNotFoundException(message: String) : RuntimeException(message)
class MerchantNoWalletException(message: String) : RuntimeException(message)
class PaymentIntentNotFoundException(message: String) : RuntimeException(message)
class PaymentIntentNotPayableException(message: String) : RuntimeException(message)
class SelfPaymentException(message: String) : RuntimeException(message)
class CardDeclinedException(message: String) : RuntimeException(message)

data class MerchantReportDay(
    val date: LocalDate,
    val collectionCount: Int,
    val grossAmount: BigDecimal,
    val fees: BigDecimal,
    val netAmount: BigDecimal,
    val byChannel: Map<String, Int>,
)

/**
 * A real, minimal subset of docs/MERCHANT_SERVICES.md's product surface --
 * registration + QR-style fixed-amount payment collection into the merchant's
 * settlement wallet, ledger-backed like every other money-moving flow in this
 * backend. Real production card processing needs actual PSP-level infrastructure
 * this repo has no path to certify, not more Kotlin -- but `chargeCard` below is a
 * real demo card-authorization flow (real Luhn validation, real ledger legs, a real
 * simulated decision), same "real simulation, not a real integration" bar every
 * other blocked-on-external-access flow in this backend already holds itself to.
 */
@Service
class MerchantService(
    private val merchantRepository: MerchantRepository,
    private val paymentIntentRepository: PaymentIntentRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val webhookDeliveryService: WebhookDeliveryService,
    private val transactionRepository: TransactionRepository,
    private val fraudRuleEngine: FraudRuleEngine,
    private val demoCardAuthorizationService: DemoCardAuthorizationService,
) {
    // Toss Payments' real published fee schedule tiers wallet-based payments
    // ("Toss Pay") at 0.8%-1.8% depending on merchant volume (see
    // docs/TOSS_ARCHITECTURE_FACTS.md's Toss Payments SDK research). No tiering
    // system exists here yet, so a single flat rate in the middle of that real
    // range is used rather than inventing a number the way the old
    // MERCHANT_SERVICES.md spec's "QR payments: 1.5%" did independently.
    private val feeRate = BigDecimal("0.015")

    @Transactional
    fun register(ownerUserId: String, businessName: String): Merchant {
        if (merchantRepository.findByOwnerUserId(ownerUserId) != null) {
            throw MerchantAlreadyRegisteredException("This account is already registered as a merchant")
        }
        val wallet = walletRepository.findByUserIdAndType(ownerUserId, WalletType.MAIN)
            ?: throw MerchantNoWalletException("No wallet found for this account")

        val merchant = Merchant(
            id = "merchant_${UUID.randomUUID()}",
            ownerUserId = ownerUserId,
            walletId = wallet.id,
            businessName = businessName,
            status = MerchantStatus.ACTIVE,
        )
        return merchantRepository.save(merchant)
    }

    fun getMyMerchant(ownerUserId: String): Merchant =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    @Transactional
    fun setWebhookUrl(ownerUserId: String, webhookUrl: String): Merchant {
        val merchant = getMyMerchant(ownerUserId)
        merchant.webhookUrl = webhookUrl
        return merchantRepository.save(merchant)
    }

    @Transactional
    fun generateQr(ownerUserId: String, amount: BigDecimal, description: String): PaymentIntent {
        val merchant = getMyMerchant(ownerUserId)
        val intent = PaymentIntent(
            id = "pi_${UUID.randomUUID()}",
            merchantId = merchant.id,
            amount = amount,
            description = description,
            expiresAt = Instant.now().plusSeconds(900),
        )
        return paymentIntentRepository.save(intent)
    }

    @Transactional
    fun collect(payerUserId: String, intentId: String, channel: String = "QR"): Map<String, Any?> {
        val intent = paymentIntentRepository.findById(intentId)
            .orElseThrow { PaymentIntentNotFoundException("Payment code not found") }
        if (intent.status != PaymentIntentStatus.PENDING) {
            throw PaymentIntentNotPayableException("This payment code has already been used")
        }
        if (intent.expiresAt.isBefore(Instant.now())) {
            intent.status = PaymentIntentStatus.EXPIRED
            paymentIntentRepository.save(intent)
            throw PaymentIntentNotPayableException("This payment code has expired")
        }

        val merchant = merchantRepository.findById(intent.merchantId)
            .orElseThrow { MerchantNotFoundException("Merchant not found") }
        if (merchant.ownerUserId == payerUserId) {
            throw SelfPaymentException("Cannot pay your own merchant QR code")
        }

        val payerWallet = walletRepository.findByUserIdAndType(payerUserId, WalletType.MAIN)
            ?: throw MerchantNoWalletException("No wallet found for this account")
        val merchantWallet = walletRepository.findById(merchant.walletId)
            .orElseThrow { MerchantNoWalletException("Merchant settlement wallet not found") }

        val fee = intent.amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)
        val netToMerchant = intent.amount.subtract(fee)
        // channel-labeled memo/description (2026-07-13, added for Face Pay) -- keeps a
        // real, honest audit trail of which authentication factor collected a given
        // payment (QR scan vs Face Pay biometric match) rather than always saying "QR".
        val channelLabel = if (channel == "FACE_PAY") "Face Pay" else "QR"

        val result = ledgerService.postLedgerTransaction(
            payerWallet.currency,
            listOf(
                LedgerLeg(payerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, intent.amount, "$channelLabel payment - ${merchant.businessName}"),
                LedgerLeg(merchantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "$channelLabel collection - ${intent.description}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "$channelLabel payment fee - ${merchant.businessName}"),
            ),
        )

        // Real Transaction row + fraud review wired in (2026-07-13) -- this method
        // previously only posted ledger legs and never wrote a Transaction row at all
        // (confirmed live: TransactionRepository wasn't even injected here). That's a
        // real, separate gap beyond just fraud review: WalletService.getTransactionHistory
        // and FraudRuleEngine's own VELOCITY/NEW_RECIPIENT rules both key off the
        // transactions table, so merchant payments were invisible to both a payer's/
        // merchant's own transaction history *and* to fraud history checks for every
        // other flow -- a repeat-merchant-payment could never trigger VELOCITY, and a
        // brand-new merchant recipient could never be flagged NEW_RECIPIENT. Persisting
        // this row here, before the fraud evaluate() call (same ordering reasoning as
        // P2pService.payRequest and WalletService.confirmTransfer: evaluating after the
        // save would let this transaction match itself as prior history), fixes both.
        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "MERC${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = payerUserId,
            recipientId = merchant.ownerUserId,
            fromWalletId = payerWallet.id,
            toWalletId = merchantWallet.id,
            amount = intent.amount,
            fee = fee,
            currency = payerWallet.currency,
            type = TransactionType.PAYMENT,
            status = TransactionStatus.COMPLETED,
            description = "$channelLabel payment - ${merchant.businessName}",
            channel = channel,
            completedAt = Instant.now(),
        )
        fraudRuleEngine.evaluate(payerUserId, merchant.ownerUserId, intent.amount, transaction.id)
        transactionRepository.save(transaction)

        intent.status = PaymentIntentStatus.COMPLETED
        intent.completedTransactionId = result.transactionId
        intent.paidByUserId = payerUserId
        paymentIntentRepository.save(intent)

        val resultMap = mapOf(
            "transactionId" to result.transactionId,
            "merchantName" to merchant.businessName,
            "amount" to intent.amount,
            "fee" to fee,
            "status" to "COMPLETED",
            "channel" to channel,
            "completedAt" to Instant.now().toString(),
        )
        // Real webhook delivery -- no-op if the merchant never registered a URL. Called last,
        // after the ledger transaction and intent status are already saved, so a slow or
        // unreachable webhook endpoint can only delay the response, never roll back real money
        // that already moved.
        webhookDeliveryService.deliverPaymentStatusChanged(merchant.webhookUrl, resultMap + ("paymentIntentId" to intentId) + ("payerId" to payerUserId))
        return resultMap
    }

    // Real demo card-processing flow (2026-07-17), closing the actionable half of this
    // row's previously fully-blocked "POS, card processing" gap -- see
    // DemoCardAuthorizationService's own doc comment for the real Luhn validation +
    // simulated authorization this runs before any ledger posting. Unlike collect()'s
    // QR flow, there is no real itunda payer wallet on the other side of a card charge
    // (a real card is issued by a real bank/network outside this system) -- the debit
    // leg goes to a real RAIL_SUSPENSE clearing account (card_network_clearing),
    // same "money entering from outside the system" pattern WalletService's own
    // external-rail transfers already use, rather than inventing a fake payer wallet.
    @Transactional
    fun chargeCard(
        ownerUserId: String, amount: BigDecimal, description: String,
        cardNumber: String, expiryMonth: Int, expiryYear: Int, cvc: String,
    ): Map<String, Any?> {
        val merchant = getMyMerchant(ownerUserId)
        val merchantWallet = walletRepository.findById(merchant.walletId)
            .orElseThrow { MerchantNoWalletException("Merchant settlement wallet not found") }

        val authResult = demoCardAuthorizationService.authorize(cardNumber, expiryMonth, expiryYear, cvc)
        if (authResult.status != CardAuthorizationStatus.APPROVED) {
            throw CardDeclinedException(authResult.detail)
        }

        val fee = amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)
        val netToMerchant = amount.subtract(fee)

        val result = ledgerService.postLedgerTransaction(
            merchantWallet.currency,
            listOf(
                LedgerLeg("card_network_clearing", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.DEBIT, amount, "Card payment - ${merchant.businessName}"),
                LedgerLeg(merchantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "Card collection - $description"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "Card payment fee - ${merchant.businessName}"),
            ),
        )

        // "external_card" mirrors WalletService.confirmTransfer's own "external"
        // recipientId convention for money that enters/leaves through a real external
        // rail rather than another itunda wallet -- senderId/recipientId are plain
        // strings with no FK constraint (confirmed directly against Transaction.kt).
        // channel = "CARD" means this shows up in getReport()'s existing byChannel
        // breakdown automatically, no changes needed there.
        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "CARD${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = "external_card_${authResult.last4}",
            recipientId = merchant.ownerUserId,
            fromWalletId = null,
            toWalletId = merchantWallet.id,
            amount = amount,
            fee = fee,
            currency = merchantWallet.currency,
            type = TransactionType.PAYMENT,
            status = TransactionStatus.COMPLETED,
            description = "Card payment - ${merchant.businessName}",
            channel = "CARD",
            completedAt = Instant.now(),
        )
        transactionRepository.save(transaction)

        val resultMap = mapOf(
            "transactionId" to result.transactionId,
            "merchantName" to merchant.businessName,
            "amount" to amount,
            "fee" to fee,
            "status" to "COMPLETED",
            "channel" to "CARD",
            "cardLast4" to authResult.last4,
            "completedAt" to Instant.now().toString(),
        )
        webhookDeliveryService.deliverPaymentStatusChanged(merchant.webhookUrl, resultMap + ("payerId" to "external_card_${authResult.last4}"))
        return resultMap
    }

    // Real merchant reports (2026-07-16) -- closes the "reports" half of the target
    // capability named in docs/TOSS_PARITY_MATRIX.md's Merchant row (QR, POS, reports,
    // settlements). Grouped in-memory by day rather than a JPQL date-function GROUP BY
    // (ReconciliationService's approach) since Transaction.createdAt is a timestamp, not
    // a pre-truncated date column like ProviderAttemptLog.occurredDate -- fine at this
    // scale, and avoids a database-specific date-truncation function. "Settlement" here
    // is just Transaction.status == COMPLETED: collect() posts to the merchant's own
    // wallet synchronously in the same ledger transaction as the collection, so there's
    // no separate pending-settlement state to report on, unlike a real payout batch.
    fun getReport(ownerUserId: String, from: LocalDate, to: LocalDate): List<MerchantReportDay> {
        val merchant = getMyMerchant(ownerUserId)
        val fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val transactions = transactionRepository.findByRecipientIdAndTypeAndCreatedAtBetween(
            merchant.ownerUserId, TransactionType.PAYMENT, fromInstant, toInstant,
        )
        return transactions
            .groupBy { LocalDate.ofInstant(it.createdAt, ZoneOffset.UTC) }
            .map { (date, dayTransactions) ->
                MerchantReportDay(
                    date = date,
                    collectionCount = dayTransactions.size,
                    grossAmount = dayTransactions.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount },
                    fees = dayTransactions.fold(BigDecimal.ZERO) { acc, t -> acc + t.fee },
                    netAmount = dayTransactions.fold(BigDecimal.ZERO) { acc, t -> acc + (t.amount - t.fee) },
                    byChannel = dayTransactions.groupingBy { it.channel ?: "QR" }.eachCount(),
                )
            }
            .sortedBy { it.date }
    }
}
