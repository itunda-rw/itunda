package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
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
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.PaymentIntentRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class MerchantAlreadyRegisteredException(message: String) : RuntimeException(message)
class MerchantNotFoundException(message: String) : RuntimeException(message)
class MerchantNoWalletException(message: String) : RuntimeException(message)
class InvalidCoordinatesException(message: String) : RuntimeException(message)
class InvalidCategoryException(message: String) : RuntimeException(message)
class InvalidPhotoUrlException(message: String) : RuntimeException(message)
class InvalidMinOrderAmountException(message: String) : RuntimeException(message)
class PaymentIntentNotFoundException(message: String) : RuntimeException(message)
class PaymentIntentNotPayableException(message: String) : RuntimeException(message)
class SelfPaymentException(message: String) : RuntimeException(message)
class CardDeclinedException(message: String) : RuntimeException(message)
class InvalidWebhookUrlException(message: String) : RuntimeException(message)
class InvalidApiKeyException(message: String) : RuntimeException(message)
class InvalidCheckoutRequestException(message: String) : RuntimeException(message)
class PaymentIntentNotRefundableException(message: String) : RuntimeException(message)
class InvalidCancelRequestException(message: String) : RuntimeException(message)

// Real external-checkout DTOs (2026-07-21) -- see PaymentsApiController's own doc
// comment for the full account of the real Toss Payments feature this mirrors.
data class CheckoutInfo(
    val paymentKey: String,
    val merchantName: String,
    val amount: BigDecimal,
    val description: String,
    val status: PaymentIntentStatus,
    val successUrl: String?,
    val failUrl: String?,
)

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
    private val shoppingCashbackService: ShoppingCashbackService,
    private val rateLimiter: RateLimiter,
    private val ledgerEntryRepository: LedgerEntryRepository,
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
        val trimmed = webhookUrl.trim()
        // Real bound, found via the same systematic sweep that fixed the identical gap
        // across Commerce/Eats/Marketplace/Jobs/RealEstate/Community/Messaging/Maps the
        // same day -- `webhook_url` is VARCHAR(500), and this DB's real
        // STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an over-length insert.
        // Never previously caught because setWebhookUrl didn't even trim its input.
        if (trimmed.length > 500) {
            throw InvalidWebhookUrlException("Webhook URL must be 500 characters or fewer")
        }
        merchant.webhookUrl = trimmed
        return merchantRepository.save(merchant)
    }

    // Real location (2026-07-18) -- the foundation of itunda's own self-hosted maps
    // effort. A separate settable field rather than a `register()` param so an existing
    // merchant can add a location later without re-registering, matching the same
    // pattern `setWebhookUrl` already established.
    @Transactional
    fun setLocation(ownerUserId: String, latitude: Double, longitude: Double): Merchant {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.latitude = latitude
        merchant.longitude = longitude
        return merchantRepository.save(merchant)
    }

    // Real category/cuisine (2026-07-19) -- powers restaurant categories + search/filter
    // for Eats (and Shopping, since both browse the same Merchant directory). Same
    // separate-settable-field pattern as setWebhookUrl/setLocation.
    @Transactional
    fun setCategory(ownerUserId: String, category: String): Merchant {
        val trimmed = category.trim()
        if (trimmed.isEmpty() || trimmed.length > 64) {
            throw InvalidCategoryException("Category must be between 1 and 64 characters")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.category = trimmed
        return merchantRepository.save(merchant)
    }

    // Real restaurant-card photo (2026-07-21) -- see Merchant.kt's own doc comment for
    // why this is a merchant-set URL, not an upload/storage pipeline. Same trim + length
    // bound discipline as setWebhookUrl.
    @Transactional
    fun setPhotoUrl(ownerUserId: String, photoUrl: String): Merchant {
        val trimmed = photoUrl.trim()
        if (trimmed.length > 500) {
            throw InvalidPhotoUrlException("Photo URL must be 500 characters or fewer")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.photoUrl = trimmed.ifEmpty { null }
        return merchantRepository.save(merchant)
    }

    // Real merchant-set minimum order amount (2026-07-21) -- nullable; passing null
    // explicitly clears it back to "no minimum", same as an unset merchant.
    @Transactional
    fun setMinOrderAmount(ownerUserId: String, minOrderAmount: BigDecimal?): Merchant {
        if (minOrderAmount != null && minOrderAmount < BigDecimal.ZERO) {
            throw InvalidMinOrderAmountException("Minimum order amount cannot be negative")
        }
        val merchant = getMyMerchant(ownerUserId)
        merchant.minOrderAmount = minOrderAmount
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

    // Real "Pay with itunda" external checkout API key (2026-07-21) -- mirrors
    // PartnerService.register's exact raw-key-shown-once/hash-stored pattern (same
    // sk_test_ prefix convention, same reasoning: no real production/live-mode
    // distinction exists here yet, so claiming a "live" prefix would be dishonest).
    // Only the logged-in merchant owner can call this (normal JWT auth, see
    // MerchantController) -- the resulting secret key is what their OWN backend server
    // then uses non-interactively, with no itunda user login involved at all. Calling
    // this again rotates the key -- the old one stops working immediately, since only
    // the hash is ever stored.
    @Transactional
    fun generateApiKey(ownerUserId: String): String {
        val merchant = getMyMerchant(ownerUserId)
        val rawKey = generateRawApiKey()
        merchant.apiKeyHash = hashApiKey(rawKey)
        merchantRepository.save(merchant)
        return rawKey
    }

    fun resolveMerchantByApiKey(apiKey: String): Merchant {
        val merchant = merchantRepository.findByApiKeyHash(hashApiKey(apiKey))
            ?: throw InvalidApiKeyException("Invalid or unknown API key")
        if (merchant.status != MerchantStatus.ACTIVE) {
            throw InvalidApiKeyException("This merchant account is suspended")
        }
        return merchant
    }

    // Real server-to-server payment creation (2026-07-21) -- the external-checkout
    // counterpart to generateQr above (that one's caller is always a logged-in itunda
    // merchant user in merchant-mfe/:merchantapp; this one's caller is the MERCHANT'S
    // OWN backend server, authenticated by API key, with no itunda user session
    // involved at all -- see PaymentsApiController). Reuses the identical PaymentIntent
    // shape and the identical collect()/webhook machinery underneath -- a customer still
    // completes this exact intent by scanning the same real QR/deep-link with their
    // itunda app, same as any in-app-generated one.
    @Transactional
    fun createExternalPayment(
        merchant: Merchant, amount: BigDecimal, description: String,
        orderId: String?, successUrl: String?, failUrl: String?,
    ): PaymentIntent {
        if (amount <= BigDecimal.ZERO) throw InvalidCheckoutRequestException("Amount must be positive")
        val trimmedDescription = description.trim()
        if (trimmedDescription.isEmpty() || trimmedDescription.length > 500) {
            throw InvalidCheckoutRequestException("Description must be between 1 and 500 characters")
        }
        if ((orderId?.length ?: 0) > 200) throw InvalidCheckoutRequestException("orderId must be 200 characters or fewer")
        if ((successUrl?.length ?: 0) > 500 || (failUrl?.length ?: 0) > 500) {
            throw InvalidCheckoutRequestException("successUrl/failUrl must be 500 characters or fewer")
        }
        val intent = PaymentIntent(
            id = "pi_${UUID.randomUUID()}",
            merchantId = merchant.id,
            amount = amount,
            description = trimmedDescription,
            expiresAt = Instant.now().plusSeconds(900),
            orderId = orderId?.trim()?.ifBlank { null },
            successUrl = successUrl?.trim()?.ifBlank { null },
            failUrl = failUrl?.trim()?.ifBlank { null },
        )
        return paymentIntentRepository.save(intent)
    }

    // Real public checkout info (2026-07-21) -- deliberately NOT behind the API key:
    // the customer's own browser calls this (via itunda's hosted checkout page), and a
    // browser never has the merchant's secret key -- only the paymentKey (this intent's
    // id), the same public/secret split every real payment gateway's checkout page
    // uses. Returns only what's safe to show a paying customer -- never the merchant's
    // internal id, webhook URL, or any other account detail.
    fun getCheckoutInfo(paymentKey: String): CheckoutInfo {
        val intent = paymentIntentRepository.findById(paymentKey)
            .orElseThrow { PaymentIntentNotFoundException("Payment not found") }
        val merchant = merchantRepository.findById(intent.merchantId)
            .orElseThrow { MerchantNotFoundException("Merchant not found") }
        return CheckoutInfo(
            paymentKey = intent.id,
            merchantName = merchant.businessName,
            amount = intent.amount,
            description = intent.description,
            status = intent.status,
            successUrl = intent.successUrl,
            failUrl = intent.failUrl,
        )
    }

    // Real server-to-server status confirmation (2026-07-21) -- the synchronous
    // counterpart to the async webhook: a merchant's backend can (and per real payment
    // gateway convention, should) confirm a payment's status directly before fulfilling
    // an order, not rely on the webhook alone arriving in time. Ownership-checked: the
    // API key resolves to a specific merchant, and this real-404s (not just returns
    // someone else's data) for a paymentKey belonging to a different merchant.
    fun getPaymentStatusForMerchant(merchant: Merchant, paymentKey: String): PaymentIntent {
        val intent = paymentIntentRepository.findById(paymentKey)
            .orElseThrow { PaymentIntentNotFoundException("Payment not found") }
        if (intent.merchantId != merchant.id) throw PaymentIntentNotFoundException("Payment not found")
        return intent
    }

    // Real cancel/refund (2026-07-21) -- mirrors Toss Payments' own real cancel API
    // exactly (docs.tosspayments.com/guides/v2/cancel-payment): paymentKey + a required
    // cancelReason, an optional cancelAmount (a full refund if omitted), supporting
    // repeated partial cancels up to the original amount rather than a single
    // all-or-nothing flag. Reuses the exact real ledger-reversal pattern
    // rw.itunda.commerce.OrderService.cancelOrder already established for Commerce
    // order cancellation: look up the original transaction's real ledger entries and
    // post a new, offsetting transaction with each leg's direction flipped -- a real
    // double-entry reversal, never mutating or deleting the original historical entry.
    //
    // Honest scoping note: Toss Payments' own real per-partial-cancel fee policy isn't
    // published in enough detail to mirror exactly (their docs cover the cancelAmount
    // parameter, not the exact fee-refund math behind it) -- this refunds each original
    // leg (payer debit, merchant credit, fee credit) in the same proportion as the
    // cancelled amount, an itunda-specific, internally-consistent choice, not a
    // fabricated claim about Toss's own internal math.
    @Transactional
    fun cancelPayment(merchant: Merchant, paymentKey: String, cancelReason: String, cancelAmount: BigDecimal?): Map<String, Any?> {
        val intent = paymentIntentRepository.findById(paymentKey)
            .orElseThrow { PaymentIntentNotFoundException("Payment not found") }
        if (intent.merchantId != merchant.id) throw PaymentIntentNotFoundException("Payment not found")
        if (intent.status != PaymentIntentStatus.COMPLETED) {
            throw PaymentIntentNotRefundableException("Only a completed payment can be cancelled -- this payment is ${intent.status}")
        }
        val transactionId = intent.completedTransactionId
            ?: throw PaymentIntentNotRefundableException("No completed transaction found for this payment")

        val trimmedReason = cancelReason.trim()
        if (trimmedReason.isEmpty() || trimmedReason.length > 200) {
            throw InvalidCancelRequestException("cancelReason must be between 1 and 200 characters")
        }
        val remaining = intent.amount.subtract(intent.refundedAmount)
        val amountToCancel = cancelAmount ?: remaining
        if (amountToCancel <= BigDecimal.ZERO || amountToCancel > remaining) {
            throw InvalidCancelRequestException("cancelAmount must be positive and no more than the remaining refundable amount ($remaining)")
        }

        val originalEntries = ledgerEntryRepository.findByTransactionId(transactionId)
        if (originalEntries.isEmpty()) throw PaymentIntentNotRefundableException("No ledger entries found for this payment")

        val ratio = amountToCancel.divide(intent.amount, 10, RoundingMode.HALF_UP)
        val reversedLegs = originalEntries.map { entry ->
            val flipped = if (entry.direction == LedgerDirection.DEBIT) LedgerDirection.CREDIT else LedgerDirection.DEBIT
            val partialAmount = entry.amount.multiply(ratio).setScale(2, RoundingMode.HALF_UP)
            LedgerLeg(entry.accountId, entry.accountType, flipped, partialAmount, "Refund for payment ${intent.id}: $trimmedReason")
        }
        val refund = ledgerService.postLedgerTransaction(originalEntries.first().currency, reversedLegs)

        intent.refundedAmount = intent.refundedAmount.add(amountToCancel)
        paymentIntentRepository.save(intent)

        val resultMap = mapOf(
            "paymentKey" to intent.id,
            "orderId" to intent.orderId,
            "cancelledAmount" to amountToCancel,
            "totalRefundedAmount" to intent.refundedAmount,
            "remainingAmount" to intent.amount.subtract(intent.refundedAmount),
            "cancelReason" to trimmedReason,
            "refundTransactionId" to refund.transactionId,
            "fullyCancelled" to (intent.refundedAmount.compareTo(intent.amount) == 0),
        )
        // Same "never let a slow/unreachable webhook block real money movement" discipline
        // as collect()/chargeCard -- called last, after the refund ledger transaction and
        // intent are already saved.
        webhookDeliveryService.deliverCancelStatusChanged(merchant.webhookUrl, resultMap)
        return resultMap
    }

    private fun generateRawApiKey(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        return "sk_test_$token"
    }

    private fun hashApiKey(rawKey: String): String =
        MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }

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

        // Real "Toss Shopping" cashback (2026-07-17) -- see ShoppingCashbackService's own
        // doc comment for why this only applies here (a real itunda payer wallet exists)
        // and not in chargeCard (an external card payer has no itunda wallet to credit).
        // Explicitly caught, not propagated: a cashback failure must never roll back or
        // fail a real payment that already succeeded, the same "auxiliary side-effect
        // can't block real money movement" discipline the webhook call below already
        // established -- REQUIRES_NEW alone doesn't guarantee that (an uncaught exception
        // here would still roll back this method's own transaction), so this needs its
        // own explicit try/catch, not just the inner service's propagation setting.
        val cashbackEarned = try {
            shoppingCashbackService.awardCashback(payerWallet, intent.amount, merchant.businessName)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }

        val resultMap = mapOf(
            "transactionId" to result.transactionId,
            "merchantName" to merchant.businessName,
            "amount" to intent.amount,
            "fee" to fee,
            "status" to "COMPLETED",
            "channel" to channel,
            "completedAt" to Instant.now().toString(),
            "cashbackEarned" to cashbackEarned,
        )
        // Real webhook delivery -- no-op if the merchant never registered a URL. Called last,
        // after the ledger transaction and intent status are already saved, so a slow or
        // unreachable webhook endpoint can only delay the response, never roll back real money
        // that already moved.
        //
        // orderId added 2026-07-21 -- a real, found-live gap: Toss Payments' own real webhook
        // payload always includes orderId ("orderId persists even when the payment status
        // changes", per docs.tosspayments.com/en/webhooks), specifically so a merchant's
        // webhook receiver can correlate the event back to ITS OWN order record without a
        // second lookup call. Every in-app QR/Face Pay/card payment leaves this null (they
        // have no external orderId at all) -- only real external-checkout payments
        // (PaymentsApiController) ever set one, so this is purely additive for every existing
        // webhook consumer.
        webhookDeliveryService.deliverPaymentStatusChanged(
            merchant.webhookUrl,
            resultMap + ("paymentIntentId" to intentId) + ("payerId" to payerUserId) + ("orderId" to intent.orderId),
        )
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
        // Real rate limit (2026-07-17, found by this pass's own security review) --
        // DemoCardAuthorizationService.simulateOutcome APPROVEs ~85% of any Luhn-valid
        // card number and this method credits that approval as real, spendable ledger
        // balance into the merchant's real wallet. Unlike a real PSP integration (where
        // a genuine issuer/network sits between an attempt and any money moving), this
        // demo has no external gate at all -- without a limit here, a scripted burst of
        // random Luhn-valid numbers against this one endpoint would mint real balance
        // with no bound, the same class of risk PartnerService.register/CertificateService
        // .issue already guard against on this exact codebase's own established
        // convention. 10/minute comfortably covers a real busy shop's checkout pace
        // while making brute-force card generation impractical.
        rateLimiter.checkLimit("merchant:chargeCard:$ownerUserId", limit = 10, window = Duration.ofMinutes(1))

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
