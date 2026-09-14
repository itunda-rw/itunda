package rw.itunda.commerce

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.AffiliateCommission
import rw.itunda.core.domain.AffiliateLink
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AffiliateCommissionRepository
import rw.itunda.core.repository.AffiliateLinkRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AffiliateProductNotFoundException(message: String) : RuntimeException(message)
class AffiliateLinkNotFoundException(message: String) : RuntimeException(message)

/**
 * Real 쿠팡파트너스 (Coupang Partners)-style affiliate link program -- see
 * `AffiliateLink.kt`'s own doc comment for the full sourced account. Any itunda
 * user generates a real trackable link for any real Shop product; when a real
 * order is later placed carrying that link's code, the referrer earns a real 3%
 * commission (Coupang's own published regular-partner rate), funded from itunda's
 * own `FEE_REVENUE` house account -- the actual attribution/payout leg lives in
 * `OrderService.placeOrder`, this service owns link creation/lookup/click-tracking
 * and the caller-facing "my links"/"my commissions" reads.
 */
@Service
class AffiliateService(
    private val affiliateLinkRepository: AffiliateLinkRepository,
    private val affiliateCommissionRepository: AffiliateCommissionRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(AffiliateService::class.java)

    companion object {
        // Coupang's own real, published regular-partner commission rate.
        val COMMISSION_RATE: BigDecimal = BigDecimal("0.03")
    }

    @Transactional
    fun createLink(userId: String, productId: String): AffiliateLink {
        rateLimiter.checkLimit("affiliate:create-link:$userId", limit = 20, window = Duration.ofHours(1))
        if (!merchantProductRepository.existsById(productId)) {
            throw AffiliateProductNotFoundException("Product not found")
        }
        var code: String
        do {
            code = "AF" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        } while (affiliateLinkRepository.findByCode(code) != null)
        return affiliateLinkRepository.save(AffiliateLink(id = "affiliate_link_${UUID.randomUUID()}", userId = userId, productId = productId, code = code))
    }

    fun getMyLinks(userId: String): List<AffiliateLink> = affiliateLinkRepository.findByUserIdOrderByCreatedAtDesc(userId)

    fun getMyCommissions(userId: String): List<AffiliateCommission> = affiliateCommissionRepository.findByReferrerIdOrderByCreatedAtDesc(userId)

    /** Best-effort click record -- see AffiliateLink.kt's own doc comment for why this
     * never gates whether a commission is paid. Real-404s if the code is unknown so a
     * client can show an honest "this link is invalid" state rather than a silent no-op.
     *
     * Real gap found live during a security review (2026-08-02): unlike every other
     * `RateLimiter.checkLimit` call in this codebase, all of which key on an
     * authenticated caller's own userId, this endpoint has no auth at all -- a shared
     * link must resolve for anyone who clicks it, including a browser with no itunda
     * session. With no rate limit, a bot could hammer one real code to inflate
     * `clickCount` (the referrer's own visible "how many people clicked my link"
     * metric) arbitrarily. Rate-limited by the CODE itself rather than a caller
     * identity that doesn't exist here -- bounds repeated hits on any single real link
     * without needing new unauthenticated-request infrastructure (no IP-based limiter
     * precedent exists anywhere in this codebase to extend instead). Code enumeration
     * across many different codes is a real, separate, lower-severity concern this
     * doesn't address -- honestly named, not silently ignored: itunda's own 6-char
     * hex code space (16^6, ~16.7M) makes blind enumeration a slow, real-cost attack
     * this scope doesn't need to solve today. */
    @Transactional
    fun resolveLink(code: String): AffiliateLink {
        rateLimiter.checkLimit("affiliate:resolve:$code", limit = 30, window = Duration.ofHours(1))
        val link = affiliateLinkRepository.findByCode(code) ?: throw AffiliateLinkNotFoundException("This link is invalid or has expired")
        link.clickCount += 1
        return affiliateLinkRepository.save(link)
    }

    /** Called by OrderService.placeOrder once a real order is committed -- never blocks
     * or rolls back the order itself if anything here is off (unknown/self code), same
     * "purely additive, zero regression risk" discipline this session applies to every
     * hook into an already-tested checkout path. */
    @Transactional
    fun payCommissionIfReferred(referralCode: String?, orderId: String, buyerId: String, orderTotal: BigDecimal) {
        if (referralCode.isNullOrBlank()) return
        val link = affiliateLinkRepository.findByCode(referralCode) ?: return
        if (link.userId == buyerId) return
        val referrerAccount = accountRepository.findByUserIdAndType(link.userId, rw.itunda.core.domain.AccountType.MAIN) ?: return
        val commission = orderTotal.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP)
        if (commission <= BigDecimal.ZERO) return
        val result = ledgerService.postLedgerTransaction(
            referrerAccount.currency,
            listOf(
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.DEBIT, commission, "Affiliate commission payout"),
                LedgerLeg(referrerAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, commission, "Affiliate commission"),
            ),
        )
        affiliateCommissionRepository.save(
            AffiliateCommission(
                id = "affiliate_commission_${UUID.randomUUID()}", linkId = link.id, referrerId = link.userId,
                orderId = orderId, buyerId = buyerId, commissionAmount = commission, payoutTransactionId = result.transactionId,
            ),
        )
        notifyCommissionEarned(link.userId, commission)
    }

    // Real whole-class zero-notification gap found live (2026-09-14, same sweep that
    // fixed ShoppingCashbackService/RewardsService/StepRewardService/
    // ShoppingMissionService): the strongest instance of this gap found all day -- the
    // referrer isn't even a party to the HTTP request that triggers this (a DIFFERENT
    // user's order, via OrderService.placeOrder, credits them), so unlike every other
    // fix in this family there was never any synchronous UI feedback to fall back on
    // at all. Without this, a referrer had literally no way to ever learn they earned
    // a commission short of noticing an unexplained credit in their own history.
    private fun notifyCommissionEarned(referrerUserId: String, commission: BigDecimal) {
        val title = "You earned an affiliate commission"
        val body = "You earned ${commission.toPlainString()} RWF in affiliate commission from a purchase through your link."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = referrerUserId, type = "AFFILIATE_COMMISSION_EARNED",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{}",
            ),
        )
        sendPushAfterCommit(referrerUserId, title, body)
    }

    private fun sendPushAfterCommit(userId: String, title: String, body: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body)
            } catch (e: Exception) {
                log.warn("Could not send affiliate-commission push to user {}", userId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
