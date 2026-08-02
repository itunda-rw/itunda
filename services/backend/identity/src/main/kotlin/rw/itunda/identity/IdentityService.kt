package rw.itunda.identity

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.KycSubmission
import rw.itunda.core.repository.KycSubmissionRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SubmissionAlreadyPendingException(message: String) : RuntimeException(message)
class SubmissionNotFoundException(message: String) : RuntimeException(message)
class SubmissionNotPendingException(message: String) : RuntimeException(message)
class IdentityUserNotFoundException(message: String) : RuntimeException(message)

private const val BUSINESS_TIN_DOCUMENT_TYPE = "BUSINESS_TIN"

@Service
class IdentityService(
    private val kycSubmissionRepository: KycSubmissionRepository,
    private val userRepository: UserRepository,
    private val demoNidaVerificationService: DemoNidaVerificationService,
    private val demoKybVerificationService: DemoKybVerificationService,
    private val merchantRepository: MerchantRepository,
    private val rateLimiter: RateLimiter,
) {

    @Transactional
    fun submit(userId: String, documentType: String, documentNumber: String, documentReference: String): KycSubmission {
        val normalizedType = documentType.trim().uppercase()
        val normalizedNumber = documentNumber.trim().uppercase()
        val normalizedReference = documentReference.trim()
        require(normalizedType in setOf("NATIONAL_ID", "PASSPORT", BUSINESS_TIN_DOCUMENT_TYPE)) { "Unsupported identity document type" }
        require(normalizedNumber.length in 1..32 && normalizedNumber.all { it.isLetterOrDigit() || it == '-' }) { "Document number must be 1 to 32 letters, digits, or hyphens" }
        require(normalizedReference.length in 3..500) { "Document reference must be between 3 and 500 characters" }
        // Real bug found live (2026-08-02): identity document submission -- a classic
        // abuse target (this session's own checklist names it directly) -- had shipped
        // with zero rate limiting, unlike every comparable user-initiated creation
        // endpoint elsewhere in this codebase (Community/Jobs/Marketplace post creation,
        // Auth's own email/phone verification resends).
        rateLimiter.checkLimit("identity:submit:$userId", limit = 5, window = Duration.ofHours(1))
        // Real bug found live (2026-08-02): the plain "any PENDING submission" check
        // just below reads-then-CREATEs a brand-new row -- there's no existing PENDING
        // submission to put an @Version guard on yet, and kyc_submissions has no unique
        // constraint enforcing "at most one PENDING row per user" either, so two
        // concurrent submit() calls from the same user could both pass that check before
        // either committed and both create a real duplicate PENDING submission. Fixed
        // the same way this codebase's own "reject if already exists" race precedent
        // works: lock a DIFFERENT already-existing row (the caller's own real User row)
        // via UserRepository.findByIdForUpdate to serialize the two concurrent
        // submissions, then re-check under that lock.
        userRepository.findByIdForUpdate(userId).orElseThrow { IdentityUserNotFoundException("User not found") }
        val existing = kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc(userId)
        if (existing.any { it.status == "PENDING" }) {
            throw SubmissionAlreadyPendingException("A KYC submission is already pending review")
        }
        // Real automated pre-check, not a real NIDA/RDB lookup -- see
        // DemoNidaVerificationService's and DemoKybVerificationService's own doc
        // comments. Shown to the human reviewer, never auto-decides the submission on
        // its own: a structural/simulated match is real signal, not the same thing as a
        // real government database confirming this person's or business's identity.
        // BUSINESS_TIN routes to the KYB pre-check (9-digit TIN, no citizenship/birth-
        // year/gender fields to parse) instead of the National ID one -- everything
        // else about this workflow (PENDING row, human review, decide()) is identical.
        val (autoStatus, autoDetail) = if (normalizedType == BUSINESS_TIN_DOCUMENT_TYPE) {
            val result = demoKybVerificationService.verify(normalizedNumber)
            result.status.name to result.detail
        } else {
            val result = demoNidaVerificationService.verify(normalizedType, normalizedNumber)
            result.status.name to result.detail
        }
        val submission = KycSubmission(
            id = "kyc_${UUID.randomUUID()}",
            userId = userId,
            documentType = normalizedType,
            documentNumber = normalizedNumber,
            documentReference = normalizedReference,
            status = "PENDING",
            submittedAt = Instant.now(),
            autoVerificationStatus = autoStatus,
            autoVerificationDetail = autoDetail,
        )
        return kycSubmissionRepository.save(submission)
    }

    fun getMySubmissions(userId: String) = kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc(userId)

    fun getQueue(pageable: Pageable): Page<KycSubmission> = kycSubmissionRepository.findByStatusOrderBySubmittedAtAsc("PENDING", pageable)

    @Transactional
    fun decide(submissionId: String, reviewerId: String, approve: Boolean, reason: String?): KycSubmission {
        val submission = kycSubmissionRepository.findById(submissionId)
            .orElseThrow { SubmissionNotFoundException("Submission not found") }
        if (submission.status != "PENDING") {
            throw SubmissionNotPendingException("Submission is already ${submission.status}")
        }

        submission.status = if (approve) "VERIFIED" else "REJECTED"
        submission.reviewedBy = reviewerId
        submission.reviewedAt = Instant.now()
        submission.decisionReason = reason
        kycSubmissionRepository.save(submission)

        // A BUSINESS_TIN submission flips the submitter's Merchant.kybVerified, not
        // User.kycVerified -- a business's KYB status and the owner's personal KYC
        // status are two real, separate facts (an owner can be personally KYC-verified
        // with an unverified business, or vice versa if they registered before ever
        // completing personal KYC).
        if (approve) {
            if (submission.documentType.equals(BUSINESS_TIN_DOCUMENT_TYPE, ignoreCase = true)) {
                val merchant = merchantRepository.findByOwnerUserId(submission.userId)
                    ?: throw IdentityUserNotFoundException("No merchant registered for this account")
                merchant.kybVerified = true
                merchantRepository.save(merchant)
            } else {
                val user = userRepository.findById(submission.userId)
                    .orElseThrow { IdentityUserNotFoundException("User not found") }
                user.kycVerified = true
                userRepository.save(user)
            }
        }

        return submission
    }
}
