package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

enum class IdentityVerificationStatus { PENDING, APPROVED, DECLINED, EXPIRED }

/**
 * A real "verify/sign in with itunda" request for an external partner -- closes the
 * "토스인증" (Toss Cert identity-verification) gap named directly by the user, distinct
 * from the existing `Certificate` e-signature feature (that's a user's own document-
 * signing key; this is itunda vouching for a user's real identity TO a third party).
 *
 * Real sourced shape (toss.im/tosscert/docs/guides/integration/user): a partner creates
 * a request server-to-server, the user reviews and approves it inside the itunda app
 * (a real consent screen naming the partner and exactly what will be shared, never a
 * silent auto-approve), the partner then polls for the result and receives the
 * disclosed identity data plus a non-repudiation signature. Rwanda has no CI/DI
 * linkage-token equivalent (Korea's real identity system Toss's own product is built
 * on) -- disclosed fields here are itunda's own real user data
 * (name/phone/kycVerified/birthDate) instead, per the user's own explicit choice of
 * "full KYC-style" disclosure scope over this feature's own scoping question.
 *
 * `disclosedPayloadJson`/`signature` are captured and frozen AT approval time, not
 * re-read live when the partner later polls -- so a user editing their own profile
 * after approving can't retroactively change what a partner already received (and a
 * partner re-polling always sees the exact data that was actually consented to).
 */
@Entity
@Table(name = "identity_verification_requests")
class IdentityVerificationRequest(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "partner_id", nullable = false, length = 64)
    val partnerId: String,

    @Column(name = "user_id", length = 64)
    var userId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: IdentityVerificationStatus = IdentityVerificationStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Real, short-lived window (5 minutes) -- matches Toss's own real tosscert tx flow
    // being time-boxed, and this project's own established real-time-limited-request
    // precedent (P2pPaymentRequest.expiresAt).
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "responded_at")
    var respondedAt: Instant? = null,

    @Column(name = "disclosed_payload_json", length = 2000)
    var disclosedPayloadJson: String? = null,

    @Column(length = 512)
    var signature: String? = null,

    @Version
    var version: Long = 0,
) {
    protected constructor() : this(id = "", partnerId = "", expiresAt = Instant.now())
}
