package rw.itunda.p2p.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.ScamReport
import rw.itunda.core.security.CurrentUser
import rw.itunda.p2p.InvalidScamReportException
import rw.itunda.p2p.ScamCheckResult
import rw.itunda.p2p.ScamReportAlreadyExistsException
import rw.itunda.p2p.ScamReportService

private fun testScamReport(id: String = "scamreport_1") = ScamReport(
    id = id, reporterId = "user_1", reportedIdentifier = "+250788999999", reason = "Never delivered goods after payment",
)

/**
 * First test coverage for ScamReportController -- previously untested despite the
 * service layer being fully tested. Covers all 3 endpoints' delegation (caller-scoped
 * userId, never client-supplied) and all 3 exception handlers.
 */
class ScamReportControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(scamReportService: ScamReportService = mockk()) = ScamReportController(scamReportService)

    Given("a real report-scam request") {
        val scamReportService = mockk<ScamReportService>()
        val ctl = controller(scamReportService)
        every {
            scamReportService.reportScam("user_1", "+250788999999", "Never delivered goods after payment")
        } returns testScamReport()

        When("reporting it") {
            val response = ctl.report(ReportScamRequest("+250788999999", "Never delivered goods after payment"), currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { scamReportService.reportScam("user_1", "+250788999999", "Never delivered goods after payment") }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real check-scam-status request") {
        val scamReportService = mockk<ScamReportService>()
        val ctl = controller(scamReportService)
        every { scamReportService.checkScamStatus("+250788999999") } returns ScamCheckResult("+250788999999", 3, true)

        When("checking it") {
            ctl.check("+250788999999")
            Then("it delegates by the real identifier") {
                verify(exactly = 1) { scamReportService.checkScamStatus("+250788999999") }
            }
        }
    }

    Given("a real my-reports request") {
        val scamReportService = mockk<ScamReportService>()
        val ctl = controller(scamReportService)
        every { scamReportService.getMyReports("user_1") } returns emptyList()

        When("fetching it") {
            ctl.mine(currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { scamReportService.getMyReports("user_1") }
            }
        }
    }

    listOf(
        Triple(InvalidScamReportException("Bad request") as RuntimeException, HttpStatus.BAD_REQUEST, "INVALID_SCAM_REPORT"),
        Triple(ScamReportAlreadyExistsException("Conflict"), HttpStatus.CONFLICT, "SCAM_REPORT_ALREADY_EXISTS"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is InvalidScamReportException -> ctl.handleInvalid(exception)
                    is ScamReportAlreadyExistsException -> ctl.handleAlreadyExists(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
