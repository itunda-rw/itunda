package rw.itunda.messaging

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Emoticon
import rw.itunda.core.domain.EmoticonAcquisitionSource
import rw.itunda.core.domain.EmoticonPack
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.UserEmoticonPack
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EmoticonPackRepository
import rw.itunda.core.repository.EmoticonRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserEmoticonPackRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class EmoticonPackNotFoundException(message: String) : RuntimeException(message)
class EmoticonNotFoundException(message: String) : RuntimeException(message)
class EmoticonPackAlreadyOwnedException(message: String) : RuntimeException(message)
class EmoticonPackNotOwnedException(message: String) : RuntimeException(message)
class EmoticonNoAccountException(message: String) : RuntimeException(message)
class EmoticonGiftRecipientNotFoundException(message: String) : RuntimeException(message)
class EmoticonGiftToSelfException(message: String) : RuntimeException(message)

/**
 * Real KakaoTalk Emoticon Store -- see EmoticonPack's own doc comment for the full
 * sourcing and honest scoping (no Emoticon Plus subscription tier, still a real,
 * separate, not-attempted-here follow-up). A user buys a pack once through the real
 * account-to-account-style ledger movement every other purchase in this backend already
 * uses (debit the buyer's ACCOUNT, credit the new `EMOTICON_REVENUE` clearing account --
 * itunda's own product, a direct sale, not an escrow hold the way Gift/Marketplace/
 * Booking/Ride money-in-flight is), then can send any emoticon from an owned pack as
 * real message content in both 1:1 and group chat -- structurally distinct from the
 * existing toggleable emoji-reaction row (MessageReaction/GroupMessageReaction), which
 * reacts to someone else's message rather than sending a purchasable sticker as its own
 * message.
 *
 * **Real gifting-a-pack-to-another-user added 2026-07-28**, closing the second of
 * `EmoticonPack.kt`'s own two named follow-ups -- `UserEmoticonPack.source`'s
 * `EmoticonAcquisitionSource.GIFTED` value already existed in the schema from day one,
 * just never had a real code path that produced it until now. `giftPack` reuses
 * `purchasePack`'s exact real ledger movement (the giver pays, same ACCOUNT-debit/
 * `EMOTICON_REVENUE`-credit pair) but credits the *recipient's* `UserEmoticonPack`, not
 * the giver's -- resolved by phone number, the same real convention `P2pService
 * .sendDirect` already establishes for "type in someone's phone number," not a
 * fabricated in-app "friend" concept this codebase doesn't have.
 */
@Service
class EmoticonService(
    private val emoticonPackRepository: EmoticonPackRepository,
    private val emoticonRepository: EmoticonRepository,
    private val userEmoticonPackRepository: UserEmoticonPackRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val ledgerService: LedgerService,
    private val messagingService: MessagingService,
    private val groupMessagingService: GroupMessagingService,
    private val rateLimiter: RateLimiter,
    private val pushNotificationService: PushNotificationService,
    private val fraudRuleEngine: FraudRuleEngine,
) {
    fun listPacks(): List<EmoticonPack> = emoticonPackRepository.findByActiveTrue()

    fun listPackEmoticons(packId: String): List<Emoticon> {
        emoticonPackRepository.findById(packId).orElseThrow { EmoticonPackNotFoundException("Emoticon pack not found") }
        return emoticonRepository.findByPackIdOrderBySortOrderAsc(packId)
    }

    fun listOwnedPacks(userId: String): List<EmoticonPack> {
        val ownedPackIds = userEmoticonPackRepository.findByUserId(userId).map { it.packId }
        if (ownedPackIds.isEmpty()) return emptyList()
        return emoticonPackRepository.findAllById(ownedPackIds)
    }

    @Transactional
    fun purchasePack(userId: String, packId: String): UserEmoticonPack {
        // Real anti-spam limit, same convention every other real free-row-adjacent
        // purchase/creation endpoint in this codebase already carries (GiftService,
        // GroupAccountService, etc).
        rateLimiter.checkLimit("emoticon:purchase:$userId", limit = 20, window = Duration.ofHours(1))

        val pack = emoticonPackRepository.findById(packId).orElseThrow { EmoticonPackNotFoundException("Emoticon pack not found") }
        if (userEmoticonPackRepository.findByUserIdAndPackId(userId, packId) != null) {
            throw EmoticonPackAlreadyOwnedException("You already own this emoticon pack")
        }
        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw EmoticonNoAccountException("No account found for this account")

        val result = ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, pack.price, "Emoticon pack purchase - ${pack.title}"),
                LedgerLeg("emoticon_revenue", LedgerAccountType.EMOTICON_REVENUE, LedgerDirection.CREDIT, pack.price, "Emoticon pack sale - ${pack.title}"),
            ),
        )
        // Real gap found (2026-09-13): real money movement (buyer's ACCOUNT debited)
        // with zero FraudRuleEngine coverage -- CardChargeService.chargeWithCard,
        // GiftService.sendGift, P2pService.pay/send all already have this exact fix,
        // this sibling service never did. recipientUserId is null -- a store purchase
        // has no counterparty the NEW_RECIPIENT rule's shape fits, so only
        // HIGH_VALUE/VELOCITY apply, same reasoning MerchantService.chargeCard's own
        // fix uses.
        fraudRuleEngine.evaluate(userId, null, pack.price, result.transactionId)

        return userEmoticonPackRepository.save(
            UserEmoticonPack(id = "user_emoticon_pack_${UUID.randomUUID()}", userId = userId, packId = packId, source = EmoticonAcquisitionSource.PURCHASED),
        )
    }

    @Transactional
    fun giftPack(giverUserId: String, recipientPhoneNumber: String, packId: String): UserEmoticonPack {
        rateLimiter.checkLimit("emoticon:gift:$giverUserId", limit = 20, window = Duration.ofHours(1))

        val pack = emoticonPackRepository.findById(packId).orElseThrow { EmoticonPackNotFoundException("Emoticon pack not found") }
        val recipient = userRepository.findByPhoneNumber(recipientPhoneNumber.trim())
            ?: throw EmoticonGiftRecipientNotFoundException("No itunda account found for this phone number")
        if (recipient.id == giverUserId) {
            throw EmoticonGiftToSelfException("Send this to someone else -- you can't gift yourself a pack")
        }
        if (userEmoticonPackRepository.findByUserIdAndPackId(recipient.id, packId) != null) {
            throw EmoticonPackAlreadyOwnedException("This recipient already owns this emoticon pack")
        }
        val giverAccount = accountRepository.findByUserIdAndType(giverUserId, AccountType.MAIN)
            ?: throw EmoticonNoAccountException("No account found for this account")

        val result = ledgerService.postLedgerTransaction(
            giverAccount.currency,
            listOf(
                LedgerLeg(giverAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, pack.price, "Emoticon pack gift - ${pack.title}"),
                LedgerLeg("emoticon_revenue", LedgerAccountType.EMOTICON_REVENUE, LedgerDirection.CREDIT, pack.price, "Emoticon pack gift sale - ${pack.title}"),
            ),
        )
        // Real gap found (2026-09-13), same as purchasePack above -- this one DOES
        // have a real counterparty (the recipient resolved by phone number, the same
        // real shape P2pService.sendDirect's own NEW_RECIPIENT rule exists for), so
        // recipientUserId is passed rather than null.
        fraudRuleEngine.evaluate(giverUserId, recipient.id, pack.price, result.transactionId)

        val gifted = userEmoticonPackRepository.save(
            UserEmoticonPack(id = "user_emoticon_pack_${UUID.randomUUID()}", userId = recipient.id, packId = packId, source = EmoticonAcquisitionSource.GIFTED),
        )
        val title = "You received a gift!"
        val body = "Someone sent you the \"${pack.title}\" emoticon pack."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = recipient.id, type = "EMOTICON_PACK_GIFTED",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"packId\":\"$packId\"}",
            ),
        )
        pushNotificationService.sendToUser(recipient.id, title, body, mapOf("packId" to packId))
        return gifted
    }

    /** Ownership check shared by both send paths -- resolves the real emoticon and
     * confirms the caller owns its pack before ever touching MessagingService/
     * GroupMessagingService, so an unowned-emoticon send fails before any message row
     * is created. */
    private fun requireOwnedEmoticon(userId: String, emoticonId: String): Emoticon {
        val emoticon = emoticonRepository.findById(emoticonId).orElseThrow { EmoticonNotFoundException("Emoticon not found") }
        if (userEmoticonPackRepository.findByUserIdAndPackId(userId, emoticon.packId) == null) {
            throw EmoticonPackNotOwnedException("You don't own the pack this emoticon belongs to")
        }
        return emoticon
    }

    fun sendEmoticon(userId: String, conversationId: String, emoticonId: String) =
        messagingService.sendMessage(userId, conversationId, body = "", emoticonId = requireOwnedEmoticon(userId, emoticonId).id)

    fun sendGroupEmoticon(userId: String, groupId: String, emoticonId: String) =
        groupMessagingService.sendMessage(userId, groupId, body = "", emoticonId = requireOwnedEmoticon(userId, emoticonId).id)
}
