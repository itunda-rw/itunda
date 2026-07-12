package rw.itunda.core.incident

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Incident
import rw.itunda.core.domain.IncidentStatus
import rw.itunda.core.provider.RailProfile
import rw.itunda.core.repository.IncidentRepository
import java.util.Optional

class IncidentDetectorTest : BehaviorSpec({

    val rail = RailProfile(id = "wasac", displayName = "WASAC - Water", avgLatencyMs = 700, successRate = 0.92, degraded = true)

    Given("a rail with no prior failures") {
        val incidentRepository = mockk<IncidentRepository>()
        val detector = IncidentDetector(incidentRepository)

        every { incidentRepository.findByRailIdAndStatus("wasac", IncidentStatus.OPEN) } returns null

        When("a single failure is recorded") {
            detector.recordFailure(rail)

            Then("no incident opens yet -- one blip isn't an outage") {
                verify(exactly = 0) { incidentRepository.save(any()) }
            }
        }
    }

    Given("a rail that fails twice within the detection window") {
        val incidentRepository = mockk<IncidentRepository>()
        val detector = IncidentDetector(incidentRepository)

        every { incidentRepository.findByRailIdAndStatus("wasac", IncidentStatus.OPEN) } returns null
        every { incidentRepository.save(any()) } answers { firstArg() }

        When("recording two failures") {
            detector.recordFailure(rail)
            detector.recordFailure(rail)

            Then("a real incident opens automatically -- no human typed this in") {
                verify(exactly = 1) { incidentRepository.save(any()) }
            }
        }
    }

    Given("a rail that already has an OPEN incident") {
        val incidentRepository = mockk<IncidentRepository>()
        val detector = IncidentDetector(incidentRepository)

        val existing = Incident(id = "incident_1", railId = "wasac", railDisplayName = "WASAC - Water", description = "already open", failureCount = 2)
        every { incidentRepository.findByRailIdAndStatus("wasac", IncidentStatus.OPEN) } returns existing
        every { incidentRepository.save(any()) } answers { firstArg() }

        When("more failures keep coming in") {
            detector.recordFailure(rail)
            detector.recordFailure(rail)

            Then("it never opens a second, duplicate incident for the same still-failing rail") {
                verify(exactly = 0) { incidentRepository.save(any()) }
            }
        }
    }

    Given("a real OPEN incident being resolved") {
        val incidentRepository = mockk<IncidentRepository>()
        val detector = IncidentDetector(incidentRepository)

        val incident = Incident(id = "incident_2", railId = "mtn_momo", railDisplayName = "MTN Mobile Money", description = "test", failureCount = 2)
        every { incidentRepository.findById("incident_2") } returns Optional.of(incident)
        every { incidentRepository.save(any()) } answers { firstArg() }

        When("an admin resolves it") {
            val resolved = detector.resolve("incident_2", "admin_1")

            Then("it's marked RESOLVED with a real resolver and timestamp") {
                resolved.status shouldBe IncidentStatus.RESOLVED
                resolved.resolvedBy shouldBe "admin_1"
            }
        }
    }

    Given("an already-resolved incident") {
        val incidentRepository = mockk<IncidentRepository>()
        val detector = IncidentDetector(incidentRepository)

        val incident = Incident(id = "incident_3", railId = "mtn_momo", railDisplayName = "MTN Mobile Money", description = "test", failureCount = 2, status = IncidentStatus.RESOLVED)
        every { incidentRepository.findById("incident_3") } returns Optional.of(incident)

        When("trying to resolve it again") {
            Then("it throws IncidentAlreadyResolvedException rather than silently re-resolving") {
                try {
                    detector.resolve("incident_3", "admin_1")
                    error("expected IncidentAlreadyResolvedException")
                } catch (e: IncidentAlreadyResolvedException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
