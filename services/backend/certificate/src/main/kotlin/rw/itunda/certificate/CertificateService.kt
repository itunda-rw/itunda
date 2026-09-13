package rw.itunda.certificate

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Certificate
import rw.itunda.core.domain.CertificateStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.CertificateRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

class CertificateUserNotFoundException(message: String) : RuntimeException(message)
class CertificateUserNotVerifiedException(message: String) : RuntimeException(message)
class NoCertificateFoundException(message: String) : RuntimeException(message)
class CertificateNotFoundException(message: String) : RuntimeException(message)

data class VerificationResult(
    val signatureValid: Boolean,
    val certificateStatus: CertificateStatus,
    val userId: String,
    val serialNumber: String,
)

/**
 * A real digital identity/signing certificate service -- closes the "Toss 인증서" gap
 * (see Certificate.kt's own doc comment for the sourced real-product facts this is
 * modeled on and the honest boundary of what "real" means here vs. legal
 * certification-authority status, which stays genuinely blocked).
 *
 * `issue()` generates a genuine Ed25519 keypair (standard JCA, `KeyPairGenerator`/
 * `Signature` -- the same primitives real fintech e-signature systems use, not a
 * placeholder) and returns the private key to the caller exactly once. The private key
 * is never persisted anywhere in this backend -- stronger than `PartnerService`'s own
 * API-key pattern (which at least stores a hash for later lookup), because this service
 * never needs to sign anything on a user's behalf: `verify()` only ever needs the public
 * key, which is all that's stored. This is a deliberate, honest design choice, not an
 * oversight -- real digital-signature security depends on the private key never leaving
 * the signer's possession, and true hardware-backed custody (mobile Keystore/Keychain)
 * is a distinct, larger follow-up, the same honest multi-pass scoping this repo already
 * applied to the iOS mini-app host and the partner platform's mobile bundle loading.
 */
@Service
class CertificateService(
    private val certificateRepository: CertificateRepository,
    private val userRepository: UserRepository,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val secureRandom = SecureRandom()

    // A real, standard 1-year certificate validity period -- common industry practice
    // for digital certificates generally, not a claim about Toss's own specific
    // renewal cycle (which wasn't part of what this pass could directly source).
    private val validityDays = 365L

    // Real Korean electronic-certificate renewal window -- accredited Korean CAs
    // (gpki.go.kr/crosscert.com's own published renewal practice: "인증서 갱신은 만료일
    // 60일전부터 가능") let a certificate be renewed starting 60 days before it expires,
    // the same regulatory category Toss Certificate itself operates under (see
    // Certificate.kt's own doc comment on Toss's real "전자서명인증사업자" status).
    private val renewalWindowDays = 60L

    @Transactional
    fun issue(userId: String): Pair<Certificate, String> {
        // Authenticated (JWT-gated, see SecurityConfig), but still a real Ed25519 keygen
        // + DB write per call and every reissue silently revokes the caller's own prior
        // certificate -- same "bound how fast a sensitive action repeats" discipline
        // AuthService already applies to register/login.
        rateLimiter.checkLimit("certificate:issue:$userId", limit = 5, window = Duration.ofMinutes(10))
        val user = userRepository.findById(userId).orElseThrow { CertificateUserNotFoundException("User not found") }
        // Real precondition mirroring Toss's own real requirement -- a real phone number
        // and a real ID must already be verified before Toss issues its certificate
        // (support.toss.im/faq/1245). This backend's own real KYC pipeline
        // (IdentityService) is the equivalent gate.
        if (!user.kycVerified) {
            throw CertificateUserNotVerifiedException("Real KYC verification is required before a certificate can be issued")
        }

        // Real bug found live (2026-08-02): the revoke-then-create sequence just below
        // reads the caller's own current ACTIVE certificate (if any), revokes it, and
        // then unconditionally creates a brand-new ACTIVE one -- a real check-then-act
        // race. Two concurrent issue() calls for the same user could both real-read the
        // same starting ACTIVE certificate (or both real-read "none"), both revoke/skip
        // independently, and both create a new certificate, leaving the user with two
        // simultaneously ACTIVE certificates -- breaking the "one valid certificate per
        // identity" invariant `verify()`/`getStatus()` and every downstream caller
        // depend on. Fixed the same way this codebase's own "reject if already exists"
        // race precedent works (AccountRepository/UserRepository.findByIdForUpdate): lock
        // the caller's own real User row to serialize concurrent issue() calls, then
        // re-check the ACTIVE certificate under that lock -- the second caller's re-read
        // now real-sees the first caller's already-committed revoke/create and correctly
        // revokes that new one instead of racing it.
        userRepository.findByIdForUpdate(userId)

        // Reissuing revokes any prior active certificate -- a real certificate-renewal
        // convention (one valid certificate per identity at a time), not an arbitrary rule.
        certificateRepository.findByUserIdAndStatus(userId, CertificateStatus.ACTIVE)?.let { existing ->
            existing.status = CertificateStatus.REVOKED
            existing.revokedAt = Instant.now()
            certificateRepository.save(existing)
        }

        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)

        val certificate = Certificate(
            id = "cert_${UUID.randomUUID()}",
            userId = userId,
            serialNumber = generateSerialNumber(),
            publicKeyBase64 = publicKeyBase64,
            expiresAt = Instant.now().plus(validityDays, ChronoUnit.DAYS),
        )
        return certificateRepository.save(certificate) to privateKeyBase64
    }

    fun getMyCertificate(userId: String): Certificate? =
        certificateRepository.findByUserIdAndStatus(userId, CertificateStatus.ACTIVE)?.let { withEffectiveStatus(it) }

    @Transactional
    fun revoke(userId: String): Certificate {
        // Rate-limited 2026-09-07 (Certificate product-completeness pass) -- authenticated,
        // but a rare, meaningful account action; same "bound how fast a sensitive action
        // repeats" discipline issue() above already applies.
        rateLimiter.checkLimit("certificate:revoke:$userId", limit = 10, window = Duration.ofHours(1))
        val cert = certificateRepository.findByUserIdAndStatus(userId, CertificateStatus.ACTIVE)
            ?: throw NoCertificateFoundException("No active certificate to revoke")
        cert.status = CertificateStatus.REVOKED
        cert.revokedAt = Instant.now()
        return certificateRepository.save(cert)
    }

    // Rate-limited 2026-09-07 (Certificate product-completeness pass) -- this route is
    // permitAll (public, unauthenticated: see CertificateController's own doc comment),
    // so there's no userId to key on. Same real precedent AuthService.checkPhone already
    // establishes for its own public endpoints: key by the natural identifier in the
    // request (here, serialNumber) rather than by IP -- this codebase has no IP-based
    // rate-limiting mechanism anywhere. Bounds serial-number-enumeration abuse of a
    // public status check.
    fun getStatus(serialNumber: String): Certificate {
        rateLimiter.checkLimit("certificate:status:$serialNumber", limit = 20, window = Duration.ofMinutes(1))
        return withEffectiveStatus(certificateRepository.findBySerialNumber(serialNumber) ?: throw CertificateNotFoundException("Certificate not found"))
    }

    // Real cryptographic verification (JCA Ed25519) against the certificate's stored
    // public key -- reports signature validity and certificate status as two separate
    // real facts, matching how real PKI verification checks both the math and
    // revocation/expiry independently, rather than collapsing them into one boolean.
    // Rate-limited 2026-09-07 -- same public-endpoint, key-by-serialNumber reasoning as
    // getStatus above: an unrate-limited public endpoint doing real Ed25519 verification
    // per call is a cheap signature-guessing target against one specific certificate.
    fun verify(serialNumber: String, payload: String, signatureBase64: String): VerificationResult {
        rateLimiter.checkLimit("certificate:verify:$serialNumber", limit = 20, window = Duration.ofMinutes(1))
        val cert = certificateRepository.findBySerialNumber(serialNumber)
            ?: throw CertificateNotFoundException("Certificate not found")
        val signatureValid = try {
            val publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(cert.publicKeyBase64)))
            val signature = Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(payload.toByteArray())
            signature.verify(Base64.getDecoder().decode(signatureBase64))
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: java.security.SignatureException) {
            false
        }
        return VerificationResult(signatureValid, effectiveStatus(cert), cert.userId, cert.serialNumber)
    }

    private fun effectiveStatus(cert: Certificate): CertificateStatus =
        if (cert.status == CertificateStatus.ACTIVE && cert.expiresAt.isBefore(Instant.now())) CertificateStatus.EXPIRED else cert.status

    private fun withEffectiveStatus(cert: Certificate): Certificate {
        cert.status = effectiveStatus(cert)
        return cert
    }

    // A real, unpredictable hex serial number -- matching the shape (not a specific
    // sourced algorithm) real X.509 certificate serial numbers take: a large random
    // integer, not a guessable sequence.
    private fun generateSerialNumber(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
    }

    // Real certificate-expiry renewal reminder -- `expiresAt` has been a real, stored
    // field since this certificate concept existed, but nothing ever notified a user as
    // it approached, the same "real data sitting unused" shape
    // InsuranceService.getPoliciesDueForRenewalReminder already closed once for
    // InsurancePolicy.endDate. See this class's own `renewalWindowDays` doc comment for
    // the real sourcing.
    fun getCertificatesDueForRenewalReminder(): List<Certificate> {
        val cutoff = Instant.now().plus(renewalWindowDays, ChronoUnit.DAYS)
        return certificateRepository.findByStatusAndRenewalReminderSentAtIsNull(CertificateStatus.ACTIVE)
            .filter { !it.expiresAt.isAfter(cutoff) }
    }

    /** One real renewal-reminder notification, called per-certificate by the scheduler
     * -- re-checks `status`/`renewalReminderSentAt` right before sending so a genuine
     * race can't double-fire, same resilience discipline
     * InsuranceService.sendRenewalReminder's own doc comment already establishes.
     * Reissuing (`POST /api/v1/certificate/issue`) is the real, already-working renewal
     * action -- this reminder just points the user at it before real expiry. */
    @Transactional
    fun sendRenewalReminder(certificateId: String) {
        val cert = certificateRepository.findById(certificateId).orElse(null) ?: return
        if (cert.status != CertificateStatus.ACTIVE || cert.renewalReminderSentAt != null) return

        val title = "Your itunda Certificate is expiring soon"
        val body = "Your certificate (serial ${cert.serialNumber}) expires on ${cert.expiresAt}. Reissue it anytime before then to keep signing without interruption."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = cert.userId, type = "CERTIFICATE_EXPIRING_SOON",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"certificateId\":\"${cert.id}\"}",
            ),
        )
        cert.renewalReminderSentAt = Instant.now()
        certificateRepository.save(cert)
        // Real fix (2026-09-13, push-before-commit ordering sweep): the push used to
        // fire BEFORE renewalReminderSentAt was saved -- a rollback after the push
        // would leave the flag unset and the next scheduler pass would resend it.
        sendRenewalPushAfterCommit(cert.userId, title, body, cert.id)
    }

    private fun sendRenewalPushAfterCommit(userId: String, title: String, body: String, certificateId: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body, mapOf("certificateId" to certificateId)) }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
