package rw.itunda.gift

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.GiftVoucher
import rw.itunda.core.domain.GiftVoucherStatus
import rw.itunda.core.format.formatAmount
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.GiftVoucherRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.messaging.MessagingService
import rw.itunda.core.pricing.PlatformFees
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class GiftVoucherNotFoundException(message: String) : RuntimeException(message)
class GiftVoucherNotActiveException(message: String) : RuntimeException(message)
class GiftVoucherExpiredException(message: String) : RuntimeException(message)
class GiftVoucherSelfException(message: String) : RuntimeException(message)
class GiftVoucherNoAccountException(message: String) : RuntimeException(message)
class GiftVoucherRecipientNotFoundException(message: String) : RuntimeException(message)
class GiftVoucherInvalidAmountException(message: String) : RuntimeException(message)
class GiftVoucherMerchantNotFoundException(message: String) : RuntimeException(message)
class GiftVoucherProductNotFoundException(message: String) : RuntimeException(message)
class GiftVoucherProductUnavailableException(message: String) : RuntimeException(message)
class GiftVoucherNotMerchantOwnerException(message: String) : RuntimeException(message)
class GiftVoucherNotExtendableException(message: String) : RuntimeException(message)
class GiftVoucherAlreadyExtendedException(message: String) : RuntimeException(message)

private const val GIFT_VOUCHER_HOLDING_ACCOUNT_ID = "gift_voucher_holding"

/**
 * Real KakaoTalk-style "선물하기" 기프티콘 (mobile gift voucher) -- see
 * [rw.itunda.core.domain.GiftVoucher]'s own doc comment for the full account of how
 * this differs from [GiftService]'s money gift, and the honest scoping choices behind
 * the default validity/extension/refund-rate constants.
 */
@Service
class GiftVoucherService(
    private val giftVoucherRepository: GiftVoucherRepository,
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerService: LedgerService,
    private val messagingService: MessagingService,
    private val rateLimiter: RateLimiter,
    private val fraudRuleEngine: FraudRuleEngine,
    private val autoTopUpService: rw.itunda.account.AutoTopUpService,
) {
    // Consolidated 2026-09-06 into core/pricing/PlatformFees -- see its own doc comment.
    // Charged at real redemption time (when itunda's product/service is actually
    // delivered to the merchant), not at purchase time.
    private val feeRate = PlatformFees.PLATFORM_FEE_RATE

    @Transactional
    fun purchaseVoucher(
        purchaserUserId: String,
        recipientPhoneNumber: String,
        merchantId: String,
        merchantProductId: String?,
        flatAmount: BigDecimal?,
    ): GiftVoucher {
        val trimmedPhone = recipientPhoneNumber.trim()
        val recipientUser = userRepository.findByPhoneNumber(trimmedPhone)
            ?: throw GiftVoucherRecipientNotFoundException("No itunda account found for this phone number")
        if (recipientUser.id == purchaserUserId) throw GiftVoucherSelfException("Cannot send a gift voucher to yourself")

        rateLimiter.checkLimit("giftvoucher:purchase:$purchaserUserId", limit = 20, window = Duration.ofHours(1))

        val merchant = merchantRepository.findById(merchantId).orElseThrow { GiftVoucherMerchantNotFoundException("Merchant not found") }
        if (merchant.status != MerchantStatus.ACTIVE) throw GiftVoucherMerchantNotFoundException("Merchant not found")

        val (amount, productNameSnapshot) = if (merchantProductId != null) {
            val product = merchantProductRepository.findById(merchantProductId).orElseThrow { GiftVoucherProductNotFoundException("Product not found") }
            if (product.merchantId != merchantId || !product.active) throw GiftVoucherProductNotFoundException("Product not found")
            // A voucher does not reserve stock at purchase—the recipient may redeem it
            // later—but selling a product-tied voucher for an item already known to be
            // sold out is misleading and creates an avoidable fulfilment failure.
            if (product.stockQuantity == 0) throw GiftVoucherProductUnavailableException("This product is currently out of stock")
            product.price to product.name
        } else {
            val trimmedAmount = flatAmount ?: throw GiftVoucherInvalidAmountException("Amount is required for a flat-value voucher")
            if (trimmedAmount <= BigDecimal.ZERO) throw GiftVoucherInvalidAmountException("Amount must be greater than zero")
            trimmedAmount to null
        }

        // Real Toss Bank/Toss Pay separation (2026-08-21) -- see MerchantService
        // .collect()'s own doc comment. Purchasing a gift voucher is real merchant
        // collection (the purchaser is effectively pre-paying a merchant, held in
        // escrow until redemption), same as QR/code payment -- draws from the
        // purchaser's itunda Pay money, auto-topped from Bank (then an external linked
        // account) if short.
        var purchaserAccount = accountRepository.findByUserIdAndType(purchaserUserId, AccountType.PAY)
            ?: throw GiftVoucherNoAccountException("No itunda Pay money found for this account")
        purchaserAccount = autoTopUpService.ensureSufficientPayBalance(purchaserUserId, purchaserAccount, amount)
        if (purchaserAccount.availableBalance < amount) {
            throw InsufficientFundsException("Insufficient available balance for this gift voucher")
        }

        // Real escrow hold -- same "hold, don't move directly until the real event
        // happens" shape GiftService's own money-gift escrow uses. Here the real event
        // is the MERCHANT redeeming the voucher, not the recipient claiming it.
        val holdResult = ledgerService.postLedgerTransaction(
            purchaserAccount.currency,
            listOf(
                LedgerLeg(purchaserAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Gift voucher purchased -- ${merchant.businessName}"),
                LedgerLeg(GIFT_VOUCHER_HOLDING_ACCOUNT_ID, LedgerAccountType.GIFT_VOUCHER_HOLDING, LedgerDirection.CREDIT, amount, "Gift voucher held in escrow"),
            ),
        )
        val holdTransaction = Transaction(
            id = holdResult.transactionId,
            referenceNumber = "GIFTVOUCHER${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = purchaserUserId,
            recipientId = recipientUser.id,
            fromAccountId = purchaserAccount.id,
            toAccountId = null,
            amount = amount,
            fee = BigDecimal.ZERO,
            currency = purchaserAccount.currency,
            type = TransactionType.TRANSFER,
            status = TransactionStatus.COMPLETED,
            description = "Gift voucher purchased -- ${merchant.businessName}",
            completedAt = Instant.now(),
        )
        // Real fraud coverage (item 247 follow-up, docs/DESIGN_REFERENCES.md §14
        // recommendation #4) -- same real gap and same fix as GiftService.sendGift:
        // this moves real money to a recipient by phone number, evaluated before save
        // so this transaction can't match itself as prior history and mask
        // NEW_RECIPIENT (see P2pService's own inline comment for the full reasoning).
        fraudRuleEngine.evaluate(purchaserUserId, recipientUser.id, amount, holdTransaction.id)
        transactionRepository.save(holdTransaction)

        val conversation = messagingService.startOrGetConversation(purchaserUserId, recipientUser.id)
        val label = productNameSnapshot ?: "${formatAmount(amount)} RWF"
        val message = messagingService.sendMessage(
            purchaserUserId, conversation.id,
            "🎁 Sent a gift voucher: $label at ${merchant.businessName}",
        )

        return giftVoucherRepository.save(
            GiftVoucher(
                id = "giftvoucher_${UUID.randomUUID()}",
                purchaserId = purchaserUserId,
                recipientId = recipientUser.id,
                conversationId = conversation.id,
                messageId = message.id,
                merchantId = merchantId,
                merchantProductId = merchantProductId,
                productNameSnapshot = productNameSnapshot,
                amount = amount,
                holdTransactionId = holdTransaction.id,
                expiresAt = Instant.now().plus(GiftVoucher.DEFAULT_VALIDITY),
            ),
        )
    }

    /** A stranger (not purchaser or recipient) gets a real 404, same IDOR discipline as
     * every other resource-ownership check in this codebase. */
    fun getVoucher(userId: String, voucherId: String): GiftVoucher {
        val voucher = giftVoucherRepository.findById(voucherId).orElseThrow { GiftVoucherNotFoundException("Gift voucher not found") }
        if (userId != voucher.purchaserId && userId != voucher.recipientId) throw GiftVoucherNotFoundException("Gift voucher not found")
        return voucher
    }

    fun getVouchersForConversation(userId: String, conversationId: String): List<GiftVoucher> {
        messagingService.getConversationForParticipant(userId, conversationId)
        return giftVoucherRepository.findByConversationId(conversationId)
    }

    /**
     * Real, sourced Kakao-style one-time expiry extension -- only usable within
     * [GiftVoucher.EXTENSION_WINDOW] of the current expiry, adds
     * [GiftVoucher.EXTENSION_AMOUNT], and only once per voucher (`extended`). Either
     * party can trigger it (either the purchaser or recipient may notice it's about to
     * lapse), same "either real party to the transaction" convention `HoodReviewService`
     * already established.
     */
    @Transactional
    fun extendExpiry(userId: String, voucherId: String): GiftVoucher {
        val voucher = getVoucher(userId, voucherId)
        if (voucher.status != GiftVoucherStatus.ACTIVE) throw GiftVoucherNotActiveException("This voucher is already ${voucher.status}")
        if (voucher.extended) throw GiftVoucherAlreadyExtendedException("This voucher has already been extended once")
        val windowStart = voucher.expiresAt.minus(GiftVoucher.EXTENSION_WINDOW)
        if (Instant.now().isBefore(windowStart)) {
            throw GiftVoucherNotExtendableException("A voucher can only be extended within ${GiftVoucher.EXTENSION_WINDOW.toDays()} days of its expiry")
        }
        voucher.expiresAt = voucher.expiresAt.plus(GiftVoucher.EXTENSION_AMOUNT)
        voucher.extended = true
        return giftVoucherRepository.save(voucher)
    }

    /**
     * Real merchant-side redemption -- mirrors `MerchantService.collect()`'s own real
     * "merchant collects" pattern: the recipient presents the voucher in person/shows
     * its id, and the merchant's own authenticated account is what actually redeems it,
     * never a self-serve redeem the recipient could fake. Real flat platform fee
     * charged here (not at purchase time), matching every other merchant collection in
     * this codebase.
     */
    @Transactional
    fun redeemVoucher(merchantOwnerUserId: String, voucherId: String): GiftVoucher {
        val voucher = giftVoucherRepository.findByIdForUpdate(voucherId).orElseThrow { GiftVoucherNotFoundException("Gift voucher not found") }
        val merchant = merchantRepository.findById(voucher.merchantId).orElseThrow { GiftVoucherMerchantNotFoundException("Merchant not found") }
        if (merchant.ownerUserId != merchantOwnerUserId) throw GiftVoucherNotFoundException("Gift voucher not found")
        if (voucher.status != GiftVoucherStatus.ACTIVE) throw GiftVoucherNotActiveException("This voucher is already ${voucher.status}")
        if (voucher.expiresAt.isBefore(Instant.now())) throw GiftVoucherExpiredException("This voucher has expired")

        val merchantAccount = accountRepository.findById(merchant.accountId)
            .orElseThrow { GiftVoucherNoAccountException("Merchant settlement account not found") }
        val fee = voucher.amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)
        val netToMerchant = voucher.amount.subtract(fee)

        val redeemResult = ledgerService.postLedgerTransaction(
            merchantAccount.currency,
            listOf(
                LedgerLeg(GIFT_VOUCHER_HOLDING_ACCOUNT_ID, LedgerAccountType.GIFT_VOUCHER_HOLDING, LedgerDirection.DEBIT, voucher.amount, "Gift voucher redeemed"),
                LedgerLeg(merchantAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "Gift voucher redemption -- ${merchant.businessName}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "Gift voucher redemption fee -- ${merchant.businessName}"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = redeemResult.transactionId,
                referenceNumber = "GIFTVOUCHERREDEEM${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = voucher.recipientId,
                recipientId = merchant.ownerUserId,
                fromAccountId = null,
                toAccountId = merchantAccount.id,
                amount = voucher.amount,
                fee = fee,
                currency = merchantAccount.currency,
                type = TransactionType.PAYMENT,
                status = TransactionStatus.COMPLETED,
                description = "Gift voucher redemption -- ${merchant.businessName}",
                channel = "GIFT_VOUCHER",
                completedAt = Instant.now(),
            ),
        )

        voucher.status = GiftVoucherStatus.REDEEMED
        voucher.redeemTransactionId = redeemResult.transactionId
        voucher.redeemedAt = Instant.now()
        val saved = giftVoucherRepository.save(voucher)
        // Real bug avoided here: the merchant owner is NOT a participant in
        // voucher.conversationId (that's the purchaser<->recipient 1:1 thread) --
        // MessagingService.sendMessage's own requireParticipant check would real-throw
        // ConversationNotFoundException if this posted as the merchant. Posted as the
        // recipient's own message instead (they're the one physically present at
        // redemption), same "no system/bot sender concept yet" convention
        // MarketplaceService's own sold-notification message already established.
        messagingService.sendMessage(
            voucher.recipientId, voucher.conversationId,
            "✅ Gift voucher redeemed at ${merchant.businessName}",
        )
        return saved
    }

    /** Real auto-refund for an unredeemed voucher, driven by [GiftVoucherExpiryScheduler].
     * Only a real, sourced 90% refund reaches the purchaser -- the remaining 10% is
     * forfeited to itunda as real fee revenue, matching real gifticon expiry economics,
     * never silently vanishing from the ledger. */
    @Transactional
    fun expireVoucher(voucher: GiftVoucher) {
        if (voucher.status != GiftVoucherStatus.ACTIVE) return
        // Refunds back to the same real itunda Pay account the voucher was purchased
        // from (see purchaseVoucher's own doc comment) -- a refund, so no auto-topup
        // applies.
        val purchaserAccount = accountRepository.findByUserIdAndType(voucher.purchaserId, AccountType.PAY) ?: return

        val refundAmount = voucher.amount.multiply(GiftVoucher.EXPIRY_REFUND_RATE).setScale(2, RoundingMode.HALF_UP)
        val forfeitedAmount = voucher.amount.subtract(refundAmount)

        val refundResult = ledgerService.postLedgerTransaction(
            purchaserAccount.currency,
            listOf(
                LedgerLeg(GIFT_VOUCHER_HOLDING_ACCOUNT_ID, LedgerAccountType.GIFT_VOUCHER_HOLDING, LedgerDirection.DEBIT, voucher.amount, "Unredeemed gift voucher expired"),
                LedgerLeg(purchaserAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, refundAmount, "Unredeemed gift voucher partial refund"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, forfeitedAmount, "Unredeemed gift voucher forfeited amount"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = refundResult.transactionId,
                referenceNumber = "GIFTVOUCHEREXP${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = voucher.recipientId,
                recipientId = voucher.purchaserId,
                fromAccountId = null,
                toAccountId = purchaserAccount.id,
                amount = refundAmount,
                fee = BigDecimal.ZERO,
                currency = purchaserAccount.currency,
                type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED,
                description = "Unredeemed gift voucher partial refund",
                completedAt = Instant.now(),
            ),
        )

        voucher.status = GiftVoucherStatus.EXPIRED
        voucher.refundTransactionId = refundResult.transactionId
        giftVoucherRepository.save(voucher)
        messagingService.sendMessage(
            voucher.purchaserId, voucher.conversationId,
            "⏰ Your gift voucher went unredeemed and expired -- ${formatAmount(refundAmount)} RWF (90%) was refunded",
        )
    }

    fun getExpiredActiveVouchers(): List<GiftVoucher> =
        giftVoucherRepository.findByStatusAndExpiresAtBefore(GiftVoucherStatus.ACTIVE, Instant.now())

    // Real Kakao gifticon expiry-reminder sweep -- `expiresAt` has been a real, stored
    // field since this voucher concept existed, but nothing ever nudged the RECIPIENT
    // to redeem before real expiry (only the purchaser was ever told, and only after
    // the fact by `expireVoucher`'s own real partial-refund message). See
    // GiftVoucher.expiryReminderSentAt's own doc comment for the real sourcing.
    fun getVouchersDueForExpiryReminder(): List<GiftVoucher> {
        val cutoff = Instant.now().plus(GiftVoucher.EXPIRY_REMINDER_WINDOW)
        return giftVoucherRepository.findByStatusAndExpiryReminderSentAtIsNull(GiftVoucherStatus.ACTIVE)
            .filter { !it.expiresAt.isAfter(cutoff) }
    }

    /** One real expiry-reminder message to the recipient, called per-voucher by the
     * scheduler -- re-checks `status`/`expiryReminderSentAt` right before sending so a
     * genuine race can't double-fire, same resilience discipline
     * InsuranceService.sendRenewalReminder/CertificateService.sendRenewalReminder's own
     * doc comments already establish. Sent into the existing real purchaser<->recipient
     * conversation, same "no system/bot sender concept yet" convention this class's own
     * purchase/redeem/expire messages already use -- posted as the purchaser since
     * they're the one who'd want their gift actually used. */
    @Transactional
    fun sendExpiryReminder(voucherId: String) {
        val voucher = giftVoucherRepository.findById(voucherId).orElse(null) ?: return
        if (voucher.status != GiftVoucherStatus.ACTIVE || voucher.expiryReminderSentAt != null) return

        messagingService.sendMessage(
            voucher.purchaserId, voucher.conversationId,
            "⏳ Your gift voucher (${voucher.productNameSnapshot ?: "${formatAmount(voucher.amount)} RWF"}) expires soon -- redeem it before ${voucher.expiresAt} or it'll be refunded",
        )
        voucher.expiryReminderSentAt = Instant.now()
        giftVoucherRepository.save(voucher)
    }
}

