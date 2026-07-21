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
import rw.itunda.core.repository.HoodReportRepository
import java.util.Optional

class HoodReportServiceTest : BehaviorSpec({
    Given("an open report for a job") {
        val repository = mockk<HoodReportRepository>()
        val service = HoodReportService(repository)
        val existing = HoodReport("report_1", "user_1", HoodReportTargetType.JOB_POST, "job_1", "Fee requested")

        When("the same user reports the same job again") {
            every { repository.findByReporterUserIdAndTargetTypeAndTargetIdAndStatus("user_1", HoodReportTargetType.JOB_POST, "job_1", HoodReportStatus.OPEN) } returns existing

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
        val service = HoodReportService(repository)
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
})
