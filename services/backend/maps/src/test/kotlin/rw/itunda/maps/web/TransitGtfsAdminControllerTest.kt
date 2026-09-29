package rw.itunda.maps.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.transit.TransitGtfsFetchFailedException
import rw.itunda.core.transit.TransitGtfsImportResult
import rw.itunda.core.transit.TransitGtfsImportService

/**
 * First test coverage for TransitGtfsAdminController -- a real ADMIN-gated one-shot
 * GTFS import trigger, previously untested at the controller layer despite
 * TransitGtfsImportService itself already having one.
 */
class TransitGtfsAdminControllerTest : BehaviorSpec({

    Given("a real ADMIN-triggered GTFS import") {
        val transitGtfsImportService = mockk<TransitGtfsImportService>()
        val ctl = TransitGtfsAdminController(transitGtfsImportService)
        val result = mockk<TransitGtfsImportResult>(relaxed = true)
        every { transitGtfsImportService.importFromUrl() } returns result

        When("triggering it") {
            val response = ctl.importGtfs()
            Then("it real-delegates and reports the real import result") {
                verify(exactly = 1) { transitGtfsImportService.importFromUrl() }
                response.body?.get("imported") shouldBe result
            }
        }
    }

    Given("a real TransitGtfsFetchFailedException") {
        val transitGtfsImportService = mockk<TransitGtfsImportService>()
        val ctl = TransitGtfsAdminController(transitGtfsImportService)

        When("its exception handler maps it to a real HTTP response") {
            val response = ctl.handleFetchFailed(TransitGtfsFetchFailedException("Upstream feed unreachable"))

            Then("it maps to 502 BAD_GATEWAY with code TRANSIT_GTFS_FETCH_FAILED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_GATEWAY
                response.body?.code shouldBe "TRANSIT_GTFS_FETCH_FAILED"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
