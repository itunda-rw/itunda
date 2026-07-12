package rw.itunda.identity

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.KycSubmission
import rw.itunda.core.repository.KycSubmissionRepository
import rw.itunda.core.repository.UserRepository
import java.time.Instant
import java.util.UUID

class SubmissionAlreadyPendingException(message: String) : RuntimeException(message)
class SubmissionNotFoundException(message: String) : RuntimeException(message)
class SubmissionNotPendingException(message: String) : RuntimeException(message)
class IdentityUserNotFoundException(message: String) : RuntimeException(message)

@Service
class IdentityService(
    private val kycSubmissionRepository: KycSubmissionRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun submit(userId: String, documentType: String, documentNumber: String, documentReference: String): KycSubmission {
        val existing = kycSubmissionRepository.findByUserIdOrderBySubmittedAtDesc(userId)
        if (existing.any { it.status == "PENDING" }) {
            throw SubmissionAlreadyPendingException("A KYC submission is already pending review")
        }
        val submission = KycSubmission(
            id = "kyc_${UUID.randomUUID()}",
            userId = userId,
            documentType = documentType,
            documentNumber = documentNumber,
            documentReference = documentReference,
            status = "PENDING",
            submittedAt = Instant.now(),
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

        if (approve) {
            val user = userRepository.findById(submission.userId)
                .orElseThrow { IdentityUserNotFoundException("User not found") }
            user.kycVerified = true
            userRepository.save(user)
        }

        return submission
    }
}
