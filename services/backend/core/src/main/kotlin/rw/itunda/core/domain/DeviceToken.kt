package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class DevicePlatform { ANDROID, IOS, WEB }

/**
 * Real push-notification device-token registration -- see `rw.itunda.core.push`'s own
 * doc comment for the full account. Closes the "push notifications on new bookings"
 * half of Naver Smart Place's own real, sourced feature (`docs/DESIGN_REFERENCES.md`)
 * that the review-reply feature shipped 2026-07-25 explicitly named as still-open, after
 * confirming via a full repo-wide sweep that this backend had zero device-token/push
 * infrastructure anywhere before this.
 *
 * One row per real client install (a user with two phones has two rows), keyed by the
 * real client-generated `token` string itself (unique) so a token migrating to a new
 * `userId` -- e.g. logging out and a different account logging in on the same device --
 * naturally overwrites the stale mapping rather than leaving a duplicate stale row that
 * would leak a push meant for account A to whoever's now signed in on that device.
 */
@Entity
@Table(name = "device_tokens")
class DeviceToken(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    var userId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val platform: DevicePlatform,

    @Column(nullable = false, unique = true, length = 512)
    val token: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", platform = DevicePlatform.ANDROID, token = "")
}
