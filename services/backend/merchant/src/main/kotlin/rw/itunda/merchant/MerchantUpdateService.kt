package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.MerchantUpdate
import rw.itunda.core.domain.MerchantUpdateLabel
import rw.itunda.core.domain.MerchantUpdateLike
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.MerchantUpdateLikeRepository
import rw.itunda.core.repository.MerchantUpdateRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InvalidMerchantUpdateException(message: String) : RuntimeException(message)
class MerchantUpdateNotFoundException(message: String) : RuntimeException(message)

/**
 * Real business news/updates feed -- see MerchantUpdate.kt's own doc comment for the
 * full account (itunda Maps redesign, 2026-08-28, direct Naver Map reference: the
 * place-detail 소식 tab). Owner-authenticated create, reusing
 * MerchantRepository.findByOwnerUserId the same way MerchantProfileService's own
 * getMyMerchant already does -- a real business can only post to its own feed, never
 * an arbitrary merchant's.
 */
@Service
class MerchantUpdateService(
    private val merchantRepository: MerchantRepository,
    private val merchantUpdateRepository: MerchantUpdateRepository,
    private val merchantUpdateLikeRepository: MerchantUpdateLikeRepository,
    private val rateLimiter: RateLimiter,
) {
    @Transactional
    fun postUpdate(
        ownerUserId: String,
        label: MerchantUpdateLabel,
        title: String,
        body: String,
        periodStart: Instant?,
        periodEnd: Instant?,
    ): MerchantUpdate {
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        val trimmedTitle = title.trim()
        val trimmedBody = body.trim()
        if (trimmedTitle.isEmpty() || trimmedTitle.length > 200) {
            throw InvalidMerchantUpdateException("Title must be between 1 and 200 characters")
        }
        if (trimmedBody.isEmpty() || trimmedBody.length > 2000) {
            throw InvalidMerchantUpdateException("Body must be between 1 and 2000 characters")
        }
        if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart)) {
            throw InvalidMerchantUpdateException("periodEnd can't be before periodStart")
        }
        return merchantUpdateRepository.save(
            MerchantUpdate(
                id = "merchant_update_${UUID.randomUUID()}", merchantId = merchant.id, label = label,
                title = trimmedTitle, body = trimmedBody, periodStart = periodStart, periodEnd = periodEnd,
            ),
        )
    }

    fun getUpdates(merchantId: String): List<MerchantUpdate> = merchantUpdateRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)

    // Real idempotent toggle -- same shape EatsReviewService.toggleHelpful already
    // establishes (real cached counter, DB-unique constraint as the real concurrency
    // guard, real rate limit from day one).
    @Transactional
    fun toggleLike(userId: String, updateId: String): Boolean {
        val update = merchantUpdateRepository.findById(updateId).orElseThrow { MerchantUpdateNotFoundException("Update not found") }
        rateLimiter.checkLimit("merchant:update:like:$userId", limit = 60, window = Duration.ofMinutes(1))

        val existing = merchantUpdateLikeRepository.findByUpdateIdAndUserId(updateId, userId)
        return if (existing != null) {
            merchantUpdateLikeRepository.delete(existing)
            update.likeCount = (update.likeCount - 1).coerceAtLeast(0)
            merchantUpdateRepository.save(update)
            false
        } else {
            merchantUpdateLikeRepository.save(MerchantUpdateLike(id = "merchant_update_like_${UUID.randomUUID()}", updateId = updateId, userId = userId))
            update.likeCount += 1
            merchantUpdateRepository.save(update)
            true
        }
    }
}
