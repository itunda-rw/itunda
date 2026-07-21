package rw.itunda.system

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.HoodReport
import rw.itunda.core.domain.HoodReportStatus
import rw.itunda.core.domain.HoodReportTargetType
import rw.itunda.core.domain.JobPayType
import rw.itunda.core.domain.JobPost
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.repository.HoodReportRepository
import rw.itunda.core.repository.ListingRepository
import rw.itunda.core.repository.CommunityPostRepository
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.core.repository.PropertyListingRepository
import java.util.Optional
import java.math.BigDecimal

class HoodReportServiceTest : BehaviorSpec({
    Given("an open report for a job") {
        val repository = mockk<HoodReportRepository>()
        val listings = mockk<ListingRepository>()
        val community = mockk<CommunityPostRepository>()
        val jobs = mockk<JobPostRepository>()
        val properties = mockk<PropertyListingRepository>()
        val service = HoodReportService(repository, listings, community, jobs, properties)
        val existing = HoodReport("report_1", "user_1", HoodReportTargetType.JOB_POST, "job_1", "Fee requested")

        When("the same user reports the same job again") {
            every { repository.findByReporterUserIdAndTargetTypeAndTargetIdAndStatus("user_1", HoodReportTargetType.JOB_POST, "job_1", HoodReportStatus.OPEN) } returns existing
            every { jobs.existsById("job_1") } returns true

            Then("it rejects the duplicate instead of growing the review queue") {
                shouldThrow<HoodReportAlreadyOpenException> {
                    service.report("user_1", HoodReportTargetType.JOB_POST, "job_1", "Fee requested")
                }
                verify(exactly = 0) { repository.save(any()) }
            }
        }
    }

    Given("an open report awaiting review") {
        val repository = mockk<HoodReportRepository>()
        val service = HoodReportService(repository, mockk(), mockk(), mockk(), mockk())
        val report = HoodReport("report_2", "user_2", HoodReportTargetType.JOB_POST, "job_2", "Misleading pay")

        When("an administrator resolves it") {
            every { repository.findById("report_2") } returns Optional.of(report)
            every { repository.save(any()) } answers { firstArg() }
            val resolved = service.resolve("report_2", "admin_1")

            Then("it records an auditable resolution") {
                resolved.status shouldBe HoodReportStatus.RESOLVED
                resolved.reviewedBy shouldBe "admin_1"
                resolved.reviewedAt shouldNotBe null
            }
        }
    }

    Given("a report for a missing marketplace listing") {
        val repository = mockk<HoodReportRepository>()
        val listings = mockk<ListingRepository>()
        val service = HoodReportService(repository, listings, mockk(), mockk(), mockk())

        When("a member submits it") {
            every { listings.existsById("listing_missing") } returns false

            Then("it is rejected before entering the review queue") {
                shouldThrow<HoodReportTargetNotFoundException> {
                    service.report(
                        reporterId = "user_3",
                        targetType = HoodReportTargetType.MARKETPLACE_LISTING,
                        targetId = "listing_missing",
                        reason = "This post no longer exists",
                    )
                }
                verify(exactly = 0) { repository.save(any()) }
            }
        }
    }

    Given("a substantiated job report") {
        val repository = mockk<HoodReportRepository>()
        val jobs = mockk<JobPostRepository>()
        val service = HoodReportService(repository, mockk(), mockk(), jobs, mockk())
        val report = HoodReport("report_3", "user_3", HoodReportTargetType.JOB_POST, "job_3", "Asks for a fee")
        val job = JobPost("job_3", "poster_1", "cleaning", "Cleaner needed", "Bring supplies", JobPayType.FIXED, BigDecimal("3000"))

        When("an administrator removes the reported job") {
            every { repository.findById("report_3") } returns Optional.of(report)
            every { jobs.findById("job_3") } returns Optional.of(job)
            every { jobs.save(any()) } answers { firstArg() }
            every { repository.save(any()) } answers { firstArg() }
            val resolved = service.removeTarget("report_3", "admin_1")

            Then("the job is hidden and the decision is audited") {
                job.status shouldBe JobPostStatus.REMOVED
                resolved.status shouldBe HoodReportStatus.RESOLVED
                resolved.reviewedBy shouldBe "admin_1"
                verify { jobs.save(job) }
            }
        }
    }
})
