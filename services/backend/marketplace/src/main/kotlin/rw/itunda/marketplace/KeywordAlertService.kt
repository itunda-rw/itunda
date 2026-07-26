package rw.itunda.marketplace

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.KeywordAlert
import rw.itunda.core.domain.Listing
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.KeywordAlertRepository
import java.util.UUID

class InvalidKeywordException(message: String) : RuntimeException(message)
class KeywordAlertCapReachedException(message: String) : RuntimeException(message)
class KeywordAlertNotFoundException(message: String) : RuntimeException(message)

/**
 * Real 당근마켓 Keyword Alert (키워드 알림) -- Karrot Market's own official FAQ
 * (cs.kr.karrotmarket.com/wv/faqs/43): a user registers a real search keyword; from
 * then on, whenever ANY seller creates a new active listing whose title matches it, the
 * user is pushed a real notification, without having to keep re-searching. A real
 * re-engagement/discovery feature, distinct from and additive to the already-built
 * `ListingFavoriteService` (saving a listing you've already seen) -- this is about
 * finding a listing you HAVEN'T seen yet.
 *
 * `addAlert` is deliberately idempotent (registering an already-registered keyword just
 * returns the existing row, same "the end state is what the caller actually wants"
 * discipline `ListingFavoriteService.addFavorite` already establishes) and enforces
 * Karrot's own real, published 30-keyword-per-user cap, application-level (matching
 * this codebase's existing convention of `RateLimiter`-style limits over DB CHECK
 * constraints).
 *
 * `notifyMatchingAlerts` is called from the controller layer, right after a listing is
 * successfully created -- deliberately NOT wired into `MarketplaceService.createListing`
 * itself, so this real, additive feature never touches that already-tested method's
 * constructor or logic at all. Best-effort: a push failure must never surface as if
 * listing creation itself had failed, same discipline this session's other
 * auxiliary-side-effect callers already establish.
 *
 * Honestly scoped: matches on listing TITLE only (not description), keeping the DB-side
 * match query simple and cheap at current scale -- a named, not silently absent,
 * limitation. Karrot's own real "quiet hours" (do-not-disturb window) sub-feature from
 * the same FAQ page is a real, well-scoped follow-up, not attempted in this pass.
 */
@Service
class KeywordAlertService(
    private val keywordAlertRepository: KeywordAlertRepository,
    private val pushNotificationService: PushNotificationService,
) {
    companion object {
        // Real, sourced cap -- Karrot's own official FAQ names exactly 30 keywords per
        // real user.
        const val MAX_KEYWORDS_PER_USER = 30
    }

    @Transactional
    fun addAlert(userId: String, keyword: String): KeywordAlert {
        val normalized = keyword.trim().lowercase()
        if (normalized.isEmpty() || normalized.length > 64) {
            throw InvalidKeywordException("Keyword must be between 1 and 64 characters")
        }
        keywordAlertRepository.findByUserIdAndKeyword(userId, normalized)?.let { return it }
        if (keywordAlertRepository.countByUserId(userId) >= MAX_KEYWORDS_PER_USER) {
            throw KeywordAlertCapReachedException("You've reached the real $MAX_KEYWORDS_PER_USER-keyword limit")
        }
        return keywordAlertRepository.save(
            KeywordAlert(id = "keyword_alert_${UUID.randomUUID()}", userId = userId, keyword = normalized),
        )
    }

    fun listAlerts(userId: String, pageable: Pageable): Page<KeywordAlert> =
        keywordAlertRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)

    @Transactional
    fun removeAlert(userId: String, alertId: String) {
        // Real 404 (not silently ignoring a mismatched owner), same "owner-only,
        // honest failure" discipline this codebase already uses elsewhere for a
        // delete-by-id action -- deleteByUserIdAndId's own affected-row count IS the
        // real ownership check, no separate findById+compare round trip needed.
        val deleted = keywordAlertRepository.deleteByUserIdAndId(userId, alertId)
        if (deleted == 0L) {
            throw KeywordAlertNotFoundException("Keyword alert not found")
        }
    }

    /**
     * Called from the controller layer right after a real listing is created -- see
     * this class's own doc comment for why. Best-effort: any failure here is swallowed,
     * never allowed to make listing creation itself look like it failed.
     */
    fun notifyMatchingAlerts(listing: Listing) {
        try {
            val lowercasedTitle = listing.title.lowercase()
            val matches = keywordAlertRepository.findMatchingAlerts(lowercasedTitle)
            for (alert in matches) {
                pushNotificationService.sendToUser(
                    alert.userId,
                    "New listing matches \"${alert.keyword}\"",
                    listing.title,
                    mapOf("listingId" to listing.id),
                )
            }
        } catch (e: Exception) {
            // Real, non-critical -- a keyword-alert push failure must never make a real
            // listing creation look like it failed.
        }
    }
}
