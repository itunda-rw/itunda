package rw.itunda.marketplace

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.KeywordAlert
import rw.itunda.core.domain.KeywordAlertQuietHours
import rw.itunda.core.domain.Listing
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.KeywordAlertQuietHoursRepository
import rw.itunda.core.repository.KeywordAlertRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class InvalidKeywordException(message: String) : RuntimeException(message)
class KeywordAlertCapReachedException(message: String) : RuntimeException(message)
class KeywordAlertNotFoundException(message: String) : RuntimeException(message)
class InvalidQuietHoursException(message: String) : RuntimeException(message)

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
 * limitation.
 *
 * **Real 방해금지 시간 (quiet hours) closed 2026-07-27**, Karrot's own official
 * separate FAQ (cs.kr.karrotmarket.com/wv/faqs/19): a real per-user start/end time
 * during which no notifications ring. Scoped here to keyword-alert pushes specifically,
 * not a real app-wide mute-everything setting (that would touch every notification
 * call site in this codebase, a much larger, separately-scoped feature). Compared
 * against real `Africa/Kigali` local clock time, not UTC -- see
 * `KeywordAlertQuietHours.kt`'s own doc comment for why.
 */
@Service
class KeywordAlertService(
    private val keywordAlertRepository: KeywordAlertRepository,
    private val keywordAlertQuietHoursRepository: KeywordAlertQuietHoursRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(KeywordAlertService::class.java)

    companion object {
        // Real, sourced cap -- Karrot's own official FAQ names exactly 30 keywords per
        // real user.
        const val MAX_KEYWORDS_PER_USER = 30
        val RWANDA_ZONE: ZoneId = ZoneId.of("Africa/Kigali")
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

    @Transactional
    fun setQuietHours(userId: String, startTime: LocalTime, endTime: LocalTime, enabled: Boolean): KeywordAlertQuietHours {
        if (startTime == endTime) throw InvalidQuietHoursException("Start and end time cannot be the same")
        val existing = keywordAlertQuietHoursRepository.findByUserId(userId)
        val setting = existing ?: KeywordAlertQuietHours(
            id = "keyword_alert_quiet_${UUID.randomUUID()}", userId = userId, startTime = startTime, endTime = endTime, enabled = enabled,
        )
        if (existing != null) {
            setting.startTime = startTime
            setting.endTime = endTime
            setting.enabled = enabled
            setting.updatedAt = Instant.now()
        }
        return keywordAlertQuietHoursRepository.save(setting)
    }

    fun getQuietHours(userId: String): KeywordAlertQuietHours? = keywordAlertQuietHoursRepository.findByUserId(userId)

    // Real wraps-past-midnight-aware window check -- e.g. a real 22:00 start / 08:00
    // end window correctly covers 23:30 AND 03:00, not just times between the two
    // clock values in naive numeric order.
    private fun isWithinQuietHours(quietHours: KeywordAlertQuietHours, now: Instant): Boolean {
        if (!quietHours.enabled) return false
        val localTime = now.atZone(RWANDA_ZONE).toLocalTime()
        return if (quietHours.startTime <= quietHours.endTime) {
            localTime >= quietHours.startTime && localTime < quietHours.endTime
        } else {
            localTime >= quietHours.startTime || localTime < quietHours.endTime
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
            if (matches.isEmpty()) return
            val now = Instant.now()
            val quietHoursByUser = keywordAlertQuietHoursRepository.findByUserIdIn(matches.map { it.userId }.distinct()).associateBy { it.userId }
            for (alert in matches) {
                val quietHours = quietHoursByUser[alert.userId]
                if (quietHours != null && isWithinQuietHours(quietHours, now)) {
                    continue
                }
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
            log.warn("Failed to notify keyword-alert subscribers for listing {}", listing.id, e)
        }
    }
}
