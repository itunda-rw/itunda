package rw.itunda.maps

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LiveLocationShare
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.LiveLocationShareRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class LiveLocationShareNotFoundException(message: String) : RuntimeException(message)
class LiveLocationShareSelfException(message: String) : RuntimeException(message)
class LiveLocationShareRecipientNotFoundException(message: String) : RuntimeException(message)
class LiveLocationTooManyActiveSharesException(message: String) : RuntimeException(message)
class InvalidLiveLocationShareDurationException(message: String) : RuntimeException(message)
class LiveLocationShareEndedException(message: String) : RuntimeException(message)
class InvalidLiveLocationCoordinateException(message: String) : RuntimeException(message)

/**
 * Real Kakao Map "친구위치" (Friend Location) live location sharing -- see
 * `LiveLocationShare.kt`'s own doc comment for the full real sourcing (including the
 * exact 1-hour-increment/6-hour-max window shape, and why itunda's own v1 is ALWAYS
 * time-bounded, unlike Kakao's own now-superseded unlimited-duration original design).
 *
 * "Live" here means periodically-refreshed, not a persistent push channel -- the same
 * real, honest choice this session's own "don't overclaim 'live' if it's really
 * 'periodically refreshed'" discipline calls for: itunda has no WebSocket/push
 * infrastructure for this, and building one just for this feature would be new,
 * unproven infra on a private cloud already documented (see memory
 * project_itunda_private_cloud.md) to have historically ~0 RAM headroom. A sharer's
 * client pushes a fresh coordinate periodically while sharing is active (same real
 * "client owns when to push, this backend never polls a device" model
 * RideDriverService.updateLocation already establishes); a recipient's client polls
 * [getSharedLocation] on its own schedule to see the latest pushed position.
 */
@Service
class LiveLocationShareService(
    private val liveLocationShareRepository: LiveLocationShareRepository,
    private val userRepository: UserRepository,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        // Real Kakao Map sourcing (v.daum.net, 2026-05-04): a real 1-hour increment,
        // real 6-hour real max, freely adjustable up/down within that ceiling while a
        // share is still active.
        val SHARE_INCREMENT: Duration = Duration.ofHours(1)
        const val MAX_DURATION_HOURS = 6

        // Not a Kakao-sourced number -- itunda's own honest choice, same posture
        // RideTrustedContactService.MAX_TRUSTED_CONTACTS's own real Uber-sourced cap
        // establishes for a different but structurally similar "how many of these can
        // one person have open at once" question. Generous enough for genuine real use
        // (family, a few friends) while still bounding one sharer's own row count.
        const val MAX_ACTIVE_SHARES_PER_SHARER = 10
    }

    @Transactional
    fun startSharing(sharerUserId: String, recipientPhoneNumber: String, durationHours: Int): LiveLocationShare {
        if (durationHours !in 1..MAX_DURATION_HOURS) {
            throw InvalidLiveLocationShareDurationException("Duration must be between 1 and $MAX_DURATION_HOURS hours")
        }
        val recipient = userRepository.findByPhoneNumber(recipientPhoneNumber.trim())
            ?: throw LiveLocationShareRecipientNotFoundException("No itunda account found for this phone number")
        if (recipient.id == sharerUserId) throw LiveLocationShareSelfException("You can't share your location with yourself")
        if (liveLocationShareRepository.countBySharerUserIdAndRevokedFalse(sharerUserId) >= MAX_ACTIVE_SHARES_PER_SHARER) {
            throw LiveLocationTooManyActiveSharesException("You can have at most $MAX_ACTIVE_SHARES_PER_SHARER active location shares at once")
        }
        return liveLocationShareRepository.save(
            LiveLocationShare(
                id = "live_location_share_${UUID.randomUUID()}",
                sharerUserId = sharerUserId,
                recipientUserId = recipient.id,
                expiresAt = Instant.now().plus(SHARE_INCREMENT.multipliedBy(durationHours.toLong())),
            ),
        )
    }

    // Real "client owns when to push a fresh reading" position update -- same 20/min
    // rate limit and reasoning RideDriverService.updateLocation already establishes.
    // Fans out to every one of this sharer's currently-active shares at once (a single
    // real position push updates every real recipient watching it), rather than
    // requiring one call per share -- matches how a real phone only has one real GPS
    // reading to push regardless of how many people are watching it.
    @Transactional
    fun updateMyLocation(sharerUserId: String, latitude: Double, longitude: Double): Int {
        rateLimiter.checkLimit("maps:location-share:update:$sharerUserId", limit = 20, window = Duration.ofMinutes(1))
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidLiveLocationCoordinateException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val now = Instant.now()
        val activeShares = liveLocationShareRepository.findBySharerUserIdAndRevokedFalse(sharerUserId).filter { it.isActive(now) }
        activeShares.forEach { share ->
            share.latitude = latitude
            share.longitude = longitude
            share.locationUpdatedAt = now
        }
        liveLocationShareRepository.saveAll(activeShares)
        return activeShares.size
    }

    // Real extend-while-active, same "freely adjust up to the real max ceiling while
    // running" behavior Kakao's own real feature offers -- capped at MAX_DURATION_HOURS
    // total from the share's own original creation, not MAX_DURATION_HOURS from now
    // (an unbounded "extend" loop could otherwise keep a share alive forever, defeating
    // the whole real point of this being time-bounded).
    @Transactional
    fun extendSharing(sharerUserId: String, shareId: String, additionalHours: Int): LiveLocationShare {
        if (additionalHours !in 1..MAX_DURATION_HOURS) {
            throw InvalidLiveLocationShareDurationException("Additional duration must be between 1 and $MAX_DURATION_HOURS hours")
        }
        val share = liveLocationShareRepository.findByIdAndSharerUserId(shareId, sharerUserId)
            ?: throw LiveLocationShareNotFoundException("Location share not found")
        if (!share.isActive()) throw LiveLocationShareEndedException("This location share has already ended")
        val maxExpiresAt = share.createdAt.plus(SHARE_INCREMENT.multipliedBy(MAX_DURATION_HOURS.toLong()))
        val requestedExpiresAt = share.expiresAt.plus(SHARE_INCREMENT.multipliedBy(additionalHours.toLong()))
        share.expiresAt = if (requestedExpiresAt.isAfter(maxExpiresAt)) maxExpiresAt else requestedExpiresAt
        return liveLocationShareRepository.save(share)
    }

    @Transactional
    fun stopSharing(sharerUserId: String, shareId: String) {
        val share = liveLocationShareRepository.findByIdAndSharerUserId(shareId, sharerUserId)
            ?: throw LiveLocationShareNotFoundException("Location share not found")
        share.revoked = true
        liveLocationShareRepository.save(share)
    }

    // Real IDOR-safe read for the recipient side -- only the actual recipient this
    // share was created for can ever read it, same ownership-check shape every other
    // "fetch one specific row by id" read in this codebase already establishes.
    fun getSharedLocation(recipientUserId: String, shareId: String): LiveLocationShare {
        val share = liveLocationShareRepository.findByIdAndRecipientUserId(shareId, recipientUserId)
            ?: throw LiveLocationShareNotFoundException("Location share not found")
        if (!share.isActive()) throw LiveLocationShareEndedException("This location share has ended")
        return share
    }

    fun myActiveShares(sharerUserId: String): List<LiveLocationShare> =
        liveLocationShareRepository.findBySharerUserIdOrderByCreatedAtDesc(sharerUserId).filter { it.isActive() }

    fun sharedWithMe(recipientUserId: String): List<LiveLocationShare> =
        liveLocationShareRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId).filter { it.isActive() }
}
