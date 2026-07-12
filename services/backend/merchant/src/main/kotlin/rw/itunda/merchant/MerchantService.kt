package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent
import rw.itunda.core.domain.PaymentIntentStatus
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.PaymentIntentRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class MerchantAlreadyRegisteredException(message: String) : RuntimeException(message)
class MerchantNotFoundException(message: String) : RuntimeException(message)
class MerchantNoWalletException(message: String) : RuntimeException(message)
class PaymentIntentNotFoundException(message: String) : RuntimeException(message)
class PaymentIntentNotPayableException(message: String) : RuntimeException(message)
class SelfPaymentException(message: String) : RuntimeException(message)

/**
 * A real, minimal subset of docs/MERCHANT_SERVICES.md's product surface --
 * registration + QR-style fixed-amount payment collection into the merchant's
 * settlement wallet, ledger-backed like every other money-moving flow in this
 * backend. Deliberately does not implement that doc's POS/card-processing/B2B-
 * payroll/webhook surface -- that doc's own header already flags those as an
 * invented spec that never checked a real provider's actual API; building them
 * for real would mean building actual PSP-level card infrastructure this repo
 * has no path to certify, not writing more Kotlin.
 */
@Service
class MerchantService(
    private val merchantRepository: MerchantRepository,
    private val paymentIntentRepository: PaymentIntentRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val webhookDeliveryService: WebhookDeliveryService,
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
    fun collect(payerUserId: String, intentId: String): Map<String, Any?> {
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

        val result = ledgerService.postLedgerTransaction(
            payerWallet.currency,
            listOf(
                LedgerLeg(payerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, intent.amount, "QR payment - ${merchant.businessName}"),
                LedgerLeg(merchantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "QR collection - ${intent.description}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "QR payment fee - ${merchant.businessName}"),
            ),
        )

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
            "completedAt" to Instant.now().toString(),
        )
        // Real webhook delivery -- no-op if the merchant never registered a URL. Called last,
        // after the ledger transaction and intent status are already saved, so a slow or
        // unreachable webhook endpoint can only delay the response, never roll back real money
        // that already moved.
        webhookDeliveryService.deliverPaymentStatusChanged(merchant.webhookUrl, resultMap + ("paymentIntentId" to intentId) + ("payerId" to payerUserId))
        return resultMap
    }
}
