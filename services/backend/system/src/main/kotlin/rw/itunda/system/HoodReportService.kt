package rw.itunda.system

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
) {
    @Transactional
    fun report(reporterId: String, targetType: HoodReportTargetType, targetId: String, reason: String): HoodReport {
        require(targetId.isNotBlank()) { "A report target is required" }
        require(reason.trim().length in 3..180) { "Give a short reason between 3 and 180 characters" }
        val exists = when (targetType) {
            HoodReportTargetType.MARKETPLACE_LISTING -> listingRepository.existsById(targetId)
            HoodReportTargetType.COMMUNITY_POST -> communityPostRepository.existsById(targetId)
            HoodReportTargetType.JOB_POST -> jobPostRepository.existsById(targetId)
            HoodReportTargetType.PROPERTY_LISTING -> propertyListingRepository.existsById(targetId)
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
        }
        report.status = HoodReportStatus.RESOLVED
        report.reviewedBy = reviewerId
        report.reviewedAt = Instant.now()
        return repository.save(report)
    }
}
