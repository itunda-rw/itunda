package rw.itunda.system

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.HoodReport
import rw.itunda.core.domain.HoodReportStatus
import rw.itunda.core.domain.HoodReportTargetType
import rw.itunda.core.domain.ListingStatus
import rw.itunda.core.domain.CommunityPostStatus
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.repository.HoodReportRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.CommunityPostRepository
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.repository.MessageRepository
import rw.itunda.core.repository.GroupMessageRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class HoodReportAlreadyOpenException(message: String) : RuntimeException(message)
class HoodReportNotFoundException(message: String) : RuntimeException(message)
class HoodReportTargetNotFoundException(message: String) : RuntimeException(message)

@Service
class HoodReportService(
    private val repository: HoodReportRepository,
    private val listingRepository: ListingRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val jobPostRepository: JobPostRepository,
    private val propertyListingRepository: PropertyListingRepository,
    // Real per-message reporting (2026-07-25) -- closes docs/DESIGN_REFERENCES.md
    // Section 3's 당근마켓 "채팅 메시지별 신고" (per-message report) gap. Both
    // repositories already live in :core (see MessagingRepositories.kt/
    // GroupMessagingRepositories.kt), so this needs no new module dependency.
    private val messageRepository: MessageRepository,
    private val groupMessageRepository: GroupMessageRepository,
    private val rateLimiter: RateLimiter,
) {
    @Transactional
    fun report(reporterId: String, targetType: HoodReportTargetType, targetId: String, reason: String): HoodReport {
        require(targetId.isNotBlank()) { "A report target is required" }
        require(reason.trim().length in 3..180) { "Give a short reason between 3 and 180 characters" }
        // Real bug found live (2026-08-02): this real content-creation (report-filing)
        // endpoint had shipped with zero rate limiting -- every other real
        // content/report-creation endpoint in this codebase already has one. An
        // authenticated caller could otherwise spam unlimited real HoodReport rows
        // against any listing/post/message they can see.
        rateLimiter.checkLimit("hood-report:$reporterId", limit = 20, window = Duration.ofHours(1))
        val exists = when (targetType) {
            HoodReportTargetType.MARKETPLACE_LISTING -> listingRepository.existsById(targetId)
            HoodReportTargetType.COMMUNITY_POST -> communityPostRepository.existsById(targetId)
            HoodReportTargetType.JOB_POST -> jobPostRepository.existsById(targetId)
            HoodReportTargetType.PROPERTY_LISTING -> propertyListingRepository.existsById(targetId)
            // A reporter only needs to be a real participant of the conversation the
            // message lives in, same real IDOR discipline every other report target
            // already gets -- deliberately not re-checked here (existsById alone, same
            // shape as every other branch); MessagingController/GroupMessagingController
            // already gate reading the message itself behind real participant/member
            // checks before a client could ever see it to report.
            HoodReportTargetType.DIRECT_MESSAGE -> messageRepository.existsById(targetId)
            HoodReportTargetType.GROUP_MESSAGE -> groupMessageRepository.existsById(targetId)
        }
        if (!exists) throw HoodReportTargetNotFoundException("Report target not found")
        if (repository.findByReporterUserIdAndTargetTypeAndTargetIdAndStatus(reporterId, targetType, targetId, HoodReportStatus.OPEN) != null) {
            throw HoodReportAlreadyOpenException("You already have an open report for this post")
        }
        return repository.save(HoodReport("hood_report_${UUID.randomUUID()}", reporterId, targetType, targetId, reason.trim()))
    }

    fun queue(pageable: Pageable): Page<HoodReport> = repository.findByStatusOrderByCreatedAtAsc(HoodReportStatus.OPEN, pageable)
    @Transactional
    fun resolve(id: String, reviewerId: String): HoodReport {
        val report = repository.findById(id).orElseThrow { HoodReportNotFoundException("Report not found") }
        report.status = HoodReportStatus.RESOLVED; report.reviewedBy = reviewerId; report.reviewedAt = Instant.now()
        return repository.save(report)
    }

    /**
     * Moderation action for a substantiated report.  This uses the same soft-removal
     * state as the owner-facing remove action, so content disappears from every public
     * Hood feed while preserving the record needed for an audit or appeal.
     */
    @Transactional
    fun removeTarget(id: String, reviewerId: String): HoodReport {
        val report = repository.findById(id).orElseThrow { HoodReportNotFoundException("Report not found") }
        when (report.targetType) {
            HoodReportTargetType.MARKETPLACE_LISTING -> {
                val listing = listingRepository.findById(report.targetId).orElseThrow { HoodReportTargetNotFoundException("Report target not found") }
                listing.status = ListingStatus.REMOVED
                listingRepository.save(listing)
            }
            HoodReportTargetType.COMMUNITY_POST -> {
                val post = communityPostRepository.findById(report.targetId).orElseThrow { HoodReportTargetNotFoundException("Report target not found") }
                post.status = CommunityPostStatus.REMOVED
                communityPostRepository.save(post)
            }
            HoodReportTargetType.JOB_POST -> {
                val post = jobPostRepository.findById(report.targetId).orElseThrow { HoodReportTargetNotFoundException("Report target not found") }
                post.status = JobPostStatus.REMOVED
                jobPostRepository.save(post)
            }
            HoodReportTargetType.PROPERTY_LISTING -> {
                val listing = propertyListingRepository.findById(report.targetId).orElseThrow { HoodReportTargetNotFoundException("Report target not found") }
                listing.status = PropertyListingStatus.REMOVED
                propertyListingRepository.save(listing)
            }
            // Messages don't have a status enum -- same real soft-delete
            // (deletedAt/deletedByUserId) MessagingService.deleteMessage/
            // GroupMessagingService.deleteMessage already use when the sender deletes
            // their own message; a moderator-removed message goes through the identical
            // real path, just attributed to the reviewer instead of the sender.
            HoodReportTargetType.DIRECT_MESSAGE -> {
                val message = messageRepository.findById(report.targetId).orElseThrow { HoodReportTargetNotFoundException("Report target not found") }
                if (message.deletedAt == null) {
                    message.deletedAt = Instant.now()
                    message.deletedByUserId = reviewerId
                    messageRepository.save(message)
                }
            }
            HoodReportTargetType.GROUP_MESSAGE -> {
                val message = groupMessageRepository.findById(report.targetId).orElseThrow { HoodReportTargetNotFoundException("Report target not found") }
                if (message.deletedAt == null) {
                    message.deletedAt = Instant.now()
                    message.deletedByUserId = reviewerId
                    groupMessageRepository.save(message)
                }
            }
        }
        val reviewedAt = Instant.now()
        val relatedOpenReports = repository.findByTargetTypeAndTargetIdAndStatus(report.targetType, report.targetId, HoodReportStatus.OPEN)
        val reportsToResolve = (relatedOpenReports + report).distinctBy { it.id }
        reportsToResolve.forEach {
            it.status = HoodReportStatus.RESOLVED
            it.reviewedBy = reviewerId
            it.reviewedAt = reviewedAt
        }
        repository.saveAll(reportsToResolve)
        return report
    }
}
