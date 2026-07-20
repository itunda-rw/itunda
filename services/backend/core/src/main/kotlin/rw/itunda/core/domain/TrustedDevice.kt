package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real device binding (2026-07-20), modeled directly on Toss's own real, published
 * Gateway architecture (toss.tech/article/slash23-server): their gateway issues a
 * "Passport" token carrying both user AND device identity, and every request is checked
 * against it -- a valid credential alone was never treated as sufficient. Itunda's own
 * JWTs never carried any device binding at all, meaning a leaked/stolen access token
 * would work from any device, forever, until it naturally expired -- a real gap this
 * closes without depending on any third-party identity provider (SMS carrier
 * verification, a device-attestation vendor, etc.), matching this whole project's
 * standing "self-hosted, not third-party" discipline.
 *
 * A device is either the one a user registered on (auto-trusted -- see
 * DeviceService.recordRegistrationDevice, since a fresh signup already proved password
 * ownership on that exact device) or one seen for the first time at login (recorded as
 * real but NOT yet trusted, until the user completes a real step-up re-verification --
 * see DeviceService.verifyDevice). `deviceId` is a real, stable, client-generated
 * identifier (a UUID persisted in local storage/Keychain/SharedPreferences), never a
 * derived fingerprint -- itunda has no interest in silently fingerprinting a user's
 * hardware, only in recognizing "the same install of the app that logged in before."
 */
@Entity
@Table(name = "trusted_devices")
class TrustedDevice(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "device_id", nullable = false, length = 128)
    val deviceId: String,

    @Column(name = "device_name", length = 200)
    var deviceName: String?,

    @Column(nullable = false)
    var trusted: Boolean,

    @Column(name = "first_seen_at", nullable = false)
    val firstSeenAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,
) {
    protected constructor() : this(id = "", userId = "", deviceId = "", deviceName = null, trusted = false)
}
