package rw.itunda.identity

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.KycSubmission
import rw.itunda.core.repository.KycSubmissionRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.UserRepository
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
) {

    @Transactional
    fun submit(userId: String, documentType: String, documentNumber: String, documentReference: String): KycSubmission {
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
        val (autoStatus, autoDetail) = if (documentType.equals(BUSINESS_TIN_DOCUMENT_TYPE, ignoreCase = true)) {
            val result = demoKybVerificationService.verify(documentNumber)
            result.status.name to result.detail
        } else {
            val result = demoNidaVerificationService.verify(documentType, documentNumber)
            result.status.name to result.detail
        }
        val submission = KycSubmission(
            id = "kyc_${UUID.randomUUID()}",
            userId = userId,
            documentType = documentType,
            documentNumber = documentNumber,
            documentReference = documentReference,
            status = "PENDING",
            submittedAt = Instant.now(),
            autoVerificationStatus = autoStatus,
            autoVerificationDetail = autoDetail,
        )
        return kycSubmissionRepository.save(submission)
    }

    fun getMySubmissions(userId: String) = kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc(userId)

    fun getQueue() = kycSubmissionRepository.findByStatusOrderBySubmittedAtAsc("PENDING")

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
