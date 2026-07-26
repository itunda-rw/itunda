package rw.itunda.p2p

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.auth.RateLimiter
import rw.itunda.core.repository.ScamReportRepository

/**
 * Real Toss 사기계좌 조회 (fraud-account lookup before transfer)-style scam report --
 * see ScamReportService's own doc comment for the full sourced account.
 */
class ScamReportServiceTest : BehaviorSpec({

    Given("a real registered scam identifier and a real reporter") {
        val scamReportRepository = mockk<ScamReportRepository>(relaxed = true)
        every { scamReportRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = ScamReportService(scamReportRepository, rateLimiter)

        When("filing a real report with a valid reason") {
            every { scamReportRepository.existsByReporterIdAndReportedIdentifier("reporter_1", "+250788999000") } returns false

            val report = service.reportScam("reporter_1", "+250788999000", "Never delivered the item I paid for")

            Then("it saves a real report") {
                report.reporterId shouldBe "reporter_1"
                report.reportedIdentifier shouldBe "+250788999000"
            }
        }

        When("the reason is blank") {
            Then("it's rejected") {
                try {
                    service.reportScam("reporter_1", "+250788999000", "   ")
                    throw AssertionError("expected InvalidScamReportException")
                } catch (e: InvalidScamReportException) {
                    // expected
                }
            }
        }

        When("the identifier is blank") {
            Then("it's rejected") {
                try {
                    service.reportScam("reporter_1", "   ", "Scammed me")
                    throw AssertionError("expected InvalidScamReportException")
                } catch (e: InvalidScamReportException) {
                    // expected
                }
            }
        }

        When("the same reporter tries to report the same identifier twice") {
            every { scamReportRepository.existsByReporterIdAndReportedIdentifier("reporter_1", "+250788999000") } returns true

            Then("the duplicate is rejected -- one person can't inflate the real distinct-reporter count alone") {
                try {
                    service.reportScam("reporter_1", "+250788999000", "Scammed me again")
                    throw AssertionError("expected ScamReportAlreadyExistsException")
                } catch (e: ScamReportAlreadyExistsException) {
                    // expected
                }
            }
        }
    }

    Given("a real identifier with a growing number of real distinct reports") {
        val scamReportRepository = mockk<ScamReportRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = ScamReportService(scamReportRepository, rateLimiter)

        When("it has fewer reports than the real warning threshold") {
            every { scamReportRepository.countByReportedIdentifier("+250788111000") } returns 2L

            val result = service.checkScamStatus("+250788111000")

            Then("no warning is shown yet") {
                result.reportCount shouldBe 2L
                result.warn shouldBe false
            }
        }

        When("it crosses the real warning threshold") {
            every { scamReportRepository.countByReportedIdentifier("+250788222000") } returns 3L

            val result = service.checkScamStatus("+250788222000")

            Then("a real warning is shown, matching Toss's own real pre-transfer caution UX") {
                result.reportCount shouldBe 3L
                result.warn shouldBe true
            }
        }

        When("checking a clean identifier with zero real reports") {
            every { scamReportRepository.countByReportedIdentifier("+250788000000") } returns 0L

            val result = service.checkScamStatus("+250788000000")

            Then("no warning, no false positive") {
                result.warn shouldBe false
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
