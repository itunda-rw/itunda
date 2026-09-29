package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * Real Kakao Map "친구위치" (Friend Location) live location sharing -- distinct from
 * `MapBookmark.isPublic`'s own real folder-share/subscribe (a static, one-time-copied
 * set of saved places), this is a real, moving position shared with one specific
 * person for a bounded window. Sourced directly: Kakao Map's real "친구위치" originally
 * offered unlimited-duration sharing, then added a real 1-hour-increment cap (up to a
 * real 6-hour max, adjustable while active) specifically in response to real privacy
 * concerns about open-ended tracking ("무제한 위치공유, 사생활 논란" -- v.daum.net,
 * 2025-11-18; the 1-hour-cap option itself: v.daum.net, 2026-05-04). itunda's own v1
 * is deliberately ALWAYS time-bounded (no "unlimited" option at all) -- not a scope
 * cut, a real, honest safety choice grounded in that exact same sourced controversy.
 *
 * One-to-one only (sharer -> one recipient), not Kakao's real group-share shape --
 * deliberately simpler for v1, matching `P2pDelayedTransfer`'s own "deliberately
 * simpler than the real-world feature" precedent; group sharing is a real, named,
 * out-of-scope follow-up, not silently dropped.
 *
 * Position updates follow the exact same real "client owns when to push a fresh
 * reading, this backend never polls a device" model `RideDriverService.updateLocation`
 * already establishes (same 20/min rate limit, same reasoning) -- `latitude`/
 * `longitude`/`locationUpdatedAt` are nullable because a share can exist before the
 * sharer's very first location push lands.
 *
 * `revoked` is a real, honest early-stop (mirroring `P2pDelayedTransfer`'s own
 * cancel-before-release shape) -- distinct from simply letting `expiresAt` pass, so a
 * recipient sees a real "they stopped sharing" state rather than the position just
 * going silently stale.
 */
@Entity
@Table(name = "live_location_shares")
class LiveLocationShare(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "sharer_user_id", nullable = false, length = 64)
    val sharerUserId: String,

    @Column(name = "recipient_user_id", nullable = false, length = 64)
    val recipientUserId: String,

    @Column
    var latitude: Double? = null,

    @Column
    var longitude: Double? = null,

    @Column(name = "location_updated_at")
    var locationUpdatedAt: Instant? = null,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", sharerUserId = "", recipientUserId = "", expiresAt = Instant.EPOCH)

    fun isActive(now: Instant = Instant.now()): Boolean = !revoked && now.isBefore(expiresAt)
}
