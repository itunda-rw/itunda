package rw.itunda.gift

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Gift
import rw.itunda.core.domain.GiftStatus
import rw.itunda.core.domain.GiftTheme
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.GiftRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class GiftNotFoundException(message: String) : RuntimeException(message)
class GiftAlreadyResolvedException(message: String) : RuntimeException(message)
class GiftExpiredException(message: String) : RuntimeException(message)
class GiftNotRecipientException(message: String) : RuntimeException(message)
class GiftSelfException(message: String) : RuntimeException(message)
class GiftNoWalletException(message: String) : RuntimeException(message)
class GiftRecipientNotFoundException(message: String) : RuntimeException(message)
class GiftInvalidAmountException(message: String) : RuntimeException(message)

private const val GIFT_HOLDING_ACCOUNT_ID = "gift_holding"

/**
 * Real KakaoTalk-style "선물하기" (gift money) -- see [rw.itunda.core.domain.Gift]'s own
 * doc comment for the full account of why this is a real escrow-then-claim flow
 * distinct from `P2pService.sendDirect`'s instant push-transfer, and why it's posted
 * as a real chat message rather than a separate notification surface.
 */
@Service
class GiftService(
    private val giftRepository: GiftRepository,
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerService: LedgerService,
    private val messagingService: MessagingService,
    private val rateLimiter: RateLimiter,
) {
    /** Send a gift to a phone number, starting/reusing a 1:1 conversation -- the entry
     * point for a client that doesn't already have a conversation open (e.g. a
     * standalone "send a gift" flow, not initiated from an existing chat thread). */
    @Transactional
    fun sendGift(senderUserId: String, recipientPhoneNumber: String, amount: BigDecimal, note: String?, theme: GiftTheme? = null): Gift {
        if (amount <= BigDecimal.ZERO) throw GiftInvalidAmountException("Amount must be greater than zero")
        val trimmedPhone = recipientPhoneNumber.trim()
        val recipientUser = userRepository.findByPhoneNumber(trimmedPhone)
            ?: throw GiftRecipientNotFoundException("No itunda account found for this phone number")
        // Real bug avoided here: checked before calling startOrGetConversation, not
        // after -- that method throws its own real SelfConversationException for a
        // self-pair, a Messaging-module type GiftController has no handler for (would
        // surface as an unhandled 500, not the real, honest GIFT_SELF_NOT_ALLOWED 400).
        if (recipientUser.id == senderUserId) throw GiftSelfException("Cannot send a gift to yourself")
        val conversation = messagingService.startOrGetConversation(senderUserId, recipientUser.id)
        return createGift(senderUserId, recipientUser.id, conversation.id, amount, note, theme)
    }

    /** Send a gift within an already-open conversation -- the real, natural entry
     * point a chat-embedded "🎁" button uses: the recipient is simply whichever
     * participant isn't the caller, resolved via [MessagingService.getConversationForParticipant]'s
     * own IDOR check rather than re-validated here. */
    @Transactional
    fun sendGiftInConversation(senderUserId: String, conversationId: String, amount: BigDecimal, note: String?, theme: GiftTheme? = null): Gift {
        val conversation = messagingService.getConversationForParticipant(senderUserId, conversationId)
        val recipientId = if (conversation.participantAId == senderUserId) conversation.participantBId else conversation.participantAId
        return createGift(senderUserId, recipientId, conversation.id, amount, note, theme)
    }

    private fun createGift(senderUserId: String, recipientUserId: String, conversationId: String, amount: BigDecimal, note: String?, theme: GiftTheme? = null): Gift {
        if (amount <= BigDecimal.ZERO) throw GiftInvalidAmountException("Amount must be greater than zero")
        if (recipientUserId == senderUserId) throw GiftSelfException("Cannot send a gift to yourself")

        // Real anti-spam limit, same convention as every other money-moving
        // creation endpoint in this codebase (P2P send/request, chargeCard, etc).
        rateLimiter.checkLimit("gift:send:$senderUserId", limit = 20, window = Duration.ofHours(1))

        val senderWallet = walletRepository.findByUserIdAndType(senderUserId, WalletType.MAIN)
            ?: throw GiftNoWalletException("No wallet found for this account")
        val recipientWallet = walletRepository.findByUserIdAndType(recipientUserId, WalletType.MAIN)
            ?: throw GiftNoWalletException("Recipient has no wallet to receive this gift")
        if (senderWallet.availableBalance < amount) {
            throw InsufficientFundsException("Insufficient available balance for this gift")
        }

        // Real escrow hold -- the sender's money leaves their wallet right now, the
        // recipient doesn't receive it until they explicitly claim it below. Same
        // "hold, don't move directly" shape EatsOrder's own delivery-fee escrow uses.
        val holdResult = ledgerService.postLedgerTransaction(
            senderWallet.currency,
            listOf(
                LedgerLeg(senderWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Gift sent"),
                LedgerLeg(GIFT_HOLDING_ACCOUNT_ID, LedgerAccountType.GIFT_HOLDING, LedgerDirection.CREDIT, amount, "Gift held in escrow"),
            ),
        )
        val holdTransaction = Transaction(
            id = holdResult.transactionId,
            referenceNumber = "GIFT${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = senderUserId,
            recipientId = recipientUserId,
            fromWalletId = senderWallet.id,
            toWalletId = recipientWallet.id,
            amount = amount,
            fee = BigDecimal.ZERO,
            currency = senderWallet.currency,
            type = TransactionType.TRANSFER,
            status = TransactionStatus.COMPLETED,
            description = "Gift sent",
            completedAt = Instant.now(),
        )
        transactionRepository.save(holdTransaction)

        val trimmedNote = note?.trim()?.take(200)
        val message = messagingService.sendMessage(senderUserId, conversationId, formatGiftBody(amount, trimmedNote, theme))

        return giftRepository.save(
            Gift(
                id = "gift_${UUID.randomUUID()}",
                senderId = senderUserId,
                recipientId = recipientUserId,
                conversationId = conversationId,
                messageId = message.id,
                amount = amount,
                note = trimmedNote,
                theme = theme,
                holdTransactionId = holdTransaction.id,
                expiresAt = Instant.now().plus(Gift.EXPIRY),
            ),
        )
    }

    /** A stranger (not sender or recipient) gets a real 404, same IDOR discipline as
     * every other resource-ownership check in this codebase. */
    fun getGift(userId: String, giftId: String): Gift {
        val gift = giftRepository.findById(giftId).orElseThrow { GiftNotFoundException("Gift not found") }
        if (userId != gift.senderId && userId != gift.recipientId) throw GiftNotFoundException("Gift not found")
        return gift
    }

    /** Real per-thread gift history -- fetched alongside a conversation's messages so
     * the Talk thread can render gift bubbles for whichever messages carry one, same
     * shape as [rw.itunda.marketplace.PriceOfferService]'s own per-conversation offer
     * fetch. IDOR-checked via [MessagingService.getConversationForParticipant] rather
     * than trusting the caller's own claimed userId against each gift row. */
    fun getGiftsForConversation(userId: String, conversationId: String): List<Gift> {
        messagingService.getConversationForParticipant(userId, conversationId)
        return giftRepository.findByConversationId(conversationId)
    }

    @Transactional
    fun claimGift(recipientUserId: String, giftId: String): Gift {
        val gift = giftRepository.findById(giftId).orElseThrow { GiftNotFoundException("Gift not found") }
        if (recipientUserId != gift.senderId && recipientUserId != gift.recipientId) {
            throw GiftNotFoundException("Gift not found")
        }
        if (recipientUserId != gift.recipientId) {
            throw GiftNotRecipientException("Only the recipient can open this gift")
        }
        if (gift.status != GiftStatus.PENDING) {
            throw GiftAlreadyResolvedException("This gift has already been opened or expired")
        }
        if (gift.expiresAt.isBefore(Instant.now())) {
            throw GiftExpiredException("This gift has expired")
        }

        rateLimiter.checkLimit("gift:claim:$recipientUserId", limit = 30, window = Duration.ofHours(1))

        val recipientWallet = walletRepository.findByUserIdAndType(gift.recipientId, WalletType.MAIN)
            ?: throw GiftNoWalletException("No wallet found for this account")

        val claimResult = ledgerService.postLedgerTransaction(
            recipientWallet.currency,
            listOf(
                LedgerLeg(GIFT_HOLDING_ACCOUNT_ID, LedgerAccountType.GIFT_HOLDING, LedgerDirection.DEBIT, gift.amount, "Gift claimed"),
                LedgerLeg(recipientWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, gift.amount, "Gift received"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = claimResult.transactionId,
                referenceNumber = "GIFTCLAIM${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = gift.senderId,
                recipientId = gift.recipientId,
                fromWalletId = null,
                toWalletId = recipientWallet.id,
                amount = gift.amount,
                fee = BigDecimal.ZERO,
                currency = recipientWallet.currency,
                type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED,
                description = "Gift received",
                completedAt = Instant.now(),
            ),
        )

        gift.status = GiftStatus.CLAIMED
        gift.claimTransactionId = claimResult.transactionId
        gift.claimedAt = Instant.now()
        messagingService.sendMessage(recipientUserId, gift.conversationId, "🎁 Gift opened — ${formatAmount(gift.amount)} RWF added to your wallet")
        return giftRepository.save(gift)
    }

    /** Real auto-refund for an unclaimed gift, driven by [GiftExpiryScheduler]. Reverses
     * the exact hold leg pair back to the sender's own wallet -- same reversing-ledger-
     * entry technique `SupportService.reverseTransaction`/order cancellation already use. */
    @Transactional
    fun expireGift(gift: Gift) {
        if (gift.status != GiftStatus.PENDING) return
        val senderWallet = walletRepository.findByUserIdAndType(gift.senderId, WalletType.MAIN) ?: return

        val refundResult = ledgerService.postLedgerTransaction(
            senderWallet.currency,
            listOf(
                LedgerLeg(GIFT_HOLDING_ACCOUNT_ID, LedgerAccountType.GIFT_HOLDING, LedgerDirection.DEBIT, gift.amount, "Unclaimed gift refunded"),
                LedgerLeg(senderWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, gift.amount, "Unclaimed gift refunded"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = refundResult.transactionId,
                referenceNumber = "GIFTEXP${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = gift.recipientId,
                recipientId = gift.senderId,
                fromWalletId = null,
                toWalletId = senderWallet.id,
                amount = gift.amount,
                fee = BigDecimal.ZERO,
                currency = senderWallet.currency,
                type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED,
                description = "Unclaimed gift refunded",
                completedAt = Instant.now(),
            ),
        )

        gift.status = GiftStatus.EXPIRED
        giftRepository.save(gift)
        messagingService.sendMessage(gift.senderId, gift.conversationId, "⏰ Your gift of ${formatAmount(gift.amount)} RWF went unclaimed and was refunded")
    }

    fun getExpiredPendingGifts(): List<Gift> =
        giftRepository.findByStatusAndExpiresAtBefore(GiftStatus.PENDING, Instant.now())
}

private fun formatAmount(amount: BigDecimal): String {
    val plain = amount.stripTrailingZeros().toPlainString()
    val parts = plain.split(".")
    val intPart = parts[0].reversed().chunked(3).joinToString(",").reversed()
    return intPart
}

/** A gift's chat message body is a real, stable, machine-parseable format (same
 * "special message the client recognizes and renders specially" trick
 * `PriceOfferService.formatOfferBody` already established) -- the gift's own real id
 * links back to the [Gift] row via [Gift.messageId], so the client never needs to
 * parse the amount out of this string; it's a human-readable fallback for any client
 * that doesn't special-case gift messages. */
// Real KakaoPay 송금봉투 (money envelope) themed presets -- see GiftTheme's own doc
// comment for the sourced account. Exactly the 4 real, sourced presets; nothing invented.
private fun themeLabel(theme: GiftTheme): String = when (theme) {
    GiftTheme.CONGRATULATIONS -> "🎉 축하해요 (Congratulations)"
    GiftTheme.HEARTFELT -> "💌 내마음 (From the heart)"
    GiftTheme.GOOD_LUCK -> "🍀 행운만땅 (Good luck)"
    GiftTheme.SETTLE_UP -> "🧾 정산해요 (Settling up)"
}

private fun formatGiftBody(amount: BigDecimal, note: String?, theme: GiftTheme? = null): String {
    val amountText = "${formatAmount(amount)} RWF"
    val prefix = theme?.let { "${themeLabel(it)} " } ?: "🎁 "
    return if (note.isNullOrBlank()) "${prefix}Sent a gift: $amountText" else "${prefix}Sent a gift: $amountText — \"$note\""
}
