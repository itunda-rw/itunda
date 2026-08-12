package rw.itunda.partners

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.IdentityVerificationRequest
import rw.itunda.core.domain.IdentityVerificationStatus
import rw.itunda.core.repository.IdentityVerificationRequestRepository
import rw.itunda.core.repository.PartnerRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class IdentityVerificationRequestNotFoundException(message: String) : RuntimeException(message)
class IdentityVerificationRequestNotPendingException(message: String) : RuntimeException(message)
class IdentityVerificationUserNotFoundException(message: String) : RuntimeException(message)

data class IdentityDisclosure(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val kycVerified: Boolean,
    val birthDate: String?,
)

/**
 * Real "verify/sign in with itunda" flow for external partners -- see
 * IdentityVerificationRequest.kt's own doc comment for the full sourced Toss Cert
 * account and this feature's own honest scope. A partner (already holding a real
 * itunda Partner API key, same auth PartnerService's mini-app endpoints already use)
 * creates a request; the itunda app shows the user a real consent screen naming the
 * partner and exactly what will be shared; only on explicit approval does this service
 * ever build/sign/store the disclosed payload -- there is no silent or default-approve
 * path anywhere in this class.
 */
@Service
class IdentityVerificationService(
    private val requestRepository: IdentityVerificationRequestRepository,
    private val partnerRepository: PartnerRepository,
    private val userRepository: UserRepository,
    private val partnerService: PartnerService,
    private val signingKeyProvider: IdentitySigningKeyProvider,
    private val rateLimiter: RateLimiter,
) {
    private val requestTtl: Duration = Duration.ofMinutes(5)

    @Transactional
    fun createRequest(apiKey: String): IdentityVerificationRequest {
        val partner = partnerService.authenticate(apiKey)
        // Real abuse guard, same shape PartnerService.register already applies to its
        // own public-ish surface -- a partner's own key is the natural rate-limit key
        // here, not an IP (this endpoint is server-to-server, never called from a
        // browser directly).
        rateLimiter.checkLimit("identity:create:${partner.id}", limit = 30, window = Duration.ofMinutes(1))
        val request = IdentityVerificationRequest(
            id = "idverify_${UUID.randomUUID()}",
            partnerId = partner.id,
            expiresAt = Instant.now().plus(requestTtl),
        )
        return requestRepository.save(request)
    }

    // Real read-path lazy expiry, same convention P2pPaymentRequest already
    // established (P2pService.payRequest) -- a PENDING row past its own expiresAt is
    // treated and persisted as EXPIRED the moment anyone next looks at it, rather than
    // needing a separate scheduled sweep just to keep status accurate.
    private fun withEffectiveStatus(request: IdentityVerificationRequest): IdentityVerificationRequest {
        if (request.status == IdentityVerificationStatus.PENDING && request.expiresAt.isBefore(Instant.now())) {
            request.status = IdentityVerificationStatus.EXPIRED
            return requestRepository.save(request)
        }
        return request
    }

    /** For the itunda app's own consent screen -- the user reviewing a request about themselves. */
    fun getForUser(requestId: String): Pair<IdentityVerificationRequest, String> {
        val request = withEffectiveStatus(
            requestRepository.findById(requestId).orElseThrow {
                IdentityVerificationRequestNotFoundException("Verification request not found")
            },
        )
        val partner = partnerRepository.findById(request.partnerId).orElseThrow {
            IdentityVerificationRequestNotFoundException("Verification request not found")
        }
        return request to partner.companyName
    }

    /** For a partner polling the real result of a request they created. */
    fun getForPartner(requestId: String, apiKey: String): IdentityVerificationRequest {
        val partner = partnerService.authenticate(apiKey)
        val request = requestRepository.findByIdAndPartnerId(requestId, partner.id)
            ?: throw IdentityVerificationRequestNotFoundException("Verification request not found")
        return withEffectiveStatus(request)
    }

    @Transactional
    fun approve(requestId: String, userId: String): IdentityVerificationRequest {
        val request = withEffectiveStatus(
            requestRepository.findById(requestId).orElseThrow {
                IdentityVerificationRequestNotFoundException("Verification request not found")
            },
        )
        if (request.status != IdentityVerificationStatus.PENDING) {
            throw IdentityVerificationRequestNotPendingException("This request has already been responded to or has expired")
        }
        val user = userRepository.findById(userId).orElseThrow {
            IdentityVerificationUserNotFoundException("Account not found")
        }
        // Real, frozen-at-approval-time snapshot -- see the entity's own doc comment
        // for why this is never re-read live from `User` on a later partner poll.
        val payload = """{"firstName":"${escape(user.firstName)}","lastName":"${escape(user.lastName)}",""" +
            """"phoneNumber":"${escape(user.phoneNumber)}","kycVerified":${user.kycVerified},""" +
            """"birthDate":${user.birthDate?.let { "\"$it\"" } ?: "null"}}"""
        request.userId = userId
        request.disclosedPayloadJson = payload
        request.signature = signingKeyProvider.sign(payload)
        request.status = IdentityVerificationStatus.APPROVED
        request.respondedAt = Instant.now()
        return requestRepository.save(request)
    }

    @Transactional
    fun decline(requestId: String, userId: String): IdentityVerificationRequest {
        val request = withEffectiveStatus(
            requestRepository.findById(requestId).orElseThrow {
                IdentityVerificationRequestNotFoundException("Verification request not found")
            },
        )
        if (request.status != IdentityVerificationStatus.PENDING) {
            throw IdentityVerificationRequestNotPendingException("This request has already been responded to or has expired")
        }
        request.userId = userId
        request.status = IdentityVerificationStatus.DECLINED
        request.respondedAt = Instant.now()
        return requestRepository.save(request)
    }

    fun publicKeyBase64(): String = signingKeyProvider.publicKeyBase64()

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
