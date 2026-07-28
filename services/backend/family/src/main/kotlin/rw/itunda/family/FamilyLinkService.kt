package rw.itunda.family

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.FamilyLink
import rw.itunda.core.domain.FamilyLinkStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.FamilyLinkRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class FamilyLinkNotFoundException(message: String) : RuntimeException(message)
class FamilyLinkChildNotFoundException(message: String) : RuntimeException(message)
class FamilyLinkSelfException(message: String) : RuntimeException(message)
class FamilyLinkAlreadyExistsException(message: String) : RuntimeException(message)
class FamilyLinkNotPendingException(message: String) : RuntimeException(message)
class FamilyLinkNotActiveException(message: String) : RuntimeException(message)
class FamilyLinkUnauthorizedException(message: String) : RuntimeException(message)
class FamilyLinkInvalidSpendLimitException(message: String) : RuntimeException(message)
class FamilySpendLimitExceededException(message: String) : RuntimeException(message)

data class FamilyLinkView(val link: FamilyLink, val guardianName: String, val childName: String)
data class ChildOverview(val childUserId: String, val childName: String, val walletBalance: BigDecimal, val recentTransactions: List<Transaction>)

/**
 * Real Toss 유스 (Toss Youth)-style guardian-child account link -- see FamilyLink.kt's
 * own doc comment for the full sourced account and honest scope boundary. Allowance
 * itself reuses the already-real `AutoTransfer`/`ScheduledTransfer` features by design
 * -- this service only ever manages the relationship and read-only oversight, never a
 * second money-movement path.
 */
@Service
class FamilyLinkService(
    private val familyLinkRepository: FamilyLinkRepository,
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val notificationRepository: NotificationRepository,
    private val rateLimiter: RateLimiter,
    private val pushNotificationService: PushNotificationService,
) {
    @Transactional
    fun inviteChild(guardianUserId: String, childPhoneNumber: String): FamilyLink {
        rateLimiter.checkLimit("family:invite:$guardianUserId", limit = 10, window = Duration.ofHours(1))

        val trimmedPhone = childPhoneNumber.trim()
        val child = userRepository.findByPhoneNumber(trimmedPhone) ?: throw FamilyLinkChildNotFoundException("No itunda account found for this phone number")
        if (child.id == guardianUserId) throw FamilyLinkSelfException("Cannot link your own account as a child")

        val existing = familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatusIn(
            guardianUserId, child.id, listOf(FamilyLinkStatus.PENDING, FamilyLinkStatus.ACTIVE),
        )
        if (existing.isNotEmpty()) throw FamilyLinkAlreadyExistsException("A link with this account already exists or is pending")

        val guardian = userRepository.findById(guardianUserId).orElseThrow { FamilyLinkChildNotFoundException("Guardian account not found") }
        val link = familyLinkRepository.save(FamilyLink(id = "familylink_${UUID.randomUUID()}", guardianUserId = guardianUserId, childUserId = child.id))

        run {
            val title = "Family link invitation"
            val body = "${guardian.firstName} ${guardian.lastName} wants to link your account as a family member."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = child.id, type = "FAMILY_LINK_INVITED",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"linkId\":\"${link.id}\"}",
                ),
            )
            // Real push wired in (2026-07-28) -- a real invitation the recipient is
            // expected to act on (accept/decline) is exactly the kind of thing that
            // shouldn't wait for the next in-app poll.
            pushNotificationService.sendToUser(child.id, title, body, mapOf("linkId" to link.id))
        }
        return link
    }

    fun getMyInvitesAsChild(childUserId: String): List<FamilyLink> = familyLinkRepository.findByChildUserIdAndStatus(childUserId, FamilyLinkStatus.PENDING)

    @Transactional
    fun respondToInvite(childUserId: String, linkId: String, accept: Boolean): FamilyLink {
        val link = familyLinkRepository.findByIdAndChildUserId(linkId, childUserId) ?: throw FamilyLinkNotFoundException("Invitation not found")
        if (link.status != FamilyLinkStatus.PENDING) throw FamilyLinkNotPendingException("This invitation has already been responded to")
        link.status = if (accept) FamilyLinkStatus.ACTIVE else FamilyLinkStatus.DECLINED
        link.respondedAt = Instant.now()
        val saved = familyLinkRepository.save(link)

        if (accept) {
            val child = userRepository.findById(childUserId).orElse(null)
            val title = "Family link accepted"
            val body = "${child?.firstName ?: "Your family member"} accepted your family link invitation."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = link.guardianUserId, type = "FAMILY_LINK_ACCEPTED",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"linkId\":\"${link.id}\"}",
                ),
            )
            pushNotificationService.sendToUser(link.guardianUserId, title, body, mapOf("linkId" to link.id))
        }
        return saved
    }

    fun getMyChildren(guardianUserId: String): List<FamilyLinkView> =
        familyLinkRepository.findByGuardianUserIdAndStatus(guardianUserId, FamilyLinkStatus.ACTIVE).map(::toView)

    fun getMyGuardians(childUserId: String): List<FamilyLinkView> =
        familyLinkRepository.findByChildUserIdAndStatus(childUserId, FamilyLinkStatus.ACTIVE).map(::toView)

    private fun toView(link: FamilyLink): FamilyLinkView {
        val guardian = userRepository.findById(link.guardianUserId).orElse(null)
        val child = userRepository.findById(link.childUserId).orElse(null)
        return FamilyLinkView(
            link = link,
            guardianName = guardian?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown",
            childName = child?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown",
        )
    }

    @Transactional
    fun revokeLink(userId: String, linkId: String): FamilyLink {
        val link = familyLinkRepository.findByIdAndGuardianUserId(linkId, userId)
            ?: familyLinkRepository.findByIdAndChildUserId(linkId, userId)
            ?: throw FamilyLinkNotFoundException("Family link not found")
        if (link.status != FamilyLinkStatus.ACTIVE) throw FamilyLinkNotActiveException("Only an active family link can be revoked")
        link.status = FamilyLinkStatus.REVOKED
        link.respondedAt = Instant.now()
        return familyLinkRepository.save(link)
    }

    // Real spend-limit enforcement (2026-07-27) -- see FamilyLink.kt's own doc comment
    // for the full sourced account. Guardian-only, gated by a real ACTIVE link with
    // that child -- same authorization shape getChildOverview already established.
    // null clears the limit (an honest, explicit opt-out, not just "very large number").
    @Transactional
    fun setSpendLimit(guardianUserId: String, childUserId: String, dailySpendLimit: BigDecimal?): FamilyLink {
        val link = familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatus(guardianUserId, childUserId, FamilyLinkStatus.ACTIVE)
            ?: throw FamilyLinkUnauthorizedException("No active family link with this account")
        if (dailySpendLimit != null && dailySpendLimit <= BigDecimal.ZERO) {
            throw FamilyLinkInvalidSpendLimitException("Spend limit must be greater than zero")
        }
        link.dailySpendLimit = dailySpendLimit
        return familyLinkRepository.save(link)
    }

    // Real spend-limit enforcement -- see FamilyLink.kt's own doc comment. Called from
    // P2pService.sendDirect before the real ledger movement, matching this codebase's
    // "the real gate must fire before money moves" discipline used everywhere else
    // (WalletFrozenException, minOrderAmount, etc). A no-op (not an exception) when the
    // sender isn't a child on any ACTIVE link with a real limit set -- the overwhelming
    // common case, and this must stay cheap for every single real P2P send in the app.
    fun enforceSpendLimit(childUserId: String, amount: BigDecimal) {
        val link = familyLinkRepository.findByChildUserIdAndStatusAndDailySpendLimitIsNotNull(childUserId, FamilyLinkStatus.ACTIVE) ?: return
        val limit = link.dailySpendLimit ?: return
        val startOfDayUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant()
        val spentToday = transactionRepository
            .findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(childUserId, TransactionType.TRANSFER, TransactionStatus.COMPLETED, startOfDayUtc)
            .sumOf { it.amount }
        if (spentToday.add(amount) > limit) {
            throw FamilySpendLimitExceededException("This transfer would exceed your real daily spend limit set by your guardian")
        }
    }

    // Real read-only oversight -- the guardian's own view of a real child's wallet
    // balance and transaction history, gated by a real ACTIVE FamilyLink, never a
    // spend-limit enforcement mechanism (see FamilyLink.kt's own doc comment for why).
    fun getChildOverview(guardianUserId: String, childUserId: String): ChildOverview {
        val link = familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatus(guardianUserId, childUserId, FamilyLinkStatus.ACTIVE)
            ?: throw FamilyLinkUnauthorizedException("No active family link with this account")
        val child = userRepository.findById(childUserId).orElseThrow { FamilyLinkChildNotFoundException("Child account not found") }
        val wallet = walletRepository.findByUserIdAndType(childUserId, WalletType.MAIN)
        val transactions = transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(childUserId, childUserId).take(20)
        return ChildOverview(
            childUserId = childUserId,
            childName = "${child.firstName} ${child.lastName}",
            walletBalance = wallet?.balance ?: BigDecimal.ZERO,
            recentTransactions = transactions,
        )
    }
}
