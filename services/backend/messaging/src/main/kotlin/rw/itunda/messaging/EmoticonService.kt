package rw.itunda.messaging

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Emoticon
import rw.itunda.core.domain.EmoticonAcquisitionSource
import rw.itunda.core.domain.EmoticonPack
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.UserEmoticonPack
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.EmoticonPackRepository
import rw.itunda.core.repository.EmoticonRepository
import rw.itunda.core.repository.UserEmoticonPackRepository
import rw.itunda.core.repository.WalletRepository
import java.time.Duration
import java.util.UUID

class EmoticonPackNotFoundException(message: String) : RuntimeException(message)
class EmoticonNotFoundException(message: String) : RuntimeException(message)
class EmoticonPackAlreadyOwnedException(message: String) : RuntimeException(message)
class EmoticonPackNotOwnedException(message: String) : RuntimeException(message)
class EmoticonNoWalletException(message: String) : RuntimeException(message)

/**
 * Real KakaoTalk Emoticon Store -- see EmoticonPack's own doc comment for the full
 * sourcing and honest scoping (no Emoticon Plus subscription tier, no gifting-a-pack,
 * both real follow-ups). A user buys a pack once through the real wallet-to-wallet-
 * style ledger movement every other purchase in this backend already uses (debit the
 * buyer's WALLET, credit the new `EMOTICON_REVENUE` clearing account -- itunda's own
 * product, a direct sale, not an escrow hold the way Gift/Marketplace/Booking/Ride
 * money-in-flight is), then can send any emoticon from an owned pack as real message
 * content in both 1:1 and group chat -- structurally distinct from the existing
 * toggleable emoji-reaction row (MessageReaction/GroupMessageReaction), which reacts
 * to someone else's message rather than sending a purchasable sticker as its own
 * message.
 */
@Service
class EmoticonService(
    private val emoticonPackRepository: EmoticonPackRepository,
    private val emoticonRepository: EmoticonRepository,
    private val userEmoticonPackRepository: UserEmoticonPackRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val messagingService: MessagingService,
    private val groupMessagingService: GroupMessagingService,
    private val rateLimiter: RateLimiter,
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
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw EmoticonNoWalletException("No wallet found for this account")

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, pack.price, "Emoticon pack purchase - ${pack.title}"),
                LedgerLeg("emoticon_revenue", LedgerAccountType.EMOTICON_REVENUE, LedgerDirection.CREDIT, pack.price, "Emoticon pack sale - ${pack.title}"),
            ),
        )

        return userEmoticonPackRepository.save(
            UserEmoticonPack(id = "user_emoticon_pack_${UUID.randomUUID()}", userId = userId, packId = packId, source = EmoticonAcquisitionSource.PURCHASED),
        )
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
