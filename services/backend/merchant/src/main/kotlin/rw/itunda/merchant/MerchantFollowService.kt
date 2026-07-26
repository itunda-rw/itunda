package rw.itunda.merchant

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.MerchantFollow
import rw.itunda.core.domain.Notification
import rw.itunda.core.repository.MerchantFollowRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InvalidBroadcastException(message: String) : RuntimeException(message)

data class FollowedMerchant(val merchantId: String, val businessName: String, val category: String?, val followedAt: Instant)
data class BroadcastResult(val recipientCount: Int)

/**
 * Real Naver Smart Store-style "알림받기" (follow a store for notices) -- see
 * [rw.itunda.core.domain.MerchantFollow]'s own doc comment for the full sourced
 * account. `follow`/`unfollow` mirror `ProductFavoriteService`/`EatsFavoriteService`'s
 * exact idempotent/silent-no-op discipline.
 *
 * `broadcastToFollowers` is the real, distinguishing capability a bookmark/favorite
 * doesn't have: the merchant's own authenticated account can push a real promotional
 * notice to every real follower. Real anti-spam bound -- Naver's own published material
 * doesn't specify an exact broadcast rate limit, so [BROADCAST_LIMIT]/[BROADCAST_WINDOW]
 * are itunda's own honest, self-imposed choice (a merchant blasting followers hourly
 * would be real spam, not a real store's genuine promotional cadence), the same
 * "no bound = abuse vector" discipline `MerchantService.chargeCard`'s own rate limit
 * already established for a different real risk.
 */
@Service
class MerchantFollowService(
    private val merchantFollowRepository: MerchantFollowRepository,
    private val merchantRepository: MerchantRepository,
    private val notificationRepository: NotificationRepository,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        const val BROADCAST_LIMIT = 3
        val BROADCAST_WINDOW: Duration = Duration.ofDays(1)
    }

    @Transactional
    fun follow(userId: String, merchantId: String): MerchantFollow {
        merchantRepository.findById(merchantId).orElseThrow { MerchantNotFoundException("Merchant not found") }
        merchantFollowRepository.findByUserIdAndMerchantId(userId, merchantId)?.let { return it }
        return merchantFollowRepository.save(
            MerchantFollow(id = "merchant_follow_${UUID.randomUUID()}", userId = userId, merchantId = merchantId),
        )
    }

    @Transactional
    fun unfollow(userId: String, merchantId: String) {
        merchantFollowRepository.deleteByUserIdAndMerchantId(userId, merchantId)
    }

    fun getMyFollowedMerchants(userId: String, pageable: Pageable): Page<FollowedMerchant> {
        val page = merchantFollowRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        val merchantsById = merchantRepository.findAllById(page.content.map { it.merchantId }).associateBy { it.id }
        return page.map { follow ->
            val merchant = merchantsById[follow.merchantId]
            FollowedMerchant(
                merchantId = follow.merchantId,
                businessName = merchant?.businessName ?: "Merchant no longer available",
                category = merchant?.category,
                followedAt = follow.createdAt,
            )
        }
    }

    // Real "관심고객" (interested-customer) follower count -- shown to the merchant
    // owner about their own store, matching Naver's own real seller-facing metric.
    fun getFollowerCount(ownerUserId: String): Long {
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        return merchantFollowRepository.countByMerchantId(merchant.id)
    }

    @Transactional
    fun broadcastToFollowers(ownerUserId: String, title: String, body: String): BroadcastResult {
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        val trimmedTitle = title.trim()
        val trimmedBody = body.trim()
        if (trimmedTitle.isEmpty() || trimmedTitle.length > 100) {
            throw InvalidBroadcastException("Title must be between 1 and 100 characters")
        }
        if (trimmedBody.isEmpty() || trimmedBody.length > 500) {
            throw InvalidBroadcastException("Body must be between 1 and 500 characters")
        }

        rateLimiter.checkLimit("merchant:broadcast:${merchant.id}", limit = BROADCAST_LIMIT, window = BROADCAST_WINDOW)

        val followers = merchantFollowRepository.findByMerchantId(merchant.id)
        followers.forEach { follow ->
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = follow.userId, type = "MERCHANT_BROADCAST",
                    title = trimmedTitle, body = trimmedBody, isRead = false, createdAt = Instant.now(),
                    dataJson = "{\"merchantId\":\"${merchant.id}\",\"businessName\":\"${merchant.businessName}\"}",
                ),
            )
        }
        return BroadcastResult(followers.size)
    }
}
