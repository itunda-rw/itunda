package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real Face Pay enrollment record -- closes docs/TOSS_PARITY_MATRIX.md's Face Pay row
 * ("blocked on... a real terminal concept"). Toss's real FacePay is a named, sourced
 * product (docs/TOSS_ARCHITECTURE_FACTS.md §6: 2M+ registered users, 240,000+
 * participating merchants, the first facial-payment product to get preliminary review
 * from Korea's PIPC privacy regulator).
 *
 * Deliberately stores no biometric data whatsoever -- not a face template, not an
 * image, not a hash of one. The actual biometric match happens on-device (this
 * backend's own NIDABiometricAuth/BiometricPrompt already do this, matching how real
 * Face ID/BiometricPrompt work: the template lives in the device's secure enclave/TEE
 * and never leaves it). This row only ever records *that* a user has turned Face Pay
 * on for their account -- functionally identical to a boolean setting, split into its
 * own table because a real feature needs a real enrollment timestamp and revocation
 * history, not because it holds anything sensitive. Given FacePay's own real PIPC
 * regulatory scrutiny (a biometric-specific privacy review), storing anything more
 * than this on a server would be a real, meaningful liability this design avoids on
 * purpose, not an oversight.
 */
@Entity
@Table(name = "facepay_enrollments")
class FacePayEnrollment(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "enrolled_at", nullable = false)
    var enrolledAt: Instant = Instant.now(),

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
) {
    protected constructor() : this(id = "", userId = "")
}
