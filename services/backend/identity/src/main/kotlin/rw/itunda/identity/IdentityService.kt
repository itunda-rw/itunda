package rw.itunda.identity

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.KycSubmission
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.KycSubmissionRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SubmissionAlreadyPendingException(message: String) : RuntimeException(message)
class SubmissionNotFoundException(message: String) : RuntimeException(message)
class SubmissionNotPendingException(message: String) : RuntimeException(message)
class IdentityUserNotFoundException(message: String) : RuntimeException(message)
class InvalidDecisionReasonException(message: String) : RuntimeException(message)
class DuplicateDocumentNumberException(message: String) : RuntimeException(message)

private const val BUSINESS_TIN_DOCUMENT_TYPE = "BUSINESS_TIN"

/**
 * 2026-08-17: `decide` now notifies the real submitter of the outcome, closing the same
 * "terminal decision, zero notification to the real person it happened to" gap Sections
 * 146 (`InsuranceService.decideClaim`)/147 (`MarketplaceService.resolveDispute`)/148
 * (`PropertyOwnershipService.decide`) already closed elsewhere -- this is the exact
 * precedent `PropertyOwnershipService`'s own doc comment says it mirrors "field-for-field",
 * flagged there as still open and now closed here too.
 */
@Service
class IdentityService(
    private val kycSubmissionRepository: KycSubmissionRepository,
    private val userRepository: UserRepository,
    private val demoNidaVerificationService: DemoNidaVerificationService,
    private val demoKybVerificationService: DemoKybVerificationService,
    private val merchantRepository: MerchantRepository,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(IdentityService::class.java)

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
        // KycSubmission.decisionReason has no explicit @Column length (255 default) and
        // is written verbatim from a reviewer's free-text input -- same missing-bound
        // bug class as the 2026-09-05 sweep, just an admin-facing input rather than a
        // concatenation.
        if (reason != null && reason.length > 255) {
            throw InvalidDecisionReasonException("Decision reason must be 255 characters or fewer")
        }
        // Real gap closed 2026-09-07 (Identity product-completeness pass): documentNumber
        // has no unique DB constraint, so nothing previously stopped a second, different
        // account from being approved for the same real NIDA/passport/TIN number a
        // different account already got verified for -- defeating the entire real purpose
        // of this feature (confirming a real, unique identity). Checked here, at the real
        // load-bearing enforcement point (the moment a second account would actually
        // become verified), not at submit() time.
        if (approve) {
            val duplicate = kycSubmissionRepository
                .findByDocumentTypeAndDocumentNumberAndStatus(submission.documentType, submission.documentNumber, "VERIFIED")
                .firstOrNull { it.userId != submission.userId }
            if (duplicate != null) {
                throw DuplicateDocumentNumberException("This document number is already verified under a different account")
            }
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

        val isKyb = submission.documentType.equals(BUSINESS_TIN_DOCUMENT_TYPE, ignoreCase = true)
        val documentLabel = if (isKyb) "business verification (KYB)" else "identity verification (KYC)"
        val notifTitle = if (approve) "Verification approved" else "Verification rejected"
        val body = if (approve) {
            "Your $documentLabel was approved."
        } else {
            "Your $documentLabel was rejected.${reason?.let { " Reason: $it" } ?: ""} You can resubmit with a new document."
        }
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = submission.userId, type = "IDENTITY_VERIFICATION_DECIDED",
                title = notifTitle, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"submissionId\":\"${submission.id}\"}",
            ),
        )
        sendPushAfterCommit(submission.userId, notifTitle, body, submission.id)

        return submission
    }

    // Same real "defer the mobile push until the real status change is durable, but the
    // in-app Notification row is saved immediately" discipline OrderReturnService
    // .sendPushAfterCommit/InsuranceService.sendPushAfterCommit/MarketplaceService's own
    // dispute-resolution notification/PropertyOwnershipService.sendPushAfterCommit
    // already establish for a structurally identical terminal decision.
    private fun sendPushAfterCommit(userId: String, title: String, body: String, submissionId: String) {
        val data = mapOf("submissionId" to submissionId)
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body, data)
            } catch (e: Exception) {
                log.warn("Could not send identity-verification-decision push for submission {}", submissionId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
