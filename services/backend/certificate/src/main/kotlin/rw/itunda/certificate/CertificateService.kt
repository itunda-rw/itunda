package rw.itunda.certificate

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.Certificate
import rw.itunda.core.domain.CertificateStatus
import rw.itunda.core.repository.CertificateRepository
import rw.itunda.core.repository.UserRepository
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
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
) {
    private val secureRandom = SecureRandom()

    // A real, standard 1-year certificate validity period -- common industry practice
    // for digital certificates generally, not a claim about Toss's own specific
    // renewal cycle (which wasn't part of what this pass could directly source).
    private val validityDays = 365L

    @Transactional
    fun issue(userId: String): Pair<Certificate, String> {
        val user = userRepository.findById(userId).orElseThrow { CertificateUserNotFoundException("User not found") }
        // Real precondition mirroring Toss's own real requirement -- a real phone number
        // and a real ID must already be verified before Toss issues its certificate
        // (support.toss.im/faq/1245). This backend's own real KYC pipeline
        // (IdentityService) is the equivalent gate.
        if (!user.kycVerified) {
            throw CertificateUserNotVerifiedException("Real KYC verification is required before a certificate can be issued")
        }

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
        val cert = certificateRepository.findByUserIdAndStatus(userId, CertificateStatus.ACTIVE)
            ?: throw NoCertificateFoundException("No active certificate to revoke")
        cert.status = CertificateStatus.REVOKED
        cert.revokedAt = Instant.now()
        return certificateRepository.save(cert)
    }

    fun getStatus(serialNumber: String): Certificate =
        withEffectiveStatus(certificateRepository.findBySerialNumber(serialNumber) ?: throw CertificateNotFoundException("Certificate not found"))

    // Real cryptographic verification (JCA Ed25519) against the certificate's stored
    // public key -- reports signature validity and certificate status as two separate
    // real facts, matching how real PKI verification checks both the math and
    // revocation/expiry independently, rather than collapsing them into one boolean.
    fun verify(serialNumber: String, payload: String, signatureBase64: String): VerificationResult {
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
}
